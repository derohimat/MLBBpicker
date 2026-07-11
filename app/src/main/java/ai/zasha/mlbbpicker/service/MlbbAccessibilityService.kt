package ai.zasha.mlbbpicker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class MlbbAccessibilityService : AccessibilityService() {
    private val tag = "MlbbAccessibility"

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString()

        if (packageName != null) {
            val isMlbb = packageName == "com.mobile.legends"
            val isOwnApp = packageName == this.packageName

            val intent = Intent(this, FloatingOverlayService::class.java).apply {
                action = if (isMlbb) {
                    FloatingOverlayService.ACTION_MLBB_FOREGROUND
                } else {
                    FloatingOverlayService.ACTION_MLBB_BACKGROUND
                }
                putExtra("isOwnApp", isOwnApp)
            }
            startService(intent)
        }
    }

    override fun onInterrupt() {
        Log.d(tag, "Accessibility Service Interrupted")
    }
}
