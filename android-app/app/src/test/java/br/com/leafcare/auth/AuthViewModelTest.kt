package br.com.leafcare.auth

import android.app.Application
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
/** In-memory [RecoveryPendingStore] for tests. */
internal class FakeRecoveryPendingStore(initial: Boolean = false) : RecoveryPendingStore {
    override var isPending: Boolean = initial
    override var lastRecoveryRequestAt: Long = 0L
    override fun clear() {
        isPending = false
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var backend: FakeBackend
    private lateinit var recoveryStore: FakeRecoveryPendingStore
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        backend = FakeBackend()
        recoveryStore = FakeRecoveryPendingStore()
        viewModel = AuthViewModel(Application(), AuthRepository(backend), recoveryStore)
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
        // No duplicated message: the static screen subtitle covers it.
        assertNull(viewModel.infoMessage.value)
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

    @Test fun cooldownRemainingSeconds_countsDown() {
        val now = 1_700_000_060_000L
        assertEquals(60, cooldownRemainingSeconds(now, now = now))
        assertEquals(18, cooldownRemainingSeconds(now - 42_000L, now = now))
        assertEquals(0, cooldownRemainingSeconds(now - 61_000L, now = now))
        assertEquals(0, cooldownRemainingSeconds(0L, now = now))
    }

    @Test fun cooldownBaseForRemaining_alignsWithServerCountdown() {
        val now = 1_700_000_060_000L
        val base = cooldownBaseForRemaining(18, now = now)

        assertEquals(18, cooldownRemainingSeconds(base, now = now))
    }

    @Test fun extractRateLimitSeconds_readsServerCountdown() {
        assertEquals(
            25,
            extractRateLimitSeconds("For security purposes, you can only request this after 25 seconds.")
        )
        assertNull(extractRateLimitSeconds("some other error"))
    }

    @Test fun findRateLimitSeconds_walksCauseChain() {
        val root = IllegalStateException("after 21 seconds")
        val wrapped = RuntimeException("sanitized", root)

        assertEquals(21, findRateLimitSeconds(wrapped))
        assertNull(findRateLimitSeconds(RuntimeException("sanitized")))
        assertNull(findRateLimitSeconds(null))
    }

    @Test fun successfulRequest_storesCooldownTimestamp() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")

        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertTrue(recoveryStore.lastRecoveryRequestAt > 0L)
        assertTrue(viewModel.cooldownSeconds.value in 1..60)
        assertTrue(viewModel.recoveryRequestSent.value)
    }

    @Test fun rateLimitResponse_syncsCooldownFromServer() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        backend.failReset = IllegalStateException(
            "POST /auth/v1/recover -> 429 {\"error_code\":\"over_email_send_rate_limit\"," +
                "\"msg\":\"For security purposes, you can only request this after 25 seconds.\"}"
        )

        viewModel.requestPasswordReset()
        advanceUntilIdle()

        // Local cooldown mirrors the server countdown (~25s window).
        assertTrue(viewModel.cooldownSeconds.value in 1..25)
        assertEquals(
            "Você solicitou um link recentemente. Tente novamente em 25 segundos.",
            viewModel.error.value
        )
    }

    @Test fun globalLimit_startsNoCooldownAndKeepsEmail() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        var attempts = 0
        backend.resetGate = { attempts++ }
        backend.failReset = IllegalStateException(
            "POST /auth/v1/recover -> 429 {\"error_code\":\"over_email_send_rate_limit\"," +
                "\"msg\":\"email rate limit exceeded\"}"
        )

        viewModel.requestPasswordReset()
        advanceUntilIdle()

        // No invented countdown: the service limit carries no retry time.
        assertEquals(1, attempts)
        assertEquals(0L, recoveryStore.lastRecoveryRequestAt)
        assertEquals(0, viewModel.cooldownSeconds.value)
        assertFalse(viewModel.recoveryRequestSent.value)
        assertFalse(viewModel.resetSending.value)
        assertEquals(
            "O envio de e-mails está temporariamente limitado. Aguarde alguns minutos e tente novamente.",
            viewModel.error.value
        )
        // The typed address is preserved for a later manual retry.
        assertEquals("a@b.com", viewModel.uiState.value.email)
    }

    @Test fun successAfterGlobalLimit_clearsErrorAndCoolsDown() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        var attempts = 0
        backend.resetGate = { attempts++ }
        backend.failReset = IllegalStateException(
            "POST /auth/v1/recover -> 429 {\"error_code\":\"over_email_send_rate_limit\"," +
                "\"msg\":\"email rate limit exceeded\"}"
        )
        viewModel.requestPasswordReset()
        advanceUntilIdle()
        assertNotNull(viewModel.error.value)

        // The gate was released and no cooldown was stored, so a later manual
        // retry reaches the backend exactly once and succeeds like new.
        backend.failReset = null
        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertEquals(1, backend.resetRequests.size)
        assertNull(viewModel.error.value)
        assertEquals(
            "E-mail de recuperação enviado. Verifique sua caixa de entrada.",
            viewModel.infoMessage.value
        )
        assertTrue(viewModel.cooldownSeconds.value in 1..60)
    }

    @Test fun usedLink_appendsCooldownRemainder() = runTest(dispatcher) {
        backend.session = null
        recoveryStore.lastRecoveryRequestAt = System.currentTimeMillis() - 42_000L
        backend.failExchange = true

        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=used-code")
        advanceUntilIdle()

        val error = viewModel.error.value.orEmpty()
        assertTrue(error.startsWith("Este link já foi usado ou expirou."))
        assertTrue(error.contains("segundos"))
    }

    @Test fun concurrentTaps_singleFlight() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        val executor = Executors.newFixedThreadPool(8)
        try {
            val start = CountDownLatch(1)
            val done = CountDownLatch(20)
            repeat(20) {
                executor.submit {
                    start.await()
                    viewModel.requestPasswordReset()
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            advanceUntilIdle()
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, backend.resetRequests.size)
    }

    @Test fun resendSignupEmail_concurrentTaps_singleFlightAndClearsLoading() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        backend.resendGate = {
            started.complete(Unit)
            finish.await()
        }
        val executor = Executors.newFixedThreadPool(8)
        try {
            val start = CountDownLatch(1)
            val done = CountDownLatch(20)
            repeat(20) {
                executor.submit {
                    start.await()
                    viewModel.resendSignupEmail()
                    done.countDown()
                }
            }
            start.countDown()
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue(viewModel.resendSending.value)

            runCurrent()
            assertTrue(started.isCompleted)
            repeat(20) { viewModel.resendSignupEmail() }
            assertEquals(1, backend.resendSignupEmails.size)

            finish.complete(Unit)
            advanceUntilIdle()
        } finally {
            executor.shutdownNow()
        }

        assertFalse(viewModel.resendSending.value)
        assertEquals(1, backend.resendSignupEmails.size)
    }

    @Test fun resendSignupEmail_errorClearsLoadingAndAllowsRetry() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        backend.failResend = IllegalStateException("429 error_code=over_email_send_rate_limit")

        viewModel.resendSignupEmail()
        advanceUntilIdle()

        assertFalse(viewModel.resendSending.value)
        assertTrue(viewModel.error.value.orEmpty().contains("temporariamente limitado"))
        assertFalse(viewModel.error.value.orEmpty().contains("over_email_send_rate_limit"))

        backend.failResend = null
        viewModel.resendSignupEmail()
        advanceUntilIdle()
        assertEquals(2, backend.resendSignupEmails.size)
        assertFalse(viewModel.resendSending.value)
    }

    @Test fun twentyRapidTaps_sendExactlyOneRequest() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")

        repeat(20) { viewModel.requestPasswordReset() }
        advanceUntilIdle()

        assertEquals(1, backend.resetRequests.size)
    }

    @Test fun restart_preservesRetryAt() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")
        viewModel.requestPasswordReset()
        advanceUntilIdle()
        val stored = recoveryStore.lastRecoveryRequestAt
        assertTrue(stored > 0L)

        // New ViewModel instance, same persisted store: same countdown.
        val restarted = AuthViewModel(
            Application(),
            AuthRepository(FakeBackend()),
            recoveryStore
        )
        assertEquals(
            cooldownRemainingSeconds(stored),
            restarted.refreshCooldown()
        )
        assertTrue(restarted.recoveryRequestSent.value)
    }

    @Test fun cooldownRemainingSeconds_roundsUp() {
        val now = 1_700_000_060_000L
        // 0.4s left still shows 1s; the button only enables at zero.
        assertEquals(1, cooldownRemainingSeconds(now - 59_600L, now = now))
        assertEquals(0, cooldownRemainingSeconds(now - 60_000L, now = now))
        assertEquals(0, cooldownRemainingSeconds(now - 60_001L, now = now))
    }
    @Test fun doubleSubmit_sendsExactlyOneRequest() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")

        // Two synchronous taps before any dispatch: still a single POST.
        viewModel.requestPasswordReset()
        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertEquals(1, backend.resetRequests.size)
    }

    @Test fun buttonBlockedDuringActiveCooldown() = runTest(dispatcher) {
        recoveryStore.lastRecoveryRequestAt = System.currentTimeMillis() - 10_000L
        viewModel.setEmail("a@b.com")

        // Matches the disabled button, including entries that bypass it.
        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertEquals(0, backend.resetRequests.size)
    }

    @Test fun buttonUsableAfterCooldownExpiry() = runTest(dispatcher) {
        recoveryStore.lastRecoveryRequestAt = System.currentTimeMillis() - 61_000L
        viewModel.setEmail("a@b.com")

        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertEquals(1, backend.resetRequests.size)
    }

    @Test fun immediateRetryAfterResponse_isRefusedByCooldown() = runTest(dispatcher) {
        viewModel.setEmail("a@b.com")

        viewModel.requestPasswordReset()
        advanceUntilIdle()
        assertFalse(viewModel.resetSending.value)

        // Cooldown just started: an immediate retry is refused structurally,
        // exactly like the disabled button. No second POST.
        viewModel.requestPasswordReset()
        advanceUntilIdle()

        assertEquals(1, backend.resetRequests.size)
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
        assertEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertEquals(AuthScreen.NewPassword, viewModel.uiState.value.currentScreen)
    }

    @Test fun recoveryDeeplinkTokens_opensNewPassword() = runTest(dispatcher) {
        backend.session = testSession()

        viewModel.handleAuthDeeplink(
            "leafcare://auth/reset-password#access_token=at&refresh_token=rt&type=recovery"
        )
        advanceUntilIdle()

        assertEquals(listOf("at" to "rt"), backend.importedTokens)
        assertEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
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
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
    }

    @Test fun recoveryDeeplinkIgnoredWhenAuthenticated() = runTest(dispatcher) {        backend.session = testSession()
        viewModel.setEmail("a@b.com")
        viewModel.setPassword("senha123")
        viewModel.signIn()
        advanceUntilIdle()
        viewModel.setScreen(AuthScreen.Login)

        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=pkce-code")
        advanceUntilIdle()

        // A logged-in session is never replaced by a link tap.
        assertTrue(backend.exchangeCodeCalls.isEmpty())
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
    }

    @Test fun cancelRecovery_signsOutAndReturnsToForgotPassword() = runTest(dispatcher) {
        backend.session = testSession()
        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=pkce-code")
        advanceUntilIdle()
        assertEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertEquals(AuthScreen.NewPassword, viewModel.uiState.value.currentScreen)
        // Cooldown state survives the cancel (server stays the authority).
        recoveryStore.lastRecoveryRequestAt = 1_700_000_000_000L

        val events = mutableListOf<AuthNavigationEvent>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.navigation.collect { events.add(it) }
        }

        viewModel.cancelRecovery()
        advanceUntilIdle()

        // Recovery session revoked, marker cleared, straight to ForgotPassword
        // (never App, never Login first), cooldown preserved.
        assertEquals(1, backend.signOutCalls)
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertFalse(recoveryStore.isPending)
        assertNull(viewModel.session.value)
        assertEquals(AuthScreen.ForgotPassword, viewModel.uiState.value.currentScreen)
        assertEquals(
            "Recuperação cancelada. Solicite um novo link.",
            viewModel.infoMessage.value
        )
        assertEquals(1_700_000_000_000L, recoveryStore.lastRecoveryRequestAt)
        assertTrue(events.none { it is AuthNavigationEvent.NavigateToApp })
        job.cancel()
    }

    @Test fun expiredLinkAfterPending_doesNotConserveStaleSession() = runTest(dispatcher) {
        backend.session = testSession()
        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=first-code")
        advanceUntilIdle()
        assertEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)

        // A second, expired link drops the stale session and fails loudly.
        backend.session = null
        backend.failExchange = true
        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=expired-code")
        advanceUntilIdle()

        assertEquals(1, backend.signOutCalls)
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertFalse(recoveryStore.isPending)
        assertNull(viewModel.session.value)
        assertEquals(
            "Este link já foi usado ou expirou. Solicite um novo e-mail de recuperação.",
            viewModel.error.value
        )
    }

    @Test fun recoverySuccess_persistsPendingMarker() = runTest(dispatcher) {
        backend.session = testSession()

        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=pkce-code")
        advanceUntilIdle()

        assertEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertTrue(recoveryStore.isPending)
    }

    @Test fun updatePasswordSuccess_clearsPendingMarker() = runTest(dispatcher) {
        backend.session = testSession()
        viewModel.handleAuthDeeplink("leafcare://auth/reset-password?code=pkce-code")
        advanceUntilIdle()
        assertTrue(recoveryStore.isPending)

        viewModel.setNewPassword("nova-senha-123")
        viewModel.setConfirmNewPassword("nova-senha-123")
        viewModel.updatePassword()
        advanceUntilIdle()

        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertFalse(recoveryStore.isPending)
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
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
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
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
    }

    @Test fun confirmDeeplinkInvalid_doesNotAuthenticate() = runTest(dispatcher) {
        backend.session = null
        viewModel.setScreen(AuthScreen.Login)

        viewModel.handleAuthDeeplink("leafcare://auth/confirm-email?code=%20")
        viewModel.handleAuthDeeplink("leafcare://auth/unknown?code=abc")
        advanceUntilIdle()

        assertTrue(backend.exchangeCodeCalls.isEmpty())
        assertTrue(backend.importedTokens.isEmpty())
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
        assertEquals(AuthScreen.Login, viewModel.uiState.value.currentScreen)
        assertNull(viewModel.session.value)
    }

    @Test fun noteDeeplink_setsProcessingSynchronously() {
        backend.session = null

        // No coroutine dispatch needed: the gate must see PROCESSING on the
        // very first composition after the Activity intent arrives.
        viewModel.noteDeeplink("leafcare://auth/reset-password?code=pkce-code")

        assertEquals(AuthBootstrapState.PROCESSING_DEEPLINK, viewModel.bootstrap.value)
    }

    @Test fun bootstrapRestoredFromPersistedMarker() {
        val restarted = AuthViewModel(
            Application(),
            AuthRepository(FakeBackend()),
            FakeRecoveryPendingStore(initial = true)
        )

        // Process death with a pending recovery reopens straight into it.
        assertEquals(AuthBootstrapState.RECOVERY_PENDING, restarted.bootstrap.value)
    }

    @Test fun cancelDialog_showAndDismiss() = runTest(dispatcher) {
        assertFalse(viewModel.showCancelDialog.value)

        viewModel.requestCancelRecovery()
        assertTrue(viewModel.showCancelDialog.value)

        viewModel.dismissCancelDialog()
        assertFalse(viewModel.showCancelDialog.value)
    }

    @Test fun resendSignupEmail_showsOwnMessage() = runTest(dispatcher) {
        viewModel.setScreen(AuthScreen.VerifyEmail)
        viewModel.setEmail("a@b.com")

        viewModel.resendSignupEmail()
        advanceUntilIdle()

        assertEquals(listOf("a@b.com"), backend.resendSignupEmails)
        assertEquals("E-mail de confirmação reenviado.", viewModel.infoMessage.value)
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
        assertNotEquals(AuthBootstrapState.RECOVERY_PENDING, viewModel.bootstrap.value)
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
