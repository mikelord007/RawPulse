package com.pulsetile.hr.widget

import android.content.Context
import android.widget.RemoteViews
import com.pulsetile.hr.R
import com.pulsetile.hr.data.MetricsSnapshot

/** Session summary widget: min / avg / max BPM and elapsed time since streaming began. */
class SessionWidgetProvider : BaseHrWidgetProvider() {

    override fun buildViews(context: Context, snap: MetricsSnapshot): RemoteViews {
        val dash = context.getString(R.string.dash)
        val views = RemoteViews(context.packageName, R.layout.widget_session)
        views.setTextViewText(R.id.min_value, snap.minBpm?.toString() ?: dash)
        views.setTextViewText(R.id.avg_value, snap.avgBpm?.toString() ?: dash)
        views.setTextViewText(R.id.max_value, snap.maxBpm?.toString() ?: dash)
        views.setTextViewText(R.id.elapsed, WidgetStyle.formatElapsed(snap.sessionElapsedSec))
        views.setOnClickPendingIntent(R.id.widget_root, tapIntent(context))
        return views
    }
}
