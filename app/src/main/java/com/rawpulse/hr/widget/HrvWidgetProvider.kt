package com.rawpulse.hr.widget

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.rawpulse.hr.R
import com.rawpulse.hr.data.MetricsSnapshot

/** Live HRV (rolling RMSSD) widget with its own trend graph — not shown live by WHOOP. */
class HrvWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot, sideDp: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_hrv)
        applySquare(views, sideDp)

        val compact = isCompact(sideDp)
        views.setViewVisibility(R.id.label, if (compact) View.GONE else View.VISIBLE)
        views.setTextViewTextSize(
            R.id.value, TypedValue.COMPLEX_UNIT_SP, if (compact) 34f else 50f
        )

        val dash = context.getString(R.string.dash)
        views.setTextViewText(R.id.value, if (snap.hasLiveReading) snap.hrvMs?.toString() ?: dash else dash)
        views.setTextViewText(R.id.status, WidgetStyle.statusText(snap))
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
