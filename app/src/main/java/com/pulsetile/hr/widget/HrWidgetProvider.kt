package com.pulsetile.hr.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.pulsetile.hr.R
import com.pulsetile.hr.data.MetricsSnapshot

/** Main 1:1 widget: large live BPM number, colour-coded by heart-rate zone. */
class HrWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hr)
        views.setTextViewText(R.id.value, snap.bpm?.toString() ?: context.getString(R.string.dash))
        views.setTextViewText(R.id.status, WidgetStyle.statusText(snap))
        val color = if (snap.bpm != null) {
            ContextCompat.getColor(context, WidgetStyle.zoneColorRes(snap.zone))
        } else {
            ContextCompat.getColor(context, R.color.text_primary)
        }
        views.setTextColor(R.id.value, color)
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
