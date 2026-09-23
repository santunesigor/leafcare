package br.com.leafcare.auth

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import br.com.leafcare.LeafCareApplication
import io.github.jan.supabase.gotrue.Auth
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
            val hasSession = backend.loadFromStorage()
            if (hasSession) {
                val session = backend.loadSession()
                if (session != null) {
                    _session.value = session
                    _user.value = session.user
                }
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
     * Two outcomes are supported:
     * - Supabase returns a session: user is authenticated.
     * - Account created but email confirmation required: no session exists.
     *   Returns [EmailConfirmationRequiredException] failure and sets [infoMessage];
     *   the user is NOT marked as authenticated.
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

            backend.signUpWithEmail(email, password, displayName)

            val session = backend.loadSession()
            if (session != null) {
                _session.value = session
                _user.value = session.user
                Result.success(session)
            } else {
                _infoMessage.value = "Confira seu e-mail para confirmar a conta."
                Result.failure(EmailConfirmationRequiredException())
            }
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("already registered", true) == true -> "Este e-mail já está cadastrado"
                e.message?.contains("weak password", true) == true -> "Senha muito fraca. Use pelo menos 6 caracteres."
                e.message?.contains("invalid email", true) == true -> "E-mail inválido"
                else -> "Erro ao criar conta: ${e.message ?: "tente novamente"}"
            }
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
            val message = when {
                e.message?.contains("invalid credentials", true) == true ||
                e.message?.contains("invalid login", true) == true -> "E-mail ou senha incorretos"
                e.message?.contains("email not confirmed", true) == true -> "Confirme seu e-mail antes de entrar"
                e.message?.contains("network", true) == true -> "Sem conexão. Verifique sua internet."
                else -> "Erro ao entrar: ${e.message ?: "tente novamente"}"
            }
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
            val message = "Erro ao sair: ${e.message ?: "tente novamente"}"
            _error.value = message
            Result.failure(AuthException(message, e))
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Request password reset email.
     * Uses the default redirect handling (no empty redirect URL).
     * Full in-app reset (deep link leafcare://auth/reset-password) still requires
     * the Supabase dashboard redirect allowlist plus a real-backend round trip,
     * so the token-exchange step is pending explicit verification.
     */
    suspend fun requestPasswordReset(email: String): Result<Unit> {
        _isLoading.value = true
        _error.value = null
        _infoMessage.value = null

        return try {
            if (email.isNullOrBlank()) {
                _error.value = "E-mail inválido"
                return Result.failure(IllegalArgumentException("E-mail inválido"))
            }

            backend.resetPasswordForEmail(email)

            _infoMessage.value = "Se o e-mail estiver cadastrado, você receberá as instruções de recuperação."
            Result.success(Unit)
        } catch (e: Exception) {
            val message = when {
                e.message?.contains("network", true) == true -> "Sem conexão. Verifique sua internet."
                else -> "Erro ao solicitar recuperação: ${e.message ?: "tente novamente"}"
            }
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
    }
}

/** Custom exception for auth errors */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Returned (not thrown) by [AuthRepository.signUp] when the account was created
 * but Supabase requires email confirmation before a session exists.
 */
class EmailConfirmationRequiredException(
    message: String = "Confira seu e-mail para confirmar a conta."
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
    suspend fun signUpWithEmail(email: String, password: String, displayName: String)
    suspend fun signInWithEmail(email: String, password: String)
    suspend fun signOut()
    suspend fun resetPasswordForEmail(email: String)
}

/** Production [AuthBackend] backed by the supabase-kt 2.1.0 public API. */
internal class SupabaseAuthBackend(private val auth: Auth) : AuthBackend {

    override val sessionStatus: StateFlow<SessionStatus> = auth.sessionStatus

    override suspend fun loadFromStorage(): Boolean = auth.loadFromStorage(true)

    override suspend fun loadSession(): UserSession? = auth.sessionManager.loadSession()

    override suspend fun signUpWithEmail(email: String, password: String, displayName: String) {
        auth.signUpWith(Email) {
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

    override suspend fun resetPasswordForEmail(email: String) {
        auth.resetPasswordForEmail(email)
    }
}
