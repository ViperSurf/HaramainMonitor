package com.haramain.monitor.audio

/** Single source of truth for every DSP parameter the UI can touch.
 *  Field order in [toFloatArray] MUST match the JNI layout in native-lib.cpp.
 *
 *  The data-class defaults ARE the "Haramain (Original)" preset — the exact
 *  numbers the user specified (Room 95, Mix 52, Comp 70, De-Esser 50,
 *  Gate −45) — so the app still boots on them. */
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
    val safetyOn: Boolean = true,
    val safetyCeilingDb: Float = -3f,     // -12..0
    val muted: Boolean = false,
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        micBoostPercent, staticRemover, noiseSuppress, gateThresholdDb,
        deEsserPercent, compressorPercent, roomSizePercent, wetDryPercent,
        balancePercent, masterGainDb, safetyCeilingDb, eqLowGainDb, eqHighGainDb,
    )

    companion object {
        /** The user's own specified settings — the boot default (preset #4). */
        val ORIGINAL = AudioParams()

        /**
         * Masjid al-Haram, from published acoustics research (see README):
         * the marble hall rings for ~6 s naturally, and the engineered sound
         * worshippers actually hear is spacious yet disciplined — the longest
         * tail of any preset, but a slightly lower wet mix than ORIGINAL so
         * the direct voice stays in command, with firm compression like the
         * gates/compressors/limiters chain the Haram's own engineers run.
         */
        val HARAMAIN = AudioParams(
            micBoostPercent = 50f, staticRemover = 55f, noiseSuppress = 35f,
            gateThresholdDb = -48f, deEsserPercent = 45f, compressorPercent = 65f,
            roomSizePercent = 100f, wetDryPercent = 45f,
        )

        /**
         * Masjid an-Nabawi: famously the gentler, sweeter, more intimate of
         * the two — a shorter tail and lower mix, clarity over grandeur.
         */
        val NABAWI = AudioParams(
            micBoostPercent = 48f, staticRemover = 55f, noiseSuppress = 35f,
            gateThresholdDb = -48f, deEsserPercent = 42f, compressorPercent = 58f,
            roomSizePercent = 82f, wetDryPercent = 38f,
        )

        val STUDIO_DRY = AudioParams(
            micBoostPercent = 45f, staticRemover = 70f, noiseSuppress = 50f,
            gateThresholdDb = -55f, deEsserPercent = 35f, compressorPercent = 60f,
            roomSizePercent = 12f, wetDryPercent = 8f,
        )
    }
}

enum class PresetKind(val label: String) {
    HARAMAIN("Haramain"),
    NABAWI("Masjid an-Nabawi"),
    STUDIO("Studio Dry"),
    ORIGINAL("Haramain (Original)"),
    CUSTOM("Custom"),
}

fun AudioParams.matchesPreset(kind: PresetKind): Boolean = when (kind) {
    PresetKind.HARAMAIN -> copy(muted = false) == AudioParams.HARAMAIN.copy(muted = false)
    PresetKind.NABAWI -> copy(muted = false) == AudioParams.NABAWI.copy(muted = false)
    PresetKind.STUDIO -> copy(muted = false) == AudioParams.STUDIO_DRY.copy(muted = false)
    PresetKind.ORIGINAL -> copy(muted = false) == AudioParams.ORIGINAL.copy(muted = false)
    PresetKind.CUSTOM -> false
}
