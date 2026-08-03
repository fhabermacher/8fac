package com.eightfac.app

import android.os.Bundle
import android.os.CountDownTimer
import android.security.keystore.UserNotAuthenticatedException
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat

/** Manual fallback: show TOTP codes locally, like any authenticator.
 *  This screen is what makes depending on 8fac safe — a dead relay
 *  degrades to "read the code off the phone", never to lockout.
 *
 *  Per-use keys need one fingerprint per code; inside an armed auto-accept
 *  window (or within the OS auth window after any recent strong auth) the
 *  "totpw:" key lets us skip the prompt. */
class CodesActivity : AppCompatActivity() {

    private lateinit var list: LinearLayout
    private var ticker: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48)
        }
        setContentView(ScrollView(this).apply { addView(list) })
        render()
    }

    private fun render() {
        list.removeAllViews()
        val services = SecretVault.services()
        if (services.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No secrets yet — add one from the main screen."
                textSize = 16f
            })
        }
        for (svc in services) {
            val codeView = TextView(this).apply {
                text = "• • • • • •"; textSize = 34f
                gravity = Gravity.CENTER
            }
            val btn = Button(this).apply {
                text = svc
                setOnClickListener { showCode(svc, codeView) }
            }
            list.addView(btn); list.addView(codeView)
        }
    }

    private fun showCode(service: String, out: TextView) {
        // free ride if a strong auth happened within the OS window
        try {
            display(Totp.code(SecretVault.macFor(service, window = true)), out)
            return
        } catch (_: UserNotAuthenticatedException) { /* prompt below */ }

        val mac = SecretVault.macFor(service)
        BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult) {
                    display(Totp.code(result.cryptoObject!!.mac!!), out)
                }
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Show code: $service")
                .setNegativeButtonText("Cancel").build(),
            BiometricPrompt.CryptoObject(mac))
    }

    private fun display(code: String, out: TextView) {
        ticker?.cancel()
        val periodMs = 30_000L
        val remaining = periodMs - System.currentTimeMillis() % periodMs
        out.text = "${code.substring(0, 3)} ${code.substring(3)}"
        ticker = object : CountDownTimer(remaining, 1_000) {
            override fun onTick(ms: Long) {
                title = "8fac codes — ${ms / 1000 + 1}s"
            }
            override fun onFinish() {
                out.text = "expired — tap again"
                title = "8fac codes"
            }
        }.start()
    }

    override fun onDestroy() { ticker?.cancel(); super.onDestroy() }
}
