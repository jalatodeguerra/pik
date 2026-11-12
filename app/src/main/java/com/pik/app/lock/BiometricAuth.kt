package com.pik.app.lock

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper around BiometricPrompt. Fingerprint / face (strong or weak) with the phone's PIN,
 * pattern or password as the fallback. BIOMETRIC_STRONG is part of BIOMETRIC_WEAK, so "strong or
 * weak or credential" is this one combination; it is also the combination that works from API 23 on
 * (strong + credential is not supported on Android 9 and 10).
 */
object BiometricAuth {
    const val AUTHENTICATORS: Int = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** True when the phone can check a fingerprint, face or screen lock right now. */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * True when the phone has no screen lock or biometric at all (or no hardware), which will not
     * fix itself. A temporarily unavailable sensor is not "permanent".
     */
    fun isPermanentlyUnavailable(context: Context): Boolean {
        val status = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
        return status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ||
            status == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }

    /**
     * Shows the system prompt. [onSuccess] runs after a good check; [onFailure] after the user cancels
     * or the prompt ends with an error (a wrong fingerprint just lets the user try again).
     */
    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String?,
        onSuccess: () -> Unit,
        onFailure: () -> Unit,
    ) {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .apply { if (subtitle != null) setSubtitle(subtitle) }
            .build()
        prompt.authenticate(info)
    }
}

/** The FragmentActivity behind a Compose [Context], or null. */
fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
