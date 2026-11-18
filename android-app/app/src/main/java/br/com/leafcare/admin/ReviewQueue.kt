package br.com.leafcare.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.LeafButton
import br.com.leafcare.ui.LeafColors
import br.com.leafcare.ui.percent
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun ReviewQueue(state: AdminState, actions: AdminActions, classes: List<String>, catalog: DiseaseCatalog) {
    var filtersOpen by remember { mutableStateOf(false) }
    val back = { if (!state.busy) actions.load("triage", JSONObject()) }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(LeafColors.Pale)) {
        AdminTopBar("Avaliação", listOfNotNull(reviewStatuses[state.photoFilterStatus], "cópias de treino".takeIf { JSONObject(state.photoQuery).optString("training") == "true" }, "${state.reviewedCount} salvas").joinToString(" · "), back, "Voltar ao painel de triagens", state.busy, backEnabled = !state.busy) {
            IconButton(onClick = { filtersOpen = true }, enabled = !state.busy) { Icon(Icons.Outlined.Tune, "Filtrar fotos", tint = LeafColors.Green) }
            IconButton(onClick = actions.refresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Atualizar fila", tint = LeafColors.Green) }
        }
        if (state.rows.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                state.error?.let { AdminNotice(it, error = true); TextButton(onClick = actions.retryPhoto, enabled = !state.busy) { Text("Tentar novamente") } }
                AdminEmpty(if (state.busy) "Carregando fila…" else if (state.reviewedCount > 0) "Fila concluída!" else "Sem fotos nesta seleção",
                    if (state.busy) "Buscando fotos para revisar." else state.message ?: "Altere os filtros ou volte mais tarde.", Icons.Outlined.TaskAlt)
                if (!state.busy) {
                    LeafButton("Voltar ao painel", back, modifier = Modifier.padding(horizontal = 20.dp))
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
    if (filtersOpen) PhotoFilterSheet(state, classes, catalog, { filtersOpen = false }) { actions.load("photos", it) }
}

@Composable private fun ReviewPage(row: JSONObject, state: AdminState, actions: AdminActions, classes: List<String>, catalog: DiseaseCatalog, index: Int) {
    var edit by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var reviewStatus by remember { mutableStateOf("confirmed") }
    val active = state.photoId == row.getString("id")
    val ready = active && state.bitmap != null && !state.busy && !state.reviewConflict
    Surface(Modifier.fillMaxSize().padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        shape = RoundedCornerShape(22.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border)) {
        Column {
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF17241B)), contentAlignment = Alignment.Center) {
                if (active && state.bitmap != null) {
                    ZoomableAdminPhoto(state.bitmap, "Foto da análise", Modifier.fillMaxSize())
                } else if (state.busy) CircularProgressIndicator(color = Color.White)
                else TextButton(onClick = actions.retryPhoto) { Text("Carregar foto", color = Color.White) }
                Surface(Modifier.align(Alignment.BottomStart).padding(12.dp), color = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(10.dp)) {
                    Text("Foto ${index + 1} de ${state.rows.size}${if (state.queueHasMore) "+" else ""}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), color = Color.White, fontSize = 11.sp)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PREVISÃO ORIGINAL", color = LeafColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TrainingBadge(row); Spacer(Modifier.width(6.dp))
                    AdminBadge(reviewLabel(row.optString("review_status")), reviewColor(row.optString("review_status")))
                }
                Text(catalog.get(row.getString("class_id")).name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${percent(row.optDouble("confidence").toFloat())} de confiança", Modifier.weight(1f), color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
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
                    ReviewActionButton("Confirmar", Icons.Outlined.CheckCircle, LeafColors.Green, ready, Modifier.weight(1f)) {
                        actions.action("review", JSONObject().put("id", row.getString("id")).put("version", row.optInt("review_version"))
                            .put("status", "confirmed").put("class_id", row.getString("class_id"))
                            .put("note", if (row.isNull("note")) "" else row.optString("note")))
                    }
                    ReviewActionButton("Corrigir", Icons.Outlined.Edit, LeafColors.Green, ready, Modifier.weight(1f)) { reviewStatus = "corrected"; edit = true }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReviewActionButton("Duvidosa", Icons.AutoMirrored.Outlined.HelpOutline, reviewColor("uncertain"), ready, Modifier.weight(1f)) { reviewStatus = "uncertain"; edit = true }
                    ReviewActionButton("Rejeitar", Icons.Outlined.HideImage, reviewColor("rejected"), ready, Modifier.weight(1f)) { reviewStatus = "rejected"; edit = true }
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
    if (edit && active) ReviewForm(row, reviewStatus, state, classes, catalog, "Salvar e próxima", { edit = false }, actions.retryPhoto, actions.action)
    if (details) AdminSheet("Dados da foto", { details = false }) {
        item {
            AdminPanel {
                AdminSectionTitle("Previsão original")
                Text(catalog.get(row.getString("class_id")).name, style = MaterialTheme.typography.titleLarge)
                val top3 = row.optJSONArray("top3") ?: JSONArray()
                repeat(top3.length()) { i ->
                    val prediction = top3.getJSONObject(i)
                    Text("${i + 1}. ${catalog.get(prediction.getString("class_id")).name} · ${percent(prediction.optDouble("confidence").toFloat())}")
                }
            }
        }
        item {
            AdminPanel {
                AdminSectionTitle("Origem da foto")
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Enviada por: ${row.label("email")}")
                        Text("Data: ${adminDate(row.label("created_at"))}")
                        Text("Modelo de origem: ${shortId(row.label("model_sha256"))}")
                    }
                }
            }
        }
        if (!row.isNull("reviewed_at")) item {
            AdminPanel {
                AdminSectionTitle("Avaliação humana")
                Text(reviewLabel(row.optString("review_status")))
                Text("Última revisão: ${adminDate(row.label("reviewed_at"))}")
                Text("Revisor: ${shortId(row.label("reviewer_id"))}")
                if (!row.isNull("reviewed_class")) Text(catalog.get(row.getString("reviewed_class")).name)
                if (row.optString("note").isNotBlank() && !row.isNull("note")) Text(row.getString("note"))
                TextButton(onClick = { details = false; actions.history(row.getString("id")) }, enabled = !state.busy) {
                    Text("Histórico da revisão")
                }
            }
        }
    }
}

@Composable internal fun ReviewForm(
    row: JSONObject, initialStatus: String, state: AdminState, classes: List<String>, catalog: DiseaseCatalog,
    saveLabel: String, close: () -> Unit, reload: () -> Unit, action: (String, JSONObject) -> Unit,
    canChangeStatus: Boolean = false,
) {
    var status by rememberSaveable { mutableStateOf(initialStatus) }
    var selectedClass by rememberSaveable { mutableStateOf(row.optString("reviewed_class").takeIf { it in classes } ?: row.getString("class_id")) }
    var note by rememberSaveable { mutableStateOf(if (row.isNull("note")) "" else row.optString("note")) }
    val statuses = remember { reviewStatuses.filterKeys { it in setOf("confirmed", "corrected", "uncertain", "rejected") } }
    val enabled = !state.busy && !state.reviewConflict
    AdminSheet("Sua avaliação", close, dismissEnabled = !state.busy, footer = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val compact = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            LeafButton("Cancelar", close, secondary = true, modifier = Modifier.weight(1f), enabled = !state.busy, contentPadding = compact)
            LeafButton(saveLabel, {
                action("review", JSONObject().put("id", row.getString("id")).put("version", row.optInt("review_version"))
                    .put("status", status).put("class_id", if (status == "confirmed") row.getString("class_id") else selectedClass).put("note", note.trim()))
            }, modifier = Modifier.weight(1.6f), enabled = enabled, contentPadding = compact)
        }
    }) {
        item {
            if (canChangeStatus) Choice("Decisão", status, statuses.mapValues { (key, _) -> reviewLabel(key) }, enabled) { status = it }
            else Text(reviewLabel(status), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("A previsão original será preservada.", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            item {
                AdminNotice(it, error = true)
                if (state.reviewConflict) TextButton(onClick = reload, enabled = !state.busy) { Text("Recarregar revisão") }
            }
        }
        if (status == "corrected") item {
            Choice("Classe revisada", selectedClass, classes.associateWith { catalog.get(it).name }, enabled) { selectedClass = it }
        }
        item {
            OutlinedTextField(note, { if (it.length <= 2000) note = it }, label = { Text("Observação (opcional)") },
                enabled = enabled, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = leafFieldColors())
        }
    }
}

@Composable internal fun ZoomableAdminPhoto(bitmap: android.graphics.Bitmap, description: String, modifier: Modifier = Modifier) {
    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(modifier.clipToBounds()) {
        Image(bitmap.asImageBitmap(), description, Modifier.fillMaxSize().pointerInput(Unit) {
            detectTransformGestures { _, movement, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                val maxX = size.width * (scale - 1) / 2
                val maxY = size.height * (scale - 1) / 2
                pan = androidx.compose.ui.geometry.Offset((pan.x + movement.x).coerceIn(-maxX, maxX), (pan.y + movement.y).coerceIn(-maxY, maxY))
            }
        }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = pan.x, translationY = pan.y), contentScale = ContentScale.Fit)
        if (scale > 1f) FilledTonalIconButton(
            onClick = { scale = 1f; pan = androidx.compose.ui.geometry.Offset.Zero },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) { Icon(Icons.Outlined.Refresh, "Restaurar zoom") }
    }
}

@Composable private fun ReviewActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = .25f)), colors = ButtonDefaults.outlinedButtonColors(containerColor = color.copy(alpha = .05f), contentColor = color),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Sheet that hugs its content up to [heightFraction] of the window: only the
 * list scrolls, the header and an optional footer stay put. System bars and
 * IME are consumed once. The close button in the header is the only close
 * control unless a footer adds actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AdminSheet(
    title: String, onClose: () -> Unit, dismissEnabled: Boolean = true, heightFraction: Float = .94f,
    footer: (@Composable () -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    val currentDismissEnabled by rememberUpdatedState(dismissEnabled)
    val confirmValueChange: (SheetValue) -> Boolean = remember { { currentDismissEnabled || it != SheetValue.Hidden } }
    ModalBottomSheet(onDismissRequest = { if (dismissEnabled) onClose() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = confirmValueChange),
        containerColor = LeafColors.Pale, contentColor = LeafColors.Text, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }, dragHandle = null) {
        Column(Modifier.fillMaxWidth().maxHeightFraction(heightFraction).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).navigationBarsPadding().imePadding()) {
            AdminSheetHeader(title, onClose, dismissEnabled)
            LazyColumn(Modifier.weight(1f, fill = false).fillMaxWidth(), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
            footer?.let { Surface(color = Color.White, shadowElevation = 6.dp) { it() } }
        }
    }
}

/** Sheet header: grab handle, title and close button in one compact block. */
@Composable internal fun AdminSheetHeader(title: String, onClose: () -> Unit, enabled: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.padding(top = 8.dp).size(width = 32.dp, height = 4.dp).background(LeafColors.Border, RoundedCornerShape(2.dp)).align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onClose, enabled = enabled) { Icon(Icons.Outlined.Close, "Fechar painel") }
        }
    }
}

/** Caps the height at a fraction of the incoming maximum without forcing it. */
internal fun Modifier.maxHeightFraction(fraction: Float) = layout { measurable, constraints ->
    val max = if (constraints.hasBoundedHeight) (constraints.maxHeight * fraction).toInt() else constraints.maxHeight
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = max))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}
