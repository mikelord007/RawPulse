package com.pulsetile.hr.widget

import android.content.Context
import android.widget.RemoteViews
import com.pulsetile.hr.R
import com.pulsetile.hr.data.MetricsSnapshot

/** Live HRV (rolling RMSSD) widget — a metric the WHOOP app does not show in real time. */
class HrvWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hrv)
        views.setTextViewText(R.id.value, snap.hrvMs?.toString() ?: context.getString(R.string.dash))
        views.setTextViewText(R.id.status, WidgetStyle.statusText(snap))
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
