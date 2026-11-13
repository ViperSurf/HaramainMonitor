package com.haramain.monitor.service

import com.haramain.monitor.audio.AudioParams
import com.haramain.monitor.audio.AudioRouter
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide shared state between the Compose ViewModel (UI process face)
 * and MicMonitorService (which must keep running with the UI gone).
 * Everything is in-memory; persistence lives in PresetStore (DataStore).
 */
object MonitorState {
    val params = MutableStateFlow(AudioParams.ORIGINAL)
    val isRunning = MutableStateFlow(false)
    val inputPeakDb = MutableStateFlow(-80f)
    val outputPeakDb = MutableStateFlow(-80f)
    val sampleRate = MutableStateFlow(0)
    val latencyMs = MutableStateFlow(0f)
    val xRuns = MutableStateFlow(0)

    // Recording (owned by the service; the library UI watches recordingsChanged)
    val isRecording = MutableStateFlow(false)
    val recordingStartedAtMs = MutableStateFlow(0L)
    val recordingsChanged = MutableStateFlow(0)

    /** Set by the service while it lives; null when monitoring is stopped. */
    @Volatile
    var router: AudioRouter? = null
}
