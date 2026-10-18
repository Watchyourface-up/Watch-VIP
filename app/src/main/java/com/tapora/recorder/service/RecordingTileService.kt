package com.tapora.recorder.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class RecordingTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile.state = if (RecordingService.state.value == RecordingPhase.Recording) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        when (RecordingService.state.value) {
            RecordingPhase.Recording -> {
            RecordingService.stop(this)
            qsTile.state = Tile.STATE_INACTIVE
            }
            RecordingPhase.Saving -> return
            RecordingPhase.Ready, RecordingPhase.Error -> try {
                RecordingService.start(this)
                // The service changes state only after MediaRecorder.start succeeds.
                qsTile.state = Tile.STATE_UNAVAILABLE
            } catch (_: SecurityException) { qsTile.state = Tile.STATE_UNAVAILABLE }
        }
        qsTile.updateTile()
    }
}
