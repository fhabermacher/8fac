package com.eightfac.app

import android.os.Bundle
import android.security.keystore.UserNotAuthenticatedException
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.eightfac.app.ui.EightfacTheme
import kotlinx.coroutines.delay

/** Manual fallback: codes shown locally, like any authenticator — a dead
 *  relay degrades to "read the code off the phone", never to lockout.
 *  One fingerprint per code (per-use Keystore keys); free inside an armed
 *  auto-accept / recent-auth window. */
class CodesActivity : AppCompatActivity() {

    // service -> code shown; codes vanish at each 30 s boundary
    private val codes = mutableStateMapOf<String, String>()
    private var now by mutableLongStateOf(System.currentTimeMillis())

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EightfacTheme {
                LaunchedEffect(Unit) {
                    while (true) {
                        now = System.currentTimeMillis()
                        if (now % 30_000 < 250) codes.clear() // period rolled
                        delay(200)
                    }
                }
                val remaining = 30f - (now % 30_000) / 1000f
                Scaffold(topBar = { TopAppBar(title = { Text("Codes") }) }) { pad ->
                    Column(Modifier.padding(pad).padding(horizontal = 20.dp)
                        .fillMaxSize()) {
                        LinearProgressIndicator(
                            progress = { remaining / 30f },
                            Modifier.fillMaxWidth().padding(vertical = 10.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val services = SecretVault.services()
                            if (services.isEmpty()) item {
                                Text("No secrets yet — add one from the main screen.")
                            }
                            items(services) { svc -> ServiceCard(svc) }
                        }
                    }
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun ServiceCard(service: String) = Card(
        Modifier.fillMaxWidth().clickable { reveal(service) }) {
        Column(Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(service, style = MaterialTheme.typography.titleMedium)
            val code = codes[service]
            Text(
                code?.let { "${it.take(3)} ${it.drop(3)}" } ?: "•••  •••",
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                color = if (code != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
            )
            if (code == null)
                Text("tap to reveal", style = MaterialTheme.typography.labelMedium)
        }
    }

    private fun reveal(service: String) {
        try { // free ride within the OS auth window
            codes[service] = Totp.code(SecretVault.macFor(service, window = true))
            return
        } catch (_: UserNotAuthenticatedException) { /* prompt below */ }

        val mac = SecretVault.macFor(service)
        BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult) {
                    codes[service] = Totp.code(result.cryptoObject!!.mac!!)
                }
            }
        ).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Show code: $service")
                .setNegativeButtonText("Cancel").build(),
            BiometricPrompt.CryptoObject(mac))
    }
}
