package com.eightfac.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eightfac.app.ui.EightfacTheme

private val SECTIONS = listOf(
    "How it works" to
        "Your PC asks for a login code (hotkey), your phone lights up as a " +
        "fingerprint prompt, one touch — the code appears on the PC. " +
        "Secrets never leave this phone: they live in the hardware " +
        "keystore, unlocked per use by your fingerprint. The relay in " +
        "between only ever sees encrypted blobs.",
    "Setup, once" to
        "1. On the PC: run install.sh (or install.ps1), then pair.py — " +
        "scan its QR here via “Pair with PC”.\n" +
        "2. Enroll sites with “Add secret”: choose ‘authenticator app’ in " +
        "the site's 2FA settings and scan its QR.\n" +
        "3. Install the ntfy app so a sleeping phone can be woken.\n" +
        "4. Allow “instant prompt” and “background” when this app asks.",
    "Auto-accept" to
        "Arm a time-boxed window (5 min) with one fingerprint — from the " +
        "app or the home-screen widget — and requests approve themselves. " +
        "Per-service is the default; ALL is the loud option. A countdown " +
        "notification shows it's armed, with a Stop button, and you get a " +
        "summary of everything approved when it ends. The OS enforces a " +
        "hard 15-minute cap regardless of what this app does.",
    "Backups & losing this phone" to
        "Keystore secrets are non-extractable by design, so a backup copy " +
        "can only be made at import time — that's why importing asks for " +
        "a passphrase. “Export encrypted backup” drops the (useless " +
        "without passphrase) file into Downloads; keep it anywhere. " +
        "Recovery: tools/backup_decrypt.py --qr on the PC prints " +
        "enrollment QRs to scan on a new phone.\n\n" +
        "Independently: save each site's recovery codes when enrolling. " +
        "If the relay is ever down, “Show codes” works offline — a dead " +
        "relay is an inconvenience, never a lockout.",
    "Security model, honestly" to
        "Protects against: leaked passwords, a compromised relay (it's " +
        "blind), a stolen pairing (codes still need your fingerprint).\n" +
        "Does not protect against: phishing (TOTP's inherent limit — " +
        "prefer passkeys where offered), or malware on your PC during an " +
        "approved login.\n\nOpen source: github.com/fhabermacher/8fac",
)

class HelpActivity : AppCompatActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EightfacTheme {
                Scaffold(topBar = { TopAppBar(title = { Text("How 8fac works") }) }) { pad ->
                    Column(
                        Modifier.padding(pad).padding(horizontal = 20.dp)
                            .fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        SECTIONS.forEach { (title, body) ->
                            Card {
                                Column(Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(title,
                                        style = MaterialTheme.typography.titleMedium)
                                    Text(body,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
