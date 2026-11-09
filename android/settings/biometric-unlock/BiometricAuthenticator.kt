package com.indoone.settings.biometric

import android.app.Activity
import android.os.Build
import android.os.CancellationSignal

class BiometricAuthenticator(private val activity: Activity) {
    fun canAuthenticate(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val manager = activity.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            ?: return false
        return manager.canAuthenticate() ==
            android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        authenticateInternal(
            title = "Enable Biometric Unlock",
            subtitle = "Confirm your fingerprint or device biometric",
            description = "Biometric unlock will work together with your App PIN.",
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    fun authenticateForUnlock(
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        authenticateInternal(
            title = "Unlock Sankalp",
            subtitle = "Use your fingerprint or device biometric",
            description = "Authenticate to unlock Sankalp.",
            onSuccess = onSuccess,
            onError = onError,
        )
    }

    private fun authenticateInternal(
        title: String,
        subtitle: String,
        description: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Biometric authentication is not supported on this Android version.")
            return
        }

        val executor = activity.mainExecutor
        val callback = object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult,
            ) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onError(errString.toString().ifBlank { "Biometric authentication failed." })
            }

            override fun onAuthenticationFailed() {
                onError("Biometric authentication failed. Try again.")
            }
        }

        val builder = android.hardware.biometrics.BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setDescription(description)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setDeviceCredentialAllowed(true)
        } else {
            builder.setNegativeButton("Cancel", executor) { _, _ ->
                onError("Biometric authentication cancelled.")
            }
        }

        builder.build().authenticate(CancellationSignal(), executor, callback)
    }
}
