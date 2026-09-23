package br.com.leafcare.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.leafcare.auth.AuthNavigationEvent
import br.com.leafcare.auth.AuthScreen
import br.com.leafcare.auth.AuthViewModel
import br.com.leafcare.ui.LeafCareTheme

@Composable
fun AuthNavHost(viewModel: AuthViewModel) {
    val navController = rememberNavController()
    val navigation by viewModel.navigation.collectAsStateWithLifecycle(
        lifecycle = LocalLifecycleOwner.current.lifecycle,
        initialValue = null
    )

    // Handle navigation events
    navigation?.let { event ->
        when (event) {
            is AuthNavigationEvent.NavigateToAuth -> {
                navController.navigate("auth/${event.initialScreen.name}") {
                    popUpTo("auth") { inclusive = true }
                }
            }
            is AuthNavigationEvent.NavigateToApp -> {
                navController.navigate("app") {
                    popUpTo("auth") { inclusive = true }
                }
            }
        }
    }

    NavHost(navController, startDestination = "auth/Login") {
        composable("auth/Login") {
            LoginScreen(viewModel)
        }
        composable("auth/SignUp") {
            SignUpScreen(viewModel)
        }
        composable("auth/ForgotPassword") {
            ForgotPasswordScreen(viewModel)
        }
        composable("auth/Profile") {
            ProfileScreen(viewModel)
        }
        composable("app") {
            // This destination signals MainActivity to show main app
        }
    }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("LeafCare", style = MaterialTheme.typography.headlineLarge)
        Text("Entre na sua conta", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = { viewModel.setEmail(it) },
            label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            label = { Text("Senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        error?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.signIn() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                Text("Entrar")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Não tem conta? ")
            TextButton(onClick = { viewModel.setScreen(AuthScreen.SignUp) }) {
                Text("Criar conta")
            }
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { viewModel.setScreen(AuthScreen.ForgotPassword) }) {
            Text("Esqueci minha senha")
        }
    }
}

@Composable
fun SignUpScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("LeafCare", style = MaterialTheme.typography.headlineLarge)
        Text("Crie sua conta", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = uiState.displayName,
            onValueChange = { viewModel.setDisplayName(it) },
            label = { Text("Nome") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.email,
            onValueChange = { viewModel.setEmail(it) },
            label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            label = { Text("Senha (mín. 6 caracteres)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.confirmPassword,
            onValueChange = { viewModel.setConfirmPassword(it) },
            label = { Text("Confirmar senha") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        error?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
            )
        }

        infoMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                if (uiState.password == uiState.confirmPassword) {
                    viewModel.signUp()
                } else {
                    viewModel.clearError()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                Text("Criar conta")
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Já tem conta? ")
            TextButton(onClick = { viewModel.setScreen(AuthScreen.Login) }) {
                Text("Entrar")
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("LeafCare", style = MaterialTheme.typography.headlineLarge)
        Text("Recuperar senha", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        Text(
            "Digite seu e-mail para receber instruções de recuperação de senha.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        )

        OutlinedTextField(
            value = uiState.email,
            onValueChange = { viewModel.setEmail(it) },
            label = { Text("E-mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        error?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
            )
        }

        infoMessage?.let { msg ->
            Text(msg, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.requestPasswordReset() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                Text("Enviar e-mail de recuperação")
            }
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = { viewModel.setScreen(AuthScreen.Login) }) {
            Text("Voltar para login")
        }
    }
}

@Composable
fun ProfileScreen(viewModel: AuthViewModel) {
    val displayName = viewModel.getDisplayName()
    val email = viewModel.getEmail()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Perfil", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(displayName ?: "Usuário", style = MaterialTheme.typography.headlineSmall)
                        Text(email ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { viewModel.signOut() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Text("Sair da conta", color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
