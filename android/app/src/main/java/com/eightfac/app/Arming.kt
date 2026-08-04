package com.eightfac.app

import android.widget.Toast
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Shared auto-accept arming: one auth (also unlocks the OS-side
 *  auth-window keys), then the window goes live. Used by the home screen
 *  and the widget's ArmActivity. */
object Arming {
    fun arm(activity: FragmentActivity, scope: String,
            onDone: () -> Unit = {}) {
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    r: BiometricPrompt.AuthenticationResult) {
                    AutoAccept.arm(activity, scope)
                    Toast.makeText(activity, "Auto-accept armed: $scope",
                        Toast.LENGTH_LONG).show()
                    onDone()
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    onDone()
                }
            }
        ).authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Arm auto-accept")
            .setSubtitle(if (scope == AutoAccept.SCOPE_ALL)
                "ALL services, 5 minutes" else "$scope, 5 minutes")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or
                Authenticators.DEVICE_CREDENTIAL)
            .build())
    }
}
