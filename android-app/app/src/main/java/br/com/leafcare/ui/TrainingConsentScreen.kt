package br.com.leafcare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
            Surface(shape = RoundedCornerShape(16.dp), color = LeafColors.Pale, border = BorderStroke(1.dp, LeafColors.Border)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = granted,
                        enabled = TRAINING_UPLOADS_ENABLED,
                        onCheckedChange = { vm.setTrainingConsent(it) },
                        modifier = Modifier.semantics {
                            contentDescription = "Consentimento global opcional para uso de fotos no treinamento"
                            stateDescription = if (TRAINING_UPLOADS_ENABLED) {
                                if (granted) "Autorizado" else "Não autorizado"
                            } else "Não autorizado"
                        },
                        colors = CheckboxDefaults.colors(checkedColor = LeafColors.Green),
                    )
                    Text("Autorizo o uso opcional das fotos", modifier = Modifier.weight(1f), color = LeafColors.Muted)
                    Text(if (consent?.syncState == "SYNCED") "Sincronizado" else "Pendente", color = LeafColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        TextButton(onClick = { vm.setTrainingConsent(false) }, enabled = consentRecorded, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text("Revogar consentimento", color = MaterialTheme.colorScheme.error)
        }
    }
}
