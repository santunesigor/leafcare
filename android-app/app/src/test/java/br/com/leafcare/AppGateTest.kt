package br.com.leafcare

import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Test

/**
 * Auth-gate decision tests: the single auth state decides which root is visible.
 * Pure functions, no Android framework.
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

    @Test fun restoringWithoutSession_showsLoading() {
        assertEquals(AppGate.Loading, appGateDestination(null, true))
    }

    @Test fun noSessionWithoutLoading_showsAuth() {
        assertEquals(AppGate.Auth, appGateDestination(null, false))
    }

    @Test fun sessionShowsMain_evenWhileLoading() {
        assertEquals(AppGate.Main, appGateDestination(testSession(), true))
    }

    @Test fun sessionShowsMain() {
        assertEquals(AppGate.Main, appGateDestination(testSession(), false))
    }

    @Test fun logoutClearedSession_showsAuth() {
        // After signOut the repository exposes null session/user (see
        // AuthRepositoryTest.signOut_clearsSession); the gate must show Auth.
        assertEquals(AppGate.Auth, appGateDestination(null, false))
    }
}
