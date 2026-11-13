#include "AudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <thread>
#include <chrono>
#include <vector>

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
            ->setUsage(mUsageGame.load() ? oboe::Usage::Game : oboe::Usage::Media)
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
    // Start at the user's buffer mode (default 1 burst = ultra-low). The
    // stream stays at the device's NATIVE sample rate (never forced), so
    // Android keeps the AAudio MMAP / FastMixer path — no OS resampling.
    // Bluetooth outputs get a 2-burst FLOOR regardless of the user's mode:
    // the radio link consumes in large, jittery chunks and a 1-burst app
    // buffer starves it into recurring dropouts.
    const int32_t effMult = mUsageGame.load() ? std::max<int32_t>(mUserBursts.load(), 2)
                                              : mUserBursts.load();
    mBufferMultiplier.store(effMult);
    mLastXRunSeen.store(0);
    mOutputStream->setBufferSizeInFrames(burst * effMult);
    mInputStream->setBufferSizeInFrames(mInputStream->getFramesPerBurst() * effMult);

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
    // Recovery restarts can reach start() without a stop() (stream-error
    // fallback) — never assign over a joinable thread (std::terminate).
    if (mPitchThread.joinable()) {
        mPitchStop.store(true);
        mPitchThread.join();
    }
    mPitchStop.store(false);
    mPitchThread = std::thread([this]() { pitchLoop(); });
    LOGI("Engine started");
    return true;
}

void AudioEngine::stop() {
    if (mRecording.load()) stopRecording();
    mRunning.store(false);
    mPitchStop.store(true);
    if (mPitchThread.joinable()) mPitchThread.join();
    mPitchHz.store(0.0f);
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

void AudioEngine::setBufferMode(int32_t bursts) {
    const int32_t b = std::max<int32_t>(1, std::min<int32_t>(4, bursts));
    mUserBursts.store(b);
    if (mRunning.load() && mOutputStream) {
        mBufferMultiplier.store(b);
        mOutputStream->setBufferSizeInFrames(mOutputBurst.load() * b);
        if (mInputStream) {
            mInputStream->setBufferSizeInFrames(mInputStream->getFramesPerBurst() * b);
        }
        mLastXRunSeen.store(getXRunCount());   // don't let the grower instantly override the user
        LOGI("Buffer mode set by user: %d bursts", b);
    }
}

void AudioEngine::setUsageGame(bool game) {
    if (game == mUsageGame.load()) return;
    mUsageGame.store(game);
    if (mRunning.load()) {
        closeStreams();
        if (!openStreams() || mInputStream->requestStart() != oboe::Result::OK ||
            mOutputStream->requestStart() != oboe::Result::OK) {
            LOGE("Re-route (usage) failed"); mRunning.store(false);
        }
    }
}

void AudioEngine::pitchLoop() {
    std::vector<float> buf(4096);
    while (!mPitchStop.load()) {
        std::this_thread::sleep_for(std::chrono::milliseconds(90));
        if (!mPitchEnabled.load() || !mRunning.load()) { mPitchHz.store(0.0f); continue; }
        const int32_t sr = mSampleRate.load();
        if (sr <= 0) continue;
        const size_t head = mPitchHead.load(std::memory_order_acquire);
        if (head < buf.size()) continue;
        for (size_t i = 0; i < buf.size(); ++i)
            buf[i] = mPitchRing[(head - buf.size() + i) & (kPitchHist - 1)];
        double energy = 0.0;
        for (float s : buf) energy += static_cast<double>(s) * s;
        if (energy / static_cast<double>(buf.size()) < 1e-6) { mPitchHz.store(0.0f); continue; }
        // Normalized autocorrelation over the vocal range 60..600 Hz.
        const int minLag = sr / 600, maxLag = sr / 60;
        double best = 0.0; int bestLag = 0;
        for (int lag = minLag; lag <= maxLag; ++lag) {
            double num = 0.0, d1 = 0.0, d2 = 0.0;
            const int n = static_cast<int>(buf.size()) - lag;
            for (int i = 0; i < n; i += 2) {
                const double a = buf[i], b = buf[i + lag];
                num += a * b; d1 += a * a; d2 += b * b;
            }
            const double corr = (d1 > 0 && d2 > 0) ? num / std::sqrt(d1 * d2) : 0.0;
            if (corr > best) { best = corr; bestLag = lag; }
        }
        mPitchHz.store(best > 0.55 && bestLag > 0 ? static_cast<float>(sr) / bestLag : 0.0f);
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
    aBtDuck.store(p.btDuckPercent); aBtDuckOn.store(p.btDuckOn);
    aWarmth.store(p.eqWarmthDb); aClarity.store(p.eqClarityDb); aAir.store(p.eqAirDb);
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
    p.btDuckPercent = aBtDuck.load(); p.btDuckOn = aBtDuckOn.load();
    p.eqWarmthDb = aWarmth.load(); p.eqClarityDb = aClarity.load(); p.eqAirDb = aAir.load();
    return p;
}

oboe::DataCallbackResult AudioEngine::onAudioReady(oboe::AudioStream *stream, void *audioData,
                                                   int32_t numFrames) {
    if (stream == mInputStream.get()) {
        const auto *raw = static_cast<const float *>(audioData);
        // Calibration analyzer on the RAW input (pre-DSP, pre-ring).
        if (mCalReset.exchange(false)) {
            calSumSq_ = 0.0; calHfSumSq_ = 0.0; calPeak_ = 0.0; calFrames_ = 0;
            calHp_ = 0.0f; calPrev_ = 0.0f;
        }
        if (mCalActive.load(std::memory_order_relaxed)) {
            for (int32_t i = 0; i < numFrames; ++i) {
                const float x = raw[i];
                calSumSq_ += static_cast<double>(x) * x;
                calPeak_ = std::max(calPeak_, static_cast<double>(std::fabs(x)));
                calHp_ = 0.78f * (calHp_ + x - calPrev_);   // one-pole high-pass ~4 kHz
                calPrev_ = x;
                calHfSumSq_ += static_cast<double>(calHp_) * calHp_;
                ++calFrames_;
            }
        }
        if (mCalPublishReq.exchange(false)) {
            const int64_t n = std::max<int64_t>(calFrames_, 1);
            const double rms = std::sqrt(calSumSq_ / n);
            const double hf = std::sqrt(calHfSumSq_ / n);
            mCalRmsDb.store(rms > 1e-7 ? static_cast<float>(20.0 * std::log10(rms)) : -80.0f);
            mCalHfDb.store(hf > 1e-7 ? static_cast<float>(20.0 * std::log10(hf)) : -80.0f);
            mCalPeakDb.store(calPeak_ > 1e-7 ? static_cast<float>(20.0 * std::log10(calPeak_)) : -80.0f);
            mCalFramesPub.store(calFrames_);
            mCalPublishSeq.fetch_add(1);
        }
        // Producer: push mono mic frames into the ring (drop newest on overflow).
        const auto *in = raw;
        const size_t head = mMicHead.load(std::memory_order_relaxed);
        const size_t tail = mMicTail.load(std::memory_order_acquire);
        const size_t space = kMicRingSize - (head - tail);
        const size_t toWrite = std::min(static_cast<size_t>(numFrames), space);
        for (size_t i = 0; i < toWrite; ++i) {
            mMicRing[(head + i) & (kMicRingSize - 1)] = in[i];
        }
        mMicHead.store(head + toWrite, std::memory_order_release);
        // Pitch history (raw input) + dry-vocal recording tap.
        if (mPitchEnabled.load(std::memory_order_relaxed)) {
            const size_t ph = mPitchHead.load(std::memory_order_relaxed);
            for (int32_t i = 0; i < numFrames; ++i)
                mPitchRing[(ph + static_cast<size_t>(i)) & (kPitchHist - 1)] = raw[i];
            mPitchHead.store(ph + static_cast<size_t>(numFrames), std::memory_order_release);
        }
        if (mRecording.load(std::memory_order_relaxed) &&
            mRecDry.load(std::memory_order_relaxed)) {
            size_t recHead = mRecHead.load(std::memory_order_relaxed);
            const size_t recTail = mRecTail.load(std::memory_order_acquire);
            for (int32_t i = 0; i < numFrames && recHead - recTail < kRecRingFrames; ++i) {
                mRecRing[(recHead & (kRecRingFrames - 1)) * 2] = raw[i];
                mRecRing[(recHead & (kRecRingFrames - 1)) * 2 + 1] = raw[i];
                ++recHead;
            }
            mRecHead.store(recHead, std::memory_order_release);
        }
        return oboe::DataCallbackResult::Continue;
    }

    // Consumer: pull from the ring, run the chain, write stereo out.
    auto *out = static_cast<float *>(audioData);
    size_t tail = mMicTail.load(std::memory_order_relaxed);
    const size_t head = mMicHead.load(std::memory_order_acquire);
    // Clock-drift guard: input and output devices run on different crystals
    // (worst in split-path: phone mic in, Bluetooth out). If the producer
    // has run far ahead, drop the oldest frames back to a quarter ring —
    // otherwise latency creeps up for minutes and ends in a burst of
    // dropouts when the ring finally wraps.
    if (head - tail > kMicRingSize * 3 / 4) {
        tail = head - kMicRingSize / 4;
        mMicTail.store(tail, std::memory_order_relaxed);
    }
    const size_t available = head - tail;
    const size_t toRead = std::min(static_cast<size_t>(numFrames), available);

    const DspParams p = snapshotParams();
    float inPeak = 0.0f, outPeak = 0.0f;
    float minGate = 1.0f, minNs = 1.0f, minLim = 1.0f;
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
        minGate = std::min(minGate, mChain.lastGateGain());
        minNs = std::min(minNs, mChain.lastNsGain());
        minLim = std::min(minLim, mChain.lastLimiterGain());
        if (recording && !mRecDry.load(std::memory_order_relaxed)) {
            const size_t recTail = mRecTail.load(std::memory_order_acquire);
            if (recHead - recTail < kRecRingFrames) {
                mRecRing[(recHead & (kRecRingFrames - 1)) * 2] = l;
                mRecRing[(recHead & (kRecRingFrames - 1)) * 2 + 1] = r;
                ++recHead;
            }
        }
    }
    mMicTail.store(tail + toRead, std::memory_order_release);
    if (recording && !mRecDry.load(std::memory_order_relaxed)) mRecHead.store(recHead, std::memory_order_release);

    if (inPeak > mInPeak.load()) mInPeak.store(inPeak);
    if (outPeak > mOutPeak.load()) mOutPeak.store(outPeak);
    mActGate.store(std::min(mActGate.load(), minGate));
    mActNs.store(std::min(mActNs.load(), minNs));
    mActLim.store(std::min(mActLim.load(), minLim));
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
        const int32_t cap = mUsageGame.load() ? 8 : 4;   // BT links may need more headroom
        if (mult < cap) {
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

// ---------------- Calibration analyzer ----------------

void AudioEngine::startCalibration() {
    mCalReset.store(true);
    mCalActive.store(true);
}

bool AudioEngine::stopCalibration(float &rmsDb, float &peakDb, float &hfDb, float &seconds) {
    mCalActive.store(false);
    const uint32_t seqBefore = mCalPublishSeq.load();
    mCalPublishReq.store(true);
    for (int i = 0; i < 60; ++i) {   // up to ~300 ms for the next input callback
        if (mCalPublishSeq.load() != seqBefore) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
    }
    const int64_t frames = mCalFramesPub.load();
    const int32_t sr = mSampleRate.load();
    if (frames <= 0 || sr <= 0) return false;
    rmsDb = mCalRmsDb.load();
    peakDb = mCalPeakDb.load();
    hfDb = mCalHfDb.load();
    seconds = static_cast<float>(frames) / static_cast<float>(sr);
    return true;
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

bool AudioEngine::startRecording(const std::string &path, bool dry) {
    if (mRecording.load() || !mRunning.load()) return false;
    mRecDry.store(dry);
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
