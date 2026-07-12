package com.pulsetile.hr.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pulsetile.hr.MainActivity
import com.pulsetile.hr.R
import com.pulsetile.hr.ble.WhoopHrManager
import com.pulsetile.hr.data.ConnectionState
import com.pulsetile.hr.data.HrReading
import com.pulsetile.hr.data.HrRepository
import com.pulsetile.hr.data.MetricsSnapshot
import com.pulsetile.hr.data.Settings
import com.pulsetile.hr.widget.WidgetUpdater
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
    private var running = false

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
        if (running) return
        running = true

        HrRepository.setMaxHr(settings.effectiveMaxHr())
        HrRepository.resetSession()
        val demo = settings.demoMode
        HrRepository.setDemo(demo)

        startForegroundInternal()

        if (demo) {
            startDemo()
        } else {
            manager.start(settings.preferredDeviceAddress)
        }
    }

    private fun stopStreaming() {
        running = false
        demoJob?.cancel()
        demoJob = null
        manager.stop()
        HrRepository.setState(ConnectionState.DISCONNECTED)
        WidgetUpdater.updateAll(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    private fun startForegroundInternal() {
        val notification = buildNotification(HrRepository.current())
        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
        )
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
        val snap = HrRepository.current()
        notificationManager.notify(NOTIF_ID, buildNotification(snap))
        WidgetUpdater.updateAll(this)
    }

    // ---- Notification ----

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Heart-rate streaming",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ongoing while PulseTile streams your WHOOP heart rate"
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun buildNotification(snap: MetricsSnapshot): Notification {
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

        val title = when {
            snap.demo && snap.bpm != null -> "${snap.bpm} BPM (demo)"
            snap.state == ConnectionState.CONNECTED && !snap.stale && snap.bpm != null -> "${snap.bpm} BPM"
            snap.state == ConnectionState.CONNECTED -> "Connected"
            snap.state == ConnectionState.SCANNING -> "Searching for your WHOOP…"
            snap.state == ConnectionState.CONNECTING -> "Connecting…"
            else -> "Not streaming"
        }
        val text = snap.hrvMs?.let { "HRV $it ms" } ?: "Streaming heart rate"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_heart)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        demoJob?.cancel()
        manager.stop()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.pulsetile.hr.START"
        const val ACTION_STOP = "com.pulsetile.hr.STOP"
        private const val CHANNEL_ID = "hr_streaming"
        private const val NOTIF_ID = 1001

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
