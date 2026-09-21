package br.com.leafcare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import br.com.leafcare.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LeafCareTheme { LeafCareApp() }
        }
    }
}

@Composable
fun LeafCareApp(vm: LeafCareViewModel = viewModel()) {
    val nav = rememberNavController()
    val state by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.results.collect { id ->
        nav.navigate("result/$id") { popUpTo("history"); launchSingleTop = true }
    } }
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavHost(nav, startDestination = "history") {
                composable("history") {
                    HistoryScreen(vm, { nav.navigate("camera") }, { nav.navigate("result/$it") })
                }
                composable("camera") {
                    CameraScreen(vm, onBack = { nav.popBackStack() })
                }
                composable("result/{id}") { entry ->
                    ResultScreen(requireNotNull(entry.arguments?.getString("id")), vm,
                        onBack = { nav.popBackStack("history", false) },
                        onCamera = { nav.navigate("camera") { popUpTo("history") } })
                }
            }
            if (state.busy) {
                AlertDialog(onDismissRequest = {}, confirmButton = {},
                    title = { Text("Analisando no celular") },
                    text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator(Modifier.size(30.dp))
                        Text("Aguarde. A foto permanece neste aparelho.")
                    } })
            }
            state.error?.let { message ->
                AlertDialog(onDismissRequest = vm::clearError,
                    title = { Text("Não foi possível concluir") }, text = { Text(message) },
                    confirmButton = { TextButton(onClick = vm::clearError) { Text("Entendi") } })
            }
        }
    }
}
