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
import androidx.compose.ui.res.stringResource
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
        Text(stringResource(R.string.help_title), fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.46).sp)
        Text(stringResource(R.string.help_subtitle), fontSize = 14.sp, lineHeight = 21.sp, color = LeafColors.Muted)
        Column(Modifier.fillMaxWidth().background(Color(0xFFF3F7F2), RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf(
                stringResource(R.string.help_tip_1),
                stringResource(R.string.help_tip_2),
                stringResource(R.string.help_tip_3),
                stringResource(R.string.help_tip_4)
            ).forEach { tip ->
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    FigmaIcon(R.drawable.v3_check, null, 16)
                    Text(tip, fontSize = 14.sp, lineHeight = 21.sp, color = Color(0xFF344036))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.help_examples_title), fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptureExample(R.drawable.v3_example_correct, stringResource(R.string.example_correct), true, Modifier.weight(1f))
                CaptureExample(R.drawable.v3_example_blur, stringResource(R.string.example_blur), false, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CaptureExample(R.drawable.v3_example_far, stringResource(R.string.example_far), false, Modifier.weight(1f))
                CaptureExample(R.drawable.v3_example_dark, stringResource(R.string.example_dark), false, Modifier.weight(1f))
            }
        }
        LeafButton(stringResource(R.string.help_button), onDismiss)
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