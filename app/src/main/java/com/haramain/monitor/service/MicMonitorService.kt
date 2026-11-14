package com.haramain.monitor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import android.os.Environment
import com.haramain.monitor.MainActivity
import com.haramain.monitor.R
import com.haramain.monitor.audio.AudioRouter
import com.haramain.monitor.audio.isBluetooth
import com.haramain.monitor.audio.NativeAudioEngine
import com.haramain.monitor.data.SettingsStore
import kotlinx.coroutines.flow.first
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The screen-off guarantee lives here:
 *  - foreground service typed microphone|mediaPlayback (Android 14 contract),
 *  - PARTIAL_WAKE_LOCK so the CPU keeps feeding the Oboe callback,
 *  - held audio focus so other apps don't yank the route mid-recitation,
 *  - persistent notification with Mute / Stop actions that work from the
 *    lock screen without ever opening the Activity.
 */
class MicMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var telemetryJob: Job? = null
    private var focusMuted = false
    private var lastRouteEvents = 0
    private var recoveryAttempts = 0
    private var currentRecFile: File? = null
    @Volatile private var recordDryCached = false
    @Volatile private var bufferModeCached = 1
    @Volatile private var autoStopCached = 10
    @Volatile private var autoRecordCached = false
    @Volatile private var lastVoiceMs = 0L
    private val settings by lazy { SettingsStore(this) }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> stopMonitoring()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> { focusMuted = true; applyParams() }
            AudioManager.AUDIOFOCUS_GAIN -> { focusMuted = false; applyParams() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (MonitorState.router == null) MonitorState.router = AudioRouter(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopMonitoring(); return START_NOT_STICKY }
            ACTION_TOGGLE_MUTE -> {
                val p = MonitorState.params.value
                MonitorState.params.value = p.copy(muted = !p.muted)
            }
            ACTION_TOGGLE_RECORD -> {
                if (MonitorState.isRunning.value) toggleRecording()
            }
            ACTION_TOGGLE_MONITOR -> {
                if (MonitorState.isRunning.value) stopMonitoring() else startMonitoring()
                return START_STICKY
            }
            else -> startMonitoring()   // ACTION_START or system restart
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        if (MonitorState.isRunning.value) { pushForeground(); return }
        val router = MonitorState.router ?: AudioRouter(this).also { MonitorState.router = it }
        router.startListening()
        router.refresh()

        if (!requestAudioFocus()) return
        acquireWakeLock()

        NativeAudioEngine.create()
        // Re-apply the user's chosen route before opening streams.
        router.selectInput(router.selectedInputId.value)
        router.selectOutput(router.selectedOutputId.value)
        if (!NativeAudioEngine.start()) {
            releaseAll()
            return
        }
        router.attachPlatformEffects()
        applyParams()
        MonitorState.isRunning.value = true
        recoveryAttempts = 0
        scope.launch { settings.setWasRunning(true) }   // cleared only by an explicit stop
        pushForeground()

        // Params -> native, continuously (lock-free atomics on the C++ side).
        scope.launch {
            MonitorState.params.collectLatest { applyParams(); pushForeground() }
        }
        // v2.0 settings -> native: buffer mode, pitch toggle, BT usage hint.
        scope.launch {
            settings.bufferMode.collectLatest {
                bufferModeCached = it
                NativeAudioEngine.setBufferMode(it)
            }
        }
        scope.launch { settings.pitchOn.collectLatest { NativeAudioEngine.setPitchEnabled(it) } }
        scope.launch { settings.recordDry.collectLatest { recordDryCached = it } }
        scope.launch { settings.autoStopMinutes.collectLatest { autoStopCached = it } }
        scope.launch {
            settings.autoRecord.collectLatest {
                autoRecordCached = it
                // Fires on the first emission inside startMonitoring too,
                // which is exactly the auto-record moment.
                if (it && MonitorState.isRunning.value && !MonitorState.isRecording.value) {
                    startRecordingInternal()
                }
            }
        }
        lastVoiceMs = System.currentTimeMillis()
        scope.launch {
            router.selectedOutputId.collectLatest { id ->
                val device = router.outputs.value.firstOrNull { it.id == id }
                NativeAudioEngine.setUsageGame(device?.isBluetooth == true)
            }
        }
        // Telemetry + native route-event watchdog + buffer health + recovery.
        telemetryJob = scope.launch {
            var inPeak = -80f; var outPeak = -80f
            var voiceEma = -80f
            var tick = 0
            while (isActive) {
                if (++tick % 20 == 0) NativeAudioEngine.adaptBuffer()   // ~1×/s xrun check
                // Signal-loss recovery: engine died underneath us (route
                // fallback already failed natively) — try to bring it back.
                if (MonitorState.isRunning.value && !NativeAudioEngine.isRunning()) {
                    if (recoveryAttempts < 3) {
                        recoveryAttempts++
                        if (NativeAudioEngine.start()) {
                            MonitorState.router?.attachPlatformEffects()
                            applyParams()
                            recoveryAttempts = 0
                        }
                    } else {
                        stopMonitoring()
                        break
                    }
                }
                // Peak-hold with gentle decay for readable meters.
                val tickInPeak = NativeAudioEngine.inputPeakDb()
                inPeak = maxOf(tickInPeak, inPeak - 3f)
                // Auto-stop: fast-attack / slow-decay EMA of the raw input
                // peak. Voice-level sound keeps the quiet clock reset; a
                // steady fan sits below the threshold and does not.
                voiceEma = if (tickInPeak > voiceEma) tickInPeak
                           else voiceEma + 0.02f * (tickInPeak - voiceEma)
                val nowTick = System.currentTimeMillis()
                if (voiceEma > -48f) lastVoiceMs = nowTick
                val stopAfterMin = autoStopCached
                if (stopAfterMin > 0 && !MonitorState.isRecording.value &&
                    lastVoiceMs > 0 && nowTick - lastVoiceMs > stopAfterMin * 60_000L) {
                    stopMonitoring()
                    break
                }
                outPeak = maxOf(NativeAudioEngine.outputPeakDb(), outPeak - 3f)
                MonitorState.inputPeakDb.value = inPeak
                MonitorState.outputPeakDb.value = outPeak
                MonitorState.sampleRate.value = NativeAudioEngine.sampleRate()
                MonitorState.latencyMs.value = NativeAudioEngine.latencyMs()
                MonitorState.xRuns.value = NativeAudioEngine.xRunCount()
                MonitorState.pitchHz.value = NativeAudioEngine.getPitchHz()
                val act = NativeAudioEngine.activity()
                MonitorState.actGate.value = act[0]
                MonitorState.actSuppress.value = act[1]
                MonitorState.actLimiter.value = act[2]
                MonitorState.bufferFrames.value = NativeAudioEngine.bufferFrames()
                val events = NativeAudioEngine.routeEventCount()
                if (events != lastRouteEvents) {
                    lastRouteEvents = events
                    router.refresh()       // device fell back natively; resync UI lists/badges
                    applyParams()          // feedback risk may have changed with the new route
                    // Hardware changed: relaunch the buffer at the user's
                    // mode on the new device (its burst size may differ).
                    NativeAudioEngine.setBufferMode(bufferModeCached)
                }
                delay(50)
            }
        }
    }

    // ---- recording ----

    fun toggleRecording() {
        if (NativeAudioEngine.isRecording()) stopRecordingInternal() else startRecordingInternal()
        pushForeground()
    }

    private fun startRecordingInternal() {
        val dir = recordingsDir(this)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "Haramain_$stamp.wav")
        if (NativeAudioEngine.startRecording(file.absolutePath, recordDryCached)) {
            currentRecFile = file
            MonitorState.isRecording.value = true
            MonitorState.recordingStartedAtMs.value = System.currentTimeMillis()
        }
    }

    private fun stopRecordingInternal() {
        NativeAudioEngine.stopRecording()
        MonitorState.isRecording.value = false
        MonitorState.recordingStartedAtMs.value = 0L
        MonitorState.recordingsChanged.value += 1
        val finished = currentRecFile
        currentRecFile = null
        if (finished != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) { copyToExportFolder(finished) }
        }
    }

    /** If the user picked a recording folder (SAF), copy each finished WAV there. */
    private suspend fun copyToExportFolder(file: File) {
        try {
            val uriString = settings.exportFolderUri.first()
            if (uriString.isBlank() || !file.exists()) return
            val treeUri = android.net.Uri.parse(uriString)
            val parentDoc = android.provider.DocumentsContract.buildDocumentUriUsingTree(
                treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri),
            )
            val docUri = android.provider.DocumentsContract.createDocument(
                contentResolver, parentDoc, "audio/wav", file.name,
            ) ?: return
            contentResolver.openOutputStream(docUri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        } catch (_: Exception) {
            // Best-effort: the original stays safe in the app folder regardless.
        }
    }

    private fun stopMonitoring() {
        if (NativeAudioEngine.isRecording()) stopRecordingInternal()
        scope.launch { settings.setWasRunning(false) }   // explicit stop: no auto-resume
        telemetryJob?.cancel()
        NativeAudioEngine.stop()
        MonitorState.router?.releasePlatformEffects()
        MonitorState.router?.stopListening()
        MonitorState.isRunning.value = false
        MonitorState.inputPeakDb.value = -80f
        MonitorState.outputPeakDb.value = -80f
        releaseAll()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun applyParams() {
        val p = MonitorState.params.value
        val risk = MonitorState.router?.isFeedbackRisk() ?: true
        NativeAudioEngine.applyParams(
            if (focusMuted) p.copy(muted = true) else p,
            feedbackRisk = risk && p.safetyOn,
        )
    }

    // ---- system resources ----

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)
            .build()
        return am.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "HaramainMonitor::MicWakeLock",
        ).apply {
            setReferenceCounted(false)
            acquire(4 * 60 * 60 * 1000L)   // 4 h safety cap; service lifecycle is the real control
        }
    }

    private fun releaseAll() {
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        wakeLock = null
        focusRequest?.let { try { audioManager?.abandonAudioFocusRequest(it) } catch (_: Exception) {} }
        focusRequest = null
    }

    override fun onDestroy() {
        scope.cancel()
        releaseAll()
        super.onDestroy()
    }

    // ---- notification ----

    private fun pushForeground() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val muted = MonitorState.params.value.muted
        val running = MonitorState.isRunning.value
        val recording = MonitorState.isRecording.value
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (running) "Monitoring live" else "Haramain Monitor")
            .setContentText(
                when {
                    !running -> "Tap to open"
                    recording -> "● REC — recording your recitation"
                    muted -> "Muted — tap Mute again to listen"
                    else -> "Haramain • screen-off monitoring active"
                },
            )
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .addAction(
                0, if (muted) "Unmute" else "Mute",
                serviceAction(ACTION_TOGGLE_MUTE, 1),
            )
            .addAction(
                0, if (recording) "Stop Rec" else "Rec",
                serviceAction(ACTION_TOGGLE_RECORD, 3),
            )
            .addAction(0, "Stop", serviceAction(ACTION_STOP, 2))
            .build()
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this, requestCode,
            Intent(this, MicMonitorService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = getString(R.string.notification_channel_desc) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "com.haramain.monitor.action.START"
        const val ACTION_STOP = "com.haramain.monitor.action.STOP"
        const val ACTION_TOGGLE_MUTE = "com.haramain.monitor.action.TOGGLE_MUTE"
        const val ACTION_TOGGLE_MONITOR = "com.haramain.monitor.action.TOGGLE_MONITOR"
        const val ACTION_TOGGLE_RECORD = "com.haramain.monitor.action.TOGGLE_RECORD"
        private const val CHANNEL_ID = "live_monitoring"
        private const val NOTIFICATION_ID = 42

        /** App-private recordings folder (no storage permission needed). */
        fun recordingsDir(context: Context): File {
            val base = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
            return File(base, "recordings").apply { mkdirs() }
        }

        fun toggleRecordIntent(context: Context): Intent =
            Intent(context, MicMonitorService::class.java).setAction(ACTION_TOGGLE_RECORD)

        fun startIntent(context: Context): Intent =
            Intent(context, MicMonitorService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, MicMonitorService::class.java).setAction(ACTION_STOP)
    }
}
