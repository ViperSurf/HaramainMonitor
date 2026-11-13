package com.haramain.monitor.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Friendly buckets the UI routes by, mapped from raw AudioDeviceInfo types.
 *  NOTE: built-in mics are deliberately NOT labelled by position. Android
 *  gives apps no reliable way to tell which physical hole a built-in mic
 *  sits behind (field-verified: a name heuristic confidently labelled the
 *  bottom mic "Back Mic" on a real phone). All built-ins share one kind and
 *  are told apart by a number suffix when names collide. */
enum class InputKind(val label: String) {
    BUILTIN("Built-in Mic"),
    WIRED_HEADSET("Wired Headset Mic"),
    USB("USB Mic / DAC"),
    BLUETOOTH("Bluetooth Mic"),
    OTHER("Microphone"),
}

enum class OutputKind(val label: String) {
    WIRED("Wired Headphones"),
    USB("USB-C DAC / Headphones"),
    BLUETOOTH("Bluetooth Audio"),
    EARPIECE("Phone Earpiece"),
    SPEAKER("Loud Speaker"),
    OTHER("Output"),
}

data class RouteDevice(
    val id: Int,
    val name: String,
    val kindLabel: String,
    val isSource: Boolean,
    val isInternal: Boolean,   // built-in mic / speaker / earpiece — matters for feedback risk
    val rawType: Int,
)

/**
 * Enumerates real hardware via AudioManager, keeps the lists live with an
 * AudioDeviceCallback (hot-plug), and pushes the user's explicit device ids
 * into the native engine (Oboe setDeviceId). -1 always means "system default".
 */
class AudioRouter(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _inputs = MutableStateFlow<List<RouteDevice>>(emptyList())
    val inputs: StateFlow<List<RouteDevice>> = _inputs.asStateFlow()

    private val _outputs = MutableStateFlow<List<RouteDevice>>(emptyList())
    val outputs: StateFlow<List<RouteDevice>> = _outputs.asStateFlow()

    private val _selectedInputId = MutableStateFlow(-1)
    val selectedInputId: StateFlow<Int> = _selectedInputId.asStateFlow()

    private val _selectedOutputId = MutableStateFlow(-1)
    val selectedOutputId: StateFlow<Int> = _selectedOutputId.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            // If a routed device vanished, fall back to default immediately;
            // the native engine also self-heals via its error callback.
            val removedIds = removedDevices.map { it.id }.toSet()
            if (_selectedInputId.value in removedIds) selectInput(-1)
            if (_selectedOutputId.value in removedIds) selectOutput(-1)
            refresh()
        }
    }

    private var platformNs: NoiseSuppressor? = null

    // Reference-counted: both the ViewModel (pickers) and the Service
    // (hot-plug safety) listen; the platform callback unregisters only when
    // the last listener goes away.
    private var listenRefs = 0

    @Synchronized
    fun startListening() {
        refresh()
        if (listenRefs++ == 0) audioManager.registerAudioDeviceCallback(callback, null)
    }

    @Synchronized
    fun stopListening() {
        if (--listenRefs <= 0) {
            listenRefs = 0
            audioManager.unregisterAudioDeviceCallback(callback)
        }
    }

    fun refresh() {
        _inputs.value = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .map { it.toRouteDevice(isSource = true) }
            .distinctBy { it.id }
            .sortedBy { it.kindLabel }
            .disambiguated()
        _outputs.value = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .map { it.toRouteDevice(isSource = false) }
            .distinctBy { it.id }
            .sortedBy { it.kindLabel }
            .disambiguated()
    }

    /** Phones often expose several devices with identical names (e.g. three
     *  "Microphone" built-ins) — suffix duplicates so each stays selectable
     *  and identifiable: "Microphone", "Microphone (2)", "Microphone (3)". */
    private fun List<RouteDevice>.disambiguated(): List<RouteDevice> {
        val seen = mutableMapOf<String, Int>()
        return map { device ->
            val key = device.kindLabel + "|" + device.name
            val n = (seen[key] ?: 0) + 1
            seen[key] = n
            if (n == 1) device else device.copy(name = "${device.name} ($n)")
        }
    }

    fun selectInput(deviceId: Int) {
        _selectedInputId.value = deviceId
        NativeAudioEngine.setInputDevice(deviceId)
    }

    fun selectOutput(deviceId: Int) {
        _selectedOutputId.value = deviceId
        NativeAudioEngine.setOutputDevice(deviceId)
    }

    fun selectedInput(): RouteDevice? = _inputs.value.firstOrNull { it.id == _selectedInputId.value }
    fun selectedOutput(): RouteDevice? = _outputs.value.firstOrNull { it.id == _selectedOutputId.value }

    /** Feedback risk = sound can loop: internal speaker (or earpiece at gain) out + internal mic in. */
    fun isFeedbackRisk(): Boolean {
        val out = selectedOutput()
        val inp = selectedInput()
        val outInternal = out?.isInternal ?: true   // default route on a phone is the speaker — assume risk
        val inInternal = inp?.isInternal ?: true
        val outIsSpeakerish = out == null || out.rawType == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        return outInternal && inInternal && outIsSpeakerish
    }

    /**
     * Attach Android's platform NoiseSuppressor to the native input session
     * when the device implements it — it complements (does not replace) the
     * C++ suppression stage. Safe no-op when unavailable.
     */
    fun attachPlatformEffects() {
        val sessionId = NativeAudioEngine.audioSessionId()
        if (sessionId <= 0) return
        try {
            if (NoiseSuppressor.isAvailable()) {
                platformNs = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            }
            if (AcousticEchoCanceler.isAvailable()) {
                AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            }
        } catch (_: Exception) {
            // Some OEM HALs throw on effect creation; the C++ chain still covers us.
        }
    }

    fun releasePlatformEffects() {
        try { platformNs?.release() } catch (_: Exception) {}
        platformNs = null
    }

    // ---- mapping ----

    private fun AudioDeviceInfo.toRouteDevice(isSource: Boolean): RouteDevice {
        val product = productName?.toString()?.trim().orEmpty()
        return if (isSource) {
            val kind = inputKind()
            RouteDevice(
                id = id,
                name = product.ifEmpty { kind.label },
                kindLabel = kind.label,
                isSource = true,
                isInternal = kind == InputKind.BUILTIN,
                rawType = type,
            )
        } else {
            val kind = outputKind()
            RouteDevice(
                id = id,
                name = product.ifEmpty { kind.label },
                kindLabel = kind.label,
                isSource = false,
                isInternal = kind == OutputKind.SPEAKER || kind == OutputKind.EARPIECE,
                rawType = type,
            )
        }
    }

    private fun AudioDeviceInfo.inputKind(): InputKind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> InputKind.BUILTIN
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> InputKind.WIRED_HEADSET
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> InputKind.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> InputKind.BLUETOOTH
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
        ) InputKind.BLUETOOTH else InputKind.OTHER
    }

    private fun AudioDeviceInfo.outputKind(): OutputKind = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> OutputKind.WIRED
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> OutputKind.BLUETOOTH
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> OutputKind.EARPIECE
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.SPEAKER
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
        ) OutputKind.BLUETOOTH else OutputKind.OTHER
    }
}
