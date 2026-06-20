package ai.zasha.mlbbpicker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val tag = "BootReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(tag, "Boot completed received")
            val prefs = context.getSharedPreferences("mlbb_picker_prefs", Context.MODE_PRIVATE)
            val autoDetect = prefs.getBoolean("pref_auto_detect", true)
            
            // Check if auto-detect is enabled and overlay permission is granted
            if (autoDetect && Settings.canDrawOverlays(context)) {
                Log.d(tag, "Auto-detect is enabled, starting FloatingOverlayService")
                val serviceIntent = Intent(context, FloatingOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
