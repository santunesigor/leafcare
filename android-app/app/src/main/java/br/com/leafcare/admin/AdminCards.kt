package br.com.leafcare.admin

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.leafcare.data.DiseaseCatalog
import br.com.leafcare.ui.LeafColors
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun adminDate(raw: String): String = runCatching {
    Instant.parse(raw).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd MMM yyyy · HH:mm", Locale.forLanguageTag("pt-BR")))
}.getOrDefault(raw)
internal fun reviewLabel(status: String) = when (status) {
    "confirmed" -> "Confirmada"; "corrected" -> "Corrigida"; "uncertain" -> "Duvidosa"; "rejected" -> "Rejeitada"; else -> "Pendente"
}
internal fun shortId(raw: String) = if (raw.length > 14) "${raw.take(8)}…${raw.takeLast(4)}" else raw

@Composable internal fun AdminPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable internal fun AdminSectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = LeafColors.Text)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
    }
}
@Composable internal fun AdminBadge(text: String, color: Color = LeafColors.Green) {
    Text(text, modifier = Modifier.background(color.copy(alpha = .09f), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 5.dp), color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
}
@Composable internal fun AdminNotice(text: String, error: Boolean = false) {
    val color = if (error) MaterialTheme.colorScheme.error else LeafColors.Green
    Row(Modifier.fillMaxWidth().background(color.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(if (error) Icons.Outlined.Info else Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = color)
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun AdminEmpty(title: String, subtitle: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(64.dp).background(Color(0xFFEAF2E9), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp), tint = LeafColors.Green) }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable internal fun AdminDashboard(data: JSONObject, catalog: DiseaseCatalog, busy: Boolean, onReview: () -> Unit) {
    val pending = (data.optInt("photos") - data.optInt("reviewed")).coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Color(0xFF194C2B), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("FILA DE REVISÃO", color = Color(0xFFCEE4CB), fontSize = 10.sp, letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
                    Icon(Icons.AutoMirrored.Outlined.FactCheck, null, tint = Color(0xFFCEE4CB), modifier = Modifier.size(26.dp))
                }
                Text("$pending", color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 54.sp)
                Text(if (pending == 1) "foto aguardando sua avaliação" else "fotos aguardando sua avaliação", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onReview, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF194C2B))) {
                    Text("Revisar fotos", fontWeight = FontWeight.SemiBold); Spacer(Modifier.width(10.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminStat("Usuários", data.optInt("users"), Icons.Outlined.PeopleAlt, Modifier.weight(1f))
            AdminStat("Fotos recebidas", data.optInt("photos"), Icons.Outlined.PhotoLibrary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AdminStat("Revisadas", data.optInt("reviewed"), Icons.Outlined.TaskAlt, Modifier.weight(1f))
            AdminStat("Envios pausados", data.optInt("paused"), Icons.Outlined.PauseCircle, Modifier.weight(1f))
        }
        AdminPanel {
            AdminSectionTitle("Previsões recebidas", "Distribuição por classe original")
            val counts = data.optJSONObject("classes") ?: JSONObject()
            val keys = counts.keys().asSequence().toList().sortedByDescending { counts.optInt(it) }
            val max = keys.maxOfOrNull { counts.optInt(it) }?.coerceAtLeast(1) ?: 1
            if (keys.isEmpty()) Text("As classes aparecerão quando chegarem fotos.", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
            keys.forEach { key ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(catalog.get(key).name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${counts.optInt(key)}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = LeafColors.Green)
                    }
                    LinearProgressIndicator(progress = { counts.optInt(key).toFloat() / max }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = LeafColors.Green, trackColor = LeafColors.Pale)
                }
            }
        }
        AdminPanel {
            AdminSectionTitle("Modelos de origem", "Identificação das versões que enviaram fotos")
            val models = data.optJSONObject("models") ?: JSONObject()
            models.keys().forEach { key ->
                var expanded by remember(key) { mutableStateOf(false) }
                Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Memory, null, Modifier.size(20.dp), tint = LeafColors.Muted)
                        Text(shortId(key), Modifier.weight(1f).padding(horizontal = 10.dp), style = MaterialTheme.typography.bodySmall)
                        AdminBadge("${models.optInt(key)} fotos")
                    }
                    if (expanded) SelectionContainer { Text(key, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
                }
            }
        }
    }
}
@Composable private fun AdminStat(title: String, count: Int, icon: ImageVector, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = LeafColors.Green)
            Text("$count", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = LeafColors.Text)
            Text(title, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
        }
    }
}
@Composable internal fun AdminUserCard(row: JSONObject, enabled: Boolean, onClick: () -> Unit) {
    val name = row.optString("display_name").takeIf { it.isNotBlank() && it != "null" } ?: row.label("email").substringBefore('@')
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).background(Color(0xFFEAF2E9), CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = LeafColors.Green, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f)) {
                    Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(row.label("email"), color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = LeafColors.Muted)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminBadge(if (row.optString("role") == "superadmin") "Superadmin" else "Usuário")
                AdminBadge(if (row.optBoolean("deletion_pending")) "Exclusão pendente" else if (row.optBoolean("uploads_enabled")) "Envios ativos" else "Envios pausados",
                    if (row.optBoolean("uploads_enabled")) LeafColors.Green else Color(0xFF99620A))
            }
            Text("${row.optInt("analyses")} análises  ·  ${row.optInt("photos")} fotos", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
        }
    }
}
@Composable internal fun AdminPhotoCard(row: JSONObject, catalog: DiseaseCatalog, enabled: Boolean, onClick: () -> Unit, thumbnail: @Composable () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp, 104.dp).clip(RoundedCornerShape(14.dp)).background(LeafColors.Pale), contentAlignment = Alignment.Center) { thumbnail() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AdminBadge(reviewLabel(row.optString("review_status")), when (row.optString("review_status")) { "rejected" -> Color(0xFFAF3636); "uncertain" -> Color(0xFF99620A); else -> LeafColors.Green })
                Text(catalog.get(row.getString("class_id")).name, fontWeight = FontWeight.SemiBold, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Text("${"%.1f".format(row.optDouble("confidence") * 100)}% de confiança", color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
                Text(row.label("email"), color = LeafColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = LeafColors.Muted)
        }
    }
}
@Composable internal fun AdminAuditCard(row: JSONObject, catalog: DiseaseCatalog) {
    var expanded by remember(row) { mutableStateOf(false) }
    val title = when (row.optString("action")) { "bootstrap" -> "Primeiro superadmin ativado"; "role" -> "Permissão alterada"; "pause" -> "Envios atualizados"; "review" -> "Revisão registrada"; "invite" -> "Convite enviado"; "reinvite" -> "Convite reenviado"; "recovery" -> "Recuperação enviada"; "delete_begin" -> "Exclusão iniciada"; "delete_complete" -> "Exclusão concluída"; "delete_failed" -> "Exclusão pendente"; else -> "Ação administrativa" }
    AdminPanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).background(LeafColors.Pale, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.History, null, Modifier.size(20.dp), tint = LeafColors.Green) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(adminDate(row.label("created_at")), color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
                val details = row.optJSONObject("details") ?: JSONObject()
                details.optJSONObject("after")?.let { after ->
                    Text(reviewLabel(after.optString("status")), color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
                    if (!after.isNull("class_id")) Text(catalog.get(after.getString("class_id")).name, style = MaterialTheme.typography.bodySmall)
                    after.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
                }
                if (row.optString("action") == "role" && details.has("role")) Text(if (details.optString("role") == "superadmin") "Acesso de superadmin concedido" else "Acesso de superadmin removido", style = MaterialTheme.typography.bodySmall)
                if (row.optString("action") == "pause" && details.has("enabled")) Text(if (details.optBoolean("enabled")) "Novos envios liberados" else "Envios pausados", style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "Ocultar identificação" else "Ver identificação", fontSize = 12.sp) }
        if (expanded) SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Responsável: ${row.label("actor_id")}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            Text("Registro: ${row.label("target_id")}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            row.optJSONObject("details")?.optJSONObject("before")?.let { before ->
                Text("Revisão anterior: ${reviewStatuses[before.optString("status")] ?: "Sem revisão"}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                if (!before.isNull("class_id")) Text(catalog.get(before.getString("class_id")).name, style = MaterialTheme.typography.bodySmall)
                before.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
                if (!before.isNull("reviewed_at")) Text(adminDate(before.getString("reviewed_at")), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                if (!before.isNull("reviewer_id")) Text("Revisor anterior: ${before.getString("reviewer_id")}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            }
        } }
    }
}
@Composable internal fun Choice(label: String, value: String, options: Map<String, String>, enabled: Boolean = true, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, LeafColors.Border), contentPadding = PaddingValues(14.dp)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, fontSize = 10.sp, color = LeafColors.Muted)
                Text(options[value] ?: value, color = LeafColors.Text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Icon(Icons.Outlined.ExpandMore, null, tint = LeafColors.Muted)
        }
        DropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (key, name) -> DropdownMenuItem(text = { Text(name) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}
