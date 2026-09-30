package br.com.leafcare.data

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
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
                if (entity.photoSyncStatus == PhotoSyncState.SYNCED ||
                    entity.photoSyncStatus == PhotoSyncState.REMOTE_ONLY
                ) {
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

    /**
     * Restores remote analyses into Room (no photo download in this unit).
     *
     * Merge policy (simplest safe):
     * - remote active + no local row -> insert as SYNCED / REMOTE_ONLY;
     * - same UUID locally (pending, synced or tombstoned) -> never overwritten,
     *   so pending uploads and offline deletions can't be lost or resurrected;
     * - remote tombstone + no local row -> skipped, never imported as active;
     * - remote tombstone + local SYNCED row -> local row and photo removed;
     * - remote tombstone + local pending/tombstone -> kept as is.
     * Malformed remote rows are skipped without failing the restore.
     */
    suspend fun restoreOnce(userId: String?): SyncRunResult {
        if (userId == null) return SyncRunResult.SkippedNoAuth

        val rows: List<JsonObject> = try {
            api.fetchAnalyses()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return SyncRunResult.Completed(1)
        }

        rows.forEach { row ->
            try {
                val entity = row.toAnalysisEntity()
                val local = dao.get(entity.id)
                if (entity.deletedAt != null) {
                    if (local != null && local.deletedAt == null &&
                        local.syncStatus == SyncState.SYNCED
                    ) {
                        dao.delete(entity.id)
                        photoDeleter(local.photoName)
                    }
                } else if (local == null) {
                    dao.insert(entity)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Malformed row: skip without failing the whole restore.
            }
        }

        return SyncRunResult.Completed(0)
    }

    /**
     * Downloads photos for REMOTE_ONLY rows into the private local photo
     * storage, using the same files the UI already reads (`{id}.img`).
     *
     * - Existing valid local file: cache satisfied, no download.
     * - Writes go through a temp file + rename, so a failed download never
     *   leaves a partial file as a valid photo.
     * - Failures keep REMOTE_ONLY (retryable); the analysis row is untouched.
     * - Remote path is always derived from the session user id, never read
     *   from stored data, so another user's path can't be fetched.
     */
    suspend fun downloadOnce(userId: String?): SyncRunResult {
        if (userId == null) return SyncRunResult.SkippedNoAuth

        var failures = 0

        dao.getPendingPhotoDownloads().forEach { entity ->
            try {
                val target = photoFile(entity.photoName)
                if (target.isFile && target.length() > 0) {
                    dao.markPhotoSynced(entity.id)
                } else {
                    if (target.isFile) target.delete()
                    val bytes = api.downloadPhoto(remotePhotoPath(userId, entity.id))
                    require(bytes.isNotEmpty()) { "empty photo payload" }
                    writeAtomically(target, bytes)
                    dao.markPhotoSynced(entity.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failures++
            }
        }

        return SyncRunResult.Completed(failures)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parent, "${target.name}.tmp")
        try {
            tmp.writeBytes(bytes)
            require(tmp.renameTo(target)) { "photo rename failed" }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        }
    }

    private fun isRemoteNotFound(e: Exception): Boolean {
        val raw = e.message.orEmpty().lowercase()
        return raw.contains("not found") ||
            raw.contains("does not exist") ||
            raw.contains("404")
    }
}
