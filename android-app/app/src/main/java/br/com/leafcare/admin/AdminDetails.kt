package br.com.leafcare.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.LeafColors
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun UserDetails(actions: AdminActions, row: JSONObject, state: AdminState) {
    val busy = state.busy
    val error = state.error
    val manageable = canManageAccount(state, row)
    var chosenRole by remember(row) { mutableStateOf(row.optString("role")) }
    var confirmation by remember(row) { mutableStateOf("") }
    var operation by remember(row) { mutableStateOf<String?>(null) }
    val deleting = row.optBoolean("deletion_pending")
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { if (!busy) actions.closeDetails() }, sheetState = sheet, containerColor = LeafColors.Pale, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        val action = operation
        if (action != null) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                IconButton(onClick = { operation = null }, enabled = !busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar ao usuário") }
                AdminPanel {
                    Icon(if (action == "delete") Icons.Outlined.DeleteOutline else Icons.Outlined.AdminPanelSettings, null, tint = if (action == "delete") MaterialTheme.colorScheme.error else LeafColors.Green, modifier = Modifier.size(30.dp))
                    Text(when (action) { "delete" -> "Excluir esta conta?"; "pause" -> if (row.optBoolean("uploads_enabled")) "Pausar os envios?" else "Liberar novos envios?"; "role" -> "Alterar permissão?"; else -> "Enviar e-mail de acesso?" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(row.label("email"), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                    Text(when(action) {
                        "delete" -> "Exclui conta, análises, revisões e fotos remotas. Cópias em aparelhos offline permanecem. Digite o e-mail da conta para confirmar."
                        "pause" -> if (row.optBoolean("uploads_enabled")) "Login, uso local e leitura continuam. Novos envios ficarão pausados." else "Só resultados novos poderão ser enviados. O acumulado fica local."
                        "role" -> "Admin revisa fotos. Superadmin gerencia admins e usuários. Apenas o proprietário gerencia superadmins; sua conta permanece protegida."
                        else -> "As instruções serão enviadas por e-mail para esta conta."
                    }, color = LeafColors.Muted)
                    if (action == "role") Choice("Novo papel", chosenRole, roleChoices(state.role), !busy) { chosenRole = it }
                    if (action == "delete") OutlinedTextField(confirmation, { confirmation = it }, label = { Text("E-mail da conta") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                }
                Button(enabled = manageable && !busy && (action != "role" || chosenRole != row.optString("role")) && (action != "delete" || confirmation.equals(row.label("email"), true)), onClick = {
                    actions.action(action, JSONObject().put("id", row.getString("id")).put("enabled", !row.optBoolean("uploads_enabled"))
                        .put("role", chosenRole).put("confirmation", confirmation)); operation = null
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (action == "delete") MaterialTheme.colorScheme.error else LeafColors.Green)) { Text(if (action == "delete") "Excluir conta" else "Confirmar") }
                TextButton(onClick = { operation = null }, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.9f), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(54.dp).background(Color(0xFFEAF2E9), CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = LeafColors.Green, modifier = Modifier.size(28.dp)) }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(row.optString("display_name").takeIf { it.isNotBlank() && it != "null" } ?: "Conta do usuário", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(row.label("email"), color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = actions.closeDetails, enabled = !busy) { Icon(Icons.Outlined.Close, "Fechar usuário") }
                    }
                }
                error?.let { item { AdminNotice(it, error = true) } }
                item {
                    AdminPanel {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminBadge(roleLabel(row.optString("role")))
                            AdminBadge(if (deleting) "Exclusão pendente" else if (row.optBoolean("uploads_enabled")) "Envios ativos" else "Envios pausados")
                        }
                        DetailLine("Cadastro", adminDate(row.label("created_at")))
                        DetailLine("Último acesso", adminDate(row.label("last_sign_in_at")))
                        DetailLine("E-mail", if (row.isNull("email_confirmed_at")) "Aguardando confirmação" else "Confirmado")
                        Text("${row.optInt("analyses")} análises  ·  ${row.optInt("photos")} fotos", color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!manageable) item {
                    AdminNotice(if (state.hierarchyVersion < 1) "A gestão de contas exige a atualização de permissões no servidor." else "Conta protegida. Você pode consultar seus dados, mas não alterar uma conta do mesmo nível, superior ou a sua própria.")
                }
                item {
                    AdminPanel {
                        AdminSectionTitle("Acesso e sincronização")
                        AccountAction(if (row.optBoolean("uploads_enabled")) "Pausar envios" else "Liberar novos envios", "Login e uso offline continuam disponíveis", Icons.Outlined.Sync, !busy && !deleting && manageable) { operation = "pause" }
                        HorizontalDivider(color = LeafColors.Border)
                        AccountAction("Alterar papel", "Escolha o nível de acesso desta pessoa", Icons.Outlined.AdminPanelSettings, !busy && !deleting && manageable) { operation = "role" }
                    }
                }
                item {
                    AdminPanel {
                        AdminSectionTitle("Conta")
                        AccountAction("Reenviar convite / acesso", "Enviar instruções por e-mail", Icons.Outlined.MailOutline, !busy && !deleting && manageable) { operation = "reinvite" }
                        HorizontalDivider(color = LeafColors.Border)
                        AccountAction("Recuperar senha", "O usuário escolhe a nova senha", Icons.Outlined.LockReset, !busy && !deleting && manageable) { operation = "recovery" }
                        HorizontalDivider(color = LeafColors.Border)
                        AccountAction("Ver atividades", "Histórico de ações desta conta", Icons.Outlined.History, !busy) { actions.history(row.getString("id")) }
                    }
                }
                item {
                    AdminPanel {
                        AccountAction(if (deleting) "Retomar exclusão" else "Excluir conta e dados", "Remove os registros e as fotos remotas", Icons.Outlined.DeleteOutline, !busy && manageable, danger = true) { operation = "delete" }
                    }
                }
                item { SelectionContainer { Text("Conta: ${shortId(row.label("id"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) } }
            }
        }
    }
}

@Composable private fun AccountAction(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else LeafColors.Green
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(23.dp), tint = if (enabled) color else LeafColors.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (enabled) color else LeafColors.Muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = LeafColors.Muted)
    }
}
@Composable private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
