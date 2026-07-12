package com.pulsetile.hr.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.pulsetile.hr.R
import com.pulsetile.hr.data.MetricsSnapshot

/** Live HRV (rolling RMSSD) widget with its own trend graph — not shown live by WHOOP. */
class HrvWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot, sideDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hrv)
        applySquare(views, sideDp)

        val color = ContextCompat.getColor(context, R.color.accent)
        setGraph(context, views, sideDp, snap.hrvSeries, color)

        views.setTextViewText(R.id.value, snap.hrvMs?.toString() ?: context.getString(R.string.dash))
        views.setTextViewText(R.id.status, WidgetStyle.statusText(snap))
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
