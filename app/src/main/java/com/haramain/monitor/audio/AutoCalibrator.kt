package com.haramain.monitor.audio

import kotlin.math.min

/** One calibration measurement of the raw microphone input. */
data class CalStats(val rmsDb: Float, val peakDb: Float, val hfDb: Float)

/**
 * Turns a "breathe, don't speak" room sample (+ an optional voice sample)
 * into a full parameter set — with hard guardrails so auto-setup can never
 * park the audio against the limiter:
 *
 *  - Boost is derived from the measured PEAK and capped so the loudest
 *    moment lands near −9 dBFS, 6 dB below the −3 dBFS safety ceiling.
 *  - If the mic is already hot (peak > −9 dBFS raw), boost stays at 0 dB.
 *  - Suppression depth is capped at 60 (the stage that once ate the voice).
 *  - The gate is set above the measured breath floor but never above the
 *    voice's own average level minus 6 dB, so it can't gate the recitation.
 *  - Reverb / EQ / balance / safety are artistic or protective — untouched.
 */
object AutoCalibrator {

    data class Result(val params: AudioParams, val notes: List<String>)

    fun compute(current: AudioParams, noise: CalStats, voice: CalStats?): Result {
        val notes = mutableListOf<String>()
        val noiseRms = noise.rmsDb.coerceAtLeast(-75f)
        if (noise.rmsDb < -75f) {
            notes += "Room sample was near-silent — if that's not right, check the selected mic and run this again."
        }

        // ---- Boost (only from a voice sample; otherwise keep the user's) ----
        var boostDb = currentBoostDb(current)
        if (voice != null) {
            if (voice.peakDb > -9f) {
                boostDb = 0f
                notes += "Your mic is already hot (peaks ${fmt(voice.peakDb)} dB) — no boost added. " +
                    "Move the mic a little farther away if it sounds harsh."
            } else {
                val byRms = -20f - voice.rmsDb          // aim average voice at −20 dBFS
                val byPeak = -9f - voice.peakDb          // loudest moment capped at −9 dBFS
                boostDb = min(byRms, byPeak).coerceIn(0f, 18f)
                notes += "Boost set to +${fmt(boostDb)} dB — your peaks land near −9 dB, " +
                    "well clear of the −3 dB safety ceiling, so the limiter stays out of the way."
            }
        } else {
            notes += "No voice sample — boost left at your current +${fmt(boostDb)} dB."
        }
        val boostPercent = (boostDb / 12f * 100f).coerceIn(0f, 200f)

        // ---- Gate: above the breath floor, below the voice ----
        var gate = (noiseRms + boostDb + 10f).coerceIn(-70f, -30f)
        if (voice != null) {
            val voiceCeiling = voice.rmsDb + boostDb - 6f
            if (gate > voiceCeiling) {
                gate = voiceCeiling.coerceAtLeast(-70f)
                notes += "Gate lowered to ${fmt(gate)} dB so it can never sit above your own voice."
            }
        }
        notes += "Gate set to ${fmt(gate)} dB — just above your breathing and room noise " +
            "(measured floor ${fmt(noiseRms)} dB)."

        // ---- Suppression / static from the floor; comp / de-esser from the voice ----
        val suppress = when {
            noiseRms < -55f -> 25f
            noiseRms < -45f -> 40f
            else -> 55f
        }
        val static = if (noiseRms > -50f) 70f else 55f

        var comp = current.compressorPercent
        var deEsser = current.deEsserPercent
        if (voice != null) {
            val crest = voice.peakDb - voice.rmsDb
            comp = when {
                crest > 14f -> 65f
                crest >= 10f -> 55f
                else -> 40f
            }
            val hfExcess = voice.hfDb - voice.rmsDb
            deEsser = when {
                hfExcess > -10f -> 60f
                hfExcess > -16f -> 48f
                else -> 38f
            }
        }

        val params = current.copy(
            micBoostPercent = boostPercent,
            staticRemover = static,
            noiseSuppress = suppress,
            noiseSuppressOn = true,
            gateThresholdDb = gate,
            deEsserPercent = deEsser,
            compressorPercent = comp,
            muted = current.muted,
        )
        return Result(params, notes)
    }

    private fun currentBoostDb(p: AudioParams): Float = p.micBoostPercent / 100f * 12f

    private fun fmt(v: Float): String = "%.0f".format(v)
}
