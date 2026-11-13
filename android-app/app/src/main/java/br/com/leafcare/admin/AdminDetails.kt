package br.com.leafcare.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import br.com.leafcare.ui.LeafButton
import br.com.leafcare.ui.LeafDialog
import br.com.leafcare.ui.LeafColors
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
    ModalBottomSheet(onDismissRequest = { if (!busy) actions.closeDetails() }, sheetState = sheet, containerColor = LeafColors.Pale, contentColor = LeafColors.Text,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), dragHandle = null) {
        Column(Modifier.fillMaxWidth().maxHeightFraction(.9f)) {
            Box(Modifier.padding(top = 8.dp).size(width = 32.dp, height = 4.dp).background(LeafColors.Border, RoundedCornerShape(2.dp)).align(Alignment.CenterHorizontally))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Person, null, tint = LeafColors.Green, modifier = Modifier.size(24.dp)) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.optString("display_name").takeIf { it.isNotBlank() && it != "null" } ?: "Conta do usuário", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(row.label("email"), color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = actions.closeDetails, enabled = !busy) { Icon(Icons.Outlined.Close, "Fechar usuário") }
            }
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let { item { AdminNotice(it, error = true) } }
                item {
                    AdminPanel {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminBadge(roleLabel(row.optString("role")))
                            UploadsBadge(row)
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            DetailLine("Cadastro", adminDate(row.label("created_at")))
                            DetailLine("Último acesso", adminDate(row.label("last_sign_in_at")))
                            DetailLine("E-mail", if (row.isNull("email_confirmed_at")) "Aguardando confirmação" else "Confirmado")
                            DetailLine("Envios", "${row.optInt("analyses")} análises · ${row.optInt("photos")} fotos")
                        }
                    }
                }
                if (!manageable) item {
                    AdminNotice(if (state.hierarchyVersion < 1) "A gestão de contas exige a atualização de permissões no servidor." else "Conta protegida. Você pode consultar seus dados, mas não alterar uma conta do mesmo nível, superior ou a sua própria.")
                }
                item {
                    AdminPanel {
                        AdminSectionTitle("Acesso e sincronização")
                        Column {
                            AccountAction(if (row.optBoolean("uploads_enabled")) "Pausar envios" else "Liberar novos envios", "Login e uso offline continuam disponíveis", Icons.Outlined.Sync, !busy && !deleting && manageable) { operation = "pause" }
                            HorizontalDivider(color = LeafColors.Border)
                            AccountAction("Alterar papel", "Escolha o nível de acesso desta pessoa", Icons.Outlined.AdminPanelSettings, !busy && !deleting && manageable) { operation = "role" }
                        }
                    }
                }
                item {
                    AdminPanel {
                        AdminSectionTitle("Conta")
                        Column {
                            AccountAction("Reenviar convite / acesso", "Enviar instruções por e-mail", Icons.Outlined.MailOutline, !busy && !deleting && manageable) { operation = "reinvite" }
                            HorizontalDivider(color = LeafColors.Border)
                            AccountAction("Recuperar senha", "O usuário escolhe a nova senha", Icons.Outlined.LockReset, !busy && !deleting && manageable) { operation = "recovery" }
                            HorizontalDivider(color = LeafColors.Border)
                            AccountAction("Ver atividades", "Histórico de ações desta conta", Icons.Outlined.History, !busy) { actions.history(row.getString("id")) }
                        }
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
    operation?.let { action ->
        val email = row.label("email")
        val pausing = row.optBoolean("uploads_enabled")
        LeafDialog(
            title = when (action) { "delete" -> "Excluir esta conta?"; "pause" -> if (pausing) "Pausar os envios?" else "Liberar novos envios?"; "role" -> "Alterar permissão?"; "recovery" -> "Enviar recuperação de senha?"; else -> "Enviar e-mail de acesso?" },
            onDismiss = { operation = null },
            dismissible = !busy,
            message = when (action) {
                "delete" -> "Exclui conta, análises, revisões e fotos remotas. Cópias em aparelhos offline permanecem."
                "pause" -> if (pausing) "Login, uso local e leitura continuam. Novos envios ficarão pausados." else "Só resultados novos poderão ser enviados. O acumulado fica local."
                "role" -> "Admin revisa fotos. Superadmin gerencia admins e usuários. Apenas o proprietário gerencia superadmins."
                "recovery" -> "A pessoa recebe um link por e-mail para escolher uma nova senha."
                else -> "As instruções serão enviadas por e-mail para esta conta."
            },
            actions = {
                LeafButton(when (action) { "delete" -> "Excluir"; "recovery", "reinvite" -> "Enviar"; else -> "Confirmar" }, {
                    actions.action(action, JSONObject().put("id", row.getString("id")).put("enabled", !pausing)
                        .put("role", chosenRole).put("confirmation", confirmation)); operation = null
                }, danger = action == "delete",
                    enabled = manageable && !busy && (action != "role" || chosenRole != row.optString("role")) && (action != "delete" || confirmation.equals(email, true)))
                LeafButton("Cancelar", { operation = null }, secondary = true, enabled = !busy)
            },
        ) {
            Row(Modifier.fillMaxWidth().background(LeafColors.Tint, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Person, null, Modifier.size(18.dp), tint = LeafColors.Green)
                Text(email, style = MaterialTheme.typography.bodyMedium, color = LeafColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (action == "role") Choice("Novo papel", chosenRole, roleChoices(state.role), !busy) { chosenRole = it }
            if (action == "delete") OutlinedTextField(confirmation, { confirmation = it }, label = { Text("Digite o e-mail para confirmar") }, singleLine = true,
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), colors = leafFieldColors())
        }
    }
}

@Composable private fun AccountAction(title: String, subtitle: String, icon: ImageVector, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else LeafColors.Green
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(23.dp), tint = if (enabled) color else LeafColors.Muted)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (enabled) color else LeafColors.Muted, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = LeafColors.Muted)
    }
}
@Composable private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, color = LeafColors.Text, modifier = Modifier.weight(1f))
    }
}
