package com.spiralcore

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        deviceCredentialLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                credentialSuccess?.invoke()
            } else {
                credentialFailure?.invoke("لم يتم فتح الخزنة.")
            }
            credentialSuccess = null
            credentialFailure = null
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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && deviceCredentialOnly) {
            launchLegacyDeviceCredential(keyguard, onSuccess, onFailure)
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R &&
            BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            onFailure("البصمة غير جاهزة على هذا الهاتف. استخدم PIN الهاتف.")
            return
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
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    onFailure("لم يتم التعرّف على البصمة. حاول مرة أخرى أو استخدم PIN الهاتف.")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onFailure(errString.toString())
                    }
                }
            },
        )
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
        deviceCredentialLauncher.launch(intent)
    }
}
