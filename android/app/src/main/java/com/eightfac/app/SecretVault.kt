package com.eightfac.app

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** TOTP secrets live as non-extractable HMAC-SHA1 keys in the Android
 *  Keystore. Each secret is imported under TWO aliases:
 *
 *  - "totp:<service>"  — biometric required per use (timeout 0). Used by the
 *    normal approval flow and the manual fallback screen.
 *  - "totpw:<service>" — auth-window key: usable for up to [WINDOW_CAP_S]
 *    seconds after any successful strong auth. Used by auto-accept, so the
 *    OS itself enforces the hard cap; [AutoAccept] enforces the (shorter)
 *    user-chosen window on top.
 *
 *  Import path: scan otpauth:// QR → base32 decode → importSecret() → the
 *  raw bytes are zeroed and never touch disk. */
object SecretVault {
    private const val PREFIX = "totp:"
    private const val PREFIX_WINDOW = "totpw:"
    const val WINDOW_CAP_S = AutoAccept.MAX_SECONDS  // OS-enforced hard cap

    private val ks: KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun importSecret(service: String, rawSecret: ByteArray) {
        val spec = SecretKeySpec(rawSecret, "HmacSHA1")
        ks.setEntry(PREFIX + service, KeyStore.SecretKeyEntry(spec),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build())
        ks.setEntry(PREFIX_WINDOW + service, KeyStore.SecretKeyEntry(spec),
            KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(WINDOW_CAP_S,
                    KeyProperties.AUTH_BIOMETRIC_STRONG or
                        KeyProperties.AUTH_DEVICE_CREDENTIAL)
                .setInvalidatedByBiometricEnrollment(true)
                .build())
        rawSecret.fill(0)
    }

    fun services(): List<String> =
        ks.aliases().toList().filter { it.startsWith(PREFIX) }
            .map { it.removePrefix(PREFIX) }.sorted()

    fun delete(service: String) {
        ks.deleteEntry(PREFIX + service)
        ks.deleteEntry(PREFIX_WINDOW + service)
    }

    /** Per-use variant: returned Mac must be wrapped in a
     *  BiometricPrompt.CryptoObject; usable only after that auth succeeds.
     *  Window variant (window=true): init() throws
     *  UserNotAuthenticatedException unless a strong auth happened within
     *  the last WINDOW_CAP_S seconds — callers catch that and fall back to
     *  the prompt flow. */
    fun macFor(service: String, window: Boolean = false): Mac {
        val alias = (if (window) PREFIX_WINDOW else PREFIX) + service
        val key = ks.getKey(alias, null) ?: error("no secret for $service")
        return Mac.getInstance("HmacSHA1").apply { init(key) }
    }
}
