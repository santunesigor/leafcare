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
import br.com.leafcare.ui.LeafButton
import br.com.leafcare.ui.LeafColors
import br.com.leafcare.ui.percent
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
/** One colour per review status, shared by badges, actions and the triage shortcuts. */
internal fun reviewColor(status: String) = when (status) {
    "confirmed" -> LeafColors.Green; "corrected" -> LeafColors.Info; "uncertain" -> LeafColors.Warning; "rejected" -> LeafColors.Danger; else -> LeafColors.Muted
}
/** Line under the pending count, shared by the summary and the triage panel. */
internal fun pendingLabel(total: Int, pending: Int) = when {
    total == 0 -> "Nenhuma foto recebida ainda"
    pending == 0 -> "Tudo revisado"
    pending == 1 -> "foto aguardando avaliação"
    else -> "fotos aguardando avaliação"
}
/** Whole days until a training copy is deleted (90 days after upload); null when the photo has none. */
internal fun trainingDaysLeft(row: JSONObject, now: Instant = Instant.now()): Int? {
    if (row.isNull("training_expires_at")) return null
    val raw = row.optString("training_expires_at").takeIf { it.isNotBlank() } ?: return null
    val expires = runCatching { java.time.OffsetDateTime.parse(raw).toInstant() }.getOrElse { runCatching { Instant.parse(raw) }.getOrNull() } ?: return null
    val hours = java.time.Duration.between(now, expires).toHours().coerceAtLeast(0)
    return ((hours + 23) / 24).toInt()
}
/** "Treino · N dias": the photo can train the model; amber when it expires within 14 days. */
@Composable internal fun TrainingBadge(row: JSONObject, short: Boolean = false) {
    val days = trainingDaysLeft(row) ?: return
    AdminBadge(if (short) "Treino" else "Treino · ${if (days <= 1) "1 dia" else "$days dias"}", if (days <= 14) LeafColors.Warning else LeafColors.Info)
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
        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(30.dp), tint = LeafColors.Green) }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
/** Overview that fits one screen: pending work, totals, review results and models. */
@Composable internal fun AdminDashboard(data: JSONObject, busy: Boolean, onReview: () -> Unit,
    browse: (JSONObject) -> Unit, managesAccounts: Boolean, onReviewTraining: () -> Unit = {}, users: () -> Unit) {
    val total = data.optInt("photos")
    val reviewed = data.optInt("reviewed")
    val pending = (total - reviewed).coerceAtLeast(0)
    val statuses = data.optJSONObject("statuses")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = LeafColors.Forest, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("$pending", color = Color.White, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold)
                    Text(pendingLabel(total, pending), color = LeafColors.OnForest, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onReview, enabled = !busy && pending > 0, modifier = Modifier.heightIn(min = 44.dp), shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = LeafColors.Forest, disabledContainerColor = Color.White.copy(alpha = .15f), disabledContentColor = Color.White)) {
                    Text(if (pending > 0) "Avaliar" else "Em dia", fontWeight = FontWeight.SemiBold)
                    if (pending > 0) { Spacer(Modifier.width(6.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminStatTile("$total", "Recebidas", Modifier.weight(1f))
            AdminStatTile("$reviewed", "Revisadas", Modifier.weight(1f))
            if (managesAccounts) {
                val paused = if (data.isNull("paused")) 0 else data.optInt("paused")
                AdminStatTile(if (data.isNull("users")) "—" else "${data.optInt("users")}", "Usuários", Modifier.weight(1f),
                    detail = if (paused > 0) "$paused pausados" else null, enabled = !busy, onClick = users)
            }
        }
        Column {
            AdminSectionHeader("Resultados das revisões", "Ver todas", !busy) { browse(JSONObject().put("status", "")) }
            if (statuses == null) Text("Contagens por situação indisponíveis nesta versão do servidor.", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            else {
                ReviewDistribution(statuses, pending)
                Spacer(Modifier.height(6.dp))
                ReviewLegend(statuses, busy, browse)
            }
        }
        data.optJSONObject("training")?.let { training -> TrainingSummary(training, busy, onReviewTraining) }
        val models = data.optJSONObject("models") ?: JSONObject()
        Column {
            AdminSectionHeader("Modelos de origem")
            if (models.length() == 0) Text("Nenhum modelo recebido.", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
            else AdminGroup {
                models.keys().asSequence().toList().sortedByDescending { models.optInt(it) }.forEachIndexed { index, key ->
                    if (index > 0) HorizontalDivider(color = LeafColors.Border)
                    AdminCountRow("Modelo ${shortId(key)}", "${models.optInt(key)} fotos", null, !busy) { browse(JSONObject().put("status", "").put("model", key)) }
                }
            }
        }
    }
}

/**
 * Training copies are what can retrain the model: only confirmed or corrected
 * ones enter a dataset, and each is deleted 90 days after upload.
 */
@Composable private fun TrainingSummary(training: JSONObject, busy: Boolean, onReviewTraining: () -> Unit) {
    val awaiting = training.optInt("awaiting_review")
    val expiring = training.optInt("expiring_soon")
    Column {
        AdminSectionHeader("Treinamento do modelo", if (awaiting > 0) "Revisar cópias" else null, !busy, onReviewTraining)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminStatTile("${training.optInt("ready")}", "Prontas", Modifier.weight(1f))
            AdminStatTile("$awaiting", "Aguardando", Modifier.weight(1f), detail = if (expiring > 0) "$expiring vencendo" else null,
                enabled = !busy, onClick = if (awaiting > 0) onReviewTraining else null)
            AdminStatTile("${training.optInt("excluded")}", "Fora do treino", Modifier.weight(1f))
        }
        Text("Só cópias confirmadas ou corrigidas entram no treino. Cada cópia é apagada 90 dias após o envio.",
            Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
    }
}

private val reviewedStatuses = listOf("confirmed", "corrected", "uncertain", "rejected")

/** One bar split by review result; the grey tail is what is still pending. */
@Composable private fun ReviewDistribution(statuses: JSONObject, pending: Int) {
    val parts = reviewedStatuses.map { it to statuses.optInt(it) } + ("pending" to pending)
    if (parts.sumOf { it.second } == 0) return
    Row(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        parts.filter { it.second > 0 }.forEach { (status, count) ->
            Box(Modifier.weight(count.toFloat()).fillMaxHeight().background(if (status == "pending") LeafColors.Border else reviewColor(status)))
        }
    }
}

/** Legend for the bar: two columns of dot, label and count; each opens the gallery. */
@Composable private fun ReviewLegend(statuses: JSONObject, busy: Boolean, browse: (JSONObject) -> Unit) {
    reviewedStatuses.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { status ->
                Row(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).clickable(enabled = !busy) { browse(JSONObject().put("status", status)) }.padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).background(reviewColor(status), CircleShape))
                    Text(reviewStatuses.getValue(status), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = LeafColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${statuses.optInt(status)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** The four review results as tappable rows that open the gallery. */
@Composable internal fun ReviewStatusGroup(statuses: JSONObject?, busy: Boolean, browse: (JSONObject) -> Unit) {
    AdminGroup {
        reviewedStatuses.forEachIndexed { index, status ->
            if (index > 0) HorizontalDivider(color = LeafColors.Border)
            AdminCountRow(reviewStatuses.getValue(status), statuses?.let { "${it.optInt(status)}" } ?: "", reviewColor(status), !busy) { browse(JSONObject().put("status", status)) }
        }
    }
}

@Composable internal fun AdminUserRow(row: JSONObject, enabled: Boolean, onClick: () -> Unit) {
    val name = row.optString("display_name").takeIf { it.isNotBlank() && it != "null" } ?: row.label("email").substringBefore('@')
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = LeafColors.Green, fontWeight = FontWeight.Bold) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(row.label("email"), color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                AdminBadge(roleLabel(row.optString("role")))
                UploadsBadge(row)
                Text("${row.optInt("analyses")} análises", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = LeafColors.Muted)
    }
}
/** Same text and colour in the user list and in the user details. */
@Composable internal fun UploadsBadge(row: JSONObject) = when {
    row.optBoolean("deletion_pending") -> AdminBadge("Exclusão pendente", LeafColors.Danger)
    row.optBoolean("uploads_enabled") -> AdminBadge("Envios ativos")
    else -> AdminBadge("Envios pausados", LeafColors.Warning)
}
@Composable internal fun AdminPhotoCard(row: JSONObject, catalog: DiseaseCatalog, enabled: Boolean, onClick: () -> Unit, thumbnail: @Composable () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp, 104.dp).clip(RoundedCornerShape(14.dp)).background(LeafColors.Pale), contentAlignment = Alignment.Center) { thumbnail() }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                AdminBadge(reviewLabel(row.optString("review_status")), reviewColor(row.optString("review_status")))
                Text(catalog.get(row.getString("class_id")).name, fontWeight = FontWeight.SemiBold, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                Text("${percent(row.optDouble("confidence").toFloat())} de confiança", color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
                Text(row.label("email"), color = LeafColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = LeafColors.Muted)
        }
    }
}
@Composable internal fun AdminAuditRow(row: JSONObject, catalog: DiseaseCatalog) {
    var expanded by remember(row) { mutableStateOf(false) }
    val title = when (row.optString("action")) { "bootstrap" -> "Proprietário ativado"; "owner_migration" -> "Proprietário protegido"; "role" -> "Permissão alterada"; "pause" -> "Envios atualizados"; "review" -> "Revisão registrada"; "invite" -> "Convite criado"; "reinvite" -> "Convite reenviado"; "recovery" -> "Recuperação enviada"; "delete_begin" -> "Exclusão iniciada"; "delete_complete" -> "Exclusão concluída"; "delete_failed" -> "Exclusão pendente"; else -> "Ação administrativa" }
    Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(32.dp).background(LeafColors.Tint, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.History, null, Modifier.size(18.dp), tint = LeafColors.Green) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Text(adminDate(row.label("created_at")), color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
                val details = row.optJSONObject("details") ?: JSONObject()
                details.optJSONObject("after")?.let { after ->
                    Text(reviewLabel(after.optString("status")), color = LeafColors.Green, style = MaterialTheme.typography.bodySmall)
                    if (!after.isNull("class_id")) Text(catalog.get(after.getString("class_id")).name, style = MaterialTheme.typography.bodySmall)
                    after.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
                }
                if (row.optString("action") == "role" && details.has("role")) Text("Novo papel: ${roleLabel(details.optString("role"))}", style = MaterialTheme.typography.bodySmall)
                if (row.optString("action") == "pause" && details.has("enabled")) Text(if (details.optBoolean("enabled")) "Novos envios liberados" else "Envios pausados", style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 36.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (expanded) "Ocultar identificação" else "Ver identificação", fontSize = 12.sp) }
        if (expanded) SelectionContainer { Column(Modifier.padding(start = 44.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Responsável: ${shortId(row.label("actor_id"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            Text("Registro: ${shortId(row.label("target_id"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            row.optJSONObject("details")?.optJSONObject("before")?.let { before ->
                Text("Revisão anterior: ${reviewStatuses[before.optString("status")] ?: "Sem revisão"}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                if (!before.isNull("class_id")) Text(catalog.get(before.getString("class_id")).name, style = MaterialTheme.typography.bodySmall)
                before.optString("note").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted) }
                if (!before.isNull("reviewed_at")) Text(adminDate(before.getString("reviewed_at")), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                if (!before.isNull("reviewer_id")) Text("Revisor anterior: ${shortId(before.getString("reviewer_id"))}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            }
        } }
    }
}
@Composable internal fun Choice(label: String, value: String, options: Map<String, String>, enabled: Boolean = true, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, LeafColors.Border), contentPadding = PaddingValues(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, fontSize = 10.sp, color = LeafColors.Muted)
                Text(options[value] ?: value, color = LeafColors.Text, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            Icon(Icons.Outlined.ExpandMore, null, tint = LeafColors.Muted)
        }
        val menuShape = RoundedCornerShape(14.dp)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp).border(1.dp, LeafColors.Border, menuShape),
            shape = menuShape,
            containerColor = Color.White,
            tonalElevation = 0.dp
        ) {
            options.forEach { (key, name) ->
                val selected = key == value
                DropdownMenuItem(
                    text = {
                        Text(
                            name,
                            color = LeafColors.Text,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = { onChange(key); expanded = false },
                    modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (selected) LeafColors.Pale else Color.Transparent),
                    leadingIcon = if (selected) ({ Icon(Icons.Outlined.Check, null, tint = LeafColors.Green) }) else null
                )
            }
        }
    }
}
