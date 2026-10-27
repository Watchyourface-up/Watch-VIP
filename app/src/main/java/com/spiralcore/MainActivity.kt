package com.spiralcore

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.spiralcore.ui.SpiralCoreApp
import com.spiralcore.ui.theme.SpiralCoreTheme

class MainActivity : FragmentActivity() {
    private lateinit var deviceCredentialLauncher: ActivityResultLauncher<Intent>
    private var credentialSuccess: (() -> Unit)? = null
    private var credentialFailure: ((String) -> Unit)? = null
    private var activeBiometricPrompt: BiometricPrompt? = null
    private var awaitingLegacyDeviceCredential = false
    private var authenticationGeneration = 0

    override fun onSaveInstanceState(outState: Bundle) {
        // No authentication state or callback is persisted across Activity recreation.
        invalidateAuthentication()
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        // A result delivered after backgrounding must never unlock a stopped/recreated Activity.
        invalidateAuthentication()
        super.onStop()
    }

    override fun onDestroy() {
        invalidateAuthentication()
        super.onDestroy()
    }

    private fun invalidateAuthentication() {
        authenticationGeneration++
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt = null
        credentialSuccess = null
        credentialFailure = null
        awaitingLegacyDeviceCredential = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        deviceCredentialLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val success = credentialSuccess
            val failure = credentialFailure
            val generation = pendingCredentialGeneration
            credentialSuccess = null
            credentialFailure = null
            awaitingLegacyDeviceCredential = false

            if (generation != authenticationGeneration || isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(
                    androidx.lifecycle.Lifecycle.State.RESUMED,
                )
            ) {
                return@registerForActivityResult
            }
            if (result.resultCode == RESULT_OK) {
                success?.invoke()
            } else {
                failure?.invoke("لم يتم فتح الخزنة.")
            }
        }

        setContent {
            SpiralCoreTheme {
                SpiralCoreApp { deviceCredentialOnly, onSuccess, onFailure ->
                    authenticate(deviceCredentialOnly, onSuccess, onFailure)
                }
            }
        }
    }

    private var pendingCredentialGeneration = -1

    private fun authenticate(
        deviceCredentialOnly: Boolean,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (isFinishing || isDestroyed || !lifecycle.currentState.isAtLeast(
                androidx.lifecycle.Lifecycle.State.RESUMED,
            )
        ) {
            return
        }

        // Invalidate any earlier request before starting a new one.
        authenticationGeneration++
        val generation = authenticationGeneration
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt = null
        credentialSuccess = null
        credentialFailure = null
        awaitingLegacyDeviceCredential = false

        val guardedSuccess = {
            if (generation == authenticationGeneration &&
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                !isFinishing && !isDestroyed
            ) {
                onSuccess()
            }
        }
        val guardedFailure: (String) -> Unit = { message ->
            if (generation == authenticationGeneration &&
                lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) &&
                !isFinishing && !isDestroyed
            ) {
                onFailure(message)
            }
        }

        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            guardedFailure("فعّل قفل الشاشة أو PIN للهاتف أولًا.")
            return
        }

        // Android 9/10 cannot combine strong biometrics with DEVICE_CREDENTIAL in one prompt.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (deviceCredentialOnly) {
                launchLegacyDeviceCredential(keyguard, generation, guardedSuccess, guardedFailure)
                return
            }
            val biometricAvailable = BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) == BiometricManager.BIOMETRIC_SUCCESS
            if (!biometricAvailable) {
                guardedFailure("البصمة غير جاهزة على هذا الهاتف. استخدم زر PIN الهاتف.")
                return
            }
        }

        val allowedAuthenticators = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && deviceCredentialOnly ->
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else -> BiometricManager.Authenticators.BIOMETRIC_STRONG
        }
        val promptBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (deviceCredentialOnly) "رمز قفل الهاتف" else "افتح SpiralCore")
            .setSubtitle(if (deviceCredentialOnly) "تحقق ببيانات اعتماد الجهاز" else "استخدم البصمة أو PIN الهاتف")
            .setAllowedAuthenticators(allowedAuthenticators)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            promptBuilder.setNegativeButtonText("إلغاء")
        }

        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    activeBiometricPrompt = null
                    guardedSuccess()
                }

                override fun onAuthenticationFailed() {
                    guardedFailure("لم يتم التعرّف على البصمة. حاول مرة أخرى أو استخدم PIN الهاتف.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    activeBiometricPrompt = null
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        guardedFailure(errString.toString())
                    }
                }
            },
        )
        activeBiometricPrompt = prompt
        try {
            prompt.authenticate(promptBuilder.build())
        } catch (_: RuntimeException) {
            activeBiometricPrompt = null
            guardedFailure("تعذر بدء التحقق من قفل الهاتف.")
        }
    }

    @Suppress("DEPRECATION")
    private fun launchLegacyDeviceCredential(
        keyguard: KeyguardManager,
        generation: Int,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val intent = keyguard.createConfirmDeviceCredentialIntent(
            "افتح SpiralCore",
            "أدخل PIN أو رمز قفل الهاتف للمتابعة.",
        )
        if (intent == null) {
            onFailure("تعذر فتح تحقق PIN الهاتف. تأكد من إعداد قفل الشاشة.")
            return
        }
        pendingCredentialGeneration = generation
        credentialSuccess = onSuccess
        credentialFailure = onFailure
        awaitingLegacyDeviceCredential = true
        try {
            deviceCredentialLauncher.launch(intent)
        } catch (_: RuntimeException) {
            credentialSuccess = null
            credentialFailure = null
            awaitingLegacyDeviceCredential = false
            onFailure("تعذر بدء تحقق PIN الهاتف.")
        }
    }
}
