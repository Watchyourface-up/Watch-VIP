package com.spiralcore.domain

/** Stable visual variation stored with each database row. */
data class ShellStyle(
    val paletteIndex: Int,
    val rotationDegrees: Float,
    val scale: Float,
)

object ShellStyles {
    private val rotations = listOf(-9f, -4f, 3f, 8f, 13f, -13f, 5f)
    private val scales = listOf(0.92f, 1.0f, 1.06f, 0.97f, 1.03f)

    fun fromSeed(seed: Int): ShellStyle {
        val value = seed.toLong() and 0xffff_ffffL
        return ShellStyle(
            paletteIndex = (value % 6).toInt(),
            rotationDegrees = rotations[((value ushr 8) % rotations.size).toInt()],
            scale = scales[((value ushr 16) % scales.size).toInt()],
        )
    }
}
