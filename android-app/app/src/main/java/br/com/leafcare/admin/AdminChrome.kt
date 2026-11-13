package br.com.leafcare.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.leafcare.ui.LeafColors

/**
 * One header for every admin screen: back arrow, title with a short context
 * line and up to two icon actions, 56dp tall. A thin progress line sits
 * under it while a request runs, without moving the content.
 */
@Composable internal fun AdminTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    backDescription: String,
    busy: Boolean,
    backEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 4.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = backEnabled) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, backDescription, tint = LeafColors.Text) }
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(title, Modifier.semantics { heading() }, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, color = LeafColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            actions()
        }
        Box(Modifier.fillMaxWidth().height(2.dp)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxSize(), color = LeafColors.Green, trackColor = LeafColors.Border)
        }
    }
}

/** Segmented tabs: one 40dp row, the selected section filled in green. */
@Composable internal fun AdminTabs(tabs: List<Pair<String, String>>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    val outer = RoundedCornerShape(14.dp)
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Color.White, outer).border(1.dp, LeafColors.Border, outer).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEach { (id, title) ->
            val isSelected = id == selected
            Box(Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                .background(if (isSelected) LeafColors.Green else Color.Transparent)
                .selectable(selected = isSelected, enabled = enabled, role = Role.Tab) { onSelect(id) }
                .padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
                Text(title, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else LeafColors.Text)
            }
        }
    }
}

/** Page controls only when there is another page to reach. */
@Composable internal fun AdminPager(page: Int, hasNext: Boolean, busy: Boolean, onPage: (Int) -> Unit) {
    if (page == 0 && !hasNext) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPage(-1) }, enabled = page > 0 && !busy) { Icon(Icons.Outlined.ChevronLeft, "Página anterior") }
        Text("Página ${page + 1}", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
        IconButton(onClick = { onPage(1) }, enabled = hasNext && !busy) { Icon(Icons.Outlined.ChevronRight, "Próxima página") }
    }
}

/** Section label with an optional trailing action, 40dp tall. */
@Composable internal fun AdminSectionHeader(title: String, action: String? = null, enabled: Boolean = true, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LeafColors.Text)
        action?.let { TextButton(onClick = onAction, enabled = enabled, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold) } }
    }
}

/** White grouped list: rows separated by hairlines instead of separate cards. */
@Composable internal fun AdminGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, LeafColors.Border), modifier = Modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** Number tile: value over label, optional second line, optionally tappable. */
@Composable internal fun AdminStatTile(value: String, label: String, modifier: Modifier, detail: String? = null, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).background(Color.White).border(1.dp, LeafColors.Border, shape)
        .then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, color = LeafColors.Text)
        Text(label, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LeafColors.Warning, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** Tappable row inside an [AdminGroup]: status dot, label, count and chevron. */
@Composable internal fun AdminCountRow(label: String, count: String, color: Color?, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, onClick = onClick).padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        color?.let { Box(Modifier.size(8.dp).background(it, CircleShape)) }
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = LeafColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(count, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LeafColors.Text)
        Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp), tint = LeafColors.Muted)
    }
}
