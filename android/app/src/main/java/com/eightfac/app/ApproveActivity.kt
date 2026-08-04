package com.eightfac.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** Fingerprint-only approval.
 *
 *  A request lights the screen (full-screen intent, like an incoming call)
 *  straight into the biometric prompt: no tap, no app to open — just the
 *  sensor. The prompt is persistent by design: accidental dismissals
 *  re-show it, so the only ways out are the fingerprint, the explicit Deny
 *  button, or the 45 s timeout that matches the PC side.
 *
 *  NOTE: Android 14+ rejects full-screen intents from non-calling apps
 *  until the user grants "Full screen intents" in special app access —
 *  MainActivity surfaces that (see canUseFullScreenIntent). */
class ApproveActivity : AppCompatActivity() {

    private lateinit var service: String
    private lateinit var reqId: String
    private var settled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        service = intent.getStringExtra("service") ?: return finish()
        reqId = intent.getStringExtra("req_id") ?: return finish()

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // The PC gives up at 45 s; stop nagging at the same moment.
        window.decorView.postDelayed({ settle(approved = false) }, 45_000)
        prompt()
    }

    private fun prompt() {
        if (settled) return
        val mac = SecretVault.macFor(service)
        val bp = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult) {
                    val code = Totp.code(result.cryptoObject!!.mac!!)
                    RelayService.sendFrom(this@ApproveActivity, JSONObject()
                        .put("t", "code").put("id", reqId).put("code", code))
                    settle(approved = true)
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    when (code) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_LOCKOUT,
                        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
                            settle(approved = false)
                        // swipe-away, transient system cancels: insist
                        else -> window.decorView.postDelayed({ prompt() }, 400)
                    }
                }
            })

        bp.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(service)
                .setSubtitle("Approve 2FA code for your PC")
                .setNegativeButtonText("Deny")
                .setConfirmationRequired(false) // sensor touch = done
                .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG)
                .build(),
            BiometricPrompt.CryptoObject(mac))
    }

    private fun settle(approved: Boolean) {
        if (settled) return
        settled = true
        if (!approved) {
            RelayService.sendFrom(this, JSONObject()
                .put("t", "deny").put("id", reqId))
        }
        // setAutoCancel only fires on a tap, and a full-screen launch isn't
        // one — without this the request notification lingers after approval.
        getSystemService(NotificationManager::class.java)
            .cancel(reqId.hashCode())
        finishAndRemoveTask()
    }

    companion object {
        fun notifyRequest(ctx: Context, service: String, reqId: String) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                "requests", "Code requests", NotificationManager.IMPORTANCE_HIGH))
            val intent = Intent(ctx, ApproveActivity::class.java)
                .putExtra("service", service).putExtra("req_id", reqId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pi = PendingIntent.getActivity(ctx, reqId.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(reqId.hashCode(), NotificationCompat.Builder(ctx, "requests")
                .setContentTitle("Code request: $service")
                .setContentText("Approve with fingerprint")
                .setSmallIcon(R.drawable.ic_stat_8fac)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .setTimeoutAfter(45_000)
                .build())
        }
    }
}
