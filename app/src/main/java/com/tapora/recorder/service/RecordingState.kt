package com.tapora.recorder.service

enum class RecordingPhase { Ready, Recording, Saving, Error }

enum class RecordingAction { Start, Stop, SaveSucceeded, Failed, Reset }

/** Pure state machine: invalid and duplicate actions are rejected. */
class RecordingStateMachine {
    var phase: RecordingPhase = RecordingPhase.Ready
        private set

    fun transition(action: RecordingAction): Boolean {
        val accepted = when (phase to action) {
            RecordingPhase.Ready to RecordingAction.Start -> RecordingPhase.Recording
            RecordingPhase.Recording to RecordingAction.Stop -> RecordingPhase.Saving
            RecordingPhase.Saving to RecordingAction.SaveSucceeded -> RecordingPhase.Ready
            RecordingPhase.Recording to RecordingAction.Failed -> RecordingPhase.Error
            RecordingPhase.Saving to RecordingAction.Failed -> RecordingPhase.Error
            RecordingPhase.Error to RecordingAction.Reset -> RecordingPhase.Ready
            else -> null
        } ?: return false
        phase = accepted
        return true
    }
}
