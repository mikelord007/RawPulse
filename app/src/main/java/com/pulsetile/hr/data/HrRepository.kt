package com.pulsetile.hr.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for live heart-rate state, shared between the BLE service,
 * the widgets and the activity. Thread-safe; updated from BLE callback threads and
 * read from the main thread / widget updates.
 */
object HrRepository {

    private val lock = Any()

    private var state = ConnectionState.DISCONNECTED
    private var demo = false
    private var maxHr = 190

    private var lastReading: HrReading? = null
    private var sessionStartMs = 0L
    private var count = 0
    private var sum = 0L
    private var minBpm = Int.MAX_VALUE
    private var maxBpmSeen = 0

    // Rolling window of (timestamp, rrMs) for live HRV.
    private val rrWindow = ArrayDeque<Pair<Long, Int>>()

    private val _flow = MutableStateFlow(MetricsSnapshot())
    val flow: StateFlow<MetricsSnapshot> get() = _flow

    fun current(): MetricsSnapshot = _flow.value

    fun setMaxHr(value: Int) {
        synchronized(lock) { maxHr = value }
        publish()
    }

    fun setState(newState: ConnectionState) {
        synchronized(lock) { state = newState }
        publish()
    }

    fun setDemo(value: Boolean) {
        synchronized(lock) { demo = value }
        publish()
    }

    /** Clears session stats. Called when a new streaming session begins. */
    fun resetSession() {
        synchronized(lock) {
            lastReading = null
            sessionStartMs = 0L
            count = 0
            sum = 0L
            minBpm = Int.MAX_VALUE
            maxBpmSeen = 0
            rrWindow.clear()
        }
        publish()
    }

    fun addReading(reading: HrReading) {
        synchronized(lock) {
            if (reading.bpm <= 0) return
            lastReading = reading
            if (sessionStartMs == 0L) sessionStartMs = reading.timestampMs
            count++
            sum += reading.bpm
            if (reading.bpm < minBpm) minBpm = reading.bpm
            if (reading.bpm > maxBpmSeen) maxBpmSeen = reading.bpm

            for (rr in reading.rrIntervalsMs) {
                if (rr in 250..2000) rrWindow.addLast(reading.timestampMs to rr)
            }
            val cutoff = reading.timestampMs - HRV_WINDOW_MS
            while (rrWindow.isNotEmpty() && rrWindow.first().first < cutoff) {
                rrWindow.removeFirst()
            }
        }
        publish()
    }

    private fun publish() {
        _flow.value = buildSnapshot()
    }

    private fun buildSnapshot(): MetricsSnapshot = synchronized(lock) {
        val now = System.currentTimeMillis()
        val last = lastReading
        val hasData = last != null
        val stale = last == null || (now - last.timestampMs) > STALE_MS
        val bpm = last?.bpm
        val hrv = Metrics.rmssd(rrWindow.map { it.second })
        val avg = if (count > 0) (sum / count).toInt() else null
        val elapsed = if (sessionStartMs > 0) (now - sessionStartMs) / 1000 else 0L
        val pctMax = bpm?.let { it * 100 / maxHr }
        val zone = bpm?.let { Metrics.zone(it, maxHr) } ?: 0

        MetricsSnapshot(
            state = state,
            demo = demo,
            bpm = bpm,
            hrvMs = hrv,
            minBpm = if (hasData && minBpm != Int.MAX_VALUE) minBpm else null,
            avgBpm = avg,
            maxBpm = if (hasData && maxBpmSeen > 0) maxBpmSeen else null,
            sessionElapsedSec = elapsed,
            zone = zone,
            pctMax = pctMax,
            stale = stale,
            maxHr = maxHr
        )
    }

    private const val HRV_WINDOW_MS = 60_000L
    private const val STALE_MS = 8_000L
}
