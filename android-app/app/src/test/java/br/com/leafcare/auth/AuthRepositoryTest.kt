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
    var signInCalls = 0
    var signOutCalls = 0
    val resetEmails = mutableListOf<String>()
    var verifyRecoveryCalls = 0
    var lastVerify: Pair<String, String>? = null
    var updatePasswordCalls = 0
    var lastNewPassword: String? = null

    override suspend fun loadFromStorage(): Boolean = session != null

    override suspend fun loadSession(): UserSession? = session

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String) {
        signUpCalls++
        lastSignUp = Triple(email, password, displayName)
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        signInCalls++
    }

    override suspend fun signOut() {
        signOutCalls++
        session = null
    }

    override suspend fun resetPasswordForEmail(email: String) {
        resetEmails += email
    }

    override suspend fun verifyRecoveryCode(email: String, code: String) {
        verifyRecoveryCalls++
        lastVerify = email to code
    }

    override suspend fun updatePassword(newPassword: String) {
        updatePasswordCalls++
        lastNewPassword = newPassword
    }
}

/**
 * Repository behavior tests with a fake [AuthBackend]: no network, no Android framework.
 * Covers the auth runtime behavior: no `!!` on session, defensive signup branch,
 * display_name pass-through, in-app OTP recovery, logout and password reset.
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
        assertEquals("Cadastro concluído. Entre com seu e-mail e senha.", repository.infoMessage.value)
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
        assertFalse(repository.hasPersistedSession())
    }

    @Test fun requestPasswordReset_recordsEmailAndInforms() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.requestPasswordReset("a@b.com")

        assertTrue(result.isSuccess)
        assertEquals(listOf("a@b.com"), backend.resetEmails)
        assertNotNull(repository.infoMessage.value)
    }

    @Test fun requestPasswordReset_blankEmailFails() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.requestPasswordReset("  ")

        assertTrue(result.isFailure)
        assertTrue(backend.resetEmails.isEmpty())
    }

    @Test fun verifyRecoveryCode_blankCodeFailsWithoutBackendCall() = runTest(testDispatcher) {
        val repository = repositoryWithSession(null)

        val result = repository.verifyRecoveryCode("a@b.com", "  ")

        assertTrue(result.isFailure)
        assertEquals(0, backend.verifyRecoveryCalls)
    }

    @Test fun verifyRecoveryCode_successReflectsSession() = runTest(testDispatcher) {
        val session = testSession()
        val repository = repositoryWithSession(session)

        val result = repository.verifyRecoveryCode("a@b.com", "123456")

        assertTrue(result.isSuccess)
        assertEquals(1, backend.verifyRecoveryCalls)
        assertEquals("a@b.com" to "123456", backend.lastVerify)
        assertEquals(session, repository.session.value)
        assertEquals("Código confirmado. Defina sua nova senha.", repository.infoMessage.value)
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
