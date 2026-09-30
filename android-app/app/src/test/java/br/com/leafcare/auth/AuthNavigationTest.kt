package br.com.leafcare.auth

import org.junit.Assert.*
import org.junit.Test

/**
 * Navigation-decision tests: pure functions, no Android framework.
 *
 * Awaiting email confirmation is intentionally *not* a navigation:
 * [navigationForAuthResult] returns null and the user stays in the Auth flow
 * while the info message is shown.
 */
class AuthNavigationTest {

    @Test fun authenticatedResult_navigatesToApp() {
        assertEquals(AuthNavigationEvent.NavigateToApp, navigationForAuthResult(true))
    }

    @Test fun failedResult_staysInAuth() {
        // Covers validation failures, wrong credentials and email-confirmation pending.
        assertNull(navigationForAuthResult(false))
    }

    @Test fun existingSession_navigatesToApp() {
        assertEquals(AuthNavigationEvent.NavigateToApp, navigationForSessionRestore(true))
    }

    @Test fun noSession_navigatesToAuth() {
        assertEquals(
            AuthNavigationEvent.NavigateToAuth(),
            navigationForSessionRestore(false)
        )
    }

    @Test fun signOut_navigatesToAuth() {
        assertEquals(AuthNavigationEvent.NavigateToAuth(), navigationAfterSignOut())
    }
}
