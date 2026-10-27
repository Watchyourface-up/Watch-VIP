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

    override fun onSaveInstanceState(outState: Bundle) {
        // Authentication state and success callbacks are intentionally never saved.
        if (!awaitingLegacyDeviceCredential) {
            credentialSuccess = null
            credentialFailure = null
        }
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt = null
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        // Cancel an in-flight biometric dialog if this Activity is backgrounded.
        activeBiometricPrompt?.cancelAuthentication()
        activeBiometricPrompt = null
        if (!awaitingLegacyDeviceCredential) {
            credentialSuccess = null
            credentialFailure = null
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        deviceCredentialLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            val success = credentialSuccess
            val failure = credentialFailure
            awaitingLegacyDeviceCredential = false
            credentialSuccess = null
            credentialFailure = null
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

    private fun authenticate(
        deviceCredentialOnly: Boolean,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (!keyguard.isDeviceSecure) {
            onFailure("فعّل قفل الشاشة أو PIN للهاتف أولًا.")
            return
        }

        // Before Android 11, the system biometric prompt cannot combine BIOMETRIC_STRONG
        // and DEVICE_CREDENTIAL. Keep the explicit credential path usable even without biometrics.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (deviceCredentialOnly) {
                launchLegacyDeviceCredential(keyguard, onSuccess, onFailure)
                return
            }
            val biometricAvailable = BiometricManager.from(this).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG,
            ) == BiometricManager.BIOMETRIC_SUCCESS
            if (!biometricAvailable) {
                onFailure("البصمة غير جاهزة على هذا الهاتف. استخدم زر PIN الهاتف.")
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
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onFailure("لم يتم التعرّف على البصمة. حاول مرة أخرى أو استخدم PIN الهاتف.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    activeBiometricPrompt = null
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onFailure(errString.toString())
                    }
                }
            },
        )
        activeBiometricPrompt = prompt
        prompt.authenticate(promptBuilder.build())
    }

    @Suppress("DEPRECATION")
    private fun launchLegacyDeviceCredential(
        keyguard: KeyguardManager,
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
        credentialSuccess = onSuccess
        credentialFailure = onFailure
        awaitingLegacyDeviceCredential = true
        try {
            deviceCredentialLauncher.launch(intent)
        } catch (_: RuntimeException) {
            awaitingLegacyDeviceCredential = false
            credentialSuccess = null
            credentialFailure = null
            onFailure("تعذر بدء تحقق PIN الهاتف.")
        }
    }
}
