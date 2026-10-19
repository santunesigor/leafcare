package br.com.leafcare.auth

import br.com.leafcare.BuildConfig
import br.com.leafcare.auth.SupabaseClientHolder
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal interface LegalAcceptanceApi {
    suspend fun hasCurrentAcceptance(): Boolean
    suspend fun acceptCurrentDocuments()
}

/** Server-confirmed, append-only legal acceptance for accounts predating the current documents. */
internal class LegalAcceptanceRepository(private val holder: SupabaseClientHolder) : LegalAcceptanceApi {
    override suspend fun hasCurrentAcceptance(): Boolean {
        val body = request(
            "/rest/v1/legal_document_acceptances?select=acceptance_id&terms_version=eq.$TERMS_OF_USE_VERSION" +
                "&privacy_version=eq.$PRIVACY_POLICY_VERSION&limit=1",
            method = "GET"
        )
        return JSONArray(String(body, Charsets.UTF_8)).length() > 0
    }

    override suspend fun acceptCurrentDocuments() {
        request(
            "/rest/v1/rpc/accept_current_legal_documents",
            method = "POST",
            body = JSONObject()
                .put("accepted_terms_version", TERMS_OF_USE_VERSION)
                .put("accepted_privacy_version", PRIVACY_POLICY_VERSION)
        )
    }

    private suspend fun request(path: String, method: String, body: JSONObject? = null): ByteArray = withContext(Dispatchers.IO) {
        val session = holder.auth.currentSessionOrNull() ?: error("Sessão indisponível")
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) error("Não foi possível confirmar o aceite (${status})")
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
