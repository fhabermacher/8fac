package com.eightfac.app

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Base64
import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.PwHash
import com.goterl.lazysodium.interfaces.SecretBox
import com.sun.jna.NativeLong
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Encrypted backup of otpauth:// URIs, appended at import time — the only
 *  moment the raw secret exists outside the Keystore. Solves the
 *  phone-loss story: keystore keys are non-extractable BY DESIGN, so
 *  without this file a dead phone means re-enrolling everything.
 *
 *  Format (decryptable on the PC with tools/backup_decrypt.py):
 *    {"v":0, "kdf":"argon2id13", "ops":2, "mem":67108864,
 *     "salt":b64, "blob":b64(nonce24 || secretbox_ct)}
 *  plaintext = JSON array of otpauth URIs. Passphrase is never stored. */
object Backup {
    private const val OPS = 2L                       // libsodium INTERACTIVE
    private val MEM = NativeLong(67_108_864L)
    private val ls = LazySodiumAndroid(SodiumAndroid())

    private fun file(ctx: Context) = File(ctx.filesDir, "backup.8fac.json")
    fun exists(ctx: Context) = file(ctx).exists()

    /** Which services have a copy in the backup (names only, no secrets). */
    fun coveredServices(ctx: Context): Set<String> =
        ctx.getSharedPreferences("backup_meta", 0)
            .getStringSet("names", emptySet()) ?: emptySet()

    private fun recordCovered(ctx: Context, service: String) {
        val p = ctx.getSharedPreferences("backup_meta", 0)
        p.edit().putStringSet("names",
            (coveredServices(ctx) + service).toSet()).apply()
    }

    private fun deriveKey(pass: String, salt: ByteArray): ByteArray {
        val out = ByteArray(SecretBox.KEYBYTES)
        val pw = pass.toByteArray()
        check((ls as PwHash.Native).cryptoPwHash(out, out.size, pw, pw.size,
                salt, OPS, MEM, PwHash.Alg.PWHASH_ALG_ARGON2ID13)) {
            "key derivation failed"
        }
        return out
    }

    private fun box(key: ByteArray, plain: ByteArray): ByteArray {
        val nonce = ls.randomBytesBuf(SecretBox.NONCEBYTES)
        val ct = ByteArray(SecretBox.MACBYTES + plain.size)
        check((ls as SecretBox.Native).cryptoSecretBoxEasy(
            ct, plain, plain.size.toLong(), nonce, key))
        return nonce + ct
    }

    private fun unbox(key: ByteArray, raw: ByteArray): ByteArray {
        val nonce = raw.copyOfRange(0, SecretBox.NONCEBYTES)
        val ct = raw.copyOfRange(SecretBox.NONCEBYTES, raw.size)
        val out = ByteArray(ct.size - SecretBox.MACBYTES)
        check((ls as SecretBox.Native).cryptoSecretBoxOpenEasy(
            out, ct, ct.size.toLong(), nonce, key)) { "wrong passphrase" }
        return out
    }

    /** Append a URI; creates the backup on first use. Throws on wrong
     *  passphrase for an existing backup. */
    fun append(ctx: Context, passphrase: String, otpauthUri: String,
               service: String? = null) {
        val f = file(ctx)
        val (salt, uris) = if (f.exists()) {
            val o = JSONObject(f.readText())
            val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
            val plain = unbox(deriveKey(passphrase, salt),
                Base64.decode(o.getString("blob"), Base64.NO_WRAP))
            salt to JSONArray(String(plain))
        } else {
            ls.randomBytesBuf(PwHash.SALTBYTES) to JSONArray()
        }
        uris.put(otpauthUri)
        val blob = box(deriveKey(passphrase, salt),
            uris.toString().toByteArray())
        f.writeText(JSONObject()
            .put("v", 0).put("kdf", "argon2id13")
            .put("ops", OPS).put("mem", MEM.toLong())
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("blob", Base64.encodeToString(blob, Base64.NO_WRAP))
            .toString())
        service?.let { recordCovered(ctx, it) }  // only after a good write
    }

    /** Copy the encrypted file into Downloads/ so it can be synced/kept
     *  off-phone. The file is useless without the passphrase. */
    fun exportToDownloads(ctx: Context): Boolean {
        val f = file(ctx)
        if (!f.exists()) return false
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "8fac-backup.json")
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
        }
        val uri = ctx.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        ctx.contentResolver.openOutputStream(uri)?.use {
            it.write(f.readBytes())
        } ?: return false
        return true
    }
}
