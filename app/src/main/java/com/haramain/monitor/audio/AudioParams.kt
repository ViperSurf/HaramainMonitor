package com.haramain.monitor.audio

/** Single source of truth for every DSP parameter the UI can touch.
 *  Field order in [toFloatArray] MUST match the JNI layout in native-lib.cpp. */
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
    val masterGainDb: Float = 0f,         // -24..+12
    val safetyOn: Boolean = true,
    val safetyCeilingDb: Float = -3f,     // -12..0
    val muted: Boolean = false,
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        micBoostPercent, staticRemover, noiseSuppress, gateThresholdDb,
        deEsserPercent, compressorPercent, roomSizePercent, wetDryPercent,
        balancePercent, masterGainDb, safetyCeilingDb,
    )

    companion object {
        /** The signature preset the app boots with. */
        val HARAMAIN = AudioParams()

        val CHURCH_HALL = AudioParams(
            micBoostPercent = 50f, staticRemover = 50f, noiseSuppress = 30f,
            gateThresholdDb = -50f, deEsserPercent = 40f, compressorPercent = 55f,
            roomSizePercent = 75f, wetDryPercent = 38f,
        )

        val STUDIO_DRY = AudioParams(
            micBoostPercent = 45f, staticRemover = 70f, noiseSuppress = 50f,
            gateThresholdDb = -55f, deEsserPercent = 35f, compressorPercent = 60f,
            roomSizePercent = 12f, wetDryPercent = 8f,
        )
    }
}

enum class PresetKind(val label: String) {
    HARAMAIN("Haramain Cathedral"),
    CHURCH("Church Hall"),
    STUDIO("Studio Dry"),
    CUSTOM("Custom"),
}

fun AudioParams.matchesPreset(kind: PresetKind): Boolean = when (kind) {
    PresetKind.HARAMAIN -> copy(muted = false) == AudioParams.HARAMAIN.copy(muted = false)
    PresetKind.CHURCH -> copy(muted = false) == AudioParams.CHURCH_HALL.copy(muted = false)
    PresetKind.STUDIO -> copy(muted = false) == AudioParams.STUDIO_DRY.copy(muted = false)
    PresetKind.CUSTOM -> false
}
