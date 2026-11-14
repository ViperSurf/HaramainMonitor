package com.haramain.monitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.log10

/**
 * Finds likely waqf (phrase-end) marks in a take: the ends of voiced
 * phrases, from the same floor-relative envelope analysis as the
 * trimmer (100 ms slices; speech line = 15th-percentile floor + 12
 * dB). A phrase is a run of voice at least 0.8 s long (short dips
 * merged); its end is a mark when a real pause (>= 0.7 s) follows,
 * or when it closes the take. It is a mirror of where YOU stopped —
 * level-based, no pitch judgement, no scoring.
 *
 * Results are cached in a "<name>.waqf" sidecar next to the WAV.
 */
object WaqfAnalyzer {

    suspend fun markers(src: File): List<Int> = withContext(Dispatchers.IO) {
        try {
            val sidecar = File(src.parentFile, src.name + ".waqf")
            if (sidecar.exists() && sidecar.lastModified() >= src.lastModified() &&
                src.name.endsWith(".wav")
            ) {
                return@withContext sidecar.readText()
                    .split(',').mapNotNull { it.trim().toIntOrNull() }
            }
            val found = analyze(src)
            try {
                sidecar.writeText(found.joinToString(","))
            } catch (_: Exception) {}
            found
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun u16le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun u32le(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun analyze(src: File): List<Int> {
        val raf = RandomAccessFile(src, "r")
        try {
            if (raf.length() < 44 + 4) return emptyList()
            val header = ByteArray(44)
            raf.readFully(header)
            fun tag(off: Int) = String(header, off, 4, Charsets.US_ASCII)
            if (tag(0) != "RIFF" || tag(8) != "WAVE" || tag(36) != "data") return emptyList()
            if (u16le(header, 20) != 1 || u16le(header, 22) != 2 || u16le(header, 34) != 16) {
                return emptyList()
            }
            val sampleRate = u32le(header, 24).toInt()
            if (sampleRate <= 0) return emptyList()
            val dataBytes = minOf(u32le(header, 40), raf.length() - 44)
            val totalFrames = (dataBytes / 4).toInt()
            val sliceFrames = maxOf(1, sampleRate / 10)
            val sliceCount = (totalFrames + sliceFrames - 1) / sliceFrames
            if (sliceCount < 10) return emptyList()

            val levels = FloatArray(sliceCount)
            raf.seek(44)
            for (s in 0 until sliceCount) {
                val frames = minOf(sliceFrames, totalFrames - s * sliceFrames)
                if (frames <= 0) { levels[s] = -120f; continue }
                val bytes = ByteArray(frames * 4)
                raf.readFully(bytes)
                var sumSq = 0.0
                var i = 0
                while (i < bytes.size) {
                    val sample = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt()) shl 8)
                    val v = sample.toShort().toDouble() / 32768.0
                    sumSq += v * v
                    i += 2
                }
                levels[s] = (10.0 * log10(sumSq / (frames * 2) + 1e-12)).toFloat()
            }

            val sorted = levels.sorted()
            val floor = sorted[(sorted.size * 0.15).toInt().coerceIn(0, sorted.size - 1)]
            val line = floor + 12f

            // Voiced runs; merge runs separated by <= 0.3 s dips.
            val runs = mutableListOf<IntRange>()
            var start = -1
            for (s in 0 until sliceCount) {
                if (levels[s] >= line) {
                    if (start < 0) start = s
                } else if (start >= 0) {
                    runs.add(start until s)
                    start = -1
                }
            }
            if (start >= 0) runs.add(start until sliceCount)
            val merged = mutableListOf<IntRange>()
            for (run in runs) {
                val last = merged.lastOrNull()
                if (last != null && run.first - (last.last + 1) <= 3) {
                    merged[merged.lastIndex] = last.first..run.last
                } else {
                    merged.add(run)
                }
            }

            // Phrase = run >= 0.8 s. Mark its end when a >= 0.7 s pause
            // follows, or when it closes the take (>= 0.5 s of tail).
            val marks = mutableListOf<Int>()
            for ((idx, run) in merged.withIndex()) {
                if (run.last - run.first + 1 < 8) continue
                val endSlice = run.last + 1
                val nextStart = merged.getOrNull(idx + 1)?.first ?: sliceCount
                val pauseAfter = nextStart - endSlice
                val tailAfter = sliceCount - endSlice
                if (pauseAfter >= 7 || (idx == merged.lastIndex && tailAfter >= 5)) {
                    marks.add(endSlice * 100)
                }
            }
            return marks
        } finally {
            raf.close()
        }
    }
}
