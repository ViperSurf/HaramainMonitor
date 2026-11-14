package com.haramain.monitor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.input.pointer.pointerInput
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
import com.haramain.monitor.ui.components.InfoTip
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
    val autoStopMinutes by viewModel.autoStopMinutes.collectAsState()
    val autoRecord by viewModel.autoRecord.collectAsState()
    val autoTrim by viewModel.autoTrim.collectAsState()
    val voiceActivated by viewModel.voiceActivated.collectAsState()
    val autoStartMic by viewModel.autoStartMic.collectAsState()
    val stopOnDisconnect by viewModel.stopOnDisconnect.collectAsState()
    val automationScope by viewModel.automationScope.collectAsState()
    val practiceSummary by viewModel.practiceSummary.collectAsState()
    val practiceGoalMin by viewModel.practiceGoalMin.collectAsState()
    val perMicMemory by viewModel.perMicMemory.collectAsState()
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
        ExpandableSection(title = "Hardware Routing", expanded = openSections["routing"] ?: true, onExpandedChange = { viewModel.setSectionOpen("routing", it) }, help = "Choose exactly which mic records you and which speaker or earbuds you hear. If a Bluetooth mic is offered, the phone or USB mic with Bluetooth output sounds far better: Bluetooth mics send your voice through a low-quality call channel.") {
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
            Spacer(Modifier.height(10.dp))
            Text("Monitor Ears", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                InfoTip("Monitor Ears", "Stereo is normal. Left Only or Right Only sends your voice (summed mono) to just that ear — for leading, so the other ear stays open to the room. Recordings are always full stereo.")
                listOf(0 to "Stereo", 1 to "Left Only", 2 to "Right Only").forEach { (mode, label) ->
                    FilterChip(
                        selected = params.monitorMode == mode,
                        onClick = { viewModel.update { p -> p.copy(monitorMode = mode) } },
                        label = { Text(label) },
                        enabled = controlsEnabled,
                    )
                }
            }
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
                    help = "For Bluetooth earbuds: softens your delayed direct voice so it stops fighting itself. The amount is Dry Duck Amount, just below.",
                    checked = params.btDuckOn,
                    onCheckedChange = { on -> viewModel.update { it.copy(btDuckOn = on) } },
                    enabled = controlsEnabled,
                )
                if (params.btDuckOn) {
                    PrecisionControl(
                        label = "Dry Duck Amount",
                        help = "On Bluetooth your direct voice arrives late and echoes against itself. Ducking lowers only the direct voice; the reverb stays full, so the space still feels alive.",
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

        // ---------- A/B compare ----------
        var abHeld by remember { mutableStateOf(false) }
        DisposableEffect(Unit) { onDispose { viewModel.setBypass(false) } }
        Surface(
            modifier = Modifier.fillMaxWidth().height(50.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        abHeld = true
                        viewModel.setBypass(true)
                        tryAwaitRelease()
                        abHeld = false
                        viewModel.setBypass(false)
                    })
                },
            shape = RoundedCornerShape(25.dp),
            color = if (abHeld) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (abHeld) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (abHeld) "RAW MIC — release to return" else "Hold to hear raw mic (A/B compare)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------- Recording ----------
        ExpandableSection(
            help = "Processed Mix records exactly what you hear, space and all. Dry Vocal records the untouched microphone, best for hearing your real voice or editing later. Recordings are WAV; the Library can share them or export M4A.",
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
            ToggleRow(
                label = "Voice-activated recording",
                checked = voiceActivated,
                onCheckedChange = viewModel::setVoiceActivated,
                help = "Recording starts when your voice crosses the Noise Gate threshold and stops after 4 seconds of quiet, so each take is saved as its own file. It follows your gate setting exactly, so raise or lower the gate to change what counts as voice. Turning this on turns Record Automatically off, and the other way round.",
            )
            ToggleRow(
                label = "Record automatically",
                checked = autoRecord,
                onCheckedChange = viewModel::setAutoRecord,
                help = "Starts recording by itself whenever monitoring starts, using the source above. Turning it on while already monitoring starts a recording right away. Auto-stop never interrupts a recording. Pairs well with Trim in the Library: record hands-free, then cut the dead air off both ends of each take with one tap.",
            )
            ToggleRow(
                label = "Auto-trim finished recordings",
                checked = autoTrim,
                onCheckedChange = viewModel::setAutoTrim,
                help = "After each recording finishes, the edge silence is trimmed automatically in the background and saved as a second file with _trimmed at the end of its name. The original recording is never changed. Works with Record automatically and voice-activated recording too.",
            )
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
        ExpandableSection(title = "Presets", expanded = openSections["presets"] ?: true, onExpandedChange = { viewModel.setSectionOpen("presets", it) }, help = "Ready-made spaces. Tap one to load it; moving any slider afterwards turns your sound into Custom. Auto Setup measures your room and your voice and builds a preset for you.") {
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
            Spacer(Modifier.height(10.dp))
            Text("About this space", style = MaterialTheme.typography.titleSmall)
            val autoNowPanel = autoPreset
            val autoSelected = autoNowPanel != null && params.copy(muted = false) == autoNowPanel.copy(muted = false)
            Text(
                if (autoSelected) {
                    "Auto — built by Auto Setup from the sound of your room and your voice."
                } else when (preset) {
                    PresetKind.HARAMAIN -> "Masjid al-Haram, Makkah: the vast marble hall. Bright, expansive reflections that carry a voice a long way — the fullest space in the app, and the default."
                    PresetKind.NABAWI -> "Masjid an-Nabawi, Madinah: warm domes and carpet soften the echo — rounder, gentler and a little more intimate than al-Haram."
                    PresetKind.STONE_DOME -> "Historic Stone Dome: an old stone prayer hall. Clear, focused reflections off hard walls, medium in size."
                    PresetKind.LOCAL_HALL -> "Local Masjid Hall: a carpeted community masjid. Modest warmth, short reflections, close and familiar."
                    PresetKind.LEAD -> "Lead (Imam): voiced for leading prayer. The voice stays in front, steady and clear, with the space underneath it rather than around it. Pairs well with single-ear monitoring in Hardware Routing."
                    PresetKind.STUDIO -> "Studio Dry: no room at all — your voice exactly as the mic hears it. Best for checking your real sound and for clean recordings."
                    PresetKind.SIMPLE -> "Simple Studio: a whisper of room and a light polish. Clean and natural, like a good small studio."
                    PresetKind.CUSTOM -> "Custom: your own settings live here. Save Custom keeps them; loading a preset replaces them."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        // ---------- DSP controls ----------
        ExpandableSection(title = "Vocal Chain", expanded = openSections["vocal"] == true, onExpandedChange = { viewModel.setSectionOpen("vocal", it) }, help = "Your voice passes through these stages in order: Boost, Static Remover, Gate, Suppression, De-Esser, Compressor, then into the space. The Activity lines under the meters show every stage working — including the quiet ones you can't hear.") {
            Text(
                "Double-tap any control to reset it to its Haramain (Original) value.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrecisionControl(
                label = "Mic Boost",
                help = "Raises the mic level before everything else. Turn it up for a quiet or distant mic; down if the meter slams into the red.",
                value = params.micBoostPercent, valueRange = 0f..200f, step = 1f,
                defaultValue = 50f,
                format = { String.format(Locale.US, "%.0f%%  (+%.1f dB)", it, it / 100f * 12f) },
                onValueChange = { v -> viewModel.update { it.copy(micBoostPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Static Remover",
                help = "A high-pass filter plus hiss softening. It strips rumble, handling noise and airy hiss, mostly during your quiet moments. You hear this one by its absence — clean pauses — never as a change to your voice.",
                value = params.staticRemover, valueRange = 0f..100f, step = 1f,
                defaultValue = 60f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(staticRemover = v) } },
                enabled = controlsEnabled,
            )
            ToggleRow(
                label = "Noise Suppression (Voice Isolation)",
                help = "Learns the steady sounds of your room (fan, AC, hum) over about a second of quiet and lowers them while leaving your voice alone. Watch it on the Activity line under the meters.",
                checked = params.noiseSuppressOn,
                onCheckedChange = { on -> viewModel.update { it.copy(noiseSuppressOn = on) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Suppression Depth",
                help = "How strongly the learned room noise is pushed down in the gaps between phrases (up to about 16 dB at 100). It never touches your voice while you speak, so in a quiet room there is almost nothing to hear — watch the Suppression % on the Activity line while a fan or AC runs.",
                value = params.noiseSuppress, valueRange = 0f..100f, step = 1f,
                defaultValue = 40f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(noiseSuppress = v) } },
                enabled = controlsEnabled && params.noiseSuppressOn,
            )
            PrecisionControl(
                label = "Noise Gate Threshold",
                help = "Anything quieter than this is silenced, so the gaps between phrases go truly quiet. Set it just above your room's background noise.",
                value = params.gateThresholdDb, valueRange = -80f..0f, step = 0.5f,
                defaultValue = -45f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(gateThresholdDb = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "De-Esser",
                help = "Softens harsh 's' and 'sh' sounds so they don't pierce, especially on bright phone mics. It only bites for a split second on strong sibilance — watch the De-esser readout under the meters while you repeat 's' sounds.",
                value = params.deEsserPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 50f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(deEsserPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Compressor",
                help = "Evens out your volume: soft words come up, loud peaks come down, so the level feels steady without riding the gain. You won't hear it switch on — watch the Compressor readout under the meters: it shows how many dB it's holding back right now.",
                value = params.compressorPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 70f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(compressorPercent = v) } },
                enabled = controlsEnabled,
            )
        }

        ExpandableSection(title = "Haramain Space", expanded = openSections["space"] == true, onExpandedChange = { viewModel.setSectionOpen("space", it) }, help = "The room your voice sits in, plus tone shaping. Room Size and Mix shape the space itself; Balance, Master Gain, Bass, Treble and the Vocal Tone Sculptor shape the sound.") {
            PrecisionControl(
                label = "Reverb Room Size",
                help = "How large the space feels. Bigger means longer, more spacious reflections.",
                value = params.roomSizePercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 95f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(roomSizePercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Reverb Wet / Dry Mix",
                help = "How much of the space is blended into your voice. Low is intimate; high washes the voice in the room.",
                value = params.wetDryPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 52f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(wetDryPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Reverb Damping",
                help = "How quickly the reverb tail loses its brightness. Low = bright, splashy reflections; high = a softer, darker, more natural tail.",
                value = params.revDampingPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 50f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(revDampingPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Reverb Width",
                help = "Stereo width of the reverb field only (your voice stays centered). Lower narrows the space toward mono; 100 is the full original width.",
                value = params.revWidthPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 100f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(revWidthPercent = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Stereo Balance",
                help = "Places your voice between the left and right ears.",
                value = params.balancePercent, valueRange = -100f..100f, step = 1f,
                defaultValue = 0f,
                format = { balanceLabel(it) },
                onValueChange = { v -> viewModel.update { it.copy(balancePercent = v) } },
                showCenterSnap = true,
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Master Gain",
                help = "The final output volume, after all processing.",
                value = params.masterGainDb, valueRange = -24f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(masterGainDb = v) } },
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "Bass Boost (Low Frequencies)",
                help = "A broad low-end shelf. Adds body; too much turns boomy.",
                value = params.eqLowGainDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqLowGainDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
            PrecisionControl(
                label = "Treble Boost (High Frequencies)",
                help = "A broad high-end shelf. Adds brightness and detail; too much turns harsh.",
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
                help = "Body and chest in the voice. Lift for a fuller sound; cut if it turns muddy.",
                value = params.eqWarmthDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqWarmthDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
            PrecisionControl(
                label = "Clarity (3 kHz)",
                help = "Presence and intelligibility, where the ear is most sensitive. Lift to cut through; cut if it pierces.",
                value = params.eqClarityDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqClarityDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
            PrecisionControl(
                label = "Air (10 kHz)",
                help = "The breathy sparkle at the very top. A little feels open and live; too much adds hiss.",
                value = params.eqAirDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqAirDb = v) } },
                enabled = controlsEnabled,
                showCenterSnap = true,
            )
        }

        // ---------- Graphic EQ ----------
        ExpandableSection(title = "Graphic EQ", expanded = openSections["geq"] == true, onExpandedChange = { viewModel.setSectionOpen("geq", it) }, help = "Five fixed bands that shape your voice BEFORE it enters the space. Cut 250 Hz to remove boxiness from a small room; lift 4 kHz for clarity; 60 Hz is mostly rumble — cutting it cleans recordings. New to EQ? The Bass and Treble sliders in Haramain Space cover most of this in simpler terms.") {
            PrecisionControl(
                label = "60 Hz — Rumble",
                value = params.eqBand60Db, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqBand60Db = v) } },
                showCenterSnap = true,
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "250 Hz — Warmth / Boxiness",
                value = params.eqBand250Db, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqBand250Db = v) } },
                showCenterSnap = true,
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "1 kHz — Body",
                value = params.eqBand1kDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqBand1kDb = v) } },
                showCenterSnap = true,
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "4 kHz — Clarity",
                value = params.eqBand4kDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqBand4kDb = v) } },
                showCenterSnap = true,
                enabled = controlsEnabled,
            )
            PrecisionControl(
                label = "12 kHz — Air",
                value = params.eqBand12kDb, valueRange = -12f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(eqBand12kDb = v) } },
                showCenterSnap = true,
                enabled = controlsEnabled,
            )
        }

        // ---------- Safety ----------
        ExpandableSection(title = "Safety & Limiter", expanded = openSections["safety"] == true, onExpandedChange = { viewModel.setSectionOpen("safety", it) }) {
            ToggleRow(
                label = "Headphone Safety",
                help = "A hard ceiling on loudness so nothing can clip or blast your ears. Leave it on unless you know exactly why you don't want it.",
                checked = params.safetyOn,
                onCheckedChange = { on -> viewModel.update { it.copy(safetyOn = on) } },
                icon = Icons.Filled.NotificationsActive,
                enabled = controlsEnabled,
            )
            ToggleRow(
                label = "Feedback Guard (Anti-Howl)",
                help = "Listens for the rising ring of speaker feedback and cuts a narrow notch at exactly that frequency before it becomes a howl. Built for leading or practicing on a speaker; harmless on headphones. An active notch shows in the Activity line and lifts itself after about 12 quiet seconds.",
                checked = params.howlGuardOn,
                onCheckedChange = { on -> viewModel.update { it.copy(howlGuardOn = on) } },
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
                help = "The loudest the output may ever get while Headphone Safety is on. -3 dB is the sane default.",
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
                help = "If the app is closed while monitoring, or the phone restarts, monitoring picks up again by itself. Auto-stop still applies.",
                checked = resumeOnLaunch,
                onCheckedChange = viewModel::setResume,
            )
            Spacer(Modifier.height(10.dp))
            Text("Auto-stop when quiet", style = MaterialTheme.typography.titleSmall)
            Text(
                "Stops monitoring by itself after this long with no voice, only room sound — so a forgotten session can't drain the battery or leave the mic hot. A recording in progress is never stopped.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 5, 10, 15, 30).forEach { m ->
                    FilterChip(
                        selected = autoStopMinutes == m,
                        onClick = { viewModel.setAutoStopMinutes(m) },
                        label = { Text(if (m == 0) "Off" else "$m min") },
                    )
                }
            }
            ToggleRow(
                label = "Remember settings per microphone",
                checked = perMicMemory,
                onCheckedChange = viewModel::setPerMicMemory,
                help = "Each microphone keeps its own sound: boost, gate, preset, everything. Switch mics and the app saves the sound you are leaving and restores the one you saved for the mic you picked. Best when your USB mic and phone mic need very different settings.",
            )
            ToggleRow(
                label = "Auto-start when my mic connects",
                help = "Starts monitoring automatically when your selected microphone appears (for example when you plug in the USB mic). Works while the app is open or still in memory. If Android blocks the start while the app is in the background, you get a notification to tap instead.",
                checked = autoStartMic,
                onCheckedChange = viewModel::setAutoStartMic,
            )
            ToggleRow(
                label = "Stop when my mic or output disconnects",
                help = "If the microphone or headphones you routed vanish mid-session (USB unplugged, Bluetooth died), monitoring stops after a 3 second grace instead of falling back to another device. Made for leading: a dropped mic should never silently become the phone mic in your pocket.",
                checked = stopOnDisconnect,
                onCheckedChange = viewModel::setStopOnDisconnect,
            )
            Spacer(Modifier.height(6.dp))
            Text("Auto start/stop applies to", style = MaterialTheme.typography.titleSmall)
            Text(
                "Which connection the two switches above watch. 'This mic' means the microphone actually in use, even on the system-default route.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "This mic", 1 to "Any external", 2 to "Bluetooth", 3 to "USB").forEach { (s, label) ->
                    FilterChip(
                        selected = automationScope == s,
                        onClick = { viewModel.setAutomationScope(s) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Practice log", style = MaterialTheme.typography.titleSmall)
            Text(
                practiceSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text("Daily practice goal", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(0, 5, 10, 15, 20, 30).forEach { m ->
                    FilterChip(
                        selected = practiceGoalMin == m,
                        onClick = { viewModel.setPracticeGoalMin(m) },
                        label = { Text(if (m == 0) "Off" else "$m min") },
                    )
                }
            }
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
    help: String? = null,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (help != null) InfoTip(title, help)
                }
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
    help: String? = null,
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
            if (help != null) InfoTip(label, help)
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
    val actGate by viewModel.actGate.collectAsState()
    val actSuppress by viewModel.actSuppress.collectAsState()
    val actLimiter by viewModel.actLimiter.collectAsState()
    val actNotchHz by viewModel.actNotchHz.collectAsState()
    val sessionStartMs by viewModel.sessionStartMs.collectAsState()
    val bufferFrames by viewModel.bufferFrames.collectAsState()

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
        ) + if (bufferFrames > 0) " • buf ${bufferFrames}f · ${bufferMode}×" else ""
    } else "Start monitoring to see the live audio path"
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(pillText, style = MaterialTheme.typography.labelLarge)
    }
    if (running) {
        // Processing activity: how hard each stage is working right now
        // (gain reduction %). Makes the subtle stages visible: stay silent
        // and Gate pins at 100%; add steady noise and Suppression moves;
        // shout with high boost and the Limiter engages.
        Text(
            String.format(
                Locale.US, "Activity — Gate %.0f%% • Suppression %.0f%% • Limiter %.0f%%",
                (1f - actGate) * 100f, (1f - actSuppress) * 100f, (1f - actLimiter) * 100f,
            ) + if (actNotchHz > 0f) String.format(Locale.US, " • Howl notch @ %.1f kHz", actNotchHz / 1000f) else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (sessionStartMs > 0) {
            val elapsedSec = ((System.currentTimeMillis() - sessionStartMs) / 1000).coerceAtLeast(0)
            Text(
                String.format(Locale.US, "Session %d:%02d", elapsedSec / 60, elapsedSec % 60),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))

    // Buffer / latency tuning (applies live, no service restart).
    Text("Buffer / Latency Tuning", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InfoTip("Buffer / Latency", "The audio buffer trades delay for stability. 1x is the smallest buffer your device offers (its frame size differs per device, shown as buf Nf); higher modes ride out glitchy links. On Bluetooth outputs the app enforces a stability floor automatically.")
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
        InfoTip("Pitch indicator", "Estimates the note you are voicing, for tuning your recitation tone. It only listens — it never changes the sound.")
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
