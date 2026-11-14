#pragma once
#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include <string>
#include <thread>
#include "dsp/DspChain.h"
#include "dsp/DspParams.h"

// Duplex low-latency engine, v2 topology:
//   INPUT stream (data callback) -> SPSC ring buffer -> OUTPUT stream
//   (data callback) runs the DSP chain and writes. No blocking reads in the
//   output callback (v1's synchronous-read design cost up to a burst of wait).
// Output buffer starts at ONE burst; a health check (driven from Kotlin once
// per second) grows it by a burst whenever xruns appear, up to 4 bursts —
// minimum latency that the individual device can actually sustain.
// All parameters are std::atomics, snapshotted once per callback.
class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine() = default;
    ~AudioEngine() override { stop(); }

    bool start();
    void stop();
    bool isRunning() const { return mRunning.load(); }

    void setInputDeviceId(int32_t id);
    void setOutputDeviceId(int32_t id);
    void setParams(const DspParams &p);

    int32_t getSampleRate() const { return mSampleRate.load(); }
    float getLatencyMs() const;                 // timestamp-measured, buffer estimate as fallback
    int32_t getAudioSessionId() const;
    int32_t getXRunCount() const;
    float takeInputPeakDb();
    float takeOutputPeakDb();
    uint32_t routeEventCount() const { return mRouteEvents.load(); }
    void adaptBufferIfNeeded();                 // xrun-driven output buffer growth

    // Recording tap (post-DSP stereo) -> WAV file on a writer thread.
    bool startRecording(const std::string &path, bool dry = false);
    void setBufferMode(int32_t bursts);          // user latency preference: 1 / 2 / 4 bursts
    void setBypass(bool on) { aBypass.store(on); }   // A/B compare (momentary)
    void setUsageGame(bool game);                // BT low-latency output usage hint
    void setPitchEnabled(bool on) { mPitchEnabled.store(on); }
    float getPitchHz() const { return mPitchHz.load(); }
    // Worst (lowest) stage gains since the previous call; reading resets
    // the accumulators, so a 50 ms UI poll captures every dip in between.
    void activity(float &gate, float &ns, float &lim, float &notchHz, float &notchCount,
                  float &compDb, float &deEssGain) {
        int count = 0; float hz = 0.0f;
        for (int s = 0; s < 4; ++s) {
            const float f = aNotchFreq[s].load();
            if (f > 1.0f) { ++count; if (hz == 0.0f) hz = f; }
        }
        notchHz = hz; notchCount = static_cast<float>(count);
        activity(gate, ns, lim);
        compDb = mActComp.load(); mActComp.store(0.0f);
        deEssGain = mActDeEss.load(); mActDeEss.store(1.0f);
    }
    void activity(float &gate, float &ns, float &lim) {
        gate = mActGate.load(); ns = mActNs.load(); lim = mActLim.load();
        mActGate.store(1.0f); mActNs.store(1.0f); mActLim.store(1.0f);
    }
    /** The devices AAudio ACTUALLY routed to (ground truth when the
     *  picker selection is "system default"). 0 = no stream. */
    int32_t inputDeviceId() const { return mInputStream ? mInputStream->getDeviceId() : 0; }
    int32_t outputDeviceId() const { return mOutputStream ? mOutputStream->getDeviceId() : 0; }

    int32_t outputBufferFrames() const {
        return mOutputStream ? mOutputStream->getBufferSizeInFrames() : 0;
    }
    int64_t stopRecording();                    // returns frames written
    bool isRecording() const { return mRecording.load(); }

    // Calibration analyzer: measures the RAW input (pre-DSP) while active —
    // RMS, peak, and high-frequency (>~4 kHz) RMS for the Auto Setup wizard.
    void startCalibration();
    // Stops and waits briefly for the callback to publish; false if no audio flowed.
    bool stopCalibration(float &rmsDb, float &peakDb, float &hfDb, float &seconds);

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    bool openStreams();
    void closeStreams();
    DspParams snapshotParams() const;

    // Mic ring (mono, SPSC: input callback produces, output callback consumes)
    static constexpr size_t kMicRingSize = 16384;   // power of two, frames
    float mMicRing[kMicRingSize];
    std::atomic<size_t> mMicHead{0}, mMicTail{0};

    // Recording ring (interleaved stereo, SPSC: output callback -> writer thread)
    static constexpr size_t kRecRingFrames = 65536;
    float mRecRing[kRecRingFrames * 2];
    std::atomic<size_t> mRecHead{0}, mRecTail{0};
    std::atomic<bool> mRecording{false};
    std::atomic<bool> mRecDry{false};
    std::atomic<int32_t> mUserBursts{1};
    std::atomic<bool> mUsageGame{false};

    // Pitch detection: raw-input history ring + worker-thread autocorrelation.
    static constexpr size_t kPitchHist = 8192;   // power of two
    float mPitchRing[kPitchHist];
    std::atomic<size_t> mPitchHead{0};
    std::atomic<bool> mPitchEnabled{false};
    std::atomic<float> mPitchHz{0.0f};
    std::atomic<float> mActGate{1.0f}, mActNs{1.0f}, mActLim{1.0f};
    std::atomic<float> mActComp{0.0f}, mActDeEss{1.0f};
    std::atomic<bool> mPitchStop{false};
    std::thread mPitchThread;
    void pitchLoop();
    std::thread mGuardThread;
    std::atomic<bool> mGuardStop{false};
    void guardLoop();
    std::thread mRecThread;
    std::string mRecPath;
    std::atomic<int64_t> mRecFramesWritten{0};
    void recorderLoop();

    // Calibration state (accumulators touched only by the input callback)
    std::atomic<bool> mCalActive{false};
    std::atomic<bool> mCalReset{false};
    std::atomic<bool> mCalPublishReq{false};
    std::atomic<uint32_t> mCalPublishSeq{0};
    std::atomic<float> mCalRmsDb{-80.0f}, mCalPeakDb{-80.0f}, mCalHfDb{-80.0f};
    std::atomic<int64_t> mCalFramesPub{0};
    double calSumSq_ = 0.0, calHfSumSq_ = 0.0, calPeak_ = 0.0;
    int64_t calFrames_ = 0;
    float calHp_ = 0.0f, calPrev_ = 0.0f;

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::atomic<bool> mRunning{false};
    // Start/stop anti-pop fade: target set by start()/stop(), the gain
    // itself lives on the callback thread and eases toward it (~40 ms).
    std::atomic<float> mFadeTarget{0.0f};
    float mFadeGain = 0.0f;
    float mFadeCoeff = 0.0005f;
    std::atomic<int32_t> mInputDeviceId{oboe::kUnspecified};
    std::atomic<int32_t> mOutputDeviceId{oboe::kUnspecified};
    std::atomic<int32_t> mSampleRate{0};
    std::atomic<uint32_t> mRouteEvents{0};
    std::atomic<float> mInPeak{0.0f};
    std::atomic<float> mOutPeak{0.0f};
    std::atomic<int32_t> mOutputBurst{0};
    std::atomic<int32_t> mBufferMultiplier{1};
    std::atomic<int32_t> mLastXRunSeen{0};

    std::atomic<float> aBoost{50.0f}, aStatic{60.0f}, aSuppress{40.0f}, aGate{-45.0f},
            aDeEsser{50.0f}, aComp{70.0f}, aRoom{95.0f}, aMix{52.0f},
            aBalance{0.0f}, aEqLow{0.0f}, aEqHigh{0.0f}, aMasterDb{0.0f}, aSafetyCeilingDb{-3.0f},
            aBtDuck{50.0f}, aWarmth{0.0f}, aClarity{0.0f}, aAir{0.0f},
            aEq60{0.0f}, aEq250{0.0f}, aEq1k{0.0f}, aEq4k{0.0f}, aEq12k{0.0f},
            aRevDamp{50.0f}, aRevWidth{100.0f};
    std::atomic<int> aMonitorMode{0};
    std::atomic<bool> aHowlGuardOn{true};
    std::atomic<float> aNotchFreq[4]{};
    std::atomic<long long> aNotchStampMs[4]{};
    std::atomic<bool> aSuppressOn{true}, aSafetyOn{true}, aFeedbackRisk{false}, aMuted{false},
            aBypass{false},
            aBtDuckOn{false};

    DspChain mChain;
};
