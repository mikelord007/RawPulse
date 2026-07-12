package com.pulsetile.hr.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.pulsetile.hr.data.HrRepository

/**
 * Pushes the current [HrRepository] snapshot to every placed PulseTile widget.
 * Called by the foreground service on each new heart-rate reading (~1/sec).
 */
object WidgetUpdater {

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val snap = HrRepository.current()
        val providers = listOf(
            HrWidgetProvider(),
            HrvWidgetProvider(),
            SessionWidgetProvider(),
            ZoneWidgetProvider()
        )
        for (provider in providers) {
            val ids = manager.getAppWidgetIds(ComponentName(context, provider.javaClass))
            if (ids.isEmpty()) continue
            val views = provider.buildViews(context, snap)
            for (id in ids) {
                manager.updateAppWidget(id, views)
            }
        }
    }
}
