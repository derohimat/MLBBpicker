package ai.zasha.mlbbpicker.service

import ai.zasha.mlbbpicker.MainActivity
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import androidx.core.content.ContextCompat

/** Shared helpers for the Quick Settings tiles that control the floating overlay. */
object OverlayTiles {

    private const val TAG = "OverlayTiles"

    /** Ask SystemUI to re-query both tiles so their state matches the overlay. */
    fun refresh(context: Context) {
        listOf(PickerTileService::class.java, BanTileService::class.java).forEach { cls ->
            try {
                TileService.requestListeningState(context, ComponentName(context, cls))
            } catch (e: Exception) {
                Log.w(TAG, "requestListeningState failed for ${cls.simpleName}", e)
            }
        }
    }

    /** Deliver [action] to the overlay service, starting it (or the app) if needed. */
    fun sendOverlayAction(tileService: TileService, action: String) {
        val intent = Intent(tileService, FloatingOverlayService::class.java).setAction(action)

        if (FloatingOverlayService.isRunning) {
            tileService.startService(intent)
            return
        }

        if (!Settings.canDrawOverlays(tileService)) {
            openApp(tileService)
            return
        }

        try {
            ContextCompat.startForegroundService(tileService, intent)
        } catch (e: Exception) {
            // Background FGS start can be blocked on newer Android; let the app start it instead
            Log.w(TAG, "Could not start overlay service from tile", e)
            openApp(tileService)
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(tileService: TileService) {
        val intent = Intent(tileService, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                tileService,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            tileService.startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            tileService.startActivityAndCollapse(intent)
        }
    }

    fun updateTile(tile: Tile?, active: Boolean, subtitle: String) {
        tile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = subtitle
        }
        tile.updateTile()
    }
}
