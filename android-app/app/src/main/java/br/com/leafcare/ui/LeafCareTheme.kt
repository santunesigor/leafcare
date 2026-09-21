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
}
@Composable
fun LeafCareTheme(content: @Composable () -> Unit) {
    val font = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold), Font(R.font.inter_extrabold, FontWeight.ExtraBold), Font(R.font.inter_semibold, FontWeight.SemiBold), Font(R.font.inter_italic, FontWeight.Normal, FontStyle.Italic))
    fun style(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = font, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight)
    MaterialTheme(colorScheme = lightColorScheme(primary = LeafColors.Green, onPrimary = Color.White, background = Color.White, surface = Color.White, onSurface = LeafColors.Text, onSurfaceVariant = LeafColors.Muted, surfaceVariant = LeafColors.Pale, outlineVariant = LeafColors.Border),
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
fun LeafButton(label: String, onClick: () -> Unit, secondary: Boolean = false) {
    Button(onClick, Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), border = if (secondary) BorderStroke(1.dp,LeafColors.Border) else null,
        colors = ButtonDefaults.buttonColors(containerColor = if (secondary) LeafColors.Pale else LeafColors.Green, contentColor = if (secondary) LeafColors.Text else Color.White)) { Text(label, fontWeight = FontWeight.Normal) }
}
