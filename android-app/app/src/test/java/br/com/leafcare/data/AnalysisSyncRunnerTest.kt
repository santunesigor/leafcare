package br.com.leafcare.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
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

    override suspend fun getPendingPhotoDownloads(): List<AnalysisEntity> =
        rows.values.filter {
            it.photoSyncStatus == PhotoSyncState.REMOTE_ONLY && it.deletedAt == null
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
    var failFetch: Boolean = false,
    var fetchRows: List<JsonObject> = emptyList(),
    var failDownload: Boolean = false,
) : AnalysisSyncApi {

    val remote = mutableMapOf<String, JsonObject>()
    val remotePhotos = mutableMapOf<String, ByteArray>()
    val upsertCalls = mutableListOf<String>()
    val deleteCalls = mutableListOf<String>()
    val uploadCalls = mutableListOf<String>()
    val photoPathUpdates = mutableListOf<Pair<String, String>>()
    val photoDeleteCalls = mutableListOf<String>()
    var fetchCalls = 0
    val downloadCalls = mutableListOf<String>()
    val remoteObjects = mutableMapOf<String, ByteArray>()

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

    override suspend fun fetchAnalyses(): List<JsonObject> {
        fetchCalls++
        if (failFetch) throw IOException("offline")
        return fetchRows
    }

    override suspend fun downloadPhoto(path: String): ByteArray {
        downloadCalls += path
        if (failDownload) throw IOException("offline")
        return remoteObjects[path] ?: throw IOException("404 object not found")
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
        displayName: String = "Olho-de-rã",
    ) = AnalysisEntity(
        id = id,
        photoName = "$id.img",
        createdAt = 1_728_000_000_000,
        classId = "frog_eye",
        displayName = displayName,
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

    private fun remoteRow(
        id: String = "r-1",
        displayName: String = "Olho-de-rã",
        deletedAt: String? = null,
        photoPath: String? = "user-1/r-1.jpg",
    ) = buildJsonObject {
        put("id", id)
        put("user_id", "user-1")
        put("created_at", "2024-09-23T12:00:00Z")
        put("class_id", "frog_eye")
        put("display_name", displayName)
        put("scientific_name", "Cercospora nicotianae")
        put("confidence", 0.82)
        put("top3", buildJsonArray {
            addJsonObject {
                put("class_id", "frog_eye")
                put("confidence", 0.82)
            }
        })
        put("inconclusive", false)
        put("threshold", 0.7)
        put("inference_ms", 30.0)
        put("model_sha256", "abc123")
        put("app_version", "0.3.0")
        if (photoPath == null) put("photo_path", JsonNull) else put("photo_path", photoPath)
        if (deletedAt == null) put("deleted_at", JsonNull) else put("deleted_at", deletedAt)
    }

    @Test fun restoreInsertsMissingRowAsSynced() = runTest(dispatcher) {
        val dao = FakeAnalysisDao()
        val api = FakeAnalysisSyncApi(fetchRows = listOf(remoteRow()))

        val result = runner(dao, api).restoreOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(1, api.fetchCalls)
        val row = dao.get("r-1")
        assertNotNull(row)
        assertEquals(SyncState.SYNCED, row?.syncStatus)
        assertEquals(PhotoSyncState.REMOTE_ONLY, row?.photoSyncStatus)
        assertEquals("r-1.img", row?.photoName)
        assertEquals("frog_eye", row?.classId)
        assertEquals("Olho-de-rã", row?.displayName)
        assertNull(row?.deletedAt)
    }

    @Test fun restoreTwiceDoesNotDuplicate() = runTest(dispatcher) {
        val dao = FakeAnalysisDao()
        val api = FakeAnalysisSyncApi(fetchRows = listOf(remoteRow()))
        val restore = runner(dao, api)

        restore.restoreOnce("user-1")
        val second = restore.restoreOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), second)
        assertEquals(2, api.fetchCalls)
        assertNotNull(dao.get("r-1"))
    }

    @Test fun remoteTombstoneIsNeverImportedAsActive() = runTest(dispatcher) {
        val dao = FakeAnalysisDao()
        val api = FakeAnalysisSyncApi(
            fetchRows = listOf(remoteRow(deletedAt = "2024-09-24T12:00:00Z"))
        )

        val result = runner(dao, api).restoreOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertNull(dao.get("r-1"))
    }

    @Test fun remoteTombstoneRemovesMatchingLocalSyncedRow() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(
                entity(
                    id = "r-1",
                    syncStatus = SyncState.SYNCED,
                    photoSyncStatus = PhotoSyncState.SYNCED
                )
            )
        )
        val api = FakeAnalysisSyncApi(
            fetchRows = listOf(remoteRow(deletedAt = "2024-09-24T12:00:00Z"))
        )
        val deletedPhotos = mutableListOf<String>()

        val result = runner(dao, api, deletedPhotos).restoreOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertNull(dao.get("r-1"))
        assertEquals(listOf("r-1.img"), deletedPhotos)
    }

    @Test fun localPendingUploadIsNeverOverwritten() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity(id = "r-1", displayName = "Local")))
        val api = FakeAnalysisSyncApi(fetchRows = listOf(remoteRow(displayName = "Remoto")))
        localPhoto("r-1.img")

        val result = runner(dao, api).restoreOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(SyncState.PENDING_UPLOAD, dao.get("r-1")?.syncStatus)
        assertEquals("Local", dao.get("r-1")?.displayName)
    }

    @Test fun restoreSkippedWithoutAuthenticatedUser() = runTest(dispatcher) {
        val dao = FakeAnalysisDao()
        val api = FakeAnalysisSyncApi(fetchRows = listOf(remoteRow()))

        val result = runner(dao, api).restoreOnce(null)

        assertEquals(SyncRunResult.SkippedNoAuth, result)
        assertEquals(0, api.fetchCalls)
        assertNull(dao.get("r-1"))
    }

    @Test fun fetchFailureKeepsRoomIntact() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(entity()))
        val api = FakeAnalysisSyncApi(failFetch = true)

        val result = runner(dao, api).restoreOnce("user-1")

        assertEquals(SyncRunResult.Completed(1), result)
        assertEquals(SyncState.PENDING_UPLOAD, dao.get("a-1")?.syncStatus)
    }

    @Test fun emptyRoomIsRebuiltFromRemote() = runTest(dispatcher) {
        val dao = FakeAnalysisDao()
        val api = FakeAnalysisSyncApi(
            fetchRows = listOf(remoteRow(id = "r-1"), remoteRow(id = "r-2"))
        )

        runner(dao, api).restoreOnce("user-1")

        assertNotNull(dao.get("r-1"))
        assertNotNull(dao.get("r-2"))
        assertEquals(SyncState.SYNCED, dao.get("r-2")?.syncStatus)
    }

    @Test fun malformedRemoteRowIsSkipped() = runTest(dispatcher) {
        val dao = FakeAnalysisDao()
        val api = FakeAnalysisSyncApi(
            fetchRows = listOf(buildJsonObject { put("oops", true) }, remoteRow())
        )

        val result = runner(dao, api).restoreOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertNotNull(dao.get("r-1"))
    }

    private fun remoteOnlyEntity(id: String = "r-1") = entity(
        id = id,
        syncStatus = SyncState.SYNCED,
        photoSyncStatus = PhotoSyncState.REMOTE_ONLY
    )

    @Test fun remoteOnlyDownloadsPhotoToLocalCache() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi()
        api.remoteObjects["user-1/r-1.jpg"] = "remote-bytes".toByteArray()

        val result = runner(dao, api).downloadOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertEquals(listOf("user-1/r-1.jpg"), api.downloadCalls)
        val cached = File(photosDir, "r-1.img")
        assertTrue(cached.exists())
        assertArrayEquals("remote-bytes".toByteArray(), cached.readBytes())
        assertEquals(PhotoSyncState.SYNCED, dao.get("r-1")?.photoSyncStatus)
        assertEquals(SyncState.SYNCED, dao.get("r-1")?.syncStatus)
    }

    @Test fun existingValidLocalFileSkipsDownload() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi()
        localPhoto("r-1.img")

        val result = runner(dao, api).downloadOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertTrue(api.downloadCalls.isEmpty())
        assertEquals(PhotoSyncState.SYNCED, dao.get("r-1")?.photoSyncStatus)
    }

    @Test fun repeatedRestoreDoesNotDuplicateFiles() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi()
        api.remoteObjects["user-1/r-1.jpg"] = "remote-bytes".toByteArray()
        val download = runner(dao, api)

        download.downloadOnce("user-1")
        val second = download.downloadOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), second)
        assertEquals(listOf("user-1/r-1.jpg"), api.downloadCalls)
    }

    @Test fun downloadFailureKeepsAnalysisAndAllowsRetry() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi(failDownload = true)
        val download = runner(dao, api)

        assertEquals(SyncRunResult.Completed(1), download.downloadOnce("user-1"))
        assertEquals(PhotoSyncState.REMOTE_ONLY, dao.get("r-1")?.photoSyncStatus)
        assertNotNull(dao.get("r-1"))
        assertFalse(File(photosDir, "r-1.img").exists())

        api.failDownload = false
        api.remoteObjects["user-1/r-1.jpg"] = "remote-bytes".toByteArray()
        assertEquals(SyncRunResult.Completed(0), download.downloadOnce("user-1"))
        assertEquals(PhotoSyncState.SYNCED, dao.get("r-1")?.photoSyncStatus)
    }

    @Test fun missingRemotePhotoDoesNotCrash() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi()

        val result = runner(dao, api).downloadOnce("user-1")

        assertEquals(SyncRunResult.Completed(1), result)
        assertEquals(PhotoSyncState.REMOTE_ONLY, dao.get("r-1")?.photoSyncStatus)
        assertNotNull(dao.get("r-1"))
        assertFalse(File(photosDir, "r-1.img").exists())
    }

    @Test fun tombstoneNeverTriggersDownload() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(
            listOf(
                entity(
                    syncStatus = SyncState.PENDING_DELETE,
                    deletedAt = 1_728_000_100_000,
                    photoSyncStatus = PhotoSyncState.REMOTE_ONLY
                )
            )
        )
        val api = FakeAnalysisSyncApi()

        val result = runner(dao, api).downloadOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertTrue(api.downloadCalls.isEmpty())
    }

    @Test fun downloadSkippedWithoutAuthenticatedUser() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi()

        val result = runner(dao, api).downloadOnce(null)

        assertEquals(SyncRunResult.SkippedNoAuth, result)
        assertTrue(api.downloadCalls.isEmpty())
        assertEquals(PhotoSyncState.REMOTE_ONLY, dao.get("r-1")?.photoSyncStatus)
    }

    @Test fun downloadUsesSessionUserPath() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi()
        api.remoteObjects["user-9/r-1.jpg"] = "remote-bytes".toByteArray()

        runner(dao, api).downloadOnce("user-9")

        // Path is always derived from the session user id, never stored data.
        assertEquals(listOf("user-9/r-1.jpg"), api.downloadCalls)
        assertTrue(File(photosDir, "r-1.img").exists())
    }

    @Test fun zeroLengthCacheIsReplacedWithoutPartialRemnants() = runTest(dispatcher) {
        val dao = FakeAnalysisDao(listOf(remoteOnlyEntity()))
        val api = FakeAnalysisSyncApi()
        api.remoteObjects["user-1/r-1.jpg"] = "remote-bytes".toByteArray()
        File(photosDir, "r-1.img").apply { writeBytes(ByteArray(0)) }

        val result = runner(dao, api).downloadOnce("user-1")

        assertEquals(SyncRunResult.Completed(0), result)
        assertArrayEquals("remote-bytes".toByteArray(), File(photosDir, "r-1.img").readBytes())
        assertFalse(File(photosDir, "r-1.img.tmp").exists())
    }
}
