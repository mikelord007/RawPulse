package com.pulsetile.hr.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import com.pulsetile.hr.MainActivity
import com.pulsetile.hr.R
import com.pulsetile.hr.data.HrRepository
import com.pulsetile.hr.data.MetricsSnapshot

/**
 * Base class for all PulseTile widgets. Live updates are pushed by the foreground
 * service via [WidgetUpdater]; onUpdate/onAppWidgetOptionsChanged cover placement,
 * resize and system refreshes.
 *
 * Widgets are forced to a true 1:1 square: an inner `card` view is sized to the
 * shorter side of the area the launcher grants (via setViewLayoutWidth/Height,
 * API 31+) and centered, so the visible card is square on any launcher grid.
 */
abstract class BaseHrWidgetProvider : AppWidgetProvider() {

    /** Build the RemoteViews for one instance. [sideDp] is the square card side. */
    abstract fun buildViews(context: Context, snap: MetricsSnapshot, sideDp: Int): RemoteViews

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val snap = HrRepository.current()
        for (id in ids) {
            mgr.updateAppWidget(id, buildViews(context, snap, squareSideDp(mgr, id)))
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        id: Int,
        newOptions: Bundle
    ) {
        val snap = HrRepository.current()
        mgr.updateAppWidget(id, buildViews(context, snap, squareSideDp(mgr, id)))
    }

    /** Force the inner card to a centered square of [sideDp] dp. */
    protected fun applySquare(views: RemoteViews, sideDp: Int) {
        views.setViewLayoutWidth(R.id.card, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
        views.setViewLayoutHeight(R.id.card, sideDp.toFloat(), TypedValue.COMPLEX_UNIT_DIP)
    }

    /** Render the trend sparkline into the card's background ImageView (R.id.graph). */
    protected fun setGraph(
        context: Context,
        views: RemoteViews,
        sideDp: Int,
        series: List<Int>,
        lineColor: Int
    ) {
        val density = context.resources.displayMetrics.density
        val px = (sideDp * density).toInt().coerceIn(1, MAX_BITMAP_PX)
        val cornerPx = CARD_CORNER_DP * px / sideDp.coerceAtLeast(1)
        val bmp = Sparkline.render(px, series, lineColor, cornerPx)
        if (bmp != null) {
            views.setImageViewBitmap(R.id.graph, bmp)
        } else {
            views.setImageViewResource(R.id.graph, android.R.color.transparent)
        }
    }

    protected fun tapIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        private const val DEFAULT_SIDE_DP = 110
        private const val MIN_SIDE_DP = 80
        private const val MAX_SIDE_DP = 320
        private const val MAX_BITMAP_PX = 384
        private const val CARD_CORNER_DP = 28f

        /** Square side (dp) = min of the widget's current portrait width/height. */
        fun squareSideDp(mgr: AppWidgetManager, id: Int): Int {
            val opts = mgr.getAppWidgetOptions(id)
            val w = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
            val h = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
            val side = minOf(
                if (w > 0) w else DEFAULT_SIDE_DP,
                if (h > 0) h else DEFAULT_SIDE_DP
            )
            return side.coerceIn(MIN_SIDE_DP, MAX_SIDE_DP)
        }
    }
}
