package com.haramain.monitor.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.log10

/**
 * Edge-silence trimmer for the app's own recordings (canonical
 * 44-byte-header PCM16 stereo WAVs written by the native recorder).
 *
 * Floor-relative: the speech line is the noise floor + 12 dB, the
 * floor being the 15th percentile of 100 ms slice levels. Steady
 * static or white noise sits AT the floor by definition, so it is
 * trimmed at any loudness. Keeps 0.3 s of room tone on each side of
 * the voice. Non-destructive: writes "<name>_trimmed.wav" beside the
 * original and never touches it.
 */
object WavTrimmer {

    sealed interface TrimResult {
        data class Trimmed(val outFile: File, val savedMs: Long) : TrimResult
        data object NothingToTrim : TrimResult
        data object NoSpeech : TrimResult
        data object Failed : TrimResult
    }

    suspend fun trim(src: File): TrimResult = withContext(Dispatchers.IO) {
        try {
            trimSync(src)
        } catch (_: Exception) {
            TrimResult.Failed
        }
    }

    private fun u16le(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or ((b[off + 1].toInt() and 0xFF) shl 8)

    private fun u32le(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun putU32le(b: ByteArray, off: Int, v: Long) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun trimSync(src: File): TrimResult {
        val raf = RandomAccessFile(src, "r")
        try {
            if (raf.length() < 44 + 4) return TrimResult.Failed
            val header = ByteArray(44)
            raf.readFully(header)
            fun tag(off: Int) = String(header, off, 4, Charsets.US_ASCII)
            if (tag(0) != "RIFF" || tag(8) != "WAVE" || tag(12) != "fmt " || tag(36) != "data") {
                return TrimResult.Failed
            }
            if (u16le(header, 20) != 1 || u16le(header, 22) != 2 || u16le(header, 34) != 16) {
                return TrimResult.Failed   // not PCM16 stereo
            }
            val sampleRate = u32le(header, 24).toInt()
            if (sampleRate <= 0) return TrimResult.Failed
            val dataBytes = minOf(u32le(header, 40), raf.length() - 44)
            val frameBytes = 4
            val totalFrames = (dataBytes / frameBytes).toInt()
            val sliceFrames = maxOf(1, sampleRate / 10)          // 100 ms slices
            val sliceCount = (totalFrames + sliceFrames - 1) / sliceFrames
            if (sliceCount < 4) return TrimResult.NothingToTrim

            // Per-slice RMS level in dB (recordings are local; 100 ms slices).
            raf.seek(44)
            for (s in 0 until sliceCount) {
                val frames = minOf(sliceFrames, totalFrames - s * sliceFrames)
                if (frames <= 0) { levels[s] = -120f; continue }
                val bytes = ByteArray(frames * frameBytes)
                raf.readFully(bytes)
                var sumSq = 0.0
                var i = 0
                while (i < bytes.size) {
                    val sample = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt()) shl 8)
                    val v = sample.toShort().toDouble() / 32768.0
                    sumSq += v * v
                    i += 2
                }
                val meanSq = sumSq / (frames * 2)
                levels[s] = (10.0 * log10(meanSq + 1e-12)).toFloat()
            }

            val sorted = levels.sorted()
            val floor = sorted[(sorted.size * 0.15).toInt().coerceIn(0, sorted.size - 1)]
            val line = floor + 12f
            val first = levels.indexOfFirst { it >= line }
            val last = levels.indexOfLast { it >= line }
            if (first < 0) return TrimResult.NoSpeech

            val padSlices = 3   // 0.3 s of room tone kept on each side
            val keepStart = maxOf(0, (first - padSlices) * sliceFrames)
            val keepEnd = minOf(totalFrames, (last + 1 + padSlices) * sliceFrames)
            val savedFrames = totalFrames - (keepEnd - keepStart)
            if (savedFrames < sampleRate / 2) return TrimResult.NothingToTrim

            val outName = src.name.removeSuffix(".wav") + "_trimmed.wav"
            val out = File(src.parentFile, outName)
            val newDataBytes = (keepEnd - keepStart).toLong() * frameBytes
            val outHeader = header.copyOf()
            putU32le(outHeader, 4, 36 + newDataBytes)
            putU32le(outHeader, 40, newDataBytes)
            RandomAccessFile(out, "rw").use { w ->
                w.setLength(0)
                w.write(outHeader)
                raf.seek(44L + keepStart.toLong() * frameBytes)
                var remaining = newDataBytes
                val chunk = ByteArray(65536)
                while (remaining > 0) {
                    val n = raf.read(chunk, 0, minOf(chunk.size.toLong(), remaining).toInt())
                    if (n <= 0) break
                    w.write(chunk, 0, n)
                    remaining -= n
                }
            }
            return TrimResult.Trimmed(out, savedFrames * 1000L / sampleRate)
        } finally {
            raf.close()
        }
    }
}
