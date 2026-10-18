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

    @Test fun volumeDoublePressRequiresTwoDistinctUpsWithinWindow() {
        var now = 1000L
        val detector = VolumeDoublePressDetector(450L) { now }
        assertFalse(detector.onVolumeUp())
        now += 200
        assertTrue(detector.onVolumeUp())
        now += 451
        assertFalse(detector.onVolumeUp())
    }

    @Test fun volumePressAtSameTimestampDoesNotTriggerAccidentally() {
        var now = 1000L
        val detector = VolumeDoublePressDetector(450L) { now }
        detector.onVolumeUp()
        assertFalse(detector.onVolumeUp())
    }
}
