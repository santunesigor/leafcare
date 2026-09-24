package br.com.leafcare.data

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import java.io.File

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
 * - Uploads photos only for remotely confirmed analyses, then associates
 *   the remote path on the row. Same deterministic path on every retry.
 * - Deletes tombstoned rows remotely (photo first), then removes them
 *   (and the photo) locally.
 * - A failed delete keeps its tombstone and retries through the delete path,
 *   so a deleted analysis can never be resurrected by a later upsert.
 * - Network/auth failures never touch local data beyond the ERROR state.
 */
internal class AnalysisSyncRunner(
    private val dao: AnalysisDao,
    private val api: AnalysisSyncApi,
    private val appVersion: String,
    private val photoDeleter: (photoName: String) -> Unit = {},
    private val photoFile: (photoName: String) -> File = { throw IllegalStateException("no photo storage") },
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

        dao.getPendingPhotoUploads().forEach { entity ->
            try {
                val path = remotePhotoPath(userId, entity.id)
                api.uploadPhoto(path, photoFile(entity.photoName).readBytes())
                api.updatePhotoPath(entity.id, path)
                dao.markPhotoSynced(entity.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Missing local file included: retryable, never a crash.
                dao.markPhotoError(entity.id)
                failures++
            }
        }

        dao.getPendingDeletes().forEach { entity ->
            try {
                if (entity.photoSyncStatus == PhotoSyncState.SYNCED) {
                    try {
                        api.deleteRemotePhotos(listOf(remotePhotoPath(userId, entity.id)))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Already gone remotely: treated as completed, not fatal.
                        if (!isRemoteNotFound(e)) throw e
                    }
                }
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

    private fun isRemoteNotFound(e: Exception): Boolean {
        val raw = e.message.orEmpty().lowercase()
        return raw.contains("not found") ||
            raw.contains("does not exist") ||
            raw.contains("404")
    }
}
