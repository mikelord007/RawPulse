package com.pulsetile.hr.widget

import com.pulsetile.hr.R
import com.pulsetile.hr.data.ConnectionState
import com.pulsetile.hr.data.MetricsSnapshot

/** Shared formatting / colour helpers for the widgets. */
object WidgetStyle {

    private val zoneColorRes = intArrayOf(
        R.color.zone0, R.color.zone1, R.color.zone2,
        R.color.zone3, R.color.zone4, R.color.zone5
    )

    fun zoneColorRes(zone: Int): Int = zoneColorRes[zone.coerceIn(0, 5)]

    fun statusText(snap: MetricsSnapshot): String = when {
        snap.demo && snap.bpm != null -> "DEMO"
        snap.state == ConnectionState.CONNECTED && !snap.stale -> "LIVE"
        snap.state == ConnectionState.CONNECTED -> "…"
        snap.state == ConnectionState.SCANNING -> "SEARCH"
        snap.state == ConnectionState.CONNECTING -> "CONN…"
        else -> "OFF"
    }

    fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
