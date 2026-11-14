package com.haramain.monitor.ui

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.haramain.monitor.data.M4aExporter
import com.haramain.monitor.data.RecordingItem
import com.haramain.monitor.data.Recordings
import com.haramain.monitor.data.SettingsStore
import com.haramain.monitor.data.ThemeMode
import com.haramain.monitor.service.MonitorState
import com.haramain.monitor.ui.theme.HaramainMonitorTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Recordings library: play, share, export to M4A, delete. */
class RecordingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            LaunchedEffect(Unit) {
                mode = SettingsStore(this@RecordingsActivity).themeMode.first()
            }
            HaramainMonitorTheme(mode) {
                RecordingsScreen(onBack = { finish() })
            }
        }
    }
}

/** One-tap copy into the public Downloads folder (MediaStore, Android 10+). */
private suspend fun saveToDownloads(context: android.content.Context, item: RecordingItem): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, item.name)
                put(MediaStore.Downloads.MIME_TYPE, if (item.isWav) "audio/wav" else "audio/mp4")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/HaramainMonitor")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext false
            context.contentResolver.openOutputStream(uri)?.use { out ->
                item.file.inputStream().use { it.copyTo(out) }
            } != null
        } catch (_: Exception) {
            false
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val changed by MonitorState.recordingsChanged.collectAsState()
    var items by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }
    var playingPath by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var speed by remember { mutableStateOf(1f) }
    var progressMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }
    var loopA by remember { mutableStateOf<Int?>(null) }
    var loopB by remember { mutableStateOf<Int?>(null) }
    var abLoop by remember { mutableStateOf(false) }
    var waqfMarks by remember { mutableStateOf<List<Int>?>(null) }
    var isPaused by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var lastLoopSeekMs by remember { mutableStateOf(0L) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(changed) { items = Recordings.list(context) }

    fun stopPlayer() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        playingPath = null
        progressMs = 0
        durationMs = 0
        loopA = null
        loopB = null
        abLoop = false
        waqfMarks = null
        isPaused = false
    }

    fun applySpeed(s: Float) {
        speed = s
        try {
            player?.setPlaybackParams(android.media.PlaybackParams().setSpeed(s).setPitch(1f))
        } catch (_: Exception) {}
    }

    fun togglePlay(item: RecordingItem) {
        if (playingPath == item.file.absolutePath && player != null) {
            // Pause / resume the loaded take (position is kept, so you
            // can set A/B points precisely).
            try {
                if (isPaused) { player?.start(); isPaused = false }
                else { player?.pause(); isPaused = true }
            } catch (_: Exception) {}
            return
        }
        stopPlayer()
        player = MediaPlayer().apply {
            setDataSource(item.file.absolutePath)
            setOnCompletionListener {
                // If an A-B loop is armed, reaching the end loops back
                // to A instead of ending playback (the loop watcher can
                // miss the final frames; this is the safety net).
                val a = loopA
                if (abLoop && a != null) {
                    try { seekTo(a); start(); isPaused = false }
                    catch (_: Exception) { stopPlayer() }
                } else {
                    stopPlayer()
                }
            }
            prepare()
            durationMs = duration
            if (speed != 1f) {
                try { setPlaybackParams(android.media.PlaybackParams().setSpeed(speed).setPitch(1f)) } catch (_: Exception) {}
            }
            start()
        }
        playingPath = item.file.absolutePath
        isPaused = false
    }

    // Progress + A-B loop watcher for the playing take.
    LaunchedEffect(playingPath) {
        while (playingPath != null) {
            val p = player
            if (p != null) {
                try {
                    if (!scrubbing) progressMs = p.currentPosition
                    val a = loopA; val b = loopB
                    val now = System.currentTimeMillis()
                    if (abLoop && a != null && b != null && !isPaused &&
                        p.currentPosition >= b && now - lastLoopSeekMs > 400
                    ) {
                        lastLoopSeekMs = now
                        p.seekTo(a)
                    }
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.delay(200)
        }
    }

    DisposableEffect(Unit) { onDispose { stopPlayer() } }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                "My Recordings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            "Recordings live only on this phone, in the app's private folder. " +
                "If you share or upload one (Drive, WhatsApp, email…), it's only as private as that destination — choose carefully.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                "No recordings yet. Start monitoring, tap Rec, and your recitation lands here as a WAV file.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.file.absolutePath }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "${SimpleDateFormat("EEE, MMM d • h:mm a", Locale.US).format(Date(item.modifiedMs))}" +
                                " • ${Recordings.formatDuration(item.durationMs)} • ${Recordings.formatSize(item.sizeBytes)}" +
                                if (item.isWav) " • WAV" else " • M4A",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (playingPath == item.file.absolutePath) {
                            Text(
                                "${Recordings.formatDuration(progressMs.toLong())} / ${Recordings.formatDuration(durationMs.toLong())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Slider(
                                value = progressMs.toFloat(),
                                onValueChange = { v ->
                                    scrubbing = true
                                    progressMs = v.toInt()
                                    try { player?.seekTo(v.toInt()) } catch (_: Exception) {}
                                },
                                onValueChangeFinished = { scrubbing = false },
                                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(onClick = {
                                    try { player?.seekTo(maxOf(0, progressMs - 10_000)) } catch (_: Exception) {}
                                }) { Text("−10s") }
                                OutlinedButton(onClick = {
                                    try { player?.seekTo(minOf(durationMs, progressMs + 10_000)) } catch (_: Exception) {}
                                }) { Text("+10s") }
                                OutlinedButton(
                                    onClick = { loopA = null; loopB = null; abLoop = false },
                                    enabled = loopA != null || loopB != null,
                                ) { Text("Reset A-B") }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Speed", style = MaterialTheme.typography.labelLarge)
                                listOf(0.5f to "0.5×", 0.75f to "0.75×", 1f to "1×").forEach { (s, label) ->
                                    FilterChip(
                                        selected = speed == s,
                                        onClick = { applySpeed(s) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OutlinedButton(onClick = { loopA = progressMs; if (abLoop && (loopB ?: 0) <= progressMs) abLoop = false }) {
                                    Text(if (loopA == null) "Set A" else "A ${Recordings.formatDuration(loopA!!.toLong())}")
                                }
                                OutlinedButton(onClick = { loopB = progressMs }) {
                                    Text(if (loopB == null) "Set B" else "B ${Recordings.formatDuration(loopB!!.toLong())}")
                                }
                                FilterChip(
                                    selected = abLoop,
                                    enabled = loopA != null && loopB != null && (loopB ?: 0) > (loopA ?: 0),
                                    onClick = { abLoop = !abLoop },
                                    label = { Text("Loop A-B") },
                                )
                            }
                            if (item.isWav) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            waqfMarks = com.haramain.monitor.data.WaqfAnalyzer.markers(item.file)
                                            if (waqfMarks.isNullOrEmpty()) {
                                                Toast.makeText(context, "No clear phrase endings found in this take", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }) {
                                        Text(if (waqfMarks == null) "Find waqf marks" else "Waqf marks found")
                                    }
                                    Text(
                                        "Likely phrase endings — tap one to jump there.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (!waqfMarks.isNullOrEmpty()) {
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        waqfMarks!!.forEach { ms ->
                                            FilterChip(
                                                selected = false,
                                                onClick = { try { player?.seekTo(ms) } catch (_: Exception) {} },
                                                label = { Text(Recordings.formatDuration(ms.toLong())) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Row {
                            IconButton(onClick = { togglePlay(item) }) {
                                Icon(
                                    if (playingPath == item.file.absolutePath && !isPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playingPath == item.file.absolutePath && !isPaused) "Pause" else "Play",
                                )
                            }
                            IconButton(onClick = {
                                val uri = FileProvider.getUriForFile(
                                    context, context.packageName + ".fileprovider", item.file,
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = if (item.isWav) "audio/wav" else "audio/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share recording"))
                            }) {
                                Icon(Icons.Filled.Share, contentDescription = "Share")
                            }
                            IconButton(onClick = {
                                scope.launch {
                                    val ok = saveToDownloads(context, item)
                                    Toast.makeText(
                                        context,
                                        if (ok) "Saved to Downloads/HaramainMonitor"
                                        else "Downloads needs Android 10+ — use Share instead",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }) {
                                Icon(Icons.Filled.Download, contentDescription = "Save to Downloads")
                            }
                            if (item.isWav) {
                                IconButton(onClick = {
                                    scope.launch {
                                        val out = M4aExporter.export(item.file)
                                        Toast.makeText(
                                            context,
                                            if (out != null) "Exported ${out.name}" else "M4A export failed",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        items = Recordings.list(context)
                                    }
                                }) {
                                    Icon(Icons.Filled.FileUpload, contentDescription = "Export as M4A")
                                }
                            }
                            if (item.isWav && !item.name.endsWith("_trimmed.wav")) {
                                IconButton(onClick = {
                                    scope.launch {
                                        val msg = when (val r = com.haramain.monitor.data.WavTrimmer.trim(item.file)) {
                                            is com.haramain.monitor.data.WavTrimmer.TrimResult.Trimmed ->
                                                "Trimmed ${Recordings.formatDuration(r.savedMs)} of edge silence → ${r.outFile.name}"
                                            com.haramain.monitor.data.WavTrimmer.TrimResult.NothingToTrim ->
                                                "Nothing to trim — no long edge silence"
                                            com.haramain.monitor.data.WavTrimmer.TrimResult.NoSpeech ->
                                                "No voice above the room tone found"
                                            com.haramain.monitor.data.WavTrimmer.TrimResult.Failed ->
                                                "Trim failed"
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                        items = Recordings.list(context)
                                    }
                                }) {
                                    Icon(Icons.Filled.ContentCut, contentDescription = "Trim edge silence")
                                }
                            }
                            IconButton(onClick = {
                                if (playingPath == item.file.absolutePath) stopPlayer()
                                item.file.delete()
                                items = Recordings.list(context)
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
