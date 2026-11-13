package br.com.leafcare.auth

import br.com.leafcare.data.TRAINING_TERMS_VERSION
import br.com.leafcare.data.TrainingConsentDao
import br.com.leafcare.data.TrainingConsentEntity
import br.com.leafcare.BuildConfig
import br.com.leafcare.auth.SupabaseClientHolder
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

internal interface LegalAcceptanceApi {
    suspend fun hasCurrentAcceptance(): Boolean
    suspend fun acceptCurrentDocuments()
    /** Offline fallback: whether this device holds a current consent for the account. */
    suspend fun hasLocalConsent(): Boolean = true
}

/** The server rejected the app's document version (it was not updated yet). */
internal class LegalVersionUnsupportedException : IllegalStateException("unsupported legal document version")

/** Server-confirmed, append-only legal acceptance for accounts predating the current documents. */
internal class LegalAcceptanceRepository(
    private val holder: SupabaseClientHolder,
    private val consentDao: TrainingConsentDao? = null,
) : LegalAcceptanceApi {
    /**
     * Current when the server holds the current (v1.5) receipt and the training consent it
     * grants. A revocation made on this device wins until it reaches the server;
     * the local consent follows the server otherwise.
     */
    override suspend fun hasCurrentAcceptance(): Boolean {
        val userId = holder.auth.currentUserOrNull()?.id ?: error("Sessão indisponível")
        val local = consentDao?.get(userId)
        if (local != null && !local.granted && local.syncState == "PENDING") return false
        val legal = JSONArray(String(request(
            "/rest/v1/legal_document_acceptances?select=acceptance_id&terms_version=eq.$TERMS_OF_USE_VERSION" +
                "&privacy_version=eq.$PRIVACY_POLICY_VERSION&limit=1",
            method = "GET"
        ), Charsets.UTF_8)).length() > 0
        if (!legal) return false
        val consent = JSONArray(String(request(
            "/rest/v1/training_consents?select=granted,terms_version&user_id=eq.$userId&limit=1",
            method = "GET"
        ), Charsets.UTF_8)).optJSONObject(0)
        val granted = consent?.optBoolean("granted") == true && consent.optString("terms_version") == TRAINING_TERMS_VERSION
        if (local?.granted != granted || local.termsVersion != TRAINING_TERMS_VERSION) {
            consentDao?.save(TrainingConsentEntity(userId, TRAINING_TERMS_VERSION, granted, System.currentTimeMillis(), "SYNCED"))
        }
        return granted
    }

    override suspend fun hasLocalConsent(): Boolean {
        val userId = holder.auth.currentUserOrNull()?.id ?: return false
        val local = consentDao?.get(userId) ?: return false
        return local.granted && local.termsVersion == TRAINING_TERMS_VERSION
    }

    override suspend fun acceptCurrentDocuments() {
        // A revocation not yet pushed is recorded before the new grant, so
        // accepting again right away never erases it. Its photo revocations
        // stay pending and the sync sends them whatever the consent.
        val userId = holder.auth.currentUserOrNull()?.id ?: error("Sessão indisponível")
        val local = consentDao?.get(userId)
        if (local != null && !local.granted && local.syncState == "PENDING") {
            request(
                "/rest/v1/training_consents?on_conflict=user_id",
                method = "POST",
                body = JSONObject()
                    .put("user_id", userId)
                    .put("terms_version", local.termsVersion)
                    .put("granted", false)
                    .put("decided_at", Instant.ofEpochMilli(local.decidedAt).toString()),
                prefer = "resolution=merge-duplicates,return=minimal",
            )
            consentDao.markConsentSynced(userId)
        }
        request(
            "/rest/v1/rpc/accept_current_legal_documents",
            method = "POST",
            body = JSONObject()
                .put("accepted_terms_version", TERMS_OF_USE_VERSION)
                .put("accepted_privacy_version", PRIVACY_POLICY_VERSION)
        )
        // The server recorded the consent with the receipt; mirror it so the app opens offline later.
        if (holder.auth.currentUserOrNull()?.id != userId) return
        consentDao?.save(TrainingConsentEntity(userId, TRAINING_TERMS_VERSION, true, System.currentTimeMillis(), "SYNCED"))
    }

    private suspend fun request(path: String, method: String, body: JSONObject? = null, prefer: String? = null): ByteArray = withContext(Dispatchers.IO) {
        val session = holder.freshSession() ?: error("Sessão indisponível")
        val connection = URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Cache-Control", "no-store")
            if (prefer != null) connection.setRequestProperty("Prefer", prefer)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                val detail = runCatching { connection.errorStream?.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrNull().orEmpty()
                // The server still runs an older document version: the 1.5 migration is missing.
                if ("unsupported legal document version" in detail) throw LegalVersionUnsupportedException()
                error("Não foi possível confirmar o aceite (${status})")
            }
            connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
