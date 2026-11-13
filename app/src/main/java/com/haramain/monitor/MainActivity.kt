package com.haramain.monitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.haramain.monitor.ui.MonitorScreen
import com.haramain.monitor.ui.MonitorViewModel
import com.haramain.monitor.ui.theme.HaramainMonitorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MonitorViewModel by viewModels()
    private var micGranted by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> refreshPermissionState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermissionState()
        if (!micGranted) requestPermissions()
        setContent {
            HaramainMonitorTheme {
                MonitorScreen(
                    viewModel = viewModel,
                    micGranted = micGranted,
                    onRequestMicPermission = { requestPermissions() },
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
