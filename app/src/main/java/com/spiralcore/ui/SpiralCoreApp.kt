package com.spiralcore.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spiralcore.ui.theme.Sand

@Composable
fun SpiralCoreApp(
    authenticate: (deviceCredentialOnly: Boolean, onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // Re-lock when the activity is no longer visible. This is intentionally
                    // independent of whether authentication was initiated by biometrics or PIN.
                    unlocked = false
                    authError = null
                }
                Lifecycle.Event.ON_DESTROY -> {
                    // Never preserve an unlocked state across Activity recreation.
                    unlocked = false
                    authError = null
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            color = Sand,
        ) {
            if (unlocked) {
                LibraryScreen()
            } else {
                LockScreen(
                    errorMessage = authError,
                    onBiometric = {
                        authError = null
                        authenticate(false, { unlocked = true }, { authError = it })
                    },
                    onDevicePin = {
                        authError = null
                        authenticate(true, { unlocked = true }, { authError = it })
                    },
                )
            }
        }
    }
}
