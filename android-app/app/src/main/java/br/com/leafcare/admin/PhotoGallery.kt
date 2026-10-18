package br.com.leafcare.admin

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.LeafColors
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun PhotoGallery(state: AdminState, actions: AdminActions, classes: List<String>, catalog: DiseaseCatalog) {
    var filtersOpen by remember { mutableStateOf(false) }
    val query = remember(state.photoQuery) { JSONObject(state.photoQuery) }
    val back = { if (!state.busy) actions.load(state.galleryOrigin, JSONObject()) }
    BackHandler(enabled = state.selected == null, onBack = back)
    Column(Modifier.fillMaxSize().background(LeafColors.Pale)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = back, enabled = !state.busy) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Voltar da galeria") }
            Column(Modifier.weight(1f)) {
                Text("Consultar fotos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(reviewStatuses[state.photoFilterStatus] ?: "Todas", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            }
            IconButton(onClick = { filtersOpen = true }, enabled = !state.busy) { Icon(Icons.Outlined.Tune, "Filtrar fotos") }
            IconButton(onClick = actions.refresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Atualizar galeria") }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        key(state.page, state.photoQuery) {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).fillMaxWidth().testTag("photo-gallery"),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(2) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (query.optString("class_id").isNotBlank()) AdminBadge(catalog.get(query.getString("class_id")).name)
                        if (query.optString("model").isNotBlank()) AdminBadge("Modelo ${shortId(query.getString("model"))}")
                        if (query.optString("from").isNotBlank() || query.optString("until").isNotBlank()) Text("Período UTC: ${query.optString("from").ifBlank { "início" }} até ${query.optString("until").ifBlank { "hoje" }}", style = MaterialTheme.typography.bodySmall)
                        if (query.optString("user_id").isNotBlank()) Text("Conta: ${shortId(query.getString("user_id"))}", style = MaterialTheme.typography.bodySmall)
                        state.error?.let { AdminNotice(it, error = true) }
                    }
                }
                items(state.rows, key = { it.getString("id") }) { row ->
                    GalleryCard(row, catalog, !state.busy, actions.thumbnail) { actions.select(row) }
                }
                if (state.rows.isEmpty()) item(span = { GridItemSpan(2) }) {
                    AdminEmpty(if (state.busy) "Carregando fotos…" else "Sem fotos nesta seleção", "Ajuste os filtros ou atualize a consulta.", Icons.Outlined.PhotoLibrary)
                }
                item(span = { GridItemSpan(2) }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { actions.page(-1) }, enabled = state.page > 0 && !state.busy) { Text("Anterior") }
                        Text("Página ${state.page + 1}", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { actions.page(1) }, enabled = state.rows.size == 20 && !state.busy) { Text("Próxima") }
                    }
                }
            }
        }
    }
    if (filtersOpen) PhotoFilterSheet(state, classes, catalog, { filtersOpen = false }) { actions.load("gallery", it) }
    state.selected?.let { row -> GalleryPhotoDetails(row, state, actions, catalog) }
}

@Composable private fun GalleryCard(row: JSONObject, catalog: DiseaseCatalog, enabled: Boolean, thumbnail: suspend (String) -> Bitmap?, onClick: () -> Unit) {
    val id = row.getString("id")
    var attempt by remember(id) { mutableIntStateOf(0) }
    var loading by remember(id, attempt) { mutableStateOf(true) }
    var bitmap by remember(id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(id, attempt) {
        loading = true
        bitmap = try { thumbnail(id) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { null }
        finally { loading = false }
    }
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp), color = androidx.compose.ui.graphics.Color.White,
        border = BorderStroke(1.dp, LeafColors.Border), modifier = Modifier.fillMaxWidth().testTag("gallery-$id")) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(LeafColors.Border), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image != null) Image(image.asImageBitmap(), "Foto: ${catalog.get(row.getString("class_id")).name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else if (loading) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { attempt++ }, enabled = enabled) { Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Text("Tentar foto", style = MaterialTheme.typography.labelSmall) }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AdminBadge(reviewLabel(row.optString("review_status")))
                Text(catalog.get(row.getString("class_id")).name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${"%.1f".format(row.optDouble("confidence") * 100)}% · previsão original", style = MaterialTheme.typography.labelSmall, color = LeafColors.Muted)
                if (!row.isNull("reviewed_class")) Text("Revisão: ${catalog.get(row.getString("reviewed_class")).name}", style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = LeafColors.Green)
            }
        }
    }
}

@Composable private fun GalleryPhotoDetails(row: JSONObject, state: AdminState, actions: AdminActions, catalog: DiseaseCatalog) {
    var scale by remember(row) { mutableFloatStateOf(1f) }
    var panX by remember(row) { mutableFloatStateOf(0f) }
    var panY by remember(row) { mutableFloatStateOf(0f) }
    AdminSheet("Consulta da foto", actions.closeDetails, dismissEnabled = !state.busy) {
        item {
            Box(Modifier.fillMaxWidth().height(300.dp).clipToBounds().background(LeafColors.Border), contentAlignment = Alignment.Center) {
                val image = state.bitmap
                if (image != null) Image(image.asImageBitmap(), "Foto ampliada", Modifier.fillMaxSize().pointerInput(row.getString("id")) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        panX = (panX + pan.x).coerceIn(-size.width * (scale - 1) / 2, size.width * (scale - 1) / 2)
                        panY = (panY + pan.y).coerceIn(-size.height * (scale - 1) / 2, size.height * (scale - 1) / 2)
                    }
                }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = panX, translationY = panY), contentScale = ContentScale.Fit)
                else if (state.busy) CircularProgressIndicator()
                else TextButton(onClick = { actions.select(row) }) { Text("Tentar carregar foto") }
            }
            Text("Use dois dedos para ampliar.", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            if (scale > 1f) TextButton(onClick = { scale = 1f; panX = 0f; panY = 0f }) { Text("Restaurar zoom") }
        }
        state.error?.let { item { AdminNotice(it, error = true) } }
        item {
            AdminPanel {
                AdminSectionTitle("Previsão original")
                Text(catalog.get(row.getString("class_id")).name, fontWeight = FontWeight.SemiBold)
                Text("${"%.1f".format(row.optDouble("confidence") * 100)}% de confiança")
                val top3 = row.optJSONArray("top3") ?: JSONArray()
                repeat(top3.length()) { i -> val p = top3.getJSONObject(i)
                    Text("${i + 1}. ${catalog.get(p.getString("class_id")).name} · ${"%.1f".format(p.optDouble("confidence") * 100)}%", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            AdminPanel {
                AdminSectionTitle("Avaliação humana")
                AdminBadge(reviewLabel(row.optString("review_status")))
                if (!row.isNull("reviewed_class")) Text(catalog.get(row.getString("reviewed_class")).name)
                if (!row.isNull("reviewed_at")) Text(adminDate(row.getString("reviewed_at")), style = MaterialTheme.typography.bodySmall)
                if (!row.isNull("reviewer_id")) Text("Revisor: ${shortId(row.getString("reviewer_id"))}", style = MaterialTheme.typography.bodySmall)
                if (!row.isNull("note") && row.optString("note").isNotBlank()) Text(row.getString("note"))
            }
        }
        item { Text("Enviada por: ${row.label("email")}\n${adminDate(row.label("created_at"))}\nModelo: ${shortId(row.label("model_sha256"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
    }
}

@Composable internal fun PhotoFilterSheet(state: AdminState, classes: List<String>, catalog: DiseaseCatalog, close: () -> Unit, apply: (JSONObject) -> Unit) {
    val query = remember(state.photoQuery) { JSONObject(state.photoQuery) }
    var status by rememberSaveable(state.photoQuery) { mutableStateOf(state.photoFilterStatus) }
    var classFilter by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("class_id")) }
    var model by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("model")) }
    var user by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("user_id")) }
    var from by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("from")) }
    var until by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("until")) }
    var advanced by rememberSaveable(state.photoQuery) { mutableStateOf(model.isNotBlank() || user.isNotBlank()) }
    AdminSheet("Filtrar fotos", close, footer = {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = close) { Text("Cancelar") }
            Button(onClick = { close(); apply(JSONObject().put("status", status).put("class_id", classFilter).put("model", model.trim()).put("user_id", user.trim()).put("from", from.trim()).put("until", until.trim())) },
                enabled = !state.busy && from != "!invalid!" && until != "!invalid!", modifier = Modifier.weight(1f)) { Text("Aplicar filtros") }
        }
    }) {
        item { Choice("Situação", status, reviewStatuses) { status = it } }
        item { Choice("Classe prevista", classFilter, linkedMapOf("" to "Todas") + classes.associateWith { catalog.get(it).name }) { classFilter = it } }
        item { AdminDateField("De", from) { from = it } }
        item { AdminDateField("Até", until) { until = it } }
        item { TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Menos filtros" else "Mais filtros") } }
        if (advanced) {
            item { OutlinedTextField(user, { user = it }, label = { Text("Identificador do usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(model, { model = it }, label = { Text("Identificador do modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AdminDateField(label: String, isoDate: String, onDateChange: (String) -> Unit) {
    val displayFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu")
    val isoFormat = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value, isoFormat) }.getOrNull()
    var text by remember { mutableStateOf(parse(isoDate)?.format(displayFormat).orEmpty()) }
    LaunchedEffect(isoDate) { if (isoDate != "!invalid!") text = parse(isoDate)?.format(displayFormat).orEmpty() }
    var showPicker by remember { mutableStateOf(false) }
    val selected = parse(isoDate) ?: parse(text)
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            val digits = input.filter(Char::isDigit).take(8)
            text = buildString {
                digits.forEachIndexed { index, char ->
                    if (index == 2 || index == 4) append('/')
                    append(char)
                }
            }
            onDateChange(parse(text)?.format(isoFormat) ?: if (text.isEmpty()) "" else "!invalid!")
        },
        label = { Text(label) },
        placeholder = { Text("DD/MM/AAAA") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = { IconButton(onClick = { showPicker = true }) { Icon(Icons.Outlined.CalendarMonth, "Escolher data") } },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
    )
    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selected?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli())
        val pickerColors = DatePickerDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.White,
            titleContentColor = LeafColors.Text,
            headlineContentColor = LeafColors.Text,
            weekdayContentColor = LeafColors.Muted,
            subheadContentColor = LeafColors.Muted,
            yearContentColor = LeafColors.Text,
            currentYearContentColor = LeafColors.Green,
            selectedYearContentColor = androidx.compose.ui.graphics.Color.White,
            selectedYearContainerColor = LeafColors.Green,
            dayContentColor = LeafColors.Text,
            disabledDayContentColor = LeafColors.Muted.copy(alpha = .38f),
            selectedDayContentColor = androidx.compose.ui.graphics.Color.White,
            disabledSelectedDayContentColor = androidx.compose.ui.graphics.Color.White.copy(alpha = .38f),
            selectedDayContainerColor = LeafColors.Green,
            todayContentColor = LeafColors.Green,
            todayDateBorderColor = LeafColors.Green,
            dayInSelectionRangeContentColor = LeafColors.Text,
            dayInSelectionRangeContainerColor = LeafColors.Pale,
            dividerColor = LeafColors.Border
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateChange(date.format(isoFormat))
                        text = date.format(displayFormat)
                    }
                    showPicker = false
                }) { Text("Selecionar") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } },
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 0.dp,
            colors = pickerColors
        ) { DatePicker(state = pickerState, showModeToggle = false, colors = pickerColors) }
    }
}
