package com.haramain.monitor.audio

/** Kotlin face of the C++ Oboe engine. One instance for the whole app —
 *  the service owns start/stop, the ViewModel owns parameters. */
object NativeAudioEngine {

    init {
        System.loadLibrary("haramain_audio")
    }

    private var handle: Long = 0L

    fun create() {
        if (handle == 0L) handle = nativeCreate()
    }

    fun destroy() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    fun start(): Boolean {
        create()
        return handle != 0L && nativeStart(handle)
    }

    fun stop() {
        if (handle != 0L) nativeStop(handle)
    }

    fun isRunning(): Boolean = handle != 0L && nativeIsRunning(handle)

    fun applyParams(p: AudioParams, feedbackRisk: Boolean) {
        if (handle == 0L) return
        nativeSetParams(
            handle, p.toFloatArray(),
            p.noiseSuppressOn, p.safetyOn, feedbackRisk, p.muted, p.btDuckOn,
        )
    }

    fun setInputDevice(deviceId: Int) {
        if (handle != 0L) nativeSetInputDevice(handle, deviceId)
    }

    fun setOutputDevice(deviceId: Int) {
        if (handle != 0L) nativeSetOutputDevice(handle, deviceId)
    }

    /** Called ~1×/s from the service: grows the output buffer if xruns appeared. */
    fun adaptBuffer() {
        if (handle != 0L) nativeAdaptBuffer(handle)
    }

    fun startRecording(path: String, dry: Boolean = false): Boolean =
        handle != 0L && nativeStartRecording(handle, path, dry)

    fun setBufferMode(bursts: Int) {
        if (handle != 0L) nativeSetBufferMode(handle, bursts)
    }

    fun setBypass(on: Boolean) {
        if (handle != 0L) nativeSetBypass(handle, on)
    }

    fun setUsageGame(game: Boolean) {
        if (handle != 0L) nativeSetUsageGame(handle, game)
    }

    fun setPitchEnabled(on: Boolean) {
        if (handle != 0L) nativeSetPitchEnabled(handle, on)
    }

    fun getPitchHz(): Float = if (handle != 0L) nativeGetPitchHz(handle) else 0f

    /** (gate, suppressor, limiter) gains of the latest audio block, 0..1. */
    fun activity(): FloatArray {
        val out = floatArrayOf(1f, 1f, 1f)
        if (handle != 0L) nativeGetActivity(handle, out)
        return out
    }

    fun bufferFrames(): Int = if (handle != 0L) nativeGetBufferFrames(handle) else 0

    /** Stops recording; returns frames written. */
    fun stopRecording(): Long = if (handle != 0L) nativeStopRecording(handle) else 0L

    fun isRecording(): Boolean = handle != 0L && nativeIsRecording(handle)

    fun startCalibration() {
        if (handle != 0L) nativeStartCalibration(handle)
    }

    /** [rmsDb, peakDb, hfRmsDb, seconds]; seconds == 0 means measurement failed. */
    fun stopCalibration(): FloatArray =
        if (handle != 0L) nativeStopCalibration(handle) else floatArrayOf(-80f, -80f, -80f, 0f)

    fun sampleRate(): Int = if (handle != 0L) nativeGetSampleRate(handle) else 0
    fun latencyMs(): Float = if (handle != 0L) nativeGetLatencyMs(handle) else 0f
    fun inputPeakDb(): Float = if (handle != 0L) nativeGetInputPeakDb(handle) else -80f
    fun outputPeakDb(): Float = if (handle != 0L) nativeGetOutputPeakDb(handle) else -80f
    fun routeEventCount(): Int = if (handle != 0L) nativeGetRouteEventCount(handle) else 0
    fun audioSessionId(): Int = if (handle != 0L) nativeGetAudioSessionId(handle) else -1
    fun xRunCount(): Int = if (handle != 0L) nativeGetXRunCount(handle) else 0

    // ---- JNI (keep in sync with native-lib.cpp) ----
    private external fun nativeCreate(): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeStart(handle: Long): Boolean
    private external fun nativeStop(handle: Long)
    private external fun nativeIsRunning(handle: Long): Boolean
    private external fun nativeSetParams(
        handle: Long, values: FloatArray,
        suppressOn: Boolean, safetyOn: Boolean, feedbackRisk: Boolean, muted: Boolean,
        btDuckOn: Boolean,
    )
    private external fun nativeSetInputDevice(handle: Long, deviceId: Int)
    private external fun nativeSetOutputDevice(handle: Long, deviceId: Int)
    private external fun nativeGetSampleRate(handle: Long): Int
    private external fun nativeGetLatencyMs(handle: Long): Float
    private external fun nativeGetInputPeakDb(handle: Long): Float
    private external fun nativeGetOutputPeakDb(handle: Long): Float
    private external fun nativeGetRouteEventCount(handle: Long): Int
    private external fun nativeGetAudioSessionId(handle: Long): Int
    private external fun nativeGetXRunCount(handle: Long): Int
    private external fun nativeAdaptBuffer(handle: Long)
    private external fun nativeStartCalibration(handle: Long)
    private external fun nativeStopCalibration(handle: Long): FloatArray
    private external fun nativeStartRecording(handle: Long, path: String, dry: Boolean): Boolean
    private external fun nativeSetBufferMode(handle: Long, bursts: Int)
    private external fun nativeSetBypass(handle: Long, on: Boolean)
    private external fun nativeSetUsageGame(handle: Long, game: Boolean)
    private external fun nativeSetPitchEnabled(handle: Long, on: Boolean)
    private external fun nativeGetPitchHz(handle: Long): Float
    private external fun nativeGetActivity(handle: Long, out: FloatArray)
    private external fun nativeGetBufferFrames(handle: Long): Int
    private external fun nativeStopRecording(handle: Long): Long
    private external fun nativeIsRecording(handle: Long): Boolean
}
