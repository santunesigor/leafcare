package br.com.leafcare.auth

import android.app.Application
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * ViewModel behavior tests with a fake [FakeBackend]: no network, plain JVM.
 * The secondary [AuthViewModel] constructor allows injecting an [AuthRepository]
 * built on the fake, so screen flow, password gating and navigation decisions
 * are exercised through the real ViewModel.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var backend: FakeBackend
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        backend = FakeBackend()
        viewModel = AuthViewModel(Application(), AuthRepository(backend))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test fun loginToSignUp_updatesCurrentScreen() {
        viewModel.setScreen(AuthScreen.SignUp)

        assertEquals(AuthScreen.SignUp, viewModel.uiState.value.currentScreen)
    }

    @Test fun loginToForgotPassword_updatesCurrentScreen() {
        viewModel.setScreen(AuthScreen.ForgotPassword)

        assertEquals(AuthScreen.ForgotPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun signUpToLogin_updatesCurrentScreen() {
        viewModel.setScreen(AuthScreen.SignUp)
        viewModel.setScreen(AuthScreen.Login)

        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
    }

    @Test fun forgotPasswordToLogin_updatesCurrentScreen() {
        viewModel.setScreen(AuthScreen.ForgotPassword)
        viewModel.setScreen(AuthScreen.Login)

        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
    }

    @Test fun mismatchedPasswords_blockSignUpWithVisibleError() = runTest(dispatcher) {
        viewModel.setDisplayName("Nome Teste")
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")
        viewModel.setConfirmPassword("outra-senha")

        viewModel.signUp()
        advanceUntilIdle()

        assertEquals(0, backend.signUpCalls)
        assertEquals("As senhas não coincidem", viewModel.error.value)
    }

    @Test fun matchingPasswords_allowSignUp() = runTest(dispatcher) {
        viewModel.setDisplayName("Nome Teste")
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")
        viewModel.setConfirmPassword("senha123")

        viewModel.signUp()
        advanceUntilIdle()

        assertEquals(1, backend.signUpCalls)
    }

    @Test fun signIn_callsBackendAction() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")

        viewModel.signIn()
        advanceUntilIdle()

        assertEquals(1, backend.signInCalls)
        assertEquals(0, backend.signUpCalls)
    }

    @Test fun signupWithoutSession_goesToVerifyEmailWithoutAppNavigation() = runTest(dispatcher) {
        backend.session = null
        viewModel.setDisplayName("Nome Teste")
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")
        viewModel.setConfirmPassword("senha123")

        val events = mutableListOf<AuthNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events.add(it) }
        }

        viewModel.signUp()
        advanceUntilIdle()

        assertTrue(events.none { it is AuthNavigationEvent.NavigateToApp })
        assertEquals(
            "Enviamos um link de confirmação para seu e-mail.",
            viewModel.infoMessage.value
        )
        assertEquals(AuthScreen.VerifyEmail, viewModel.uiState.value.currentScreen)
        job.cancel()
    }

    @Test fun signupSuccess_navigatesToAppDirectly() = runTest(dispatcher) {
        backend.session = testSession()
        viewModel.setScreen(AuthScreen.SignUp)
        viewModel.setDisplayName("Nome Teste")
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")
        viewModel.setConfirmPassword("senha123")

        val events = mutableListOf<AuthNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events.add(it) }
        }

        viewModel.signUp()
        advanceUntilIdle()

        assertTrue(events.any { it is AuthNavigationEvent.NavigateToApp })
        job.cancel()
    }

    @Test fun authScreensContainExpectedSet() {
        // Regression guard: signup confirmation OTP stays out (no corporate
        // SMTP domain). VerifyEmail is the link-confirmation screen (no code).
        assertEquals(
            setOf(AuthScreen.Login, AuthScreen.SignUp, AuthScreen.VerifyEmail, AuthScreen.ForgotPassword, AuthScreen.NewPassword, AuthScreen.Profile),
            AuthScreen.values().toSet()
        )
    }

    @Test fun loginErrorClearedWhenNavigatingToSignUp() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("errada")
        backend.session = null

        viewModel.signIn()
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        viewModel.setScreen(AuthScreen.SignUp)

        assertNull(viewModel.error.value)
        assertNull(viewModel.infoMessage.value)
        assertEquals(AuthScreen.SignUp, viewModel.uiState.value.currentScreen)
    }

    @Test fun signupErrorStaysOnSignUpAndClearsOnLogin() = runTest(dispatcher) {
        viewModel.setScreen(AuthScreen.SignUp)
        viewModel.setDisplayName("Nome Teste")
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")
        viewModel.setConfirmPassword("outra-senha")

        // Error is visible on its own screen (not hidden early).
        viewModel.signUp()
        advanceUntilIdle()
        assertEquals("As senhas não coincidem", viewModel.error.value)
        assertEquals(AuthScreen.SignUp, viewModel.uiState.value.currentScreen)

        viewModel.setScreen(AuthScreen.Login)

        assertNull(viewModel.error.value)
    }

    @Test fun loginErrorClearedWhenNavigatingToForgotPassword() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("errada")
        backend.session = null

        viewModel.signIn()
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        viewModel.setScreen(AuthScreen.ForgotPassword)

        assertNull(viewModel.error.value)
        assertNull(viewModel.infoMessage.value)
        assertEquals(AuthScreen.ForgotPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun signOutResetsToInitialLoginScreen() = runTest(dispatcher) {
        viewModel.setScreen(AuthScreen.SignUp)
        viewModel.setEmail("a@b.com")

        val events = mutableListOf<AuthNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events.add(it) }
        }

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
        assertEquals("", viewModel.uiState.value.email)
        assertTrue(events.any { it is AuthNavigationEvent.NavigateToAuth })
        job.cancel()
    }

    @Test fun requestPasswordReset_showsInfoAndStays() = runTest(dispatcher) {
        viewModel.setScreen(AuthScreen.ForgotPassword)
        viewModel.setEmail("a@b.com")

        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertEquals(
            listOf("a@b.com" to "leafcare://auth/reset-password"),
            backend.resetRequests
        )
        assertNotNull(viewModel.infoMessage.value)
        assertEquals(AuthScreen.ForgotPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun recoveryDeeplinkCode_opensNewPassword() = runTest(dispatcher) {
        // No repo session yet; the fake backend yields one after the exchange.
        backend.session = testSession()

        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=pkce-code")
        advanceUntilIdle()

        assertEquals(listOf("pkce-code"), backend.exchangeCodeCalls)
        assertTrue(viewModel.recoveryMode.value)
        assertEquals(AuthScreen.NewPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun recoveryDeeplinkTokens_opensNewPassword() = runTest(dispatcher) {
        backend.session = testSession()

        viewModel.handleAuthDeeplink(
            "leafcare://auth/reset-password#access_token=at&refresh_token=rt&type=recovery"
        )
        advanceUntilIdle()

        assertEquals(listOf("at" to "rt"), backend.importedTokens)
        assertTrue(viewModel.recoveryMode.value)
        assertEquals(AuthScreen.NewPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun recoveryDeeplinkInvalid_isIgnored() = runTest(dispatcher) {
        backend.session = null
        viewModel.setScreen(AuthScreen.Login)

        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=%20")
        viewModel.handleAuthDeeplink("https://evil.example/x")
        advanceUntilIdle()

        assertTrue(backend.exchangeCodeCalls.isEmpty())
        assertTrue(backend.importedTokens.isEmpty())
        assertFalse(viewModel.recoveryMode.value)
        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
    }

    @Test fun recoveryDeeplinkIgnoredWhenAuthenticated() = runTest(dispatcher) {
        backend.session = testSession()
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")
        viewModel.signIn()
        advanceUntilIdle()
        viewModel.setScreen(AuthScreen.Login)

        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=pkce-code")
        advanceUntilIdle()

        // A logged-in session is never replaced by a link tap.
        assertTrue(backend.exchangeCodeCalls.isEmpty())
        assertFalse(viewModel.recoveryMode.value)
    }

    @Test fun confirmDeeplinkCode_authenticatesWithoutNewPassword() = runTest(dispatcher) {
        backend.session = testSession()

        val events = mutableListOf<AuthNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events.add(it) }
        }

        viewModel.handleAuthDeeplink("leafcare://auth/confirm-email?code=pkce-code")
        advanceUntilIdle()

        assertEquals(listOf("pkce-code"), backend.exchangeCodeCalls)
        // Confirm opens the app directly: no recovery mode, no NewPassword.
        assertFalse(viewModel.recoveryMode.value)
        assertTrue(events.none { it is AuthNavigationEvent.NavigateToApp })
        job.cancel()
    }

    @Test fun confirmDeeplinkTokens_authenticatesWithoutNewPassword() = runTest(dispatcher) {
        backend.session = testSession()

        viewModel.handleAuthDeeplink(
            "leafcare://auth/confirm-email#access_token=at&refresh_token=rt&type=signup"
        )
        advanceUntilIdle()

        assertEquals(listOf("at" to "rt"), backend.importedTokens)
        assertFalse(viewModel.recoveryMode.value)
    }

    @Test fun confirmDeeplinkInvalid_doesNotAuthenticate() = runTest(dispatcher) {
        backend.session = null
        viewModel.setScreen(AuthScreen.Login)

        viewModel.handleAuthDeeplink("leafcare://auth/confirm-email?code=%20")
        viewModel.handleAuthDeeplink("leafcare://auth/unknown?code=abc")
        advanceUntilIdle()

        assertTrue(backend.exchangeCodeCalls.isEmpty())
        assertTrue(backend.importedTokens.isEmpty())
        assertFalse(viewModel.recoveryMode.value)
        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
        assertNull(viewModel.session.value)
    }

    @Test fun resendSignupEmail_recordsEmailAndInforms() = runTest(dispatcher) {
        viewModel.setScreen(AuthScreen.VerifyEmail)
        viewModel.setEmail("a@b.com")

        viewModel.resendSignupEmail()
        advanceUntilIdle()

        assertEquals(listOf("a@b.com"), backend.resendSignupEmails)
        assertNotNull(viewModel.infoMessage.value)
    }

    @Test fun mismatchedNewPasswords_blockUpdateWithVisibleError() = runTest(dispatcher) {
        viewModel.setScreen(AuthScreen.NewPassword)
        viewModel.setNewPassword("nova-senha-123")
        viewModel.setConfirmNewPassword("outra-senha")

        viewModel.updatePassword()
        advanceUntilIdle()

        assertEquals(0, backend.updatePasswordCalls)
        assertEquals("As senhas não coincidem", viewModel.error.value)
        assertEquals(AuthScreen.NewPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun matchingNewPasswords_updateAndNavigateToApp() = runTest(dispatcher) {
        backend.session = testSession()
        viewModel.setScreen(AuthScreen.NewPassword)
        viewModel.setNewPassword("nova-senha-123")
        viewModel.setConfirmNewPassword("nova-senha-123")

        val events = mutableListOf<AuthNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events.add(it) }
        }

        viewModel.updatePassword()
        advanceUntilIdle()

        assertEquals(1, backend.updatePasswordCalls)
        assertFalse(viewModel.recoveryMode.value)
        assertTrue(events.any { it is AuthNavigationEvent.NavigateToApp })
        job.cancel()
    }

    @Test fun displayNameOf_readsMetadata() {
        val user = UserInfo(
            id = "user-id",
            aud = "",
            email = "a@b.com",
            userMetadata = buildJsonObject {
                put("display_name", "Maria Silva")
            }
        )

        assertEquals("Maria Silva", displayNameOf(user))
    }

    @Test fun displayNameOf_nullWithoutUserOrMetadata() {
        assertNull(displayNameOf(null))
        assertNull(
            displayNameOf(
                UserInfo(
                    id = "user-id",
                    aud = "",
                    email = "a@b.com"
                )
            )
        )
    }

    private fun testSession() = UserSession(
        accessToken = "access-token",
        refreshToken = "refresh-token",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(
            id = "user-id",
            aud = "",
            email = "a@b.com"
        ),
        expiresAt = Clock.System.now()
    )
}
