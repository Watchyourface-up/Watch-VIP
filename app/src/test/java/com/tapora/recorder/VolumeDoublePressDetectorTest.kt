package com.tapora.recorder

import android.view.KeyEvent
import com.tapora.recorder.service.VolumeDoublePressDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeDoublePressDetectorTest {
    private fun detector() = VolumeDoublePressDetector(KeyEvent.KEYCODE_VOLUME_UP, 420)

    @Test fun singlePressPassesThrough() {
        val d = detector()
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1000))
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 1080))
    }

    @Test fun doublePressTriggersAndConsumesOnlySecondRelease() {
        val d = detector()
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1000))
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 1060))
        assertEquals(VolumeDoublePressDetector.Result.TRIGGER_AND_CONSUME, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1250))
        assertEquals(VolumeDoublePressDetector.Result.CONSUME, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_UP, 0, 1310))
    }

    @Test fun lateSecondPressIsPassedAndStartsNewWindow() {
        val d = detector()
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1000))
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1500))
    }

    @Test fun otherKeysAreNeverConsumed() {
        val d = detector()
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.ACTION_DOWN, 0, 1000))
    }

    @Test fun anotherKeyBreaksTheConsecutiveSequence() {
        val d = detector()
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1000))
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_POWER, KeyEvent.ACTION_DOWN, 0, 1100))
        assertEquals(VolumeDoublePressDetector.Result.PASS, d.onEvent(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1200))
    }
}
