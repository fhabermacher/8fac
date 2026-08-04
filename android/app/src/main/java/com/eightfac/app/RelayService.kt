package com.eightfac.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.security.keystore.UserNotAuthenticatedException
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

/** Foreground service holding the relay WebSocket (role "phone").
 *
 *  Prototype transport: persistent socket with exponential-backoff
 *  reconnect. Production: replace with a UnifiedPush/FCM wake-up that
 *  starts this service on demand — same message handling, no always-on
 *  connection.
 *
 *  Incoming "req" handling:
 *  1. [AutoAccept] window armed for the service → use the OS auth-window
 *     key ("totpw:") and reply immediately, no prompt. If the OS says the
 *     window has lapsed (UserNotAuthenticatedException), fall through.
 *  2. Otherwise → high-priority notification opening [ApproveActivity]. */
class RelayService : Service() {

    private val client = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var backoffMs = 1_000L
    private lateinit var pairing: Pairing
    private lateinit var box: CryptoBox

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        pairing = Pairing.load(this) ?: run { stopSelf(); return START_NOT_STICKY }
        box = CryptoBox(pairing.key)
        startForeground(1, serviceNotification())
        // A nudge (push wake, app opened, pairing changed) always tears down
        // the socket: it may be half-dead after Doze/network changes, and a
        // fresh connect is cheap. Cures the "force-stop to reconnect" state.
        if (i?.action == ACTION_RECONNECT && ws != null) {
            ws?.cancel(); ws = null
            backoffMs = 1_000L
        }
        if (ws == null) connect()
        return START_STICKY
    }

    private fun connect() {
        val req = Request.Builder().url(pairing.relay).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                backoffMs = 1_000L
                webSocket.send(JSONObject()
                    .put("role", "phone").put("pair_id", pairing.pairId)
                    .apply { pairing.invite?.let { put("invite", it) } }
                    .toString())
                Wake.endpoint(this@RelayService)?.let { url ->
                    webSocket.send(JSONObject().put("deposit",
                        box.seal(JSONObject().put("t", "endpoint")
                            .put("url", url))).toString())
                }
                flushPending()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = JSONObject(text)
                if (!msg.has("blob")) return
                val payload = runCatching { box.unseal(msg.getString("blob")) }
                    .getOrNull() ?: return
                if (payload.optString("t") == "req") handleRequest(payload)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable,
                                   r: Response?) = scheduleReconnect()

            override fun onClosed(webSocket: WebSocket, code: Int,
                                  reason: String) = scheduleReconnect()
        })
    }

    private fun scheduleReconnect() {
        ws = null
        handler.postDelayed({ if (ws == null) connect() }, backoffMs)
        backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
    }

    private fun handleRequest(req: JSONObject) {
        val service = req.getString("service")
        val reqId = req.getString("id")
        if (AutoAccept.isArmed(service)) {
            try {
                val code = Totp.code(SecretVault.macFor(service, window = true))
                send(JSONObject().put("t", "code")
                    .put("id", reqId).put("code", code))
                return
            } catch (_: UserNotAuthenticatedException) {
                // OS window lapsed even though app timer hadn't — the OS wins
            }
        }
        ApproveActivity.notifyRequest(this, service, reqId)
    }

    private fun trySend(payload: JSONObject): Boolean =
        ws?.send(JSONObject().put("blob", box.seal(payload)).toString()) == true

    private fun flushPending() {
        while (true) {
            val p = pending.peek() ?: return
            if (!trySend(p)) return
            pending.poll()
        }
    }

    private fun serviceNotification(): Notification {
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel("relay",
                "8fac relay", NotificationManager.IMPORTANCE_MIN))
        return NotificationCompat.Builder(this, "relay")
            .setContentTitle("8fac listening")
            .setSmallIcon(R.drawable.ic_stat_8fac)
            .setOngoing(true).build()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }

    companion object {
        private const val ACTION_RECONNECT = "com.eightfac.app.RECONNECT"
        private var instance: RelayService? = null
        private val pending = ArrayDeque<JSONObject>()

        fun start(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, RelayService::class.java))

        /** Start if needed AND force a fresh socket — the connection may be
         *  half-dead. Called on push wake, app open, and re-pair. */
        fun nudge(ctx: Context) =
            ctx.startForegroundService(Intent(ctx, RelayService::class.java)
                .setAction(ACTION_RECONNECT))

        /** Send an encrypted reply; queues (and restarts the service) if the
         *  socket is down so an approval never silently vanishes. */
        fun send(payload: JSONObject) {
            val svc = instance
            if (svc == null || !svc.trySend(payload)) pending.add(payload)
        }

        fun sendFrom(ctx: Context, payload: JSONObject) {
            send(payload)
            if (instance == null) start(ctx)
        }
    }
}
