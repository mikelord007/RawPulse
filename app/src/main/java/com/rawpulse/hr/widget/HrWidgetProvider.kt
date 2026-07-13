package com.rawpulse.hr.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.rawpulse.hr.R
import com.rawpulse.hr.data.MetricsSnapshot

/** Main 1:1 widget: large live BPM number over a moving trend graph, colour-coded by zone. */
class HrWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot, sideDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hr)
        applySquare(views, sideDp)

        val live = snap.hasLiveReading
        val compact = isCompact(sideDp)
        val color = if (live) {
            ContextCompat.getColor(context, WidgetStyle.zoneColorRes(snap.zone))
        } else {
            ContextCompat.getColor(context, R.color.text_primary)
        }
        // At 1x1 there's no room for the sparkline; just show the big number.
        // Otherwise draw the trend graph when live, and clear it when idle (never freeze).
        if (live && !compact) {
            setGraph(context, views, sideDp, snap.bpmSeries, color)
        } else {
            views.setImageViewResource(R.id.graph, android.R.color.transparent)
        }

        // Drop the "BPM" caption and shrink the number so it fits a 1x1 tile.
        views.setViewVisibility(R.id.label, if (compact) View.GONE else View.VISIBLE)
        views.setTextViewTextSize(
            R.id.value, TypedValue.COMPLEX_UNIT_SP, if (compact) 34f else 52f
        )

        val dash = context.getString(R.string.dash)
        views.setTextViewText(R.id.value, if (live) snap.bpm?.toString() ?: dash else dash)
        views.setTextViewText(R.id.status, WidgetStyle.statusText(snap))
        views.setTextColor(R.id.value, color)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
