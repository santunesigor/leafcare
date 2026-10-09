package br.com.leafcare.auth

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import br.com.leafcare.LeafCareApplication
import io.github.jan.supabase.gotrue.user.UserSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ViewModel for authentication flow.
 * Handles UI state for login, signup, password reset, and profile screens.
 */
class AuthViewModel internal constructor(
    application: Application,
    private val authRepository: AuthRepository,
    store: RecoveryPendingStore? = null
) : AndroidViewModel(application) {

    /** Production entry point used by `by viewModels()` in MainActivity. */
    constructor(application: Application) : this(application, AuthRepository(application))

    private val recoveryStore: RecoveryPendingStore =
        store ?: PrefsRecoveryPendingStore(application)

    // Current authentication state
    val session = authRepository.session
    val user = authRepository.user
    val isLoading = authRepository.isLoading
    val error = authRepository.error
    val infoMessage = authRepository.infoMessage
    val sessionChecked = authRepository.sessionChecked

    // Explicit auth bootstrap: the gate renders only Loading until the
    // state is resolved, so no App frame can leak during deeplink
    // processing. Initialized from the persisted recovery marker, so a
    // recovery session can never open the main app after process death.
    private val _bootstrap = MutableStateFlow(
        if (recoveryStore.isPending) {
            AuthBootstrapState.RECOVERY_PENDING
        } else {
            AuthBootstrapState.CHECKING
        }
    )
    internal val bootstrap: StateFlow<AuthBootstrapState> = _bootstrap.asStateFlow()

    // Password-reset submit gate: CAS-atomic single-flight plus a UI mirror.
    // The atomic gate (not the boolean flow) is the correctness mechanism.
    private val resetGate = AtomicBoolean(false)
    private val _resetSending = MutableStateFlow(false)
    val resetSending: StateFlow<Boolean> = _resetSending.asStateFlow()

    private val resendGate = AtomicBoolean(false)
    private val _resendSending = MutableStateFlow(false)
    val resendSending: StateFlow<Boolean> = _resendSending.asStateFlow()

    // Resend cooldown UI state: remaining seconds and whether an email was
    // ever requested (persists across restarts via the store).
    private val _cooldownSeconds = MutableStateFlow(0)
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds.asStateFlow()
    private val _recoveryRequestSent = MutableStateFlow(false)
    val recoveryRequestSent: StateFlow<Boolean> = _recoveryRequestSent.asStateFlow()

    // Cancel-recovery confirmation dialog state (recovery NewPassword only).
    private val _showCancelDialog = MutableStateFlow(false)
    val showCancelDialog: StateFlow<Boolean> = _showCancelDialog.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.sessionChecked.first { it }
            if (_bootstrap.value == AuthBootstrapState.CHECKING) {
                _bootstrap.value = AuthBootstrapState.READY
            } else if (_bootstrap.value == AuthBootstrapState.RECOVERY_PENDING &&
                authRepository.session.value == null
            ) {
                // Pending marker but no session to continue with: the recovery
                // cannot proceed. Go straight to ForgotPassword with guidance.
                clearRecoveryPending()
                setScreen(AuthScreen.ForgotPassword)
                authRepository.setInfo(
                    "A recuperação anterior foi interrompida. Solicite um novo link."
                )
            }
        }
    }

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
            // show the VerifyEmail screen (its static subtitle covers the
            // message, so previous messages are cleared to avoid duplication).
            if (result.exceptionOrNull() is SignupWithoutSessionException) {
                setScreen(AuthScreen.VerifyEmail)
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
        clearRecoveryPending()
        val app = getApplication<Application>() as? LeafCareApplication ?: return
        (result.getOrNull() as? UserSession)?.user?.id?.let { app.ensureAccountIsolation(it) }
        app.scheduleSync()
    }

    /** Sign out action: always back to the initial Auth screen. */
    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            clearForms()
            clearRecoveryPending()
            setScreen(AuthScreen.Login)
            _navigation.send(navigationAfterSignOut())
        }
    }

    /** Request password-recovery email with the secure app link. */
    fun requestPasswordReset(now: Long = System.currentTimeMillis()) {
        // Atomic single-flight: exactly one in-flight submission even under
        // concurrent taps from any thread. CAS (not check-then-act) closes
        // the race where two callers both observe "free" before either marks.
        if (!resetGate.compareAndSet(false, true)) return
        // A live local cooldown also refuses: matches the disabled button,
        // including entry points that bypass it (e.g. keyboard Done) and
        // taps landing before recomposition disables the button.
        // The gate is released here: nothing was started.
        if (cooldownRemainingSeconds(recoveryStore.lastRecoveryRequestAt, now = now) > 0) {
            resetGate.set(false)
            return
        }
        val state = _uiState.value
        _resetSending.value = true
        try {
            viewModelScope.launch {
                try {
                    val result = authRepository.requestPasswordReset(email = state.email.trim())
                    if (result.isSuccess) {
                        recoveryStore.lastRecoveryRequestAt = now
                    } else if (AuthRepository.classifyRateLimit(result.exceptionOrNull()) is RecoveryRateLimit.UserCooldown) {
                        // Align the local cooldown with a server-provided countdown.
                        // The cause chain keeps the raw backend message (with N).
                        findRateLimitSeconds(result.exceptionOrNull())?.let { seconds ->
                            recoveryStore.lastRecoveryRequestAt =
                                cooldownBaseForRemaining(seconds, now = now)
                        }
                    }
                } finally {
                    _resetSending.value = false
                    refreshCooldown()
                    resetGate.set(false)
                }
            }
        } catch (e: Exception) {
            // viewModelScope.launch itself cannot realistically throw, but a
            // stuck gate would wedge the button forever: always release.
            _resetSending.value = false
            refreshCooldown()
            resetGate.set(false)
        }
    }

    /** Recomputes the visible resend cooldown. Returns remaining seconds. */
    fun refreshCooldown(now: Long = System.currentTimeMillis()): Int {
        val remaining = cooldownRemainingSeconds(recoveryStore.lastRecoveryRequestAt, now = now)
        _cooldownSeconds.value = remaining
        _recoveryRequestSent.value = recoveryStore.lastRecoveryRequestAt > 0L
        return remaining
    }

    /** Shows the cancel-recovery confirmation (recovery NewPassword only). */
    fun requestCancelRecovery() {
        _showCancelDialog.value = true
    }

    /** Dismisses the cancel-recovery confirmation, staying in recovery. */
    fun dismissCancelDialog() {
        _showCancelDialog.value = false
    }

    /**
     * Notes an incoming auth deep link BEFORE the gate may render the app.
     * Must be called synchronously (Activity onCreate/onNewIntent, before
     * first composition): a recovery/confirm return immediately forces
     * PROCESSING_DEEPLINK (Loading), so no App frame can leak. Unknown links
     * and taps over a normal session change nothing.
     */
    fun noteDeeplink(url: String) {
        val link = parseAuthDeeplink(url) ?: return
        if (authRepository.hasPersistedSession() &&
            _bootstrap.value != AuthBootstrapState.RECOVERY_PENDING
        ) {
            return
        }
        if (_bootstrap.value == AuthBootstrapState.PROCESSING_DEEPLINK) return
        val wasPending = _bootstrap.value == AuthBootstrapState.RECOVERY_PENDING
        _bootstrap.value = AuthBootstrapState.PROCESSING_DEEPLINK
        processDeeplink(link, wasPending)
    }

    /**
     * Handles an auth deep-link return (`leafcare://auth/...`): signup
     * confirmation opens the app directly; password recovery opens the
     * new-password screen. See [noteDeeplink]. No browser/WebView.
     */
    fun handleAuthDeeplink(url: String) {
        noteDeeplink(url)
    }

    private fun processDeeplink(link: AuthDeeplink, wasPending: Boolean) {
        viewModelScope.launch {
            if (wasPending) {
                // Drop any stale recovery state before attempting the new
                // link, so an expired/reused link can never conserve it.
                authRepository.signOut()
                clearRecoveryPending()
                _bootstrap.value = AuthBootstrapState.PROCESSING_DEEPLINK
            }
            val result = authRepository.completeEmailLink(link)
            if (!result.isSuccess) {
                _bootstrap.value = AuthBootstrapState.READY
                // Used/expired links may carry a live cooldown: surface the
                // remainder so the user knows when a new link is allowed.
                val remaining = refreshCooldown()
                if (remaining > 0) {
                    val base = authRepository.error.value.orEmpty()
                    if (base.isNotBlank()) {
                        authRepository.setError(
                            "$base\nVocê poderá solicitar outro em $remaining segundos."
                        )
                    }
                }
                return@launch
            }
            isolateCurrentUser()
            clearForms()
            when (link) {
                is AuthDeeplink.ConfirmEmailCode,
                is AuthDeeplink.ConfirmEmailTokens -> {
                    _bootstrap.value = AuthBootstrapState.READY
                }
                is AuthDeeplink.RecoveryCode,
                is AuthDeeplink.RecoveryTokens -> {
                    setRecoveryPending(true)
                    setScreen(AuthScreen.NewPassword, clearMessages = false)
                }
            }
        }
    }

    /**
     * Cancels an in-progress password recovery: the recovery session is
     * signed out, the pending marker is cleared and the user returns
     * directly to ForgotPassword (never App, never Login first). The
     * send cooldown is preserved so a new link can be requested when allowed.
     */
    fun cancelRecovery() {
        viewModelScope.launch {
            authRepository.signOut()
            clearRecoveryPending()
            clearForms()
            _showCancelDialog.value = false
            setScreen(AuthScreen.ForgotPassword)
            authRepository.setInfo("Recuperação cancelada. Solicite um novo link.")
        }
    }

    private fun setRecoveryPending(pending: Boolean) {
        _bootstrap.value = if (pending) {
            AuthBootstrapState.RECOVERY_PENDING
        } else {
            AuthBootstrapState.READY
        }
        if (pending) {
            recoveryStore.isPending = true
        } else {
            recoveryStore.clear()
        }
    }

    private fun clearRecoveryPending() {
        setRecoveryPending(false)
    }

    /** Resend the signup confirmation email (in-app, no browser). */
    fun resendSignupEmail() {
        if (!resendGate.compareAndSet(false, true)) return
        val state = _uiState.value
        _resendSending.value = true
        viewModelScope.launch {
            try {
                authRepository.resendSignupEmail(email = state.email.trim())
            } finally {
                _resendSending.value = false
                resendGate.set(false)
            }
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
                clearRecoveryPending()
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

/** Explicit auth bootstrap state: the gate renders only Loading until resolved. */
internal enum class AuthBootstrapState {
    /** Initial storage restore still running. */
    CHECKING,
    /** Auth deep link identified, callback being processed. */
    PROCESSING_DEEPLINK,
    /** Valid recovery callback completed; new password still pending. */
    RECOVERY_PENDING,
    /** Bootstrap resolved; gate follows the session. */
    READY
}

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
                    (type == null || type == "recovery" || type == "invite") ->
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
 * Minimal persistent marker for an in-progress password recovery.
 * Created only after a valid recovery callback, cleared on password change,
 * cancel (+ sign-out) or fresh full authentication. Survives process death
 * so a persisted recovery session can never open the main app.
 * Holds no token, password or sensitive data: a single boolean.
 */
internal interface RecoveryPendingStore {
    var isPending: Boolean
    fun clear()

    /** Epoch millis of the last recovery email request, 0 when none. */
    var lastRecoveryRequestAt: Long
}

internal class PrefsRecoveryPendingStore(context: Context) : RecoveryPendingStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "leafcare_auth", Context.MODE_PRIVATE
    )

    override var isPending: Boolean
        get() = prefs.getBoolean(KEY_RECOVERY_PENDING, false)
        set(value) {
            prefs.edit().putBoolean(KEY_RECOVERY_PENDING, value).apply()
        }

    override fun clear() {
        prefs.edit().remove(KEY_RECOVERY_PENDING).apply()
    }

    override var lastRecoveryRequestAt: Long
        get() = prefs.getLong(KEY_LAST_RECOVERY_REQUEST_AT, 0L)
        set(value) {
            prefs.edit().putLong(KEY_LAST_RECOVERY_REQUEST_AT, value).apply()
        }

    companion object {
        private const val KEY_RECOVERY_PENDING = "recovery_pending"
        private const val KEY_LAST_RECOVERY_REQUEST_AT = "last_recovery_request_at"
    }
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
