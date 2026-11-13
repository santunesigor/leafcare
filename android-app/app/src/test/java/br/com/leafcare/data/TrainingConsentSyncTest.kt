package br.com.leafcare.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.Files

private class ConsentDaoFake(var consent: TrainingConsentEntity?, rows: List<TrainingPhotoAuthorizationEntity>) : TrainingConsentDao {
    private val photos = rows.associateBy { it.userId to it.analysisId }.toMutableMap()
    override fun observe(userId: String): Flow<TrainingConsentEntity?> = emptyFlow()
    override suspend fun get(userId: String) = consent?.takeIf { it.userId == userId }
    override suspend fun save(consent: TrainingConsentEntity) { this.consent = consent }
    override suspend fun getPendingPhotos(userId: String) = photos.values.filter { it.userId == userId && it.syncState == "PENDING" }
    override suspend fun getAllPhotos(userId: String) = photos.values.filter { it.userId == userId }
    override suspend fun revokeAllPhotos(userId: String) { photos.replaceAll { _, row -> if (row.userId == userId && row.authorized) row.copy(authorized = false, syncState = "PENDING") else row } }
    override suspend fun savePhoto(authorization: TrainingPhotoAuthorizationEntity) { photos[authorization.userId to authorization.analysisId] = authorization }
    override suspend fun markConsentSynced(userId: String) { if (consent?.userId == userId) consent = consent?.copy(syncState = "SYNCED") }
    override suspend fun markPhotoSynced(userId: String, id: String) { photos[userId to id]?.let { photos[userId to id] = it.copy(syncState = "SYNCED") } }
    override suspend fun hasConsentRows() = consent != null
    override suspend fun getPendingRevocations(userId: String) = photos.values.filter { it.userId == userId && it.syncState == "PENDING" && !it.authorized }
    override suspend fun getPhoto(userId: String, id: String) = photos[userId to id]
    override suspend fun revokePhoto(userId: String, id: String) { photos[userId to id]?.takeIf { it.authorized }?.let { photos[userId to id] = it.copy(authorized = false, syncState = "PENDING") } }
    override suspend fun deletePhoto(userId: String, id: String) { photos.remove(userId to id) }
}

private class ConsentApiFake(
    private val events: MutableList<String>,
    private val failConsent: Boolean = false,
    private val failAuthorizationFor: String? = null,
) : TrainingConsentSyncApi {
    override suspend fun saveConsent(userId: String, consent: TrainingConsentEntity) { events += "consent:${consent.granted}"; if (failConsent) throw IOException("offline") }
    override suspend fun saveAuthorization(userId: String, authorization: TrainingPhotoAuthorizationEntity) {
        events += "photo-auth:${authorization.authorized}"
        if (authorization.analysisId == failAuthorizationFor) throw IOException("rls")
    }
    override suspend fun upload(path: String, bytes: ByteArray) { events += "upload" }
    override suspend fun delete(paths: List<String>) { events += "delete:${paths.joinToString()}" }
}

class TrainingConsentSyncTest {
    @Test fun revocationIsSentBeforeAnyTrainingUpload() = runTest {
        val events = mutableListOf<String>()
        val consent = ConsentDaoFake(
            TrainingConsentEntity("user-1", granted = false, decidedAt = 10),
            listOf(TrainingPhotoAuthorizationEntity("user-1", "a-1", TRAINING_TERMS_VERSION, true, 5)),
        )
        val row = AnalysisEntity("a-1", "a-1.img", 10, "x", "x", "x", .9f, "[]", false, .5f, 1.0, "hash", SyncState.SYNCED, null, PhotoSyncState.LOCAL_ONLY)
        val runner = AnalysisSyncRunner(
            FakeAnalysisDao(listOf(row)), FakeAnalysisSyncApi(), "test", consentDao = consent,
            trainingApi = ConsentApiFake(events),
        )
        runner.syncOnce("user-1")
        assertEquals(listOf("consent:false", "photo-auth:false", "delete:${trainingPhotoPath("user-1", "a-1")}"), events)
        assertFalse(consent.getAllPhotos("user-1").single().authorized)
    }

    @Test fun refusalDoesNotBlockNormalAnalysisHistorySync() = runTest {
        val events = mutableListOf<String>()
        val row = AnalysisEntity("history-1", "history-1.img", 10, "x", "x", "x", .9f, "[]", false, .5f, 1.0, "hash")
        val dao = FakeAnalysisDao(listOf(row))
        val historyApi = FakeAnalysisSyncApi()
        val runner = AnalysisSyncRunner(
            dao, historyApi, "test",
            consentDao = ConsentDaoFake(TrainingConsentEntity("user-1", granted = false, decidedAt = 10), emptyList()),
            trainingApi = ConsentApiFake(events),
        )
        runner.syncOnce("user-1")
        assertEquals(SyncState.SYNCED, dao.get(row.id)?.syncStatus)
        assertEquals("consent:false", events.first())
        assertTrue(historyApi.remote.containsKey("history-1"))
        assertFalse(events.contains("upload"))
    }

    @Test fun explicitlyAuthorizedImageUploadsAfterRemoteConsentIsConfirmed() = runTest {
        assertTrue(TRAINING_UPLOADS_ENABLED)
        val events = mutableListOf<String>()
        val image = Files.createTempFile("training", ".jpg").toFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        try {
            val row = AnalysisEntity("a-1", "a-1.img", 10, "x", "x", "x", .9f, "[]", false, .5f, 1.0, "hash", SyncState.SYNCED, null, PhotoSyncState.LOCAL_ONLY)
            val runner = AnalysisSyncRunner(
                FakeAnalysisDao(listOf(row)), FakeAnalysisSyncApi(), "test",
                photoFile = { image },
                consentDao = ConsentDaoFake(
                    TrainingConsentEntity("user-1", granted = true, decidedAt = 10),
                    listOf(TrainingPhotoAuthorizationEntity("user-1", "a-1", TRAINING_TERMS_VERSION, true, 11)),
                ),
                trainingApi = ConsentApiFake(events),
            )
            runner.syncOnce("user-1")
            assertEquals(listOf("consent:true", "photo-auth:true", "upload"), events)
            assertTrue(events.contains("upload"))
        } finally { image.delete() }
    }

    @Test fun failedRemoteConsentConfirmationKeepsPhotoPending() = runTest {
        val events = mutableListOf<String>()
        val image = Files.createTempFile("training-offline", ".jpg").toFile().apply { writeBytes(byteArrayOf(1)) }
        val localConsent = ConsentDaoFake(
            TrainingConsentEntity("user-1", granted = true, decidedAt = 10),
            listOf(TrainingPhotoAuthorizationEntity("user-1", "a-1", TRAINING_TERMS_VERSION, true, 11)),
        )
        try {
            val row = AnalysisEntity("a-1", "a-1.img", 10, "x", "x", "x", .9f, "[]", false, .5f, 1.0, "hash", SyncState.SYNCED, null, PhotoSyncState.LOCAL_ONLY)
            val result = AnalysisSyncRunner(
                FakeAnalysisDao(listOf(row)), FakeAnalysisSyncApi(), "test", photoFile = { image },
                consentDao = localConsent, trainingApi = ConsentApiFake(events, failConsent = true),
            ).syncOnce("user-1")
            assertEquals(SyncRunResult.Completed(1), result)
            assertEquals(listOf("consent:true"), events)
            assertEquals("PENDING", localConsent.getPendingPhotos("user-1").single().syncState)
        } finally { image.delete() }
    }

    @Test fun oneFailedRevocationDoesNotKeepOtherTrainingCopies() = runTest {
        val events = mutableListOf<String>()
        val consent = ConsentDaoFake(
            TrainingConsentEntity("user-1", granted = false, decidedAt = 10),
            listOf(
                TrainingPhotoAuthorizationEntity("user-1", "gone", TRAINING_TERMS_VERSION, true, 5),
                TrainingPhotoAuthorizationEntity("user-1", "kept", TRAINING_TERMS_VERSION, true, 6),
            ),
        )
        // Both analyses are active remotely, so the rejected revocation must be retried.
        val historyApi = FakeAnalysisSyncApi().apply {
            remote["gone"] = JsonObject(emptyMap()); remote["kept"] = JsonObject(emptyMap())
        }
        val result = AnalysisSyncRunner(
            FakeAnalysisDao(), historyApi, "test", consentDao = consent,
            trainingApi = ConsentApiFake(events, failAuthorizationFor = "gone"),
        ).syncOnce("user-1")
        assertEquals(SyncRunResult.Completed(1), result)
        assertTrue(events.contains("delete:${trainingPhotoPath("user-1", "gone")}"))
        assertTrue(events.contains("delete:${trainingPhotoPath("user-1", "kept")}"))
        assertEquals(listOf("gone"), consent.getPendingPhotos("user-1").map { it.analysisId })
    }

    @Test fun syncedGrantIsNotResentOverRemoteRevocation() = runTest {
        val events = mutableListOf<String>()
        val runner = AnalysisSyncRunner(
            FakeAnalysisDao(), FakeAnalysisSyncApi(), "test",
            consentDao = ConsentDaoFake(TrainingConsentEntity("user-1", granted = true, decidedAt = 10, syncState = "SYNCED"), emptyList()),
            trainingApi = ConsentApiFake(events),
        )
        runner.syncOnce("user-1")
        assertFalse(events.any { it.startsWith("consent:") })
    }

    @Test fun revocationOnlySyncsWithdrawalWithoutHistory() = runTest {
        val events = mutableListOf<String>()
        val row = AnalysisEntity("history-1", "history-1.img", 10, "x", "x", "x", .9f, "[]", false, .5f, 1.0, "hash")
        val dao = FakeAnalysisDao(listOf(row))
        val historyApi = FakeAnalysisSyncApi()
        val runner = AnalysisSyncRunner(
            dao, historyApi, "test",
            consentDao = ConsentDaoFake(
                TrainingConsentEntity("user-1", granted = false, decidedAt = 10),
                listOf(TrainingPhotoAuthorizationEntity("user-1", "a-1", TRAINING_TERMS_VERSION, true, 5)),
            ),
            trainingApi = ConsentApiFake(events),
        )
        assertEquals(SyncRunResult.Completed(0), runner.syncTrainingRevocationOnly("user-1"))
        assertEquals("consent:false", events.first())
        assertTrue(events.any { it.startsWith("delete:") })
        assertFalse(historyApi.remote.containsKey("history-1"))
    }

    @Test fun authorizedPhotoUploadsAfterItsAnalysisInTheSamePass() = runTest {
        val events = mutableListOf<String>()
        val image = Files.createTempFile("training-order", ".jpg").toFile().apply { writeBytes(byteArrayOf(1)) }
        try {
            val row = AnalysisEntity("a-1", "a-1.img", 10, "x", "x", "x", .9f, "[]", false, .5f, 1.0, "hash")
            val historyApi = FakeAnalysisSyncApi()
            val api = object : AnalysisSyncApi by historyApi {
                override suspend fun upsertAnalysis(row: kotlinx.serialization.json.JsonObject) {
                    events += "history"; historyApi.upsertAnalysis(row)
                }
            }
            AnalysisSyncRunner(
                FakeAnalysisDao(listOf(row)), api, "test", photoFile = { image },
                consentDao = ConsentDaoFake(
                    TrainingConsentEntity("user-1", granted = true, decidedAt = 10),
                    listOf(TrainingPhotoAuthorizationEntity("user-1", "a-1", TRAINING_TERMS_VERSION, true, 11)),
                ),
                trainingApi = ConsentApiFake(events),
            ).syncOnce("user-1")
            assertTrue(events.indexOf("history") < events.indexOf("upload"))
        } finally { image.delete() }
    }

    private fun analysis(
        id: String,
        syncStatus: SyncState = SyncState.SYNCED,
        deletedAt: Long? = null,
        inconclusive: Boolean = false,
        createdAt: Long = 10,
    ) = AnalysisEntity(id, "$id.img", createdAt, "x", "x", "x", .9f, "[]", inconclusive, .5f, 1.0, "hash", syncStatus, deletedAt, PhotoSyncState.LOCAL_ONLY)

    private fun grantedConsent(vararg rows: TrainingPhotoAuthorizationEntity) = ConsentDaoFake(
        TrainingConsentEntity("user-1", granted = true, decidedAt = 10, syncState = "SYNCED"), rows.toList(),
    )

    private fun authorized(id: String, syncState: String = "PENDING") =
        TrainingPhotoAuthorizationEntity("user-1", id, TRAINING_TERMS_VERSION, true, 11, syncState)

    private fun remoteWith(vararg ids: String) = FakeAnalysisSyncApi().apply { ids.forEach { remote[it] = JsonObject(emptyMap()) } }

    @Test fun deletedAnalysisNeverUploadsTrainingCopyAndRevokesIt() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(authorized("a-1"))
        val api = remoteWith("a-1")
        val dao = FakeAnalysisDao(listOf(analysis("a-1", SyncState.PENDING_DELETE, deletedAt = 20)))
        val result = AnalysisSyncRunner(
            dao, api, "test", photoFile = { error("training photo must not be read") },
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(listOf("photo-auth:false", "delete:${trainingPhotoPath("user-1", "a-1")}"), events)
        assertEquals(listOf("a-1"), api.deleteCalls)
        assertNull(dao.get("a-1"))
        val row = consent.getPhoto("user-1", "a-1")!!
        assertFalse(row.authorized)
        assertEquals("SYNCED", row.syncState)
    }

    @Test fun syncedCopyOfDeletedAnalysisIsRevokedAndRemoved() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(authorized("a-1", syncState = "SYNCED"))
        val dao = FakeAnalysisDao(listOf(analysis("a-1", SyncState.PENDING_DELETE, deletedAt = 20)))
        AnalysisSyncRunner(dao, remoteWith("a-1"), "test", consentDao = consent, trainingApi = ConsentApiFake(events)).syncOnce("user-1")
        assertEquals(listOf("photo-auth:false", "delete:${trainingPhotoPath("user-1", "a-1")}"), events)
        assertNull(dao.get("a-1"))
    }

    @Test fun deletedAnalysisWithoutLocalAuthorizationStillDeletesTrainingCopy() = runTest {
        val events = mutableListOf<String>()
        val dao = FakeAnalysisDao(listOf(analysis("a-1", SyncState.PENDING_DELETE, deletedAt = 20)))
        AnalysisSyncRunner(dao, remoteWith("a-1"), "test", consentDao = grantedConsent(), trainingApi = ConsentApiFake(events)).syncOnce("user-1")
        assertEquals(listOf("delete:${trainingPhotoPath("user-1", "a-1")}"), events)
        assertNull(dao.get("a-1"))
    }

    @Test fun pausedAccountRevokesTrainingCopyButKeepsTombstonePending() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(authorized("a-1", syncState = "SYNCED"))
        val api = remoteWith("a-1")
        val dao = FakeAnalysisDao(listOf(analysis("a-1", SyncState.PENDING_DELETE, deletedAt = 20)))
        val result = AnalysisSyncRunner(
            dao, api, "test", uploadPolicy = { UploadPolicy(enabled = false) },
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(listOf("photo-auth:false", "delete:${trainingPhotoPath("user-1", "a-1")}"), events)
        assertTrue(api.deleteCalls.isEmpty())
        assertTrue(api.photoDeleteCalls.isEmpty())
        assertEquals(SyncState.PENDING_DELETE, dao.get("a-1")?.syncStatus)
    }

    @Test fun unknownPolicyStillPushesRevocations() = runTest {
        val events = mutableListOf<String>()
        val consent = ConsentDaoFake(TrainingConsentEntity("user-1", granted = false, decidedAt = 10), listOf(authorized("a-1")))
        val result = AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("a-1"))), remoteWith("a-1"), "test",
            uploadPolicy = { error("my_account_policy returned null") },
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertEquals(SyncRunResult.Completed(1), result)
        assertEquals(listOf("consent:false", "photo-auth:false", "delete:${trainingPhotoPath("user-1", "a-1")}"), events)
    }

    @Test fun inconclusiveAnalysisIsNeverUploadedForTraining() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(authorized("a-1"))
        AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("a-1", inconclusive = true))), remoteWith("a-1"), "test",
            photoFile = { error("training photo must not be read") },
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertFalse(events.contains("upload"))
        assertFalse(events.contains("photo-auth:true"))
        assertFalse(consent.getPhoto("user-1", "a-1")!!.authorized)
    }

    @Test fun authorizationOfAnalysisThatNeverReachedServerIsDroppedWithoutRetries() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(authorized("missing"), authorized("local"))
        // "missing" was removed locally without ever being uploaded: the server rejects it.
        val result = AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("local", SyncState.LOCAL_ONLY))), FakeAnalysisSyncApi(), "test",
            consentDao = consent, trainingApi = ConsentApiFake(events, failAuthorizationFor = "missing"),
        ).syncOnce("user-1")
        assertEquals(SyncRunResult.Completed(0), result)
        assertTrue(consent.getAllPhotos("user-1").isEmpty())
        assertFalse(events.contains("upload"))
    }

    @Test fun unsyncedAnalysisAuthorizationWaitsWithoutFailure() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(authorized("a-1"))
        val result = AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("a-1", SyncState.PENDING_UPLOAD))), FakeAnalysisSyncApi(), "test",
            uploadPolicy = { UploadPolicy(enabled = false) },
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertEquals(SyncRunResult.Completed(0), result)
        assertTrue(events.isEmpty())
        assertTrue(consent.getPhoto("user-1", "a-1")!!.authorized)
    }

    @Test fun trainingCopyBeforeCutoffIsNeverUploaded() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(authorized("a-1"))
        AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("a-1", createdAt = 10))), remoteWith("a-1"), "test",
            uploadPolicy = { UploadPolicy(cutoffMs = 10) },
            photoFile = { error("training photo must not be read") },
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertFalse(events.contains("upload"))
        assertFalse(events.contains("photo-auth:true"))
        assertFalse(consent.getPhoto("user-1", "a-1")!!.authorized)
    }

    @Test fun reacceptanceDoesNotDropPendingPhotoRevocation() = runTest {
        val events = mutableListOf<String>()
        // Revoked, then accepted again before the sync ran: consent is granted again.
        val consent = grantedConsent(TrainingPhotoAuthorizationEntity("user-1", "a-1", TRAINING_TERMS_VERSION, false, 12))
        AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("a-1"))), remoteWith("a-1"), "test",
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertEquals(listOf("photo-auth:false", "delete:${trainingPhotoPath("user-1", "a-1")}"), events)
        assertEquals("SYNCED", consent.getPhoto("user-1", "a-1")!!.syncState)
        // Already settled revocations are not re-sent on later passes.
        events.clear()
        AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("a-1"))), remoteWith("a-1"), "test",
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncOnce("user-1")
        assertTrue(events.isEmpty())
    }

    @Test fun revocationOnlyPushesPhotoRevocationsEvenWithActiveConsent() = runTest {
        val events = mutableListOf<String>()
        val consent = grantedConsent(TrainingPhotoAuthorizationEntity("user-1", "a-1", TRAINING_TERMS_VERSION, false, 12))
        val api = remoteWith("a-1")
        val result = AnalysisSyncRunner(
            FakeAnalysisDao(listOf(analysis("a-1", SyncState.PENDING_UPLOAD))), api, "test",
            consentDao = consent, trainingApi = ConsentApiFake(events),
        ).syncTrainingRevocationOnly("user-1")
        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(listOf("photo-auth:false", "delete:${trainingPhotoPath("user-1", "a-1")}"), events)
        assertTrue(api.upsertCalls.isEmpty())
    }
}
