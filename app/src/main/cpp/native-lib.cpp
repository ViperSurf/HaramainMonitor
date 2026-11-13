#include <jni.h>
#include <algorithm>
#include "AudioEngine.h"

// JNI bridge — thin by design. All state lives in AudioEngine; the handle is
// an opaque pointer owned by the Kotlin NativeAudioEngine singleton.
//
// Float-array parameter layout for nativeSetParams (keep in sync with Kotlin):
//   0 micBoostPercent   1 staticRemover    2 noiseSuppress   3 gateThresholdDb
//   4 deEsserPercent    5 compressorPercent 6 roomSizePercent 7 wetDryPercent
//   8 balancePercent    9 masterGainDb     10 safetyCeilingDb
//   11 eqLowGainDb     12 eqHighGainDb
//   13 btDuckPercent   14 eqWarmthDb      15 eqClarityDb    16 eqAirDb
// Booleans travel as separate args to keep this array purely numeric.

static AudioEngine *fromHandle(jlong handle) {
    return reinterpret_cast<AudioEngine *>(handle);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeCreate(JNIEnv *, jobject) {
    return reinterpret_cast<jlong>(new AudioEngine());
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeDestroy(JNIEnv *, jobject, jlong handle) {
    delete fromHandle(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeStart(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->start() : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeStop(JNIEnv *, jobject, jlong handle) {
    if (auto *e = fromHandle(handle)) e->stop();
}

JNIEXPORT jboolean JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeIsRunning(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e && e->isRunning() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeSetParams(JNIEnv *env, jobject, jlong handle,
                                                                  jfloatArray values,
                                                                  jboolean suppressOn, jboolean safetyOn,
                                                                  jboolean feedbackRisk, jboolean muted,
                                                                  jboolean btDuckOn) {
    auto *e = fromHandle(handle);
    if (!e || !values || env->GetArrayLength(values) < 13) return;
    jfloat v[17] = {};
    const jsize n = std::min<jsize>(env->GetArrayLength(values), 17);
    env->GetFloatArrayRegion(values, 0, n, v);
    if (n < 17) v[13] = 50.0f;   // older 13-float callers: duck amount default
    DspParams p;
    p.micBoostPercent = v[0]; p.staticRemover = v[1]; p.noiseSuppress = v[2];
    p.gateThresholdDb = v[3]; p.deEsserPercent = v[4]; p.compressorPercent = v[5];
    p.roomSizePercent = v[6]; p.wetDryPercent = v[7]; p.balancePercent = v[8];
    p.masterGainDb = v[9]; p.safetyCeilingDb = v[10];
    p.eqLowGainDb = v[11]; p.eqHighGainDb = v[12];
    p.btDuckPercent = v[13];
    p.eqWarmthDb = v[14]; p.eqClarityDb = v[15]; p.eqAirDb = v[16];
    p.btDuckOn = btDuckOn;
    p.noiseSuppressOn = suppressOn; p.safetyOn = safetyOn;
    p.feedbackRisk = feedbackRisk; p.muted = muted;
    e->setParams(p);
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeSetInputDevice(JNIEnv *, jobject, jlong handle, jint deviceId) {
    if (auto *e = fromHandle(handle)) e->setInputDeviceId(deviceId);
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeSetOutputDevice(JNIEnv *, jobject, jlong handle, jint deviceId) {
    if (auto *e = fromHandle(handle)) e->setOutputDeviceId(deviceId);
}

JNIEXPORT jint JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetSampleRate(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->getSampleRate() : 0;
}

JNIEXPORT jfloat JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetLatencyMs(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->getLatencyMs() : 0.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetInputPeakDb(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->takeInputPeakDb() : -80.0f;
}

JNIEXPORT jfloat JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetOutputPeakDb(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->takeOutputPeakDb() : -80.0f;
}

JNIEXPORT jint JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetRouteEventCount(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? static_cast<jint>(e->routeEventCount()) : 0;
}

JNIEXPORT jint JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetAudioSessionId(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->getAudioSessionId() : -1;
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeAdaptBuffer(JNIEnv *, jobject, jlong handle) {
    if (auto *e = fromHandle(handle)) e->adaptBufferIfNeeded();
}

JNIEXPORT jboolean JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeStartRecording(JNIEnv *env, jobject, jlong handle, jstring path, jboolean dry) {
    auto *e = fromHandle(handle);
    if (!e || !path) return JNI_FALSE;
    const char *chars = env->GetStringUTFChars(path, nullptr);
    const bool ok = e->startRecording(chars, dry == JNI_TRUE);
    env->ReleaseStringUTFChars(path, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeStopRecording(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? static_cast<jlong>(e->stopRecording()) : 0;
}

JNIEXPORT jboolean JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeIsRecording(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e && e->isRecording() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeStartCalibration(JNIEnv *, jobject, jlong handle) {
    if (auto *e = fromHandle(handle)) e->startCalibration();
}

// Returns float[4]: rmsDb, peakDb, hfRmsDb, seconds (seconds == 0 on failure).
JNIEXPORT jfloatArray JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeStopCalibration(JNIEnv *env, jobject, jlong handle) {
    jfloatArray out = env->NewFloatArray(4);
    jfloat v[4] = {-80.0f, -80.0f, -80.0f, 0.0f};
    if (auto *e = fromHandle(handle)) {
        e->stopCalibration(v[0], v[1], v[2], v[3]);
    }
    env->SetFloatArrayRegion(out, 0, 4, v);
    return out;
}

JNIEXPORT jint JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetXRunCount(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->getXRunCount() : 0;
}


JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeSetBufferMode(JNIEnv *, jobject, jlong handle, jint bursts) {
    if (auto *e = fromHandle(handle)) e->setBufferMode(bursts);
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeSetUsageGame(JNIEnv *, jobject, jlong handle, jboolean game) {
    if (auto *e = fromHandle(handle)) e->setUsageGame(game == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeSetPitchEnabled(JNIEnv *, jobject, jlong handle, jboolean on) {
    if (auto *e = fromHandle(handle)) e->setPitchEnabled(on == JNI_TRUE);
}

JNIEXPORT jfloat JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetPitchHz(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->getPitchHz() : 0.0f;
}

} // extern "C"
