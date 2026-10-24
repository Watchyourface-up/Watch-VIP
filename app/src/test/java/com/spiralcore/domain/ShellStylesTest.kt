package com.spiralcore.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellStylesTest {
    @Test
    fun styleIsStableForPersistedSeed() {
        assertEquals(ShellStyles.fromSeed(42), ShellStyles.fromSeed(42))
    }

    @Test
    fun styleUsesSupportedPaletteAndBoundedDrawingValues() {
        val style = ShellStyles.fromSeed(-173)
        assertTrue(style.paletteIndex in 0..5)
        assertTrue(style.rotationDegrees >= 0f && style.rotationDegrees < 360f)
        assertTrue(style.scale in 0.88f..1.12f)
        assertTrue(style.widthFactor in 0.88f..1.12f)
        assertTrue(style.heightFactor in 0.88f..1.12f)
        assertTrue(style.spiralFactor in 0.78f..1.18f)
    }

    @Test
    fun seedsSeparatedByOldStylePeriodProduceDifferentShells() {
        assertTrue(ShellStyles.fromSeed(1) != ShellStyles.fromSeed(211))
    }
}
