package com.spiralcore.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.spiralcore.ui.theme.Ink
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection

@Composable
fun SpiralCoreApp(
    authenticate: (deviceCredentialOnly: Boolean, onSuccess: () -> Unit, onFailure: (String) -> Unit) -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                unlocked = false
                authError = null
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF163044),
                            Color(0xFF0B1422),
                            Color(0xFF070E16),
                        ),
                    ),
                )
                .windowInsetsPadding(WindowInsets.safeDrawing),
            color = Color.Transparent,
            contentColor = Ink,
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
