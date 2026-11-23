#include "DspChain.h"
#include "HowlDetector.h"
#include <algorithm>
#include <cmath>
#include <cstdint>

// Standalone WebAssembly bridge for Haramain Monitor's C++17 DSP engine.
// Compiled with Emscripten (--no-entry -s STANDALONE_WASM=1) and driven
// inside an AudioWorkletProcessor on 128-frame Web Audio quanta.

namespace {

constexpr int MAX_FRAMES = 256;
constexpr int HOWL_RING_SIZE = 4096;

DspChain gChain;
DspParams gParams;
HowlDetector gHowl;
double gSampleRate = 48000.0;

float gInL[MAX_FRAMES];
float gInR[MAX_FRAMES];
float gOutL[MAX_FRAMES];
float gOutR[MAX_FRAMES];

float gHowlRing[HOWL_RING_SIZE];
int gHowlWritePos = 0;
int gHowlSamplesSinceCheck = 0;
float gNotchTimerSec[4] = {0.0f, 0.0f, 0.0f, 0.0f};
int gActiveNotches = 0;

void updateHowlGuard(int frames) {
    if (!gParams.howlGuardOn || gParams.muted || gParams.bypass) {
        for (int i = 0; i < 4; ++i) {
            gParams.notchFreq[i] = 0.0f;
            gNotchTimerSec[i] = 0.0f;
        }
        gActiveNotches = 0;
        return;
    }

    const float dt = static_cast<float>(frames) / static_cast<float>(gSampleRate);
    int active = 0;
    for (int i = 0; i < 4; ++i) {
        if (gParams.notchFreq[i] > 1.0f) {
            gNotchTimerSec[i] -= dt;
            if (gNotchTimerSec[i] <= 0.0f) {
                gParams.notchFreq[i] = 0.0f;
            } else {
                ++active;
            }
        }
    }

    for (int i = 0; i < frames; ++i) {
        gHowlRing[gHowlWritePos] = 0.5f * (gOutL[i] + gOutR[i]);
        gHowlWritePos = (gHowlWritePos + 1) % HOWL_RING_SIZE;
    }

    gHowlSamplesSinceCheck += frames;
    const int checkInterval = static_cast<int>(gSampleRate * 0.08); // ~80 ms
    if (gHowlSamplesSinceCheck >= checkInterval) {
        gHowlSamplesSinceCheck = 0;
        // Linearize ring buffer for HowlDetector::analyze
        static float linear[HOWL_RING_SIZE];
        const int firstLen = HOWL_RING_SIZE - gHowlWritePos;
        std::copy(gHowlRing + gHowlWritePos, gHowlRing + HOWL_RING_SIZE, linear);
        std::copy(gHowlRing, gHowlRing + gHowlWritePos, linear + firstLen);

        const float detectedHz = gHowl.analyze(linear, HOWL_RING_SIZE);
        if (detectedHz > 100.0f) {
            int targetSlot = -1;
            for (int i = 0; i < 4; ++i) {
                if (gParams.notchFreq[i] > 1.0f && std::fabs(gParams.notchFreq[i] - detectedHz) < 35.0f) {
                    targetSlot = i;
                    break;
                }
                if (targetSlot < 0 && gParams.notchFreq[i] < 1.0f) {
                    targetSlot = i;
                }
            }
            if (targetSlot < 0) targetSlot = 0;
            gParams.notchFreq[targetSlot] = detectedHz;
            gNotchTimerSec[targetSlot] = 12.0f;
        }
    }

    active = 0;
    for (int i = 0; i < 4; ++i) {
        if (gParams.notchFreq[i] > 1.0f) ++active;
    }
    gActiveNotches = active;
}

} // namespace

extern "C" {

void dsp_init(double sampleRate) {
    gSampleRate = sampleRate > 8000.0 ? sampleRate : 48000.0;
    gChain.prepare(gSampleRate);
    gChain.reset();
    gHowl.prepare(gSampleRate);
    std::fill(gHowlRing, gHowlRing + HOWL_RING_SIZE, 0.0f);
    gHowlWritePos = 0;
    gHowlSamplesSinceCheck = 0;
    for (int i = 0; i < 4; ++i) {
        gParams.notchFreq[i] = 0.0f;
        gNotchTimerSec[i] = 0.0f;
    }
    gActiveNotches = 0;
}

float* dsp_get_in_l_ptr()  { return gInL; }
float* dsp_get_in_r_ptr()  { return gInR; }
float* dsp_get_out_l_ptr() { return gOutL; }
float* dsp_get_out_r_ptr() { return gOutR; }

void dsp_process(int numFrames) {
    const int n = std::clamp(numFrames, 0, MAX_FRAMES);
    for (int i = 0; i < n; ++i) {
        float l = gInL[i];
        float r = gInR[i];
        gChain.processFrame(l, r, gParams);

        // Monitor Ears routing (0 = stereo, 1 = left ear only, 2 = right ear only)
        if (gParams.monitorMode == 1) {
            r = 0.0f;
        } else if (gParams.monitorMode == 2) {
            l = 0.0f;
        }
        gOutL[i] = l;
        gOutR[i] = r;
    }
    updateHowlGuard(n);
}

void dsp_set_param(int id, float value) {
    switch (id) {
        case 0:  gParams.micBoostPercent   = value; break;
        case 1:  gParams.staticRemover     = value; break;
        case 2:  gParams.noiseSuppress     = value; break;
        case 3:  gParams.noiseSuppressOn   = (value > 0.5f); break;
        case 4:  gParams.gateThresholdDb   = value; break;
        case 5:  gParams.deEsserPercent    = value; break;
        case 6:  gParams.compressorPercent = value; break;
        case 7:  gParams.roomSizePercent   = value; break;
        case 8:  gParams.wetDryPercent     = value; break;
        case 9:  gParams.balancePercent    = value; break;
        case 10: gParams.eqLowGainDb       = value; break;
        case 11: gParams.eqHighGainDb      = value; break;
        case 12: gParams.masterGainDb      = value; break;
        case 13: gParams.btDuckPercent     = value; break;
        case 14: gParams.btDuckOn          = (value > 0.5f); break;
        case 15: gParams.eqWarmthDb        = value; break;
        case 16: gParams.eqClarityDb       = value; break;
        case 17: gParams.eqAirDb           = value; break;
        case 18: gParams.safetyOn          = (value > 0.5f); break;
        case 19: gParams.safetyCeilingDb   = value; break;
        case 20: gParams.eqBand60Db        = value; break;
        case 21: gParams.eqBand250Db       = value; break;
        case 22: gParams.eqBand1kDb        = value; break;
        case 23: gParams.eqBand4kDb        = value; break;
        case 24: gParams.eqBand12kDb       = value; break;
        case 25: gParams.revDampingPercent = value; break;
        case 26: gParams.revWidthPercent   = value; break;
        case 27: gParams.monitorMode       = static_cast<int>(value); break;
        case 28: gParams.howlGuardOn       = (value > 0.5f); break;
        case 29: gParams.muted             = (value > 0.5f); break;
        case 30: gParams.bypass            = (value > 0.5f); break;
        default: break;
    }
}

float dsp_get_in_peak()      { return gChain.lastInPeak(); }
float dsp_get_out_peak()     { return gChain.lastOutPeak(); }
float dsp_get_gate_gain()    { return gChain.lastGateGain(); }
float dsp_get_ns_gain()      { return gChain.lastNsGain(); }
float dsp_get_limiter_gain() { return gChain.lastLimiterGain(); }
float dsp_get_comp_gr_db()   { return gChain.lastCompGrDb(); }
int   dsp_get_active_notches() { return gActiveNotches; }

} // extern "C"
