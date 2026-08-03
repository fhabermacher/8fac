package com.eightfac.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat

/** Time-boxed auto-approval windows (PROTOCOL.md §4).
 *
 *  Layered enforcement:
 *  - The OS: auto-accept uses the "totpw:" auth-window Keystore keys, valid
 *    for at most [MAX_SECONDS] after the arming fingerprint. Even a bug here
 *    can't stretch beyond that.
 *  - This class: the user-chosen (shorter) window and scope, the countdown
 *    notification with a Stop action, and the approval log surfaced when
 *    the window ends. */
object AutoAccept {
    const val DEFAULT_SECONDS = 5 * 60
    const val MAX_SECONDS = 15 * 60
    const val SCOPE_ALL = "*"
    private const val NOTIF_ID = 2
    private const val CHANNEL = "autoaccept"

    private val until = HashMap<String, Long>()  // scope -> elapsedRealtime ms
    private val approvals = ArrayList<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var expiry: Runnable? = null

    /** Call ONLY after a successful BiometricPrompt — the prompt is what
     *  makes the OS-side window keys usable at all. */
    fun arm(ctx: Context, scope: String, seconds: Int = DEFAULT_SECONDS) {
        require(seconds in 1..MAX_SECONDS)
        until[scope] = SystemClock.elapsedRealtime() + seconds * 1000L
        showCountdown(ctx, scope, seconds)
        expiry?.let { handler.removeCallbacks(it) }
        expiry = Runnable { disarm(ctx) }.also {
            handler.postDelayed(it, seconds * 1000L)
        }
    }

    fun disarm(ctx: Context) {
        until.clear()
        expiry?.let { handler.removeCallbacks(it) }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.cancel(NOTIF_ID)
        if (approvals.isNotEmpty()) {
            nm.notify(NOTIF_ID + 1, builder(ctx)
                .setContentTitle("Auto-accept ended")
                .setStyle(NotificationCompat.BigTextStyle()
                    .bigText("Approved without prompt:\n" +
                        approvals.joinToString("\n")))
                .setAutoCancel(true).build())
            approvals.clear()
        }
    }

    /** True if [service] is covered by an armed window; logs the hit. */
    fun isArmed(service: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        val hit = listOf(service, SCOPE_ALL).any { (until[it] ?: 0) > now }
        if (hit) approvals.add(service)
        return hit
    }

    private fun showCountdown(ctx: Context, scope: String, seconds: Int) {
        val stop = PendingIntent.getBroadcast(ctx, 0,
            Intent(ctx, DisarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val label = if (scope == SCOPE_ALL) "ALL services" else scope
        ctx.getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, builder(ctx)
                .setContentTitle("Auto-accept armed: $label")
                .setUsesChronometer(true).setChronometerCountDown(true)
                .setWhen(System.currentTimeMillis() + seconds * 1000L)
                .setOngoing(true)
                .addAction(0, "Stop", stop)
                .build())
    }

    private fun builder(ctx: Context): NotificationCompat.Builder {
        ctx.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL,
                "Auto-accept", NotificationManager.IMPORTANCE_DEFAULT))
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_8fac)
    }

    class DisarmReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) = disarm(ctx)
    }
}
