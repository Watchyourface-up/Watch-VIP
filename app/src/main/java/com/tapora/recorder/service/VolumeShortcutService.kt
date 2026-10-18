package com.tapora.recorder.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/** Opt-in global key shortcut. It never reads window contents or accessibility-node text. */
class VolumeShortcutService : AccessibilityService() {
    private val detector = VolumeDoublePressDetector(KeyEvent.KEYCODE_VOLUME_UP)

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        }
        Log.i(TAG, "accessibility_connected filterKeyEvents=${serviceInfo.flags and AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS != 0} enabled=${isShortcutEnabled()}")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { Log.w(TAG, "accessibility_interrupted") }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val enabled = isShortcutEnabled()
        if (!enabled) {
            detector.reset()
            return false
        }
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val pm = getSystemService(PowerManager::class.java)
            val interactive = if (Build.VERSION.SDK_INT >= 20) pm.isInteractive else @Suppress("DEPRECATION") pm.isScreenOn
            Log.i(TAG, "key_received key=VOLUME_UP action=${if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"} repeat=${event.repeatCount} interactive=$interactive deviceIdle=${if (Build.VERSION.SDK_INT >= 23) pm.isDeviceIdleMode else false} serviceArmed=${RecordingService.isArmed} recording=${RecordingService.isRecording}")
        }
        val result = detector.onEvent(event.keyCode, event.action, event.repeatCount, event.eventTime)
        if (result != VolumeDoublePressDetector.Result.PASS) Log.i(TAG, "double_press_result=$result")
        return when (result) {
            VolumeDoublePressDetector.Result.PASS -> false
            VolumeDoublePressDetector.Result.CONSUME -> true
            VolumeDoublePressDetector.Result.TRIGGER_AND_CONSUME -> {
                if (RecordingService.isArmed) {
                    Log.i(TAG, "shortcut_triggered recordingCurrently=${RecordingService.isRecording}")
                    RecordingService.toggleFromShortcut(this)
                    true
                } else {
                    Log.e(TAG, "shortcut_triggered_but_fgs_not_armed; refusing background microphone start")
                    false
                }
            }
        }
    }

    private fun isShortcutEnabled() = getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_VOLUME_SHORTCUT_ENABLED, false)

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.w(TAG, "accessibility_unbound")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Log.w(TAG, "accessibility_destroyed")
        super.onDestroy()
    }

    companion object {
        const val PREFS = "tapora_preferences"
        const val KEY_VOLUME_SHORTCUT_ENABLED = "volume_shortcut_enabled"
        private const val TAG = "TAPORA-Shortcut"
    }
}
