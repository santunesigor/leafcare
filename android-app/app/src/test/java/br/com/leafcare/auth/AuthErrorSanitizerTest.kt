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
        val message = AuthRepository.sanitizeError(AuthOperation.SIGN_IN, Exception())

        assertEquals("Não foi possível entrar. Tente novamente.", message)
        assertFalse(message.contains("null", ignoreCase = true))
    }

    @Test fun signOutFallback_isFixedMessage() {
        assertEquals(
            "Não foi possível sair. Tente novamente.",
            AuthRepository.sanitizeError(AuthOperation.SIGN_OUT, Exception("boom"))
        )
    }

    @Test fun passwordResetFallback_isFixedMessage() {
        assertEquals(
            "Não foi possível enviar a recuperação. Tente novamente.",
            AuthRepository.sanitizeError(AuthOperation.PASSWORD_RESET, Exception("boom"))
        )
    }

    @Test fun recoveryVerifyFallback_isFixedMessage() {
        assertEquals(
            "Não foi possível concluir a recuperação. Tente novamente.",
            AuthRepository.sanitizeError(AuthOperation.RECOVERY_VERIFY, Exception("boom"))
        )
    }

    @Test fun updatePasswordFallback_isFixedMessage() {
        assertEquals(
            "Não foi possível definir a nova senha. Tente novamente.",
            AuthRepository.sanitizeError(AuthOperation.UPDATE_PASSWORD, Exception("boom"))
        )
    }

    @Test fun expiredLink_mapsToLinkMessage() {
        val raw = Exception("Status 400 body={\"msg\":\"Email link is invalid or has expired\"}")

        assertEquals(
            "Link inválido ou expirado.",
            AuthRepository.sanitizeError(AuthOperation.RECOVERY_VERIFY, raw)
        )
    }

    @Test fun rateLimitErrorCode_mapsToWaitMessage() {
        val raw = Exception("Status 429 error_code=over_email_send_rate_limit")

        assertEquals(
            "Você solicitou um e-mail recentemente. Aguarde um pouco antes de tentar novamente.",
            AuthRepository.sanitizeError(AuthOperation.PASSWORD_RESET, raw)
        )
    }

    @Test fun rateLimitWording_mapsToWaitMessage() {
        val raw = Exception("429 Too Many Requests: rate limit exceeded")

        assertEquals(
            "Você solicitou um e-mail recentemente. Aguarde um pouco antes de tentar novamente.",
            AuthRepository.sanitizeError(AuthOperation.PASSWORD_RESET, raw)
        )
    }

    @Test fun unauthorizedAddress_mapsToFriendlyMessage() {
        val raw = Exception("Email address not authorized by mail server")

        assertEquals(
            "Envio indisponível para esse endereço nesta configuração de teste.",
            AuthRepository.sanitizeError(AuthOperation.PASSWORD_RESET, raw)
        )
    }
}
