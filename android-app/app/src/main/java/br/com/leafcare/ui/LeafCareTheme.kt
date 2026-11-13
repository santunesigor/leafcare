package br.com.leafcare.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import br.com.leafcare.R

object LeafColors {
    val Green = Color(0xFF2E7D32)
    val Text = Color(0xFF19211B)
    val Muted = Color(0xFF69736B)
    val Border = Color(0xFFDFE6DF)
    val Pale = Color(0xFFF7F9F7)
    /** Inner blocks inside white surfaces (HelpSheet tips, dialog options). */
    val Tint = Color(0xFFF3F7F2)
    val Danger = Color(0xFFB3261E)
    /** Review status "Duvidosa": amber readable on white and on its 9% tint. */
    val Warning = Color(0xFF99620A)
    /** Review status "Corrigida". */
    val Info = Color(0xFF3F6580)
    /** Admin hero surface and the text/icons on it. */
    val Forest = Color(0xFF194C2B)
    val OnForest = Color(0xFFD6E8D4)
}
/**
 * Every Material role is defined: an unset role falls back to Material's
 * baseline lilac palette, which leaked into dialogs, date pickers, menus,
 * sheets and chips.
 */
private val LeafColorScheme = lightColorScheme(
    primary = LeafColors.Green, onPrimary = Color.White,
    primaryContainer = Color(0xFFE3F1E4), onPrimaryContainer = Color(0xFF14411A),
    inversePrimary = Color(0xFF9AD49E),
    secondary = Color(0xFF3F5F45), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EFE6), onSecondaryContainer = LeafColors.Text,
    tertiary = Color(0xFF4E6B2F), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8F0DA), onTertiaryContainer = Color(0xFF1F2C10),
    background = Color.White, onBackground = LeafColors.Text,
    surface = Color.White, onSurface = LeafColors.Text,
    surfaceVariant = LeafColors.Pale, onSurfaceVariant = LeafColors.Muted,
    surfaceTint = Color.White,
    surfaceBright = Color.White, surfaceDim = Color(0xFFEEF2EE),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = LeafColors.Pale,
    inverseSurface = Color(0xFF2B332D), inverseOnSurface = Color(0xFFEEF3EE),
    outline = Color(0xFF8A968C), outlineVariant = LeafColors.Border,
    error = LeafColors.Danger, onError = Color.White,
    errorContainer = Color(0xFFFBE9E7), onErrorContainer = Color(0xFF5F1712),
)

@Composable
fun LeafCareTheme(content: @Composable () -> Unit) {
    val font = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold), Font(R.font.inter_extrabold, FontWeight.ExtraBold), Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic))
    fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight)
    MaterialTheme(colorScheme = LeafColorScheme, shapes = Shapes(extraLarge = RoundedCornerShape(24.dp)),
        typography = Typography(bodyLarge = style(15,24), bodyMedium = style(14,21), bodySmall = style(12,18), titleLarge = style(23,28), titleMedium = style(17,26), titleSmall = style(16,22,FontWeight.Bold), headlineMedium = style(32,36), headlineSmall = style(27,32), labelLarge = style(15,23), labelMedium = style(12,18,FontWeight.ExtraBold), labelSmall = style(11,17,FontWeight.ExtraBold)), content = content)
}
@Composable
fun FigmaIcon(asset: Int, description: String?, size: Int = 24) {
    Image(painterResource(asset), description, Modifier.size(size.dp))
}
@Composable
fun RoundIconButton(asset: Int, description: String, onClick: () -> Unit, enabled: Boolean = true, dark: Boolean = false) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Box(Modifier.size(40.dp).background(if (dark) Color(0x880D1B0F) else Color.White, CircleShape).border(1.dp, if (dark) Color.White.copy(alpha = .5f) else LeafColors.Border, CircleShape), contentAlignment = Alignment.Center) {
            FigmaIcon(asset, description)
        }
    }
}
@Composable
fun LeafButton(
    label: String,
    onClick: () -> Unit,
    secondary: Boolean = false,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
) {
    val container = when { secondary -> LeafColors.Pale; danger -> LeafColors.Danger; else -> LeafColors.Green }
    val content = when { !secondary -> Color.White; danger -> LeafColors.Danger; else -> LeafColors.Text }
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), contentPadding = contentPadding, border = if (secondary) BorderStroke(1.dp,LeafColors.Border) else null,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content, disabledContainerColor = LeafColors.Border, disabledContentColor = LeafColors.Muted)) { Text(label, fontWeight = FontWeight.Normal) }
}

/**
 * The app's dialog language: a compact white card (20dp corners, 20dp inner
 * padding), 18sp title, muted 14sp message and actions side by side — cancel
 * on the left, the main action on the right — falling back to a stack only
 * when a label does not fit (long text or enlarged fonts).
 *
 * Pass actions main-first: `LeafButton("Excluir"…); LeafButton("Cancelar"…)`.
 */
@Composable
fun LeafDialog(
    title: String,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissible: Boolean = true,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    LeafDialogWindow(onDismiss, dismissible) { LeafDialogContent(title, message, actions, content) }
}

@Composable
internal fun LeafDialogWindow(onDismiss: () -> Unit, dismissible: Boolean = true, content: @Composable () -> Unit) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        Surface(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).widthIn(max = 360.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.White, content = content)
    }
}

@Composable
fun LeafDialogContent(
    title: String,
    message: String? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, Modifier.semantics { heading() }, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, color = LeafColors.Text)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp, color = LeafColors.Muted) }
        }
        content()
        Box(Modifier.padding(top = 4.dp)) { LeafDialogActions(actions) }
    }
}

/**
 * Lays out dialog buttons in one row (reversed, so the first/main action sits
 * on the right) when every label fits its share; otherwise stacks them with
 * the main action on top.
 */
@Composable
fun LeafDialogActions(content: @Composable () -> Unit) {
    Layout(content) { measurables, constraints ->
        val width = constraints.maxWidth
        if (measurables.isEmpty()) return@Layout layout(width, 0) {}
        val gap = 10.dp.roundToPx()
        val count = measurables.size
        val slot = (width - gap * (count - 1)) / count
        val row = count > 1 && measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= slot }
        if (row) {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(slot)) }
            val height = placeables.maxOf { it.height }
            layout(width, height) {
                placeables.asReversed().forEachIndexed { index, p -> p.place(index * (slot + gap), (height - p.height) / 2) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints.fixedWidth(width)) }
            layout(width, placeables.sumOf { it.height } + gap * (count - 1)) {
                var y = 0
                placeables.forEach { it.place(0, y); y += it.height + gap }
            }
        }
    }
}

/** Blocking progress in the dialog language: spinner beside title and message. */
@Composable
fun LeafProgressDialog(title: String, message: String) {
    LeafDialogWindow(onDismiss = {}, dismissible = false) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(Modifier.size(28.dp), color = LeafColors.Green, trackColor = LeafColors.Border, strokeWidth = 3.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, Modifier.semantics { heading() }, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, color = LeafColors.Text)
                Text(message, style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
            }
        }
    }
}
