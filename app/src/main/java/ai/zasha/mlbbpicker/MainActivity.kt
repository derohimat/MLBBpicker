package ai.zasha.mlbbpicker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ai.zasha.mlbbpicker.data.HeroRepository
import ai.zasha.mlbbpicker.data.MetaStatsRepository
import ai.zasha.mlbbpicker.service.FloatingOverlayService
import ai.zasha.mlbbpicker.theme.MLBBPickerTheme
import ai.zasha.mlbbpicker.ui.main.MainScreen
import ai.zasha.mlbbpicker.ui.main.MainScreenViewModel

class MainActivity : ComponentActivity() {

    private lateinit var repository: HeroRepository
    private lateinit var metaStatsRepository: MetaStatsRepository
    private val viewModel: MainScreenViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainScreenViewModel(repository, metaStatsRepository) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = HeroRepository(applicationContext)
        metaStatsRepository = MetaStatsRepository(applicationContext)

        enableEdgeToEdge()
        setContent {
            MLBBPickerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onItemClick = {},
                        viewModel = viewModel,
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onRequestUsagePermission = { requestUsagePermission() },
                        onToggleService = { toggleService() },
                        onToggleAutoDetect = { enabled -> toggleAutoDetect(enabled) },
                        onToggleAutoHide = { enabled -> toggleAutoHide(enabled) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateViewModelStatus()
    }

    private fun updateViewModelStatus() {
        val overlayGranted = checkOverlayPermission()
        val usageGranted = checkUsageStatsPermission()
        val serviceRunning = FloatingOverlayService.isRunning

        val prefs = getSharedPreferences("mlbb_picker_prefs", Context.MODE_PRIVATE)
        val autoDetect = prefs.getBoolean("pref_auto_detect", true)
        val autoHide = prefs.getBoolean("pref_auto_hide", true)

        viewModel.updateStatus(
            isServiceRunning = serviceRunning,
            isDrawOverlayGranted = overlayGranted,
            isUsageStatsGranted = usageGranted,
            autoDetectEnabled = autoDetect,
            autoHideEnabled = autoHide
        )
    }

    private fun checkOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun checkUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestOverlayPermission() {
        if (!checkOverlayPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }
    }

    private fun requestUsagePermission() {
        if (!checkUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun toggleService() {
        if (FloatingOverlayService.isRunning) {
            stopService(Intent(this, FloatingOverlayService::class.java))
            updateViewModelStatus()
            Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show()
        } else {
            if (!checkOverlayPermission()) {
                Toast.makeText(this, "Please grant Overlay Permission first!", Toast.LENGTH_LONG).show()
                requestOverlayPermission()
                return
            }
            val intent = Intent(this, FloatingOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            updateViewModelStatus()
            Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAutoDetect(enabled: Boolean) {
        val prefs = getSharedPreferences("mlbb_picker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_auto_detect", enabled).apply()
        updateViewModelStatus()
    }

    private fun toggleAutoHide(enabled: Boolean) {
        val prefs = getSharedPreferences("mlbb_picker_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("pref_auto_hide", enabled).apply()
        updateViewModelStatus()
    }
}
