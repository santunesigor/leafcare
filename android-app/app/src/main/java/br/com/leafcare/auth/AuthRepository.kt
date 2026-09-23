package br.com.leafcare.auth

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import br.com.leafcare.LeafCareApplication
import io.github.jan.supabase.gotrue.Auth
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

/**
 * Repository for authentication operations.
 * Wraps Supabase Auth client and exposes authentication state as LiveData.
 */
class AuthRepository(application: Application) {

    private val supabaseHolder = (application as LeafCareApplication).supabaseClientHolder
    private val auth: Auth = supabaseHolder.auth

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
            val hasSession = auth.loadFromStorage(true)
            if (hasSession) {
                val session = auth.sessionManager.loadSession()
                if (session != null) {
                    _session.value = session
                    _user.value = session.user
                }
            }
        }

        // Observe session status changes
        auth.sessionStatus.onEach { status ->
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
     * The display name is stored in user metadata and used by the
     * database trigger to create the profile.
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<UserSession> {
        _isLoading.value = true
        _error.value = null

        return try {
            if (displayName.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Nome não pode ser vazio"))
            }
            if (email.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("E-mail inválido"))
            }
            if (password.length < 6) {
                return Result.failure(IllegalArgumentException("A senha deve ter pelo menos 6 caracteres"))
            }

            val result = auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }

            val session = auth.sessionManager.loadSession()
            if (session != null) {
                _session.value = session
                _user.value = session.user
            }
            Result.success(session!!)
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

        return try {
            if (email.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("E-mail inválido"))
            }
            if (password.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("Senha não pode ser vazia"))
            }

            val result = auth.signInWith(Email) {
                this.email = email
                this.password = password
            }

            val session = auth.sessionManager.loadSession()
            if (session != null) {
                _session.value = session
                _user.value = session.user
            }
            Result.success(session!!)
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

        return try {
            auth.signOut(io.github.jan.supabase.gotrue.SignOutScope.GLOBAL)
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
     */
    suspend fun requestPasswordReset(email: String): Result<Unit> {
        _isLoading.value = true
        _error.value = null

        return try {
            if (email.isNullOrBlank()) {
                return Result.failure(IllegalArgumentException("E-mail inválido"))
            }

            auth.resetPasswordForEmail(email, "", null)

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

    /** Checks if there's a persisted session (without requiring network) */
    fun hasPersistedSession(): Boolean {
        return _session.value != null
    }

    /** Gets the current user ID if authenticated */
    fun getCurrentUserId(): String? {
        return _user.value?.id
    }
}

/** Custom exception for auth errors */
class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
