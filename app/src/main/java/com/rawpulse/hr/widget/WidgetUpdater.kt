package com.rawpulse.hr.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.rawpulse.hr.data.HrRepository

/**
 * Pushes the current [HrRepository] snapshot to every placed RawPulse widget.
 * Called by the foreground service on each new heart-rate reading (~1/sec).
 * Builds per-id so each instance gets its correct square size.
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
            for (id in ids) {
                val sideDp = BaseHrWidgetProvider.squareSideDp(manager, id)
                manager.updateAppWidget(id, provider.buildViews(context, snap, sideDp))
            }
        }
    }
}
