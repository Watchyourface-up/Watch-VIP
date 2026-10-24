package com.spiralcore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spiralcore.domain.ShellStyles
import com.spiralcore.ui.theme.Coral
import com.spiralcore.ui.theme.DeepSea
import com.spiralcore.ui.theme.DeepSeaDark
import com.spiralcore.ui.theme.ShellLine
import com.spiralcore.ui.theme.SoftCoral
import com.spiralcore.ui.theme.SoftMint
import androidx.compose.ui.graphics.drawscope.Fill

@Composable
fun ShellMark(seed: Int, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val palettes = listOf(
        DeepSea to SoftMint,
        Coral to SoftCoral,
        Color(0xFF719083) to Color(0xFFE6EEE5),
        Color(0xFFC39A62) to Color(0xFFF2E8D5),
        Color(0xFF7186A1) to Color(0xFFE3E8F0),
        DeepSeaDark to Color(0xFFDCE9E4),
    )
    val style = ShellStyles.fromSeed(seed)
    val (shellColor, innerColor) = palettes[style.paletteIndex]

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "قوقعة رابط فريدة" },
    ) {
        val side = minOf(this.size.width, this.size.height)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val r = side * 0.38f
        val center = Offset(cx, cy)

        val body = Path().apply {
            moveTo(cx - r * 0.68f, cy + r * 0.52f)
            cubicTo(cx - r * 1.12f, cy + r * 0.05f, cx - r * 0.78f, cy - r * 0.82f, cx, cy - r * 0.84f)
            cubicTo(cx + r * 0.79f, cy - r * 0.86f, cx + r * 1.05f, cy + r * 0.01f, cx + r * 0.61f, cy + r * 0.66f)
            cubicTo(cx + r * 0.25f, cy + r * 1.02f, cx - r * 0.22f, cy + r * 0.93f, cx - r * 0.68f, cy + r * 0.52f)
            close()
        }
        val spiral = Path().apply {
            moveTo(cx + r * 0.52f, cy + r * 0.55f)
            cubicTo(cx + r * 0.88f, cy + r * 0.09f, cx + r * 0.58f, cy - r * 0.55f, cx + r * 0.05f, cy - r * 0.54f)
            cubicTo(cx - r * 0.51f, cy - r * 0.53f, cx - r * 0.68f, cy + r * 0.13f, cx - r * 0.25f, cy + r * 0.40f)
            cubicTo(cx + r * 0.10f, cy + r * 0.63f, cx + r * 0.43f, cy + r * 0.31f, cx + r * 0.30f, cy + r * 0.02f)
            cubicTo(cx + r * 0.20f, cy - r * 0.23f, cx - r * 0.18f, cy - r * 0.18f, cx - r * 0.15f, cy + r * 0.08f)
            cubicTo(cx - r * 0.13f, cy + r * 0.26f, cx + r * 0.10f, cy + r * 0.29f, cx + r * 0.15f, cy + r * 0.15f)
        }

        rotate(style.rotationDegrees, pivot = center) {
            scale(style.scale, pivot = center) {
                drawPath(body, color = innerColor, style = Fill)
                drawPath(body, color = shellColor, style = Stroke(width = side * 0.045f, cap = StrokeCap.Round))
                drawPath(spiral, color = shellColor, style = Stroke(width = side * 0.055f, cap = StrokeCap.Round))
                drawLine(
                    color = ShellLine.copy(alpha = 0.82f),
                    start = Offset(cx - r * 0.61f, cy + r * 0.50f),
                    end = Offset(cx - r * 0.82f, cy + r * 0.65f),
                    strokeWidth = side * 0.035f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
