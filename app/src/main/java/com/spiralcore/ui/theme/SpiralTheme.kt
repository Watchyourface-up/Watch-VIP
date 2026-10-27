package com.spiralcore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Sand = Color(0xFF0B1422)
val Paper = Color(0xFF162232)
val DeepSea = Color(0xFF2EC4B6)
val DeepSeaDark = Color(0xFFB7F3EC)
val Coral = Color(0xFFD4B46A)
val Ink = Color(0xFFE8EEF4)
val MutedInk = Color(0xFF93A3B3)
val ShellLine = Color(0xFFE4C77A)
val SoftMint = Color(0xFF12383A)
val SoftCoral = Color(0xFF3A3220)

private val NavyInk = Color(0xFF042226)

private val SpiralColors = darkColorScheme(
    primary = DeepSea,
    onPrimary = NavyInk,
    primaryContainer = Color(0xFF134E4A),
    onPrimaryContainer = Color(0xFFD5F7F3),
    secondary = Coral,
    onSecondary = Color(0xFF2A220F),
    secondaryContainer = SoftCoral,
    onSecondaryContainer = Color(0xFFF6E7C4),
    background = Sand,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFF1C2C3E),
    onSurfaceVariant = MutedInk,
    outline = Color(0xFF3E5366),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun SpiralCoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpiralColors,
        content = content,
    )
}
