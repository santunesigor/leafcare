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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
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
import br.com.leafcare.ui.LeafButton
import br.com.leafcare.ui.LeafColors
import br.com.leafcare.ui.percent
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

@Composable internal fun PhotoGallery(state: AdminState, actions: AdminActions, classes: List<String>, catalog: DiseaseCatalog) {
    var filtersOpen by remember { mutableStateOf(false) }
    val query = remember(state.photoQuery) { JSONObject(state.photoQuery) }
    val back = { if (!state.busy) actions.load(state.galleryOrigin, JSONObject()) }
    BackHandler(enabled = state.selected == null, onBack = back)
    Column(Modifier.fillMaxSize().background(LeafColors.Pale)) {
        AdminTopBar("Consultar fotos", reviewStatuses[state.photoFilterStatus] ?: "Todas", back, "Voltar da galeria", state.busy, backEnabled = !state.busy) {
            IconButton(onClick = { filtersOpen = true }, enabled = !state.busy) { Icon(Icons.Outlined.Tune, "Filtrar fotos", tint = LeafColors.Green) }
            IconButton(onClick = actions.refresh, enabled = !state.busy) { Icon(Icons.Outlined.Refresh, "Atualizar galeria", tint = LeafColors.Green) }
        }
        key(state.page, state.photoQuery) {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).fillMaxWidth().testTag("photo-gallery"),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                item(span = { GridItemSpan(2) }) { AdminPager(state.page, state.rows.size == 20, state.busy, actions.page) }
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
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), color = androidx.compose.ui.graphics.Color.White,
        border = BorderStroke(1.dp, LeafColors.Border), modifier = Modifier.fillMaxWidth().testTag("gallery-$id")) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).background(LeafColors.Border), contentAlignment = Alignment.Center) {
                val image = bitmap
                if (image != null) Image(image.asImageBitmap(), "Foto: ${catalog.get(row.getString("class_id")).name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else if (loading) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                else TextButton(onClick = { attempt++ }, enabled = enabled) { Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp)); Text("Recarregar", style = MaterialTheme.typography.labelSmall) }
            }
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AdminBadge(reviewLabel(row.optString("review_status")), reviewColor(row.optString("review_status")))
                Text(catalog.get(row.getString("class_id")).name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${percent(row.optDouble("confidence").toFloat())} · previsão original", style = MaterialTheme.typography.labelSmall, color = LeafColors.Muted)
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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${catalog.get(row.getString("class_id")).name} · ${percent(row.optDouble("confidence").toFloat())} de confiança", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    val top3 = row.optJSONArray("top3") ?: JSONArray()
                    repeat(top3.length()) { i -> val p = top3.getJSONObject(i)
                        Text("${i + 1}. ${catalog.get(p.getString("class_id")).name} · ${percent(p.optDouble("confidence").toFloat())}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                    }
                }
            }
        }
        item {
            AdminPanel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    AdminSectionTitle("Avaliação humana")
                    AdminBadge(reviewLabel(row.optString("review_status")), reviewColor(row.optString("review_status")))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!row.isNull("reviewed_class")) Text(catalog.get(row.getString("reviewed_class")).name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    if (!row.isNull("note") && row.optString("note").isNotBlank()) Text(row.getString("note"), style = MaterialTheme.typography.bodyMedium)
                    val meta = listOfNotNull(
                        row.optString("reviewed_at").takeIf { !row.isNull("reviewed_at") }?.let(::adminDate),
                        row.optString("reviewer_id").takeIf { !row.isNull("reviewer_id") }?.let { "Revisor: ${shortId(it)}" },
                    )
                    if (meta.isNotEmpty()) Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                }
            }
        }
        item { Text("Enviada por: ${row.label("email")}\n${adminDate(row.label("created_at"))}\nModelo: ${shortId(row.label("model_sha256"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun PhotoFilterSheet(state: AdminState, classes: List<String>, catalog: DiseaseCatalog, close: () -> Unit, apply: (JSONObject) -> Unit) {
    val query = remember(state.photoQuery) { JSONObject(state.photoQuery) }
    var status by rememberSaveable(state.photoQuery) { mutableStateOf(state.photoFilterStatus) }
    var classFilter by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("class_id")) }
    var model by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("model")) }
    var user by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("user_id")) }
    var from by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("from")) }
    var until by rememberSaveable(state.photoQuery) { mutableStateOf(query.optString("until")) }
    var advanced by rememberSaveable(state.photoQuery) { mutableStateOf(model.isNotBlank() || user.isNotBlank()) }
    val invalidDate = from == "!invalid!" || until == "!invalid!"
    val inverted = !invalidDate && from.isNotBlank() && until.isNotBlank() && from > until
    AdminSheet("Filtrar fotos", close, footer = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LeafButton("Limpar", {
                status = "pending"; classFilter = ""; model = ""; user = ""; from = ""; until = ""
            }, secondary = true, modifier = Modifier.weight(1f), enabled = !state.busy)
            LeafButton("Aplicar filtros", {
                close(); apply(JSONObject().put("status", status).put("class_id", classFilter).put("model", model.trim()).put("user_id", user.trim()).put("from", from.trim()).put("until", until.trim()))
            }, modifier = Modifier.weight(1.6f), enabled = !state.busy && !invalidDate && !inverted)
        }
    }) {
        item {
            FilterSection("Situação") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    reviewStatuses.forEach { (key, name) -> StatusChip(name, key == status) { status = key } }
                }
            }
        }
        item {
            FilterSection("Classe prevista") {
                Choice("Classe", classFilter, linkedMapOf("" to "Todas") + classes.associateWith { catalog.get(it).name }) { classFilter = it }
            }
        }
        item {
            FilterSection("Período") {
                AdminDateField("De", from) { from = it }
                AdminDateField("Até", until, error = if (inverted) "A data final é anterior à inicial." else null) { until = it }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { advanced = !advanced }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (advanced) "Menos filtros" else "Mais filtros", color = LeafColors.Green, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Icon(if (advanced) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, tint = LeafColors.Green, modifier = Modifier.size(20.dp))
            }
        }
        if (advanced) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(user, { user = it }, label = { Text("Identificador do usuário") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = leafFieldColors())
                    OutlinedTextField(model, { model = it }, label = { Text("Identificador do modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = leafFieldColors())
                }
            }
        }
    }
}

@Composable private fun FilterSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LeafColors.Text)
        content()
    }
}

/** Same selection language as the history filter: Pale + green label + check. */
@Composable private fun StatusChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.heightIn(min = 40.dp).clip(shape)
            .background(if (selected) LeafColors.Tint else Color.White)
            .border(1.dp, if (selected) LeafColors.Green else LeafColors.Border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected) Icon(Icons.Outlined.Check, null, tint = LeafColors.Green, modifier = Modifier.size(16.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = if (selected) LeafColors.Green else LeafColors.Text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable internal fun leafFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LeafColors.Green, unfocusedBorderColor = LeafColors.Border,
    focusedLabelColor = LeafColors.Green, unfocusedLabelColor = LeafColors.Muted,
    unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, cursorColor = LeafColors.Green,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AdminDateField(label: String, isoDate: String, error: String? = null, onDateChange: (String) -> Unit) {
    val displayFormat = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
    val isoFormat = DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    fun parse(value: String): LocalDate? = runCatching { LocalDate.parse(value, isoFormat) }.getOrNull()
    // The field shows DD/MM/AAAA: typed text is parsed in that format, not ISO.
    fun parseTyped(value: String): LocalDate? = runCatching { LocalDate.parse(value, displayFormat) }.getOrNull()
    var text by remember { mutableStateOf(parse(isoDate)?.format(displayFormat).orEmpty()) }
    LaunchedEffect(isoDate) { if (isoDate != "!invalid!") text = parse(isoDate)?.format(displayFormat).orEmpty() }
    var showPicker by remember { mutableStateOf(false) }
    val selected = parse(isoDate) ?: parseTyped(text)
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
            onDateChange(parseTyped(text)?.format(isoFormat) ?: if (text.isEmpty()) "" else "!invalid!")
        },
        label = { Text(label) },
        placeholder = { Text("DD/MM/AAAA") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        trailingIcon = { IconButton(onClick = { showPicker = true }) { Icon(Icons.Outlined.CalendarMonth, "Escolher data") } },
        isError = isoDate == "!invalid!" || error != null,
        supportingText = (if (isoDate == "!invalid!") "Use o formato DD/MM/AAAA com uma data válida." else error)?.let { message -> { Text(message) } },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = leafFieldColors()
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
