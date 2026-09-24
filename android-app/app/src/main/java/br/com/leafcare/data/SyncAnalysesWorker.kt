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
import br.com.leafcare.BuildConfig
import br.com.leafcare.LeafCareApplication
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import java.io.File
import java.util.concurrent.TimeUnit

private const val SYNC_WORK_NAME = "sync-analyses"
private const val MAX_ATTEMPTS = 5

/** Private bucket holding analysis photos at `{user_id}/{analysis_id}.jpg`. */
internal const val PHOTOS_BUCKET = "analysis-photos"

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
        .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND, request)
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
        val userId = app.supabaseClientHolder.auth.currentUserOrNull()?.id
        val dao = app.database.analysisDao()
        val client = app.supabaseClientHolder.client
        val api = PostgrestAnalysisSyncApi(
            postgrest = client.postgrest,
            bucket = client.storage[PHOTOS_BUCKET]
        )
        val photosDir = File(applicationContext.filesDir, "photos")
        val runner = AnalysisSyncRunner(
            dao = dao,
            api = api,
            appVersion = BuildConfig.VERSION_NAME,
            photoDeleter = { name ->
                require(File(name).name == name)
                File(photosDir, name).delete()
            },
            photoFile = { name ->
                require(File(name).name == name)
                File(photosDir, name)
            }
        )
        val pushFailures = when (val push = runner.syncOnce(userId)) {
            is SyncRunResult.SkippedNoAuth -> return Result.success()
            is SyncRunResult.Completed -> push.failures
        }
        // Same pass, same retry policy: push first, then restore remote rows
        // into Room (no photo download in this unit).
        val restoreFailures =
            (runner.restoreOnce(userId) as? SyncRunResult.Completed)?.failures ?: 0
        // Then cache photos for REMOTE_ONLY rows (download unit).
        val downloadFailures =
            (runner.downloadOnce(userId) as? SyncRunResult.Completed)?.failures ?: 0
        val failures = pushFailures + restoreFailures + downloadFailures
        return if (failures == 0 || runAttemptCount >= MAX_ATTEMPTS) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
