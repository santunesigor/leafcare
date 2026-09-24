package br.com.leafcare.data

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant

/** Outcome of one sync pass. Never throws for backend failures. */
internal sealed interface SyncRunResult {
    /** No authenticated user: nothing to do (retried on the next trigger). */
    data object SkippedNoAuth : SyncRunResult

    /** Pass finished; [failures] rows were marked ERROR for a later retry. */
    data class Completed(val failures: Int) : SyncRunResult
}

/**
 * Offline-first sync engine: Room -> Supabase.
 *
 * - Uploads pending rows with idempotent upsert on the local UUID.
 * - Deletes tombstoned rows remotely, then removes them (and the photo) locally.
 * - A failed delete keeps its tombstone and retries through the delete path,
 *   so a deleted analysis can never be resurrected by a later upsert.
 * - Network/auth failures never touch local data beyond the ERROR state.
 */
internal class AnalysisSyncRunner(
    private val dao: AnalysisDao,
    private val api: AnalysisSyncApi,
    private val appVersion: String,
    private val photoDeleter: (photoName: String) -> Unit = {},
) {
    suspend fun syncOnce(userId: String?): SyncRunResult {
        if (userId == null) return SyncRunResult.SkippedNoAuth

        var failures = 0

        dao.getPendingUploads().forEach { entity ->
            try {
                api.upsertAnalysis(entity.toRemoteJson(userId, appVersion))
                dao.markSynced(entity.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.markError(entity.id)
                failures++
            }
        }

        dao.getPendingDeletes().forEach { entity ->
            try {
                val deletedAt = entity.deletedAt ?: System.currentTimeMillis()
                api.markRemoteDeleted(entity.id, Instant.fromEpochMilliseconds(deletedAt).toString())
                dao.delete(entity.id)
                photoDeleter(entity.photoName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.markError(entity.id)
                failures++
            }
        }

        return SyncRunResult.Completed(failures)
    }
}
