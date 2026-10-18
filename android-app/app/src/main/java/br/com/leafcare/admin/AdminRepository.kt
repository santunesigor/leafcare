package br.com.leafcare.admin

import br.com.leafcare.BuildConfig
import br.com.leafcare.auth.SupabaseClientHolder
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal class AdminAccessException(message: String) : Exception(message)
internal class AdminReviewConflictException(message: String) : Exception(message)

internal interface AdminApi {
    suspend fun policy(): JSONObject
    suspend fun command(operation: String, payload: JSONObject = JSONObject()): String
    suspend fun photo(id: String): ByteArray
}

/** Authenticated requests; no service key or persistent administrative cache. */
internal class AdminRepository(private val holder: SupabaseClientHolder) : AdminApi {
    override suspend fun policy(): JSONObject = JSONObject(String(request("/rest/v1/rpc/my_account_policy", JSONObject())))
    override suspend fun command(operation: String, payload: JSONObject): String =
        String(request("/functions/v1/leafcare-admin", JSONObject().put("operation", operation).put("payload", payload)))
    override suspend fun photo(id: String): ByteArray = request("/functions/v1/leafcare-admin",
        JSONObject().put("operation", "photo").put("payload", JSONObject().put("id", id)))

    private suspend fun request(path: String, body: JSONObject): ByteArray = withContext(Dispatchers.IO) {
        val session = holder.auth.currentSessionOrNull() ?: throw AdminAccessException("Entre novamente na sua conta")
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val code = runCatching { JSONObject(error).optString("code") }.getOrDefault("")
                val message = runCatching { JSONObject(error).optString("error") }.getOrDefault("")
                if (status == 401 || (status == 403 && code != "operation_forbidden")) throw AdminAccessException("Sem acesso administrativo. Entre novamente ou confira sua permissão.")
                if (status == 409) throw AdminReviewConflictException(message.ifBlank { "Revisão mudou; recarregue antes de salvar" })
                throw IllegalStateException(message.ifBlank { "Serviço indisponível. Confira a conexão e tente novamente." })
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            if (holder.auth.currentUserOrNull()?.id != session.user?.id) throw AdminAccessException("A conta mudou")
            bytes
        } finally { connection.disconnect() }
    }
}
