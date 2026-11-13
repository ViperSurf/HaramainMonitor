#include <jni.h>
#include "AudioEngine.h"

// JNI bridge — thin by design. All state lives in AudioEngine; the handle is
// an opaque pointer owned by the Kotlin NativeAudioEngine singleton.
//
// Float-array parameter layout for nativeSetParams (keep in sync with Kotlin):
//   0 micBoostPercent   1 staticRemover    2 noiseSuppress   3 gateThresholdDb
//   4 deEsserPercent    5 compressorPercent 6 roomSizePercent 7 wetDryPercent
//   8 balancePercent    9 masterGainDb     10 safetyCeilingDb
// Booleans travel as separate calls to keep this array purely numeric.

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
                                                                  jboolean feedbackRisk, jboolean muted) {
    auto *e = fromHandle(handle);
    if (!e || !values || env->GetArrayLength(values) < 11) return;
    jfloat v[11];
    env->GetFloatArrayRegion(values, 0, 11, v);
    DspParams p;
    p.micBoostPercent = v[0]; p.staticRemover = v[1]; p.noiseSuppress = v[2];
    p.gateThresholdDb = v[3]; p.deEsserPercent = v[4]; p.compressorPercent = v[5];
    p.roomSizePercent = v[6]; p.wetDryPercent = v[7]; p.balancePercent = v[8];
    p.masterGainDb = v[9]; p.safetyCeilingDb = v[10];
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

JNIEXPORT jint JNICALL
Java_com_haramain_monitor_audio_NativeAudioEngine_nativeGetXRunCount(JNIEnv *, jobject, jlong handle) {
    auto *e = fromHandle(handle);
    return e ? e->getXRunCount() : 0;
}

} // extern "C"
