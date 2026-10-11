package br.com.leafcare.admin

import android.app.Application
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdminQueueTest {
    private lateinit var api: FakeAdminApi
    private lateinit var vm: AdminViewModel
    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api = FakeAdminApi()
        vm = AdminViewModel(Application(), api)
        vm.checkAccess()
    }
    @After fun cleanup() { vm.clear(); Dispatchers.resetMain() }
    private fun open(status: String = "pending") = vm.load("photos", query = JSONObject().put("status", status).put("class_id", "frog_eye"))
    private fun save() {
        val row = vm.state.value.rows[vm.state.value.photoIndex]
        vm.action("review", JSONObject().put("id", row.getString("id")).put("version", row.optInt("review_version"))
            .put("status", "confirmed").put("class_id", "frog_eye").put("note", ""))
    }
    @Test fun savesFortyFivePendingPhotosWithoutSkippingShiftedPages() {
        api.seed(45)
        open()
        repeat(45) { save() }
        assertEquals(45, api.saved.distinct().size)
        assertEquals(45, vm.state.value.reviewedCount)
        assertTrue(vm.state.value.rows.isEmpty())
        assertFalse(vm.state.value.queueHasMore)
        assertNull(vm.state.value.error)
        assertTrue(api.queries.all { it.optString("class_id") == "frog_eye" && it.optString("status") == "pending" })
    }
    @Test fun filteredQueueDoesNotSwitchToPendingOrRepeatSavedItems() {
        api.seed(25, "confirmed")
        open("confirmed")
        repeat(25) { save() }
        assertEquals(25, api.saved.distinct().size)
        assertTrue(vm.state.value.rows.isEmpty())
        assertTrue(api.queries.all { it.optString("status") == "confirmed" })
    }
    @Test fun movingNearLastPhotoAppendsNextBatchWithoutSaving() {
        api.seed(25)
        open()
        vm.movePhoto(18)
        assertEquals(25, vm.state.value.rows.size)
        vm.movePhoto(19)
        vm.nextPhoto()
        assertEquals("photo-20", vm.state.value.photoId)
        assertEquals(0, api.saved.size)
        assertEquals(25, api.rows.size)
    }
    @Test fun failedFilterRequestPreservesCurrentQueueAndActiveFilter() {
        api.seed(25, "confirmed")
        open("confirmed")
        api.listFailure = true
        open("pending")
        assertEquals("confirmed", vm.state.value.photoFilterStatus)
        assertEquals(20, vm.state.value.rows.size)
        api.listFailure = false
        vm.movePhoto(18)
        assertEquals(25, vm.state.value.rows.size)
        assertTrue(api.queries.all { it.optString("status") == "confirmed" })
    }
    @Test fun failedSaveKeepsCurrentPhotoAndAllowsRetry() {
        api.seed(2)
        open()
        val bitmap = vm.state.value.bitmap
        api.reviewFailure = IllegalStateException("Sem conexão")
        save()
        assertEquals("photo-0", vm.state.value.photoId)
        assertSame(bitmap, vm.state.value.bitmap)
        assertEquals(0, vm.state.value.reviewedCount)
        assertEquals("Sem conexão", vm.state.value.error)
        api.reviewFailure = null
        save()
        assertEquals("photo-1", vm.state.value.photoId)
        assertEquals(1, vm.state.value.reviewedCount)
    }
    @Test fun conflictBlocksSaveUntilReloadingCurrentVersion() {
        api.seed(2)
        open()
        api.rows[0].put("review_version", 2)
        api.reviewFailure = AdminReviewConflictException("Revisão mudou")
        save()
        assertTrue(vm.state.value.reviewConflict)
        api.reviewFailure = null
        save()
        assertTrue(api.saved.isEmpty())
        vm.retryPhoto()
        assertFalse(vm.state.value.reviewConflict)
        assertEquals(2, vm.state.value.rows[0].optInt("review_version"))
        save()
        assertEquals(2, api.savedVersion)
    }
    @Test fun photoFailureAfterSuccessfulSaveNeverResubmitsPreviousPhoto() {
        api.seed(2)
        open()
        api.failedPhoto = "photo-1"
        save()
        assertEquals(listOf("photo-0"), api.saved)
        assertEquals(1, vm.state.value.reviewedCount)
        assertEquals("photo-1", vm.state.value.photoId)
        assertNull(vm.state.value.bitmap)
        save()
        assertEquals(1, api.saved.size)
        api.failedPhoto = null
        vm.retryPhoto()
        assertNotNull(vm.state.value.bitmap)
        save()
        assertEquals(listOf("photo-0", "photo-1"), api.saved)
    }
    @Test fun failedRefillAfterLastSaveCanRetryWithoutSavingAgain() {
        api.seed(21)
        open()
        // Preserve a next batch flag, then fail the request made while the buffer gets short.
        repeat(17) { save() }
        api.listFailure = true
        save()
        assertEquals(18, api.saved.size)
        assertNull(vm.state.value.bitmap)
        api.listFailure = false
        vm.retryPhoto()
        assertNotNull(vm.state.value.bitmap)
        assertEquals(18, api.saved.size)
    }
    @Test fun accessRevocationClearsQueueAndImage() {
        api.seed(2)
        open()
        api.reviewFailure = AdminAccessException("Revogado")
        save()
        assertFalse(vm.state.value.allowed)
        assertTrue(vm.state.value.rows.isEmpty())
        assertNull(vm.state.value.bitmap)
    }

    private class FakeAdminApi : AdminApi {
        val rows = mutableListOf<JSONObject>()
        val saved = mutableListOf<String>()
        val queries = mutableListOf<JSONObject>()
        var savedVersion = -1
        var reviewFailure: Exception? = null
        var failedPhoto: String? = null
        var listFailure = false
        private val bytes = ByteArrayOutputStream().also { Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        fun seed(count: Int, status: String = "pending") {
            repeat(count) { rows += JSONObject().put("id", "photo-$it").put("class_id", "frog_eye").put("review_status", status).put("review_version", 0) }
        }
        override suspend fun policy() = JSONObject().put("role", "superadmin")
        override suspend fun photo(id: String): ByteArray { if (failedPhoto == id) error("Não foi possível carregar a foto"); return bytes }
        override suspend fun command(operation: String, payload: JSONObject): String = when (operation) {
            "photos" -> {
                if (listFailure) error("Falha ao buscar fotos")
                queries += JSONObject(payload.toString())
                val result = rows.filter { (!payload.has("id") || it.getString("id") == payload.getString("id")) &&
                    (payload.optString("status").isBlank() || it.optString("review_status") == payload.getString("status")) }
                    .drop(payload.optInt("page") * 20).take(20)
                JSONArray(result.map { JSONObject(it.toString()) }).toString()
            }
            "review" -> {
                reviewFailure?.let { throw it }
                saved += payload.getString("id")
                savedVersion = payload.getInt("version")
                rows.first { it.getString("id") == payload.getString("id") }.put("review_status", payload.getString("status"))
                "{}"
            }
            else -> error("Operação inesperada: $operation")
        }
    }
}
