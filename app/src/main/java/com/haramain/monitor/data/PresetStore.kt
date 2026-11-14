package com.haramain.monitor.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.haramain.monitor.audio.AudioParams
import com.haramain.monitor.audio.PresetKind
import androidx.datastore.preferences.core.stringPreferencesKey
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

    // ---- per-microphone memory (v2.4): each input device keeps its own sound ----

    private fun devicePrefix(deviceId: Int) = "dev" + deviceId + "_"

    suspend fun saveDeviceParams(deviceId: Int, params: AudioParams, kind: PresetKind) {
        context.dataStore.edit {
            it.writeParams(devicePrefix(deviceId), params)
            it[stringPreferencesKey(devicePrefix(deviceId) + "kind")] = kind.name
        }
    }

    suspend fun deviceParams(deviceId: Int): Pair<AudioParams, PresetKind>? {
        val prefs = context.dataStore.data.first()
        val params = prefs.readParams(devicePrefix(deviceId)) ?: return null
        val kindName = prefs[stringPreferencesKey(devicePrefix(deviceId) + "kind")]
        val kind = runCatching { PresetKind.valueOf(kindName ?: "CUSTOM") }
            .getOrDefault(PresetKind.CUSTOM)
        return params to kind
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
            eqBand60Db = this[floatPreferencesKey(prefix + "geq60")] ?: 0f,
            eqBand250Db = this[floatPreferencesKey(prefix + "geq250")] ?: 0f,
            eqBand1kDb = this[floatPreferencesKey(prefix + "geq1k")] ?: 0f,
            eqBand4kDb = this[floatPreferencesKey(prefix + "geq4k")] ?: 0f,
            eqBand12kDb = this[floatPreferencesKey(prefix + "geq12k")] ?: 0f,
            revDampingPercent = this[floatPreferencesKey(prefix + "revdamp")] ?: 50f,
            revWidthPercent = this[floatPreferencesKey(prefix + "revwidth")] ?: 100f,
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
        this[floatPreferencesKey(prefix + "geq60")] = p.eqBand60Db
        this[floatPreferencesKey(prefix + "geq250")] = p.eqBand250Db
        this[floatPreferencesKey(prefix + "geq1k")] = p.eqBand1kDb
        this[floatPreferencesKey(prefix + "geq4k")] = p.eqBand4kDb
        this[floatPreferencesKey(prefix + "geq12k")] = p.eqBand12kDb
        this[floatPreferencesKey(prefix + "revdamp")] = p.revDampingPercent
        this[floatPreferencesKey(prefix + "revwidth")] = p.revWidthPercent
    }

    private companion object {
        const val PREFIX_CUSTOM = "custom_"
        const val PREFIX_AUTO = "auto_"
        const val PREFIX_LAST = "last_"
    }
}
