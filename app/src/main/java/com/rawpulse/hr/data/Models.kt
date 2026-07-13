package com.rawpulse.hr.data

/** Connection state of the BLE link to the WHOOP heart-rate broadcast. */
enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED
}

/**
 * A single heart-rate measurement decoded from the standard BLE
 * Heart Rate Measurement characteristic (0x2A37).
 *
 * @param bpm beats per minute
 * @param rrIntervalsMs R-R intervals in milliseconds (may be empty; used to derive live HRV)
 * @param timestampMs when this reading was received
 * @param sensorContact true/false if the sensor reports contact status, null if unsupported
 */
data class HrReading(
    val bpm: Int,
    val rrIntervalsMs: List<Int>,
    val timestampMs: Long = System.currentTimeMillis(),
    val sensorContact: Boolean? = null
)

/**
 * Immutable snapshot of everything the widgets and UI render. Produced by [HrRepository].
 *
 * @param bpm latest heart rate, or null if no reading yet
 * @param hrvMs rolling RMSSD (live HRV) in ms over the recent window, or null if not enough data
 * @param zone heart-rate zone 0..5 (0 = rest, 5 = max)
 * @param pctMax percent of max HR, or null
 * @param stale true if the latest reading is old (link dropped / out of range)
 */
data class MetricsSnapshot(
    val state: ConnectionState = ConnectionState.DISCONNECTED,
    val demo: Boolean = false,
    val bpm: Int? = null,
    val hrvMs: Int? = null,
    val minBpm: Int? = null,
    val avgBpm: Int? = null,
    val maxBpm: Int? = null,
    val sessionElapsedSec: Long = 0L,
    val zone: Int = 0,
    val pctMax: Int? = null,
    val stale: Boolean = true,
    val maxHr: Int = 190,
    /** Recent BPM values, oldest → newest, for the trend sparkline. */
    val bpmSeries: List<Int> = emptyList(),
    /** Recent HRV (RMSSD ms) values, oldest → newest, for the HRV sparkline. */
    val hrvSeries: List<Int> = emptyList()
) {
    /**
     * True only when we're connected AND the latest reading is fresh. This is the single
     * gate for showing a live number: when the link drops or the band goes silent, this
     * flips false so the UI shows "--" instead of a frozen value.
     */
    val hasLiveReading: Boolean
        get() = state == ConnectionState.CONNECTED && !stale
}
