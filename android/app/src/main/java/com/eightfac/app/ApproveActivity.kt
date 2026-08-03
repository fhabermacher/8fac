package com.eightfac.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/** The one-tap-plus-fingerprint approval flow:
 *  notification (shows WHICH service — never approve blind, PROTOCOL.md §3)
 *  → tap → BiometricPrompt wrapping the Keystore HMAC key → compute TOTP
 *  → encrypted reply via RelayService. */
class ApproveActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val service = intent.getStringExtra("service") ?: return finish()
        val reqId = intent.getStringExtra("req_id") ?: return finish()

        val mac = SecretVault.macFor(service)
        val prompt = BiometricPrompt(this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult) {
                    val code = Totp.code(result.cryptoObject!!.mac!!)
                    RelayService.instance?.reply(JSONObject()
                        .put("t", "code").put("id", reqId).put("code", code))
                    finish()
                }

                override fun onAuthenticationError(code: Int, msg: CharSequence) {
                    RelayService.instance?.reply(JSONObject()
                        .put("t", "deny").put("id", reqId))
                    finish()
                }
            })

        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Code request: $service")
                .setSubtitle("Approve TOTP for your paired PC?")
                .setNegativeButtonText("Deny")
                .build(),
            BiometricPrompt.CryptoObject(mac))
    }

    companion object {
        fun notifyRequest(ctx: Context, service: String, reqId: String) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(
                "requests", "Code requests", NotificationManager.IMPORTANCE_HIGH))
            val intent = Intent(ctx, ApproveActivity::class.java)
                .putExtra("service", service).putExtra("req_id", reqId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(ctx, reqId.hashCode(), intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            nm.notify(reqId.hashCode(), NotificationCompat.Builder(ctx, "requests")
                .setContentTitle("Code request: $service")
                .setContentText("Tap to approve with fingerprint")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setTimeoutAfter(45_000) // matches PC-side TIMEOUT
                .build())
        }
    }
}
