package com.tapora.recorder.service

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class RecordingTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = if (RecordingService.state.value == RecordingPhase.Recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val permissionsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        if (!permissionsGranted) {
            qsTile.state = Tile.STATE_UNAVAILABLE
            qsTile.updateTile()
            return
        }
        when (RecordingService.state.value) {
            RecordingPhase.Recording -> RecordingService.stopRecording(this)
            RecordingPhase.Saving -> return
            RecordingPhase.Ready, RecordingPhase.Error -> try {
                RecordingService.start(this)
                // State becomes active only after MediaRecorder.start succeeds.
                qsTile.state = Tile.STATE_UNAVAILABLE
            } catch (_: SecurityException) { qsTile.state = Tile.STATE_UNAVAILABLE }
        }
        qsTile.updateTile()
    }
}
