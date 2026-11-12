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
import br.com.leafcare.ui.LeafButton
import br.com.leafcare.ui.LeafColors
import org.json.JSONObject

/** Counts come from the full remote overview, never from the current 20-photo batch. */
internal fun LazyListScope.triageDashboard(data: JSONObject, catalog: DiseaseCatalog, busy: Boolean, evaluate: () -> Unit, open: (JSONObject) -> Unit) {
    val total = data.optInt("photos").coerceAtLeast(0)
    val reviewed = data.optInt("reviewed").coerceIn(0, total)
    val pending = total - reviewed
    val progress = if (total == 0) 0f else reviewed.toFloat() / total
    item {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, LeafColors.Border)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("$pending", fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = LeafColors.Text)
                        Text(pendingLabel(total, pending), style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                    }
                    Text("${(progress * 100).toInt()}%", color = LeafColors.Green, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = LeafColors.Green, trackColor = LeafColors.Pale, drawStopIndicator = {})
                Text("$reviewed de $total fotos revisadas", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
                LeafButton(if (pending == 0) "Nenhuma pendência" else "Avaliar pendentes", evaluate, enabled = !busy && pending > 0)
            }
        }
    }
    item {
        Column {
            AdminSectionHeader("Consultar avaliações", "Ver todas", !busy) { open(JSONObject().put("status", "")) }
            ReviewStatusGroup(data.optJSONObject("statuses"), busy, open)
        }
    }
    val counts = data.optJSONObject("classes") ?: JSONObject()
    val keys = counts.keys().asSequence().toList().sortedByDescending { counts.optInt(it) }
    if (keys.isNotEmpty()) item {
        Column {
            AdminSectionHeader("Fotos por previsão")
            AdminGroup {
                keys.forEachIndexed { index, id ->
                    if (index > 0) HorizontalDivider(color = LeafColors.Border)
                    AdminCountRow(catalog.get(id).name, "${counts.optInt(id)}", null, !busy) { open(JSONObject().put("status", "").put("class_id", id)) }
                }
            }
        }
    }
}
