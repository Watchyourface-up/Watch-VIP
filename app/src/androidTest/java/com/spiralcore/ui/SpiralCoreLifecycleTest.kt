package com.spiralcore.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.testing.TestLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SpiralCoreLifecycleTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun stoppedCompositionReturnsToLockedScreen() {
        val owner = TestLifecycleOwner(initialState = Lifecycle.State.RESUMED)
        var authenticateCalls = 0
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                SpiralCoreApp { _, onSuccess, _ ->
                    authenticateCalls++
                    onSuccess()
                }
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
    }

    @Test
    fun processOrConfigurationRecreationStartsLocked() {
        var authenticateCalls = 0
        compose.setContent {
            SpiralCoreApp { _, onSuccess, _ ->
                authenticateCalls++
                onSuccess()
            }
        }
        compose.onNodeWithText("فتح بالبصمة").performClick()
        compose.onNodeWithText("إنشاء قوقعة").assertIsDisplayed()

        compose.setContent {
            SpiralCoreApp { _, _, _ -> authenticateCalls++ }
        }
        compose.onNodeWithText("فتح بالبصمة").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, authenticateCalls) }
    }
}
