package com.spiralcore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sand = Color(0xFFF5F1E8)
val Paper = Color(0xFFFFFCF6)
val DeepSea = Color(0xFF23695C)
val DeepSeaDark = Color(0xFF174A42)
val Coral = Color(0xFFCE8066)
val Ink = Color(0xFF24332F)
val MutedInk = Color(0xFF6C7771)
val ShellLine = Color(0xFFB88768)
val SoftMint = Color(0xFFE1EEE8)
val SoftCoral = Color(0xFFF5E3DA)

private val SpiralColors = lightColorScheme(
    primary = DeepSea,
    onPrimary = Color.White,
    secondary = Coral,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = SoftMint,
    onSurfaceVariant = MutedInk,
    error = Color(0xFFB3261E),
)

@Composable
fun SpiralCoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpiralColors,
        content = content,
    )
}
