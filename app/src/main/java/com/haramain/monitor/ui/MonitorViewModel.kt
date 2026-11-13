package com.haramain.monitor.ui

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haramain.monitor.audio.AudioParams
import com.haramain.monitor.audio.AudioRouter
import com.haramain.monitor.audio.PresetKind
import com.haramain.monitor.audio.RouteDevice
import com.haramain.monitor.audio.matchesPreset
import com.haramain.monitor.data.PresetStore
import com.haramain.monitor.data.SettingsStore
import com.haramain.monitor.data.ThemeMode
import com.haramain.monitor.service.MicMonitorService
import com.haramain.monitor.service.MonitorState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val store = PresetStore(app)
    private val settings = SettingsStore(app)

    // ---- app settings (theme / lock / resume) ----
    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val settingsLocked: StateFlow<Boolean> = settings.settingsLocked
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hasPin: StateFlow<Boolean> = settings.hasPin
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val resumeOnLaunch: StateFlow<Boolean> = settings.resumeOnLaunch
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }
    fun setResume(enabled: Boolean) = viewModelScope.launch { settings.setResume(enabled) }
    fun lockSettings() = viewModelScope.launch { settings.setLocked(true) }
    fun setPin(pin: String?) = viewModelScope.launch { settings.setPin(pin) }

    fun unlockSettings(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = settings.verifyPin(pin)
            if (ok) settings.setLocked(false)
            onResult(ok)
        }
    }

    // ---- recording ----
    val isRecording: StateFlow<Boolean> = MonitorState.isRecording
    val recordingStartedAtMs: StateFlow<Long> = MonitorState.recordingStartedAtMs

    fun toggleRecording(context: Context) {
        context.startService(MicMonitorService.toggleRecordIntent(context))
    }

    /** The router lives in MonitorState so the Service drives the same instance. */
    val router: AudioRouter = MonitorState.router ?: AudioRouter(app).also {
        MonitorState.router = it
    }

    val params: StateFlow<AudioParams> = MonitorState.params
    val isRunning: StateFlow<Boolean> = MonitorState.isRunning
    val inputPeakDb: StateFlow<Float> = MonitorState.inputPeakDb
    val outputPeakDb: StateFlow<Float> = MonitorState.outputPeakDb
    val sampleRate: StateFlow<Int> = MonitorState.sampleRate
    val latencyMs: StateFlow<Float> = MonitorState.latencyMs
    val xRuns: StateFlow<Int> = MonitorState.xRuns

    val inputs: StateFlow<List<RouteDevice>> = router.inputs
    val outputs: StateFlow<List<RouteDevice>> = router.outputs
    val selectedInputId: StateFlow<Int> = router.selectedInputId
    val selectedOutputId: StateFlow<Int> = router.selectedOutputId

    private val _activePreset = MutableStateFlow(PresetKind.ORIGINAL)   // boots on the user's own settings
    val activePreset: StateFlow<PresetKind> = _activePreset.asStateFlow()

    private val _hasCustom = MutableStateFlow(false)
    val hasCustom: StateFlow<Boolean> = _hasCustom.asStateFlow()

    private val _feedbackRisk = MutableStateFlow(true)
    val feedbackRisk: StateFlow<Boolean> = _feedbackRisk.asStateFlow()

    val customPreset: StateFlow<AudioParams?> = store.customPreset
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        router.startListening()
        // Restore last-used parameters once.
        viewModelScope.launch {
            store.lastParams.collect { restored ->
                if (MonitorState.params.value == AudioParams.ORIGINAL && restored != AudioParams.ORIGINAL) {
                    MonitorState.params.value = restored
                    _activePreset.value = detectPreset(restored)
                }
            }
        }
        viewModelScope.launch {
            store.customPreset.collect { _hasCustom.value = it != null }
        }
        // Persist edits + keep preset highlighting honest.
        viewModelScope.launch {
            MonitorState.params.collect { p ->
                store.saveLast(p)
                _activePreset.value = detectPreset(p)
            }
        }
        // Feedback risk follows route changes.
        viewModelScope.launch {
            router.outputs.collect { refreshRisk() }
        }
        viewModelScope.launch {
            router.inputs.collect { refreshRisk() }
        }
    }

    private fun refreshRisk() {
        _feedbackRisk.value = router.isFeedbackRisk()
    }

    fun update(transform: (AudioParams) -> AudioParams) {
        MonitorState.params.value = transform(MonitorState.params.value)
    }

    fun selectPreset(kind: PresetKind) {
        val current = MonitorState.params.value
        MonitorState.params.value = when (kind) {
            PresetKind.HARAMAIN -> AudioParams.HARAMAIN.copy(muted = current.muted)
            PresetKind.NABAWI -> AudioParams.NABAWI.copy(muted = current.muted)
            PresetKind.STUDIO -> AudioParams.STUDIO_DRY.copy(muted = current.muted)
            PresetKind.ORIGINAL -> AudioParams.ORIGINAL.copy(muted = current.muted)
            PresetKind.CUSTOM -> customPreset.value?.copy(muted = current.muted) ?: current
        }
        _activePreset.value = kind
    }

    fun saveCustom() {
        viewModelScope.launch { store.saveCustom(MonitorState.params.value) }
        _activePreset.value = PresetKind.CUSTOM
    }

    fun selectInput(deviceId: Int) {
        router.selectInput(deviceId)
        refreshRisk()
    }

    fun selectOutput(deviceId: Int) {
        router.selectOutput(deviceId)
        refreshRisk()
    }

    fun toggleMonitor(context: Context) {
        if (MonitorState.isRunning.value) {
            context.startService(MicMonitorService.stopIntent(context))
        } else {
            ContextCompat.startForegroundService(context, MicMonitorService.startIntent(context))
        }
    }

    fun toggleMute() = update { it.copy(muted = !it.muted) }

    private fun detectPreset(p: AudioParams): PresetKind = when {
        p.matchesPreset(PresetKind.HARAMAIN) -> PresetKind.HARAMAIN
        p.matchesPreset(PresetKind.NABAWI) -> PresetKind.NABAWI
        p.matchesPreset(PresetKind.STUDIO) -> PresetKind.STUDIO
        p.matchesPreset(PresetKind.ORIGINAL) -> PresetKind.ORIGINAL
        else -> PresetKind.CUSTOM
    }

    override fun onCleared() {
        router.stopListening()
        super.onCleared()
    }
}
