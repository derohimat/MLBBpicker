package ai.zasha.mlbbpicker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class FloatingOverlayService : Service() {

    private val tag = "FloatingOverlayService"
    private val channelId = "mlbb_picker_service_channel"
    private val notificationId = 101

    private lateinit var overlayViewManager: OverlayViewManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var trackingJob: Job? = null

    companion object {
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service onCreate")
        isRunning = true
        overlayViewManager = OverlayViewManager(this)
        createNotificationChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Service onStartCommand")
        startForeground(notificationId, createNotification())

        // Show overlay immediately on manual start
        overlayViewManager.showOverlay()

        // Start checking for MLBB in foreground
        startForegroundAppTracking()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(tag, "Service onDestroy")
        isRunning = false
        trackingJob?.cancel()
        serviceScope.cancel()
        overlayViewManager.hideOverlay()
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun startForegroundAppTracking() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                try {
                    val prefs = getSharedPreferences("mlbb_picker_prefs", MODE_PRIVATE)
                    val autoDetect = prefs.getBoolean("pref_auto_detect", true)
                    val autoHide = prefs.getBoolean("pref_auto_hide", true)

                    if (autoDetect) {
                        val foregroundApp = getForegroundPackageName()
                        Log.d(tag, "Current foreground app: $foregroundApp")
                        val isMlbb = foregroundApp == "com.mobile.legends"

                        if (isMlbb) {
                            overlayViewManager.showOverlay()
                        } else {
                            if (autoHide) {
                                overlayViewManager.hideOverlay()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error in tracking loop", e)
                }
                delay(2500)
            }
        }
    }

    private fun getForegroundPackageName(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        // Query last 10 seconds of usage events
        val events = usageStatsManager.queryEvents(time - 1000 * 10, time)
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "MLBB Picker Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the MLBB Draft Overlay helper active."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MLBB Picker Overlay Active")
            .setContentText("The floating draft assistant is ready to help.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        return builder.build()
    }
}
