package com.example.util

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

enum class BiometricAuthStatus {
    AVAILABLE,
    NOT_ENROLLED,
    NOT_AVAILABLE,
    UNSUPPORTED
}

object BiometricHelper {

    fun checkBiometricStatus(context: Context): BiometricAuthStatus {
        val biometricManager = BiometricManager.from(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            return when (biometricManager.canAuthenticate(authenticators)) {
                BiometricManager.BIOMETRIC_SUCCESS -> BiometricAuthStatus.AVAILABLE
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAuthStatus.NOT_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAuthStatus.NOT_AVAILABLE
                else -> BiometricAuthStatus.UNSUPPORTED
            }
        } else {
            val biometricResult = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            if (biometricResult == BiometricManager.BIOMETRIC_SUCCESS) {
                return BiometricAuthStatus.AVAILABLE
            }
            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (keyguardManager?.isDeviceSecure == true) {
                return BiometricAuthStatus.AVAILABLE
            }
            return when (biometricResult) {
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAuthStatus.NOT_ENROLLED
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricAuthStatus.NOT_AVAILABLE
                else -> BiometricAuthStatus.UNSUPPORTED
            }
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "تأیید هویت جیب‌بان",
        subtitle: String = "برای دسترسی به اطلاعات مالی، اثر انگشت یا رمز دستگاه را وارد کنید",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            promptInfoBuilder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            @Suppress("DEPRECATION")
            promptInfoBuilder.setDeviceCredentialAllowed(true)
        }

        val promptInfo = promptInfoBuilder.build()

        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancelled, don't show a harsh error
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) {
                        val localizedMessage = when (errorCode) {
                            BiometricPrompt.ERROR_LOCKOUT ->
                                "تعداد تلاش‌های ناموفق زیاد بود. لطفاً چند ثانیه صبر کرده و دوباره تلاش کنید."
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                                "احراز هویت بیومتریک موقتاً قفل شد. لطفاً از رمز دستگاه استفاده کنید."
                            BiometricPrompt.ERROR_NO_BIOMETRICS,
                            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL ->
                                "هیچ اثر انگشت یا رمز صفحه‌ای در تنظیمات دستگاه تعریف نشده است."
                            BiometricPrompt.ERROR_HW_NOT_PRESENT,
                            BiometricPrompt.ERROR_HW_UNAVAILABLE ->
                                "سخت‌افزار احراز هویت در دسترس نیست."
                            else -> errString.toString().ifBlank { "خطا در احراز هویت" }
                        }
                        onError(localizedMessage)
                    }
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onError("احراز هویت انجام نشد. لطفاً مجدداً امتحان کنید.")
                }
            }
        )

        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            onError(e.message ?: "خطا در احراز هویت")
        }
    }
}
