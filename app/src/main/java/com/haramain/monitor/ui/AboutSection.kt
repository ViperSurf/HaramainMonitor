package com.haramain.monitor.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.haramain.monitor.data.Recordings
import com.haramain.monitor.service.MicMonitorService
import com.haramain.monitor.service.MonitorState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val URL_PROFILE = "https://github.com/vipersurf"
private const val URL_REPO = "https://github.com/ViperSurf/HaramainMonitor"
private const val URL_RELEASES = "https://github.com/ViperSurf/HaramainMonitor/releases"
private const val URL_ISSUES = "https://github.com/ViperSurf/HaramainMonitor/issues"
private const val URL_DOCS = "https://github.com/ViperSurf/HaramainMonitor#readme"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutAndSupportContent(
    viewModel: MonitorViewModel,
    micGranted: Boolean,
    onRequestMicPermission: () -> Unit,
    onShowMessage: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val running by viewModel.isRunning.collectAsState()
    val sampleRate by viewModel.sampleRate.collectAsState()
    val bufferMode by viewModel.bufferMode.collectAsState()
    val bufferFrames by viewModel.bufferFrames.collectAsState()
    val preset by viewModel.activePreset.collectAsState()
    val recChanged by MonitorState.recordingsChanged.collectAsState()

    val (versionName, versionCode) = remember { readVersionInfo(context) }

    var recCount by remember { mutableStateOf(0) }
    var recBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(recChanged) {
        withContext(Dispatchers.IO) {
            val files = MicMonitorService.recordingsDir(context).listFiles()?.filter { it.isFile }.orEmpty()
            recCount = files.size
            recBytes = files.sumOf { it.length() }
        }
    }

    val notifGranted = remember(micGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }
    val btGranted = remember(micGranted) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else true
    }

    fun buildDiagnostics(): String = buildString {
        appendLine("Haramain Monitor v$versionName (build $versionCode)")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Permissions: mic=$micGranted, notif=$notifGranted, bt=$btGranted")
        appendLine("Audio: running=$running, sr=${sampleRate}Hz, bufferMode=${bufferMode}x (${bufferFrames}f), preset=${preset.label}")
        append("Recordings: $recCount files (${Recordings.formatSize(recBytes)})")
    }

    // ---- 1. App Info & Identity ----
    Text("App Info & Identity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Text(
        "Haramain Monitor — Ultra-low-latency live microphone monitoring & vocal reverb for Android.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
    Text(
        "Version $versionName (Build $versionCode) • Apache License 2.0 • 100% Offline",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Haramain Diagnostics", buildDiagnostics()))
            if (onShowMessage != null) onShowMessage("Diagnostics copied to clipboard")
            else Toast.makeText(context, "Diagnostics copied to clipboard", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = null)
            Text("Copy diagnostics", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { openUrl(context, URL_PROFILE) }) {
            Icon(Icons.Filled.Person, contentDescription = null)
            Text("Developer", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Haramain Monitor")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Haramain Monitor — ultra-low-latency live mic monitoring & vocal reverb for Android:\n$URL_RELEASES",
                )
            }
            context.startActivity(Intent.createChooser(send, "Share Haramain Monitor"))
        }) {
            Icon(Icons.Filled.Share, contentDescription = null)
            Text("Share App", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { openUrl(context, URL_REPO) }) {
            Icon(Icons.Filled.Star, contentDescription = null)
            Text("Rate / Star", modifier = Modifier.padding(start = 6.dp))
        }
    }

    Spacer(Modifier.height(8.dp))
    InlineAccordion(title = "What's New / Changelog (v$versionName)") {
        ChangelogBody()
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    // ---- 2. Help & Feedback ----
    Text("Help & Feedback", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    InlineAccordion(title = "FAQ & Quick Guide") {
        FaqBody()
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val diag = Uri.encode(buildDiagnostics())
            openUrl(
                context,
                "$URL_ISSUES/new?template=bug_report.yml&title=%5BBug%5D+&diagnostics=$diag",
            )
        }) {
            Icon(Icons.Filled.BugReport, contentDescription = null)
            Text("Report a Bug", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = {
            openUrl(
                context,
                "$URL_ISSUES/new?template=feature_request.yml&title=%5BFeature%5D+",
            )
        }) {
            Icon(Icons.Filled.Lightbulb, contentDescription = null)
            Text("Request a Feature", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { openUrl(context, URL_DOCS) }) {
            Icon(Icons.Filled.Description, contentDescription = null)
            Text("Documentation", modifier = Modifier.padding(start = 6.dp))
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    // ---- 3. System & Diagnostics (Adaptive Android Checks) ----
    Text("System & Diagnostics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text(
        "• Microphone permission: ${if (micGranted) "Granted" else "Not granted"} · " +
            "Notifications: ${if (notifGranted) "Granted" else "Off"} · " +
            "Bluetooth: ${if (btGranted) "Granted" else "Off"}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "• Audio engine: ${if (running) "Live (${sampleRate} Hz, ${bufferMode}× buffer)" else "Idle (${bufferMode}× buffer configured)"} · " +
            "Preset: ${preset.label}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "• Local recordings storage: $recCount files (${Recordings.formatSize(recBytes)}) in app-private storage",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (!micGranted) {
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = onRequestMicPermission) {
            Text("Grant Microphone Permission")
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

    // ---- 4. Open Source & Legal ----
    Text("Open Source & Legal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { openUrl(context, URL_REPO) }) {
            Icon(Icons.Filled.Code, contentDescription = null)
            Text("Source Code", modifier = Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { openUrl(context, URL_ISSUES) }) {
            Text("Issue Tracker")
        }
    }
    Spacer(Modifier.height(8.dp))
    InlineAccordion(title = "Third-Party Licenses") {
        LicensesBody()
    }
    Spacer(Modifier.height(6.dp))
    InlineAccordion(title = "Privacy Policy, Terms & Honesty Disclaimer") {
        LegalBody()
    }
}

@Composable
private fun InlineAccordion(
    title: String,
    content: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.animateContentSize(
                animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(
                    if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (open) "Collapse $title" else "Expand $title",
                )
            }
            if (open) {
                Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun ChangelogBody() {
    val notes = listOf(
        "v4.0" to "Material 3 redesign with 3-accent Studio Orange & Haramain Emerald palettes + pure OLED black dark mode; 3-tab Bottom Navigation (Monitor / Sound / Settings); About & Support section; Reset all settings; Feedback Guard & Lead preset fixes; automated C++ & JVM regression suite.",
        "v3.6" to "Voice Steadiness reframe (Hz-only breath mirror, no note names); finished takes stay loaded at the end in the Library; single-launch service collectors.",
        "v3.5" to "Fixed auto-record stop race; Library player gained pause/resume, seek slider, -10s/+10s skip, and A-B loop completion guard.",
        "v3.4" to "Connection automation keyed to actual routed device IDs with scope chips; Auto-trim finished recordings; Waqf phrase-end markers in Library.",
        "v3.2" to "Earbud & lock-screen media-button mute; Library speed (0.5x/0.75x/1x) & A-B phrase loop; daily practice goal & streak; non-destructive edge silence trim.",
        "v3.0" to "Platform NoiseSuppressor removed from record path for lower latency; Lead (Imam) preset; Monitor Ears (Stereo / Left / Right); FFT Feedback Guard anti-howl notch bank.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        notes.forEach { (ver, desc) ->
            Text("$ver — $desc", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FaqBody() {
    val faqs = listOf(
        "How do I get the lowest latency?" to
            "Use wired headphones or a USB-C DAC/mic and keep Buffer Mode on Ultra-Low 1×. Bluetooth audio adds 100–350 ms of codec delay by hardware design; if using Bluetooth earbuds, enable Bluetooth Dry-Ducking in Haramain Space and route a phone/USB mic.",
        "Which preset should I pick if I just want vocal reverb or studio monitoring?" to
            "Use Masjid al-Haram or Historic Stone Dome for lush, long hall reverb; Local Masjid Hall or Lead (Imam) for medium room space with the voice up front; or Studio Dry / Simple Studio for clean podcast and speech monitoring.",
        "Why don't I hear Noise Suppression or Static Remover changing my voice?" to
            "By design, both stages work in the gaps between your phrases so they never thin out your active voice. Watch the Suppression % readout under the meters while a fan or AC is running.",
        "What are Waqf marks and Voice Steadiness?" to
            "Waqf marks automatically find phrase endings (pauses) in a WAV recording so you can tap a timestamp chip to jump between segments. Voice Steadiness displays your held-vowel pitch in Hz so you can check breath stability.",
        "Gesture shortcuts" to
            "Double-tap any slider readout to reset it to its default value. Press and hold 'Hold to hear raw mic' for an instant A/B comparison against your unprocessed microphone.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        faqs.forEach { (q, a) ->
            Column {
                Text(q, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    a,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LicensesBody() {
    val libs = listOf(
        "Haramain Monitor" to "Apache License 2.0 • Copyright © ViperSurf",
        "Google Oboe (1.9.0)" to "Apache License 2.0 • Low-latency AAudio / OpenSL ES C++ audio engine",
        "AndroidX & Jetpack Compose (BOM 2024.10.00)" to "Apache License 2.0 • UI, Material 3, Lifecycle, Activity, Core, DataStore Preferences",
        "Freeverb Reverb Topology" to "Public Domain • Schroeder/Moorer parallel-comb & allpass reverb model by Jezar at Dreampoint",
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        libs.forEach { (name, detail) ->
            Column {
                Text(name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LegalBody() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Privacy Policy (100% Offline & Local-First)", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            "Haramain Monitor does not request the INTERNET permission and contains zero network, analytics, tracking, or advertising code. Live microphone audio is processed in memory only. Recordings, presets, and practice logs stay on your device unless you explicitly share or export them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Recitation & Acoustic Honesty Disclaimer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            "Voice Steadiness (Hz) and Waqf phrase markers are objective acoustic mirrors of pitch stability and pause timing. They never score, grade, or judge Qur'an recitation, tajweed, or vocal performance.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("Terms of Service", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(
            "Provided under the Apache License 2.0 on an 'AS IS' basis without warranties of any kind. Always start with moderate headphone volume and leave Headphone Safety enabled (−3 dBFS ceiling) to protect your hearing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun readVersionInfo(context: Context): Pair<String, Long> = try {
    val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pkg.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        pkg.versionCode.toLong()
    }
    (pkg.versionName ?: "4.0") to code
} catch (_: Exception) {
    "4.0" to 25L
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
    }
}
