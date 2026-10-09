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
@Composable internal fun UserDetails(actions: AdminActions, row: JSONObject, busy: Boolean, error: String?) {
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
                        "role" -> "Esta permissão dá acesso completo à administração de contas e fotos."
                        else -> "As instruções serão enviadas por e-mail para esta conta."
                    }, color = LeafColors.Muted)
                    if (action == "delete") OutlinedTextField(confirmation, { confirmation = it }, label = { Text("E-mail da conta") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                }
                Button(enabled = !busy && (action != "delete" || confirmation.equals(row.label("email"), true)), onClick = {
                    actions.action(action, JSONObject().put("id", row.getString("id")).put("enabled", !row.optBoolean("uploads_enabled"))
                        .put("role", if (row.optString("role") == "superadmin") "user" else "superadmin").put("confirmation", confirmation)); operation = null
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
                            AdminBadge(if (row.optString("role") == "superadmin") "Superadmin" else "Usuário")
                            AdminBadge(if (deleting) "Exclusão pendente" else if (row.optBoolean("uploads_enabled")) "Envios ativos" else "Envios pausados")
                        }
                        DetailLine("Cadastro", adminDate(row.label("created_at")))
                        DetailLine("Último acesso", adminDate(row.label("last_sign_in_at")))
                        DetailLine("E-mail", if (row.isNull("email_confirmed_at")) "Aguardando confirmação" else "Confirmado")
                        Text("${row.optInt("analyses")} análises  ·  ${row.optInt("photos")} fotos", color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
                    }
                }
                item {
                    AdminPanel {
                        AdminSectionTitle("Acesso e sincronização")
                        AccountAction(if (row.optBoolean("uploads_enabled")) "Pausar envios" else "Liberar novos envios", "Login e uso offline continuam disponíveis", Icons.Outlined.Sync, !busy && !deleting) { operation = "pause" }
                        HorizontalDivider(color = LeafColors.Border)
                        AccountAction(if (row.optString("role") == "superadmin") "Remover superadmin" else "Tornar superadmin", "Permissão para gerir contas e revisar fotos", Icons.Outlined.AdminPanelSettings, !busy && !deleting) { operation = "role" }
                    }
                }
                item {
                    AdminPanel {
                        AdminSectionTitle("Conta")
                        AccountAction("Reenviar convite / acesso", "Enviar instruções por e-mail", Icons.Outlined.MailOutline, !busy && !deleting) { operation = "reinvite" }
                        HorizontalDivider(color = LeafColors.Border)
                        AccountAction("Recuperar senha", "O usuário escolhe a nova senha", Icons.Outlined.LockReset, !busy && !deleting) { operation = "recovery" }
                        HorizontalDivider(color = LeafColors.Border)
                        AccountAction("Ver atividades", "Histórico de ações desta conta", Icons.Outlined.History, !busy) { actions.history(row.getString("id")) }
                    }
                }
                item {
                    AdminPanel {
                        AccountAction(if (deleting) "Retomar exclusão" else "Excluir conta e dados", "Remove os registros e as fotos remotas", Icons.Outlined.DeleteOutline, !busy, danger = true) { operation = "delete" }
                    }
                }
                item { SelectionContainer { Text("Identificador: ${row.label("id")}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) } }
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

@Composable internal fun ReviewDetails(actions: AdminActions, row: JSONObject, state: AdminState, classes: List<String>, catalog: DiseaseCatalog) {
    var reviewStatus by remember(row) { mutableStateOf(row.optString("review_status").takeIf { it in listOf("confirmed", "corrected", "uncertain", "rejected") } ?: "confirmed") }
    var selectedClass by remember(row) { mutableStateOf(row.optString("reviewed_class").takeIf { it in classes } ?: row.getString("class_id")) }
    var note by remember(row) { mutableStateOf(if (row.isNull("note")) "" else row.optString("note")) }
    var scale by remember(row) { mutableFloatStateOf(1f) }
    var panX by remember(row) { mutableFloatStateOf(0f) }
    var panY by remember(row) { mutableFloatStateOf(0f) }
    var metadata by remember(row) { mutableStateOf(false) }
    Dialog(onDismissRequest = { if (!state.busy) actions.closeDetails() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = LeafColors.Pale) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.closeDetails, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar à triagem") }
                    Column(Modifier.weight(1f)) {
                        Text("Revisar foto", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("Sua avaliação preserva a previsão original", color = LeafColors.Muted, fontSize = 10.sp)
                    }
                    Icon(Icons.AutoMirrored.Outlined.FactCheck, null, tint = LeafColors.Green)
                }
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE7EDE6)).clipToBounds(), contentAlignment = Alignment.Center) {
                            state.bitmap?.let { bitmap ->
                                Image(bitmap.asImageBitmap(), "Foto da análise", Modifier.fillMaxSize()
                                    .pointerInput(row) { detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 5f)
                                        val maxX = size.width * (scale - 1) / 2; val maxY = size.height * (scale - 1) / 2
                                        panX = (panX + pan.x).coerceIn(-maxX, maxX); panY = (panY + pan.y).coerceIn(-maxY, maxY)
                                    } }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = panX, translationY = panY))
                            }
                            Surface(onClick = { scale = 1f; panX = 0f; panY = 0f }, shape = CircleShape, color = Color.White.copy(alpha = .94f), modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                                Icon(Icons.Outlined.ZoomOutMap, "Restaurar zoom", tint = LeafColors.Green, modifier = Modifier.padding(10.dp).size(20.dp))
                            }
                        }
                    }
                    state.error?.let { item { AdminNotice(it, error = true); TextButton(onClick = actions.refresh, enabled = !state.busy) { Text("Recarregar lista") } } }
                    item {
                        AdminPanel {
                            AdminBadge("PREVISÃO ORIGINAL")
                            Text(catalog.get(row.getString("class_id")).name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                            Text("${"%.1f".format(row.optDouble("confidence") * 100)}% de confiança", color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
                            val top3 = row.optJSONArray("top3") ?: JSONArray()
                            repeat(top3.length()) { index ->
                                val p = top3.getJSONObject(index)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${index + 1}. ${catalog.get(p.getString("class_id")).name}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                                    Text("${"%.1f".format(p.optDouble("confidence") * 100)}%", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                                }
                            }
                            TextButton(onClick = { metadata = !metadata }, contentPadding = PaddingValues(0.dp)) { Text(if (metadata) "Ocultar dados da foto" else "Dados da foto") }
                            if (metadata) SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                DetailLine("Enviada por", row.label("email")); DetailLine("Data", adminDate(row.label("created_at"))); DetailLine("Modelo de origem", row.label("model_sha256"))
                            } }
                        }
                    }
                    item {
                        AdminPanel {
                            AdminSectionTitle("Sua avaliação", "Escolha o resultado da revisão")
                            val options = listOf("confirmed" to "Confirmar", "corrected" to "Corrigir classe", "uncertain" to "Duvidosa", "rejected" to "Rejeitar")
                            options.chunked(2).forEach { pair -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                pair.forEach { (id, label) ->
                                    val selected = reviewStatus == id
                                    val icon = when (id) { "confirmed" -> Icons.Outlined.CheckCircle; "corrected" -> Icons.Outlined.Edit; "uncertain" -> Icons.AutoMirrored.Outlined.HelpOutline; else -> Icons.Outlined.HideImage }
                                    Surface(onClick = { reviewStatus = id }, enabled = !state.busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                                        color = if (selected) Color(0xFFEAF2E9) else Color.White, border = BorderStroke(1.dp, if (selected) LeafColors.Green else LeafColors.Border)) {
                                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(icon, null, Modifier.size(22.dp), tint = if (selected) LeafColors.Green else LeafColors.Muted)
                                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (selected) LeafColors.Green else LeafColors.Text)
                                        }
                                    }
                                }
                            } }
                            if (reviewStatus == "corrected") Choice("Classe revisada", selectedClass, classes.associateWith { catalog.get(it).name }, !state.busy) { selectedClass = it }
                            OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text(if (reviewStatus in listOf("rejected", "uncertain")) "Motivo obrigatório" else "Observação (opcional)") }, enabled = !state.busy,
                                minLines = 2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                            if (!row.isNull("reviewed_at")) {
                                Text("Última revisão: ${adminDate(row.label("reviewed_at"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                                Text("Revisor: ${shortId(row.label("reviewer_id"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                            }
                            TextButton(onClick = { actions.history(row.getString("id")) }, enabled = !state.busy) { Icon(Icons.Outlined.History, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Histórico da revisão") }
                        }
                    }
                }
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = actions.closeDetails, enabled = !state.busy) { Text("Cancelar", color = LeafColors.Muted) }
                        Button(enabled = !state.busy && (reviewStatus !in listOf("rejected", "uncertain") || note.isNotBlank()), onClick = {
                            actions.action("review", JSONObject().put("id", row.getString("id")).put("version", row.optInt("review_version"))
                                .put("status", reviewStatus).put("class_id", selectedClass).put("note", note.trim()))
                        }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("Salvar e continuar", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}
