package com.eightfac.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** One-tap home-screen widget: opens the arm dialog (scope + fingerprint). */
class ArmWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, ArmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE)
        ids.forEach { id ->
            val rv = RemoteViews(ctx.packageName, R.layout.widget_arm)
            rv.setOnClickPendingIntent(R.id.widget_btn, pi)
            mgr.updateAppWidget(id, rv)
        }
    }
}
