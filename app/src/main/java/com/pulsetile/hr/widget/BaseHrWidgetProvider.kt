package com.pulsetile.hr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.pulsetile.hr.MainActivity
import com.pulsetile.hr.data.HrRepository
import com.pulsetile.hr.data.MetricsSnapshot

/**
 * Base class for all PulseTile widgets. Subclasses only need to render a
 * [RemoteViews] from the current [MetricsSnapshot]. Live updates are pushed by
 * the foreground service via [WidgetUpdater]; the standard onUpdate path here
 * covers first placement and system-triggered refreshes.
 */
abstract class BaseHrWidgetProvider : AppWidgetProvider() {

    abstract fun buildViews(context: Context, snap: MetricsSnapshot): RemoteViews

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val snap = HrRepository.current()
        val views = buildViews(context, snap)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    protected fun tapIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
