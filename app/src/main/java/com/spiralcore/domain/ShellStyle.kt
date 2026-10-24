package com.spiralcore.domain

/** Stable, per-link visual parameters derived from the persisted shell seed. */
data class ShellStyle(
    val paletteIndex: Int,
    val rotationDegrees: Float,
    val scale: Float,
    val widthFactor: Float,
    val heightFactor: Float,
    val spiralFactor: Float,
    val strokeFactor: Float,
)

object ShellStyles {
    fun fromSeed(seed: Int): ShellStyle {
        val palette = unsigned(mix32(seed xor 0x163B))
        val rotation = unsigned(mix32(seed xor 0x57A1))
        val scale = unsigned(mix32(seed xor 0x29C7))
        val width = unsigned(mix32(seed xor 0x4F31))
        val height = unsigned(mix32(seed xor 0x71D9))
        val spiral = unsigned(mix32(seed xor 0x32A5))
        val stroke = unsigned(mix32(seed xor 0x6B17))

        return ShellStyle(
            paletteIndex = (palette % 6L).toInt(),
            rotationDegrees = (rotation % 36_000L).toFloat() / 100f,
            scale = 0.88f + (scale % 25L).toFloat() / 100f,
            widthFactor = 0.88f + (width % 25L).toFloat() / 100f,
            heightFactor = 0.88f + (height % 25L).toFloat() / 100f,
            spiralFactor = 0.78f + (spiral % 41L).toFloat() / 100f,
            strokeFactor = 0.88f + (stroke % 25L).toFloat() / 100f,
        )
    }

    private fun unsigned(value: Int): Long = value.toLong() and 0xffff_ffffL

    private fun mix32(input: Int): Int {
        var value = input
        value = (value xor (value ushr 16)) * 0x45d9f3b
        value = (value xor (value ushr 16)) * 0x45d9f3b
        return value xor (value ushr 16)
    }
}
