package com.haramain.monitor.data

import android.content.Context
import com.haramain.monitor.audio.AudioParams
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Export / import of everything that makes the app "yours", as one JSON file:
 * presets (custom, auto, last-used), theme, lock on/off, resume, mic names
 * and hidden entries, and section layout.
 *
 * Deliberately NOT exported: the lock PIN (a settings file must never be a
 * key), the recording-folder URI (a per-device SAF permission), and the
 * was-running flag (meaningless on another device).
 */
object SettingsTransfer {

    private const val APP_TAG = "haramain-monitor-settings"
    private const val FORMAT_VERSION = 1

    suspend fun buildExport(context: Context): String {
        val presets = PresetStore(context)
        val settings = SettingsStore(context)
        val (custom, auto, last) = presets.snapshot()

        val root = JSONObject()
        root.put("app", APP_TAG)
        root.put("version", FORMAT_VERSION)

        val presetsJson = JSONObject()
        custom?.let { presetsJson.put("custom", it.toJson()) }
        auto?.let { presetsJson.put("auto", it.toJson()) }
        presetsJson.put("last", last.toJson())
        root.put("presets", presetsJson)

        val settingsJson = JSONObject()
        settingsJson.put("theme", settings.themeMode.first().name)
        settingsJson.put("locked", settings.settingsLocked.first())
        settingsJson.put("resumeOnLaunch", settings.resumeOnLaunch.first())
        val labels = JSONObject()
        settings.micLabels.first().forEach { (id, label) -> labels.put(id.toString(), label) }
        settingsJson.put("micLabels", labels)
        settingsJson.put("hiddenMicIds", settings.hiddenMicIds.first().joinToString(","))
        val sections = JSONObject()
        settings.openSections.first().forEach { (id, open) -> sections.put(id, open) }
        settingsJson.put("openSections", sections)
        root.put("settings", settingsJson)

        return root.toString(2)
    }

    /** Applies a previously exported file. Returns false if it isn't one of ours. */
    suspend fun import(context: Context, json: String): Boolean {
        return try {
            importInternal(context, json)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun importInternal(context: Context, json: String): Boolean {
        val root = JSONObject(json)
        if (root.optString("app") != APP_TAG) return false
        val presets = PresetStore(context)
        val settings = SettingsStore(context)

        root.optJSONObject("presets")?.let { p ->
            if (p.has("custom")) presets.saveCustom(paramsFromJson(p.getJSONObject("custom")))
            else presets.clearCustom()
            if (p.has("auto")) presets.saveAuto(paramsFromJson(p.getJSONObject("auto")))
            else presets.clearAuto()
            if (p.has("last")) presets.saveLast(paramsFromJson(p.getJSONObject("last")))
        }

        root.optJSONObject("settings")?.let { s ->
            if (s.has("theme")) {
                runCatching { ThemeMode.valueOf(s.getString("theme")) }
                    .getOrNull()?.let { settings.setTheme(it) }
            }
            if (s.has("locked")) settings.setLocked(s.getBoolean("locked"))
            if (s.has("resumeOnLaunch")) settings.setResume(s.getBoolean("resumeOnLaunch"))
            if (s.has("micLabels") || s.has("hiddenMicIds")) {
                val labels = mutableMapOf<Int, String>()
                s.optJSONObject("micLabels")?.let { lj ->
                    lj.keys().forEach { key -> key.toIntOrNull()?.let { labels[it] = lj.getString(key) } }
                }
                val hidden = (s.optString("hiddenMicIds") ?: "")
                    .split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                settings.restoreTeaching(labels, hidden)
            }
            if (s.has("openSections")) {
                val map = mutableMapOf<String, Boolean>()
                val sj = s.getJSONObject("openSections")
                sj.keys().forEach { key -> map[key] = sj.getBoolean(key) }
                settings.setOpenSections(map)
            }
        }
        return true
    }

    private fun AudioParams.toJson(): JSONObject {
        val j = JSONObject()
        j.put("micBoostPercent", micBoostPercent.toDouble())
        j.put("staticRemover", staticRemover.toDouble())
        j.put("noiseSuppress", noiseSuppress.toDouble())
        j.put("noiseSuppressOn", noiseSuppressOn)
        j.put("gateThresholdDb", gateThresholdDb.toDouble())
        j.put("deEsserPercent", deEsserPercent.toDouble())
        j.put("compressorPercent", compressorPercent.toDouble())
        j.put("roomSizePercent", roomSizePercent.toDouble())
        j.put("wetDryPercent", wetDryPercent.toDouble())
        j.put("balancePercent", balancePercent.toDouble())
        j.put("eqLowGainDb", eqLowGainDb.toDouble())
        j.put("eqHighGainDb", eqHighGainDb.toDouble())
        j.put("masterGainDb", masterGainDb.toDouble())
        j.put("safetyOn", safetyOn)
        j.put("safetyCeilingDb", safetyCeilingDb.toDouble())
        return j
    }

    private fun paramsFromJson(j: JSONObject): AudioParams {
        fun f(key: String, default: Float): Float =
            if (j.has(key)) j.getDouble(key).toFloat() else default
        fun b(key: String, default: Boolean): Boolean =
            if (j.has(key)) j.getBoolean(key) else default
        val d = AudioParams.ORIGINAL
        return AudioParams(
            micBoostPercent = f("micBoostPercent", d.micBoostPercent),
            staticRemover = f("staticRemover", d.staticRemover),
            noiseSuppress = f("noiseSuppress", d.noiseSuppress),
            noiseSuppressOn = b("noiseSuppressOn", d.noiseSuppressOn),
            gateThresholdDb = f("gateThresholdDb", d.gateThresholdDb),
            deEsserPercent = f("deEsserPercent", d.deEsserPercent),
            compressorPercent = f("compressorPercent", d.compressorPercent),
            roomSizePercent = f("roomSizePercent", d.roomSizePercent),
            wetDryPercent = f("wetDryPercent", d.wetDryPercent),
            balancePercent = f("balancePercent", d.balancePercent),
            eqLowGainDb = f("eqLowGainDb", d.eqLowGainDb),
            eqHighGainDb = f("eqHighGainDb", d.eqHighGainDb),
            masterGainDb = f("masterGainDb", d.masterGainDb),
            safetyOn = b("safetyOn", d.safetyOn),
            safetyCeilingDb = f("safetyCeilingDb", d.safetyCeilingDb),
            muted = false,
        )
    }
}
