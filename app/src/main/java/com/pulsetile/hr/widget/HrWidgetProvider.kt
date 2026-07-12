package com.pulsetile.hr.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.pulsetile.hr.R
import com.pulsetile.hr.data.MetricsSnapshot

/** Main 1:1 widget: large live BPM number over a moving trend graph, colour-coded by zone. */
class HrWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot, sideDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hr)
        applySquare(views, sideDp)

        val color = if (snap.bpm != null) {
            ContextCompat.getColor(context, WidgetStyle.zoneColorRes(snap.zone))
        } else {
            ContextCompat.getColor(context, R.color.text_primary)
        }
        setGraph(context, views, sideDp, snap.bpmSeries, color)

        views.setTextViewText(R.id.value, snap.bpm?.toString() ?: context.getString(R.string.dash))
        views.setTextViewText(R.id.status, WidgetStyle.statusText(snap))
        views.setTextColor(R.id.value, color)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
