package br.com.leafcare.auth

import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.user.UserInfo
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** In-memory [AuthBackend] for tests. No network is touched. */
internal class FakeBackend(var session: UserSession? = null) : AuthBackend {

    override val sessionStatus: StateFlow<SessionStatus> =
        MutableStateFlow(SessionStatus.NotAuthenticated)

    var signUpCalls = 0
    var lastSignUp: Triple<String, String, String>? = null
    var lastSignUpRedirect: String? = null
    var signInCalls = 0
    var signOutCalls = 0
    val resetRequests = mutableListOf<Pair<String, String>>()
    val exchangeCodeCalls = mutableListOf<String>()
    val importedTokens = mutableListOf<Pair<String, String>>()
    val resendSignupEmails = mutableListOf<String>()
    var updatePasswordCalls = 0
    var lastNewPassword: String? = null
    var failExchange: Boolean = false
    var failImport: Boolean = false
    var failReset: Exception? = null
    var resetGate: (suspend () -> Unit)? = null

    override suspend fun loadFromStorage(): Boolean = session != null

    override suspend fun loadSession(): UserSession? = session

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        redirectUrl: String?
    ) {
        signUpCalls++
        lastSignUp = Triple(email, password, displayName)
        lastSignUpRedirect = redirectUrl
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        signInCalls++
    }

    override suspend fun signOut() {
        signOutCalls++
        session = null
    }

    override suspend fun requestPasswordRecovery(email: String, redirectUrl: String) {
        resetGate?.invoke()
        failReset?.let { throw it }
        resetRequests += email to redirectUrl
    }

    override suspend fun exchangeLinkCode(code: String) {
        if (failExchange) throw IllegalStateException("Email link is invalid or has expired")
        exchangeCodeCalls += code
    }

    override suspend fun importLinkTokens(accessToken: String, refreshToken: String) {
        if (failImport) throw IllegalStateException("Email link is invalid or has expired")
        importedTokens += accessToken to refreshToken
    }

    override suspend fun resendSignupEmail(email: String) {
        resendSignupEmails += email
    }

    override suspend fun updatePassword(newPassword: String) {
        updatePasswordCalls++
        lastNewPassword = newPassword
    }
}

/**
 * Repository behavior tests with a fake [AuthBackend]: no network, no Android framework.
 * Covers the auth runtime behavior: no `!!` on session, defensive signup branch,
 * display_name pass-through and logout.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var backend: FakeBackend

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        backend = FakeBackend()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repositoryWithSession(session: UserSession?): AuthRepository {
        backend.session = session
        return AuthRepository(backend)
    }

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

    @Test fun signUp_validationFailureDoesNotCallAuth() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.signUp("a@b.com", "123456", "   ")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals(0, backend.signUpCalls)
    }

    @Test fun signUp_withSessionAuthenticates() = runTest(testDispatcher) {
        val session = testSession()
        val repository = repositoryWithSession(session)

        val result = repository.signUp("a@b.com", "123456", "Nome Teste")

        assertTrue(result.isSuccess)
        assertEquals(session, result.getOrNull())
        assertEquals(session, repository.session.value)
        assertTrue(repository.hasPersistedSession())
        assertNull(repository.infoMessage.value)
        assertEquals(1, backend.signUpCalls)
    }

    @Test fun signUp_withoutSessionIsDefensiveWithoutCrash() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        // Must not throw (previous code crashed on `session!!` here).
        val result = repository.signUp("a@b.com", "123456", "Nome Teste")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SignupWithoutSessionException)
        assertNull(repository.session.value)
        assertFalse(repository.hasPersistedSession())
        assertEquals("Enviamos um link de confirmação para seu e-mail.", repository.infoMessage.value)
    }

    @Test fun restoreMarksSessionChecked() = runTest(testDispatcher) {
        val repository = AuthRepository(backend)

        // The bootstrap flag flips once the storage attempt finishes; the
        // gate shows only Loading before that (see AppGateTest).
        testScheduler.advanceUntilIdle()
        assertTrue(repository.sessionChecked.value)
    }

    @Test fun signUp_forwardsDisplayName() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        repository.signUp("a@b.com", "123456", "Nome Teste")

        assertEquals(Triple("a@b.com", "123456", "Nome Teste"), backend.lastSignUp)
    }

    @Test fun signIn_validationFailureDoesNotCallAuth() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.signIn("", "123456")

        assertTrue(result.isFailure)
        assertEquals(0, backend.signInCalls)
    }

    @Test fun signIn_withSessionAuthenticates() = runTest(testDispatcher) {
        val session = testSession()
        val repository = repositoryWithSession(session)

        val result = repository.signIn("a@b.com", "123456")

        assertTrue(result.isSuccess)
        assertEquals(session, repository.session.value)
        assertEquals(1, backend.signInCalls)
    }

    @Test fun signOut_clearsSession() = runTest(testDispatcher) {
        val repository = repositoryWithSession(testSession())

        val result = repository.signOut()

        assertTrue(result.isSuccess)
        assertEquals(1, backend.signOutCalls)
        assertNull(repository.session.value)
        assertNull(repository.user.value)
        assertFalse(repository.hasPersistedSession())
    }

    @Test fun requestPasswordReset_sendsLinkRedirect() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.requestPasswordReset("a@b.com")

        assertTrue(result.isSuccess)
        assertEquals(
            listOf("a@b.com" to "leafcare://auth/reset-password"),
            backend.resetRequests
        )
        assertNotNull(repository.infoMessage.value)
    }

    @Test fun requestPasswordReset_blankEmailFailsWithoutBackendCall() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.requestPasswordReset("  ")

        assertTrue(result.isFailure)
        assertTrue(backend.resetRequests.isEmpty())
    }

    @Test fun requestPasswordReset_rateLimitExplainsWait() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)
        backend.failReset = IllegalStateException(
            "POST /auth/v1/recover -> 429 {\"error_code\":\"over_email_send_rate_limit\"}"
        )

        val result = repository.requestPasswordReset("a@b.com")

        assertTrue(result.isFailure)
        assertEquals(
            "Você solicitou um e-mail recentemente. Aguarde um pouco antes de tentar novamente.",
            repository.error.value
        )
        assertNull(repository.session.value)
    }

    @Test fun requestPasswordReset_networkErrorExplainsConnection() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)
        backend.failReset = IllegalStateException("Unable to resolve host \"example.supabase.co\"")

        val result = repository.requestPasswordReset("a@b.com")

        assertTrue(result.isFailure)
        assertEquals(
            "Não foi possível enviar o e-mail. Verifique sua conexão e tente novamente.",
            repository.error.value
        )
    }

    @Test fun completeRecoveryWithCode_authenticates() = runTest(testDispatcher) {
        val session = testSession()
        val repository = repositoryWithSession(session)

        val result = repository.completeEmailLink(AuthDeeplink.RecoveryCode("pkce-code"))

        assertTrue(result.isSuccess)
        assertEquals(listOf("pkce-code"), backend.exchangeCodeCalls)
        assertEquals(session, repository.session.value)
        assertTrue(repository.hasPersistedSession())
    }

    @Test fun completeRecoveryWithTokens_authenticates() = runTest(testDispatcher) {
        val session = testSession()
        val repository = repositoryWithSession(session)

        val result = repository.completeEmailLink(
            AuthDeeplink.RecoveryTokens("access-123", "refresh-123")
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("access-123" to "refresh-123"), backend.importedTokens)
        assertEquals(session, repository.session.value)
    }

    @Test fun completeConfirmEmailWithCode_authenticates() = runTest(testDispatcher) {
        val session = testSession()
        val repository = repositoryWithSession(session)

        val result = repository.completeEmailLink(AuthDeeplink.ConfirmEmailCode("pkce-code"))

        assertTrue(result.isSuccess)
        assertEquals(listOf("pkce-code"), backend.exchangeCodeCalls)
        assertEquals(session, repository.session.value)
    }

    @Test fun completeConfirmEmailWithTokens_authenticates() = runTest(testDispatcher) {
        val session = testSession()
        val repository = repositoryWithSession(session)

        val result = repository.completeEmailLink(
            AuthDeeplink.ConfirmEmailTokens("access-123", "refresh-123")
        )

        assertTrue(result.isSuccess)
        assertEquals(listOf("access-123" to "refresh-123"), backend.importedTokens)
        assertEquals(session, repository.session.value)
    }

    @Test fun signUp_usesConfirmEmailRedirect() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        repository.signUp("a@b.com", "123456", "Nome Teste")

        assertEquals("leafcare://auth/confirm-email", backend.lastSignUpRedirect)
    }

    @Test fun resendSignupEmail_recordsEmailAndInforms() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.resendSignupEmail("a@b.com")

        assertTrue(result.isSuccess)
        assertEquals(listOf("a@b.com"), backend.resendSignupEmails)
        assertNotNull(repository.infoMessage.value)
    }

    @Test fun completeExpiredLink_failsWithoutSession() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)
        backend.failExchange = true

        val result = repository.completeEmailLink(AuthDeeplink.RecoveryCode("expired"))

        assertTrue(result.isFailure)
        assertEquals("Link inválido ou expirado.", repository.error.value)
        assertNull(repository.session.value)
        assertFalse(repository.hasPersistedSession())
    }

    @Test fun updatePassword_shortPasswordFailsWithoutBackendCall() = runTest(testDispatcher) {
        val repository = repositoryWithSession(testSession())

        val result = repository.updatePassword("123")

        assertTrue(result.isFailure)
        assertEquals(0, backend.updatePasswordCalls)
    }

    @Test fun updatePassword_successForwardsNewPassword() = runTest(testDispatcher) {
        val repository = repositoryWithSession(testSession())

        val result = repository.updatePassword("nova-senha-123")

        assertTrue(result.isSuccess)
        assertEquals(1, backend.updatePasswordCalls)
        assertEquals("nova-senha-123", backend.lastNewPassword)
        assertEquals("Senha alterada com sucesso.", repository.infoMessage.value)
    }
}