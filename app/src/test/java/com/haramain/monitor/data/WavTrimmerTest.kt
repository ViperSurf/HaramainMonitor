package com.haramain.monitor.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

class WavTrimmerTest {

    private fun writeWav(file: File, sampleRate: Int, durationSec: Double, amplitudeAtSec: (Double) -> Double) {
        val totalFrames = (sampleRate * durationSec).toInt()
        val dataBytes = totalFrames * 4L
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            fun u32(v: Long) = byteArrayOf(
                (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
                ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
            )
            fun u16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
            raf.write("RIFF".toByteArray())
            raf.write(u32(36 + dataBytes))
            raf.write("WAVE".toByteArray())
            raf.write("fmt ".toByteArray())
            raf.write(u32(16))
            raf.write(u16(1))
            raf.write(u16(2))
            raf.write(u32(sampleRate.toLong()))
            raf.write(u32(sampleRate.toLong() * 4))
            raf.write(u16(4))
            raf.write(u16(16))
            raf.write("data".toByteArray())
            raf.write(u32(dataBytes))
            val buf = ByteArray(totalFrames * 4)
            for (i in 0 until totalFrames) {
                val t = i.toDouble() / sampleRate
                val v = (amplitudeAtSec(t) * sin(2 * PI * 220.0 * t) * 32767.0).toInt().coerceIn(-32767, 32767)
                val lo = (v and 0xFF).toByte()
                val hi = ((v shr 8) and 0xFF).toByte()
                buf[i * 4] = lo; buf[i * 4 + 1] = hi
                buf[i * 4 + 2] = lo; buf[i * 4 + 3] = hi
            }
            raf.write(buf)
        }
    }

    @Test
    fun `trims leading and trailing room tone while keeping 0_3s padding`() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "haramain_trim_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val wav = File(dir, "take.wav")
            // 7.5 s take: -55 dB floor, speech (-14 dB) from 2.0 s to 5.0 s -> keeps 1.7..5.3 s (~3.6 s)
            writeWav(wav, 16000, 7.5) { t ->
                if (t in 2.0..5.0) 0.2 else 0.0018
            }
            val res = WavTrimmer.trim(wav)
            assertTrue("Expected Trimmed, got $res", res is WavTrimmer.TrimResult.Trimmed)
            val trimmed = res as WavTrimmer.TrimResult.Trimmed
            assertTrue(trimmed.outFile.exists())
            assertTrue("savedMs=${trimmed.savedMs} should be ~3900ms", trimmed.savedMs in 3500L..4200L)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `steady floor without speech returns NoSpeech`() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "haramain_trim_nospeech_${System.nanoTime()}").apply { mkdirs() }
        try {
            val wav = File(dir, "static.wav")
            writeWav(wav, 16000, 3.0) { 0.01 }
            val res = WavTrimmer.trim(wav)
            assertEquals(WavTrimmer.TrimResult.NoSpeech, res)
        } finally {
            dir.deleteRecursively()
        }
    }
}
