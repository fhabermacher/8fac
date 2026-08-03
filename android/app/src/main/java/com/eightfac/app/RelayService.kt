package com.eightfac.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Foreground service holding the relay WebSocket (role "phone").
 *
 *  Prototype transport: persistent socket. Production: replace with a
 *  UnifiedPush/FCM wake-up that starts this service on demand — same
 *  message handling, no always-on connection.
 *
 *  On an incoming "req": if an [AutoAccept] window covers the service, the
 *  Keystore auth-window key is still valid → compute and reply immediately;
 *  otherwise post a high-priority notification opening [ApproveActivity]. */
class RelayService : Service() {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS).build()
    private var ws: WebSocket? = null
    private lateinit var pairing: Pairing
    private lateinit var box: CryptoBox

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        pairing = Pairing.load(this) ?: run { stopSelf(); return START_NOT_STICKY }
        box = CryptoBox(pairing.key)
        startForeground(1, serviceNotification())
        connect()
        return START_STICKY
    }

    private fun connect() {
        val req = Request.Builder().url(pairing.relay).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(JSONObject()
                    .put("role", "phone").put("pair_id", pairing.pairId).toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = JSONObject(text)
                if (!msg.has("blob")) return
                val payload = runCatching { box.unseal(msg.getString("blob")) }
                    .getOrNull() ?: return
                if (payload.optString("t") != "req") return
                handleRequest(payload)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, r: Response?) {
                // TODO exponential backoff reconnect
            }
        })
    }

    private fun handleRequest(req: JSONObject) {
        val service = req.getString("service")
        if (AutoAccept.isArmed(service)) {
            // auth-window key is OS-valid → no prompt needed
            val code = Totp.code(SecretVault.macFor(service))
            reply(JSONObject().put("t", "code")
                .put("id", req.getString("id")).put("code", code))
        } else {
            ApproveActivity.notifyRequest(this, service,
                req.getString("id"))
        }
    }

    fun reply(payload: JSONObject) {
        ws?.send(JSONObject().put("blob", box.seal(payload)).toString())
    }

    private fun serviceNotification(): Notification {
        val ch = NotificationChannel("relay", "8fac relay",
            NotificationManager.IMPORTANCE_MIN)
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(ch)
        return NotificationCompat.Builder(this, "relay")
            .setContentTitle("8fac listening")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun start(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, RelayService::class.java))
        // TODO: singleton access for ApproveActivity.reply — use a bound
        // service or a small event bus instead of this prototype shortcut
        var instance: RelayService? = null
    }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }
}
