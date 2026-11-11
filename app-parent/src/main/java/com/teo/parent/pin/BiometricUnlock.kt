package com.teo.parent.pin

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint/face unlock for the parent app — only ever a shortcut next to the protection code,
 * never a replacement: every screen that offers it keeps the code entry visible, and the code
 * is what works on the child's phone (whose sensor doesn't know the parent's finger).
 *
 * BIOMETRIC_WEAK so face unlock is offered wherever the phone exposes it to apps; Samsung usually
 * exposes only the fingerprint sensor, in which case this simply means fingerprint. The on/off
 * choice is per device on purpose: a second parent's phone has its own fingers enrolled.
 */
object BiometricUnlock {

    private const val PREFS = "app_lock"
    private const val KEY_ENABLED = "biometric_enabled"

    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun isEnabled(context: Context): Boolean =
        isAvailable(context) &&
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Shows the system prompt; [onSuccess] only on a recognised finger/face. Cancelling or
     *  failing just closes it — the code entry underneath is the way in from there. */
    fun prompt(activity: FragmentActivity, title: String, onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setNegativeButtonText("Ввести код")
            .build()
        prompt.authenticate(info)
    }
}
