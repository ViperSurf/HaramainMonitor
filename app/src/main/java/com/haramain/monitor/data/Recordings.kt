package com.haramain.monitor.data

import android.content.Context
import com.haramain.monitor.service.MicMonitorService
import java.io.File
import java.io.RandomAccessFile

data class RecordingItem(
    val file: File,
    val durationMs: Long,
    val sampleRate: Int,
) {
    val name: String get() = file.name
    val sizeBytes: Long get() = file.length()
    val modifiedMs: Long get() = file.lastModified()
    val isWav: Boolean get() = file.extension.equals("wav", true)
}

/** Lists recordings from the app-private folder; duration comes from the WAV header. */
object Recordings {

    fun list(context: Context): List<RecordingItem> {
        val dir = MicMonitorService.recordingsDir(context)
        val files = dir.listFiles { f ->
            f.isFile && (f.extension.equals("wav", true) || f.extension.equals("m4a", true))
        } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }.map { file ->
            if (file.extension.equals("wav", true)) {
                val (duration, sr) = wavInfo(file)
                RecordingItem(file, duration, sr)
            } else {
                // M4A exports: duration from the file's metadata.
                val dur = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(file.absolutePath)
                    val d = retriever.extractMetadata(
                        android.media.MediaMetadataRetriever.METADATA_KEY_DURATION,
                    )?.toLongOrNull() ?: 0L
                    retriever.release()
                    d
                } catch (_: Exception) { 0L }
                RecordingItem(file, dur, 0)
            }
        }
    }

    /** (durationMs, sampleRate) parsed from our own WAV files (44-byte PCM header). */
    private fun wavInfo(file: File): Pair<Long, Int> = try {
        RandomAccessFile(file, "r").use { raf ->
            if (file.length() < 44) return 0L to 0
            val header = ByteArray(44)
            raf.readFully(header)
            val sr = (header[24].toInt() and 0xFF) or
                ((header[25].toInt() and 0xFF) shl 8) or
                ((header[26].toInt() and 0xFF) shl 16) or
                ((header[27].toInt() and 0xFF) shl 24)
            if (sr <= 0) return 0L to 0
            val dataBytes = file.length() - 44
            (dataBytes * 1000 / (sr * 4)) to sr   // 4 bytes per stereo frame (16-bit x2)
        }
    } catch (_: Exception) {
        0L to 0
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0) return "--:--"
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
