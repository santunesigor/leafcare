package br.com.leafcare.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Contato de privacidade", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, color = LeafColors.Text)
                IconButton(onClick = {
                    clipboardManager.setText(AnnotatedString(br.com.leafcare.auth.PRIVACY_CONTACT_EMAIL))
                    copied = true
                }) {
                    Icon(
                        if (copied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = if (copied) "E-mail copiado" else "Copiar e-mail de privacidade",
                        tint = LeafColors.Green
                    )
                }
            }
            Text(
                br.com.leafcare.auth.PRIVACY_CONTACT_EMAIL,
                style = MaterialTheme.typography.bodyMedium,
                color = LeafColors.Text,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
