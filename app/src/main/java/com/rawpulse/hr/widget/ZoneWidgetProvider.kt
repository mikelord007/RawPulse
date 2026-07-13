package com.rawpulse.hr.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.rawpulse.hr.R
import com.rawpulse.hr.data.MetricsSnapshot

/** Heart-rate zone widget: % of max HR over a trend graph, coloured by zone. */
class ZoneWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot, sideDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_zone)
        applySquare(views, sideDp)

        val live = snap.hasLiveReading
        val dash = context.getString(R.string.dash)
        val color = if (live) {
            ContextCompat.getColor(context, WidgetStyle.zoneColorRes(snap.zone))
        } else {
            ContextCompat.getColor(context, R.color.text_primary)
        }

        // At 1x1 drop the "% MAX" caption and progress bar and shrink the number to fit.
        val compact = isCompact(sideDp)
        views.setViewVisibility(R.id.label, if (compact) View.GONE else View.VISIBLE)
        views.setViewVisibility(R.id.zone_bar, if (compact) View.GONE else View.VISIBLE)
        views.setTextViewTextSize(
            R.id.value, TypedValue.COMPLEX_UNIT_SP, if (compact) 32f else 52f
        )

        views.setTextViewText(R.id.value, if (live) snap.pctMax?.toString() ?: dash else dash)
        views.setTextColor(R.id.value, color)
        views.setTextViewText(R.id.zone_label, if (live) "ZONE ${snap.zone}" else "ZONE")
        views.setTextColor(R.id.zone_label, color)
        views.setProgressBar(R.id.zone_bar, 100, if (live) (snap.pctMax ?: 0).coerceIn(0, 100) else 0, false)
        views.setColorStateList(R.id.zone_bar, "setProgressTintList", ColorStateList.valueOf(color))
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
