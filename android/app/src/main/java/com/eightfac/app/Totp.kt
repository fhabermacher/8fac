package com.eightfac.app

import java.nio.ByteBuffer
import javax.crypto.Mac

/** RFC 6238, mirroring eightfac/totp.py. The Mac instance is initialized by
 *  the caller with a Keystore key (biometric-gated) — the raw secret is never
 *  in app memory after import. */
object Totp {
    fun code(mac: Mac, digits: Int = 6, period: Int = 30,
             nowMs: Long = System.currentTimeMillis()): String {
        val counter = nowMs / 1000 / period
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val h = mac.doFinal(msg)
        val offset = (h.last().toInt() and 0x0F)
        val bin = ByteBuffer.wrap(h, offset, 4).int and 0x7FFFFFFF
        return (bin % Math.pow(10.0, digits.toDouble()).toInt())
            .toString().padStart(digits, '0')
    }

    /** base32 decode for otpauth:// import (no padding required) */
    fun base32Decode(s: String): ByteArray {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var bits = 0; var value = 0
        val out = ArrayList<Byte>()
        for (c in s.trim().uppercase().replace(" ", "").trimEnd('=')) {
            value = (value shl 5) or alphabet.indexOf(c).also {
                require(it >= 0) { "bad base32 char $c" } }
            bits += 5
            if (bits >= 8) { bits -= 8; out.add(((value shr bits) and 0xFF).toByte()) }
        }
        return out.toByteArray()
    }
}
