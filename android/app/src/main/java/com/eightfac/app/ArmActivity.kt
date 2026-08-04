package com.eightfac.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import com.eightfac.app.ui.EightfacTheme

/** Launched from the home-screen widget: pick a scope, fingerprint, armed.
 *  Translucent — appears as a floating dialog over whatever is open. */
class ArmActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val services = SecretVault.services()
        setContent {
            EightfacTheme {
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Auto-accept for 5 min") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            services.forEach { svc ->
                                TextButton(onClick = { pick(svc) }) { Text(svc) }
                            }
                            TextButton(onClick = { pick(AutoAccept.SCOPE_ALL) }) {
                                Text("ALL services (loud option)")
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { finish() }) { Text("Cancel") }
                    },
                )
            }
        }
    }

    private fun pick(scope: String) = Arming.arm(this, scope) { finish() }
}
