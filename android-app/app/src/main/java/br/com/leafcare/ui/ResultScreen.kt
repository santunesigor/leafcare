package br.com.leafcare.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.leafcare.R
import br.com.leafcare.data.*
import coil.compose.AsyncImage
import org.json.JSONArray
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ResultScreen(id: String, vm: LeafCareViewModel, onBack: () -> Unit, onCamera: () -> Unit) {
    val flow = remember(id) { vm.repository.observe(id) }
    val row by flow.collectAsStateWithLifecycle(initialValue = null)
    var confirmDelete by remember { mutableStateOf(false) }
    val analysis = row
    if (analysis == null) Column(Modifier.padding(24.dp)) {
        Text("Carregando análise. Se ela foi excluída, retorne ao histórico.")
        TextButton(onClick = onBack) { Text("Voltar ao histórico") }
    } else ResultContent(analysis, vm.catalog.get(analysis.classId), vm.repository.photo(analysis.photoName), onBack, onCamera, { confirmDelete = true })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Excluir esta análise?") },
        text = { Text("O registro e sua foto serão removidos deste aparelho.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete(id, onBack) }) { Text("Excluir") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } })
}

@Composable
fun ResultContent(analysis: AnalysisEntity, disease: DiseaseInfo, photo: Any, onBack: () -> Unit, onCamera: () -> Unit, onDelete: () -> Unit) {
    val top3 = remember(analysis.top3Json) { JSONArray(analysis.top3Json) }
    var expanded by remember(analysis.id) { mutableStateOf(analysis.inconclusive) }
    var enlarged by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(256.dp).background(Color(0xFF1E3521))) {
            AsyncImage(photo, "Foto analisada", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0f to Color(0x61050F06), .38f to Color.Transparent, 1f to Color(0x40050F06))))
            Box(Modifier.padding(start = 16.dp, top = 18.dp)) { RoundIconButton(R.drawable.v3_back_light, "Voltar", onBack, dark = true) }
            Text("Foto analisada", Modifier.align(Alignment.TopEnd).padding(top = 31.dp, end = 22.dp), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(horizontal = 22.dp, vertical = 24.dp)) {
            Text(if (analysis.inconclusive) "RESULTADO INCONCLUSIVO" else "POSSÍVEL IDENTIFICAÇÃO", color = LeafColors.Green, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.54.sp)
            Spacer(Modifier.height(4.dp))
            Text(if (analysis.inconclusive) "Fotografe novamente" else analysis.displayName, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-1.28).sp)
            Spacer(Modifier.height(4.dp))
            Text(if (analysis.inconclusive) "Não foi possível identificar com confiança suficiente." else analysis.scientificName,
                fontSize = 15.sp, lineHeight = 23.sp, color = LeafColors.Muted, fontStyle = if (analysis.inconclusive) FontStyle.Normal else FontStyle.Italic)
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                Column {
                    Text(percent(analysis.confidence), color = LeafColors.Green, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("de confiança", fontSize = 12.sp, color = LeafColors.Muted)
                }
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFFE4EBE4))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(analysis.confidence.coerceIn(0f,1f)).background(LeafColors.Green, RoundedCornerShape(99.dp)))
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().background(Color(0xFFFFF9DF), RoundedCornerShape(14.dp)).border(1.dp, Color(0xFFEADB9A), RoundedCornerShape(14.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                FigmaIcon(R.drawable.v3_warning, null, 19)
                Column {
                    Text("Triagem visual", color = Color(0xFF6B5714), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Confirme a identificação com um profissional.", color = Color(0xFF6B5714), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ocultar possibilidades" else "Ver as 3 possibilidades", fontSize = 13.sp) }
            if (expanded) {
                repeat(top3.length()) { index ->
                    val p = top3.getJSONObject(index)
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${index+1}.")
                        Text(p.getString("name"), Modifier.weight(1f))
                        Text(percent(p.getDouble("confidence").toFloat()), color = LeafColors.Green)
                    }
                }
                Text("Confiança do modelo não é probabilidade comprovada de doença.", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            }
            if (!analysis.inconclusive) {
                if (disease.referenceImages.isNotEmpty()) {
                    Section("Imagens semelhantes") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            disease.referenceImages.forEach { asset ->
                                AsyncImage(asset, "Referência de ${disease.name}", Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(13.dp)).clickable { enlarged = asset }, contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
                Section("Descrição") { Text(disease.description, color = Color(0xFF526054)) }
                Section("Principais sintomas") {
                    Column(Modifier.fillMaxWidth().border(1.dp, LeafColors.Border, RoundedCornerShape(15.dp))) {
                        Symptom("APARÊNCIA", disease.appearance.ifBlank { disease.symptoms.joinToString(" ") })
                        HorizontalDivider(color = Color(0xFFE8EDE8))
                        Symptom("REGIÃO AFETADA", disease.affectedRegion.ifBlank { "Compare outras folhas e partes da planta com auxílio de um técnico." })
                        HorizontalDivider(color = Color(0xFFE8EDE8))
                        Symptom("EVOLUÇÃO DOS SINTOMAS", disease.symptomEvolution.ifBlank { "Registre novas fotos para acompanhar as alterações ao longo do tempo." })
                    }
                }
                Section("Condições favoráveis") { InfoCard(disease.favorableConditions, Color(0xFFF5F8F4), Color(0xFFDCE5DC)) }
            }
            Section("Orientação ao produtor") {
                InfoCard(if (analysis.inconclusive) "Registre outras folhas afetadas e observe se há sintomas em outras plantas. Procure assistência técnica para confirmar a causa." else disease.guidance,
                    Color(0xFFEDF7EC), Color(0xFFCCE1CC))
            }
            if (!disease.reviewed) Text("Conteúdo informativo: revisão agronômica pendente.", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            val date = Instant.ofEpochMilli(analysis.createdAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")))
            Text("Salva neste aparelho em $date", Modifier.padding(top = 18.dp), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            Text("Inferência: ${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", analysis.inferenceMs)} ms • Limite: ${percent(analysis.threshold)}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            Spacer(Modifier.height(22.dp))
            LeafButton("Voltar ao histórico", onBack)
            Spacer(Modifier.height(10.dp))
            LeafButton("Fotografar novamente", onCamera, true)
            TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Excluir análise", color = MaterialTheme.colorScheme.error) }
        }
    }
    enlarged?.let { asset -> Dialog(onDismissRequest = { enlarged = null }) {
        Surface(shape = RoundedCornerShape(16.dp)) { Column {
            AsyncImage(asset, "Imagem de referência ampliada", Modifier.fillMaxWidth().aspectRatio(1f), contentScale = ContentScale.Fit)
            TextButton(onClick = { enlarged = null }) { Text("Fechar") }
        } }
    } }
}
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 5.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 17.sp, lineHeight = 26.sp)
            if (title == "Imagens semelhantes") FigmaIcon(R.drawable.v3_images, null, 18)
        }
        content()
    }
}
@Composable
private fun Symptom(title: String, body: String) {
    Column(Modifier.padding(horizontal = 14.dp, vertical = 17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = LeafColors.Green, letterSpacing = .36.sp)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4E5B50))
    }
}
@Composable
private fun InfoCard(text: String, background: Color, border: Color) {
    Text(text, Modifier.fillMaxWidth().background(background, RoundedCornerShape(15.dp)).border(1.dp, border, RoundedCornerShape(15.dp)).padding(15.dp),
        style = MaterialTheme.typography.bodyMedium, color = Color(0xFF304C33))
}
