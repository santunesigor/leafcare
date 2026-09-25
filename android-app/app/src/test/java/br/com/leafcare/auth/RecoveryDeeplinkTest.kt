package br.com.leafcare.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * Recovery deep-link parser tests: only genuine LeafCare recovery returns
 * are accepted. Pure function, no Android framework.
 */
class RecoveryDeeplinkTest {

    @Test fun pkceCodeLink_isAccepted() {
        val link = parseRecoveryDeeplink("leafcare://auth/reset-password?code=pkce-code-123")

        assertEquals(RecoveryDeeplink.Code("pkce-code-123"), link)
    }

    @Test fun tokenFragmentLink_isAccepted() {
        val link = parseRecoveryDeeplink(
            "leafcare://auth/reset-password#access_token=at123&refresh_token=rt123&expires_in=3600&token_type=bearer&type=recovery"
        )

        assertEquals(RecoveryDeeplink.Tokens("at123", "rt123"), link)
    }

    @Test fun wrongScheme_isRejected() {
        assertNull(parseRecoveryDeeplink("https://example.com/auth/reset-password?code=abc"))
    }

    @Test fun nonRecoveryType_isRejected() {
        assertNull(
            parseRecoveryDeeplink(
                "leafcare://auth/reset-password#access_token=at&refresh_token=rt&type=signup"
            )
        )
    }

    @Test fun incompleteFragment_isRejected() {
        assertNull(parseRecoveryDeeplink("leafcare://auth/reset-password#access_token=only"))
    }

    @Test fun blankCode_isRejected() {
        assertNull(parseRecoveryDeeplink("leafcare://auth/reset-password?code=%20"))
    }

    @Test fun garbage_isRejected() {
        assertNull(parseRecoveryDeeplink("not a url at all"))
        assertNull(parseRecoveryDeeplink(""))
    }

    @Test fun percentEncodedValues_areDecoded() {
        val link = parseRecoveryDeeplink("leafcare://auth/reset-password?code=a%2Fb%3Dc")

        assertEquals(RecoveryDeeplink.Code("a/b=c"), link)
    }
}
