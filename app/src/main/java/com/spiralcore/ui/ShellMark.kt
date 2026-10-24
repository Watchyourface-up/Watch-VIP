package com.spiralcore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
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
        val r = side * 0.28f
        val rx = r * style.widthFactor
        val ry = r * style.heightFactor
        val sr = r * style.spiralFactor
        val center = Offset(cx, cy)
        val outlineWidth = side * 0.035f * style.strokeFactor

        val body = Path().apply {
            moveTo(cx - rx * 0.68f, cy + ry * 0.52f)
            cubicTo(cx - rx * 1.12f, cy + ry * 0.05f, cx - rx * 0.78f, cy - ry * 0.82f, cx, cy - ry * 0.84f)
            cubicTo(cx + rx * 0.79f, cy - ry * 0.86f, cx + rx * 1.05f, cy + ry * 0.01f, cx + rx * 0.61f, cy + ry * 0.66f)
            cubicTo(cx + rx * 0.25f, cy + ry * 1.02f, cx - rx * 0.22f, cy + ry * 0.93f, cx - rx * 0.68f, cy + ry * 0.52f)
            close()
        }
        val spiral = Path().apply {
            moveTo(cx + sr * 0.52f, cy + sr * 0.55f)
            cubicTo(cx + sr * 0.88f, cy + sr * 0.09f, cx + sr * 0.58f, cy - sr * 0.55f, cx + sr * 0.05f, cy - sr * 0.54f)
            cubicTo(cx - sr * 0.51f, cy - sr * 0.53f, cx - sr * 0.68f, cy + sr * 0.13f, cx - sr * 0.25f, cy + sr * 0.40f)
            cubicTo(cx + sr * 0.10f, cy + sr * 0.63f, cx + sr * 0.43f, cy + sr * 0.31f, cx + sr * 0.30f, cy + sr * 0.02f)
            cubicTo(cx + sr * 0.20f, cy - sr * 0.23f, cx - sr * 0.18f, cy - sr * 0.18f, cx - sr * 0.15f, cy + sr * 0.08f)
            cubicTo(cx - sr * 0.13f, cy + sr * 0.26f, cx + sr * 0.10f, cy + sr * 0.29f, cx + sr * 0.15f, cy + sr * 0.15f)
        }

        rotate(style.rotationDegrees, pivot = center) {
            scale(style.scale, pivot = center) {
                drawPath(body, color = innerColor, style = Fill)
                drawPath(body, color = shellColor, style = Stroke(width = outlineWidth, cap = StrokeCap.Round))
                drawPath(spiral, color = shellColor, style = Stroke(width = outlineWidth * 1.35f, cap = StrokeCap.Round))
                drawLine(
                    color = ShellLine.copy(alpha = 0.82f),
                    start = Offset(cx - rx * 0.61f, cy + ry * 0.50f),
                    end = Offset(cx - rx * 0.82f, cy + ry * 0.65f),
                    strokeWidth = outlineWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
