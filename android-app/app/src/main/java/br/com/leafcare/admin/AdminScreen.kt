package br.com.leafcare.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.leafcare.data.DiseaseCatalog
import androidx.compose.ui.platform.LocalContext
import org.json.JSONArray
import org.json.JSONObject

private val sections = linkedMapOf("dashboard" to "Visão geral", "users" to "Usuários", "photos" to "Triagem", "audit" to "Atividades")
private val statuses = linkedMapOf("pending" to "Pendentes", "confirmed" to "Confirmadas", "corrected" to "Corrigidas", "uncertain" to "Duvidosas", "rejected" to "Rejeitadas", "" to "Todas")
private fun JSONObject.label(key: String) = if (isNull(key)) "—" else optString(key, "—")

@Composable
internal fun AdminScreen(vm: AdminViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val catalog = remember { DiseaseCatalog(context) }
    val classes = remember { context.assets.open("classes.json").bufferedReader().use { JSONArray(it.readText()) }.let { a -> List(a.length()) { a.getString(it) } } }
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("pending") }
    var classFilter by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var until by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf(false) }
    var inviteEmail by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.load() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Text("Administração", style = MaterialTheme.typography.titleLarge)
        }
        if (!state.allowed) {
            Text(state.error ?: "Administração disponível somente online para superadmins.")
            Button(onClick = vm::checkAccess) { Text("Verificar acesso") }
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            sections.forEach { (id, title) ->
                FilterChip(selected = state.section == id, onClick = { vm.load(id, query = if (id == "photos") JSONObject().put("status", "pending") else JSONObject()) },
                    label = { Text(title) }, enabled = !state.busy, modifier = Modifier.padding(end = 8.dp))
            }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.message?.let { Text(it) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            if (state.section == "dashboard") {
                item {
                    val d = state.dashboard
                    Text("Usuários: ${d.optInt("users")} • Envios pausados: ${d.optInt("paused")}")
                    Text("Fotos: ${d.optInt("photos")} • Revisadas: ${d.optInt("reviewed")}")
                    Text("Pendentes: ${(d.optInt("photos") - d.optInt("reviewed")).coerceAtLeast(0)}")
                    Text("Fotos por classe", style = MaterialTheme.typography.titleMedium)
                    val counts = d.optJSONObject("classes") ?: JSONObject()
                    counts.keys().forEach { Text("${catalog.get(it).name}: ${counts.optInt(it)}") }
                    Text("Fotos por modelo (SHA256)", style = MaterialTheme.typography.titleMedium)
                    val models = d.optJSONObject("models") ?: JSONObject()
                    models.keys().forEach { Text("$it: ${models.optInt(it)}") }
                    Button(onClick = { vm.load("dashboard") }, enabled = !state.busy) { Text("Atualizar") }
                }
            } else {
                item {
                    if (state.section == "users") {
                        OutlinedTextField(search, { search = it }, label = { Text("Nome ou e-mail") }, modifier = Modifier.fillMaxWidth())
                        Row {
                            Button(onClick = { vm.load("users", query = JSONObject().put("search", search)) }, enabled = !state.busy) { Text("Buscar") }
                            TextButton(onClick = { invite = true }, enabled = !state.busy) { Text("Convidar usuário") }
                        }
                    }
                    if (state.section == "photos") {
                        Choice("Situação", status, statuses) { status = it }
                        Choice("Classe prevista", classFilter, linkedMapOf("" to "Todas") + classes.associateWith { catalog.get(it).name }) { classFilter = it }
                        OutlinedTextField(userId, { userId = it }, label = { Text("UUID do usuário (opcional)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(model, { model = it }, label = { Text("SHA256 do modelo (opcional)") }, modifier = Modifier.fillMaxWidth())
                        Row {
                            OutlinedTextField(from, { from = it }, label = { Text("De: AAAA-MM-DD") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(until, { until = it }, label = { Text("Até: AAAA-MM-DD") }, modifier = Modifier.weight(1f))
                        }
                        OutlinedTextField(minimum, { minimum = it }, label = { Text("Confiança mínima: 0 a 1") }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { vm.load("photos", query = JSONObject().put("status", status).put("class_id", classFilter)
                            .put("model", model.trim()).put("user_id", userId.trim()).put("from", from.trim()).put("until", until.trim()).put("min_confidence", minimum.trim().replace(',', '.'))) }, enabled = !state.busy) { Text("Aplicar filtros") }
                    }
                }
                items(state.rows, key = { it.get("id").toString() }) { row ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = !state.busy && state.section != "audit") { vm.select(row) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (state.section == "photos") PhotoThumbnail(vm, row.getString("id"))
                            Column(Modifier.padding(start = 8.dp)) {
                                when (state.section) {
                                    "users" -> {
                                        Text(row.label("display_name"), style = MaterialTheme.typography.titleMedium)
                                        Text(row.label("email"))
                                        Text("${row.label("role")} • ${if (row.optBoolean("uploads_enabled")) "Envios ativos" else "Envios pausados"}")
                                        if (row.optBoolean("deletion_pending")) Text("Exclusão pendente", color = MaterialTheme.colorScheme.error)
                                        Text("Análises: ${row.optInt("analyses")} • Fotos: ${row.optInt("photos")}")
                                    }
                                    "photos" -> {
                                        Text(catalog.get(row.getString("class_id")).name, style = MaterialTheme.typography.titleMedium)
                                        Text("Confiança: ${"%.2f".format(row.optDouble("confidence") * 100)}%")
                                        Text(statuses[row.optString("review_status")] ?: "Pendente")
                                        Text(row.label("email"))
                                    }
                                    "audit" -> {
                                        Text("${row.label("action")} • ${row.label("created_at")}")
                                        Text("Responsável: ${row.label("actor_id")}")
                                        Text("Registro: ${row.label("target_id")}")
                                        Text(row.label("details"))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    if (state.rows.isEmpty() && !state.busy) Text("Nenhum registro encontrado.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { vm.page(-1) }, enabled = state.page > 0 && !state.busy) { Text("Anterior") }
                        Text("Página ${state.page + 1}")
                        TextButton(onClick = { vm.page(1) }, enabled = state.rows.size == 20 && !state.busy) { Text("Próxima") }
                    }
                }
            }
        }
    }
    if (invite) AlertDialog(onDismissRequest = { if (!state.busy) invite = false }, title = { Text("Convidar usuário") },
        text = { OutlinedTextField(inviteEmail, { inviteEmail = it }, label = { Text("E-mail") }) },
        confirmButton = { TextButton(onClick = { vm.action("invite", JSONObject().put("email", inviteEmail.trim())); invite = false }, enabled = inviteEmail.isNotBlank() && !state.busy) { Text("Enviar convite") } },
        dismissButton = { TextButton(onClick = { invite = false }) { Text("Cancelar") } })
    state.selected?.let { row ->
        if (state.section == "users") UserDetails(vm, row, state.busy, state.error)
        if (state.section == "photos") ReviewDetails(vm, row, state, classes, catalog)
    }
}

@Composable private fun Choice(label: String, value: String, options: Map<String, String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: ${options[value] ?: value}") }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (key, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}

@Composable private fun PhotoThumbnail(vm: AdminViewModel, id: String) {
    val thumbnails by vm.thumbnails.collectAsStateWithLifecycle()
    LaunchedEffect(id) { vm.thumbnail(id) }
    val bitmap = thumbnails[id]
    if (bitmap == null) Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Text("Foto") }
    else Image(bitmap.asImageBitmap(), "Miniatura da análise", modifier = Modifier.size(64.dp))
}

@Composable private fun UserDetails(vm: AdminViewModel, row: JSONObject, busy: Boolean, error: String?) {
    var confirmation by remember(row) { mutableStateOf("") }
    var operation by remember(row) { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!busy) vm.closeDetails() }, title = { Text(row.label("email")) },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Text("Nome: ${row.label("display_name")}")
                Text("UUID: ${row.label("id")}")
                Text("Cadastro: ${row.label("created_at")}")
                Text("Confirmação: ${row.label("email_confirmed_at")}")
                Text("Último acesso: ${row.label("last_sign_in_at")}")
                TextButton(onClick = { operation = "pause" }, enabled = !busy && !row.optBoolean("deletion_pending")) { Text(if (row.optBoolean("uploads_enabled")) "Pausar envios" else "Liberar somente novos envios") }
                TextButton(onClick = { operation = "role" }, enabled = !busy && !row.optBoolean("deletion_pending")) { Text(if (row.optString("role") == "superadmin") "Remover superadmin" else "Tornar superadmin") }
                TextButton(onClick = { operation = "reinvite" }, enabled = !busy) { Text("Reenviar convite / acesso") }
                TextButton(onClick = { operation = "recovery" }, enabled = !busy) { Text("Enviar recuperação de senha") }
                TextButton(onClick = { vm.history(row.getString("id")) }, enabled = !busy) { Text("Ver atividades da conta") }
                TextButton(onClick = { operation = "delete" }, enabled = !busy) { Text(if (row.optBoolean("deletion_pending")) "Retomar exclusão" else "Excluir conta e dados remotos") }
            }
        }, confirmButton = { TextButton(onClick = vm::closeDetails, enabled = !busy) { Text("Fechar") } })
    operation?.let { action ->
        AlertDialog(onDismissRequest = { operation = null }, title = { Text("Confirmar ação") },
            text = { Column {
                Text(when(action) {
                    "delete" -> "Exclui conta, análises, revisões e fotos remotas. A operação não apaga cópias em aparelhos offline. Digite o e-mail da conta para confirmar."
                    "pause" -> if (row.optBoolean("uploads_enabled")) "Pausa envios; login, uso local e leitura continuam." else "Só resultados novos poderão ser enviados. O acumulado fica local."
                    "role" -> "Altera acesso completo à administração de contas e fotos."
                    else -> "Enviar e-mail de acesso para ${row.label("email")}?"
                })
                if (action == "delete") OutlinedTextField(confirmation, { confirmation = it }, label = { Text("E-mail da conta") })
            } },
            confirmButton = { TextButton(enabled = !busy && (action != "delete" || confirmation.equals(row.label("email"), true)), onClick = {
                vm.action(action, JSONObject().put("id", row.getString("id"))
                    .put("enabled", !row.optBoolean("uploads_enabled"))
                    .put("role", if (row.optString("role") == "superadmin") "user" else "superadmin")
                    .put("confirmation", confirmation)); operation = null
            }) { Text("Confirmar") } }, dismissButton = { TextButton(onClick = { operation = null }) { Text("Cancelar") } })
    }
}

@Composable private fun ReviewDetails(vm: AdminViewModel, row: JSONObject, state: AdminState, classes: List<String>, catalog: DiseaseCatalog) {
    var reviewStatus by remember(row) { mutableStateOf(row.optString("review_status").takeIf { it in statuses && it.isNotBlank() } ?: "confirmed") }
    var selectedClass by remember(row) { mutableStateOf(row.optString("reviewed_class").takeIf { it in classes } ?: row.getString("class_id")) }
    var note by remember(row) { mutableStateOf(if (row.isNull("note")) "" else row.optString("note")) }
    var scale by remember(row) { mutableFloatStateOf(1f) }
    AlertDialog(onDismissRequest = { if (!state.busy) vm.closeDetails() }, modifier = Modifier.fillMaxHeight(0.95f),
        title = { Text("Revisar foto") },
        text = { LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                state.bitmap?.let { bitmap ->
                    Box(Modifier.fillMaxWidth().height(240.dp).clipToBounds().background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Image(bitmap.asImageBitmap(), "Foto da análise", Modifier.fillMaxSize()
                            .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 5f) } }
                            .graphicsLayer(scaleX = scale, scaleY = scale))
                    }
                    TextButton(onClick = { scale = 1f }) { Text("Restaurar zoom") }
                }
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = vm::reload) { Text("Recarregar lista") } }
                Text("Previsão original: ${catalog.get(row.getString("class_id")).name}")
                Text("Confiança: ${"%.2f".format(row.optDouble("confidence") * 100)}% • Limiar: ${"%.2f".format(row.optDouble("threshold") * 100)}%")
                Text("Data: ${row.label("created_at")}")
                Text("Usuário: ${row.label("email")}")
                Text("Modelo: ${row.label("model_sha256")}")
                val top3 = row.optJSONArray("top3") ?: JSONArray()
                repeat(top3.length()) { index -> val p = top3.getJSONObject(index); Text("${index + 1}. ${catalog.get(p.getString("class_id")).name}: ${"%.2f".format(p.optDouble("confidence") * 100)}%") }
                HorizontalDivider()
                Text("Avaliação humana", style = MaterialTheme.typography.titleMedium)
                Text("Revisor: ${row.label("reviewer_id")} • ${row.label("reviewed_at")}")
                Choice("Resultado", reviewStatus, linkedMapOf("confirmed" to "Confirmar previsão", "corrected" to "Corrigir classe", "uncertain" to "Duvidosa", "rejected" to "Rejeitar imagem")) { reviewStatus = it }
                if (reviewStatus == "corrected") Choice("Classe revisada", selectedClass, classes.associateWith { catalog.get(it).name }) { selectedClass = it }
                OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text(if (reviewStatus in listOf("rejected", "uncertain")) "Motivo obrigatório" else "Observação") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { vm.history(row.getString("id")) }, enabled = !state.busy) { Text("Histórico desta revisão") }
            }
        } },
        confirmButton = { TextButton(enabled = !state.busy && (reviewStatus !in listOf("rejected", "uncertain") || note.isNotBlank()), onClick = {
            vm.action("review", JSONObject().put("id", row.getString("id")).put("version", row.optInt("review_version"))
                .put("status", reviewStatus).put("class_id", selectedClass).put("note", note.trim()))
        }) { Text("Salvar e continuar") } },
        dismissButton = { TextButton(onClick = vm::closeDetails, enabled = !state.busy) { Text("Cancelar") } })
}
