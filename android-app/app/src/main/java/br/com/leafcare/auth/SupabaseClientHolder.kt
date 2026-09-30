package br.com.leafcare.auth

import android.app.Application
import android.util.Log
import br.com.leafcare.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Holds a single [SupabaseClient] instance for the application lifecycle.
 * Initializes only the Auth module.
 */
class SupabaseClientHolder(application: Application) {

    companion object {
        private const val TAG = "SupabaseClientHolder"
    }

    val client: SupabaseClient
        get() = _client

    val auth: Auth
        get() = client.auth

    private val _client: SupabaseClient

    init {
        val url = BuildConfig.SUPABASE_URL
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY

        if (url.isNullOrBlank()) {
            throw IllegalStateException("SUPABASE_URL is not configured. Check local.properties.")
        }
        if (publishableKey.isNullOrBlank() || publishableKey == "YOUR_PUBLISHABLE_KEY_HERE") {
            Log.w(TAG, "SUPABASE_PUBLISHABLE_KEY is a placeholder. Auth will not work without a real key.")
        }

        _client = createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = publishableKey
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
