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
    fun styleUsesSupportedPaletteAndScale() {
        val style = ShellStyles.fromSeed(-173)
        assertTrue(style.paletteIndex in 0..5)
        assertTrue(style.scale in 0.9f..1.1f)
    }

    @Test
    fun differentSeedsCanProduceDifferentShells() {
        assertTrue(ShellStyles.fromSeed(1) != ShellStyles.fromSeed(2))
    }
}
