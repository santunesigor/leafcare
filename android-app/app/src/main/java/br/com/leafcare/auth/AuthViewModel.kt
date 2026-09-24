package br.com.leafcare.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import br.com.leafcare.LeafCareApplication
import io.github.jan.supabase.gotrue.user.UserSession
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ViewModel for authentication flow.
 * Handles UI state for login, signup, password reset, and profile screens.
 */
class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository
) : AndroidViewModel(application) {

    /** Production entry point used by `by viewModels()` in MainActivity. */
    constructor(application: Application) : this(application, AuthRepository(application))

    // Current authentication state
    val session = authRepository.session
    val user = authRepository.user
    val isLoading = authRepository.isLoading
    val error = authRepository.error
    val infoMessage = authRepository.infoMessage

    /**
     * Authenticated user's display name from user metadata, or null when
     * unavailable. Home greeting falls back to a plain greeting in that case.
     */
    fun getAuthDisplayName(): String? = displayNameOf(user.value)

    // UI state for different screens
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    // Navigation events
    private val _navigation = Channel<AuthNavigationEvent>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

    // --- Actions ---

    /** Updates the current screen */
    fun setScreen(screen: AuthScreen) {
        _uiState.value = _uiState.value.copy(currentScreen = screen)
    }

    /** Updates email field */
    fun setEmail(email: String) {
        _uiState.value = _uiState.value.copy(email = email)
    }

    /** Updates password field */
    fun setPassword(password: String) {
        _uiState.value = _uiState.value.copy(password = password)
    }

    /** Updates confirm password field */
    fun setConfirmPassword(confirmPassword: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = confirmPassword)
    }

    /** Updates display name field */
    fun setDisplayName(displayName: String) {
        _uiState.value = _uiState.value.copy(displayName = displayName)
    }

    /** Updates recovery code field */
    fun setRecoveryCode(recoveryCode: String) {
        _uiState.value = _uiState.value.copy(recoveryCode = recoveryCode)
    }

    /** Updates new password field */
    fun setNewPassword(newPassword: String) {
        _uiState.value = _uiState.value.copy(newPassword = newPassword)
    }

    /** Updates new password confirmation field */
    fun setConfirmNewPassword(confirmNewPassword: String) {
        _uiState.value = _uiState.value.copy(confirmNewPassword = confirmNewPassword)
    }

    /** Clears all form fields */
    fun clearForms() {
        _uiState.value = _uiState.value.copy(
            email = "",
            password = "",
            confirmPassword = "",
            displayName = "",
            recoveryCode = "",
            newPassword = "",
            confirmNewPassword = ""
        )
    }

    /** Sign up action */
    fun signUp() {
        val state = _uiState.value
        if (state.password != state.confirmPassword) {
            authRepository.setError("As senhas não coincidem")
            return
        }
        viewModelScope.launch {
            val result = authRepository.signUp(
                email = state.email.trim(),
                password = state.password,
                displayName = state.displayName.trim()
            )
            // Signup without session is defensive-only (email confirmation is
            // disabled on the hosted project): stay in Auth on the Login
            // screen so the user can sign in. No web/browser involved.
            if (result.exceptionOrNull() is SignupWithoutSessionException) {
                setScreen(AuthScreen.Login)
                return@launch
            }
            val destination = navigationForAuthResult(result.isSuccess)
            if (destination != null) {
                clearForms()
                _navigation.send(destination)
            }
            onAuthenticated(result)
        }
    }

    /** Sign in action */
    fun signIn() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = authRepository.signIn(
                email = state.email.trim(),
                password = state.password
            )
            val destination = navigationForAuthResult(result.isSuccess)
            if (destination != null) {
                clearForms()
                _navigation.send(destination)
            }
            onAuthenticated(result)
        }
    }

    /**
     * Post-login hook: isolate local data to this account (wipe on account
     * switch, keep on same-account re-login) and trigger a sync pass.
     * Cold starts schedule via Application.onCreate. Safe cast keeps JVM
     * tests (plain Application stub) green.
     */
    private suspend fun onAuthenticated(result: Result<*>) {
        if (!result.isSuccess) return
        val app = getApplication<Application>() as? LeafCareApplication ?: return
        (result.getOrNull() as? UserSession)?.user?.id?.let { app.ensureAccountIsolation(it) }
        app.scheduleSync()
    }

    /** Sign out action */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _navigation.send(navigationAfterSignOut())
        }
    }

    /** Request password-reset code (in-app OTP, no browser) */
    fun requestPasswordReset() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = authRepository.requestPasswordReset(email = state.email.trim())
            if (result.isSuccess) {
                setScreen(AuthScreen.RecoveryCode)
            }
        }
    }

    /** Verify the recovery code received by email (in-app, no browser) */
    fun verifyRecoveryCode() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = authRepository.verifyRecoveryCode(
                email = state.email.trim(),
                code = state.recoveryCode.trim()
            )
            if (result.isSuccess) {
                setScreen(AuthScreen.NewPassword)
            }
        }
    }

    /** Define a new password on the recovery session (in-app, no browser) */
    fun updatePassword() {
        val state = _uiState.value
        if (state.newPassword != state.confirmNewPassword) {
            authRepository.setError("As senhas não coincidem")
            return
        }
        viewModelScope.launch {
            val result = authRepository.updatePassword(password = state.newPassword)
            if (result.isSuccess) {
                clearForms()
                _navigation.send(AuthNavigationEvent.NavigateToApp)
            }
            onAuthenticated(result)
        }
    }

    /** Clear error */
    fun clearError() {
        authRepository.clearError()
    }

    /** Clear informational message */
    fun clearInfo() {
        authRepository.clearInfo()
    }

    /** Called when session is restored on app start */
    fun onSessionRestored() {
        viewModelScope.launch {
            _navigation.send(navigationForSessionRestore(authRepository.hasPersistedSession()))
        }
    }

    /** Gets current user's display name */
    fun getDisplayName(): String? {
        val metadata = user.value?.userMetadata
        return metadata?.get("display_name")?.jsonPrimitive?.content
            ?: user.value?.email
            ?: "Usuário"
    }

    /** Gets current user's email */
    fun getEmail(): String? {
        return user.value?.email
    }
}

/**
 * UI state for auth screens
 */
data class AuthUiState(
    val currentScreen: AuthScreen = AuthScreen.Login,
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val displayName: String = "",
    val recoveryCode: String = "",
    val newPassword: String = "",
    val confirmNewPassword: String = ""
)

/** Auth screen types */
enum class AuthScreen {
    Login,
    SignUp,
    ForgotPassword,
    RecoveryCode,
    NewPassword,
    Profile
}

/** Navigation events for auth flow */
sealed interface AuthNavigationEvent {    data class NavigateToAuth(val initialScreen: AuthScreen = AuthScreen.Login) : AuthNavigationEvent
    object NavigateToApp : AuthNavigationEvent
}

/**
 * Pure navigation decisions for the auth flow (unit-testable, no Android dependencies).
 * A failed sign-up/sign-in is intentionally *not* a navigation: the user stays
 * in the Auth flow while the error or info message is shown.
 */

/** After sign-up/sign-in: go to App only when authenticated, otherwise stay. */
internal fun navigationForAuthResult(authenticated: Boolean): AuthNavigationEvent? =
    if (authenticated) AuthNavigationEvent.NavigateToApp else null

/** After startup restore: existing session goes to App, otherwise to Auth. */
internal fun navigationForSessionRestore(hasSession: Boolean): AuthNavigationEvent =
    if (hasSession) AuthNavigationEvent.NavigateToApp else AuthNavigationEvent.NavigateToAuth()

/** After sign-out: always back to Auth. */
internal fun navigationAfterSignOut(): AuthNavigationEvent =
    AuthNavigationEvent.NavigateToAuth()

/** Extracts the producer display name from a GoTrue user, or null when absent. */
internal fun displayNameOf(user: io.github.jan.supabase.gotrue.user.UserInfo?): String? =
    user?.userMetadata?.get("display_name")?.jsonPrimitive?.content