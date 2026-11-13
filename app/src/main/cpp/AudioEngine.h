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
    bool startRecording(const std::string &path);
    int64_t stopRecording();                    // returns frames written
    bool isRecording() const { return mRecording.load(); }

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
    std::thread mRecThread;
    std::string mRecPath;
    std::atomic<int64_t> mRecFramesWritten{0};
    void recorderLoop();

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::atomic<bool> mRunning{false};
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
            aBalance{0.0f}, aEqLow{0.0f}, aEqHigh{0.0f}, aMasterDb{0.0f}, aSafetyCeilingDb{-3.0f};
    std::atomic<bool> aSuppressOn{true}, aSafetyOn{true}, aFeedbackRisk{false}, aMuted{false};

    DspChain mChain;
};
