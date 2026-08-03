package com.eightfac.app

import android.os.SystemClock

/** Time-boxed auto-approval windows (PROTOCOL.md §4).
 *
 *  Arming always costs one fingerprint (the arming flow authenticates
 *  against a Keystore key imported with authWindowSeconds = window, so the
 *  OS itself enforces the validity period — we don't hold secrets hostage
 *  to app logic). Scope "*" means all services; default is per-service.
 *  Hard cap 15 min. Everything approved inside a window is logged and
 *  surfaced when it ends. */
object AutoAccept {
    const val DEFAULT_SECONDS = 5 * 60
    const val MAX_SECONDS = 15 * 60

    private val until = HashMap<String, Long>()  // scope -> elapsedRealtime ms
    val log = ArrayList<String>()

    fun arm(scope: String, seconds: Int = DEFAULT_SECONDS) {
        require(seconds <= MAX_SECONDS)
        until[scope] = SystemClock.elapsedRealtime() + seconds * 1000L
        // TODO: persistent countdown notification with a "stop" action
        // TODO: widget PendingIntent lands here (after BiometricPrompt)
    }

    fun disarm() { until.clear() }

    fun isArmed(service: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        return listOf(service, "*").any { (until[it] ?: 0) > now }
            .also { if (it) log.add(service) }
    }

    fun remainingMs(): Long =
        ((until.values.maxOrNull() ?: 0) - SystemClock.elapsedRealtime())
            .coerceAtLeast(0)
}
