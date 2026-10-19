package br.com.leafcare.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
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
}

private class ConsentApiFake(private val events: MutableList<String>, private val failConsent: Boolean = false) : TrainingConsentSyncApi {
    override suspend fun saveConsent(userId: String, consent: TrainingConsentEntity) { events += "consent:${consent.granted}"; if (failConsent) throw IOException("offline") }
    override suspend fun saveAuthorization(userId: String, authorization: TrainingPhotoAuthorizationEntity) { events += "photo-auth:${authorization.authorized}" }
    override suspend fun upload(path: String, bytes: ByteArray) { events += "upload" }
    override suspend fun delete(paths: List<String>) { events += "delete" }
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
        assertEquals(listOf("consent:false", "photo-auth:false", "delete"), events)
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

    @Test fun pendingImageNeverUploadsWhileLegalGateIsDisabled() = runTest {
        assertFalse(TRAINING_UPLOADS_ENABLED)
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
            assertEquals(listOf("consent:false", "photo-auth:false", "delete"), events)
            assertFalse(events.contains("upload"))
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
            assertEquals(listOf("consent:false"), events)
            assertEquals("PENDING", localConsent.getPendingPhotos("user-1").single().syncState)
        } finally { image.delete() }
    }
}
