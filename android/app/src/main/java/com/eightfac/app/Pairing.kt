package com.eightfac.app

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/** The pairing scanned from the PC's QR (PROTOCOL.md §1), stored encrypted.
 *  pair_id routes at the relay; key is the E2E secretbox key. */
data class Pairing(val pairId: String, val key: ByteArray, val relay: String,
                   val invite: String? = null) {
    companion object {
        fun fromQr(json: String): Pairing {
            val o = JSONObject(json)
            require(o.getInt("v") == 0) { "unknown pairing version" }
            return Pairing(
                o.getString("pair_id"),
                Base64.decode(o.getString("key"), Base64.DEFAULT),
                o.getString("relay"),
                o.optString("invite").ifEmpty { null })
        }

        private fun prefs(ctx: Context) = EncryptedSharedPreferences.create(
            ctx, "pairing",
            MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)

        fun save(ctx: Context, p: Pairing) = prefs(ctx).edit()
            .putString("pair_id", p.pairId)
            .putString("key", Base64.encodeToString(p.key, Base64.NO_WRAP))
            .putString("relay", p.relay)
            .putString("invite", p.invite).apply()

        fun load(ctx: Context): Pairing? {
            val pr = prefs(ctx)
            val id = pr.getString("pair_id", null) ?: return null
            return Pairing(id,
                Base64.decode(pr.getString("key", null)!!, Base64.NO_WRAP),
                pr.getString("relay", null)!!,
                pr.getString("invite", null))
        }
    }
}
