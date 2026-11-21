package com.haramain.monitor.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

class WaqfAnalyzerTest {

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
    fun `finds phrase endings at expected timestamps and writes sidecar`() = runBlocking {
        val dir = File(System.getProperty("java.io.tmpdir"), "haramain_waqf_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val wav = File(dir, "recitation.wav")
            // 3 phrases ending at 3.5s, 7.7s, 11.4s with >= 0.7s pauses and >= 0.5s tail
            writeWav(wav, 16000, 12.2) { t ->
                when {
                    t in 0.5..3.5 -> 0.25
                    t in 4.5..7.7 -> 0.25
                    t in 8.6..11.4 -> 0.25
                    else -> 0.001
                }
            }
            val marks = WaqfAnalyzer.markers(wav)
            assertEquals(listOf(3500, 7700, 11400), marks)
            val sidecar = File(dir, "recitation.wav.waqf")
            assertTrue("Expected .waqf sidecar", sidecar.exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
