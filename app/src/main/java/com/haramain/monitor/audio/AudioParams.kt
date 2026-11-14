package com.haramain.monitor.audio

/** Single source of truth for every DSP parameter the UI can touch.
 *  Field order in [toFloatArray] MUST match the JNI layout in native-lib.cpp.
 *
 *  The data-class defaults ARE the "Masjid al-Haram" preset — the exact
 *  numbers the user specified (Room 95, Mix 52, Comp 70, De-Esser 50,
 *  Gate −45) — so the app boots on them. */
data class AudioParams(
    val micBoostPercent: Float = 50f,     // 0..200  (0..+24 dB)
    val staticRemover: Float = 60f,       // 0..100
    val noiseSuppress: Float = 40f,       // 0..100
    val noiseSuppressOn: Boolean = true,
    val gateThresholdDb: Float = -45f,    // -80..0
    val deEsserPercent: Float = 50f,      // 0..100
    val compressorPercent: Float = 70f,   // 0..100
    val roomSizePercent: Float = 95f,     // 0..100
    val wetDryPercent: Float = 52f,       // 0..100
    val balancePercent: Float = 0f,       // -100..+100
    val eqLowGainDb: Float = 0f,          // -12..+12 bass shelf trim
    val eqHighGainDb: Float = 0f,         // -12..+12 treble shelf trim
    val masterGainDb: Float = 0f,         // -24..+12
    val btDuckPercent: Float = 50f,       // 0..100 dry-monitor ducking on Bluetooth
    val btDuckOn: Boolean = false,
    val eqWarmthDb: Float = 0f,           // -12..+12 tone sculptor: 150 Hz
    val eqClarityDb: Float = 0f,          // -12..+12 tone sculptor: 3 kHz
    val eqAirDb: Float = 0f,              // -12..+12 tone sculptor: 10 kHz
    val safetyOn: Boolean = true,
    val safetyCeilingDb: Float = -3f,     // -12..0
    val eqBand60Db: Float = 0f,           // graphic EQ (v2.9), -12..+12
    val eqBand250Db: Float = 0f,
    val eqBand1kDb: Float = 0f,
    val eqBand4kDb: Float = 0f,
    val eqBand12kDb: Float = 0f,
    val revDampingPercent: Float = 50f,   // reverb tail damping; 50 == classic fixed value
    val revWidthPercent: Float = 100f,    // reverb stereo width; 100 == original
    val monitorMode: Int = 0,             // 0 stereo, 1 left ear only, 2 right ear only
    val howlGuardOn: Boolean = true,      // anti-feedback notch guard
    val muted: Boolean = false,
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        micBoostPercent, staticRemover, noiseSuppress, gateThresholdDb,
        deEsserPercent, compressorPercent, roomSizePercent, wetDryPercent,
        balancePercent, masterGainDb, safetyCeilingDb, eqLowGainDb, eqHighGainDb,
        btDuckPercent, eqWarmthDb, eqClarityDb, eqAirDb,
        eqBand60Db, eqBand250Db, eqBand1kDb, eqBand4kDb, eqBand12kDb,
        revDampingPercent, revWidthPercent, monitorMode.toFloat(),
        if (howlGuardOn) 1f else 0f,
    )

    companion object {
        /**
         * Masjid al-Haram — the DEFAULT preset, and the data-class defaults:
         * the exact numbers the user specified (Room 95, Mix 52, Comp 70,
         * De-Esser 50, Gate -45). Expansive marble-hall reflections with
         * crisp vocal presence.
         */
        val HARAMAIN = AudioParams()

        /** Backwards-compatible alias (older code/docs name). */
        val ORIGINAL = HARAMAIN

        /**
         * Masjid an-Nabawi: warm, balanced dome and courtyard diffusion —
         * the gentler of the two holy mosques.
         */
        val NABAWI = AudioParams(
            micBoostPercent = 48f, staticRemover = 55f, noiseSuppress = 35f,
            gateThresholdDb = -48f, deEsserPercent = 50f, compressorPercent = 65f,
            roomSizePercent = 82f, wetDryPercent = 42f,
        )

        /** Historic Stone Dome: rich, resonant stone acoustics. */
        val STONE_DOME = AudioParams(
            micBoostPercent = 50f, staticRemover = 55f, noiseSuppress = 35f,
            gateThresholdDb = -48f, deEsserPercent = 45f, compressorPercent = 65f,
            roomSizePercent = 75f, wetDryPercent = 38f,
        )

        /** Local Masjid Hall: natural medium prayer-hall ambiance. */
        val LOCAL_HALL = AudioParams(
            micBoostPercent = 50f, staticRemover = 50f, noiseSuppress = 35f,
            gateThresholdDb = -50f, deEsserPercent = 45f, compressorPercent = 60f,
            roomSizePercent = 45f, wetDryPercent = 25f,
        )

        /** Studio Dry: zero-reverb vocal isolation. */
        val STUDIO_DRY = AudioParams(
            micBoostPercent = 45f, staticRemover = 70f, noiseSuppress = 50f,
            gateThresholdDb = -55f, deEsserPercent = 40f, compressorPercent = 55f,
            roomSizePercent = 0f, wetDryPercent = 0f,
        )

        /**
         * Lead (Imam): voiced for leading — the voice stays in front,
         * steady and clear, with the space underneath it rather than
         * around it.
         */
        val LEAD = AudioParams(
            micBoostPercent = 50f, staticRemover = 60f, noiseSuppress = 35f,
            gateThresholdDb = -50f, deEsserPercent = 45f, compressorPercent = 65f,
            roomSizePercent = 60f, wetDryPercent = 30f,
        )

        /** Simple Studio: clean, nearly dry, podcast-style — for plain speech. */
        val SIMPLE_STUDIO = AudioParams(
            micBoostPercent = 50f, staticRemover = 60f, noiseSuppress = 45f,
            gateThresholdDb = -50f, deEsserPercent = 40f, compressorPercent = 55f,
            roomSizePercent = 10f, wetDryPercent = 6f,
        )
    }
}

enum class PresetKind(val label: String) {
    HARAMAIN("Masjid al-Haram"),
    NABAWI("Masjid an-Nabawi"),
    STONE_DOME("Historic Stone Dome"),
    LOCAL_HALL("Local Masjid Hall"),
    LEAD("Lead (Imam)"),
    STUDIO("Studio Dry"),
    SIMPLE("Simple Studio"),
    CUSTOM("Custom"),
}

fun AudioParams.matchesPreset(kind: PresetKind): Boolean = when (kind) {
    PresetKind.HARAMAIN -> copy(muted = false) == AudioParams.HARAMAIN.copy(muted = false)
    PresetKind.NABAWI -> copy(muted = false) == AudioParams.NABAWI.copy(muted = false)
    PresetKind.STONE_DOME -> copy(muted = false) == AudioParams.STONE_DOME.copy(muted = false)
    PresetKind.LOCAL_HALL -> copy(muted = false) == AudioParams.LOCAL_HALL.copy(muted = false)
    PresetKind.LEAD -> copy(muted = false) == AudioParams.LEAD.copy(muted = false)
    PresetKind.STUDIO -> copy(muted = false) == AudioParams.STUDIO_DRY.copy(muted = false)
    PresetKind.SIMPLE -> copy(muted = false) == AudioParams.SIMPLE_STUDIO.copy(muted = false)
    PresetKind.CUSTOM -> false
}
