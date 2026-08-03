package com.eightfac.app

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Minimal home screen: pair with a PC (scan its QR), list services,
 *  start the relay service. Deliberately ugly — function first.
 *
 *  TODO: import secrets by scanning otpauth:// QRs (Totp.base32Decode →
 *        SecretVault.importSecret), manual code display as relay-down
 *        fallback (a MUST before depending on this — see README), and the
 *        auto-accept arm button/widget (BiometricPrompt → AutoAccept.arm). */
class MainActivity : AppCompatActivity() {

    private val scanPairing = registerForActivityResult(ScanContract()) { res ->
        res.contents?.let {
            Pairing.save(this, Pairing.fromQr(it))
            RelayService.start(this)
            recreate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48)
        }
        val status = TextView(this).apply {
            text = if (Pairing.load(this@MainActivity) != null)
                "Paired ✓ — services: ${SecretVault.services().joinToString()}"
            else "Not paired"
            textSize = 18f
        }
        val pairBtn = Button(this).apply {
            text = "Pair with PC (scan QR)"
            setOnClickListener {
                scanPairing.launch(ScanOptions()
                    .setPrompt("Scan the QR from pair.py"))
            }
        }
        root.addView(status); root.addView(pairBtn)
        setContentView(root)

        if (Pairing.load(this) != null) RelayService.start(this)
    }
}
