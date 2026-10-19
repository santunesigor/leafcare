package br.com.leafcare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PrivacyContactCard(modifier: Modifier = Modifier) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = LeafColors.Pale),
        border = BorderStroke(1.dp, LeafColors.Border),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Contato de privacidade", style = MaterialTheme.typography.labelSmall, color = LeafColors.Muted)
                Text(
                    br.com.leafcare.auth.PRIVACY_CONTACT_EMAIL,
                    style = MaterialTheme.typography.bodyMedium,
                    color = LeafColors.Text,
                    fontWeight = FontWeight.SemiBold
                )
            }
            TextButton(onClick = {
                clipboardManager.setText(AnnotatedString(br.com.leafcare.auth.PRIVACY_CONTACT_EMAIL))
                copied = true
            }) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = null, tint = LeafColors.Green)
                Text(if (copied) "Copiado" else "Copiar", color = LeafColors.Green)
            }
        }
    }
}
