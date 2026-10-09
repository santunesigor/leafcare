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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject

internal data class AdminState(
    val allowed: Boolean = false, val busy: Boolean = false, val error: String? = null,
    val section: String = "dashboard", val page: Int = 0, val rows: List<JSONObject> = emptyList(),
    val dashboard: JSONObject = JSONObject(), val selected: JSONObject? = null,
    val bitmap: Bitmap? = null, val message: String? = null,
)
internal class AdminViewModel(application: Application) : AndroidViewModel(application) {
    private val api = AdminRepository((application as LeafCareApplication).supabaseClientHolder)
    private val mutable = MutableStateFlow(AdminState())
    val state = mutable.asStateFlow()
    private val thumbnailState = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbnails = thumbnailState.asStateFlow()
    private val thumbnailSlots = Semaphore(2)
    private var owner: String? = null
    private var generation = 0
    private var work: Job? = null
    private var filters = JSONObject()

    fun bind(userId: String?) {
        if (owner != userId) { clear(); owner = userId }
        if (userId != null) checkAccess()
    }
    fun clear() { generation++; work?.cancel(); mutable.value = AdminState(); owner = null; filters = JSONObject(); thumbnailState.value = emptyMap() }
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
    private fun deny() { generation++; work?.cancel(); thumbnailState.value = emptyMap(); mutable.value = AdminState(error = "Administração exige conexão e permissão de superadmin.") }
    private fun run(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        val revision = generation
        mutable.value = mutable.value.copy(busy = true, error = null, message = null)
        work = viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: AdminAccessException) { if (revision == generation) deny() }
            catch (e: Exception) { if (revision == generation) mutable.value = mutable.value.copy(error = e.message ?: "Falha de conexão") }
            finally { if (revision == generation) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun load(section: String = mutable.value.section, page: Int = 0, query: JSONObject = JSONObject()) {
        if (mutable.value.busy) return
        filters = query
        run {
            thumbnailState.value = emptyMap()
            val payload = JSONObject(query.toString()).put("page", page)
            val result = api.command(section, payload)
            mutable.value = mutable.value.copy(section = section, page = page, selected = null, bitmap = null,
                dashboard = if (section == "dashboard") JSONObject(result) else mutable.value.dashboard,
                rows = if (section == "dashboard") emptyList() else JSONArray(result).let { array -> List(array.length()) { array.getJSONObject(it) } })
        }
    }
    suspend fun thumbnail(id: String) {
        val revision = generation
        if (thumbnailState.value.containsKey(id)) return
        try {
            thumbnailSlots.withPermit {
                val bytes = api.photo(id)
                val bitmap = decodePhoto(bytes, 160)
                if (revision == generation && bitmap != null && mutable.value.rows.any { it.optString("id") == id }) thumbnailState.value = thumbnailState.value + (id to bitmap)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: AdminAccessException) { if (revision == generation) deny() }
        catch (_: Exception) { /* Missing image does not erase the review queue. */ }
    }
    fun reload() = load(mutable.value.section, mutable.value.page, filters)
    fun page(delta: Int) = load(mutable.value.section, (mutable.value.page + delta).coerceAtLeast(0), filters)
    fun select(row: JSONObject) {
        run {
            val bitmap = if (mutable.value.section == "photos") {
                val bytes = api.photo(row.getString("id"))
                decodePhoto(bytes, 2048) ?: error("Foto inválida")
            } else null
            mutable.value = mutable.value.copy(selected = row, bitmap = bitmap)
        }
    }
    fun closeDetails() { mutable.value = mutable.value.copy(selected = null, bitmap = null) }
    fun action(operation: String, payload: JSONObject) {
        run {
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
            if (operation == "review") {
                val pending = JSONArray(api.command("photos", JSONObject(filters.toString()).put("status", "pending").put("page", 0)))
                val next = if (pending.length() > 0) pending.getJSONObject(0) else null
                if (next != null) {
                    val bytes = api.photo(next.getString("id"))
                    mutable.value = mutable.value.copy(selected = next, bitmap = decodePhoto(bytes, 2048))
                }
            }
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
