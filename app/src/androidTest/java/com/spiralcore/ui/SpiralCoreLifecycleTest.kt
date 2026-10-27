package com.spiralcore.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpiralCoreLifecycleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stopsAndRecreatedCompositionAlwaysRequiresAuthentication() {
        val owner = TestLifecycleOwner(initialState = Lifecycle.State.RESUMED)
        var authenticateCalls = 0
        compose.setContent {
            androidx.lifecycle.compose.LocalLifecycleOwner provides owner
            SpiralCoreApp { _, onSuccess, _ ->
                authenticateCalls++
                onSuccess()
            }
        }

        compose.onNodeWithText("فتح بالبصمة").assertIsDisplayed()
        compose.onNodeWithText("فتح بالبصمة").performClick()
        compose.onNodeWithText("إنشاء قوقعة").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, authenticateCalls) }

        compose.runOnIdle { owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP) }
        compose.onNodeWithText("فتح بالبصمة").assertIsDisplayed()
        compose.runOnIdle { owner.handleLifecycleEvent(Lifecycle.Event.ON_START) }
        compose.onNodeWithText("فتح بالبصمة").assertIsDisplayed()

        // A newly created composition does not retain the old unlocked state.
        compose.setContent {
            SpiralCoreApp { _, _, _ -> authenticateCalls++ }
        }
        compose.onNodeWithText("فتح بالبصمة").assertIsDisplayed()
    }
}
