package br.com.leafcare.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.LeafColors
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun ReviewQueue(state: AdminState, actions: AdminActions, classes: List<String>, catalog: DiseaseCatalog) {
    var filtersOpen by remember { mutableStateOf(false) }
    val query = remember(state.photoQuery) { JSONObject(state.photoQuery) }
    var status by rememberSaveable(state.photoQuery) { mutableStateOf(state.photoFilterStatus) }
    var classFilter by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("class_id")) }
    var model by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("model")) }
    var user by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("user_id")) }
    var from by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("from")) }
    var until by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("until")) }
    var advancedFilters by rememberSaveable(state.photoQuery) { mutableStateOf(model.isNotBlank() || user.isNotBlank()) }
    val back = { if (!state.busy) actions.load("triage", JSONObject()) }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(LeafColors.Pale)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar ao painel de triagens") }
            Column(Modifier.weight(1f)) {
                Text("Avaliação", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("${reviewStatuses[state.photoFilterStatus]} · ${state.reviewedCount} salvas", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            }
            IconButton(onClick = { filtersOpen = true }, enabled = !state.busy) { Icon(Icons.Outlined.Tune, "Filtrar fotos", tint = LeafColors.Green) }
            IconButton(onClick = actions.refresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Atualizar fila", tint = LeafColors.Green) }
        }
        Box(Modifier.fillMaxWidth().height(3.dp)) { if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = LeafColors.Green) }
        if (state.rows.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                state.error?.let { AdminNotice(it, error = true); TextButton(onClick = actions.retryPhoto, enabled = !state.busy) { Text("Tentar novamente") } }
                AdminEmpty(if (state.busy) "Carregando fila…" else if (state.reviewedCount > 0) "Fila concluída!" else "Sem fotos nesta seleção",
                    if (state.busy) "Buscando fotos para revisar." else state.message ?: "Altere os filtros ou volte mais tarde.", Icons.Outlined.TaskAlt)
                if (!state.busy) {
                    Button(onClick = back, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) { Text("Voltar ao painel") }
                    TextButton(onClick = actions.refresh) { Text("Atualizar fila") }
                }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth().testTag("review-photo")) {
                val index = state.photoIndex.coerceIn(state.rows.indices)
                val row = state.rows[index]
                key(row.getString("id"), row.optInt("review_version")) {
                    ReviewPage(row, state, actions, classes, catalog, index)
                }
            }
        }
    }
    if (filtersOpen) AdminSheet("Filtrar fotos", { filtersOpen = false }, footer = {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { filtersOpen = false }) { Text("Cancelar") }
            Button(onClick = {
                filtersOpen = false
                actions.load("photos", JSONObject().put("status", status).put("class_id", classFilter)
                    .put("model", model.trim()).put("user_id", user.trim()).put("from", from.trim()).put("until", until.trim()))
            }, enabled = !state.busy, modifier = Modifier.weight(1f)) { Text("Aplicar filtros") }
        }
    }) {
        item { Choice("Situação", status, reviewStatuses) { status = it } }
        item { Choice("Classe prevista", classFilter, linkedMapOf("" to "Todas") + classes.associateWith { catalog.get(it).name }) { classFilter = it } }
        item { OutlinedTextField(from, { from = it }, label = { Text("De (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(until, { until = it }, label = { Text("Até (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        item { TextButton(onClick = { advancedFilters = !advancedFilters }) { Text(if (advancedFilters) "Menos filtros" else "Mais filtros"); Icon(if (advancedFilters) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null) } }
        if (advancedFilters) {
            item { OutlinedTextField(user, { user = it }, label = { Text("Identificador do usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(model, { model = it }, label = { Text("Identificador do modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@Composable private fun ReviewPage(row: JSONObject, state: AdminState, actions: AdminActions, classes: List<String>, catalog: DiseaseCatalog, index: Int) {
    var edit by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    var reviewStatus by rememberSaveable { mutableStateOf(row.optString("review_status").takeIf { it in listOf("confirmed", "corrected", "uncertain", "rejected") } ?: "confirmed") }
    var selectedClass by rememberSaveable { mutableStateOf(row.optString("reviewed_class").takeIf { it in classes } ?: row.getString("class_id")) }
    var note by rememberSaveable { mutableStateOf(if (row.isNull("note")) "" else row.optString("note")) }
    val active = state.photoId == row.getString("id")
    val ready = active && state.bitmap != null && !state.busy && !state.reviewConflict
    val cancelEdit = {
        if (!state.busy) {
            reviewStatus = row.optString("review_status").takeIf { it in listOf("confirmed", "corrected", "uncertain", "rejected") } ?: "confirmed"
            selectedClass = row.optString("reviewed_class").takeIf { it in classes } ?: row.getString("class_id")
            note = if (row.isNull("note")) "" else row.optString("note")
            edit = false
        }
    }
    val submit = { status: String ->
        actions.action("review", JSONObject().put("id", row.getString("id")).put("version", row.optInt("review_version"))
            .put("status", status).put("class_id", if (status == "confirmed") row.getString("class_id") else selectedClass).put("note", note.trim()))
    }
    Surface(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border)) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF17241B)), contentAlignment = Alignment.Center) {
                if (active && state.bitmap != null) {
                    Image(state.bitmap.asImageBitmap(), "Foto da análise", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    FilledTonalIconButton(onClick = { zoom = true }, enabled = !state.busy, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White, contentColor = LeafColors.Green)) { Icon(Icons.Outlined.ZoomIn, "Ampliar foto") }
                } else if (state.busy) CircularProgressIndicator(color = Color.White)
                else TextButton(onClick = actions.retryPhoto) { Text("Carregar foto", color = Color.White) }
                Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(10.dp)) {
                    Text("Foto ${index + 1} de ${state.rows.size}${if (state.queueHasMore) "+" else ""}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontSize = 11.sp)
                }
            }
            // Prediction can scroll on short screens while all four decisions stay visible.
            Column(Modifier.fillMaxWidth().heightIn(max = 138.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PREVISÃO ORIGINAL", color = LeafColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    AdminBadge(reviewLabel(row.optString("review_status")))
                }
                Text(catalog.get(row.getString("class_id")).name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${"%.1f".format(row.optDouble("confidence") * 100)}% de confiança", Modifier.weight(1f), color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { reviewStatus = "confirmed"; edit = true }, enabled = ready) { Icon(Icons.Outlined.EditNote, "Adicionar observação", tint = LeafColors.Green) }
                    TextButton(onClick = { details = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Icon(Icons.Outlined.Info, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Detalhes", style = MaterialTheme.typography.labelMedium) }
                }
                if (index == state.photoIndex) {
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = actions.retryPhoto, enabled = !state.busy) { Text(if (state.reviewConflict) "Recarregar revisão" else "Tentar novamente") } }
                    state.message?.let { Text(it, color = LeafColors.Green, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewActionButton("Confirmar", Icons.Outlined.CheckCircle, LeafColors.Green, ready, Modifier.weight(1f)) { submit("confirmed") }
                    ReviewActionButton("Corrigir", Icons.Outlined.Edit, LeafColors.Green, ready, Modifier.weight(1f)) { reviewStatus = "corrected"; edit = true }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewActionButton("Duvidosa", Icons.AutoMirrored.Outlined.HelpOutline, Color(0xFF99620A), ready, Modifier.weight(1f)) { reviewStatus = "uncertain"; edit = true }
                    ReviewActionButton("Rejeitar", Icons.Outlined.HideImage, Color(0xFFAF3636), ready, Modifier.weight(1f)) { reviewStatus = "rejected"; edit = true }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { actions.movePhoto(index - 1) }, enabled = index > 0 && !state.busy, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Icon(Icons.Outlined.ChevronLeft, null, Modifier.size(18.dp)); Text("Anterior", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = actions.nextPhoto, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text("Pular", style = MaterialTheme.typography.bodySmall); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
            }
        }
    }
    if (edit) AdminSheet("Sua avaliação", cancelEdit, dismissEnabled = !state.busy, footer = {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = cancelEdit, enabled = !state.busy) { Text("Cancelar") }
            Button(onClick = { submit(reviewStatus) }, enabled = ready, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("Salvar e próxima") }
        }
    }) {
        item { Text(when (reviewStatus) { "corrected" -> "Corrigir a classe prevista"; "uncertain" -> "Marcar como duvidosa"; "rejected" -> "Rejeitar esta foto"; else -> "Confirmar a previsão" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("A previsão original será preservada.", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall) }
        state.error?.let { item { AdminNotice(it, error = true); TextButton(onClick = actions.retryPhoto, enabled = !state.busy) { Text(if (state.reviewConflict) "Recarregar revisão" else "Tentar novamente") } } }
        if (reviewStatus == "corrected") item { Choice("Classe revisada", selectedClass, classes.associateWith { catalog.get(it).name }, !state.busy) { selectedClass = it } }
        item { OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text("Observação (opcional)") }, enabled = !state.busy, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) }
    }
    if (details) AdminSheet("Dados da foto", { details = false }) {
        item { AdminBadge("PREVISÃO ORIGINAL"); Text(catalog.get(row.getString("class_id")).name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        item {
            val top3 = row.optJSONArray("top3") ?: JSONArray()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(top3.length()) { i -> val p = top3.getJSONObject(i)
                    Text("${i + 1}. ${catalog.get(p.getString("class_id")).name} · ${"%.1f".format(p.optDouble("confidence") * 100)}%", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        item { SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Enviada por: ${row.label("email")}")
            Text("Data: ${adminDate(row.label("created_at"))}")
            Text("Modelo de origem: ${row.label("model_sha256")}")
            if (!row.isNull("reviewed_at")) {
                Text("Última revisão: ${adminDate(row.label("reviewed_at"))}")
                Text("Revisor: ${row.label("reviewer_id")}")
                Text("Classe revisada: ${catalog.get(row.optString("reviewed_class").takeIf { it in classes } ?: row.getString("class_id")).name}")
                Text("Observação: ${row.label("note")}")
            }
        } } }
        item { TextButton(onClick = { actions.history(row.getString("id")) }, enabled = !state.busy) { Text("Histórico da revisão") } }
    }
    if (zoom && active && state.bitmap != null) AdminSheet("Ampliar foto", { zoom = false }) {
        item {
            var scale by remember { mutableFloatStateOf(1f) }
            var panX by remember { mutableFloatStateOf(0f) }
            var panY by remember { mutableFloatStateOf(0f) }
            Box(Modifier.fillMaxWidth().height(360.dp).clipToBounds().background(Color(0xFF17241B))) {
                Image(state.bitmap.asImageBitmap(), "Foto ampliada", Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoomChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 5f)
                        val maxX = size.width * (scale - 1) / 2; val maxY = size.height * (scale - 1) / 2
                        panX = (panX + pan.x).coerceIn(-maxX, maxX); panY = (panY + pan.y).coerceIn(-maxY, maxY)
                    }
                }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = panX, translationY = panY), contentScale = ContentScale.Fit)
            }
            TextButton(onClick = { scale = 1f; panX = 0f; panY = 0f }) { Text("Restaurar zoom") }
        }
    }
}

@Composable private fun ReviewActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = .25f)), colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = .05f), contentColor = color),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Bounded sheet: only fields scroll; system bars and IME are consumed once. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AdminSheet(
    title: String, onClose: () -> Unit, dismissEnabled: Boolean = true, heightFraction: Float = .94f,
    footer: @Composable () -> Unit = { TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Fechar") } },
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    ModalBottomSheet(onDismissRequest = { if (dismissEnabled) onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { dismissEnabled || it != SheetValue.Hidden }),
        containerColor = LeafColors.Pale, contentWindowInsets = { WindowInsets(0, 0, 0, 0) }, dragHandle = null) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(heightFraction).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, enabled = dismissEnabled) { Icon(Icons.Outlined.Close, "Fechar painel") }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
            Surface(color = Color.White, shadowElevation = 6.dp) { footer() }
        }
    }
}
