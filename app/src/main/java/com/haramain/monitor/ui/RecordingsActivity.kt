package com.haramain.monitor.ui

import android.content.ContentValues
import android.content.Intent
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

/** Recordings library: play, search, share, export to M4A, trim, waqf marks, delete. */
class RecordingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            var palette by remember { mutableStateOf(com.haramain.monitor.data.ThemePalette.STUDIO) }
            LaunchedEffect(Unit) {
                val s = SettingsStore(this@RecordingsActivity)
                mode = s.themeMode.first()
                palette = s.themePalette.first()
            }
            HaramainMonitorTheme(mode, palette) {
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val changed by MonitorState.recordingsChanged.collectAsState()
    var items by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
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
    val snackbarHostState = remember { SnackbarHostState() }

    fun showSnack(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg) }
    }

    suspend fun refreshItems() {
        items = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            Recordings.list(context)
        }
    }

    LaunchedEffect(changed) { refreshItems() }

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
            try {
                if (isPaused) {
                    val a = loopA
                    if (abLoop && a != null) player?.seekTo(a)
                    else if (durationMs > 0 && progressMs >= durationMs - 300) player?.seekTo(0)
                    player?.start(); isPaused = false
                } else {
                    player?.pause(); isPaused = true
                }
            } catch (_: Exception) {}
            return
        }
        stopPlayer()
        player = MediaPlayer().apply {
            setDataSource(item.file.absolutePath)
            setOnCompletionListener {
                val a = loopA
                if (abLoop && a != null) {
                    try { seekTo(a); start(); isPaused = false }
                    catch (_: Exception) { stopPlayer() }
                } else {
                    isPaused = true
                    progressMs = durationMs
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

    val filtered = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items
        else items.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("My Recordings", fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) searchQuery = ""
                    }) {
                        Icon(
                            if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchOpen) "Close search" else "Search recordings",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            AnimatedVisibility(visible = searchOpen) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search recordings…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                )
            }
            Text(
                "Recordings live only on this phone, in the app's private folder. " +
                    "If you share or upload one, it's only as private as that destination — choose carefully.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            if (filtered.isEmpty()) {
                Text(
                    if (items.isEmpty()) {
                        "No recordings yet. Start monitoring, tap Rec, and your recitation lands here as a WAV file."
                    } else {
                        "No recordings match \"$searchQuery\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filtered, key = { it.file.absolutePath }) { item ->
                    val isThisPlaying = playingPath == item.file.absolutePath
                    var menuOpen by remember { mutableStateOf(false) }

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "${SimpleDateFormat("EEE, MMM d • h:mm a", Locale.US).format(Date(item.modifiedMs))}" +
                                            " • ${Recordings.formatDuration(item.durationMs)} • ${Recordings.formatSize(item.sizeBytes)}" +
                                            if (item.isWav) " • WAV" else " • M4A",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Box {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More actions")
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        DropdownMenuItem(
                                            leadingIcon = { Icon(Icons.Filled.Download, contentDescription = null) },
                                            text = { Text("Save to Downloads") },
                                            onClick = {
                                                menuOpen = false
                                                scope.launch {
                                                    val ok = saveToDownloads(context, item)
                                                    showSnack(
                                                        if (ok) "Saved to Downloads/HaramainMonitor"
                                                        else "Downloads needs Android 10+ — use Share instead",
                                                    )
                                                }
                                            },
                                        )
                                        if (item.isWav) {
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Filled.FileUpload, contentDescription = null) },
                                                text = { Text("Export as M4A") },
                                                onClick = {
                                                    menuOpen = false
                                                    scope.launch {
                                                        val out = M4aExporter.export(item.file)
                                                        showSnack(if (out != null) "Exported ${out.name}" else "M4A export failed")
                                                        refreshItems()
                                                    }
                                                },
                                            )
                                        }
                                        if (item.isWav && !item.name.endsWith("_trimmed.wav")) {
                                            DropdownMenuItem(
                                                leadingIcon = { Icon(Icons.Filled.ContentCut, contentDescription = null) },
                                                text = { Text("Trim edge silence") },
                                                onClick = {
                                                    menuOpen = false
                                                    scope.launch {
                                                        val msg = when (val r = com.haramain.monitor.data.WavTrimmer.trim(item.file)) {
                                                            is com.haramain.monitor.data.WavTrimmer.TrimResult.Trimmed ->
                                                                "Trimmed ${Recordings.formatDuration(r.savedMs)} → ${r.outFile.name}"
                                                            com.haramain.monitor.data.WavTrimmer.TrimResult.NothingToTrim ->
                                                                "Nothing to trim — no long edge silence"
                                                            com.haramain.monitor.data.WavTrimmer.TrimResult.NoSpeech ->
                                                                "No voice above the room tone found"
                                                            com.haramain.monitor.data.WavTrimmer.TrimResult.Failed ->
                                                                "Trim failed"
                                                        }
                                                        showSnack(msg)
                                                        refreshItems()
                                                    }
                                                },
                                            )
                                        }
                                        DropdownMenuItem(
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                )
                                            },
                                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                menuOpen = false
                                                if (playingPath == item.file.absolutePath) stopPlayer()
                                                item.file.delete()
                                                scope.launch { refreshItems() }
                                            },
                                        )
                                    }
                                }
                            }

                            if (isThisPlaying) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "${Recordings.formatDuration(progressMs.toLong())} / ${Recordings.formatDuration(durationMs.toLong())}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.Center,
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
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        "Speed",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.align(Alignment.CenterVertically),
                                    )
                                    listOf(0.5f to "0.5×", 0.75f to "0.75×", 1f to "1×").forEach { (s, label) ->
                                        FilterChip(
                                            selected = speed == s,
                                            onClick = { applySpeed(s) },
                                            label = { Text(label) },
                                        )
                                    }
                                }
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    OutlinedButton(onClick = {
                                        loopA = progressMs
                                        if (abLoop && (loopB ?: 0) <= progressMs) abLoop = false
                                    }) {
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
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                        ),
                                    )
                                }
                                if (item.isWav) {
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        OutlinedButton(onClick = {
                                            scope.launch {
                                                waqfMarks = com.haramain.monitor.data.WaqfAnalyzer.markers(item.file)
                                                if (waqfMarks.isNullOrEmpty()) {
                                                    showSnack("No clear phrase endings found in this take")
                                                }
                                            }
                                        }) {
                                            Text(if (waqfMarks == null) "Find waqf marks" else "Waqf marks found")
                                        }
                                        Text(
                                            "Tap a mark to jump there.",
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
                                                    colors = FilterChipDefaults.filterChipColors(
                                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(onClick = { togglePlay(item) }) {
                                    Icon(
                                        if (isThisPlaying && !isPaused) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                    )
                                    Text(
                                        if (isThisPlaying && !isPaused) "Pause" else "Play",
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                OutlinedButton(onClick = {
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
                                    Icon(Icons.Filled.Share, contentDescription = null)
                                    Text("Share", modifier = Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
