package com.pulsetile.hr.widget

import android.content.Context
import android.content.res.ColorStateList
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.pulsetile.hr.R
import com.pulsetile.hr.data.MetricsSnapshot

/** Heart-rate zone widget: % of max HR, coloured by zone with a progress bar. */
class ZoneWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_zone)
        val color = ContextCompat.getColor(context, WidgetStyle.zoneColorRes(snap.zone))

        views.setTextViewText(R.id.value, snap.pctMax?.toString() ?: context.getString(R.string.dash))
        views.setTextColor(R.id.value, color)
        views.setTextViewText(R.id.zone_label, "ZONE ${snap.zone}")
        views.setTextColor(R.id.zone_label, color)
        views.setProgressBar(R.id.zone_bar, 100, (snap.pctMax ?: 0).coerceIn(0, 100), false)
        views.setColorStateList(R.id.zone_bar, "setProgressTintList", ColorStateList.valueOf(color))
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
