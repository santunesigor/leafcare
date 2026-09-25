package br.com.leafcare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import br.com.leafcare.auth.AuthNavigationEvent
import br.com.leafcare.auth.AuthViewModel
import br.com.leafcare.auth.displayNameOf
import br.com.leafcare.ui.auth.AuthNavHost
import br.com.leafcare.ui.auth.NewPasswordScreen
import br.com.leafcare.ui.auth.ProfileScreen
import br.com.leafcare.ui.*
import io.github.jan.supabase.gotrue.user.UserSession

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleRecoveryIntent(intent)
        setContent {
            LeafCareTheme { LeafCareApp() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleRecoveryIntent(intent)
    }

    /** Forwards password-recovery deep links; browser/WebView never involved. */
    private fun handleRecoveryIntent(intent: Intent?) {
        val url = intent?.data?.toString() ?: return
        if (url.startsWith("leafcare://")) {
            authViewModel.handleRecoveryDeeplink(url)
        }
    }
}

@Composable
fun LeafCareApp(authViewModel: AuthViewModel = viewModel()) {
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val sessionChecked by authViewModel.sessionChecked.collectAsStateWithLifecycle()
    val recoveryMode by authViewModel.recoveryMode.collectAsStateWithLifecycle()

    // On first launch, trigger session restoration
    androidx.compose.runtime.LaunchedEffect(Unit) {
        authViewModel.onSessionRestored()
    }

    // Auth gate: Loading until the restore attempt resolves, so the Login
    // screen never flashes when a persisted session exists. A completed
    // recovery deep link takes precedence over the main app.
    when (appGateDestination(session, sessionChecked, recoveryMode)) {
        AppGate.Loading -> LoadingScreen()
        AppGate.Recovery -> AuthNavHost(authViewModel)
        AppGate.Main -> MainAppNavHost(authViewModel)
        AppGate.Auth -> AuthNavHost(authViewModel)
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("Carregando...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun MainAppNavHost(authViewModel: AuthViewModel) {
    val nav = rememberNavController()
    val vm: LeafCareViewModel = viewModel()
    val state by vm.ui.collectAsStateWithLifecycle()
    // Drives recomposition when the user changes; the name is read fresh below.
    val user by authViewModel.user.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.results.collect { id ->
        nav.navigate("result/$id") { popUpTo("history"); launchSingleTop = true } } }
    // After a password change inside "change-password", go back to profile.
    LaunchedEffect(nav, authViewModel) {
        authViewModel.navigation.collect { event ->
            if (event is AuthNavigationEvent.NavigateToApp &&
                nav.currentDestination?.route == "change-password"
            ) {
                nav.popBackStack()
            }
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavHost(
                nav,
                startDestination = "history",
                enterTransition = { leafEnterTransition },
                exitTransition = { leafExitTransition },
                popEnterTransition = { leafEnterTransition },
                popExitTransition = { leafExitTransition }
            ) {
                composable("history") {
                    HistoryScreen(vm, { nav.navigate("camera") }, { nav.navigate("result/$it") }, { nav.navigate("profile") }, displayNameOf(user))
                }
                composable("profile") {
                    // Same AuthViewModel instance: logout clears the session and the
                    // auth gate above switches back to Auth, disposing this NavHost,
                    // so Back can never return to an authenticated screen.
                    ProfileScreen(
                        authViewModel,
                        onBack = { nav.popBackStack() },
                        onChangePassword = { nav.navigate("change-password") }
                    )
                }
                composable("change-password") {
                    NewPasswordScreen(
                        authViewModel,
                        onBack = { nav.popBackStack() }
                    )
                }
                composable("camera") {
                    CameraScreen(vm, onBack = { nav.popBackStack() })
                }
                composable("result/{id}") { entry ->
                    ResultScreen(requireNotNull(entry.arguments?.getString("id")), vm,
                        onBack = { nav.popBackStack("history", false) },
                        onCamera = { nav.navigate("camera") { popUpTo("history") } }) }
            }
            if (state.busy) {
                AlertDialog(onDismissRequest = {}, confirmButton = {},
                    title = { Text("Analisando no celular") },
                    text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(30.dp))
                        Text("Aguarde. A foto permanece neste aparelho.") } })
            }
            state.error?.let { message ->
                AlertDialog(onDismissRequest = vm::clearError,
                    title = { Text("Não foi possível concluir") }, text = { Text(message) },
                    confirmButton = { TextButton(onClick = vm::clearError) { Text("Entendi") } }) }
        }
    }
}

/**
 * Navigation motion in the HelpSheet dialog language: a quick centered scale,
 * like a dialog opening/closing. No generic crossfade, no lateral slide.
 */
private val leafEnterTransition: EnterTransition =
    scaleIn(initialScale = 0.94f, animationSpec = tween(220, easing = FastOutSlowInEasing))
private val leafExitTransition: ExitTransition =
    scaleOut(targetScale = 0.96f, animationSpec = tween(180, easing = LinearOutSlowInEasing))

/** Visible root decided by the single auth state. */
internal enum class AppGate {
    Loading,
    Recovery,
    Main,
    Auth
}

/**
 * Pure auth-gate decision (unit-testable): Loading until the restore attempt
 * resolves, then recovery mode (deep link completed), then Main with a
 * session, Auth otherwise. Auth is never shown before the real state is known.
 */
internal fun appGateDestination(
    session: UserSession?,
    sessionChecked: Boolean,
    recoveryMode: Boolean = false
): AppGate =
    if (!sessionChecked) {
        AppGate.Loading
    } else if (recoveryMode) {
        AppGate.Recovery
    } else if (session != null) {
        AppGate.Main
    } else {
        AppGate.Auth
    }