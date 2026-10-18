package com.tapora.recorder.service

/**
 * Platform-independent detector. It only emits an intent after two UP events
 * inside the configured interval; it never starts recording itself.
 */
class VolumeDoublePressDetector(
    private val intervalMs: Long = 450L,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var lastUpAt: Long? = null

    fun onVolumeUp(): Boolean {
        val now = clock()
        val previous = lastUpAt
        lastUpAt = now
        return previous != null && now - previous in 1..intervalMs
    }

    fun reset() { lastUpAt = null }
}
