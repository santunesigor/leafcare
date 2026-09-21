package br.com.leafcare.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
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
fun HistoryScreen(vm: LeafCareViewModel, onCamera: () -> Unit, onResult: (String) -> Unit) {
    val rows by vm.analyses.collectAsStateWithLifecycle()
    val threshold by vm.threshold.collectAsStateWithLifecycle()
    val modelError = vm.getModelError()
    HistoryContent(rows, threshold, onCamera, onResult, { vm.getPhoto(it) }, modelError, vm::setThreshold)
}

@Composable
fun HistoryContent(rows: List<br.com.leafcare.data.AnalysisEntity>, threshold: Float, onCamera: () -> Unit, onResult: (String) -> Unit,
    photo: (String) -> Any, modelError: String?, onThreshold: (Float) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("Todas") }
    var showFilter by remember { mutableStateOf(false) }
    var showThreshold by remember { mutableStateOf(false) }
    val filtered = rows.filter { row ->
        val title = if (row.inconclusive) "Resultado inconclusivo" else row.displayName
        (title.contains(query, true) || row.classId.contains(query, true) || row.scientificName.contains(query, true)) &&
            (filter == "Todas" || (filter == "Inconclusivas") == row.inconclusive)
    }
    val grouped = filtered.groupBy { Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault()).toLocalDate() }
    val formatter = remember { DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR")) }
    Scaffold(floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = { Surface(onClick = onCamera, shape = CircleShape, shadowElevation = 12.dp, color = LeafColors.Green,
            modifier = Modifier.padding(bottom = 6.dp).size(68.dp), border = BorderStroke(6.dp, Color.White)) {
            Box(contentAlignment = Alignment.Center) { FigmaIcon(R.drawable.v3_camera, "Abrir câmera", 28) }
        } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(24.dp, 28.dp, 24.dp, 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("LEAFCARE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.04.sp, color = LeafColors.Green)
                        Spacer(Modifier.height(4.dp))
                        Text("Olá, Produtor", fontSize = 27.sp, letterSpacing = (-0.945).sp)
                        Spacer(Modifier.height(4.dp))
                        Text("Suas análises recentes", fontSize = 14.sp, color = LeafColors.Muted)
                    }
                    FigmaIcon(R.drawable.v3_logo, null, 42)
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
                                if (query.isEmpty()) Text("Pesquisar análise", fontSize = 15.sp, color = LeafColors.Muted)
                                inner()
                            })
                    }
                    Spacer(Modifier.width(10.dp))
                    Box {
                        IconButton(onClick = { showFilter = true }, modifier = Modifier.size(46.dp).border(1.dp, LeafColors.Border, RoundedCornerShape(14.dp))) { FigmaIcon(R.drawable.v3_filter, "Filtrar análises", 20) }
                        DropdownMenu(expanded = showFilter, onDismissRequest = { showFilter = false }) {
                            DropdownMenuItem(text = { Text("Confiança mínima: ${percent(threshold)}") }, onClick = { showFilter = false; showThreshold = true })
                            listOf("Todas", "Identificadas", "Inconclusivas").forEach { option ->
                                DropdownMenuItem(text = { Text(option) }, onClick = { filter = option; showFilter = false })
                            }
                        }
                    }
                }
                if (filter != "Todas") Text("Filtro: $filter", style = MaterialTheme.typography.labelMedium)
            }
            item {
                modelError?.let { message ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Preparação do modelo pendente", style = MaterialTheme.typography.titleMedium)
                            Text(message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                
            }
            if (filtered.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Eco, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(if (rows.isEmpty()) "Seu histórico começa aqui" else "Nenhuma análise encontrada", style = MaterialTheme.typography.titleLarge)
                    Text(if (rows.isEmpty()) "Fotografe uma folha ou escolha uma imagem da galeria." else "Experimente outra pesquisa ou filtro.")
                }
            }
            grouped.forEach { (date, records) ->
                item(key = "date-$date") { Text(date.format(formatter).uppercase(Locale.forLanguageTag("pt-BR")), Modifier.padding(top = 18.dp), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = LeafColors.Muted) }
                items(records, key = { it.id }) { row ->
                    OutlinedCard(Modifier.fillMaxWidth().clickable { onResult(row.id) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.outlinedCardColors(containerColor = Color.White)) {
                        Row(Modifier.padding(10.dp).heightIn(min = 66.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AsyncImage(model = photo(row.photoName), contentDescription = "Foto da folha analisada",
                                modifier = Modifier.size(72.dp, 66.dp).clip(RoundedCornerShape(11.dp)), contentScale = ContentScale.Crop)
                            Column(Modifier.weight(1f)) {
                                Text(if (row.inconclusive) "Resultado inconclusivo" else row.displayName, style = MaterialTheme.typography.titleSmall)
                                if (!row.inconclusive) Text(row.scientificName, fontStyle = FontStyle.Italic, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(5.dp))
                                Text("${percent(row.confidence)} de confiança", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                            }
                            FigmaIcon(R.drawable.v3_chevron, null, 20)
                        }
                    }
                }
            }
        }
    }
    if (showThreshold) {
        var value by remember { mutableFloatStateOf(threshold) }
        AlertDialog(onDismissRequest = { showThreshold = false }, title = { Text("Confiança mínima") }, text = {
            Column {
                Text("Abaixo deste valor, as próximas análises serão inconclusivas. Este limite ainda precisa ser validado em campo.")
                Text(percent(value), style = MaterialTheme.typography.headlineSmall)
                Slider(value = value, onValueChange = { value = it }, valueRange = 0.5f..0.95f, steps = 8)
                Text("As análises anteriores mantêm o limite usado na captura.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = { onThreshold(value); showThreshold = false }) { Text("Salvar") } },
            dismissButton = { TextButton(onClick = { showThreshold = false }) { Text("Cancelar") } })
    }
}
