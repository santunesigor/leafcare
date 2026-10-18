package br.com.leafcare.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.LeafColors
import org.json.JSONObject

/** Counts come from the full remote overview, never from the current 20-photo batch. */
internal fun LazyListScope.triageDashboard(data: JSONObject, catalog: DiseaseCatalog, busy: Boolean, evaluate: () -> Unit, open: (JSONObject) -> Unit) {
    val total = data.optInt("photos").coerceAtLeast(0)
    val reviewed = data.optInt("reviewed").coerceIn(0, total)
    val pending = total - reviewed
    val progress = if (total == 0) 0f else reviewed.toFloat() / total
    item {
        AdminSectionTitle("Painel de triagens", "Um olhar cuidadoso para cada folha")
    }
    item {
        Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFF194C2B)) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF194C2B), Color(0xFF2C6D40)))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AGUARDANDO AVALIAÇÃO", Modifier.weight(1f), color = Color(0xFFD6E8D4), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    Icon(Icons.Outlined.Eco, null, tint = Color(0xFFD6E8D4), modifier = Modifier.size(28.dp))
                }
                Text("$pending", color = Color.White, fontSize = 48.sp, lineHeight = 54.sp, fontWeight = FontWeight.ExtraBold)
                Text(when { total == 0 -> "As fotos aparecerão aqui quando chegarem."; pending == 0 -> "Tudo em dia. Todas as fotos receberam uma revisão."; pending == 1 -> "Uma folha precisa do seu olhar."; else -> "Folhas prontas para receber o seu olhar." }, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = evaluate, enabled = !busy && pending > 0,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF194C2B), disabledContainerColor = Color.White.copy(alpha = .15f), disabledContentColor = Color.White)) {
                    Text(if (pending == 0) "Nenhuma pendência" else "Avaliar pendentes", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Icon(if (pending == 0) Icons.Outlined.CheckCircle else Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp))
                }
            }
        }
    }
    item {
        AdminPanel {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TriageMetric("Recebidas", total, Icons.Outlined.PhotoLibrary, Modifier.weight(1f))
                VerticalDivider(Modifier.height(68.dp), color = LeafColors.Border)
                TriageMetric("Revisadas", reviewed, Icons.Outlined.TaskAlt, Modifier.weight(1f))
            }
            HorizontalDivider(color = LeafColors.Border)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Progresso das revisões", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                Text("${(progress * 100).toInt()}%", color = LeafColors.Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = LeafColors.Green, trackColor = LeafColors.Pale)
            Text("$reviewed de $total fotos revisadas", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    item { AdminSectionTitle("Consultar avaliações", "Veja as fotos em galeria, sem alterar a avaliação") }
    item {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewSelection("Confirmadas", "confirmed", Icons.Outlined.CheckCircle, LeafColors.Green, busy, Modifier.weight(1f), open)
                ReviewSelection("Corrigidas", "corrected", Icons.Outlined.Edit, Color(0xFF3F6580), busy, Modifier.weight(1f), open)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewSelection("Duvidosas", "uncertain", Icons.AutoMirrored.Outlined.HelpOutline, Color(0xFF89610D), busy, Modifier.weight(1f), open)
                ReviewSelection("Rejeitadas", "rejected", Icons.Outlined.HideImage, Color(0xFFAA4545), busy, Modifier.weight(1f), open)
            }
            OutlinedButton(onClick = { open(JSONObject().put("status", "")) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, LeafColors.Border)) {
                Icon(Icons.Outlined.PhotoLibrary, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Ver todas as fotos")
            }
        }
    }
    val counts = data.optJSONObject("classes") ?: JSONObject()
    val keys = counts.keys().asSequence().toList().sortedByDescending { counts.optInt(it) }
    if (keys.isNotEmpty()) {
        item { AdminSectionTitle("Fotos por previsão", "Toque em uma classe para consultar suas fotos") }
        items(keys, key = { "class-$it" }) { id ->
            Surface(onClick = { open(JSONObject().put("status", "").put("class_id", id)) }, enabled = !busy, color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, LeafColors.Border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(catalog.get(id).name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        AdminBadge("${counts.optInt(id)}")
                        Icon(Icons.Outlined.ChevronRight, null, tint = LeafColors.Muted, modifier = Modifier.size(20.dp))
                    }
                    LinearProgressIndicator(progress = { (counts.optInt(id).toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = LeafColors.Green.copy(alpha = .7f), trackColor = LeafColors.Pale)
                }
            }
        }
    }
}

@Composable private fun TriageMetric(label: String, count: Int, icon: ImageVector, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(17.dp), tint = LeafColors.Green)
            Text(label, color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        Text("$count", fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, color = LeafColors.Text)
    }
}

@Composable private fun ReviewSelection(title: String, status: String, icon: ImageVector, color: Color, busy: Boolean, modifier: Modifier, open: (JSONObject) -> Unit) {
    Surface(onClick = { open(JSONObject().put("status", status)) }, enabled = !busy, modifier = modifier, color = Color.White, border = BorderStroke(1.dp, LeafColors.Border), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(color.copy(alpha = .09f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(21.dp), tint = color) }
            Text(title, color = LeafColors.Text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
