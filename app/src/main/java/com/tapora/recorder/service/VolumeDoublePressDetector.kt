package com.tapora.recorder.service

/** Deterministic key-event recognizer. PASS leaves ordinary system volume behavior untouched. */
internal class VolumeDoublePressDetector(
    private val targetKeyCode: Int,
    private val doublePressWindowMs: Long = 420L
) {
    enum class Result { PASS, TRIGGER_AND_CONSUME, CONSUME }

    private var firstDownAt = Long.MIN_VALUE
    private var consumeFollowingUp = false

    fun onEvent(keyCode: Int, action: Int, repeatCount: Int, eventTime: Long): Result {
        if (keyCode != targetKeyCode) {
            if (action == ACTION_DOWN && repeatCount == 0) firstDownAt = Long.MIN_VALUE
            return Result.PASS
        }
        if (action == ACTION_UP && consumeFollowingUp) {
            consumeFollowingUp = false
            return Result.CONSUME
        }
        if (action != ACTION_DOWN) return Result.PASS
        if (repeatCount > 0) {
            firstDownAt = Long.MIN_VALUE
            return Result.PASS
        }
        val previous = firstDownAt
        if (previous != Long.MIN_VALUE && eventTime >= previous && eventTime - previous <= doublePressWindowMs) {
            firstDownAt = Long.MIN_VALUE
            consumeFollowingUp = true
            return Result.TRIGGER_AND_CONSUME
        }
        firstDownAt = eventTime
        return Result.PASS
    }

    fun reset() {
        firstDownAt = Long.MIN_VALUE
        consumeFollowingUp = false
    }

    companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
    }
}
