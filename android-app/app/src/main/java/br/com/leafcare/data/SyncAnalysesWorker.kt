package br.com.leafcare.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.leafcare.admin.AdminRepository
import br.com.leafcare.BuildConfig
import br.com.leafcare.LeafCareApplication
import br.com.leafcare.auth.LegalAcceptanceRepository
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.concurrent.TimeUnit

private const val SYNC_WORK_NAME = "sync-analyses"
private const val MAX_ATTEMPTS = 5

/** Private bucket holding analysis photos at `{user_id}/{analysis_id}.jpg`. */
internal const val PHOTOS_BUCKET = "analysis-photos"
internal const val TRAINING_PHOTOS_BUCKET = "training-photos"

/** Schedules a sync pass: runs only with network, with exponential backoff. */
fun requestAnalysisSync(context: Context) {
    val request = OneTimeWorkRequestBuilder<SyncAnalysesWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

/**
 * Best-effort push of pending training withdrawals (consent, photo
 * authorizations, training copies) for the signed-in user, e.g. right before
 * logout. Never uploads. Returns true when nothing is left pending; never throws
 * for backend failures.
 */
suspend fun pushPendingTrainingRevocation(context: Context): Boolean {
    val app = context.applicationContext as LeafCareApplication
    val userId = app.supabaseClientHolder.auth.currentUserOrNull()?.id ?: return false
    return try {
        val result = syncRunner(app, userId).syncTrainingRevocationOnly(userId)
        result == SyncRunResult.Completed(0)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}

internal fun syncRunner(app: LeafCareApplication, userId: String): AnalysisSyncRunner {
    val auth = app.supabaseClientHolder.auth
    val client = app.supabaseClientHolder.client
    val photosDir = File(app.filesDir, "photos")
    return AnalysisSyncRunner(
        dao = app.database.analysisDao(),
        api = PostgrestAnalysisSyncApi(
            postgrest = client.postgrest,
            bucket = client.storage[PHOTOS_BUCKET]
        ),
        appVersion = BuildConfig.VERSION_NAME,
        uploadPolicy = {
            // my_account_policy is SQL null while the account deletion is pending:
            // JSONObject("null") throws and the runner fails closed.
            val policy = AdminRepository(app.supabaseClientHolder).policy()
            check(auth.currentUserOrNull()?.id == userId) { "Account changed" }
            UploadPolicy(
                enabled = policy.getBoolean("uploads_enabled"),
                cutoffMs = if (policy.isNull("cutoff_ms")) null else policy.getLong("cutoff_ms"),
            )
        },
        photoDeleter = { name ->
            require(File(name).name == name)
            File(photosDir, name).delete()
        },
        photoFile = { name ->
            require(File(name).name == name)
            File(photosDir, name)
        },
        consentDao = app.database.trainingConsentDao(),
        trainingApi = PostgrestTrainingConsentSyncApi(client.postgrest, client.storage[TRAINING_PHOTOS_BUCKET]),
        sessionUserId = { auth.currentUserOrNull()?.id },
    )
}

/**
 * Background sync worker. Survives app close/restart (WorkManager persists the
 * queue); retries with backoff up to [MAX_ATTEMPTS], afterwards rows stay in
 * ERROR state until the next trigger.
 */
class SyncAnalysesWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as LeafCareApplication
        val auth = app.supabaseClientHolder.auth
        // In a fresh worker process GoTrue restores the session asynchronously.
        auth.awaitInitialization()
        val userId = auth.currentUserOrNull()?.id ?: return Result.success()
        val hasCurrentLegalAcceptance = try {
            LegalAcceptanceRepository(app.supabaseClientHolder, app.database.trainingConsentDao()).hasCurrentAcceptance()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return if (runAttemptCount >= MAX_ATTEMPTS) Result.success() else Result.retry()
        }
        if (auth.currentUserOrNull()?.id != userId) return Result.success()
        val runner = syncRunner(app, userId)
        if (!hasCurrentLegalAcceptance) {
            // Leave local history queued until a current receipt is confirmed,
            // but a training withdrawal must still reach the server.
            val revocation = runner.syncTrainingRevocationOnly(userId) as? SyncRunResult.Completed
            val failed = (revocation?.failures ?: 0) > 0
            return if (!failed || runAttemptCount >= MAX_ATTEMPTS) Result.success() else Result.retry()
        }
        val pushFailures = when (val push = runner.syncOnce(userId)) {
            is SyncRunResult.SkippedNoAuth -> return Result.success()
            is SyncRunResult.Completed -> push.failures
        }
        // Account switch or logout mid-pass: abort instead of restoring or
        // downloading as the wrong user (or logged out).
        if (auth.currentUserOrNull()?.id != userId) return Result.success()
        // Same pass, same retry policy: push first, then restore remote rows
        // into Room, then cache their photos.
        val restoreFailures =
            (runner.restoreOnce(userId) as? SyncRunResult.Completed)?.failures ?: 0
        if (auth.currentUserOrNull()?.id != userId) return Result.success()
        val downloadFailures =
            (runner.downloadOnce(userId) as? SyncRunResult.Completed)?.failures ?: 0
        val failures = pushFailures + restoreFailures + downloadFailures
        return if (failures == 0 || runAttemptCount >= MAX_ATTEMPTS) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        /** Cancels queued and running sync passes (call on sign-out). */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
        }
    }
}
