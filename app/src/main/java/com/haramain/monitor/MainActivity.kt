package com.haramain.monitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.haramain.monitor.data.SettingsStore
import com.haramain.monitor.service.MicMonitorService
import com.haramain.monitor.service.MonitorState
import com.haramain.monitor.ui.MonitorScreen
import com.haramain.monitor.ui.MonitorViewModel
import com.haramain.monitor.ui.theme.HaramainMonitorTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        /** Set by the mic-connected fallback notification (v3.3). */
        const val EXTRA_START_MONITORING = "com.haramain.monitor.extra.START_MONITORING"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleStartExtra(intent)
    }

    private fun handleStartExtra(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_START_MONITORING, false) == true) {
            try {
                ContextCompat.startForegroundService(this, MicMonitorService.startIntent(this))
            } catch (_: Exception) {}
        }
    }

    private val viewModel: MonitorViewModel by viewModels()
    private var micGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        refreshPermissionState()
        if (results[Manifest.permission.RECORD_AUDIO] == true) maybeResumeOnLaunch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        handleStartExtra(intent)
        super.onCreate(savedInstanceState)
        val settings = SettingsStore(this)
        refreshPermissionState()
        if (!micGranted) requestPermissions() else maybeResumeOnLaunch()
        setContent {
            val themeMode by settings.themeMode.collectAsState(initial = com.haramain.monitor.data.ThemeMode.SYSTEM)
            val themePalette by settings.themePalette.collectAsState(initial = com.haramain.monitor.data.ThemePalette.STUDIO)
            HaramainMonitorTheme(themeMode, themePalette) {
                MonitorScreen(
                    viewModel = viewModel,
                    micGranted = micGranted,
                    onRequestMicPermission = { requestPermissions() },
                )
            }
        }
    }

    /**
     * Resume-on-launch: if monitoring was live when the app process last died
     * (explicit stops clear the flag) and the user kept the setting on, bring
     * the monitor back without a tap.
     */
    private fun maybeResumeOnLaunch() {
        if (MonitorState.isRunning.value) return
        val settings = SettingsStore(this)
        lifecycleScope.launch {
            val resume = combine(settings.resumeOnLaunch, settings.wasRunning) { a, b -> a && b }
                .first()
            if (resume && micGranted) {
                ContextCompat.startForegroundService(
                    this@MainActivity,
                    Intent(this@MainActivity, MicMonitorService::class.java)
                        .setAction(MicMonitorService.ACTION_START),
                )
            }
        }
    }

    private fun refreshPermissionState() {
        micGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        val wanted = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            wanted += Manifest.permission.POST_NOTIFICATIONS
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            wanted += Manifest.permission.BLUETOOTH_CONNECT
        }
        permissionLauncher.launch(wanted.toTypedArray())
    }
}
