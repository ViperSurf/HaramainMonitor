package com.haramain.monitor.ui

import android.app.Application
import android.content.Intent
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.haramain.monitor.audio.AudioParams
import com.haramain.monitor.audio.NativeAudioEngine
import com.haramain.monitor.audio.AudioRouter
import com.haramain.monitor.audio.isBluetooth
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
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

    // ---- auto calibration ----
    val autoPreset: StateFlow<AudioParams?> = store.autoPreset
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _hasAuto = MutableStateFlow(false)
    val hasAuto: StateFlow<Boolean> = _hasAuto.asStateFlow()

    /** Measures the raw mic for [durationMs]; null if no audio flowed. */
    suspend fun measureStep(durationMs: Long): com.haramain.monitor.audio.CalStats? {
        com.haramain.monitor.audio.NativeAudioEngine.startCalibration()
        kotlinx.coroutines.delay(durationMs)
        val a = com.haramain.monitor.audio.NativeAudioEngine.stopCalibration()
        return if (a[3] > 0.5f) com.haramain.monitor.audio.CalStats(a[0], a[1], a[2]) else null
    }

    fun applyAuto(result: com.haramain.monitor.audio.AutoCalibrator.Result) {
        MonitorState.params.value = result.params
        viewModelScope.launch { store.saveAuto(result.params) }
    }

    fun applySavedAuto() {
        val auto = autoPreset.value ?: return
        MonitorState.params.value = auto.copy(muted = MonitorState.params.value.muted)
    }

    // ---- recording export folder (SAF tree URI string, "" = app folder only) ----
    val exportFolderUri: StateFlow<String> = settings.exportFolderUri
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun setExportFolder(uri: String) = viewModelScope.launch { settings.setExportFolder(uri) }

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

    // ---- user-taught mic names / hiding ----
    val micLabels: StateFlow<Map<Int, String>> = settings.micLabels
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val hiddenMicIds: StateFlow<Set<Int>> = settings.hiddenMicIds
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** What the input picker shows: custom labels applied, hidden entries
     *  removed — except the currently selected one, which is never hidden
     *  out from under an active route. Labels stored as "group:<Base>" are
     *  physical-mic assignments: members of a group are numbered live
     *  ("Bottom Mic 1", "Bottom Mic 2") in device-id order; a group of one
     *  shows the plain base name. */
    val displayInputs: StateFlow<List<RouteDevice>> = kotlinx.coroutines.flow.combine(
        router.inputs, micLabels, hiddenMicIds, router.selectedInputId,
    ) { devices, labels, hidden, selectedId ->
        val groupIndex = mutableMapOf<Int, Pair<String, Int>>()
        val groupSize = mutableMapOf<String, Int>()
        devices.filter { labels[it.id]?.startsWith(MIC_GROUP_PREFIX) == true }
            .groupBy { labels[it.id]!!.removePrefix(MIC_GROUP_PREFIX) }
            .forEach { (base, members) ->
                val sorted = members.sortedBy { it.id }
                groupSize[base] = sorted.size
                sorted.forEachIndexed { i, d -> groupIndex[d.id] = base to (i + 1) }
            }
        devices.mapNotNull { device ->
            if (device.id in hidden && device.id != selectedId) {
                null
            } else {
                val gi = groupIndex[device.id]
                when {
                    gi != null -> device.copy(
                        name = if (groupSize[gi.first]!! > 1) "${gi.first} ${gi.second}" else gi.first,
                    )
                    labels[device.id] != null -> device.copy(name = labels[device.id]!!)
                    else -> device
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setMicLabel(deviceId: Int, label: String?) =
        viewModelScope.launch { settings.setMicLabel(deviceId, label) }

    // ---- section layout (persisted) ----
    val openSections: StateFlow<Map<String, Boolean>> = settings.openSections
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun setSectionOpen(id: String, open: Boolean) =
        viewModelScope.launch { settings.setSectionOpen(id, open) }

    val outputIsBluetooth: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        router.outputs, router.selectedOutputId,
    ) { outs, id -> outs.firstOrNull { it.id == id }?.isBluetooth == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val inputIsBluetooth: StateFlow<Boolean> = kotlinx.coroutines.flow.combine(
        router.inputs, router.selectedInputId,
    ) { ins, id -> ins.firstOrNull { it.id == id }?.isBluetooth == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Split-path fix: monitor through Bluetooth OUT but record from a
     *  phone/USB mic, avoiding the 16 kHz Bluetooth call-mode (HFP/SCO). */
    fun usePhoneOrUsbMic() {
        val candidates = router.inputs.value.filter { !it.isBluetooth }
        val pick = candidates.firstOrNull { !it.isInternal } ?: candidates.firstOrNull()
        pick?.let { selectInput(it.id) }
    }

    // ---- v2.0 feature preferences ----
    val bufferMode: StateFlow<Int> = settings.bufferMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val pitchOn: StateFlow<Boolean> = settings.pitchOn
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val recordDry: StateFlow<Boolean> = settings.recordDry
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val autoStopMinutes: StateFlow<Int> = settings.autoStopMinutes
        .stateIn(viewModelScope, SharingStarted.Eagerly, 10)
    val pitchHz: StateFlow<Float> = MonitorState.pitchHz
    val actGate: StateFlow<Float> = MonitorState.actGate
    val actSuppress: StateFlow<Float> = MonitorState.actSuppress
    val actLimiter: StateFlow<Float> = MonitorState.actLimiter
    val actNotchHz: StateFlow<Float> = MonitorState.actNotchHz
    val sessionStartMs: StateFlow<Long> = MonitorState.sessionStartMs
    val bufferFrames: StateFlow<Int> = MonitorState.bufferFrames

    fun setBufferMode(bursts: Int) = viewModelScope.launch { settings.setBufferMode(bursts) }
    fun setPitchOn(on: Boolean) = viewModelScope.launch { settings.setPitchOn(on) }
    fun setRecordDry(dry: Boolean) = viewModelScope.launch { settings.setRecordDry(dry) }
    fun setAutoStopMinutes(m: Int) = viewModelScope.launch { settings.setAutoStopMinutes(m) }

    val autoRecord: StateFlow<Boolean> = settings.autoRecord
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val perMicMemory: StateFlow<Boolean> = settings.perMicMemory
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val voiceActivated: StateFlow<Boolean> = settings.voiceActivated
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Auto-record and voice-activated recording are mutually exclusive:
    // enabling one turns the other off (the service also guards).
    fun setAutoRecord(on: Boolean) = viewModelScope.launch {
        settings.setAutoRecord(on)
        if (on) settings.setVoiceActivated(false)
    }

    val autoStartMic: StateFlow<Boolean> = settings.autoStartMic
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoStartMic(on: Boolean) = viewModelScope.launch { settings.setAutoStartMic(on) }

    val stopOnDisconnect: StateFlow<Boolean> = settings.stopOnDisconnect
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setStopOnDisconnect(on: Boolean) = viewModelScope.launch { settings.setStopOnDisconnect(on) }

    val automationScope: StateFlow<Int> = settings.automationScope
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun setAutomationScope(s: Int) = viewModelScope.launch { settings.setAutomationScope(s) }

    val autoTrim: StateFlow<Boolean> = settings.autoTrim
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAutoTrim(on: Boolean) = viewModelScope.launch { settings.setAutoTrim(on) }

    val practiceSummary: StateFlow<String> = settings.practiceStats
        .map { s ->
            fun fmt(sec: Long): String {
                val m = sec / 60
                return if (m >= 60) "${m / 60} h ${m % 60} min" else "$m min"
            }
            buildString {
                append("Total ${fmt(s.totalSec)} · ${s.sessions} sessions · Today ${fmt(s.todaySec)}")
                if (s.goalMin > 0) {
                    append(" of ${s.goalMin} min goal")
                    if (s.streakDays > 0) append(" · ${s.streakDays}-day streak")
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val practiceGoalMin: StateFlow<Int> = settings.practiceGoalMin
        .stateIn(viewModelScope, SharingStarted.Eagerly, 10)

    fun setPracticeGoalMin(m: Int) = viewModelScope.launch { settings.setPracticeGoalMin(m) }

    fun setVoiceActivated(on: Boolean) = viewModelScope.launch {
        settings.setVoiceActivated(on)
        if (on) settings.setAutoRecord(false)
    }
    fun setPerMicMemory(on: Boolean) = viewModelScope.launch { settings.setPerMicMemory(on) }

    /** A/B compare: direct to the engine (momentary, latency-sensitive). */
    fun setBypass(on: Boolean) = NativeAudioEngine.setBypass(on)

    // ---- settings export / import ----
    suspend fun buildExportJson(): String =
        com.haramain.monitor.data.SettingsTransfer.buildExport(getApplication())

    suspend fun importSettings(json: String): Boolean {
        val ok = com.haramain.monitor.data.SettingsTransfer.import(getApplication(), json)
        if (ok) {
            // Bring the imported last-used parameters live immediately.
            MonitorState.params.value = store.lastParams.first()
        }
        return ok
    }

    fun setMicHidden(deviceId: Int, hidden: Boolean) =
        viewModelScope.launch { settings.setMicHidden(deviceId, hidden) }

    fun clearMicTeaching() = viewModelScope.launch { settings.clearMicTeaching() }
    val selectedInputId: StateFlow<Int> = router.selectedInputId
    val selectedOutputId: StateFlow<Int> = router.selectedOutputId

    private val _activePreset = MutableStateFlow(PresetKind.HARAMAIN)   // boots on Masjid al-Haram (the user's own settings)
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
        viewModelScope.launch {
            store.autoPreset.collect { _hasAuto.value = it != null }
        }
        // Keep preset highlighting honest on every emission (cheap,
        // in-memory comparisons only).
        viewModelScope.launch {
            MonitorState.params.collect { p -> _activePreset.value = detectPreset(p) }
        }
        // Persist edits DEBOUNCED (v2.5 smoothness): a slider drag emits
        // dozens of params per second, and a 19-key DataStore file write
        // per emission is pure churn. Only the settled value is saved.
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            MonitorState.params.debounce(600).collect { p -> store.saveLast(p) }
        }
        // Auto-start on connect (v2.9; scope-aware, device-keyed since
        // v3.4): a NEW input device starts monitoring when it matches
        // the automation scope. Keyed to devices, not the picker
        // selection — on the system-default route the selection stays
        // -1 while the engine happily uses the USB mic, which is why
        // the old selected-id rule never fired in real use.
        viewModelScope.launch {
            var prevIds: Set<Int>? = null
            router.inputs.collect { devices ->
                val ids = devices.map { it.id }.toSet()
                val prev = prevIds
                prevIds = ids
                try {
                    if (prev != null && !MonitorState.isRunning.value && settings.autoStartMic.first()) {
                        val scope = settings.automationScope.first()
                        val sel = router.selectedInputId.value
                        val arrived = devices.filter { it.id !in prev }
                        val fire = arrived.any { d ->
                            when (scope) {
                                1 -> !d.isInternal
                                2 -> d.isBluetooth
                                3 -> d.rawType == android.media.AudioDeviceInfo.TYPE_USB_DEVICE ||
                                    d.rawType == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                                else -> d.id == sel || (sel == -1 && !d.isInternal)
                            }
                        }
                        if (fire) {
                            val ctx = getApplication<Application>()
                            try {
                                ContextCompat.startForegroundService(ctx, MicMonitorService.startIntent(ctx))
                            } catch (e: Exception) {
                                // Android blocks microphone FGS starts from
                                // the background (12+/14). Fall back to a
                                // tap-to-start notification.
                                notifyMicConnected(ctx)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // One bad emission must never kill auto-start.
                }
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
            PresetKind.SIMPLE -> AudioParams.SIMPLE_STUDIO.copy(muted = current.muted)
            PresetKind.STONE_DOME -> AudioParams.STONE_DOME.copy(muted = current.muted)
            PresetKind.LOCAL_HALL -> AudioParams.LOCAL_HALL.copy(muted = current.muted)
            PresetKind.LEAD -> AudioParams.LEAD.copy(muted = current.muted)
            PresetKind.CUSTOM -> customPreset.value?.copy(muted = current.muted) ?: current
        }
        _activePreset.value = kind
    }

    fun saveCustom() {
        viewModelScope.launch { store.saveCustom(MonitorState.params.value) }
        _activePreset.value = PresetKind.CUSTOM
    }

    private var lastInputId: Int? = null

    fun selectInput(deviceId: Int) {
        val prevId = lastInputId
        val leavingParams = MonitorState.params.value
        val leavingKind = _activePreset.value
        router.selectInput(deviceId)
        refreshRisk()
        lastInputId = deviceId
        if (!perMicMemory.value || prevId == deviceId) return
        viewModelScope.launch {
            // Save the sound we are leaving under the old mic, then load
            // the arriving mic's own sound if it has one.
            if (prevId != null) store.saveDeviceParams(prevId, leavingParams, leavingKind)
            val saved = store.deviceParams(deviceId)
            if (saved != null) {
                MonitorState.params.value = saved.first.copy(muted = MonitorState.params.value.muted)
                _activePreset.value = saved.second
            }
        }
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
        p.matchesPreset(PresetKind.SIMPLE) -> PresetKind.SIMPLE
        p.matchesPreset(PresetKind.STONE_DOME) -> PresetKind.STONE_DOME
        p.matchesPreset(PresetKind.LOCAL_HALL) -> PresetKind.LOCAL_HALL
        else -> PresetKind.CUSTOM
    }

    override fun onCleared() {
        router.stopListening()
        super.onCleared()
    }
    /** Auto-start fallback: Android may refuse to start a microphone
     *  foreground service while the app is in the background. Post a
     *  high-priority notification instead; tapping it opens the app
     *  with an extra that starts monitoring from the foreground. */
    private fun notifyMicConnected(ctx: Application) {
        try {
            val channelId = "mic_connected"
            val nm = ctx.getSystemService(android.content.Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId, "Microphone connected",
                    android.app.NotificationManager.IMPORTANCE_HIGH,
                ),
            )
            val open = android.app.PendingIntent.getActivity(
                ctx, 7,
                Intent(ctx, com.haramain.monitor.MainActivity::class.java)
                    .putExtra(com.haramain.monitor.MainActivity.EXTRA_START_MONITORING, true)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val n = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Your microphone connected")
                .setContentText("Android blocked an automatic start — tap to start monitoring")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            nm.notify(4202, n)
        } catch (_: Exception) {}
    }

}

/** Stored mic-label prefix marking a physical-mic group assignment
 *  ("group:Bottom Mic") whose members are auto-numbered in the picker. */
const val MIC_GROUP_PREFIX = "group:"
