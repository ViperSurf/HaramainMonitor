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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@Composable
private fun RecordingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val changed by MonitorState.recordingsChanged.collectAsState()
    var items by remember { mutableStateOf<List<RecordingItem>>(emptyList()) }
    var playingPath by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(changed) { items = Recordings.list(context) }

    fun stopPlayer() {
        try { player?.stop() } catch (_: Exception) {}
        player?.release()
        player = null
        playingPath = null
    }

    fun togglePlay(item: RecordingItem) {
        if (playingPath == item.file.absolutePath) {
            stopPlayer()
            return
        }
        stopPlayer()
        player = MediaPlayer().apply {
            setDataSource(item.file.absolutePath)
            setOnCompletionListener { stopPlayer() }
            prepare()
            start()
        }
        playingPath = item.file.absolutePath
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
                        Row {
                            IconButton(onClick = { togglePlay(item) }) {
                                Icon(
                                    if (playingPath == item.file.absolutePath) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playingPath == item.file.absolutePath) "Stop" else "Play",
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
