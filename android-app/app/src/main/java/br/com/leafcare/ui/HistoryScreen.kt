package br.com.leafcare.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.leafcare.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun percent(value: Float) = String.format(Locale.forLanguageTag("pt-BR"), "%.0f%%", value * 100)

@Composable
fun HistoryScreen(vm: LeafCareViewModel, onCamera: () -> Unit, onResult: (String) -> Unit, onProfile: () -> Unit = {}, displayName: String? = null) {
    val rows by vm.analyses.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val modelError = vm.getModelError()
    HistoryContent(rows, threshold, onCamera, onResult, { vm.getPhoto(it) }, modelError, onProfile, displayName)
}

/**
 * Home greeting from the authenticated user's display name.
 * Falls back to a plain greeting when the name is unavailable.
 */
internal fun homeGreeting(displayName: String?): String =
    if (displayName.isNullOrBlank()) "Olá" else "Olá, $displayName"

@Composable
fun HistoryContent(rows: List<br.com.leafcare.data.AnalysisEntity>, threshold: Float, onCamera: () -> Unit, onResult: (String) -> Unit,
    photo: (String) -> Any, modelError: String?, onProfile: () -> Unit = {}, displayName: String? = null) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todas") }
    var showFilter by remember { mutableStateOf(false) }
    val filtered = rows.filter { row ->
        val title = if (row.inconclusive) stringResource(R.string.result_inconclusive_title) else row.displayName
        (title.contains(query, true) || row.classId.contains(query, true) || row.scientificName.contains(query, true)) &&
            (filter == stringResource(R.string.filter_all) || (filter == stringResource(R.string.filter_inconclusive)) == row.inconclusive)
    }
    val grouped = filtered.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    val formatter = remember { DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR")) }
    Scaffold(floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { Surface(onClick = onCamera, shape = CircleShape, shadowElevation = 12.dp, color = LeafColors.Green,
            modifier = Modifier.padding(bottom = 6.dp).size(68.dp), border = BorderStroke(6.dp, Color.White)) {
            Box(contentAlignment = Alignment.Center) { FigmaIcon(R.drawable.v3_camera, stringResource(R.string.camera_button_desc), 28) }
        } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp, 28.dp, 24.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FigmaIcon(R.drawable.v3_logo, null, 16)
                            Text(stringResource(R.string.history_title), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.04.sp, color = LeafColors.Green)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(homeGreeting(displayName), fontSize = 27.sp, letterSpacing = (-0.945).sp)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.history_subtitle), fontSize = 14.sp, color = LeafColors.Muted)
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(onClick = onProfile, shape = CircleShape, color = LeafColors.Pale,
                        border = BorderStroke(1.dp, LeafColors.Border), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            FigmaIcon(R.drawable.v3_logo, "Abrir perfil", 26)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 46.dp).background(LeafColors.Pale, RoundedCornerShape(14.dp)).border(1.dp, LeafColors.Border, RoundedCornerShape(14.dp)).padding(13.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        FigmaIcon(R.drawable.v3_search, null, 19)
                        BasicTextField(value = query, onValueChange = { query = it }, singleLine = true, modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = LeafColors.Text), decorationBox = { inner ->
                                if (query.isEmpty()) Text(stringResource(R.string.search_hint), fontSize = 15.sp, color = LeafColors.Muted)
                                inner()
                            })
                    }
                    Spacer(Modifier.width(10.dp))
                    Box {
                        IconButton(onClick = { showFilter = true }, modifier = Modifier.size(46.dp).border(1.dp, LeafColors.Border, RoundedCornerShape(14.dp))) { FigmaIcon(R.drawable.v3_filter, stringResource(R.string.filter_button_desc), 20) }
                        if (showFilter) {
                            // Compact menu anchored below the filter button, right-aligned,
                            // sized to its content. Explicit position provider: the default
                            // popup alignment opened away from the button.
                            val density = LocalDensity.current
                            val menuOffset = remember(density) {
                                object : PopupPositionProvider {
                                    override fun calculatePosition(
                                        anchorBounds: IntRect,
                                        windowSize: IntSize,
                                        layoutDirection: LayoutDirection,
                                        popupContentSize: IntSize
                                    ): IntOffset {
                                        val x = (anchorBounds.right - popupContentSize.width)
                                            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
                                        val y = (anchorBounds.bottom + with(density) { 4.dp.roundToPx() })
                                            .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
                                        return IntOffset(x, y)
                                    }
                                }
                            }
                            Popup(popupPositionProvider = menuOffset, onDismissRequest = { showFilter = false }) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, LeafColors.Border),
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.width(200.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        listOf(stringResource(R.string.filter_all), stringResource(R.string.filter_identified), stringResource(R.string.filter_inconclusive)).forEach { option ->
                                            val selected = filter == option
                                            Row(
                                                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { filter = option; showFilter = false }
                                                    .background(if (selected) LeafColors.Pale else Color.Transparent).padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                if (selected) FigmaIcon(R.drawable.v3_check, null, 16) else Spacer(Modifier.size(16.dp))
                                                Text(
                                                    option,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                                    ),
                                                    color = LeafColors.Text
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (filter != stringResource(R.string.filter_all)) Text("${stringResource(R.string.filter_prefix)}$filter", style = MaterialTheme.typography.labelMedium)
            }
            item {
                modelError?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.model_error_title), style = MaterialTheme.typography.titleMedium)
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }

            }
            if (filtered.isEmpty()) item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Eco, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (rows.isEmpty()) stringResource(R.string.empty_history_title) else stringResource(R.string.empty_search_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = LeafColors.Text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        if (rows.isEmpty()) stringResource(R.string.empty_history_subtitle) else stringResource(R.string.empty_search_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = LeafColors.Muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            grouped.forEach { (date, records) ->
                item(key = "date-$date") { Text(date.format(formatter).uppercase(Locale.forLanguageTag("pt-BR")), Modifier.padding(top = 18.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = LeafColors.Muted) }
                items(records, key = { it.id }) { row ->
                    OutlinedCard(Modifier.fillMaxWidth().clickable { onResult(row.id) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(10.dp).heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(model = photo(row.photoName), contentDescription = stringResource(R.string.cd_photo_history),
                                modifier = Modifier.size(72.dp, 66.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f)) {
                                Text(if (row.inconclusive) stringResource(R.string.result_inconclusive_title) else row.displayName, style = MaterialTheme.typography.titleSmall)
                                if (!row.inconclusive) Text(row.scientificName, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(5.dp))
                                Text("${percent(row.confidence)} ${stringResource(R.string.confidence_label)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                            FigmaIcon(R.drawable.v3_chevron, null, 20)
                        }
                    }
                }
            }
            if (rows.isNotEmpty()) item {
                Text(
                    stringResource(R.string.confidence_threshold_label, percent(threshold)),
                    style = MaterialTheme.typography.bodySmall,
                    color = LeafColors.Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}