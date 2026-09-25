package ai.zasha.mlbbpicker.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class FloatingOverlayService : Service() {

    private val tag = "FloatingOverlayService"
    private val channelId = "mlbb_picker_service_channel"
    private val notificationId = 101

    private lateinit var overlayViewManager: OverlayViewManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        var isRunning = false
        var isOverlayVisible = false
        const val ACTION_SHOW_OVERLAY = "ai.zasha.mlbbpicker.SHOW_OVERLAY"
        const val ACTION_MLBB_FOREGROUND = "ai.zasha.mlbbpicker.MLBB_FOREGROUND"
        const val ACTION_MLBB_BACKGROUND = "ai.zasha.mlbbpicker.MLBB_BACKGROUND"
        const val ACTION_TOGGLE_OVERLAY = "ai.zasha.mlbbpicker.TOGGLE_OVERLAY"
        const val ACTION_SHOW_BANS = "ai.zasha.mlbbpicker.SHOW_BANS"
        const val PANEL_TAB_BANS = 2
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service onCreate")
        isRunning = true
        overlayViewManager = OverlayViewManager(this)
        createNotificationChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "Service onStartCommand, action: ${intent?.action}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(notificationId, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(notificationId, createNotification())
        }

        val prefs = getSharedPreferences("mlbb_picker_prefs", MODE_PRIVATE)
        val autoDetect = prefs.getBoolean("pref_auto_detect", true)
        val autoHide = prefs.getBoolean("pref_auto_hide", true)

        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                overlayViewManager.showOverlay(byUserTrigger = true)
            }

            ACTION_TOGGLE_OVERLAY -> {
                overlayViewManager.toggleOverlay()
            }

            ACTION_SHOW_BANS -> {
                overlayViewManager.showPanelOnTab(PANEL_TAB_BANS)
            }

            ACTION_MLBB_FOREGROUND -> {
                if (autoDetect) {
                    overlayViewManager.showOverlay(byUserTrigger = false)
                }
            }

            ACTION_MLBB_BACKGROUND -> {
                if (autoDetect) {
                    val isOwnApp = intent.getBooleanExtra("isOwnApp", false)
                    if (isOwnApp) {
                        overlayViewManager.hideOverlay(manually = false)
                    } else if (autoHide) {
                        overlayViewManager.hideOverlay(manually = false)
                    }
                }
            }

            else -> {
                // Initial start
                overlayViewManager.showOverlay(byUserTrigger = true)
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(tag, "Service onDestroy")
        isRunning = false
        serviceScope.cancel()
        overlayViewManager.hideOverlay(manually = false)
        isOverlayVisible = false
        OverlayTiles.refresh(this)
        super.onDestroy()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
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
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun createNotification(): Notification {
        val showOverlayIntent = Intent(this, FloatingOverlayService::class.java).apply {
            action = ACTION_SHOW_OVERLAY
        }
        val pendingIntent = PendingIntent.getService(
            this, 
            0, 
            showOverlayIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("MLBB Picker Overlay Active")
            .setContentText("The floating draft assistant is ready. Tap to show menu.")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        return builder.build()
    }
}
