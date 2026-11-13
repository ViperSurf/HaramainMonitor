#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>
#include <thread>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "HaramainAudio", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HaramainAudio", __VA_ARGS__)

bool AudioEngine::openStreams() {
    // ---- Input (microphone) ----
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)                        // voice mics are mono; chain dual-monos it
            ->setInputPreset(oboe::InputPreset::VoicePerformance) // lowest-latency path, no HAL voice processing fighting our DSP
            ->setSessionId(oboe::SessionId::Allocate)   // lets Kotlin attach platform NoiseSuppressor to this session
            ->setDeviceId(mInputDeviceId.load());
    // Sample rate intentionally left unspecified -> device native rate (no resampling).

    oboe::Result result = inBuilder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGE("Input stream failed: %s (retrying with default device)", oboe::convertToText(result));
        mInputDeviceId.store(oboe::kUnspecified);
        inBuilder.setDeviceId(oboe::kUnspecified);
        result = inBuilder.openStream(mInputStream);
        if (result != oboe::Result::OK) { LOGE("Input stream failed hard: %s", oboe::convertToText(result)); return false; }
        mRouteEvents.fetch_add(1);
    }

    // ---- Output (headphones / earpiece / speaker / USB / BT) ----
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(2)                        // stereo: reverb + pan need it
            ->setUsage(oboe::Usage::Media)
            ->setContentType(oboe::ContentType::Speech)
            ->setDeviceId(mOutputDeviceId.load())
            ->setDataCallback(this)
            ->setErrorCallback(this);

    result = outBuilder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Output stream failed: %s (retrying with default device)", oboe::convertToText(result));
        mOutputDeviceId.store(oboe::kUnspecified);
        outBuilder.setDeviceId(oboe::kUnspecified);
        result = outBuilder.openStream(mOutputStream);
        if (result != oboe::Result::OK) { LOGE("Output stream failed hard: %s", oboe::convertToText(result)); return false; }
        mRouteEvents.fetch_add(1);
    }

    // Match buffer to 2 bursts — the classic low-latency sweet spot:
    // small enough for <20 ms round trip, big enough to avoid underruns.
    const int32_t burst = mOutputStream->getFramesPerBurst();
    mOutputStream->setBufferSizeInFrames(burst * 2);

    const int32_t sr = mOutputStream->getSampleRate();
    mSampleRate.store(sr);
    mChain.prepare(static_cast<double>(sr));
    LOGI("Streams open: sr=%d burst=%d inDev=%d outDev=%d", sr, burst,
         mInputStream->getDeviceId(), mOutputStream->getDeviceId());
    return true;
}

void AudioEngine::closeStreams() {
    if (mOutputStream) { mOutputStream->requestStop(); mOutputStream->close(); mOutputStream.reset(); }
    if (mInputStream) { mInputStream->requestStop(); mInputStream->close(); mInputStream.reset(); }
}

bool AudioEngine::start() {
    if (mRunning.load()) return true;
    if (!openStreams()) return false;
    if (mInputStream->requestStart() != oboe::Result::OK) { closeStreams(); return false; }
    if (mOutputStream->requestStart() != oboe::Result::OK) { closeStreams(); return false; }
    mRunning.store(true);
    LOGI("Engine started");
    return true;
}

void AudioEngine::stop() {
    mRunning.store(false);
    closeStreams();
}

void AudioEngine::setInputDeviceId(int32_t id) {
    if (id == mInputDeviceId.load() && mRunning.load()) return;
    mInputDeviceId.store(id);
    if (mRunning.load()) {           // rebuild streams on the caller's thread (never the audio thread)
        closeStreams();
        if (!openStreams() || mInputStream->requestStart() != oboe::Result::OK ||
            mOutputStream->requestStart() != oboe::Result::OK) {
            LOGE("Re-route (input) failed"); mRunning.store(false);
        }
    }
}

void AudioEngine::setOutputDeviceId(int32_t id) {
    if (id == mOutputDeviceId.load() && mRunning.load()) return;
    mOutputDeviceId.store(id);
    if (mRunning.load()) {
        closeStreams();
        if (!openStreams() || mInputStream->requestStart() != oboe::Result::OK ||
            mOutputStream->requestStart() != oboe::Result::OK) {
            LOGE("Re-route (output) failed"); mRunning.store(false);
        }
    }
}

void AudioEngine::setParams(const DspParams &p) {
    aBoost.store(p.micBoostPercent); aStatic.store(p.staticRemover);
    aSuppress.store(p.noiseSuppress); aSuppressOn.store(p.noiseSuppressOn);
    aGate.store(p.gateThresholdDb); aDeEsser.store(p.deEsserPercent);
    aComp.store(p.compressorPercent); aRoom.store(p.roomSizePercent);
    aMix.store(p.wetDryPercent); aBalance.store(p.balancePercent);
    aMasterDb.store(p.masterGainDb); aSafetyOn.store(p.safetyOn);
    aSafetyCeilingDb.store(p.safetyCeilingDb); aFeedbackRisk.store(p.feedbackRisk);
    aMuted.store(p.muted);
}

DspParams AudioEngine::snapshotParams() const {
    DspParams p;
    p.micBoostPercent = aBoost.load(); p.staticRemover = aStatic.load();
    p.noiseSuppress = aSuppress.load(); p.noiseSuppressOn = aSuppressOn.load();
    p.gateThresholdDb = aGate.load(); p.deEsserPercent = aDeEsser.load();
    p.compressorPercent = aComp.load(); p.roomSizePercent = aRoom.load();
    p.wetDryPercent = aMix.load(); p.balancePercent = aBalance.load();
    p.masterGainDb = aMasterDb.load(); p.safetyOn = aSafetyOn.load();
    p.safetyCeilingDb = aSafetyCeilingDb.load(); p.feedbackRisk = aFeedbackRisk.load();
    p.muted = aMuted.load();
    return p;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData,
                                                   int32_t numFrames) {
    auto *out = static_cast<float *>(audioData);
    const int32_t frames = std::min(numFrames, kMaxFrames);

    // Pull this burst from the mic (blocking read with a short timeout, the
    // LiveEffect pattern — input is started first so frames are ready).
    auto readResult = mInputStream->read(mInputBuffer, frames, 2 * 1000 * 1000 /* 2 ms in ns */);
    const int32_t framesRead = readResult ? readResult.value() : 0;
    if (framesRead < frames) {
        std::memset(mInputBuffer + framesRead, 0, sizeof(float) * (frames - framesRead));
    }

    const DspParams p = snapshotParams();
    float inPeak = 0.0f, outPeak = 0.0f;
    for (int32_t i = 0; i < frames; ++i) {
        float l = mInputBuffer[i];
        float r = l;                                    // dual-mono
        mChain.processFrame(l, r, p);
        out[i * 2] = l;
        out[i * 2 + 1] = r;
        inPeak = std::max(inPeak, mChain.lastInPeak());
        outPeak = std::max(outPeak, mChain.lastOutPeak());
    }
    // Zero any remainder if the device ever asks for > kMaxFrames.
    for (int32_t i = frames; i < numFrames; ++i) { out[i * 2] = 0; out[i * 2 + 1] = 0; }

    if (inPeak > mInPeak.load()) mInPeak.store(inPeak);
    if (outPeak > mOutPeak.load()) mOutPeak.store(outPeak);
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    // Classic case: headphones unplugged / BT device dropped mid-session.
    LOGE("Stream error after close: %s — falling back to default route", oboe::convertToText(error));
    mRouteEvents.fetch_add(1);
    if (!mRunning.load()) return;
    mInputDeviceId.store(oboe::kUnspecified);
    mOutputDeviceId.store(oboe::kUnspecified);
    // Reopen off the callback thread.
    std::thread([this]() {
        closeStreams();
        if (openStreams()) {
            mInputStream->requestStart();
            mOutputStream->requestStart();
        } else {
            mRunning.store(false);
        }
    }).detach();
}

float AudioEngine::getLatencyMs() const {
    if (!mOutputStream || !mInputStream) return 0.0f;
    const double sr = static_cast<double>(mSampleRate.load());
    if (sr <= 0) return 0.0f;
    // Estimate: input buffer + output buffer + one burst of scheduling slop.
    const double frames = mInputStream->getBufferSizeInFrames()
                        + mOutputStream->getBufferSizeInFrames()
                        + mOutputStream->getFramesPerBurst();
    return static_cast<float>(frames / sr * 1000.0);
}

int32_t AudioEngine::getAudioSessionId() const {
    return mInputStream ? mInputStream->getAudioSessionId() : -1;
}

int32_t AudioEngine::getXRunCount() const {
    if (!mOutputStream) return 0;
    auto r = mOutputStream->getXRunCount();
    return r ? r.value() : 0;
}

float AudioEngine::takeInputPeakDb() {
    const float peak = mInPeak.exchange(0.0f);
    return peak > 1e-6f ? 20.0f * std::log10(peak) : -80.0f;
}

float AudioEngine::takeOutputPeakDb() {
    const float peak = mOutPeak.exchange(0.0f);
    return peak > 1e-6f ? 20.0f * std::log10(peak) : -80.0f;
}
