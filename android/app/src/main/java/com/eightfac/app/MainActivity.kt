package com.eightfac.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

/** Home screen: pair with a PC, import otpauth:// secrets, open the
 *  offline fallback codes screen, arm auto-accept. Deliberately ugly —
 *  function first. */
class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView

    private val scanPairing = registerForActivityResult(ScanContract()) { res ->
        res.contents?.let {
            runCatching { Pairing.fromQr(it) }
                .onSuccess { p ->
                    Pairing.save(this, p); RelayService.start(this); refresh()
                }
                .onFailure { toast("Not a valid 8fac pairing QR") }
        }
    }

    private val scanSecret = registerForActivityResult(ScanContract()) { res ->
        res.contents?.let { importOtpauth(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 18f }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48)
            addView(status)
            addView(button("Pair with PC (scan QR)") {
                scanPairing.launch(ScanOptions()
                    .setPrompt("Scan the QR from pair.py"))
            })
            addView(button("Add secret (scan otpauth QR)") {
                scanSecret.launch(ScanOptions()
                    .setPrompt("Scan the site's authenticator QR"))
            })
            addView(button("Show codes (offline fallback)") {
                startActivity(Intent(this@MainActivity, CodesActivity::class.java))
            })
            addView(button("Arm auto-accept…") { pickAutoAcceptScope() })
            // Doze suspends the relay socket; exemption keeps requests
            // arriving until the push wake-up path exists
            val pm = getSystemService(PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                addView(button("Allow background (battery exemption)") {
                    startActivity(Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")))
                })
            }
        }
        setContentView(root)
        refresh()
        // Android 13+: notifications (approval prompts!) are silent without this
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        if (Pairing.load(this) != null) RelayService.start(this)
    }

    /** otpauth://totp/Issuer:account?secret=BASE32&issuer=Issuer */
    private fun importOtpauth(text: String) {
        val uri = Uri.parse(text)
        if (uri.scheme != "otpauth" || uri.host != "totp") {
            toast("Not an otpauth://totp QR"); return
        }
        val secret = uri.getQueryParameter("secret")
            ?: run { toast("QR has no secret"); return }
        val label = uri.path?.trimStart('/') ?: ""
        val service = (uri.getQueryParameter("issuer")
            ?: label.substringBefore(':').ifEmpty { label })
            .lowercase().replace(" ", "-")
        runCatching {
            SecretVault.importSecret(service, Totp.base32Decode(secret))
        }.onSuccess { toast("Imported: $service"); refresh() }
            .onFailure { toast("Import failed: ${it.message}") }
    }

    private fun pickAutoAcceptScope() {
        val services = SecretVault.services()
        if (services.isEmpty()) { toast("No secrets yet"); return }
        val items = (services + "ALL services (loud option)").toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Auto-accept for 5 min — which service?")
            .setItems(items) { _, i ->
                val scope = if (i == services.size) AutoAccept.SCOPE_ALL
                            else services[i]
                armWithBiometric(scope)
            }.show()
    }

    /** Arming always costs one auth — it's also what unlocks the OS-side
     *  auth-window keys that auto-accept relies on. */
    private fun armWithBiometric(scope: String) {
        BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    r: BiometricPrompt.AuthenticationResult) {
                    AutoAccept.arm(this@MainActivity, scope)
                    toast("Auto-accept armed: $scope")
                }
            }
        ).authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Arm auto-accept")
            .setSubtitle(if (scope == AutoAccept.SCOPE_ALL)
                "ALL services, 5 minutes" else "$scope, 5 minutes")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or
                Authenticators.DEVICE_CREDENTIAL)
            .build())
    }

    private fun refresh() {
        val paired = Pairing.load(this) != null
        val services = SecretVault.services()
        status.text = buildString {
            append(if (paired) "Paired ✓" else "Not paired")
            append("\nSecrets: ")
            append(if (services.isEmpty()) "none" else services.joinToString())
            append("\n")
        }
    }

    private fun button(label: String, onClick: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { onClick() } }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
