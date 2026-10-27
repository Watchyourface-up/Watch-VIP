package com.spiralcore.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spiralcore.domain.ShellStyles
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class ShellPalette(
    val depth: Color,
    val pearl: Color,
    val groove: Color,
    val core: Color,
)

private val ShellPalettes = listOf(
    ShellPalette(Color(0xFF16344A), Color(0xFFF4F8FA), Color(0xFF2EC4B6), Color(0xFFD4B46A)),
    ShellPalette(Color(0xFF3A3220), Color(0xFFF8F1DE), Color(0xFFD4B46A), Color(0xFF2EC4B6)),
    ShellPalette(Color(0xFF1C3A50), Color(0xFFF7FBFC), Color(0xFF7EDCD4), Color(0xFFE4C77A)),
    ShellPalette(Color(0xFF0E3A40), Color(0xFFE7F7F5), Color(0xFFD4B46A), Color(0xFF2EC4B6)),
    ShellPalette(Color(0xFF243848), Color(0xFFE6EDF2), Color(0xFFC6A15B), Color(0xFF5EE0D6)),
    ShellPalette(Color(0xFF12323A), Color(0xFFF3F7F8), Color(0xFF1AA6A0), Color(0xFFE8D7A4)),
)

@Composable
fun ShellMark(seed: Int, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val style = ShellStyles.fromSeed(seed)
    val palette = ShellPalettes[style.paletteIndex]

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "قوقعة رابط فريدة" },
    ) {
        val side = minOf(this.size.width, this.size.height)
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val rx = side * 0.34f * style.widthFactor
        val ry = side * 0.31f * style.heightFactor
        val center = Offset(cx, cy)
        val turns = 1.85f + (style.spiralFactor - 0.78f) * 0.85f
        val grooveWidth = side * 0.058f * style.strokeFactor

        scale(style.scale, pivot = center) {
            drawOval(
                color = Color.Black.copy(alpha = 0.30f),
                topLeft = Offset(cx - rx * 0.46f, cy + ry * 0.72f),
                size = Size(rx * 1.05f, ry * 0.22f),
            )
            rotate(style.rotationDegrees, pivot = center) {
                val bodyPts = spiralPoints(cx, cy, rx, ry, turns, -0.62f, 64, 0.06f, 1f)
                val body = pathOf(bodyPts, Offset(cx + rx * 0.04f, cy + ry * 0.02f))
                val groove = pathOf(
                    spiralPoints(cx, cy, rx * 0.78f, ry * 0.78f, turns * 0.92f, -0.55f, 48, 0.12f, 0.94f),
                )
                val core = pathOf(
                    spiralPoints(cx, cy, rx * 0.44f, ry * 0.44f, turns * 0.62f, -0.40f, 32, 0.16f, 0.92f),
                )
                drawPath(
                    body,
                    brush = Brush.radialGradient(
                        colors = listOf(palette.pearl, palette.depth),
                        center = Offset(cx - rx * 0.28f, cy - ry * 0.34f),
                        radius = maxOf(rx, ry) * 1.7f,
                    ),
                )
                drawPath(
                    body,
                    color = Color(0xFFD5DEE6),
                    style = Stroke(width = grooveWidth * 0.28f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                drawPath(
                    groove,
                    color = palette.groove,
                    style = Stroke(width = grooveWidth, cap = StrokeCap.Round),
                )
                drawPath(
                    core,
                    color = palette.core,
                    style = Stroke(width = grooveWidth * 0.56f, cap = StrokeCap.Round),
                )
                drawPath(
                    pathOf(bodyPts.takeLast(14)),
                    color = Color.White.copy(alpha = 0.82f),
                    style = Stroke(width = grooveWidth * 0.34f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

private fun spiralPoints(
    cx: Float,
    cy: Float,
    rx: Float,
    ry: Float,
    turns: Float,
    start: Float,
    steps: Int,
    r0: Float,
    r1: Float,
): List<Offset> {
    val points = ArrayList<Offset>(steps + 1)
    for (i in 0..steps) {
        val t = i / steps.toFloat()
        val angle = (start + t * turns) * (2.0 * PI)
        val radius = r0 + (r1 - r0) * t
        points += Offset(
            cx + cos(angle).toFloat() * rx * radius,
            cy + sin(angle).toFloat() * ry * radius,
        )
    }
    return points
}

private fun pathOf(points: List<Offset>, closeTo: Offset? = null): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (index in 1 until points.size) {
        lineTo(points[index].x, points[index].y)
    }
    if (closeTo != null) {
        lineTo(closeTo.x, closeTo.y)
        close()
    }
}
