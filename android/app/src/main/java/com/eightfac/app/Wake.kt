package com.eightfac.app

import android.content.Context
import org.unifiedpush.android.connector.MessagingReceiver
import org.unifiedpush.android.connector.UnifiedPush

/** Wake-on-demand via UnifiedPush (PROTOCOL.md §2): a distributor app
 *  (e.g. ntfy) holds the single battery-cheap push connection; when the PC
 *  POSTs to our endpoint, we get onMessage and nudge the relay connection
 *  awake. The endpoint URL is deposited (encrypted) at the relay so the PC
 *  can learn it. */
object Wake {
    private const val PREFS = "wake"

    fun setup(ctx: Context) {
        val distributors = UnifiedPush.getDistributors(ctx)
        if (distributors.isEmpty()) return // no ntfy app — fallback: socket only
        UnifiedPush.saveDistributor(ctx, distributors.first())
        UnifiedPush.registerApp(ctx)
    }

    fun ready(ctx: Context): Boolean = endpoint(ctx) != null

    fun endpoint(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, 0).getString("endpoint", null)

    fun save(ctx: Context, url: String?) =
        ctx.getSharedPreferences(PREFS, 0).edit()
            .putString("endpoint", url).apply()
}

class WakeReceiver : MessagingReceiver() {
    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
        Wake.save(context, endpoint)
        RelayService.nudge(context) // connect → deposit the new endpoint
    }

    override fun onMessage(context: Context, message: ByteArray, instance: String) {
        RelayService.nudge(context) // the PC wants us — connect now
    }

    override fun onUnregistered(context: Context, instance: String) {
        Wake.save(context, null)
    }

    override fun onRegistrationFailed(context: Context, instance: String) = Unit
}
