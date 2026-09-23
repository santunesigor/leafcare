package br.com.leafcare.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ViewModel for authentication flow.
 * Handles UI state for login, signup, password reset, and profile screens.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = AuthRepository(application)

    // Current authentication state
    val session = authRepository.session
    val user = authRepository.user
    val isLoading = authRepository.isLoading
    val error = authRepository.error
    val infoMessage = authRepository.infoMessage

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

    /** Clears all form fields */
    fun clearForms() {
        _uiState.value = _uiState.value.copy(
            email = "",
            password = "",
            confirmPassword = "",
            displayName = ""
        )
    }

    /** Sign up action */
    fun signUp() {
        val state = _uiState.value
        viewModelScope.launch {
            val result = authRepository.signUp(
                email = state.email.trim(),
                password = state.password,
                displayName = state.displayName.trim()
            )
            // Confirmation-required failures deliberately stay in the Auth flow:
            // the info message is shown, no navigation is sent.
            val destination = navigationForAuthResult(result.isSuccess)
            if (destination != null) {
                clearForms()
                _navigation.send(destination)
            }
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
        }
    }

    /** Sign out action */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _navigation.send(navigationAfterSignOut())
        }
    }

    /** Request password reset */
    fun requestPasswordReset() {
        val state = _uiState.value
        viewModelScope.launch {
            authRepository.requestPasswordReset(email = state.email.trim())
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
    val displayName: String = ""
)

/** Auth screen types */
enum class AuthScreen {
    Login,
    SignUp,
    ForgotPassword,
    Profile
}

/** Navigation events for auth flow */
sealed interface AuthNavigationEvent {
    data class NavigateToAuth(val initialScreen: AuthScreen = AuthScreen.Login) : AuthNavigationEvent
    object NavigateToApp : AuthNavigationEvent
}

/**
 * Pure navigation decisions for the auth flow (unit-testable, no Android dependencies).
 * Awaiting email confirmation is intentionally *not* a navigation: the user stays
 * in the Auth flow while [br.com.leafcare.auth.AuthRepository.infoMessage] is shown.
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