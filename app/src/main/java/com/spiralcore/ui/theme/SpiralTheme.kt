package com.spiralcore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Global SpiralCore palette
val BrandOrange = Color(0xFFE65100)
val AlertRed = Color(0xFFD32F2F)
val PureWhite = Color(0xFFFFFFFF)
val SecondaryGray = Color(0xFFF5F5F5)
val SoftBlack = Color(0xFF2B2B2B)
val MutedGray = Color(0xFF757575)
val OrangeTint = Color(0xFFFFE5D6)
val RedTint = Color(0xFFFFEBEE)
val ShellStroke = Color(0xFFB94B12)

// Backward-compatible semantic aliases used by existing UI components.
val Sand = PureWhite
val Paper = PureWhite
val DeepSea = BrandOrange
val DeepSeaDark = Color(0xFFB83F00)
val Coral = AlertRed
val Ink = SoftBlack
val MutedInk = MutedGray
val ShellLine = ShellStroke
val SoftMint = SecondaryGray
val SoftCoral = RedTint

private val SpiralColors = lightColorScheme(
    primary = BrandOrange,
    onPrimary = PureWhite,
    primaryContainer = OrangeTint,
    onPrimaryContainer = SoftBlack,
    secondary = AlertRed,
    onSecondary = PureWhite,
    secondaryContainer = RedTint,
    onSecondaryContainer = SoftBlack,
    background = PureWhite,
    onBackground = SoftBlack,
    surface = PureWhite,
    onSurface = SoftBlack,
    surfaceVariant = SecondaryGray,
    onSurfaceVariant = MutedGray,
    error = AlertRed,
    errorContainer = RedTint,
    onError = PureWhite,
    onErrorContainer = Color(0xFF8E1B1B),
    outline = Color(0xFFBDBDBD),
    outlineVariant = Color(0xFFE0E0E0),
)

@Composable
fun SpiralCoreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SpiralColors,
        content = content,
    )
}
