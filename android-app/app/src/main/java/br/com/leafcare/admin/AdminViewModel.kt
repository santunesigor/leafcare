package br.com.leafcare.admin

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.leafcare.LeafCareApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal data class AdminState(
    val allowed: Boolean = false, val busy: Boolean = false, val error: String? = null,
    val section: String = "dashboard", val page: Int = 0, val rows: List<JSONObject> = emptyList(),
    val dashboard: JSONObject = JSONObject(), val selected: JSONObject? = null,
    val bitmap: Bitmap? = null, val message: String? = null,
    val photoIndex: Int = 0, val photoId: String? = null, val queueHasMore: Boolean = false,
    val reviewedCount: Int = 0, val reviewConflict: Boolean = false,
    val photoFilterStatus: String = "pending",
)
internal class AdminViewModel(application: Application, private val api: AdminApi) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, AdminRepository((application as LeafCareApplication).supabaseClientHolder))
    private val mutable = MutableStateFlow(AdminState())
    val state = mutable.asStateFlow()
    private var owner: String? = null
    private var generation = 0
    private var work: Job? = null
    private var filters = JSONObject()
    private val reviewedIds = mutableSetOf<String>()

    fun bind(userId: String?) {
        if (owner != userId) { clear(); owner = userId }
        if (userId != null) checkAccess()
    }
    fun clear() { generation++; work?.cancel(); mutable.value = AdminState(); owner = null; filters = JSONObject(); reviewedIds.clear() }
    fun checkAccess() {
        val revision = generation
        viewModelScope.launch {
            try {
                val policy = api.policy()
                if (revision == generation) {
                    if (policy.optString("role") == "superadmin") mutable.value = mutable.value.copy(allowed = true)
                    else deny()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (revision == generation) deny() }
        }
    }
    private fun deny() { generation++; work?.cancel(); mutable.value = AdminState(error = "Administração exige conexão e permissão de superadmin.") }
    private fun run(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        val revision = generation
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        work = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: AdminAccessException) { if (revision == generation) deny() }
            catch (e: AdminReviewConflictException) { if (revision == generation) mutable.value = mutable.value.copy(error = e.message, reviewConflict = true) }
            catch (e: Exception) { if (revision == generation) mutable.value = mutable.value.copy(error = e.message ?: "Falha de conexão") }
            finally { if (revision == generation) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun load(section: String = mutable.value.section, page: Int = 0, query: JSONObject = if (section == "photos") JSONObject().put("status", "pending") else JSONObject()) {
        if (mutable.value.busy) return
        run {
            val payload = JSONObject(query.toString()).put("page", page)
            val result = api.command(section, payload)
            filters = query
            reviewedIds.clear()
            mutable.value = mutable.value.copy(section = section, page = page, selected = null, bitmap = null,
                photoIndex = 0, photoId = null, reviewedCount = 0, reviewConflict = false,
                dashboard = if (section == "dashboard") JSONObject(result) else mutable.value.dashboard,
                rows = if (section == "dashboard") emptyList() else JSONArray(result).let { array -> List(array.length()) { array.getJSONObject(it) } })
            if (section == "photos") {
                mutable.value = mutable.value.copy(queueHasMore = mutable.value.rows.size == 20, photoFilterStatus = query.optString("status"))
                showPhoto(0)
            }
        }
    }
    fun reload() = load(mutable.value.section, mutable.value.page, filters)
    fun page(delta: Int) = load(mutable.value.section, (mutable.value.page + delta).coerceAtLeast(0), filters)
    fun select(row: JSONObject) {
        if (mutable.value.section == "photos") { movePhoto(mutable.value.rows.indexOfFirst { it.optString("id") == row.optString("id") }); return }
        run {
            mutable.value = mutable.value.copy(selected = row)
        }
    }
    fun closeDetails() { mutable.value = mutable.value.copy(selected = null, bitmap = null) }
    private suspend fun showPhoto(index: Int) {
        val row = mutable.value.rows.getOrNull(index) ?: return
        val id = row.getString("id")
        mutable.value = mutable.value.copy(photoIndex = index, photoId = id, bitmap = null, reviewConflict = false)
        val bitmap = decodePhoto(api.photo(id), 2048) ?: error("Foto inválida. Tente carregar novamente.")
        mutable.value = mutable.value.copy(bitmap = bitmap)
    }
    fun movePhoto(index: Int) {
        if (index !in mutable.value.rows.indices || (index == mutable.value.photoIndex && mutable.value.bitmap != null)) return
        run {
            showPhoto(index)
            if (index >= mutable.value.rows.lastIndex - 1) appendPhotos()
        }
    }
    private suspend fun appendPhotos() {
        if (!mutable.value.queueHasMore) return
        val known = mutable.value.rows.map { it.getString("id") }.toSet() + reviewedIds
        var page = 0
        // Start again at zero: reviewing pending photos shifts every later offset.
        while (true) {
            val batch = JSONArray(api.command("photos", JSONObject(filters.toString()).put("page", page)))
                .let { a -> List(a.length()) { a.getJSONObject(it) } }
            val fresh = batch.filter { it.getString("id") !in known }
            if (fresh.isNotEmpty() || batch.size < 20) {
                mutable.value = mutable.value.copy(rows = mutable.value.rows + fresh, queueHasMore = batch.size == 20)
                return
            }
            page++
        }
    }
    fun nextPhoto() {
        run {
            val next = mutable.value.photoIndex + 1
            if (next >= mutable.value.rows.size) appendPhotos()
            if (next < mutable.value.rows.size) {
                showPhoto(next)
                if (next >= mutable.value.rows.lastIndex - 1) appendPhotos()
            }
            else mutable.value = mutable.value.copy(message = "Fim da fila. As fotos que você pulou continuam sem alteração.")
        }
    }
    fun retryPhoto() {
        run {
            val index = mutable.value.photoIndex
            val id = mutable.value.rows.getOrNull(index)?.getString("id")
            if (id == null) { appendPhotos(); showPhoto(0); return@run }
            if (mutable.value.reviewConflict) {
                val rows = JSONArray(api.command("photos", JSONObject().put("id", id)))
                if (rows.length() == 0) error("Foto indisponível. Atualize a fila.")
                mutable.value = mutable.value.copy(rows = mutable.value.rows.toMutableList().apply { set(index, rows.getJSONObject(0)) })
            }
            showPhoto(index)
        }
    }
    private suspend fun saveReview(payload: JSONObject) {
        val before = mutable.value
        val row = before.rows.getOrNull(before.photoIndex) ?: return
        if (before.bitmap == null || before.reviewConflict || payload.optString("id") != row.getString("id")) return
        api.command("review", payload)
        reviewedIds += row.getString("id")
        val remaining = before.rows.filterNot { it.getString("id") == row.getString("id") }
        val index = before.photoIndex.coerceAtMost((remaining.size - 1).coerceAtLeast(0))
        mutable.value = mutable.value.copy(rows = remaining, photoIndex = index, photoId = null, bitmap = null,
            selected = null, reviewedCount = before.reviewedCount + 1, message = "Revisão salva.")
        // Commit the success to state before further network calls, preventing a duplicate save on retry.
        if (remaining.isEmpty() || before.photoIndex >= remaining.lastIndex - 1) appendPhotos()
        if (mutable.value.rows.isNotEmpty()) showPhoto(before.photoIndex.coerceAtMost(mutable.value.rows.lastIndex))
        else mutable.value = mutable.value.copy(message = "Fila concluída. Todas as fotos desta seleção foram revisadas.")
    }
    fun action(operation: String, payload: JSONObject) {
        run {
            if (operation == "review") { saveReview(payload); return@run }
            var result = JSONObject(api.command(operation, payload))
            if (operation == "delete") {
                // The server deletes at most 100 objects per request; cancellation is resumable.
                while (result.optBoolean("pending")) result = JSONObject(api.command(operation, payload))
            }
            mutable.value = mutable.value.copy(selected = null, bitmap = null, message = "Operação concluída.")
            val query = JSONObject(filters.toString()).put("page", mutable.value.page)
            val data = api.command(mutable.value.section, query)
            val rows = JSONArray(data).let { a -> List(a.length()) { a.getJSONObject(it) } }
            mutable.value = mutable.value.copy(rows = rows)
        }
    }
    private fun decodePhoto(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > maxDimension) inSampleSize *= 2
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
    fun history(id: String) = load("audit", query = JSONObject().put("id", id))
}
