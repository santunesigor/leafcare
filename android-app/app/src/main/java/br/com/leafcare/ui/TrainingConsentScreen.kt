package br.com.leafcare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.leafcare.data.TRAINING_TERMS_VERSION
import br.com.leafcare.data.TRAINING_UPLOADS_ENABLED

@Composable
fun TrainingConsentScreen(vm: LeafCareViewModel, onBack: () -> Unit) {
    val consent by vm.trainingConsent.collectAsStateWithLifecycle()
    val consentRecorded = consent?.granted == true
    val granted = TRAINING_UPLOADS_ENABLED && consentRecorded && consent?.termsVersion == TRAINING_TERMS_VERSION
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = LeafColors.Green) }
            Text("Privacidade e fotos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LeafColors.Text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Uso de fotos no treinamento", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
            Text("Você decide se quer contribuir. A autorização é opcional e pode ser revogada nesta tela; recusar não altera a análise nem o histórico.", color = LeafColors.Text)
            Text("Política de privacidade", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
            Text("Somente fotos com sua autorização nesta tela e uma confirmação individual antes de cada análise poderão ser copiadas para uma área privada separada do histórico. Cada cópia será apagada em até 90 dias do envio ou antes, quando a autorização for revogada. Exportações locais de treinamento serão apagadas em até 30 dias.", color = LeafColors.Text)
            Text("A classe prevista pelo aplicativo não é um diagnóstico validado. Uma foto autorizada só poderá entrar em um conjunto de treinamento após revisão humana e validação de qualidade. Identificadores da conta e metadados EXIF serão removidos na exportação.", color = LeafColors.Text)
            Text("A análise local e o histórico continuam disponíveis offline após autenticação. Futuras cópias de treinamento poderão ser revogadas e excluídas, mas isso não reverte automaticamente pesos de modelos já treinados.", color = LeafColors.Text)
            PrivacyContactCard()
            Surface(shape = RoundedCornerShape(16.dp), color = LeafColors.Pale, border = BorderStroke(1.dp, if (granted) LeafColors.Green else LeafColors.Border)) {
                // The whole block toggles; the label reads as an active choice, not as disabled text.
                Row(
                    Modifier.fillMaxWidth()
                        .toggleable(value = granted, enabled = TRAINING_UPLOADS_ENABLED, role = Role.Checkbox, onValueChange = { vm.setTrainingConsent(it) })
                        .padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp)
                        .semantics {
                            contentDescription = "Consentimento global opcional para uso de fotos no treinamento"
                            stateDescription = if (TRAINING_UPLOADS_ENABLED) {
                                if (granted) "Autorizado" else "Não autorizado"
                            } else "Não autorizado"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = granted,
                        enabled = TRAINING_UPLOADS_ENABLED,
                        onCheckedChange = null,
                        modifier = Modifier.padding(10.dp),
                        colors = CheckboxDefaults.colors(checkedColor = LeafColors.Green, uncheckedColor = LeafColors.Muted, checkmarkColor = Color.White),
                    )
                    Text("Autorizo o uso opcional das fotos", modifier = Modifier.weight(1f), color = LeafColors.Text, style = MaterialTheme.typography.bodyMedium)
                    Text(if (consent?.syncState == "SYNCED") "Sincronizado" else "Pendente", color = if (consent?.syncState == "SYNCED") LeafColors.Green else LeafColors.Muted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, LeafColors.Border, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            LeafButton("Revogar consentimento", { vm.setTrainingConsent(false) }, secondary = true, danger = true, enabled = consentRecorded)
        }
    }
}
