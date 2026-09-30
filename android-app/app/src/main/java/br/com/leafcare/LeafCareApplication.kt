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
import br.com.leafcare.data.shouldWipeForAccountSwitch
import br.com.leafcare.data.requestAnalysisSync
import br.com.leafcare.ml.LeafClassifier

class LeafCareApplication : Application(), Configuration.Provider {
    val database by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "leafcare.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }
    val catalog by lazy { DiseaseCatalog(this) }
    val repository by lazy { AnalysisRepository(this, database.analysisDao(), catalog, LeafClassifier(this)) }

    // Supabase client initialized once for the application lifecycle
    val supabaseClientHolder by lazy { SupabaseClientHolder(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        // Flush analyses pending from before a restart.
        scheduleSync()
    }

    /** Schedules a sync pass for locally changed analyses. */
    fun scheduleSync() {
        requestAnalysisSync(this)
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
        if (shouldWipeForAccountSwitch(stored, userId, repository.hasLocalRows())) {
            repository.clearAllLocal()
        }
        prefs.edit().putString(KEY_LAST_USER_ID, userId).apply()
    }

    companion object {
        private const val PREFS_NAME = "leafcare"
        private const val KEY_LAST_USER_ID = "last_account_user_id"
    }
}
