package com.haramain.monitor.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

private val Context.settingsDataStore by preferencesDataStore(name = "haramain_settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** App-level settings: theme, settings lock (+ optional PIN), resume behavior. */
class SettingsStore(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[THEME] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }
    val settingsLocked: Flow<Boolean> =
        context.settingsDataStore.data.map { it[LOCKED] ?: false }
    val hasPin: Flow<Boolean> =
        context.settingsDataStore.data.map { !it[PIN_HASH].isNullOrEmpty() }
    val resumeOnLaunch: Flow<Boolean> =
        context.settingsDataStore.data.map { it[RESUME] ?: true }
    val wasRunning: Flow<Boolean> =
        context.settingsDataStore.data.map { it[WAS_RUNNING] ?: false }
    /** SAF tree URI of the user's chosen recording folder; "" = app folder only. */
    val exportFolderUri: Flow<String> =
        context.settingsDataStore.data.map { it[EXPORT_FOLDER] ?: "" }

    suspend fun setExportFolder(uri: String) {
        context.settingsDataStore.edit { it[EXPORT_FOLDER] = uri }
    }

    // ---- user-taught mic names ("teach it once") ----
    // Android cannot say which physical mic a logical entry belongs to, so
    // the user names entries once; persisted by AudioDeviceInfo id.

    /** deviceId -> custom label. Stored as "id=label;id=label" (%-escaped). */
    val micLabels: Flow<Map<Int, String>> =
        context.settingsDataStore.data.map { prefs -> decodeLabels(prefs[MIC_LABELS] ?: "") }

    val hiddenMicIds: Flow<Set<Int>> =
        context.settingsDataStore.data.map { prefs ->
            (prefs[HIDDEN_MICS] ?: "").split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
        }

    suspend fun setMicLabel(deviceId: Int, label: String?) {
        context.settingsDataStore.edit { prefs ->
            val map = decodeLabels(prefs[MIC_LABELS] ?: "").toMutableMap()
            if (label.isNullOrBlank()) map.remove(deviceId) else map[deviceId] = label.trim()
            prefs[MIC_LABELS] = encodeLabels(map)
        }
    }

    suspend fun setMicHidden(deviceId: Int, hidden: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val ids = (prefs[HIDDEN_MICS] ?: "").split(",")
                .mapNotNull { it.trim().toIntOrNull() }.toMutableSet()
            if (hidden) ids += deviceId else ids -= deviceId
            prefs[HIDDEN_MICS] = ids.joinToString(",")
        }
    }

    suspend fun clearMicTeaching() {
        context.settingsDataStore.edit {
            it.remove(MIC_LABELS)
            it.remove(HIDDEN_MICS)
        }
    }

    /** Bulk restore (settings import): labels + hidden ids in one edit. */
    suspend fun restoreTeaching(labels: Map<Int, String>, hidden: Set<Int>) {
        context.settingsDataStore.edit { prefs ->
            prefs[MIC_LABELS] = encodeLabels(labels)
            prefs[HIDDEN_MICS] = hidden.joinToString(",")
        }
    }

    // ---- v2.0 feature preferences ----

    val bufferMode: Flow<Int> =
        context.settingsDataStore.data.map { it[BUFFER_MODE] ?: 1 }

    suspend fun setBufferMode(bursts: Int) {
        context.settingsDataStore.edit { it[BUFFER_MODE] = bursts.coerceIn(1, 4) }
    }

    /** Auto-stop after this many minutes with no voice. 0 = off. Default 10. */
    val autoStopMinutes: Flow<Int> =
        context.settingsDataStore.data.map { it[AUTO_STOP_MIN] ?: 10 }

    suspend fun setAutoStopMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[AUTO_STOP_MIN] = minutes.coerceIn(0, 60) }
    }

    /** Start recording automatically whenever monitoring starts. Default off. */
    val autoRecord: Flow<Boolean> =
        context.settingsDataStore.data.map { it[AUTO_RECORD] ?: false }

    suspend fun setAutoRecord(on: Boolean) {
        context.settingsDataStore.edit { it[AUTO_RECORD] = on }
    }

    /** Voice-activated recording: record only while the voice is present. Default off. */
    val voiceActivated: Flow<Boolean> =
        context.settingsDataStore.data.map { it[VOICE_ACTIVATED] ?: false }

    suspend fun setVoiceActivated(on: Boolean) {
        context.settingsDataStore.edit { it[VOICE_ACTIVATED] = on }
    }

    /** Remember a separate sound per microphone. Default on. */
    val perMicMemory: Flow<Boolean> =
        context.settingsDataStore.data.map { it[PER_MIC_MEMORY] ?: true }

    suspend fun setPerMicMemory(on: Boolean) {
        context.settingsDataStore.edit { it[PER_MIC_MEMORY] = on }
    }

    val pitchOn: Flow<Boolean> =
        context.settingsDataStore.data.map { it[PITCH_ON] ?: false }

    suspend fun setPitchOn(on: Boolean) {
        context.settingsDataStore.edit { it[PITCH_ON] = on }
    }

    val recordDry: Flow<Boolean> =
        context.settingsDataStore.data.map { it[RECORD_DRY] ?: false }

    suspend fun setRecordDry(dry: Boolean) {
        context.settingsDataStore.edit { it[RECORD_DRY] = dry }
    }

    // ---- collapsible section state (persisted across app restarts) ----

    val openSections: Flow<Map<String, Boolean>> =
        context.settingsDataStore.data.map { prefs -> decodeSections(prefs[SECTIONS] ?: "") }

    suspend fun setSectionOpen(id: String, open: Boolean) {
        context.settingsDataStore.edit { prefs ->
            val map = decodeSections(prefs[SECTIONS] ?: "").toMutableMap()
            map[id] = open
            prefs[SECTIONS] = encodeSections(map)
        }
    }

    suspend fun setOpenSections(map: Map<String, Boolean>) {
        context.settingsDataStore.edit { it[SECTIONS] = encodeSections(map) }
    }

    private fun decodeSections(raw: String): Map<String, Boolean> =
        raw.split(";").mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            entry.substring(0, eq) to (entry.substring(eq + 1) == "1")
        }.toMap()

    private fun encodeSections(map: Map<String, Boolean>): String =
        map.entries.joinToString(";") { (id, open) -> "$id=${if (open) "1" else "0"}" }

    private fun decodeLabels(raw: String): Map<Int, String> =
        raw.split(";").mapNotNull { entry ->
            val eq = entry.indexOf('=')
            if (eq <= 0) return@mapNotNull null
            val id = entry.substring(0, eq).toIntOrNull() ?: return@mapNotNull null
            id to unescape(entry.substring(eq + 1))
        }.toMap()

    private fun encodeLabels(map: Map<Int, String>): String =
        map.entries.joinToString(";") { (id, label) -> "$id=${escape(label)}" }

    private fun escape(s: String): String =
        s.replace("%", "%25").replace(";", "%3B").replace("=", "%3D")

    private fun unescape(s: String): String =
        s.replace("%3D", "=").replace("%3B", ";").replace("%25", "%")

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME] = mode.name }
    }

    suspend fun setLocked(locked: Boolean) {
        context.settingsDataStore.edit { it[LOCKED] = locked }
    }

    suspend fun setPin(pin: String?) {
        context.settingsDataStore.edit {
            if (pin.isNullOrEmpty()) it.remove(PIN_HASH) else it[PIN_HASH] = hashPin(pin)
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.settingsDataStore.data.first()[PIN_HASH]
        return stored.isNullOrEmpty() || stored == hashPin(pin)
    }

    suspend fun setResume(enabled: Boolean) {
        context.settingsDataStore.edit { it[RESUME] = enabled }
    }

    suspend fun setWasRunning(running: Boolean) {
        context.settingsDataStore.edit { it[WAS_RUNNING] = running }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val LOCKED = booleanPreferencesKey("settings_locked")
        val PIN_HASH = stringPreferencesKey("lock_pin_hash")
        val RESUME = booleanPreferencesKey("resume_on_launch")
        val WAS_RUNNING = booleanPreferencesKey("was_running")
        val EXPORT_FOLDER = stringPreferencesKey("export_folder_uri")
        val MIC_LABELS = stringPreferencesKey("mic_labels")
        val HIDDEN_MICS = stringPreferencesKey("hidden_mic_ids")
        val SECTIONS = stringPreferencesKey("open_sections")
        val BUFFER_MODE = androidx.datastore.preferences.core.intPreferencesKey("buffer_mode")
        val AUTO_STOP_MIN = androidx.datastore.preferences.core.intPreferencesKey("auto_stop_min")
        val AUTO_RECORD = androidx.datastore.preferences.core.booleanPreferencesKey("auto_record")
        val VOICE_ACTIVATED = androidx.datastore.preferences.core.booleanPreferencesKey("voice_activated")
        val PER_MIC_MEMORY = androidx.datastore.preferences.core.booleanPreferencesKey("per_mic_memory")
        val PITCH_ON = booleanPreferencesKey("pitch_on")
        val RECORD_DRY = booleanPreferencesKey("record_dry")
    }
}
