package com.tapora.recorder

import com.tapora.recorder.service.*
import org.junit.Assert.*
import org.junit.Test

class RecordingReliabilityTest {
    @Test fun duplicateStartAndStopAreRejected() {
        val machine = RecordingStateMachine()
        assertTrue(machine.transition(RecordingAction.Start))
        assertFalse(machine.transition(RecordingAction.Start))
        assertTrue(machine.transition(RecordingAction.Stop))
        assertFalse(machine.transition(RecordingAction.Stop))
        assertEquals(RecordingPhase.Saving, machine.phase)
    }

    @Test fun saveMustCompleteBeforeReturningReady() {
        val machine = RecordingStateMachine()
        machine.transition(RecordingAction.Start)
        machine.transition(RecordingAction.Stop)
        assertEquals(RecordingPhase.Saving, machine.phase)
        assertTrue(machine.transition(RecordingAction.SaveSucceeded))
        assertEquals(RecordingPhase.Ready, machine.phase)
    }

    @Test fun failedSaveIsRecoverableButNotReportedAsReady() {
        val machine = RecordingStateMachine()
        machine.transition(RecordingAction.Start)
        machine.transition(RecordingAction.Stop)
        machine.transition(RecordingAction.Failed)
        assertEquals(RecordingPhase.Error, machine.phase)
        assertFalse(machine.transition(RecordingAction.SaveSucceeded))
        assertTrue(machine.transition(RecordingAction.Reset))
    }

    @Test fun volumeDoublePressTriggersOnlyOnSecondFreshDownWithinWindow() {
        val detector = VolumeDoublePressDetector(android.view.KeyEvent.KEYCODE_VOLUME_UP)
        assertEquals(VolumeDoublePressDetector.Result.PASS, detector.onEvent(24, 0, 0, 1000))
        assertEquals(VolumeDoublePressDetector.Result.TRIGGER_AND_CONSUME, detector.onEvent(24, 0, 0, 1200))
        assertEquals(VolumeDoublePressDetector.Result.CONSUME, detector.onEvent(24, 1, 0, 1210))
        assertEquals(VolumeDoublePressDetector.Result.PASS, detector.onEvent(24, 0, 0, 1300))
    }

    @Test fun repeatedKeyDownDoesNotTriggerAccidentally() {
        val detector = VolumeDoublePressDetector(android.view.KeyEvent.KEYCODE_VOLUME_UP)
        assertEquals(VolumeDoublePressDetector.Result.PASS, detector.onEvent(24, 0, 0, 1000))
        assertEquals(VolumeDoublePressDetector.Result.PASS, detector.onEvent(24, 0, 1, 1100))
        assertEquals(VolumeDoublePressDetector.Result.PASS, detector.onEvent(24, 0, 0, 1200))
    }
}
