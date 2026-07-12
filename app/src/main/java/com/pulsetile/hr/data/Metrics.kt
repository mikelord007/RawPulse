package com.pulsetile.hr.data

import kotlin.math.sqrt

/** Pure helpers for heart-rate zones and HRV. */
object Metrics {

    /**
     * Heart-rate zone 0..5 based on percent of max HR:
     * 0 rest (<50%), 1 (50-60%), 2 (60-70%), 3 (70-80%), 4 (80-90%), 5 (>=90%).
     */
    fun zone(bpm: Int, maxHr: Int): Int {
        if (maxHr <= 0) return 0
        val pct = bpm * 100.0 / maxHr
        return when {
            pct < 50 -> 0
            pct < 60 -> 1
            pct < 70 -> 2
            pct < 80 -> 3
            pct < 90 -> 4
            else -> 5
        }
    }

    /**
     * RMSSD (root mean square of successive differences) over a list of R-R intervals in ms.
     * This is the standard short-window HRV metric. Returns null if there are too few intervals.
     */
    fun rmssd(rrIntervalsMs: List<Int>): Int? {
        if (rrIntervalsMs.size < 3) return null
        var sumSq = 0.0
        var n = 0
        for (i in 1 until rrIntervalsMs.size) {
            val diff = (rrIntervalsMs[i] - rrIntervalsMs[i - 1]).toDouble()
            sumSq += diff * diff
            n++
        }
        if (n == 0) return null
        return sqrt(sumSq / n).toInt()
    }
}
