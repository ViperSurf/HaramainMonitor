package com.haramain.monitor.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioParamsTest {

    @Test
    fun `default AudioParams matches HARAMAIN and ORIGINAL`() {
        val def = AudioParams()
        assertEquals(AudioParams.HARAMAIN, def)
        assertEquals(AudioParams.ORIGINAL, def)
        assertEquals(95f, def.roomSizePercent, 0.001f)
        assertEquals(52f, def.wetDryPercent, 0.001f)
        assertEquals(70f, def.compressorPercent, 0.001f)
        assertEquals(50f, def.deEsserPercent, 0.001f)
        assertEquals(-45f, def.gateThresholdDb, 0.001f)
        assertTrue(def.safetyOn)
        assertEquals(-3f, def.safetyCeilingDb, 0.001f)
        assertTrue(def.howlGuardOn)
    }

    @Test
    fun `toFloatArray produces 26 floats in exact JNI order`() {
        val p = AudioParams(
            micBoostPercent = 11f,
            staticRemover = 12f,
            noiseSuppress = 13f,
            gateThresholdDb = -33f,
            deEsserPercent = 14f,
            compressorPercent = 15f,
            roomSizePercent = 16f,
            wetDryPercent = 17f,
            balancePercent = -20f,
            masterGainDb = 2f,
            safetyCeilingDb = -4f,
            eqLowGainDb = 1f,
            eqHighGainDb = -1f,
            btDuckPercent = 66f,
            eqWarmthDb = 3f,
            eqClarityDb = 4f,
            eqAirDb = 5f,
            eqBand60Db = -2f,
            eqBand250Db = -3f,
            eqBand1kDb = 2.5f,
            eqBand4kDb = 3.5f,
            eqBand12kDb = 1.5f,
            revDampingPercent = 40f,
            revWidthPercent = 80f,
            monitorMode = 2,
            howlGuardOn = false,
        )
        val arr = p.toFloatArray()
        assertEquals(26, arr.size)
        assertEquals(11f, arr[0], 0.001f)
        assertEquals(12f, arr[1], 0.001f)
        assertEquals(13f, arr[2], 0.001f)
        assertEquals(-33f, arr[3], 0.001f)
        assertEquals(14f, arr[4], 0.001f)
        assertEquals(15f, arr[5], 0.001f)
        assertEquals(16f, arr[6], 0.001f)
        assertEquals(17f, arr[7], 0.001f)
        assertEquals(-20f, arr[8], 0.001f)
        assertEquals(2f, arr[9], 0.001f)
        assertEquals(-4f, arr[10], 0.001f)
        assertEquals(1f, arr[11], 0.001f)
        assertEquals(-1f, arr[12], 0.001f)
        assertEquals(66f, arr[13], 0.001f)
        assertEquals(3f, arr[14], 0.001f)
        assertEquals(4f, arr[15], 0.001f)
        assertEquals(5f, arr[16], 0.001f)
        assertEquals(-2f, arr[17], 0.001f)
        assertEquals(-3f, arr[18], 0.001f)
        assertEquals(2.5f, arr[19], 0.001f)
        assertEquals(3.5f, arr[20], 0.001f)
        assertEquals(1.5f, arr[21], 0.001f)
        assertEquals(40f, arr[22], 0.001f)
        assertEquals(80f, arr[23], 0.001f)
        assertEquals(2f, arr[24], 0.001f)
        assertEquals(0f, arr[25], 0.001f)
    }

    @Test
    fun `every built-in PresetKind matches itself and is distinct`() {
        val builtins = listOf(
            PresetKind.HARAMAIN to AudioParams.HARAMAIN,
            PresetKind.NABAWI to AudioParams.NABAWI,
            PresetKind.STONE_DOME to AudioParams.STONE_DOME,
            PresetKind.LOCAL_HALL to AudioParams.LOCAL_HALL,
            PresetKind.LEAD to AudioParams.LEAD,
            PresetKind.STUDIO to AudioParams.STUDIO_DRY,
            PresetKind.SIMPLE to AudioParams.SIMPLE_STUDIO,
        )
        for ((kind, params) in builtins) {
            assertTrue("$kind should match itself", params.matchesPreset(kind))
            assertTrue("$kind muted should still match", params.copy(muted = true).matchesPreset(kind))
            for ((otherKind, _) in builtins) {
                if (otherKind != kind) {
                    assertFalse("$kind should not match $otherKind", params.matchesPreset(otherKind))
                }
            }
            assertFalse(params.matchesPreset(PresetKind.CUSTOM))
            assertEquals("$kind should round-trip through detectPreset()", kind, params.detectPreset())
        }
        assertEquals(PresetKind.CUSTOM, AudioParams.HARAMAIN.copy(roomSizePercent = 77f).detectPreset())
    }
}
