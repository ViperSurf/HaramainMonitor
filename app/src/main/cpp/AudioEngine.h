#pragma once
#include <oboe/Oboe.h>
#include <atomic>
#include <memory>
#include "dsp/DspChain.h"
#include "dsp/DspParams.h"

// Duplex low-latency engine (pattern follows Oboe's LiveEffect sample):
// an output stream in LowLatency/Exclusive mode pulls frames, synchronously
// reads the input stream inside the callback, runs the DSP chain, and writes.
// All user parameters live in std::atomics; the callback snapshots them once
// per buffer — lock-free, no allocation on the audio thread.
class AudioEngine : public oboe::AudioStreamDataCallback,
                    public oboe::AudioStreamErrorCallback {
public:
    AudioEngine() = default;
    ~AudioEngine() override { stop(); }

    bool start();
    void stop();
    bool isRunning() const { return mRunning.load(); }

    // Routing: oboe::kUnspecified (-1) = system default. Safe to call while
    // running — streams are rebuilt with the new device ids.
    void setInputDeviceId(int32_t id);
    void setOutputDeviceId(int32_t id);

    // Parameter setters (called from JNI/Kotlin, any thread).
    void setParams(const DspParams &p);

    int32_t getSampleRate() const { return mSampleRate.load(); }
    float getLatencyMs() const;
    int32_t getAudioSessionId() const;
    int32_t getXRunCount() const;
    // Peak meters: read-and-reset semantics so Kotlin can decay them in UI time.
    float takeInputPeakDb();
    float takeOutputPeakDb();
    // Incremented whenever a stream dies (device unplugged etc.) and the engine
    // falls back to the default device — Kotlin polls this to refresh badges.
    uint32_t routeEventCount() const { return mRouteEvents.load(); }

    // Oboe callbacks
    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *stream, void *audioData,
                                          int32_t numFrames) override;
    void onErrorAfterClose(oboe::AudioStream *stream, oboe::Result error) override;

private:
    bool openStreams();
    void closeStreams();
    DspParams snapshotParams() const;

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::atomic<bool> mRunning{false};
    std::atomic<int32_t> mInputDeviceId{oboe::kUnspecified};
    std::atomic<int32_t> mOutputDeviceId{oboe::kUnspecified};
    std::atomic<int32_t> mSampleRate{0};
    std::atomic<uint32_t> mRouteEvents{0};
    std::atomic<float> mInPeak{0.0f};
    std::atomic<float> mOutPeak{0.0f};

    // Lock-free parameter block (one atomic per field; snapshot per callback).
    std::atomic<float> aBoost{50.0f}, aStatic{60.0f}, aSuppress{40.0f}, aGate{-45.0f},
            aDeEsser{50.0f}, aComp{70.0f}, aRoom{95.0f}, aMix{52.0f},
            aBalance{0.0f}, aMasterDb{0.0f}, aSafetyCeilingDb{-3.0f};
    std::atomic<bool> aSuppressOn{true}, aSafetyOn{true}, aFeedbackRisk{false}, aMuted{false};

    DspChain mChain;
    static constexpr int32_t kMaxFrames = 4096;
    float mInputBuffer[kMaxFrames];   // mono mic frames pulled per callback
};
