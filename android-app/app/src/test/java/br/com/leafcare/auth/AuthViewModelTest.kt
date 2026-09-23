package br.com.leafcare.auth

import android.app.Application
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

    @Test fun requestPasswordResetSuccess_goesToRecoveryCode() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")

        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertEquals(listOf("a@b.com"), backend.resetEmails)
        assertEquals(AuthScreen.RecoveryCode, viewModel.uiState.value.currentScreen)
    }

    @Test fun verifyRecoveryCodeSuccess_goesToNewPassword() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        viewModel.setRecoveryCode("123456")

        viewModel.verifyRecoveryCode()
        advanceUntilIdle()

        assertEquals(1, backend.verifyRecoveryCalls)
        assertEquals(AuthScreen.NewPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun mismatchedNewPasswords_blockUpdateWithVisibleError() = runTest(dispatcher) {
        viewModel.setNewPassword("nova-senha-123")
        viewModel.setConfirmNewPassword("outra-senha")

        viewModel.updatePassword()
        advanceUntilIdle()

        assertEquals(0, backend.updatePasswordCalls)
        assertEquals("As senhas não coincidem", viewModel.error.value)
    }

    @Test fun matchingNewPasswords_updateAndNavigateToApp() = runTest(dispatcher) {
        viewModel.setNewPassword("nova-senha-123")
        viewModel.setConfirmNewPassword("nova-senha-123")

        val events = mutableListOf<AuthNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events.add(it) }
        }

        viewModel.updatePassword()
        advanceUntilIdle()

        assertEquals(1, backend.updatePasswordCalls)
        assertTrue(events.any { it is AuthNavigationEvent.NavigateToApp })
        job.cancel()
    }
}
