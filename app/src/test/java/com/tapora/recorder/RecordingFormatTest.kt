package com.tapora.recorder

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingFormatTest {
    @Test fun durationFormatting() {
        assertEquals("00:00", formatDuration(0))
        assertEquals("01:05", formatDuration(65_000))
        assertEquals("61:01", formatDuration(3_661_000))
    }
}
