package br.com.leafcare.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.leafcare.R
import br.com.leafcare.auth.AuthScreen
import br.com.leafcare.auth.AuthViewModel
import br.com.leafcare.ui.FigmaIcon
import br.com.leafcare.ui.LeafColors

/**
 * Auth flow host. MainActivity is the auth gate (session != null -> app),
 * so this host simply renders the screen selected in [AuthViewModel.uiState].
 * No nested NavHost: the previous route-based host ignored uiState.currentScreen,
 * which made the SignUp/ForgotPassword buttons appear dead.
 */
@Composable
fun AuthNavHost(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (uiState.currentScreen) {
        AuthScreen.Login -> LoginScreen(viewModel)
        AuthScreen.SignUp -> SignUpScreen(viewModel)
        AuthScreen.VerifyEmail -> VerifyEmailScreen(viewModel)
        AuthScreen.ForgotPassword -> ForgotPasswordScreen(viewModel)
        AuthScreen.NewPassword -> NewPasswordScreen(
            viewModel = viewModel,
            onBack = { viewModel.requestCancelRecovery() },
            confirmCancelRecovery = true
        )
        AuthScreen.Profile -> ProfileScreen(viewModel, onBack = { viewModel.setScreen(AuthScreen.Login) })
    }
}

/**
 * White V3 scaffold: an independent top-start back area (respecting the
 * status bar) above centered, scrollable content with ime padding.
 * The back control never lives inside the centered content column.
 */
@Composable
private fun AuthScaffold(
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    // Keep the Android back button/gesture consistent with the visible arrow.
    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
    ) {
        if (onBack != null) {
            AuthTopBar(onBack)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            content()
            Spacer(Modifier.weight(1f))
        }
    }
}

/** Shared top-start back control: 48dp touch target, V3 styling. */
@Composable
private fun AuthTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = LeafColors.Text)
        }
    }
}

@Composable
private fun AuthHeader(logoSize: Int, title: String, subtitle: String) {
    FigmaIcon(R.drawable.v3_logo, "LeafCare", logoSize)
    Spacer(Modifier.height(20.dp))
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        color = LeafColors.Text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = LeafColors.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onImeDone: () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = LeafColors.Muted)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = LeafColors.Pale,
                unfocusedContainerColor = LeafColors.Pale,
                disabledContainerColor = LeafColors.Pale,
                focusedBorderColor = LeafColors.Green,
                unfocusedBorderColor = LeafColors.Border,
                focusedTextColor = LeafColors.Text,
                unfocusedTextColor = LeafColors.Text,
                cursorColor = LeafColors.Green
            ),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onImeDone() }),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None
        )
    }
}

/** Primary green button in the LeafButton style, with loading/disabled state. */
@Composable
private fun AuthButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val active = enabled && !loading
    Button(
        onClick = { if (active) onClick() },
        enabled = active,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = LeafColors.Green,
            contentColor = Color.White,
            disabledContainerColor = LeafColors.Pale,
            disabledContentColor = LeafColors.Muted
        )
    ) {
        if (loading) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.5.dp)
                Text(label)
            }
        } else {
            Text(label)
        }
    }
}

@Composable
private fun AuthMessages(error: String?, infoMessage: String?) {
    error?.let { msg ->
        Text(
            msg,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
    infoMessage?.let { msg ->
        Text(
            msg,
            color = LeafColors.Green,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        )
    }
}

@Composable
private fun AuthLinkButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun AuthLinkedRow(prefix: String, link: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(prefix, color = LeafColors.Muted, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onClick) {
            Text(link, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()

    AuthScaffold {
        AuthHeader(
            logoSize = 64,
            title = "Bem-vindo ao LeafCare",
            subtitle = "Entre para acessar seu histórico e suas análises."
        )

        AuthField(
            value = uiState.email,
            onValueChange = { viewModel.setEmail(it) },
            label = "E-mail",
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(16.dp))
        AuthField(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            label = "Senha",
            keyboardType = KeyboardType.Password,
            password = true,
            imeAction = ImeAction.Done,
            onImeDone = { viewModel.signIn() }
        )

        AuthMessages(error, infoMessage)
        Spacer(Modifier.height(24.dp))

        AuthButton(label = "Entrar", loading = isLoading, onClick = { viewModel.signIn() })
        Spacer(Modifier.height(12.dp))

        AuthLinkButton("Esqueceu sua senha?") { viewModel.setScreen(AuthScreen.ForgotPassword) }
        AuthLinkedRow("Não tem uma conta?", "Criar conta") { viewModel.setScreen(AuthScreen.SignUp) }
    }
}

@Composable
fun SignUpScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()

    AuthScaffold(onBack = { viewModel.setScreen(AuthScreen.Login) }) {
        AuthHeader(
            logoSize = 48,
            title = "Crie sua conta",
            subtitle = "Seus registros ficam disponíveis nos seus dispositivos."
        )

        AuthField(
            value = uiState.displayName,
            onValueChange = { viewModel.setDisplayName(it) },
            label = "Nome"
        )
        Spacer(Modifier.height(16.dp))
        AuthField(
            value = uiState.email,
            onValueChange = { viewModel.setEmail(it) },
            label = "E-mail",
            keyboardType = KeyboardType.Email
        )
        Spacer(Modifier.height(16.dp))
        AuthField(
            value = uiState.password,
            onValueChange = { viewModel.setPassword(it) },
            label = "Senha (mín. 6 caracteres)",
            keyboardType = KeyboardType.Password,
            password = true
        )
        Spacer(Modifier.height(16.dp))
        AuthField(
            value = uiState.confirmPassword,
            onValueChange = { viewModel.setConfirmPassword(it) },
            label = "Confirmar senha",
            keyboardType = KeyboardType.Password,
            password = true,
            imeAction = ImeAction.Done,
            onImeDone = { viewModel.signUp() }
        )

        AuthMessages(error, infoMessage)
        Spacer(Modifier.height(24.dp))

        AuthButton(label = "Criar conta", loading = isLoading, onClick = { viewModel.signUp() })
        Spacer(Modifier.height(12.dp))

        AuthLinkedRow("Já tem uma conta?", "Entrar") { viewModel.setScreen(AuthScreen.Login) }
    }
}

@Composable
fun VerifyEmailScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resendSending by viewModel.resendSending.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()

    AuthScaffold(onBack = { viewModel.setScreen(AuthScreen.SignUp) }) {
        AuthHeader(
            logoSize = 56,
            title = "Verifique seu e-mail",
            subtitle = "Enviamos um link de confirmação para ${uiState.email}."
        )

        AuthMessages(error, infoMessage)
        Spacer(Modifier.height(24.dp))

        TextButton(
            onClick = { viewModel.resendSignupEmail() },
            enabled = !resendSending,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (resendSending) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Reenviar e-mail", color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(12.dp))

        AuthLinkButton("Voltar para entrar") { viewModel.setScreen(AuthScreen.Login) }
    }
}

@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val resetSending by viewModel.resetSending.collectAsStateWithLifecycle()
    val cooldownSeconds by viewModel.cooldownSeconds.collectAsStateWithLifecycle()
    val recoveryRequestSent by viewModel.recoveryRequestSent.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()
    val sending = isLoading || resetSending

    // Ticks the persisted resend cooldown while this screen is visible.
    LaunchedEffect(Unit) {
        viewModel.refreshCooldown()
        while (true) {
            delay(1000)
            viewModel.refreshCooldown()
        }
    }

    AuthScaffold(onBack = { viewModel.setScreen(AuthScreen.Login) }) {
        AuthHeader(
            logoSize = 56,
            title = "Recuperar senha",
            subtitle = "Informe seu e-mail e enviaremos um link de recuperação."
        )

        AuthField(
            value = uiState.email,
            onValueChange = { viewModel.setEmail(it) },
            label = "E-mail",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeDone = { viewModel.requestPasswordReset() }
        )

        AuthMessages(error, infoMessage)
        Spacer(Modifier.height(24.dp))

        AuthButton(
            label = when {
                sending -> "Enviando..."
                cooldownSeconds > 0 -> "Enviar novamente em ${cooldownSeconds}s"
                recoveryRequestSent -> "Enviar novamente"
                else -> "Enviar e-mail"
            },
            loading = sending,
            enabled = cooldownSeconds == 0,
            onClick = { viewModel.requestPasswordReset() }
        )
        Spacer(Modifier.height(12.dp))

        AuthLinkButton("Voltar para entrar") { viewModel.setScreen(AuthScreen.Login) }
    }
}

/**
 * New-password form shared by the recovery deep-link flow (Auth) and the
 * authenticated change flow (main app). Behavior comes from the ViewModel;
 * only back navigation differs per host.
 */
@Composable
fun NewPasswordScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    confirmCancelRecovery: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val infoMessage by viewModel.infoMessage.collectAsStateWithLifecycle()
    val showCancelDialog by viewModel.showCancelDialog.collectAsStateWithLifecycle()

    AuthScaffold(onBack = { if (confirmCancelRecovery) viewModel.requestCancelRecovery() else onBack() }) {
        AuthHeader(
            logoSize = 56,
            title = "Nova senha",
            subtitle = "Defina uma nova senha para a sua conta."
        )

        AuthField(
            value = uiState.newPassword,
            onValueChange = { viewModel.setNewPassword(it) },
            label = "Nova senha (mín. 6 caracteres)",
            keyboardType = KeyboardType.Password,
            password = true
        )
        Spacer(Modifier.height(16.dp))
        AuthField(
            value = uiState.confirmNewPassword,
            onValueChange = { viewModel.setConfirmNewPassword(it) },
            label = "Confirmar nova senha",
            keyboardType = KeyboardType.Password,
            password = true,
            imeAction = ImeAction.Done,
            onImeDone = { viewModel.updatePassword() }
        )

        AuthMessages(error, infoMessage)
        Spacer(Modifier.height(24.dp))

        AuthButton(label = "Salvar nova senha", loading = isLoading, onClick = { viewModel.updatePassword() })
    }

    if (confirmCancelRecovery && showCancelDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCancelDialog() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissCancelDialog() }) {
                    Text("Continuar recuperação", color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelRecovery() }) {
                    Text("Sair", color = MaterialTheme.colorScheme.error)
                }
            },
            title = { Text("Cancelar recuperação?") },
            text = { Text("Se você sair agora, será necessário solicitar um novo link de recuperação por e-mail.") }
        )
    }
}

@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onChangePassword: () -> Unit = {}
) {
    val displayName = viewModel.getDisplayName()
    val email = viewModel.getEmail()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuthTopBar(onBack)
        Spacer(Modifier.weight(1f))

        FigmaIcon(R.drawable.v3_logo, "LeafCare", 64)
        Spacer(Modifier.height(16.dp))
        Text(
            "CONTA LEAFCARE",
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.04.sp,
            color = LeafColors.Green
        )
        Spacer(Modifier.height(8.dp))
        Text(
            displayName ?: "Usuário",
            style = MaterialTheme.typography.headlineSmall,
            color = LeafColors.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            email ?: "",
            style = MaterialTheme.typography.bodyMedium,
            color = LeafColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onChangePassword,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, LeafColors.Border),
            colors = ButtonDefaults.buttonColors(
                containerColor = LeafColors.Pale,
                contentColor = LeafColors.Text
            )
        ) {
            Text("Alterar senha", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(12.dp))

        Button(
            onClick = { viewModel.signOut() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, LeafColors.Border),
            colors = ButtonDefaults.buttonColors(
                containerColor = LeafColors.Pale,
                contentColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = LeafColors.Pale,
                disabledContentColor = LeafColors.Muted
            )
        ) {
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
            } else {
                Text("Sair da conta", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
