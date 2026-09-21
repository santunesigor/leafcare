package br.com.leafcare.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.leafcare.R

@Composable
fun HelpSheet(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.padding(15.dp).widthIn(max = 390.dp).fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = Color.White) { HelpContent(onDismiss) }
    }
}
@Composable
fun HelpContent(onDismiss: () -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Como tirar a foto", fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.46).sp)
        Text("Uma boa imagem ajuda o LeafCare a comparar os sintomas.", fontSize = 14.sp, lineHeight = 21.sp, color = LeafColors.Muted)
        Column(Modifier.fillMaxWidth().background(Color(0xFFF3F7F2), RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Fotografe uma folha por vez.", "Use boa iluminação.", "Deixe a mancha visível e em foco.", "Evite sombras e fundos confusos.").forEach {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    FigmaIcon(R.drawable.v3_check, null, 16)
                    Text(it, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFF344036))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Exemplos", fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptureExample(R.drawable.v3_example_correct, "Correta", true, Modifier.weight(1f))
                CaptureExample(R.drawable.v3_example_blur, "Desfocada", false, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptureExample(R.drawable.v3_example_far, "Distante", false, Modifier.weight(1f))
                CaptureExample(R.drawable.v3_example_dark, "Pouca luz", false, Modifier.weight(1f))
            }
        }
        LeafButton("Entendi", onDismiss)
    }
}
@Composable
private fun CaptureExample(image: Int, label: String, correct: Boolean, modifier: Modifier) {
    val color = if (correct) LeafColors.Green else Color(0xFFD35B52)
    Box(modifier.aspectRatio(167f / 108f).clip(RoundedCornerShape(13.dp)).border(2.dp, color, RoundedCornerShape(13.dp))) {
        Image(painterResource(image), "Exemplo: $label", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text((if (correct) "✓ " else "× ") + label,
            Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.White.copy(alpha = .94f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 4.dp),
            color = if (correct) Color(0xFF226526) else Color(0xFF9A312B), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    }
}
