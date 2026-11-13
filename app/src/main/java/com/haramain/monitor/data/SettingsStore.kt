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
    }
}
