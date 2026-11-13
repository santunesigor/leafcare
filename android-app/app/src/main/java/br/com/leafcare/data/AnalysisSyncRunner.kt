package br.com.leafcare.data

import kotlinx.coroutines.CancellationException
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
 * - Training withdrawals (consent, authorizations, training copies) run first,
 *   with or without an upload policy, even while uploads are paused.
 * - Deletes tombstoned rows remotely (photo first), then removes them
 *   (and the photo) locally; a paused or unknown policy keeps them pending.
 * - A failed delete keeps its tombstone and retries through the delete path,
 *   so a deleted analysis can never be resurrected by a later upsert.
 * - Network/auth failures never touch local data beyond the ERROR state.
 */
internal data class UploadPolicy(val enabled: Boolean = true, val cutoffMs: Long? = null) {
    fun includes(createdAt: Long): Boolean = cutoffMs == null || createdAt > cutoffMs
}

internal class AnalysisSyncRunner(
    private val dao: AnalysisDao,
    private val api: AnalysisSyncApi,
    private val appVersion: String,
    private val uploadPolicy: suspend () -> UploadPolicy = { UploadPolicy() },
    private val photoDeleter: (photoName: String) -> Unit = {},
    private val photoFile: (photoName: String) -> File = { throw IllegalStateException("no photo storage") },
    private val consentDao: TrainingConsentDao? = null,
    private val trainingApi: TrainingConsentSyncApi? = null,
    /** Signed-in user right now; restore aborts when it no longer matches the pass. */
    private val sessionUserId: (() -> String?)? = null,
) {
    suspend fun syncOnce(userId: String?): SyncRunResult {
        if (userId == null) return SyncRunResult.SkippedNoAuth
        var failures = 0

        // Withdrawals first and unconditionally: they need no upload policy and
        // are accepted by the server even while uploads are paused.
        val consent = syncConsent(userId)
        failures += consent.failures
        failures += syncRevocations(userId)

        // Unknown policy (offline, or null while the account deletion is pending)
        // fails closed like a pause: tombstones and uploads wait.
        val policy = try { uploadPolicy() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        failures += syncDeletes(userId, removeAnalyses = policy?.enabled == true)
        if (policy == null) return SyncRunResult.Completed(failures + 1)
        if (!policy.enabled) return SyncRunResult.Completed(failures)

        dao.getPendingUploads().forEach { entity ->
            if (!policy.includes(entity.createdAt)) {
                dao.markLocalOnly(entity.id)
                return@forEach
            }
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

        // After the history upserts: the server only accepts training copies
        // of analyses that already exist remotely.
        if (consent.trainingAllowed) failures += uploadTrainingPhotos(userId, consent.termsVersion, policy)

        dao.getPendingPhotoUploads().forEach { entity ->
            if (!policy.includes(entity.createdAt)) {
                dao.markPhotoLocalOnly(entity.id)
                return@forEach
            }
            if (!entity.canUploadPhoto()) return@forEach
            try {
                val path = remotePhotoPath(userId, entity.id)
                api.uploadPhoto(path, photoFile(entity.photoName).readBytes())
                check(api.updatePhotoPath(entity.id, path)) { "photo path not applied" }
                dao.markPhotoSynced(entity.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Missing local file included: retryable, never a crash.
                dao.markPhotoError(entity.id)
                failures++
            }
        }

        return SyncRunResult.Completed(failures)
    }

    /**
     * Tombstoned rows. The training copy is always removed (also while paused);
     * the analysis photo and the remote tombstone only when [removeAnalyses]
     * (uploads enabled): a paused account keeps them pending until release.
     */
    private suspend fun syncDeletes(userId: String, removeAnalyses: Boolean): Int {
        var failures = 0
        dao.getPendingDeletes().forEach { entity ->
            try {
                removeTrainingCopy(userId, entity.id, requireSettled = removeAnalyses)
                if (!removeAnalyses) return@forEach
                // Any conclusive photo may have been uploaded (even if a later step
                // failed); deleting a missing object is harmless.
                if (entity.canUploadPhoto() ||
                    entity.photoSyncStatus == PhotoSyncState.SYNCED ||
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
                if (!api.markRemoteDeleted(entity.id, Instant.fromEpochMilliseconds(deletedAt).toString())) {
                    // 0 rows: fine when the row never reached the server (or is already
                    // tombstoned); an active remote row means the update was refused.
                    check(!api.isRemoteActive(entity.id)) { "remote tombstone not applied" }
                }
                dao.delete(entity.id)
                photoDeleter(entity.photoName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                dao.markError(entity.id)
                failures++
            }
        }
        return failures
    }

    /**
     * A local authorization is revoked through [syncRevocations] (already run in
     * this pass); without one the copy may come from another device, so the
     * object is deleted directly.
     */
    private suspend fun removeTrainingCopy(userId: String, analysisId: String, requireSettled: Boolean) {
        val consentDao = consentDao ?: return
        val consentApi = trainingApi ?: return
        val authorization = consentDao.getPhoto(userId, analysisId)
        if (authorization == null) {
            consentApi.delete(listOf(trainingPhotoPath(userId, analysisId)))
        } else if (requireSettled) {
            check(authorization.syncState != "PENDING") { "training revocation pending" }
        }
    }

    private data class ConsentPass(val failures: Int, val trainingAllowed: Boolean, val termsVersion: String?)

    /**
     * Withdrawals only (consent, photo authorizations, training copies of deleted
     * analyses): used while the legal documents await re-acceptance and on logout.
     * Never uploads and never touches the analysis history.
     */
    suspend fun syncTrainingRevocationOnly(userId: String?): SyncRunResult {
        if (userId == null) return SyncRunResult.SkippedNoAuth
        var failures = 0
        val local = consentDao?.get(userId)
        if (local != null && !isConsentActive(local)) failures += syncConsent(userId).failures
        failures += syncRevocations(userId)
        failures += syncDeletes(userId, removeAnalyses = false)
        return SyncRunResult.Completed(failures)
    }

    private fun isConsentActive(consent: TrainingConsentEntity?): Boolean =
        TRAINING_UPLOADS_ENABLED && consent?.granted == true && consent.termsVersion == TRAINING_TERMS_VERSION

    private suspend fun syncConsent(userId: String): ConsentPass {
        val consentDao = consentDao ?: return ConsentPass(0, false, null)
        val consentApi = trainingApi ?: return ConsentPass(0, false, null)
        val localConsent = consentDao.get(userId) ?: return ConsentPass(0, false, null)
        val consentActive = isConsentActive(localConsent)
        // Local first: the photo revocations are pushed even if this request fails.
        if (!consentActive) consentDao.revokeAllPhotos(userId)
        try {
            // A granted decision already synced is never re-sent: an old device
            // must not overwrite a revocation made elsewhere. Revocations always are.
            if (localConsent.syncState == "PENDING" || !consentActive) {
                consentApi.saveConsent(userId, localConsent.copy(granted = consentActive))
                consentDao.markConsentSynced(userId)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { return ConsentPass(1, false, null) }
        return ConsentPass(0, consentActive, localConsent.termsVersion)
    }

    /**
     * Settles authorizations that can never be honored, then pushes every pending
     * revocation whatever the current consent (a re-acceptance must not drop them).
     */
    private suspend fun syncRevocations(userId: String): Int {
        val consentDao = consentDao ?: return 0
        trainingApi ?: return 0
        dao.getPendingDeletes().forEach { consentDao.revokePhoto(userId, it.id) }
        consentDao.getPendingPhotos(userId).filter { it.authorized }.forEach { authorization ->
            val analysis = dao.get(authorization.analysisId)
            when {
                // Excluded by the cutoff: it never reaches the server, nothing to record there.
                analysis?.syncStatus == SyncState.LOCAL_ONLY ->
                    consentDao.deletePhoto(userId, authorization.analysisId)
                // Deleted or inconclusive analyses are never used for training.
                analysis == null || analysis.deletedAt != null || analysis.inconclusive ->
                    consentDao.revokePhoto(userId, authorization.analysisId)
            }
        }
        var failures = 0
        // Each photo on its own: one failure must not keep other copies on the server.
        consentDao.getPendingRevocations(userId).forEach { if (!revokeRemotely(userId, it)) failures++ }
        return failures
    }

    /** Sends authorized=false and removes the training copy. True once settled locally. */
    private suspend fun revokeRemotely(userId: String, authorization: TrainingPhotoAuthorizationEntity): Boolean {
        val consentDao = consentDao ?: return true
        val consentApi = trainingApi ?: return true
        val saved = try {
            consentApi.saveAuthorization(userId, authorization.copy(authorized = false))
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        // Rejected because the analysis never reached the server (or is gone there):
        // there is no remote authorization to update, so retrying would fail forever.
        val moot = !saved && try { !api.isRemoteActive(authorization.analysisId) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        val removed = try {
            consentApi.delete(listOf(trainingPhotoPath(userId, authorization.analysisId)))
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
        if (!removed) return false
        when {
            saved -> consentDao.markPhotoSynced(userId, authorization.analysisId)
            moot -> consentDao.deletePhoto(userId, authorization.analysisId)
            else -> return false
        }
        return true
    }

    private suspend fun uploadTrainingPhotos(userId: String, termsVersion: String?, policy: UploadPolicy): Int {
        val consentDao = consentDao ?: return 0
        val consentApi = trainingApi ?: return 0
        var failures = 0
        consentDao.getPendingPhotos(userId).filter { it.authorized && it.userId == userId && it.termsVersion == termsVersion }.forEach { authorization ->
            val analysis = dao.get(authorization.analysisId)
            // Only active, conclusive analyses already on the server; the others were
            // settled by the revocation pass or wait for their own upload.
            if (analysis == null || analysis.deletedAt != null || analysis.inconclusive ||
                analysis.syncStatus != SyncState.SYNCED
            ) return@forEach
            if (!policy.includes(analysis.createdAt)) {
                // Same cutoff as the analysis photo: captured before the release, never sent.
                consentDao.revokePhoto(userId, authorization.analysisId)
                if (!revokeRemotely(userId, authorization)) failures++
                return@forEach
            }
            try {
                consentApi.saveAuthorization(userId, authorization)
                consentApi.upload(trainingPhotoPath(userId, authorization.analysisId), photoFile(analysis.photoName).readBytes())
                consentDao.markPhotoSynced(userId, authorization.analysisId)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failures++ }
        }
        return failures
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
     * Malformed remote rows are skipped without failing the restore; the pass
     * stops when the signed-in account changes after the fetch.
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
            // Logout or account switch after the fetch: never write rows as another account.
            if (sessionUserId != null && sessionUserId.invoke() != userId) return SyncRunResult.Completed(0)
            try {
                if (row["user_id"]?.jsonPrimitive?.content != userId) return@forEach
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
                } else if (local.deletedAt == null && local.syncStatus == SyncState.SYNCED &&
                    local.photoSyncStatus == PhotoSyncState.LOCAL_ONLY &&
                    entity.photoSyncStatus == PhotoSyncState.REMOTE_ONLY
                ) {
                    // A previously absent photo appeared remotely; preserve local analysis.
                    dao.markPhotoRemoteOnly(local.id)
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
