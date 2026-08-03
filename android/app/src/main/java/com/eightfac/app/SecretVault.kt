package com.eightfac.app

import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import java.security.KeyStore
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** TOTP secrets live as HMAC-SHA1 keys inside the Android Keystore,
 *  biometric-gated: the key material is not extractable and the key is
 *  unusable until BiometricPrompt succeeds (per use, or within an armed
 *  auto-accept window — see [AutoAccept]).
 *
 *  Import path: scan otpauth:// QR → base32 decode → importSecret() → the
 *  raw bytes leave app memory for good. */
object SecretVault {
    private const val PREFIX = "totp:"
    private val ks: KeyStore =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun importSecret(service: String, rawSecret: ByteArray,
                     authWindowSeconds: Int = 0) {
        val protection = KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationParameters(
                authWindowSeconds, // 0 = biometric required per use
                KeyProperties.AUTH_BIOMETRIC_STRONG)
            .setInvalidatedByBiometricEnrollment(true)
            .build()
        ks.setEntry(PREFIX + service,
            KeyStore.SecretKeyEntry(SecretKeySpec(rawSecret, "HmacSHA1")),
            protection)
        rawSecret.fill(0)
    }

    fun services(): List<String> =
        ks.aliases().toList().filter { it.startsWith(PREFIX) }
            .map { it.removePrefix(PREFIX) }

    fun delete(service: String) = ks.deleteEntry(PREFIX + service)

    /** Returns an *uninitialized-auth* Mac to wrap in a
     *  BiometricPrompt.CryptoObject; usable only after auth succeeds. */
    fun macFor(service: String): Mac {
        val key = ks.getKey(PREFIX + service, null)
            ?: error("no secret for $service")
        return Mac.getInstance("HmacSHA1").apply { init(key) }
    }
}
