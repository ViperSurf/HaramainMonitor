package com.haramain.monitor.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.Composable
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
import com.haramain.monitor.ui.components.PrecisionControl
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Main dashboard — dark mode first.
 * Top: hardware routing. Middle: monitor toggle, meters, telemetry.
 * Below: presets, then one PrecisionControl per DSP parameter.
 * Boots on the Haramain Cathedral preset (Room 95, Mix 52, Comp 70, De-Esser 50).
 */
@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    micGranted: Boolean,
    onRequestMicPermission: () -> Unit,
) {
    val context = LocalContext.current
    val params by viewModel.params.collectAsState()
    val running by viewModel.isRunning.collectAsState()
    val inDb by viewModel.inputPeakDb.collectAsState()
    val outDb by viewModel.outputPeakDb.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val latency by viewModel.latencyMs.collectAsState()
    val xRuns by viewModel.xRuns.collectAsState()
    val inputs by viewModel.inputs.collectAsState()
    val outputs by viewModel.outputs.collectAsState()
    val selIn by viewModel.selectedInputId.collectAsState()
    val selOut by viewModel.selectedOutputId.collectAsState()
    val preset by viewModel.activePreset.collectAsState()
    val hasCustom by viewModel.hasCustom.collectAsState()
    val feedbackRisk by viewModel.feedbackRisk.collectAsState()

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
                    if (running) "● LIVE — monitoring" else "Live vocal monitoring • Oboe / AAudio",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
        SectionCard {
            Text("Hardware Routing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            DevicePicker(
                label = "Input Mic",
                icon = Icons.Filled.Mic,
                devices = inputs,
                selectedId = selIn,
                onSelect = viewModel::selectInput,
            )
            Spacer(Modifier.height(8.dp))
            DevicePicker(
                label = "Output",
                icon = Icons.Filled.HeadsetMic,
                devices = outputs,
                selectedId = selOut,
                onSelect = viewModel::selectOutput,
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
                OutlinedButton(
                    onClick = viewModel::toggleMute,
                    enabled = running,
                    modifier = Modifier.height(64.dp),
                ) {
                    Text(if (params.muted) "Unmute" else "Mute")
                }
            }
            Spacer(Modifier.height(12.dp))
            LevelMeter("Input", inDb, running)
            LevelMeter("Output", outDb, running)
            Spacer(Modifier.height(6.dp))
            Text(
                if (running) {
                    String.format(
                        Locale.US, "%.1f kHz • %.1f ms round-trip • %d xruns",
                        sampleRate / 1000f, latency, xRuns,
                    )
                } else "Start monitoring to see live sample rate and latency",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ---------- Presets ----------
        SectionCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Presets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = viewModel::saveCustom) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Text("Save Custom", modifier = Modifier.padding(start = 6.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetKind.entries.forEach { kind ->
                    FilterChip(
                        selected = preset == kind,
                        enabled = kind != PresetKind.CUSTOM || hasCustom || preset == PresetKind.CUSTOM,
                        onClick = { viewModel.selectPreset(kind) },
                        label = { Text(kind.label) },
                    )
                }
            }
        }

        // ---------- DSP controls ----------
        SectionCard {
            Text("Vocal Chain", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Double-tap any control to reset it to its Haramain default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PrecisionControl(
                label = "Mic Boost",
                value = params.micBoostPercent, valueRange = 0f..200f, step = 1f,
                defaultValue = 50f,
                format = { String.format(Locale.US, "%.0f%%  (+%.1f dB)", it, it / 100f * 12f) },
                onValueChange = { v -> viewModel.update { it.copy(micBoostPercent = v) } },
            )
            PrecisionControl(
                label = "Static Remover",
                value = params.staticRemover, valueRange = 0f..100f, step = 1f,
                defaultValue = 60f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(staticRemover = v) } },
            )
            ToggleRow(
                label = "Noise Suppression (Voice Isolation)",
                checked = params.noiseSuppressOn,
                onCheckedChange = { on -> viewModel.update { it.copy(noiseSuppressOn = on) } },
            )
            PrecisionControl(
                label = "Suppression Depth",
                value = params.noiseSuppress, valueRange = 0f..100f, step = 1f,
                defaultValue = 40f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(noiseSuppress = v) } },
                enabled = params.noiseSuppressOn,
            )
            PrecisionControl(
                label = "Noise Gate Threshold",
                value = params.gateThresholdDb, valueRange = -80f..0f, step = 0.5f,
                defaultValue = -45f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(gateThresholdDb = v) } },
            )
            PrecisionControl(
                label = "De-Esser",
                value = params.deEsserPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 50f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(deEsserPercent = v) } },
            )
            PrecisionControl(
                label = "Compressor",
                value = params.compressorPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 70f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(compressorPercent = v) } },
            )
        }

        SectionCard {
            Text("Haramain Space", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PrecisionControl(
                label = "Reverb Room Size",
                value = params.roomSizePercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 95f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(roomSizePercent = v) } },
            )
            PrecisionControl(
                label = "Reverb Wet / Dry Mix",
                value = params.wetDryPercent, valueRange = 0f..100f, step = 1f,
                defaultValue = 52f,
                format = { pct(it) },
                onValueChange = { v -> viewModel.update { it.copy(wetDryPercent = v) } },
            )
            PrecisionControl(
                label = "Stereo Balance",
                value = params.balancePercent, valueRange = -100f..100f, step = 1f,
                defaultValue = 0f,
                format = { balanceLabel(it) },
                onValueChange = { v -> viewModel.update { it.copy(balancePercent = v) } },
                showCenterSnap = true,
            )
            PrecisionControl(
                label = "Master Gain",
                value = params.masterGainDb, valueRange = -24f..12f, step = 0.5f,
                defaultValue = 0f,
                format = { db(it) },
                onValueChange = { v -> viewModel.update { it.copy(masterGainDb = v) } },
            )
        }

        // ---------- Safety ----------
        SectionCard {
            ToggleRow(
                label = "Headphone Safety",
                checked = params.safetyOn,
                onCheckedChange = { on -> viewModel.update { it.copy(safetyOn = on) } },
                icon = Icons.Filled.NotificationsActive,
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
                enabled = params.safetyOn,
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
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun LevelMeter(label: String, dbValue: Float, active: Boolean) {
    val fraction = ((dbValue.coerceIn(-60f, 0f) + 60f) / 60f)
    val animated by animateFloatAsState(targetValue = if (active) fraction else 0f, label = "meter$label")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(0.22f))
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.weight(0.6f).height(10.dp),
            color = when {
                dbValue > -6f -> MaterialTheme.colorScheme.error
                dbValue > -18f -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.primary
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
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = devices.firstOrNull { it.id == selectedId }
    val display = selected?.let { "${it.kindLabel} — ${it.name}" } ?: "System Default"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
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
