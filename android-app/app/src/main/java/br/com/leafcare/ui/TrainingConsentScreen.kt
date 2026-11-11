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
import br.com.leafcare.auth.TERMS_OF_USE_VERSION
import br.com.leafcare.data.TRAINING_TERMS_VERSION
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import br.com.leafcare.data.TRAINING_UPLOADS_ENABLED

@Composable
fun TrainingConsentScreen(vm: LeafCareViewModel, onBack: () -> Unit, onRevoked: () -> Unit) {
    val consent by vm.trainingConsent.collectAsStateWithLifecycle()
    val granted = TRAINING_UPLOADS_ENABLED && consent?.granted == true && consent?.termsVersion == TRAINING_TERMS_VERSION
    var confirmRevoke by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = LeafColors.Green) }
            Text("Privacidade e fotos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = LeafColors.Text)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(shape = RoundedCornerShape(16.dp), color = LeafColors.Pale, border = BorderStroke(1.dp, if (granted) LeafColors.Green else LeafColors.Border)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(if (granted) "Fotos autorizadas para treinamento" else "Autorização revogada", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = LeafColors.Text)
                        Text("Concedida ao aceitar os Termos e a Política v$TERMS_OF_USE_VERSION.", style = MaterialTheme.typography.bodySmall, color = LeafColors.Muted)
                    }
                    Text(if (consent?.syncState == "SYNCED") "Sincronizado" else "Pendente", color = if (consent?.syncState == "SYNCED") LeafColors.Green else LeafColors.Muted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, LeafColors.Border, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
            Text("Como suas fotos são usadas", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
            Text("Usar o LeafCare inclui autorizar o uso das fotos que você analisa para treinar o modelo de IA. Cada foto analisada é copiada para uma área privada, separada do histórico, e só entra em um conjunto de treinamento após revisão humana e validação de qualidade. Identificadores da conta e metadados EXIF são removidos na exportação.", color = LeafColors.Text)
            Text("Prazos", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
            Text("Cada cópia é apagada em até 90 dias do envio, ou antes, se você revogar. Exportações locais de treinamento são apagadas em até 30 dias.", color = LeafColors.Text)
            Text("Revogação", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium, color = LeafColors.Green, fontWeight = FontWeight.SemiBold)
            Text("Ao revogar, novos envios param e a exclusão das cópias sob controle do projeto é iniciada assim que houver conexão. Como esse uso é condição para usar o app, o LeafCare só volta a funcionar com um novo aceite. Pesos de modelos já treinados não são desfeitos automaticamente.", color = LeafColors.Text)
            PrivacyContactCard()
        }
        Box(Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
            LeafButton("Revogar autorização", { confirmRevoke = true }, secondary = true, danger = true, enabled = granted)
        }
    }
    if (confirmRevoke) LeafDialog(
        title = "Revogar autorização?",
        onDismiss = { confirmRevoke = false },
        message = "Novos envios param e as cópias de treinamento serão excluídas. O LeafCare só poderá ser usado novamente depois de aceitar os termos outra vez.",
        actions = {
            LeafButton("Revogar e sair do app", { confirmRevoke = false; vm.revokeTrainingConsent(onRevoked) }, danger = true)
            LeafButton("Cancelar", { confirmRevoke = false }, secondary = true)
        },
    )
}
