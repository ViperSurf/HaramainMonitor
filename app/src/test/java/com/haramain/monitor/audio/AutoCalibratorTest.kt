package com.haramain.monitor.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCalibratorTest {

    @Test
    fun `hot mic gets zero boost`() {
        val base = AudioParams.HARAMAIN
        val noise = CalStats(rmsDb = -60f, peakDb = -50f, hfDb = -70f)
        val voice = CalStats(rmsDb = -16f, peakDb = -6f, hfDb = -28f)
        val res = AutoCalibrator.compute(base, noise, voice)
        assertEquals(0f, res.params.micBoostPercent, 0.01f)
    }

    @Test
    fun `quiet mic boost is capped at +18 dB and peaks stay at or below -9 dBFS`() {
        val base = AudioParams.HARAMAIN
        val noise = CalStats(rmsDb = -65f, peakDb = -55f, hfDb = -75f)
        val voice = CalStats(rmsDb = -42f, peakDb = -30f, hfDb = -52f)
        val res = AutoCalibrator.compute(base, noise, voice)
        val boostDb = res.params.micBoostPercent / 100f * 12f
        assertTrue("boostDb=$boostDb should be <= 18", boostDb <= 18.01f)
        assertTrue("boosted peak should stay <= -9", voice.peakDb + boostDb <= -8.99f)
    }

    @Test
    fun `gate never sits above voice rms minus 6 dB`() {
        val base = AudioParams.HARAMAIN
        val noise = CalStats(rmsDb = -38f, peakDb = -30f, hfDb = -45f)
        val voice = CalStats(rmsDb = -34f, peakDb = -20f, hfDb = -44f)
        val res = AutoCalibrator.compute(base, noise, voice)
        val boostDb = res.params.micBoostPercent / 100f * 12f
        val voiceCeiling = voice.rmsDb + boostDb - 6f
        assertTrue(
            "gate ${res.params.gateThresholdDb} must be <= voiceCeiling $voiceCeiling",
            res.params.gateThresholdDb <= voiceCeiling + 0.01f,
        )
    }

    @Test
    fun `calibration preserves artistic reverb and EQ settings`() {
        val custom = AudioParams.LEAD.copy(eqBand1kDb = 3f, revDampingPercent = 72f)
        val noise = CalStats(rmsDb = -55f, peakDb = -45f, hfDb = -65f)
        val voice = CalStats(rmsDb = -24f, peakDb = -14f, hfDb = -36f)
        val res = AutoCalibrator.compute(custom, noise, voice)
        assertEquals(custom.roomSizePercent, res.params.roomSizePercent, 0.01f)
        assertEquals(custom.wetDryPercent, res.params.wetDryPercent, 0.01f)
        assertEquals(3f, res.params.eqBand1kDb, 0.01f)
        assertEquals(72f, res.params.revDampingPercent, 0.01f)
    }
}
