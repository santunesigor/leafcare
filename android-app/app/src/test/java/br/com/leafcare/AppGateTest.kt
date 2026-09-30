package br.com.leafcare

import br.com.leafcare.auth.AuthBootstrapState
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Test

/**
 * Auth-gate decision tests: bootstrap first, recovery pending beats any
 * session, then the session decides. Pure functions, no Android framework.
 *
 * Logout clears the session, so the gate falls back to Auth and the disposed
 * main NavHost can never be reached again via Back.
 */
class AppGateTest {

    private fun testSession(): UserSession = UserSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(
            id = "user-id",
            aud = "",
            email = "user@leafcare.test"
        ),
        expiresAt = Clock.System.now()
    )

    @Test fun checking_showsLoading() {
        assertEquals(
            AppGate.Loading,
            appGateDestination(null, AuthBootstrapState.CHECKING)
        )
    }

    @Test fun processingDeeplink_showsLoading() {
        // A callback being processed never renders App/History frames.
        assertEquals(
            AppGate.Loading,
            appGateDestination(testSession(), AuthBootstrapState.PROCESSING_DEEPLINK)
        )
        assertEquals(
            AppGate.Loading,
            appGateDestination(null, AuthBootstrapState.PROCESSING_DEEPLINK)
        )
    }

    @Test fun recoveryPendingWithSession_showsRecoveryNeverApp() {
        assertEquals(
            AppGate.Recovery,
            appGateDestination(testSession(), AuthBootstrapState.RECOVERY_PENDING)
        )
    }

    @Test fun recoveryPendingWithoutSession_showsAuth() {
        assertEquals(
            AppGate.Auth,
            appGateDestination(null, AuthBootstrapState.RECOVERY_PENDING)
        )
    }

    @Test fun readyWithSession_showsMain() {
        assertEquals(
            AppGate.Main,
            appGateDestination(testSession(), AuthBootstrapState.READY)
        )
    }

    @Test fun readyWithoutSession_showsAuth() {
        assertEquals(
            AppGate.Auth,
            appGateDestination(null, AuthBootstrapState.READY)
        )
    }

    @Test fun logoutClearedSession_showsAuth() {
        // After signOut the repository exposes null session/user (see
        // AuthRepositoryTest.signOut_clearsSession); the gate must show Auth.
        assertEquals(
            AppGate.Auth,
            appGateDestination(null, AuthBootstrapState.READY)
        )
    }
}
