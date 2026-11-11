package br.com.leafcare

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import br.com.leafcare.auth.SupabaseClientHolder
import br.com.leafcare.data.AnalysisRepository
import br.com.leafcare.data.AppDatabase
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.data.MIGRATION_1_2
import br.com.leafcare.data.MIGRATION_2_3
import br.com.leafcare.data.MIGRATION_3_4
import br.com.leafcare.data.SyncAnalysesWorker
import br.com.leafcare.data.pushPendingTrainingRevocation
import br.com.leafcare.data.shouldWipeForAccountSwitch
import br.com.leafcare.data.requestAnalysisSync
import br.com.leafcare.ml.LeafClassifier
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LeafCareApplication : Application(), Configuration.Provider {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "leafcare.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
    }
    val catalog by lazy { DiseaseCatalog(this) }
    val repository by lazy { AnalysisRepository(this, database.analysisDao(), catalog, LeafClassifier(this)) }

    // Supabase client initialized once for the application lifecycle
    val supabaseClientHolder by lazy { SupabaseClientHolder(this) }

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Flush analyses pending from before a restart.
        scheduleSync()
        backgroundScope.launch {
            try {
                cleanupOrphanFiles(File(filesDir, "photos"), cacheDir, System.currentTimeMillis()) { name ->
                    database.analysisDao().get(name.removeSuffix(".img"))?.photoName == name
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Best effort: leftovers are retried on the next start.
            }
        }
    }

    /** Schedules a sync pass for locally changed analyses. */
    fun scheduleSync() {
        requestAnalysisSync(this)
    }

    /** Drops queued sync passes (sign-out): none may run without the account. */
    fun cancelSync() {
        SyncAnalysesWorker.cancel(this)
    }

    /**
     * Best effort, bounded: sends a training withdrawal still pending on this
     * device while the session exists. It stays queued on failure or timeout.
     */
    suspend fun pushPendingTrainingRevocation(userId: String) {
        try {
            val consentDao = database.trainingConsentDao()
            val consent = consentDao.get(userId) ?: return
            val pending = (!consent.granted && consent.syncState == "PENDING") ||
                consentDao.getPendingPhotos(userId).any { !it.authorized }
            if (!pending) return
            withTimeoutOrNull(REVOCATION_PUSH_TIMEOUT_MS) { pushPendingTrainingRevocation(this@LeafCareApplication) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Stays PENDING locally; hasCurrentAcceptance keeps honoring it.
        }
    }

    /**
     * Enforces account isolation after a successful authentication.
     * Same user: keeps everything. Different user (or unattributable rows
     * from before isolation existed): wipes local rows and photos so one
     * account can never see or sync another account's data.
     */
    suspend fun ensureAccountIsolation(userId: String) {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val stored = prefs.getString(KEY_LAST_USER_ID, null)
        val hasConsentState = database.trainingConsentDao().hasConsentRows()
        if (shouldWipeForAccountSwitch(stored, userId, repository.hasLocalRows() || hasConsentState)) {
            repository.clearAllLocal()
        }
        prefs.edit().putString(KEY_LAST_USER_ID, userId).apply()
    }

    companion object {
        private const val PREFS_NAME = "leafcare"
        private const val KEY_LAST_USER_ID = "last_account_user_id"
        private const val REVOCATION_PUSH_TIMEOUT_MS = 5_000L
    }
}

/** Files younger than this may belong to an analysis or a download in progress. */
internal const val ORPHAN_FILE_MIN_AGE_MS = 60 * 60 * 1000L

/**
 * Removes leftovers older than [ORPHAN_FILE_MIN_AGE_MS]: photos (`{id}.img`)
 * without an analysis row, interrupted downloads (`*.tmp`) and camera captures
 * (`capture-*.jpg`). Unknown file names are kept.
 */
internal suspend fun cleanupOrphanFiles(
    photosDir: File,
    cacheDir: File,
    now: Long,
    isReferenced: suspend (photoName: String) -> Boolean,
) {
    val cutoff = now - ORPHAN_FILE_MIN_AGE_MS
    photosDir.listFiles()?.forEach { file ->
        if (!file.isFile || file.lastModified() > cutoff) return@forEach
        when {
            file.name.endsWith(".tmp") -> file.delete()
            file.name.endsWith(".img") && !isReferenced(file.name) -> file.delete()
        }
    }
    cacheDir.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith("capture-") && file.name.endsWith(".jpg") &&
            file.lastModified() <= cutoff
        ) file.delete()
    }
}
