package com.haramain.monitor.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.haramain.monitor.audio.AudioParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "haramain_presets")

/** DataStore persistence: one user Custom preset + the last-used parameters. */
class PresetStore(private val context: Context) {

    val customPreset: Flow<AudioParams?> =
        context.dataStore.data.map { prefs -> prefs.readParams(PREFIX_CUSTOM) }

    val autoPreset: Flow<AudioParams?> =
        context.dataStore.data.map { prefs -> prefs.readParams(PREFIX_AUTO) }

    val lastParams: Flow<AudioParams> =
        context.dataStore.data.map { prefs -> prefs.readParams(PREFIX_LAST) ?: AudioParams.ORIGINAL }

    suspend fun saveCustom(params: AudioParams) {
        context.dataStore.edit { it.writeParams(PREFIX_CUSTOM, params) }
    }

    /** (custom, auto, last) snapshot for settings export. */
    suspend fun snapshot(): Triple<AudioParams?, AudioParams?, AudioParams> = Triple(
        context.dataStore.data.first().readParams(PREFIX_CUSTOM),
        context.dataStore.data.first().readParams(PREFIX_AUTO),
        context.dataStore.data.first().readParams(PREFIX_LAST) ?: AudioParams.ORIGINAL,
    )

    suspend fun clearCustom() {
        context.dataStore.edit { it.remove(floatPreferencesKey(PREFIX_CUSTOM + "boost")) }
    }

    suspend fun clearAuto() {
        context.dataStore.edit { it.remove(floatPreferencesKey(PREFIX_AUTO + "boost")) }
    }

    suspend fun saveAuto(params: AudioParams) {
        context.dataStore.edit { it.writeParams(PREFIX_AUTO, params) }
    }

    suspend fun saveLast(params: AudioParams) {
        context.dataStore.edit { it.writeParams(PREFIX_LAST, params) }
    }

    // ---- (de)serialization: one key per field, prefixed per slot ----

    private fun Preferences.readParams(prefix: String): AudioParams? {
        val boost = this[floatPreferencesKey(prefix + "boost")] ?: return null
        return AudioParams(
            micBoostPercent = boost,
            staticRemover = this[floatPreferencesKey(prefix + "static")] ?: 60f,
            noiseSuppress = this[floatPreferencesKey(prefix + "suppress")] ?: 40f,
            noiseSuppressOn = this[booleanPreferencesKey(prefix + "suppressOn")] ?: true,
            gateThresholdDb = this[floatPreferencesKey(prefix + "gate")] ?: -45f,
            deEsserPercent = this[floatPreferencesKey(prefix + "deesser")] ?: 50f,
            compressorPercent = this[floatPreferencesKey(prefix + "comp")] ?: 70f,
            roomSizePercent = this[floatPreferencesKey(prefix + "room")] ?: 95f,
            wetDryPercent = this[floatPreferencesKey(prefix + "mix")] ?: 52f,
            balancePercent = this[floatPreferencesKey(prefix + "balance")] ?: 0f,
            eqLowGainDb = this[floatPreferencesKey(prefix + "eqlow")] ?: 0f,
            eqHighGainDb = this[floatPreferencesKey(prefix + "eqhigh")] ?: 0f,
            masterGainDb = this[floatPreferencesKey(prefix + "master")] ?: 0f,
            btDuckPercent = this[floatPreferencesKey(prefix + "duck")] ?: 50f,
            btDuckOn = this[booleanPreferencesKey(prefix + "duckOn")] ?: false,
            eqWarmthDb = this[floatPreferencesKey(prefix + "warmth")] ?: 0f,
            eqClarityDb = this[floatPreferencesKey(prefix + "clarity")] ?: 0f,
            eqAirDb = this[floatPreferencesKey(prefix + "air")] ?: 0f,
            safetyOn = this[booleanPreferencesKey(prefix + "safetyOn")] ?: true,
            safetyCeilingDb = this[floatPreferencesKey(prefix + "ceiling")] ?: -3f,
            muted = false,
        )
    }

    private fun MutablePreferences.writeParams(prefix: String, p: AudioParams) {
        this[floatPreferencesKey(prefix + "boost")] = p.micBoostPercent
        this[floatPreferencesKey(prefix + "static")] = p.staticRemover
        this[floatPreferencesKey(prefix + "suppress")] = p.noiseSuppress
        this[booleanPreferencesKey(prefix + "suppressOn")] = p.noiseSuppressOn
        this[floatPreferencesKey(prefix + "gate")] = p.gateThresholdDb
        this[floatPreferencesKey(prefix + "deesser")] = p.deEsserPercent
        this[floatPreferencesKey(prefix + "comp")] = p.compressorPercent
        this[floatPreferencesKey(prefix + "room")] = p.roomSizePercent
        this[floatPreferencesKey(prefix + "mix")] = p.wetDryPercent
        this[floatPreferencesKey(prefix + "balance")] = p.balancePercent
        this[floatPreferencesKey(prefix + "eqlow")] = p.eqLowGainDb
        this[floatPreferencesKey(prefix + "eqhigh")] = p.eqHighGainDb
        this[floatPreferencesKey(prefix + "master")] = p.masterGainDb
        this[floatPreferencesKey(prefix + "duck")] = p.btDuckPercent
        this[booleanPreferencesKey(prefix + "duckOn")] = p.btDuckOn
        this[floatPreferencesKey(prefix + "warmth")] = p.eqWarmthDb
        this[floatPreferencesKey(prefix + "clarity")] = p.eqClarityDb
        this[floatPreferencesKey(prefix + "air")] = p.eqAirDb
        this[booleanPreferencesKey(prefix + "safetyOn")] = p.safetyOn
        this[floatPreferencesKey(prefix + "ceiling")] = p.safetyCeilingDb
    }

    private companion object {
        const val PREFIX_CUSTOM = "custom_"
        const val PREFIX_AUTO = "auto_"
        const val PREFIX_LAST = "last_"
    }
}
