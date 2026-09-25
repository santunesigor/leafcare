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
import kotlinx.coroutines.flow.asStateFlow
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
    val sessionChecked = authRepository.sessionChecked

    // True while a password-recovery deep link is being completed: the gate
    // shows the new-password screen instead of the main app.
    private val _recoveryMode = MutableStateFlow(false)
    val recoveryMode: StateFlow<Boolean> = _recoveryMode.asStateFlow()

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

    /**
     * Updates the current screen. By default clears the previous screen's
     * messages so errors never leak into the next screen; success flows that
     * navigate right after setting a message pass `clearMessages = false`.
     */
    fun setScreen(screen: AuthScreen, clearMessages: Boolean = true) {
        if (clearMessages) {
            authRepository.clearError()
            authRepository.clearInfo()
        }
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
            // Signup without session means email confirmation is pending:
            // show the VerifyEmail screen (message preserved). The user taps
            // the email link, which returns through the deep link.
            if (result.exceptionOrNull() is SignupWithoutSessionException) {
                setScreen(AuthScreen.VerifyEmail, clearMessages = false)
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

    /** Sign out action: always back to the initial Auth screen. */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            clearForms()
            _recoveryMode.value = false
            setScreen(AuthScreen.Login)
            _navigation.send(navigationAfterSignOut())
        }
    }

    /** Request password-recovery email with the secure app link. */
    fun requestPasswordReset() {
        val state = _uiState.value
        viewModelScope.launch {
            authRepository.requestPasswordReset(email = state.email.trim())
        }
    }

    /**
     * Handles an auth deep-link return (`leafcare://auth/...`): signup
     * confirmation opens the app directly; password recovery opens the
     * new-password screen. Ignored when already authenticated or when the
     * link is unknown. No browser or WebView involved.
     */
    fun handleAuthDeeplink(url: String) {
        if (authRepository.hasPersistedSession()) return
        val link = parseAuthDeeplink(url) ?: return
        viewModelScope.launch {
            val result = authRepository.completeEmailLink(link)
            if (!result.isSuccess) return@launch
            isolateCurrentUser()
            clearForms()
            when (link) {
                is AuthDeeplink.ConfirmEmailCode,
                is AuthDeeplink.ConfirmEmailTokens -> Unit
                is AuthDeeplink.RecoveryCode,
                is AuthDeeplink.RecoveryTokens -> {
                    _recoveryMode.value = true
                    setScreen(AuthScreen.NewPassword, clearMessages = false)
                }
            }
        }
    }

    /** Resend the signup confirmation email (in-app, no browser). */
    fun resendSignupEmail() {
        val state = _uiState.value
        viewModelScope.launch {
            authRepository.resendSignupEmail(email = state.email.trim())
        }
    }

    /** Define a new password (recovery flow or authenticated change). */
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
                _recoveryMode.value = false
                _navigation.send(AuthNavigationEvent.NavigateToApp)
            }
            onAuthenticated(result)
        }
    }

    /** Isolates local data to the current session user and schedules sync. */
    private suspend fun isolateCurrentUser() {
        val app = getApplication<Application>() as? LeafCareApplication ?: return
        authRepository.getCurrentUserId()?.let { app.ensureAccountIsolation(it) }
        app.scheduleSync()
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
    val newPassword: String = "",
    val confirmNewPassword: String = ""
)

/** Auth screen types */
enum class AuthScreen {
    Login,
    SignUp,
    VerifyEmail,
    ForgotPassword,
    NewPassword,
    Profile
}

/**
 * An auth return from a Supabase email link, either PKCE (`?code=`) or
 * session tokens (`#access_token=&refresh_token=`), for signup confirmation
 * (`/confirm-email`) or password recovery (`/reset-password`).
 * Anything else is ignored. The two flows are never confused.
 */
internal sealed interface AuthDeeplink {
    data class ConfirmEmailCode(val code: String) : AuthDeeplink
    data class ConfirmEmailTokens(val accessToken: String, val refreshToken: String) : AuthDeeplink
    data class RecoveryCode(val code: String) : AuthDeeplink
    data class RecoveryTokens(val accessToken: String, val refreshToken: String) : AuthDeeplink
}

/**
 * Pure parser for LeafCare auth deep links (JVM-testable, no Android types).
 * Returns null for unknown links; those never authenticate and never crash.
 */
internal fun parseAuthDeeplink(url: String): AuthDeeplink? {
    return try {
        val uri = java.net.URI(url)
        if (uri.scheme != "leafcare") return null
        fun params(raw: String?): Map<String, String> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split("&").mapNotNull { part ->
                val idx = part.indexOf("=")
                if (idx <= 0) return@mapNotNull null
                val key = java.net.URLDecoder.decode(part.substring(0, idx), "UTF-8")
                val value = java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                key to value
            }.toMap()
        }
        val code = params(uri.rawQuery)["code"]?.takeIf { it.isNotBlank() }
        val fragment = params(uri.rawFragment)
        val accessToken = fragment["access_token"]
        val refreshToken = fragment["refresh_token"]
        val type = fragment["type"]
        return when (uri.path) {
            "/confirm-email" -> when {
                code != null -> AuthDeeplink.ConfirmEmailCode(code)
                !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank() &&
                    (type == null || type == "signup") ->
                    AuthDeeplink.ConfirmEmailTokens(accessToken, refreshToken)
                else -> null
            }
            "/reset-password" -> when {
                code != null -> AuthDeeplink.RecoveryCode(code)
                !accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank() &&
                    (type == null || type == "recovery") ->
                    AuthDeeplink.RecoveryTokens(accessToken, refreshToken)
                else -> null
            }
            else -> null
        }
    } catch (e: Exception) {
        null
    }
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