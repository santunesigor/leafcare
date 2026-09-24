package br.com.leafcare.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** In-memory [AnalysisDao]: mirrors the Room WHERE clauses for queue semantics. */
internal class FakeAnalysisDao(initial: List<AnalysisEntity> = emptyList()) : AnalysisDao {

    private val rows = initial.associateBy { it.id }.toMutableMap()
    private val changes = MutableStateFlow(0)

    override fun observeAll(): Flow<List<AnalysisEntity>> =
        changes.map { rows.values.filter { it.deletedAt == null }.sortedByDescending { it.createdAt } }

    override fun observe(id: String): Flow<AnalysisEntity?> =
        changes.map { rows[id] }

    override suspend fun get(id: String): AnalysisEntity? = rows[id]

    override suspend fun insert(analysis: AnalysisEntity) {
        rows[analysis.id] = analysis
        changes.value++
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
        changes.value++
    }

    override suspend fun getPendingUploads(): List<AnalysisEntity> =
        rows.values.filter {
            (it.syncStatus == SyncState.PENDING_UPLOAD || it.syncStatus == SyncState.ERROR) &&
                it.deletedAt == null
        }

    override suspend fun getPendingDeletes(): List<AnalysisEntity> =
        rows.values.filter {
            it.syncStatus == SyncState.PENDING_DELETE ||
                (it.syncStatus == SyncState.ERROR && it.deletedAt != null)
        }

    override suspend fun markSynced(id: String) {
        update(id) { it.copy(syncStatus = SyncState.SYNCED) }
    }

    override suspend fun markError(id: String) {
        update(id) { it.copy(syncStatus = SyncState.ERROR) }
    }

    override suspend fun markDeleted(id: String, deletedAt: Long) {
        update(id) { it.copy(syncStatus = SyncState.PENDING_DELETE, deletedAt = deletedAt) }
    }

    override suspend fun getPendingPhotoUploads(): List<AnalysisEntity> =
        rows.values.filter {
            (it.photoSyncStatus == PhotoSyncState.PENDING_UPLOAD || it.photoSyncStatus == PhotoSyncState.ERROR) &&
                it.deletedAt == null &&
                it.syncStatus == SyncState.SYNCED
        }

    override suspend fun markPhotoSynced(id: String) {
        update(id) { it.copy(photoSyncStatus = PhotoSyncState.SYNCED) }
    }

    override suspend fun markPhotoError(id: String) {
        update(id) { it.copy(photoSyncStatus = PhotoSyncState.ERROR) }
    }

    private fun update(id: String, transform: (AnalysisEntity) -> AnalysisEntity) {
        rows[id]?.let {
            rows[id] = transform(it)
            changes.value++
        }
    }
}

/** In-memory remote: one record per UUID, so duplicates are observable. */
internal class FakeAnalysisSyncApi(
    var failUpsert: Boolean = false,
    var failDelete: Boolean = false,
    var failUpload: Boolean = false,
    var failPhotoDelete: Boolean = false,
    var photoDeleteNotFound: Boolean = false,
) : AnalysisSyncApi {

    val remote = mutableMapOf<String, JsonObject>()
    val remotePhotos = mutableMapOf<String, ByteArray>()
    val upsertCalls = mutableListOf<String>()
    val deleteCalls = mutableListOf<String>()
    val uploadCalls = mutableListOf<String>()
    val photoPathUpdates = mutableListOf<Pair<String, String>>()
    val photoDeleteCalls = mutableListOf<String>()

    override suspend fun upsertAnalysis(row: JsonObject) {
        if (failUpsert) throw IOException("offline")
        val id = row.getValue("id").toString().trim('"')
        upsertCalls += id
        remote[id] = row
    }

    override suspend fun markRemoteDeleted(id: String, deletedAtIso: String) {
        if (failDelete) throw IOException("offline")
        deleteCalls += id
        remote.remove(id)
    }

    override suspend fun uploadPhoto(path: String, bytes: ByteArray) {
        if (failUpload) throw IOException("offline")
        uploadCalls += path
        remotePhotos[path] = bytes
    }

    override suspend fun updatePhotoPath(id: String, photoPath: String) {
        photoPathUpdates += id to photoPath
    }

    override suspend fun deleteRemotePhotos(paths: List<String>) {
        if (photoDeleteNotFound) throw IOException("404 object not found")
        if (failPhotoDelete) throw IOException("offline")
        photoDeleteCalls += paths
        paths.forEach { remotePhotos.remove(it) }
    }
}

/**
 * Sync engine tests with fakes: no network, no Android framework.
 * Covers queue states, idempotency, tombstones and the no-auth gate.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisSyncRunnerTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var photosDir: File

    @Before
    fun setUp() {
        photosDir = Files.createTempDirectory("leafcare-photos").toFile()
    }

    @After
    fun tearDown() {
        photosDir.deleteRecursively()
    }

    private fun entity(
        id: String = "a-1",
        syncStatus: SyncState = SyncState.PENDING_UPLOAD,
        deletedAt: Long? = null,
        photoSyncStatus: PhotoSyncState = PhotoSyncState.PENDING_UPLOAD,
    ) = AnalysisEntity(
        id = id,
        photoName = "$id.img",
        createdAt = 1_728_000_000_000,
        classId = "frog_eye",
        displayName = "Olho-de-rã",
        scientificName = "Cercospora nicotianae",
        confidence = 0.82f,
        top3Json = """[{"class_id":"frog_eye","confidence":0.82}]""",
        inconclusive = false,
        threshold = 0.7f,
        inferenceMs = 30.0,
        modelSha256 = "abc123",
        syncStatus = syncStatus,
        deletedAt = deletedAt,
        photoSyncStatus = photoSyncStatus
    )

    /** Creates a local photo file for the test. */
    private fun localPhoto(name: String, bytes: ByteArray = "fake-jpeg".toByteArray()): File =
        File(photosDir, name).apply { writeBytes(bytes) }

    private fun runner(
        dao: FakeAnalysisDao,
        api: FakeAnalysisSyncApi,
        deletedPhotos: MutableList<String> = mutableListOf(),
    ) = AnalysisSyncRunner(
        dao = dao,
        api = api,
        appVersion = "test",
        photoDeleter = { deletedPhotos += it },
        photoFile = { name -> File(photosDir, name) }
    )

    @Test fun newAnalysisUploadsWithSameUuidAndMarksSynced() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity()))
        val api = FakeAnalysisSyncApi()
        localPhoto("a-1.img")

        val result = runner(dao, api).syncOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(SyncState.SYNCED, dao.get("a-1")?.syncStatus)
        assertEquals(listOf("a-1"), api.upsertCalls)
        assertEquals("user-1", api.remote.getValue("a-1").getValue("user_id").toString().trim('"'))
        assertEquals(PhotoSyncState.SYNCED, dao.get("a-1")?.photoSyncStatus)
        assertEquals(listOf("user-1/a-1.jpg"), api.uploadCalls)
        assertEquals(listOf("a-1" to "user-1/a-1.jpg"), api.photoPathUpdates)
        assertTrue(File(photosDir, "a-1.img").exists())
    }

    @Test fun networkFailurePreservesLocalAndMarksError() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity(photoSyncStatus = PhotoSyncState.SYNCED)))
        val api = FakeAnalysisSyncApi(failUpsert = true)

        val result = runner(dao, api).syncOnce("user-1")

        assertEquals(SyncRunResult.Completed(1), result)
        assertNotNull(dao.get("a-1"))
        assertEquals(SyncState.ERROR, dao.get("a-1")?.syncStatus)
        assertTrue(api.remote.isEmpty())
    }

    @Test fun retryDoesNotDuplicateRemoteRecord() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity(photoSyncStatus = PhotoSyncState.SYNCED)))
        val api = FakeAnalysisSyncApi()
        val sync = runner(dao, api)

        sync.syncOnce("user-1")
        val second = sync.syncOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), second)
        assertEquals(listOf("a-1"), api.upsertCalls)
        assertEquals(1, api.remote.size)
    }

    @Test fun deleteTombstonesRemotelyThenRemovesLocally() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(
                entity(
                    syncStatus = SyncState.PENDING_DELETE,
                    deletedAt = 1_728_000_100_000,
                    photoSyncStatus = PhotoSyncState.SYNCED
                )
            )
        )
        val api = FakeAnalysisSyncApi()
        val deletedPhotos = mutableListOf<String>()

        val result = runner(dao, api, deletedPhotos).syncOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(listOf("a-1"), api.deleteCalls)
        assertEquals(listOf("user-1/a-1.jpg"), api.photoDeleteCalls)
        assertTrue(api.upsertCalls.isEmpty())
        assertNull(dao.get("a-1"))
        assertEquals(listOf("a-1.img"), deletedPhotos)
    }

    @Test fun deleteOfNeverSyncedRowRemovesLocallyWithoutUpsert() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity(syncStatus = SyncState.PENDING_DELETE, deletedAt = 1_728_000_100_000)))
        val api = FakeAnalysisSyncApi()

        runner(dao, api).syncOnce("user-1")

        assertNull(dao.get("a-1"))
        assertTrue(api.upsertCalls.isEmpty())
        assertTrue(api.remote.isEmpty())
    }

    @Test fun failedDeleteKeepsTombstoneAndRetriesThroughDeletePath() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity(syncStatus = SyncState.PENDING_DELETE, deletedAt = 1_728_000_100_000)))
        val api = FakeAnalysisSyncApi(failDelete = true)
        val sync = runner(dao, api)

        assertEquals(SyncRunResult.Completed(1), sync.syncOnce("user-1"))
        assertEquals(SyncState.ERROR, dao.get("a-1")?.syncStatus)
        assertEquals(1_728_000_100_000, dao.get("a-1")?.deletedAt)

        api.failDelete = false
        assertEquals(SyncRunResult.Completed(0), sync.syncOnce("user-1"))
        assertTrue(api.upsertCalls.isEmpty())
        assertNull(dao.get("a-1"))
    }

    @Test fun syncSkippedWithoutAuthenticatedUser() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity()))
        val api = FakeAnalysisSyncApi()

        val result = runner(dao, api).syncOnce(null)

        assertEquals(SyncRunResult.SkippedNoAuth, result)
        assertTrue(api.upsertCalls.isEmpty())
        assertTrue(api.deleteCalls.isEmpty())
        assertTrue(api.uploadCalls.isEmpty())
        assertEquals(SyncState.PENDING_UPLOAD, dao.get("a-1")?.syncStatus)
        assertEquals(PhotoSyncState.PENDING_UPLOAD, dao.get("a-1")?.photoSyncStatus)
    }

    @Test fun remotePhotoPathFormat() {
        assertEquals("user-1/a-1.jpg", remotePhotoPath("user-1", "a-1"))
    }

    @Test fun photoUploadErrorKeepsLocalPhoto() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(entity(syncStatus = SyncState.SYNCED, photoSyncStatus = PhotoSyncState.PENDING_UPLOAD))
        )
        val api = FakeAnalysisSyncApi(failUpload = true)
        localPhoto("a-1.img")

        val result = runner(dao, api).syncOnce("user-1")

        assertEquals(SyncRunResult.Completed(1), result)
        assertEquals(SyncState.SYNCED, dao.get("a-1")?.syncStatus)
        assertEquals(PhotoSyncState.ERROR, dao.get("a-1")?.photoSyncStatus)
        assertTrue(File(photosDir, "a-1.img").exists())
        assertTrue(api.photoPathUpdates.isEmpty())
    }

    @Test fun photoRetryUsesSamePathWithoutDuplicating() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(entity(syncStatus = SyncState.SYNCED, photoSyncStatus = PhotoSyncState.PENDING_UPLOAD))
        )
        val api = FakeAnalysisSyncApi(failUpload = true)
        localPhoto("a-1.img")
        val sync = runner(dao, api)

        assertEquals(SyncRunResult.Completed(1), sync.syncOnce("user-1"))
        api.failUpload = false
        assertEquals(SyncRunResult.Completed(0), sync.syncOnce("user-1"))

        assertEquals(listOf("user-1/a-1.jpg"), api.uploadCalls)
        assertEquals(1, api.remotePhotos.size)
        assertEquals(PhotoSyncState.SYNCED, dao.get("a-1")?.photoSyncStatus)
    }

    @Test fun pendingDeleteSkipsPhotoUpload() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(entity(syncStatus = SyncState.PENDING_DELETE, deletedAt = 1_728_000_100_000))
        )
        val api = FakeAnalysisSyncApi(failUpload = true)

        runner(dao, api).syncOnce("user-1")

        assertTrue(api.uploadCalls.isEmpty())
    }

    @Test fun missingLocalPhotoMarksErrorWithoutCrash() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(entity(syncStatus = SyncState.SYNCED, photoSyncStatus = PhotoSyncState.PENDING_UPLOAD))
        )
        val api = FakeAnalysisSyncApi()

        val result = runner(dao, api).syncOnce("user-1")

        assertEquals(SyncRunResult.Completed(1), result)
        assertEquals(PhotoSyncState.ERROR, dao.get("a-1")?.photoSyncStatus)
        assertEquals(SyncState.SYNCED, dao.get("a-1")?.syncStatus)
        assertNotNull(dao.get("a-1"))
    }

    @Test fun missingRemotePhotoOnDeleteIsSuccess() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(
                entity(
                    syncStatus = SyncState.PENDING_DELETE,
                    deletedAt = 1_728_000_100_000,
                    photoSyncStatus = PhotoSyncState.SYNCED
                )
            )
        )
        val api = FakeAnalysisSyncApi(photoDeleteNotFound = true)

        val result = runner(dao, api).syncOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertNull(dao.get("a-1"))
    }

    @Test fun failedRemotePhotoDeleteKeepsTombstoneRetryable() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(
                entity(
                    syncStatus = SyncState.PENDING_DELETE,
                    deletedAt = 1_728_000_100_000,
                    photoSyncStatus = PhotoSyncState.SYNCED
                )
            )
        )
        val api = FakeAnalysisSyncApi(failPhotoDelete = true)

        assertEquals(SyncRunResult.Completed(1), runner(dao, api).syncOnce("user-1"))
        assertEquals(SyncState.ERROR, dao.get("a-1")?.syncStatus)
        assertEquals(1_728_000_100_000, dao.get("a-1")?.deletedAt)
        assertTrue(api.deleteCalls.isEmpty())
    }
}
