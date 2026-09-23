package br.com.leafcare.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * Error sanitization tests: raw backend errors (URL, headers, tokens, HTTP body)
 * must never reach the UI. Pure functions, no Android framework.
 */
class AuthErrorSanitizerTest {

    private val rawDump = Exception(
        "POST https://nhkqfanjfcivcbndivav.supabase.co/auth/v1/signup " +
            "Status 401 headers={apikey=SECRET_KEY, Authorization=Bearer SECRET_KEY} " +
            "body={\"message\":\"Invalid API key\"}"
    )

    @Test fun invalidApiKey_mapsToConfigMessageWithoutLeak() {
        val message = AuthRepository.sanitizeError(AuthOperation.SIGN_UP, rawDump)

        assertEquals(
            "Não foi possível conectar ao serviço. Verifique a configuração do aplicativo.",
            message
        )
        assertFalse(message.contains("SECRET_KEY"))
        assertFalse(message.contains("http"))
        assertFalse(message.contains("Authorization"))
        assertFalse(message.contains("apikey"))
    }

    @Test fun genericSignUpError_isFixedMessageWithoutLeak() {
        val raw = Exception(
            "GET https://nhkqfanjfcivcbndivav.supabase.co/auth/v1/user " +
                "Status 500 headers={apikey=SECRET_KEY} body={\"error\":\"boom\"}"
        )

        val message = AuthRepository.sanitizeError(AuthOperation.SIGN_UP, raw)

        assertEquals("Não foi possível criar sua conta. Tente novamente.", message)
        assertFalse(message.contains("SECRET_KEY"))
        assertFalse(message.contains("http"))
    }

    @Test fun invalidCredentials_mapsToLoginMessage() {
        val raw = Exception("Status 400 body={\"error\":\"invalid credentials\"}")

        assertEquals(
            "E-mail ou senha incorretos",
            AuthRepository.sanitizeError(AuthOperation.SIGN_IN, raw)
        )
    }

    @Test fun unresolvableHost_mapsToOfflineMessage() {
        val raw = Exception("Unable to resolve host \"nhkqfanjfcivcbndivav.supabase.co\"")

        assertEquals(
            "Sem conexão. Verifique sua internet.",
            AuthRepository.sanitizeError(AuthOperation.SIGN_IN, raw)
        )
    }

    @Test fun nullMessage_mapsToFallbackWithoutNullText() {
        val message = AuthRepository.sanitizeError(AuthOperation.PASSWORD_RESET, Exception())

        assertEquals("Não foi possível enviar a recuperação. Tente novamente.", message)
        assertFalse(message.contains("null", ignoreCase = true))
    }

    @Test fun signOutFallback_isFixedMessage() {
        assertEquals(
            "Não foi possível sair. Tente novamente.",
            AuthRepository.sanitizeError(AuthOperation.SIGN_OUT, Exception("boom"))
        )
    }
}
