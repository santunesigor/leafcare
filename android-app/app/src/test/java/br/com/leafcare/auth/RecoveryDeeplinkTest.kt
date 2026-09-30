package br.com.leafcare.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * Auth deep-link parser tests: signup confirmation and password recovery
 * are distinguished by path and never confused. Unknown links are ignored.
 * Pure function, no Android framework.
 */
class RecoveryDeeplinkTest {

    @Test fun confirmEmailCodeLink_isAccepted() {
        val link = parseAuthDeeplink("leafcare://auth/confirm-email?code=pkce-code-123")

        assertEquals(AuthDeeplink.ConfirmEmailCode("pkce-code-123"), link)
    }

    @Test fun confirmEmailTokenFragment_isAccepted() {
        val link = parseAuthDeeplink(
            "leafcare://auth/confirm-email#access_token=at123&refresh_token=rt123&token_type=bearer&type=signup"
        )

        assertEquals(AuthDeeplink.ConfirmEmailTokens("at123", "rt123"), link)
    }

    @Test fun recoveryCodeLink_isAccepted() {
        val link = parseAuthDeeplink("leafcare://auth/reset-password?code=pkce-code-123")

        assertEquals(AuthDeeplink.RecoveryCode("pkce-code-123"), link)
    }

    @Test fun recoveryTokenFragment_isAccepted() {
        val link = parseAuthDeeplink(
            "leafcare://auth/reset-password#access_token=at123&refresh_token=rt123&expires_in=3600&token_type=bearer&type=recovery"
        )

        assertEquals(AuthDeeplink.RecoveryTokens("at123", "rt123"), link)
    }

    @Test fun confirmAndRecovery_areNeverConfused() {
        val confirm = parseAuthDeeplink("leafcare://auth/confirm-email?code=abc")
        val recovery = parseAuthDeeplink("leafcare://auth/reset-password?code=abc")

        assertTrue(confirm is AuthDeeplink.ConfirmEmailCode)
        assertTrue(recovery is AuthDeeplink.RecoveryCode)
        assertFalse(recovery is AuthDeeplink.ConfirmEmailCode)
    }

    @Test fun unknownPath_isRejected() {
        assertNull(parseAuthDeeplink("leafcare://auth/other?code=abc"))
        assertNull(parseAuthDeeplink("leafcare://other?code=abc"))
    }

    @Test fun wrongScheme_isRejected() {
        assertNull(parseAuthDeeplink("https://example.com/auth/reset-password?code=abc"))
    }

    @Test fun crossTypeFragments_areRejected() {
        // Signup tokens on the recovery path (and vice versa) are ignored.
        assertNull(
            parseAuthDeeplink(
                "leafcare://auth/reset-password#access_token=at&refresh_token=rt&type=signup"
            )
        )
        assertNull(
            parseAuthDeeplink(
                "leafcare://auth/confirm-email#access_token=at&refresh_token=rt&type=recovery"
            )
        )
    }

    @Test fun incompleteLinks_areRejected() {
        assertNull(parseAuthDeeplink("leafcare://auth/confirm-email#access_token=only"))
        assertNull(parseAuthDeeplink("leafcare://auth/reset-password?code=%20"))
    }

    @Test fun garbage_isRejected() {
        assertNull(parseAuthDeeplink("not a url at all"))
        assertNull(parseAuthDeeplink(""))
    }

    @Test fun percentEncodedValues_areDecoded() {
        val link = parseAuthDeeplink("leafcare://auth/confirm-email?code=a%2Fb%3Dc")

        assertEquals(AuthDeeplink.ConfirmEmailCode("a/b=c"), link)
    }
}
