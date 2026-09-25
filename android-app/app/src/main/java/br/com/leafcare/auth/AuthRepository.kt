package br.com.leafcare.auth

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import br.com.leafcare.LeafCareApplication
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.SignOutScope
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.SessionStatus.Authenticated
import io.github.jan.supabase.gotrue.SessionStatus.LoadingFromStorage
import io.github.jan.supabase.gotrue.SessionStatus.NetworkError
import io.github.jan.supabase.gotrue.SessionStatus.NotAuthenticated
import io.github.jan.supabase.gotrue.user.UserSession
import io.github.jan.supabase.gotrue.user.UserInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Repository for authentication operations.
 * Wraps Supabase Auth client and exposes authentication state as LiveData.
 */
class AuthRepository internal constructor(private val backend: AuthBackend) {

    constructor(application: Application) :
        this(SupabaseAuthBackend((application as LeafCareApplication).supabaseClientHolder.auth))

    // Current session state exposed as StateFlow for UI observation
    private val _session = MutableStateFlow<UserSession?>(null)
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    // Current user state exposed as StateFlow
    private val _user = MutableStateFlow<UserInfo?>(null)
    val user: StateFlow<UserInfo?> = _user.asStateFlow()

    // Loading state for auth operations
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state for auth operations
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Informational message (email confirmation, password reset). Not an error.
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    // Scope for background operations
    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private val mainScope = CoroutineScope(Dispatchers.Main + Job())

    // Explicit bootstrap state: false until the first storage-restore attempt
    // finishes. The UI gate shows only Loading before this resolves, so the
    // Login screen never flashes when a persisted session exists.
    private val _sessionChecked = MutableStateFlow(false)
    val sessionChecked: StateFlow<Boolean> = _sessionChecked.asStateFlow()

    // Flag to track if initial session restoration has been attempted
    private var _initialSessionRestored = false

    init {
        restoreSession()
    }

    /**
     * Restores the persisted session from local storage.
     * This is called on app startup and does not require network.
     * The Auth client handles session persistence internally.
     */
    fun restoreSession() {
        if (_initialSessionRestored) return
        _initialSessionRestored = true

        // Load session from storage (does not require network)
        ioScope.launch {
            try {
                val hasSession = backend.loadFromStorage()
                if (hasSession) {
                    val session = backend.loadSession()
                    if (session != null) {
                        _session.value = session
                        _user.value = session.user
                    }
                }
            } finally {
                _sessionChecked.value = true
            }
        }

        // Observe session status changes
        backend.sessionStatus.onEach { status ->
            when (status) {
                is Authenticated -> {
                    _session.value = status.session
                    _user.value = status.session.user
                }
                is LoadingFromStorage -> {
                    // Session is being loaded from storage
                }
                is NetworkError -> {
                    // Network error during session check
                }
                is NotAuthenticated -> {
                    _session.value = null
                    _user.value = null
                }
            }
        }.launchIn(mainScope)
    }

    /**
     * Sign up a new user with email, password, and display name.
     * The display name is sent as user metadata ("display_name") and used by the
     * database trigger to create the profile.
     *
     * Email confirmation is ON: a successful sign-up returns no session yet.
     * The confirmation email links back to [SIGNUP_REDIRECT_URL]; completing
     * it (see [completeEmailLink]) authenticates the user. No browser in-app,
     * no OTP, no localhost.
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<UserSession> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        return try {
            val validationError = validateSignUpInput(displayName, email, password)
            if (validationError != null) {
                _error.value = validationError
                return Result.failure(IllegalArgumentException(validationError))
            }

            backend.signUpWithEmail(email, password, displayName, SIGNUP_REDIRECT_URL)

            val session = backend.loadSession()
            if (session != null) {
                _session.value = session
                _user.value = session.user
                Result.success(session)
            } else {
                // Confirmation pending: the user taps the email link, which
                // returns through the deep link handled by completeEmailLink().
                _infoMessage.value = "Enviamos um link de confirmação para seu e-mail."
                Result.failure(SignupWithoutSessionException())
            }
        } catch (e: Exception) {
            val message = sanitizeError(AuthOperation.SIGN_UP, e)
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Sign in with email and password.
     */
    suspend fun signIn(email: String, password: String): Result<UserSession> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        return try {
            val validationError = validateSignInInput(email, password)
            if (validationError != null) {
                _error.value = validationError
                return Result.failure(IllegalArgumentException(validationError))
            }

            backend.signInWithEmail(email, password)

            val session = backend.loadSession()
            if (session != null) {
                _session.value = session
                _user.value = session.user
                Result.success(session)
            } else {
                val message = "Erro ao entrar: não foi possível obter a sessão. Tente novamente."
                _error.value = message
                Result.failure(AuthException(message))
            }
        } catch (e: Exception) {
            val message = sanitizeError(AuthOperation.SIGN_IN, e)
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Sign out the current user.
     */
    suspend fun signOut(): Result<Unit> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        return try {
            backend.signOut()
            _session.value = null
            _user.value = null
            Result.success(Unit)
        } catch (e: Exception) {
            val message = sanitizeError(AuthOperation.SIGN_OUT, e)
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /** Clears the current error state */
    fun clearError() {
        _error.value = null
    }

    /** Shows a validation error without a backend call (e.g. mismatched passwords). */
    fun setError(message: String) {
        _infoMessage.value = null
        _error.value = message
    }

    /**
     * Sends the password-recovery email with a secure Supabase link back to
     * the app ([PASSWORD_RECOVERY_REDIRECT]). No OTP, no browser in-app.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        return try {
            if (email.isBlank()) {
                _error.value = "E-mail inválido"
                return Result.failure(IllegalArgumentException("E-mail inválido"))
            }

            backend.requestPasswordRecovery(email.trim(), PASSWORD_RECOVERY_REDIRECT)

            _infoMessage.value = "E-mail de recuperação enviado. Verifique sua caixa de entrada."
            Result.success(Unit)
        } catch (e: Exception) {
            val message = sanitizeError(AuthOperation.PASSWORD_RESET, e)
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Completes an email deep link (signup confirmation or password
     * recovery): PKCE code or session tokens. On success a session exists
     * and is reflected locally. The caller decides the next screen.
     */
    internal suspend fun completeEmailLink(link: AuthDeeplink): Result<Unit> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        val operation = when (link) {
            is AuthDeeplink.ConfirmEmailCode,
            is AuthDeeplink.ConfirmEmailTokens -> AuthOperation.SIGNUP_CONFIRM
            is AuthDeeplink.RecoveryCode,
            is AuthDeeplink.RecoveryTokens -> AuthOperation.RECOVERY_VERIFY
        }

        return try {
            when (link) {
                is AuthDeeplink.ConfirmEmailCode -> backend.exchangeLinkCode(link.code)
                is AuthDeeplink.ConfirmEmailTokens ->
                    backend.importLinkTokens(link.accessToken, link.refreshToken)
                is AuthDeeplink.RecoveryCode -> backend.exchangeLinkCode(link.code)
                is AuthDeeplink.RecoveryTokens ->
                    backend.importLinkTokens(link.accessToken, link.refreshToken)
            }

            val session = backend.loadSession()
            if (session != null) {
                _session.value = session
                _user.value = session.user
                Result.success(Unit)
            } else {
                val message = sanitizeError(operation, IllegalStateException("no session"))
                _error.value = message
                Result.failure(AuthException(message))
            }
        } catch (e: Exception) {
            val message = sanitizeError(operation, e)
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Resends the signup confirmation email. Fully in-app (no browser).
     */
    suspend fun resendSignupEmail(email: String): Result<Unit> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        return try {
            if (email.isBlank()) {
                _error.value = "E-mail inválido"
                return Result.failure(IllegalArgumentException("E-mail inválido"))
            }

            backend.resendSignupEmail(email.trim())

            _infoMessage.value = "E-mail de confirmação reenviado."
            Result.success(Unit)
        } catch (e: Exception) {
            val message = sanitizeError(AuthOperation.SIGNUP_RESEND, e)
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Sets a new password on the current session (recovery or authenticated
     * password change).
     */
    suspend fun updatePassword(password: String): Result<Unit> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        return try {
            val validationError = validatePasswordInput(password)
            if (validationError != null) {
                _error.value = validationError
                return Result.failure(IllegalArgumentException(validationError))
            }

            backend.updatePassword(password)

            _infoMessage.value = "Senha alterada com sucesso."
            Result.success(Unit)
        } catch (e: Exception) {
            val message = sanitizeError(AuthOperation.UPDATE_PASSWORD, e)
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /** Shows an informational message, replacing any error. */
    fun setInfo(message: String) {
        _error.value = null
        _infoMessage.value = message
    }

    /** Clears the current informational message */
    fun clearInfo() {
        _infoMessage.value = null
    }

    /** Checks if there's a persisted session (without requiring network) */
    fun hasPersistedSession(): Boolean {
        return _session.value != null
    }

    /** Gets the current user ID if authenticated */
    fun getCurrentUserId(): String? {
        return _user.value?.id
    }

    companion object {
        /**
         * Pure signup validation. Returns the user-facing error message,
         * or null when the input is valid.
         */
        internal fun validateSignUpInput(displayName: String, email: String, password: String): String? {
            if (displayName.isBlank()) return "Nome não pode ser vazio"
            if (email.isBlank()) return "E-mail inválido"
            if (password.length < 6) return "A senha deve ter pelo menos 6 caracteres"
            return null
        }

        /**
         * Pure sign-in validation. Returns the user-facing error message,
         * or null when the input is valid.
         */
        internal fun validateSignInInput(email: String, password: String): String? {
            if (email.isBlank()) return "E-mail inválido"
            if (password.isBlank()) return "Senha não pode ser vazia"
            return null
        }

        /**
         * Pure new-password validation. Returns the user-facing error message,
         * or null when the input is valid.
         */
        internal fun validatePasswordInput(password: String): String? {
            if (password.length < 6) return "A senha deve ter pelo menos 6 caracteres"
            return null
        }

        /**
         * Converts backend exceptions into short user-facing messages.
         * The raw error (URL, headers, tokens, HTTP body) is NEVER included:
         * supabase-kt error messages embed the full request/response dump.
         */
        internal fun sanitizeError(operation: AuthOperation, e: Exception): String {
            val original = e.message.orEmpty()
            val raw = original.lowercase().replace('_', ' ')
            if (raw.contains("already registered")) return "Este e-mail já está cadastrado"
            if (raw.contains("weak password")) return "Senha muito fraca. Use pelo menos 6 caracteres."
            if (raw.contains("invalid email")) return "E-mail inválido"
            if (raw.contains("invalid credentials") || raw.contains("invalid login")) {
                return "E-mail ou senha incorretos"
            }
            if (raw.contains("email not confirmed")) return "Confirme seu e-mail antes de entrar"
            if (raw.contains("invalid or has expired")) return "Link inválido ou expirado."
            if (isRateLimited(e, original, raw)) {
                // When the server reports its own countdown, surface it so the
                // UI can align the local cooldown with the same number.
                val seconds = extractRateLimitSeconds(original)
                return if (seconds != null) {
                    "Você solicitou um link recentemente. Aguarde $seconds segundos para solicitar outro."
                } else {
                    "Você solicitou um e-mail recentemente. Aguarde um pouco antes de tentar novamente."
                }
            }
            if (isUnauthorizedAddress(raw)) {
                return "Envio indisponível para esse endereço nesta configuração de teste."
            }
            if (raw.contains("invalid api key")) {
                return "Não foi possível conectar ao serviço. Verifique a configuração do aplicativo."
            }
            if (isNetworkError(raw)) {
                return if (operation == AuthOperation.PASSWORD_RESET) {
                    "Não foi possível enviar o e-mail. Verifique sua conexão e tente novamente."
                } else {
                    "Sem conexão. Verifique sua internet."
                }
            }
            return when (operation) {
                AuthOperation.SIGN_UP -> "Não foi possível criar sua conta. Tente novamente."
                AuthOperation.SIGN_IN -> "Não foi possível entrar. Tente novamente."
                AuthOperation.SIGN_OUT -> "Não foi possível sair. Tente novamente."
                AuthOperation.PASSWORD_RESET -> "Não foi possível enviar a recuperação. Tente novamente."
                AuthOperation.RECOVERY_VERIFY -> "Não foi possível concluir a recuperação. Tente novamente."
                AuthOperation.UPDATE_PASSWORD -> "Não foi possível definir a nova senha. Tente novamente."
                AuthOperation.SIGNUP_CONFIRM -> "Não foi possível concluir o cadastro. Tente novamente."
                AuthOperation.SIGNUP_RESEND -> "Não foi possível reenviar o e-mail. Tente novamente."
            }
        }

        private fun isNetworkError(raw: String): Boolean {
            return raw.contains("network") ||
                raw.contains("unable to resolve host") ||
                raw.contains("failed to connect") ||
                raw.contains("connection refused") ||
                raw.contains("timeout") ||
                raw.contains("unknownhost")
        }

        /**
         * Rate-limit detection. supabase-kt 2.1.0 exposes no statusCode or
         * errorCode accessor on RestException (only error/description), so
         * the check combines those fields with the raw message. The
         * `over_email_send_rate_limit` code is matched before underscore
         * normalization; HTTP 429 wording is the fallback.
         */
        private fun isRateLimited(e: Exception, original: String, raw: String): Boolean {
            val lowerOriginal = original.lowercase()
            if (lowerOriginal.contains("over_email_send_rate_limit")) return true
            if (e is RestException) {
                val fields = "${e.error} ${e.description}".lowercase()
                if (fields.contains("over_email_send_rate_limit") ||
                    fields.contains("rate limit") ||
                    fields.contains("too many requests")
                ) {
                    return true
                }
            }
            return raw.contains("429") ||
                raw.contains("rate limit") ||
                raw.contains("too many requests")
        }

        /**
         * Best-effort mapping for addresses the default mailer refuses.
         * Narrow on purpose: only when the message mentions mail delivery.
         */
        private fun isUnauthorizedAddress(raw: String): Boolean {
            return raw.contains("not authorized") &&
                (raw.contains("mail") || raw.contains("email") ||
                    raw.contains("smtp") || raw.contains("send"))
        }
    }
}

/** Auth operations that need user-facing error messages. */
internal enum class AuthOperation {
    SIGN_UP,
    SIGN_IN,
    SIGN_OUT,
    PASSWORD_RESET,
    RECOVERY_VERIFY,
    UPDATE_PASSWORD,
    SIGNUP_CONFIRM,
    SIGNUP_RESEND
}

/** Deep-link target for the Supabase password-recovery email. */
internal const val PASSWORD_RECOVERY_REDIRECT = "leafcare://auth/reset-password"

/** Deep-link target for the Supabase signup-confirmation email. */
internal const val SIGNUP_REDIRECT_URL = "leafcare://auth/confirm-email"

/** Default resend cooldown, seconds. The server stays the authority. */
internal const val RECOVERY_COOLDOWN_SECONDS = 60

/**
 * Remaining cooldown seconds from a stored request timestamp.
 * Pure function of [lastRequestAt] and [now] (epoch millis).
 */
internal fun cooldownRemainingSeconds(
    lastRequestAt: Long,
    cooldownSeconds: Int = RECOVERY_COOLDOWN_SECONDS,
    now: Long = System.currentTimeMillis()
): Int {
    if (lastRequestAt <= 0L) return 0
    val elapsed = ((now - lastRequestAt) / 1000).toInt()
    return (cooldownSeconds - elapsed).coerceAtLeast(0)
}

/**
 * Base timestamp that yields [remainingSeconds] of cooldown at [now].
 * Used to align the local cooldown with a server-provided countdown.
 */
internal fun cooldownBaseForRemaining(
    remainingSeconds: Int,
    cooldownSeconds: Int = RECOVERY_COOLDOWN_SECONDS,
    now: Long = System.currentTimeMillis()
): Long = now - (cooldownSeconds - remainingSeconds).coerceAtLeast(0) * 1000L

/**
 * Extracts a server-provided countdown ("...after N seconds") if present.
 * Used only as complementary info; error-type detection never depends on it.
 */
internal fun extractRateLimitSeconds(raw: String): Int? {
    val match = Regex("""after\s+(\d+)\s+seconds?""", RegexOption.IGNORE_CASE).find(raw)
    return match?.groupValues?.getOrNull(1)?.toIntOrNull()
}

/**
 * Finds a server-provided rate-limit countdown walking the cause chain
 * (the sanitized UI message no longer carries the raw wording).
 */
internal fun findRateLimitSeconds(throwable: Throwable?): Int? {
    var current = throwable
    while (current != null) {
        extractRateLimitSeconds(current.message.orEmpty())?.let { return it }
        current = current.cause
    }
    return null
}

/** Custom exception for auth errors */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Returned (not thrown) by [AuthRepository.signUp] when no session exists
 * after sign-up. Defensive only (confirmation is OFF in this MVP).
 */
class SignupWithoutSessionException(
    message: String = "Enviamos um link de confirmação para seu e-mail."
) : Exception(message)

/**
 * Minimal test seam over the GoTrue calls [AuthRepository] needs.
 * Exists because supabase-kt's [Auth] is a sealed interface and cannot be
 * faked outside its own module; this narrow interface covers exactly the
 * calls the repository makes, nothing more.
 */
internal interface AuthBackend {
    val sessionStatus: StateFlow<SessionStatus>
    suspend fun loadFromStorage(): Boolean
    suspend fun loadSession(): UserSession?
    suspend fun signUpWithEmail(email: String, password: String, displayName: String, redirectUrl: String?)
    suspend fun signInWithEmail(email: String, password: String)
    suspend fun signOut()
    suspend fun requestPasswordRecovery(email: String, redirectUrl: String)
    suspend fun exchangeLinkCode(code: String)
    suspend fun importLinkTokens(accessToken: String, refreshToken: String)
    suspend fun updatePassword(newPassword: String)
    suspend fun resendSignupEmail(email: String)
}

/** Production [AuthBackend] backed by the supabase-kt 2.1.0 public API. */
internal class SupabaseAuthBackend(private val auth: Auth) : AuthBackend {

    override val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    override suspend fun loadFromStorage(): Boolean = auth.loadFromStorage(true)

    override suspend fun loadSession(): UserSession? = auth.sessionManager.loadSession()

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        redirectUrl: String?
    ) {
        auth.signUpWith(Email, redirectUrl) {
            this.email = email
            this.password = password
            data = buildJsonObject {
                put("display_name", displayName)
            }
        }
    }

    override suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        auth.signOut(SignOutScope.GLOBAL)
    }

    override suspend fun requestPasswordRecovery(email: String, redirectUrl: String) {
        auth.resetPasswordForEmail(email, redirectUrl)
    }

    override suspend fun exchangeLinkCode(code: String) {
        auth.exchangeCodeForSession(code)
    }

    override suspend fun importLinkTokens(accessToken: String, refreshToken: String) {
        auth.importAuthToken(accessToken, refreshToken, retrieveUser = true)
    }

    override suspend fun updatePassword(newPassword: String) {
        auth.modifyUser {
            password = newPassword
        }
    }

    override suspend fun resendSignupEmail(email: String) {
        auth.resendEmail(OtpType.Email.SIGNUP, email)
    }
}
