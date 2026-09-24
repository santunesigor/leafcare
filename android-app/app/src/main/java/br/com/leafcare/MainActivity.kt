package br.com.leafcare

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
import br.com.leafcare.auth.AuthViewModel
import br.com.leafcare.auth.displayNameOf
import br.com.leafcare.ui.auth.AuthNavHost
import br.com.leafcare.ui.auth.ProfileScreen
import br.com.leafcare.ui.*
import io.github.jan.supabase.gotrue.user.UserSession

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeafCareTheme { LeafCareApp() }
        }
    }
}

@Composable
fun LeafCareApp(authViewModel: AuthViewModel = viewModel()) {
    val session by authViewModel.session.collectAsStateWithLifecycle()
    val isLoading by authViewModel.isLoading.collectAsStateWithLifecycle()

    // On first launch, trigger session restoration
    androidx.compose.runtime.LaunchedEffect(Unit) {
        authViewModel.onSessionRestored()
    }

    // Auth gate: a single auth state decides which flow is visible.
    when (appGateDestination(session, isLoading)) {
        AppGate.Loading -> LoadingScreen()
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
                    ProfileScreen(authViewModel, onBack = { nav.popBackStack() })
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
    Main,
    Auth
}/**
 * Pure auth-gate decision (unit-testable): restoring without a session shows
 * loading, a session shows the app, otherwise the auth flow.
 */
internal fun appGateDestination(session: UserSession?, isLoading: Boolean): AppGate =
    if (isLoading && session == null) {
        AppGate.Loading
    } else if (session != null) {
        AppGate.Main
    } else {
        AppGate.Auth
    }