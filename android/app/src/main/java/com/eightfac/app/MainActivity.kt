package com.eightfac.app

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eightfac.app.ui.EightfacTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

data class HomeState(
    val paired: Boolean = false,
    val relayHost: String = "",
    val services: List<String> = emptyList(),
    val wakeReady: Boolean = false,
    val batteryExempt: Boolean = true,
)

class MainActivity : AppCompatActivity() {

    private var state by mutableStateOf(HomeState())
    private var scopeDialog by mutableStateOf(false)

    private val scanPairing = registerForActivityResult(ScanContract()) { res ->
        res.contents?.let {
            runCatching { Pairing.fromQr(it) }
                .onSuccess { p ->
                    Pairing.save(this, p); RelayService.nudge(this); refresh()
                }
                .onFailure { toast("Not a valid 8fac pairing QR") }
        }
    }

    private val scanSecret = registerForActivityResult(ScanContract()) { res ->
        res.contents?.let { importOtpauth(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        Wake.setup(this)
        if (Pairing.load(this) != null) RelayService.nudge(this)
        setContent { EightfacTheme { HomeScreen() } }
        refresh()
    }

    override fun onResume() { super.onResume(); refresh() }

    @OptIn(ExperimentalMaterial3Api::class)
    @androidx.compose.runtime.Composable
    private fun HomeScreen() {
        Scaffold(topBar = { TopAppBar(title = { Text("8fac") }) }) { pad ->
            Column(
                Modifier.padding(pad).padding(horizontal = 20.dp)
                    .fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                StatusCard()
                ServicesCard()
                ActionButtons()
                if (!state.batteryExempt) BatteryCard()
            }
        }
        if (scopeDialog) ScopeDialog()
    }

    @androidx.compose.runtime.Composable
    private fun StatusCard() = Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (state.paired) "Paired ✓" else "Not paired",
                style = MaterialTheme.typography.titleLarge)
            if (state.paired)
                Text("Relay: ${state.relayHost}",
                    style = MaterialTheme.typography.bodyMedium)
            Text(
                if (state.wakeReady) "Push wake: ready ✓"
                else "Push wake: none — install the ntfy app for reliable wake-ups",
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.wakeReady) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
            )
        }
    }

    @androidx.compose.runtime.Composable
    private fun ServicesCard() = Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Secrets", style = MaterialTheme.typography.titleMedium)
            if (state.services.isEmpty())
                Text("None yet — add one by scanning a site's authenticator QR.",
                    style = MaterialTheme.typography.bodyMedium)
            else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.services.forEach {
                    AssistChip(onClick = {}, label = { Text(it) })
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ActionButtons() = Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = {
            scanPairing.launch(ScanOptions().setPrompt("Scan the QR from pair.py"))
        }, Modifier.fillMaxWidth()) { Text("Pair with PC") }
        FilledTonalButton(onClick = {
            scanSecret.launch(ScanOptions()
                .setPrompt("Scan the site's authenticator QR"))
        }, Modifier.fillMaxWidth()) { Text("Add secret") }
        FilledTonalButton(onClick = {
            startActivity(Intent(this@MainActivity, CodesActivity::class.java))
        }, Modifier.fillMaxWidth()) { Text("Show codes (offline fallback)") }
        OutlinedButton(onClick = {
            if (state.services.isEmpty()) toast("No secrets yet")
            else scopeDialog = true
        }, Modifier.fillMaxWidth()) { Text("Arm auto-accept…") }
    }

    @androidx.compose.runtime.Composable
    private fun BatteryCard() = Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Background reliability",
                style = MaterialTheme.typography.titleMedium)
            Text("Without a battery exemption Android may delay requests " +
                "when the phone is idle.",
                style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = {
                startActivity(Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")))
            }) { Text("Allow background") }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ScopeDialog() = AlertDialog(
        onDismissRequest = { scopeDialog = false },
        title = { Text("Auto-accept for 5 min") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                state.services.forEach { svc ->
                    TextButton(onClick = {
                        scopeDialog = false; armWithBiometric(svc)
                    }) { Text(svc) }
                }
                TextButton(onClick = {
                    scopeDialog = false
                    armWithBiometric(AutoAccept.SCOPE_ALL)
                }) { Text("ALL services (loud option)") }
            }
        },
        confirmButton = {
            TextButton(onClick = { scopeDialog = false }) { Text("Cancel") }
        },
    )

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

    /** Arming always costs one auth — it also unlocks the OS-side
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
        val pairing = Pairing.load(this)
        val pm = getSystemService(PowerManager::class.java)
        state = HomeState(
            paired = pairing != null,
            relayHost = pairing?.relay?.removePrefix("wss://")
                ?.removePrefix("ws://") ?: "",
            services = SecretVault.services(),
            wakeReady = Wake.ready(this),
            batteryExempt = pm.isIgnoringBatteryOptimizations(packageName),
        )
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
