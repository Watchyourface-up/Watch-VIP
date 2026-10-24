package com.spiralcore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spiralcore.ui.theme.DeepSea
import com.spiralcore.ui.theme.MutedInk

@Composable
fun LockScreen(
    errorMessage: String?,
    onBiometric: () -> Unit,
    onDevicePin: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ShellMark(seed = 0x5A17, size = 112.dp)
        Spacer(Modifier.height(22.dp))
        Text(
            text = "SpiralCore",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DeepSea,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "روابطك.",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "كل رابط له قوقعته الخاصة.",
            style = MaterialTheme.typography.bodyLarge,
            color = MutedInk,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onBiometric,
            modifier = Modifier.size(width = 280.dp, height = 54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepSea),
        ) {
            Icon(Icons.Outlined.Fingerprint, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("فتح بالبصمة")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDevicePin,
            modifier = Modifier.size(width = 280.dp, height = 52.dp),
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null)
            Spacer(Modifier.size(10.dp))
            Text("استخدام PIN الهاتف")
        }
        if (!errorMessage.isNullOrBlank()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "تبقى روابطك على هذا الجهاز.",
            style = MaterialTheme.typography.labelMedium,
            color = MutedInk,
        )
    }
}
