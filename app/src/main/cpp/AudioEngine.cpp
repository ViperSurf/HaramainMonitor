#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <thread>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "HaramainAudio", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HaramainAudio", __VA_ARGS__)

bool AudioEngine::openStreams() {
    // ---- Input (microphone), data-callback mode feeding the ring ----
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setInputPreset(oboe::InputPreset::VoicePerformance)
            ->setSessionId(oboe::SessionId::Allocate)
            ->setDeviceId(mInputDeviceId.load())
            ->setDataCallback(this)
            ->setErrorCallback(this);

    oboe::Result result = inBuilder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGE("Input stream failed: %s (retrying with default device)", oboe::convertToText(result));
        mInputDeviceId.store(oboe::kUnspecified);
        inBuilder.setDeviceId(oboe::kUnspecified);
        result = inBuilder.openStream(mInputStream);
        if (result != oboe::Result::OK) { LOGE("Input stream failed hard: %s", oboe::convertToText(result)); return false; }
        mRouteEvents.fetch_add(1);
    }

    // ---- Output ----
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Exclusive)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(2)
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

    const int32_t burst = mOutputStream->getFramesPerBurst();
    mOutputBurst.store(burst);
    mBufferMultiplier.store(1);
    mLastXRunSeen.store(0);
    // Minimum-latency start: ONE burst out, two bursts in. The health check
    // grows the output buffer only if this device actually underruns.
    mOutputStream->setBufferSizeInFrames(burst * 1);
    mInputStream->setBufferSizeInFrames(mInputStream->getFramesPerBurst() * 2);

    const int32_t sr = mOutputStream->getSampleRate();
    mSampleRate.store(sr);
    mChain.prepare(static_cast<double>(sr));
    mMicHead.store(0); mMicTail.store(0);
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
    if (mRecording.load()) stopRecording();
    mRunning.store(false);
    closeStreams();
}

void AudioEngine::setInputDeviceId(int32_t id) {
    if (id == mInputDeviceId.load() && mRunning.load()) return;
    mInputDeviceId.store(id);
    if (mRunning.load()) {
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
    aEqLow.store(p.eqLowGainDb); aEqHigh.store(p.eqHighGainDb);
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
    p.eqLowGainDb = aEqLow.load(); p.eqHighGainDb = aEqHigh.load();
    p.masterGainDb = aMasterDb.load(); p.safetyOn = aSafetyOn.load();
    p.safetyCeilingDb = aSafetyCeilingDb.load(); p.feedbackRisk = aFeedbackRisk.load();
    p.muted = aMuted.load();
    return p;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData,
                                                   int32_t numFrames) {
    if (stream == mInputStream.get()) {
        // Producer: push mono mic frames into the ring (drop newest on overflow).
        const auto *in = static_cast<const float *>(audioData);
        const size_t head = mMicHead.load(std::memory_order_relaxed);
        const size_t tail = mMicTail.load(std::memory_order_acquire);
        const size_t space = kMicRingSize - (head - tail);
        const size_t toWrite = std::min(static_cast<size_t>(numFrames), space);
        for (size_t i = 0; i < toWrite; ++i) {
            mMicRing[(head + i) & (kMicRingSize - 1)] = in[i];
        }
        mMicHead.store(head + toWrite, std::memory_order_release);
        return oboe::DataCallbackResult::Continue;
    }

    // Consumer: pull from the ring, run the chain, write stereo out.
    auto *out = static_cast<float *>(audioData);
    const size_t tail = mMicTail.load(std::memory_order_relaxed);
    const size_t head = mMicHead.load(std::memory_order_acquire);
    const size_t available = head - tail;
    const size_t toRead = std::min(static_cast<size_t>(numFrames), available);

    const DspParams p = snapshotParams();
    float inPeak = 0.0f, outPeak = 0.0f;
    const bool recording = mRecording.load(std::memory_order_relaxed);
    size_t recHead = mRecHead.load(std::memory_order_relaxed);

    for (int32_t i = 0; i < numFrames; ++i) {
        float l = 0.0f;
        if (static_cast<size_t>(i) < toRead) {
            l = mMicRing[(tail + i) & (kMicRingSize - 1)];
        }
        float r = l;                                    // dual-mono
        mChain.processFrame(l, r, p);
        out[i * 2] = l;
        out[i * 2 + 1] = r;
        inPeak = std::max(inPeak, mChain.lastInPeak());
        outPeak = std::max(outPeak, mChain.lastOutPeak());
        if (recording) {
            const size_t recTail = mRecTail.load(std::memory_order_acquire);
            if (recHead - recTail < kRecRingFrames) {
                mRecRing[(recHead & (kRecRingFrames - 1)) * 2] = l;
                mRecRing[(recHead & (kRecRingFrames - 1)) * 2 + 1] = r;
                ++recHead;
            }
        }
    }
    mMicTail.store(tail + toRead, std::memory_order_release);
    if (recording) mRecHead.store(recHead, std::memory_order_release);

    if (inPeak > mInPeak.load()) mInPeak.store(inPeak);
    if (outPeak > mOutPeak.load()) mOutPeak.store(outPeak);
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream * /*stream*/, oboe::Result error) {
    LOGE("Stream error after close: %s — falling back to default route", oboe::convertToText(error));
    mRouteEvents.fetch_add(1);
    if (!mRunning.load()) return;
    mInputDeviceId.store(oboe::kUnspecified);
    mOutputDeviceId.store(oboe::kUnspecified);
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

void AudioEngine::adaptBufferIfNeeded() {
    if (!mOutputStream) return;
    const int32_t xruns = getXRunCount();
    if (xruns > mLastXRunSeen.load()) {
        mLastXRunSeen.store(xruns);
        const int32_t mult = mBufferMultiplier.load();
        if (mult < 4) {
            const int32_t next = mult + 1;
            mBufferMultiplier.store(next);
            mOutputStream->setBufferSizeInFrames(mOutputBurst.load() * next);
            LOGI("XRuns detected (%d) — output buffer grown to %d bursts", xruns, next);
        }
    }
}

float AudioEngine::getLatencyMs() const {
    if (!mOutputStream || !mInputStream) return 0.0f;
    const double sr = static_cast<double>(mSampleRate.load());
    if (sr <= 0) return 0.0f;
    // Measured path: frames the device has captured but the app hasn't
    // consumed (input) + frames written but not yet presented (output),
    // using the streams' hardware timestamps.
    auto outTs = mOutputStream->getTimestamp(CLOCK_MONOTONIC);
    auto inTs = mInputStream->getTimestamp(CLOCK_MONOTONIC);
    if (outTs && inTs) {
        const double outFrames = static_cast<double>(mOutputStream->getFramesWritten() - outTs.value().position);
        const double inFrames = static_cast<double>(inTs.value().position - mInputStream->getFramesRead());
        const double ringFrames = static_cast<double>(mMicHead.load() - mMicTail.load());
        const double total = std::max(0.0, outFrames) + std::max(0.0, inFrames) + ringFrames;
        if (total > 0) return static_cast<float>(total / sr * 1000.0);
    }
    // Fallback: buffer-size estimate.
    const double frames = mInputStream->getBufferSizeInFrames()
                        + mOutputStream->getBufferSizeInFrames()
                        + mOutputStream->getFramesPerBurst();
    return static_cast<float>(frames / sr * 1000.0);
}

int32_t AudioEngine::getAudioSessionId() const {
    return mInputStream ? static_cast<int32_t>(mInputStream->getSessionId()) : -1;
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

// ---------------- Recording (WAV, writer thread) ----------------

static void writeU32LE(FILE *f, uint32_t v) {
    const uint8_t b[4] = {static_cast<uint8_t>(v), static_cast<uint8_t>(v >> 8),
                          static_cast<uint8_t>(v >> 16), static_cast<uint8_t>(v >> 24)};
    fwrite(b, 1, 4, f);
}
static void writeU16LE(FILE *f, uint16_t v) {
    const uint8_t b[2] = {static_cast<uint8_t>(v), static_cast<uint8_t>(v >> 8)};
    fwrite(b, 1, 2, f);
}

bool AudioEngine::startRecording(const std::string &path) {
    if (mRecording.load() || !mRunning.load()) return false;
    mRecPath = path;
    mRecHead.store(0); mRecTail.store(0);
    mRecFramesWritten.store(0);
    mRecording.store(true);
    mRecThread = std::thread([this]() { recorderLoop(); });
    LOGI("Recording started: %s", path.c_str());
    return true;
}

int64_t AudioEngine::stopRecording() {
    if (!mRecording.load()) return mRecFramesWritten.load();
    mRecording.store(false);
    if (mRecThread.joinable()) mRecThread.join();   // writer drains the ring, then patches the header
    LOGI("Recording stopped: %lld frames", (long long) mRecFramesWritten.load());
    return mRecFramesWritten.load();
}

void AudioEngine::recorderLoop() {
    const int32_t sr = mSampleRate.load() > 0 ? mSampleRate.load() : 48000;
    FILE *f = fopen(mRecPath.c_str(), "wb");
    if (!f) { LOGE("Recorder: cannot open %s", mRecPath.c_str()); return; }
    // Placeholder WAV header (patched at close): PCM16 stereo.
    fwrite("RIFF", 1, 4, f); writeU32LE(f, 0); fwrite("WAVE", 1, 4, f);
    fwrite("fmt ", 1, 4, f); writeU32LE(f, 16); writeU16LE(f, 1); writeU16LE(f, 2);
    writeU32LE(f, static_cast<uint32_t>(sr)); writeU32LE(f, static_cast<uint32_t>(sr * 4));
    writeU16LE(f, 4); writeU16LE(f, 16);
    fwrite("data", 1, 4, f); writeU32LE(f, 0);

    int16_t chunk[4096 * 2];
    while (mRecording.load() || mRecTail.load() < mRecHead.load()) {
        const size_t tail = mRecTail.load(std::memory_order_relaxed);
        const size_t head = mRecHead.load(std::memory_order_acquire);
        size_t available = head - tail;
        if (available == 0) {
            if (!mRecording.load()) break;
            std::this_thread::sleep_for(std::chrono::milliseconds(20));
            continue;
        }
        if (available > 4096) available = 4096;
        for (size_t i = 0; i < available; ++i) {
            const size_t idx = (tail + i) & (kRecRingFrames - 1);
            const float l = std::max(-1.0f, std::min(1.0f, mRecRing[idx * 2]));
            const float r = std::max(-1.0f, std::min(1.0f, mRecRing[idx * 2 + 1]));
            chunk[i * 2] = static_cast<int16_t>(l * 32767.0f);
            chunk[i * 2 + 1] = static_cast<int16_t>(r * 32767.0f);
        }
        fwrite(chunk, sizeof(int16_t), available * 2, f);
        mRecFramesWritten.fetch_add(static_cast<int64_t>(available));
        mRecTail.store(tail + available, std::memory_order_release);
    }

    const uint32_t dataBytes = static_cast<uint32_t>(mRecFramesWritten.load() * 4);
    fseek(f, 4, SEEK_SET); writeU32LE(f, 36 + dataBytes);
    fseek(f, 40, SEEK_SET); writeU32LE(f, dataBytes);
    fclose(f);
}
