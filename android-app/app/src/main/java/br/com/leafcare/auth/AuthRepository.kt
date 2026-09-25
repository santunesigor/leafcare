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
     * MVP policy: email confirmation is OFF (no corporate SMTP domain yet),
     * so a successful sign-up returns a session and the user is authenticated
     * directly. The null-session branch below is defensive only.
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
                // Defensive only: confirmation is OFF, so sign-up returns a
                // session. If it ever doesn't, stay unauthenticated.
                _infoMessage.value = "Cadastro concluído. Entre com seu e-mail e senha."
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
         * Converts backend exceptions into short user-facing messages.
         * The raw error (URL, headers, tokens, HTTP body) is NEVER included:
         * supabase-kt error messages embed the full request/response dump.
         */
        internal fun sanitizeError(operation: AuthOperation, e: Exception): String {
            val raw = e.message.orEmpty().lowercase().replace('_', ' ')
            if (raw.contains("already registered")) return "Este e-mail já está cadastrado"
            if (raw.contains("weak password")) return "Senha muito fraca. Use pelo menos 6 caracteres."
            if (raw.contains("invalid email")) return "E-mail inválido"
            if (raw.contains("invalid credentials") || raw.contains("invalid login")) {
                return "E-mail ou senha incorretos"
            }
            if (raw.contains("email not confirmed")) return "Confirme seu e-mail antes de entrar"
            if (raw.contains("invalid api key")) {
                return "Não foi possível conectar ao serviço. Verifique a configuração do aplicativo."
            }
            if (isNetworkError(raw)) return "Sem conexão. Verifique sua internet."
            return when (operation) {
                AuthOperation.SIGN_UP -> "Não foi possível criar sua conta. Tente novamente."
                AuthOperation.SIGN_IN -> "Não foi possível entrar. Tente novamente."
                AuthOperation.SIGN_OUT -> "Não foi possível sair. Tente novamente."
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
    }
}

/** Auth operations that need user-facing error messages. */
internal enum class AuthOperation {
    SIGN_UP,
    SIGN_IN,
    SIGN_OUT
}

/** Custom exception for auth errors */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Returned (not thrown) by [AuthRepository.signUp] when no session exists
 * after sign-up. Defensive only (confirmation is OFF in this MVP).
 */
class SignupWithoutSessionException(
    message: String = "Cadastro concluído. Entre com seu e-mail e senha."
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
}
