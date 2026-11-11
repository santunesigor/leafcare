package br.com.leafcare.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
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
) {
    val container = when { secondary -> LeafColors.Pale; danger -> LeafColors.Danger; else -> LeafColors.Green }
    val content = when { !secondary -> Color.White; danger -> LeafColors.Danger; else -> LeafColors.Text }
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), border = if (secondary) BorderStroke(1.dp,LeafColors.Border) else null,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content, disabledContainerColor = LeafColors.Border, disabledContentColor = LeafColors.Muted)) { Text(label, fontWeight = FontWeight.Normal) }
}

/**
 * The app's dialog language (same as HelpSheet): white surface, 24dp corners,
 * title in the help-title style, muted message and full-width LeafButtons.
 */
@Composable
fun LeafDialog(
    title: String,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissible: Boolean = true,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
        ),
    ) {
        Surface(Modifier.padding(15.dp).widthIn(max = 390.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) {
            LeafDialogContent(title, message, actions, content)
        }
    }
}

@Composable
fun LeafDialogContent(
    title: String,
    message: String? = null,
    actions: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.46).sp, color = LeafColors.Text)
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = LeafColors.Muted) }
        }
        content()
        Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}
