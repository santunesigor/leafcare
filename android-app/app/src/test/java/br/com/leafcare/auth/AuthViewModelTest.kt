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

    @Test fun signupWithoutSession_goesToLoginWithoutAppNavigation() = runTest(dispatcher) {
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
            "Cadastro concluído. Entre com seu e-mail e senha.",
            viewModel.infoMessage.value
        )
        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
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

    @Test fun authScreensContainNoOtpScreens() {
        // Regression guard: signup confirmation and recovery OTP were removed
        // from this MVP (no corporate SMTP domain yet).
        assertEquals(
            setOf(AuthScreen.Login, AuthScreen.SignUp, AuthScreen.ForgotPassword, AuthScreen.Profile),
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
