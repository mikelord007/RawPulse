package com.rawpulse.hr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.rawpulse.hr.MainActivity
import com.rawpulse.hr.R
import com.rawpulse.hr.ble.WhoopHrManager
import com.rawpulse.hr.data.ConnectionState
import com.rawpulse.hr.data.HrReading
import com.rawpulse.hr.data.HrRepository
import com.rawpulse.hr.data.Settings
import com.rawpulse.hr.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random
import kotlin.math.sin

/**
 * Foreground service that holds the BLE connection to the WHOOP and pushes each
 * reading to the widgets. This is what makes ~1/sec updates possible: Android's
 * built-in widget refresh has a 30-minute minimum, so we update the widgets
 * ourselves from here instead.
 */
class HrService : Service(), WhoopHrManager.Listener {

    private lateinit var manager: WhoopHrManager
    private lateinit var settings: Settings
    private lateinit var notificationManager: NotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var demoJob: Job? = null
    private var heartbeatJob: Job? = null
    private var running = false
    private var currentDemo = false

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager = WhoopHrManager(this).apply { listener = this@HrService }
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopStreaming()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startStreaming()
        }
        return START_STICKY
    }

    private fun startStreaming() {
        val demo = settings.demoMode
        // Already streaming: only act if the demo/live mode actually changed, in which
        // case switch the data source live without tearing down the foreground service.
        // This is what makes the demo toggle work while streaming.
        if (running && demo == currentDemo) return

        if (!startForegroundInternal()) {
            running = false
            stopSelf()
            return
        }
        running = true
        currentDemo = demo

        HrRepository.setMaxHr(settings.effectiveMaxHr())
        HrRepository.resetSession()
        HrRepository.setDemo(demo)

        startSource(demo)
        startHeartbeat()
    }

    /** (Re)starts the active data source, cancelling whichever one was running before. */
    private fun startSource(demo: Boolean) {
        demoJob?.cancel()
        demoJob = null
        manager.stop()
        if (demo) {
            startDemo()
        } else {
            manager.start(settings.preferredDeviceAddress)
        }
    }

    /**
     * Periodically re-publishes so staleness is re-evaluated over time. Without this, a
     * band that stops broadcasting (out of range, dead battery) leaves the last number
     * frozen on screen until Android eventually reports the BLE disconnect.
     */
    private fun startHeartbeat() {
        if (heartbeatJob != null) return
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(HEARTBEAT_MS)
                HrRepository.refresh()
                WidgetUpdater.updateAll(this@HrService)
            }
        }
    }

    private fun stopStreaming() {
        running = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        demoJob?.cancel()
        demoJob = null
        manager.stop()
        HrRepository.setState(ConnectionState.DISCONNECTED)
        // Clear the last reading and session stats so every widget falls back to its
        // empty state ("--") instead of freezing on the final values.
        HrRepository.resetSession()
        WidgetUpdater.updateAll(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundInternal(): Boolean {
        val notification = buildNotification()
        return try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
            true
        } catch (e: SecurityException) {
            // The connectedDevice FGS type needs BLUETOOTH_CONNECT at call time; if the
            // permission was revoked before a sticky restart, bail out instead of crash-looping.
            Log.w(TAG, "startForeground rejected (permission revoked?)", e)
            false
        } catch (e: IllegalStateException) {
            // Covers ForegroundServiceStartNotAllowedException: the OS restarted us at a
            // moment it won't allow a foreground promotion.
            Log.w(TAG, "startForeground not allowed right now", e)
            false
        }
    }

    // ---- Demo mode: synthetic HR so the pipeline can be verified without a band ----

    private fun startDemo() {
        demoJob?.cancel()
        demoJob = scope.launch {
            val rnd = Random()
            var phase = 0.0
            HrRepository.setState(ConnectionState.CONNECTED)
            while (isActive) {
                val bpm = (100 + 40 * sin(phase)).toInt().coerceIn(50, 190)
                val baseRr = 60000 / bpm
                val rr = (baseRr + rnd.nextInt(41) - 20).coerceIn(300, 1500)
                onReading(HrReading(bpm, listOf(rr)))
                phase += 0.12
                delay(1000)
            }
        }
    }

    // ---- WhoopHrManager.Listener ----

    override fun onState(state: ConnectionState) {
        HrRepository.setState(state)
        pushUpdates()
    }

    override fun onReading(reading: HrReading) {
        HrRepository.addReading(reading)
        pushUpdates()
    }

    private fun pushUpdates() {
        // The notification is intentionally static (see buildNotification): Android requires
        // a foreground-service notification to keep the ~1/sec widget updates alive, but we
        // don't stream live BPM/HRV into it. So each reading only refreshes the widgets.
        WidgetUpdater.updateAll(this)
    }

    // ---- Notification ----

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Streaming",
            // MIN keeps it silent, out of the status bar, and collapsed in the shade —
            // the least intrusive form Android allows for a required foreground service.
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Required while RawPulse streams to your widgets"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * A deliberately static, minimal notification. Android requires an ongoing
     * notification for a foreground service, and the foreground service is what lets us
     * refresh the widgets ~1/sec (the OS widget refresh floor is 30 minutes). We keep it
     * quiet and free of live metrics so it isn't a second, distracting readout.
     */
    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, HrService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse)
            .setContentTitle("RawPulse")
            .setContentText("Streaming to your widgets")
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        demoJob?.cancel()
        manager.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "HrService"
        const val ACTION_START = "com.rawpulse.hr.START"
        const val ACTION_STOP = "com.rawpulse.hr.STOP"
        private const val CHANNEL_ID = "hr_streaming"
        private const val NOTIF_ID = 1001
        private const val HEARTBEAT_MS = 3000L

        fun start(context: Context) {
            val intent = Intent(context, HrService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, HrService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
