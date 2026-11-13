package com.haramain.monitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.haramain.monitor.audio.PresetKind
import com.haramain.monitor.audio.RouteDevice
import com.haramain.monitor.audio.AutoCalibrator
import com.haramain.monitor.audio.CalStats
import com.haramain.monitor.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.haramain.monitor.ui.components.PrecisionControl
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Main dashboard — dark mode first.
 * Top: hardware routing. Middle: monitor toggle, meters, telemetry.
 * Below: presets, then one PrecisionControl per DSP parameter.
 * Boots on the user's own Haramain (Original) preset (Room 95, Mix 52, Comp 70, De-Esser 50).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    micGranted: Boolean,
    onRequestMicPermission: () -> Unit,
) {
    val context = LocalContext.current
    val params by viewModel.params.collectAsState()
    val running by viewModel.isRunning.collectAsState()
    val inputs by viewModel.inputs.collectAsState()
    val outputs by viewModel.outputs.collectAsState()
    val selIn by viewModel.selectedInputId.collectAsState()
    val selOut by viewModel.selectedOutputId.collectAsState()
    val preset by viewModel.activePreset.collectAsState()
    val btOut by viewModel.outputIsBluetooth.collectAsState()
    val btIn by viewModel.inputIsBluetooth.collectAsState()
    val recordDry by viewModel.recordDry.collectAsState()
    val hasCustom by viewModel.hasCustom.collectAsState()
    val feedbackRisk by viewModel.feedbackRisk.collectAsState()
    val locked by viewModel.settingsLocked.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val hasPin by viewModel.hasPin.collectAsState()
    val resumeOnLaunch by viewModel.resumeOnLaunch.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingStartedAt by viewModel.recordingStartedAtMs.collectAsState()
    val controlsEnabled = !locked

    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isRecording) {
        while (isRecording) {
            nowMs = System.currentTimeMillis()
            delay(500)
        }
    }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showAutoDialog by remember { mutableStateOf(false) }
    var showMicManager by remember { mutableStateOf(false) }
    val displayInputs by viewModel.displayInputs.collectAsState()
    val hasAuto by viewModel.hasAuto.collectAsState()
    val autoPreset by viewModel.autoPreset.collectAsState()
    val exportFolderUri by viewModel.exportFolderUri.collectAsState()
    val openSections by viewModel.openSections.collectAsState()
    var pendingExport by remember { mutableStateOf<String?>(null) }
    val uiScope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val json = pendingExport
        pendingExport = null
        if (uri != null && json != null) {
            uiScope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)
                            ?.use { it.write(json.toByteArray()) }
                        true
                    } catch (_: Exception) { false }
                }
                Toast.makeText(
                    context,
                    if (ok) "Settings exported" else "Export failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            uiScope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)
                            ?.use { it.readBytes().decodeToString() }
                    } catch (_: Exception) { null }
                }
                val ok = text?.let { viewModel.importSettings(it) } ?: false
                Toast.makeText(
                    context,
                    if (ok) "Settings imported" else "That file isn't a Haramain Monitor settings export",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
    val folderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            } catch (_: Exception) {}
            viewModel.setExportFolder(uri.toString())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ---------- Header ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp)) {
                Text("Haramain Monitor", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        running && params.muted -> "🔇 MUTED — tap Unmute to hear yourself"
                        running -> "● LIVE — monitoring"
                        else -> "Live vocal monitoring • Oboe / AAudio"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        running && params.muted -> MaterialTheme.colorScheme.error
                        running -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        if (locked) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Settings are locked — controls are read-only. Start/Stop, Mute and Rec still work.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        if (!micGranted) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Microphone permission needed", fontWeight = FontWeight.Bold)
                    Text("Live monitoring can't start without it.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestMicPermission) { Text("Grant microphone access") }
                }
            }
        }

        // ---------- Routing bar ----------
        ExpandableSection(title = "Hardware Routing", expanded = openSections["routing"] ?: true, onExpandedChange = { viewModel.setSectionOpen("routing", it) }) {
            DevicePicker(
                label = "Input Mic",
                icon = Icons.Filled.Mic,
                devices = displayInputs,
                selectedId = selIn,
                onSelect = viewModel::selectInput,
                enabled = controlsEnabled,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showMicManager = true }, enabled = controlsEnabled) {
                Icon(Icons.Filled.Tune, contentDescription = null)
                Text("Name & organize mics", modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.height(8.dp))
            DevicePicker(
                label = "Output",
                icon = Icons.Filled.HeadsetMic,
                devices = outputs,
                selectedId = selOut,
                onSelect = viewModel::selectOutput,
                enabled = controlsEnabled,
            )
            if (feedbackRisk && params.safetyOn) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text(
                        "Speaker + built-in mic: feedback protection is capping output at −12 dBFS. " +
                            "Plug in headphones for full level.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (btOut) {
                Spacer(Modifier.height(12.dp))
                Text("Bluetooth Output", style = MaterialTheme.typography.titleSmall)
                if (btIn) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Text(
                            "A Bluetooth mic drops Android into 16 kHz call mode (HFP/SCO) and " +
                                "muffles everything. Split-path is better: hear on Bluetooth, " +
                                "record from the phone or USB mic.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { viewModel.usePhoneOrUsbMic() }, enabled = controlsEnabled) {
                        Text("Use phone / USB mic instead")
                    }
                } else {
                    Text(
                        "Split-path active: Bluetooth for your ears, phone/USB mic for your voice — full 48 kHz, no call-mode muffling.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    label = "BT Dry Monitor Ducking",
                    checked = params.btDuckOn,
                    onCheckedChange = { on -> viewModel.update { it.copy(btDuckOn = on) } },
                    enabled = controlsEnabled,
                )
                if (params.btDuckOn) {
                    PrecisionControl(
                        label = "Dry Duck Amount",
                        value = params.btDuckPercent, valueRange = 0f..100f, step = 1f,
                        defaultValue = 50f,
                        format = { pct(it) },
                        onValueChange = { v -> viewModel.update { it.copy(btDuckPercent = v) } },
                        enabled = controlsEnabled,
                    )
                    Text(
                        "Wireless latency makes your direct voice echo against itself. Ducking lowers only the dry voice — the Masjid reverb stays full.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ---------- Master control & telemetry ----------
        SectionCard {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.toggleMonitor(context) },
                    enabled = micGranted,
                    modifier = Modifier.weight(1f).height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (running) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Icon(if (running) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp, contentDescription = null)
                    Text(
                        if (running) "Stop Monitoring" else "Start Monitoring",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                if (params.muted) {
                    Button(
                        onClick = viewModel::toggleMute,
                        enabled = running,
                        modifier = Modifier.height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Filled.VolumeOff, contentDescription = null)
                        Text("UNMUTE", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    OutlinedButton(
                        onClick = viewModel::toggleMute,
                        enabled = running,
                        modifier = Modifier.height(64.dp),
                    ) {
                        Text("Mute")
                    }
                }
            }
            if (params.muted && running) {
                Spacer(Modifier.height(10.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VolumeOff, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
                            Text(
                                "MUTED — you can't hear yourself",
                                color = MaterialTheme.colorScheme.onError,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        TextButton(onClick = viewModel::toggleMute) {
                            Text("Unmute", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MetersPanel(viewModel)
        }

        // ---------- Recording ----------
        ExpandableSection(
            title = "Recording",
            headerBadge = if (isRecording) "● REC" else null,
            expanded = openSections["recording"] == true,
            onExpandedChange = { viewModel.setSectionOpen("recording", it) },
        ) {
            Text(
                when {
                    isRecording -> {
                        val secs = ((nowMs - recordingStartedAt) / 1000).coerceAtLeast(0)
                        String.format(Locale.US, "● REC %d:%02d — saving WAV", secs / 60, secs % 60)
                    }
                    running -> "Tap Rec to capture the processed audio (WAV)"
                    else -> "Start monitoring to record"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !recordDry,
                    onClick = { viewModel.setRecordDry(false) },
                    enabled = !isRecording,
                    label = { Text("Processed Mix") },
                )
                FilterChip(
                    selected = recordDry,
                    onClick = { viewModel.setRecordDry(true) },
                    enabled = !isRecording,
                    label = { Text("Dry Vocal") },
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.toggleRecording(context) },
                    enabled = running,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                    ),
                ) {
                    Icon(Icons.Filled.FiberManualRecord, contentDescription = null)
                    Text(if (isRecording) "Stop" else "Rec", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = {
                    context.startActivity(Intent(context, RecordingsActivity::class.java))
                }) {
                    Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                    Text("Library", modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        // ---------- Presets ----------
        ExpandableSection(title = "Presets", expanded = openSections["presets"] ?: true, onExpandedChange = { viewModel.setSectionOpen("presets", it) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showAutoDialog = true }, enabled = controlsEnabled) {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                        Text("Auto Setup", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = viewModel::saveCustom, enabled = controlsEnabled) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Text("Save Custom", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetKind.entries.forEach { kind ->
                    FilterChip(
                        selected = preset == kind,
                        enabled = controlsEnabled && (kind != PresetKind.CUSTOM || hasCustom || preset == PresetKind.CUSTOM),
                        onClick = { viewModel.selectPreset(kind) },
                        label = { Text(kind.label) },
                    )
                }
                if (hasAuto) {
                    val autoNow = autoPreset
                    FilterChip(
                        selected = autoNow != null && params.copy(muted = false) == autoNow.copy(muted = false),
                        enabled = controlsEnabled,
                        onClick = { viewModel.applySavedAuto() },
                        label = { Text("Auto") },
                    )
                }
            }
        }

        // ---------- DSP controls ----------
        ExpandableSection(title = "Vocal Chain", expanded = openSections["vocal"] == true, onExpandedChange = { viewModel.setSectionOpen("vocal", it) }) {
            Text(
                "Double-tap any control to reset it to its Haramain (Original) value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrecisionControl(
                label = "Mic Boost",
                value = params.micBoostPercent, valueRange = 0f..200f, step = 1f,
                defaultValue = 50f,
                format = { String.format(Locale.US, "%.0f%%  (+%.1f dB)", it, it / 100f * 12f) },
                onValueChange = { v -> viewModel.update { it.copy(micBoostPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Static Remover",
                value = params.staticRemover, valueRange = 0f..100f, step = 1f,
                defaultValue = 60f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(staticRemover = v) } },
                enabled = controlsEnabled,
            )
            ToggleRow(
                label = "Noise Suppression (Voice Isolation)",
                checked = params.noiseSuppressOn,
                onCheckedChange = { on -> viewModel.update { it.copy(noiseSuppressOn = on) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Suppression Depth",
                value = params.noiseSuppress, valueRange = 0f..100f, step = 1f,
                defaultValue = 40f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(noiseSuppress = v) } },
                enabled = controlsEnabled && params.noiseSuppressOn,
            )
            PrecisionControl(
                label = "Noise Gate Threshold",
                value = params.gateThresholdDb, valueRange = -80f..0f, step = 0.5f,
                defaultValue = -45f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(gateThresholdDb = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "De-Esser",
                value = params.deEsserPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 50f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(deEsserPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Compressor",
                value = params.compressorPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 70f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(compressorPercent = v) } },
                enabled = controlsEnabled,
            )
        }

        ExpandableSection(title = "Haramain Space", expanded = openSections["space"] == true, onExpandedChange = { viewModel.setSectionOpen("space", it) }) {
            PrecisionControl(
                label = "Reverb Room Size",
                value = params.roomSizePercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 95f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(roomSizePercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Reverb Wet / Dry Mix",
                value = params.wetDryPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 52f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(wetDryPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Stereo Balance",
                value = params.balancePercent, valueRange = -100f..100f, step = 1f,
                defaultValue = 0f,
                format = { balanceLabel(it) },
                onValueChange = { v -> viewModel.update { it.copy(balancePercent = v) } },
                showCenterSnap = true,
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Master Gain",
                value = params.masterGainDb, valueRange = -24f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(masterGainDb = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Bass Boost (Low Frequencies)",
                value = params.eqLowGainDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqLowGainDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
            PrecisionControl(
                label = "Treble Boost (High Frequencies)",
                value = params.eqHighGainDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqHighGainDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
            Spacer(Modifier.height(10.dp))
            Text("Vocal Tone Sculptor", style = MaterialTheme.typography.titleSmall)
            PrecisionControl(
                label = "Warmth (150 Hz)",
                value = params.eqWarmthDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqWarmthDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
            PrecisionControl(
                label = "Clarity (3 kHz)",
                value = params.eqClarityDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqClarityDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
            PrecisionControl(
                label = "Air (10 kHz)",
                value = params.eqAirDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqAirDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
        }

        // ---------- Safety ----------
        ExpandableSection(title = "Safety & Limiter", expanded = openSections["safety"] == true, onExpandedChange = { viewModel.setSectionOpen("safety", it) }) {
            ToggleRow(
                label = "Headphone Safety",
                checked = params.safetyOn,
                onCheckedChange = { on -> viewModel.update { it.copy(safetyOn = on) } },
                icon = Icons.Filled.NotificationsActive,
                enabled = controlsEnabled,
            )
            Text(
                if (params.safetyOn) {
                    "Ceiling −3 dBFS, plus a −12 dBFS cap while speaker + built-in mic could feed back. " +
                        "Turning safety off leaves only the −0.5 dB brickwall limiter."
                } else {
                    "Safety OFF — only the −0.5 dB brickwall limiter protects your ears."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrecisionControl(
                label = "Safety Ceiling",
                value = params.safetyCeilingDb, valueRange = -12f..0f, step = 0.5f,
                defaultValue = -3f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(safetyCeilingDb = v) } },
                enabled = controlsEnabled && params.safetyOn,
            )
        }

        // ---------- App settings ----------
        ExpandableSection(title = "App Settings", expanded = openSections["appsettings"] == true, onExpandedChange = { viewModel.setSectionOpen("appsettings", it) }) {
            Text("Theme", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { viewModel.setTheme(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            ToggleRow(
                label = "Resume monitoring when app reopens",
                checked = resumeOnLaunch,
                onCheckedChange = viewModel::setResume,
            )
            Spacer(Modifier.height(4.dp))
            Text("Recording folder", style = MaterialTheme.typography.titleSmall)
            Text(
                if (exportFolderUri.isBlank()) {
                    "Recordings stay in the app's private folder."
                } else {
                    "A copy of every finished recording is also saved to the folder you picked."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                    Text(if (exportFolderUri.isBlank()) "Choose folder…" else "Change folder")
                }
                if (exportFolderUri.isNotBlank()) {
                    OutlinedButton(onClick = { viewModel.setExportFolder("") }) { Text("Reset") }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (locked) {
                    Button(onClick = {
                        if (hasPin) showUnlockDialog = true
                        else viewModel.unlockSettings("") {}
                    }) {
                        Icon(Icons.Filled.LockOpen, contentDescription = null)
                        Text("Unlock settings", modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    Button(onClick = viewModel::lockSettings) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Text("Lock settings", modifier = Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = { showSetPinDialog = true }) {
                        Text(if (hasPin) "Change PIN" else "Set PIN (optional)")
                    }
                }
            }
            Text(
                "Locking makes every audio control read-only — handy when someone else holds the phone or it's in your pocket. " +
                    "The PIN is optional; without one, unlocking is one tap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text("Move to another device", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    uiScope.launch {
                        pendingExport = viewModel.buildExportJson()
                        exportLauncher.launch("haramain-settings.json")
                    }
                }) { Text("Export settings") }
                OutlinedButton(onClick = {
                    importLauncher.launch(arrayOf("application/json"))
                }) { Text("Import settings") }
            }
            Text(
                "Exports presets, mic names, theme and layout. The lock PIN and the recording folder are never exported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Tip: wired or USB-C output gives the lowest latency. Bluetooth adds 100 ms+ by design of the codec.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showMicManager) {
        MicManagerDialog(
            viewModel = viewModel,
            controlsEnabled = controlsEnabled,
            onDismiss = { showMicManager = false },
        )
    }

    if (showAutoDialog) {
        AutoSetupDialog(
            viewModel = viewModel,
            running = running,
            onDismiss = { showAutoDialog = false },
        )
    }

    if (showUnlockDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showUnlockDialog = false },
            title = { Text("Unlock settings") },
            text = {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.unlockSettings(pin) { ok ->
                        if (ok) showUnlockDialog = false
                        else Toast.makeText(context, "Wrong PIN", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Unlock") }
            },
            dismissButton = {
                TextButton(onClick = { showUnlockDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showSetPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetPinDialog = false },
            title = { Text("Set lock PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("New PIN (blank removes it)") },
                        singleLine = true,
                    )
                    Text(
                        "Stored only as a hash on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setPin(newPin.ifEmpty { null })
                    showSetPinDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showSetPinDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Collapsible section — closed by default so the screen stays calm; tap the
 * header to open. The master control card above stays a plain SectionCard
 * (Start/Stop and the meters are always visible).
 */
@Composable
private fun ExpandableSection(
    title: String,
    headerBadge: String? = null,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (headerBadge != null) {
                        Text(
                            headerBadge,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    )
                }
            }
            Column(
                Modifier.animateContentSize(
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                ),
            ) {
                if (expanded) {
                    Column(
                        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                            .graphicsLayer { alpha = 1f },
                    ) { content() }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.padding(horizontal = 4.dp))
            }
            Text(label, style = MaterialTheme.typography.titleSmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

private fun pitchNoteName(hz: Float): String {
    if (hz <= 0f) return "—"
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    val midi = (69.0 + 12.0 * (kotlin.math.ln(hz / 440.0) / kotlin.math.ln(2.0))).roundToInt()
    return names[((midi % 12) + 12) % 12] + (midi / 12 - 1).toString()
}

/**
 * Everything that updates at meter rate lives in this one composable, so
 * 20 fps telemetry recomposes ONLY here — never the screen, the sections,
 * or their expand/collapse animations.
 */
@Composable
private fun MetersPanel(viewModel: MonitorViewModel) {
    val running by viewModel.isRunning.collectAsState()
    val inDb by viewModel.inputPeakDb.collectAsState()
    val outDb by viewModel.outputPeakDb.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val latency by viewModel.latencyMs.collectAsState()
    val xRuns by viewModel.xRuns.collectAsState()
    val btOut by viewModel.outputIsBluetooth.collectAsState()
    val bufferMode by viewModel.bufferMode.collectAsState()
    val pitchOn by viewModel.pitchOn.collectAsState()
    val pitchHz by viewModel.pitchHz.collectAsState()

    LevelMeter("Input", inDb, running)
    LevelMeter("Output", outDb, running)
    Spacer(Modifier.height(8.dp))

    // Live audio-path telemetry pill.
    val pathLabel = when {
        !running -> "Idle"
        btOut -> "BT Split-Path (A2DP/LE)"
        else -> "Wired/USB Direct"
    }
    val pillText = if (running) {
        pathLabel + String.format(
            Locale.US, " • %.0f kHz • ~%.0f ms • %d xruns",
            sampleRate / 1000f, latency, xRuns,
        )
    } else "Start monitoring to see the live audio path"
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(pillText, style = MaterialTheme.typography.labelLarge)
    }
    Spacer(Modifier.height(10.dp))

    // Buffer / latency tuning (applies live, no service restart).
    Text("Buffer / Latency Tuning", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(1 to "Ultra-Low 1×", 2 to "Balanced 2×", 4 to "Stable 4×").forEach { (bursts, label) ->
            FilterChip(
                selected = bufferMode == bursts,
                onClick = { viewModel.setBufferMode(bursts) },
                label = { Text(label) },
            )
        }
    }
    Spacer(Modifier.height(6.dp))

    // Pitch indicator toggle + readout.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("Pitch indicator", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (pitchOn) {
                Text(
                    if (running && pitchHz > 0f) {
                        pitchNoteName(pitchHz) + String.format(Locale.US, " • %.0f Hz", pitchHz)
                    } else "Listening…",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 10.dp),
                )
            }
            Switch(checked = pitchOn, onCheckedChange = { viewModel.setPitchOn(it) })
        }
    }
}

@Composable
private fun LevelMeter(label: String, dbValue: Float, active: Boolean) {
    val fraction = ((dbValue.coerceIn(-60f, 0f) + 60f) / 60f)
    val animatedState = animateFloatAsState(targetValue = if (active) fraction else 0f, label = "meter$label")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.22f))
        val barColor = when {
            dbValue > -6f -> MaterialTheme.colorScheme.error
            dbValue > -18f -> MaterialTheme.colorScheme.secondary
            else -> MaterialTheme.colorScheme.primary
        }
        // The bar is painted in the DRAW phase (drawBehind reads the animated
        // value there), so 60 fps meter updates never recompose this row.
        Box(
            modifier = Modifier.weight(0.6f).height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawBehind {
                    val f = animatedState.value.coerceIn(0f, 1f)
                    if (f > 0f) drawRect(color = barColor, size = Size(size.width * f, size.height))
                },
        )
        Text(
            if (active) String.format(Locale.US, "%.0f dB", dbValue) else "— dB",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(0.18f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevicePicker(
    label: String,
    icon: ImageVector,
    devices: List<RouteDevice>,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedId }
    val display = selected?.let { "${it.kindLabel} — ${it.name}" } ?: "System Default"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            leadingIcon = { Icon(icon, contentDescription = null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("System Default") },
                onClick = { onSelect(-1); expanded = false },
            )
            devices.forEach { device ->
                DropdownMenuItem(
                    text = { Text("${device.kindLabel} — ${device.name}") },
                    onClick = { onSelect(device.id); expanded = false },
                )
            }
        }
    }
}

private fun pct(v: Float): String = "${v.roundToInt()}%"
private fun db(v: Float): String = String.format(Locale.US, "%.1f dB", v)
private fun balanceLabel(v: Float): String = when {
    v <= -99f -> "L 100%"
    v >= 99f -> "R 100%"
    v < -0.5f -> String.format(Locale.US, "L %.0f%%", -v)
    v > 0.5f -> String.format(Locale.US, "R %.0f%%", v)
    else -> "Center"
}

/**
 * Auto Setup wizard: (1) breathe, don't speak — measures the room;
 * (2) optional voice sample — measures the recitation. Guardrailed math in
 * [AutoCalibrator] turns the measurements into a preset the user can review
 * before applying.
 */
@Composable
private fun AutoSetupDialog(
    viewModel: MonitorViewModel,
    running: Boolean,
    onDismiss: () -> Unit,
) {
    var stage by remember { mutableStateOf(0) }
    var noise by remember { mutableStateOf<CalStats?>(null) }
    var voice by remember { mutableStateOf<CalStats?>(null) }
    var result by remember { mutableStateOf<AutoCalibrator.Result?>(null) }
    val scope = rememberCoroutineScope()
    val currentParams by viewModel.params.collectAsState()

    fun finish() {
        val n = noise ?: return
        result = AutoCalibrator.compute(currentParams, n, voice)
        stage = 4
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto Setup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (stage) {
                    0 -> {
                        if (!running) {
                            Text("Start monitoring first — with the mic and output route you'll actually use — then run Auto Setup.")
                        } else {
                            Text("Step 1: sit where you'll recite and breathe normally for 3 seconds. Don't speak — this measures your room and your breath.")
                            Text("Step 2 (optional): recite at your normal volume for 4 seconds, so boost and compression fit your voice.")
                            Text(
                                "Auto Setup can never push you into the limiter: boost is capped from your measured peaks with 6 dB of headroom, and suppression is capped at 60.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    1 -> {
                        Text("Listening to the room… breathe normally, don't speak.")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    2 -> {
                        Text("Room measured: floor ${"%.0f".format(noise?.rmsDb ?: -80f)} dB, breath peaks ${"%.0f".format(noise?.peakDb ?: -80f)} dB.")
                        Text("Now the voice step — or skip it and keep your current boost.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                stage = 3
                                scope.launch {
                                    voice = viewModel.measureStep(4200)
                                    finish()
                                }
                            }) { Text("Start voice step") }
                            TextButton(onClick = { finish() }) { Text("Skip") }
                        }
                    }
                    3 -> {
                        Text("Recite now, at your normal volume…")
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    4 -> {
                        val r = result
                        if (r != null) {
                            val p = r.params
                            Text(
                                "Boost ${"%.0f".format(p.micBoostPercent)}% • Gate ${"%.0f".format(p.gateThresholdDb)} dB • " +
                                    "Suppression ${"%.0f".format(p.noiseSuppress)} • Compressor ${"%.0f".format(p.compressorPercent)} • " +
                                    "De-Esser ${"%.0f".format(p.deEsserPercent)}",
                                fontWeight = FontWeight.Bold,
                            )
                            r.notes.forEach { note ->
                                Text(note, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Reverb and EQ keep your current flavor. Applying saves this as the Auto preset.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        Text("No audio was captured. Check that monitoring is running and the right mic is selected, then try again.")
                    }
                }
            }
        },
        confirmButton = {
            when (stage) {
                0 -> TextButton(
                    onClick = {
                        stage = 1
                        scope.launch {
                            noise = viewModel.measureStep(3200)
                            stage = if (noise != null) 2 else 5
                        }
                    },
                    enabled = running,
                ) { Text("Begin step 1") }
                4 -> TextButton(onClick = {
                    result?.let { viewModel.applyAuto(it) }
                    onDismiss()
                }) { Text("Apply") }
                else -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (stage == 4) "Discard" else "Cancel") }
        },
    )
}

/**
 * "Teach it once" mic manager: Android can't say which physical mic a
 * logical entry belongs to, so the user names entries (persisted by device
 * id) and hides useless duplicates. Select routes an entry live so, while
 * monitoring, tapping near a mic identifies it on the input meter.
 */
@Composable
private fun MicManagerDialog(
    viewModel: MonitorViewModel,
    controlsEnabled: Boolean,
    onDismiss: () -> Unit,
) {
    val rawInputs by viewModel.inputs.collectAsState()
    val storedLabels by viewModel.micLabels.collectAsState()
    val storedHidden by viewModel.hiddenMicIds.collectAsState()
    val selectedId by viewModel.selectedInputId.collectAsState()

    var labelDrafts by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var hiddenDrafts by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(rawInputs) {
        if (!initialized && rawInputs.isNotEmpty()) {
            labelDrafts = storedLabels
            hiddenDrafts = storedHidden
            initialized = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name & organize mics") },
        text = {
            Column {
                Text(
                    "One physical mic can appear several times (different Android source profiles). " +
                        "Start monitoring, tap Select on an entry, then tap near a mic — the input meter shows which one it is. " +
                        "Name it, and hide the duplicates you don't need.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(rawInputs, key = { it.id }) { device ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${device.kindLabel} — ${device.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (device.id == selectedId) {
                                        Text(
                                            "Currently selected",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { viewModel.selectInput(device.id) },
                                    enabled = controlsEnabled,
                                ) { Text("Select") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Bottom Mic", "Back Mic").forEach { base ->
                                    val tag = MIC_GROUP_PREFIX + base
                                    FilterChip(
                                        selected = (labelDrafts[device.id] ?: "") == tag,
                                        onClick = {
                                            labelDrafts = if ((labelDrafts[device.id] ?: "") == tag) {
                                                labelDrafts - device.id
                                            } else {
                                                labelDrafts + (device.id to tag)
                                            }
                                        },
                                        label = { Text(base) },
                                    )
                                }
                                if ((labelDrafts[device.id] ?: "").startsWith(MIC_GROUP_PREFIX)) {
                                    Text(
                                        "Numbered automatically",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 10.dp),
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = (labelDrafts[device.id] ?: "")
                                    .takeUnless { it.startsWith(MIC_GROUP_PREFIX) } ?: "",
                                onValueChange = { labelDrafts = labelDrafts + (device.id to it) },
                                label = { Text("Custom name (blank = original)") },
                                placeholder = { Text(device.name) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Hide from picker", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = device.id in hiddenDrafts,
                                    onCheckedChange = { on ->
                                        hiddenDrafts = if (on) hiddenDrafts + device.id else hiddenDrafts - device.id
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = {
                    viewModel.clearMicTeaching()
                    labelDrafts = emptyMap()
                    hiddenDrafts = emptySet()
                }) { Text("Reset all names & hidden") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                rawInputs.forEach { device ->
                    viewModel.setMicLabel(device.id, labelDrafts[device.id])
                    viewModel.setMicHidden(device.id, device.id in hiddenDrafts)
                }
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
