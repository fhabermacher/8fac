package com.eightfac.app

import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.SecretBox
import org.json.JSONObject
import kotlin.math.abs

/** E2E envelope under the pairing key — mirrors eightfac/crypto.py:
 *  blob = base64( nonce[24] || XSalsa20-Poly1305 ciphertext ), payload JSON
 *  carries "ts", unseal enforces ±60 s freshness. */
class CryptoBox(private val key: ByteArray) {
    private val ls = LazySodiumAndroid(SodiumAndroid())
    private val box = ls as SecretBox.Native

    fun seal(payload: JSONObject): String {
        payload.put("ts", System.currentTimeMillis() / 1000)
        val pt = payload.toString().toByteArray()
        val nonce = ls.randomBytesBuf(SecretBox.NONCEBYTES)
        val ct = ByteArray(SecretBox.MACBYTES + pt.size)
        check(box.cryptoSecretBoxEasy(ct, pt, pt.size.toLong(), nonce, key))
        return Base64.encodeToString(nonce + ct, Base64.NO_WRAP)
    }

    fun unseal(blobB64: String): JSONObject {
        val raw = Base64.decode(blobB64, Base64.NO_WRAP)
        val nonce = raw.copyOfRange(0, SecretBox.NONCEBYTES)
        val ct = raw.copyOfRange(SecretBox.NONCEBYTES, raw.size)
        val pt = ByteArray(ct.size - SecretBox.MACBYTES)
        check(box.cryptoSecretBoxOpenEasy(pt, ct, ct.size.toLong(), nonce, key)) {
            "decryption failed (tampered blob?)"
        }
        val obj = JSONObject(String(pt))
        val ts = obj.getLong("ts")
        require(abs(System.currentTimeMillis() / 1000 - ts) <= 60) { "stale payload" }
        return obj
    }
}
