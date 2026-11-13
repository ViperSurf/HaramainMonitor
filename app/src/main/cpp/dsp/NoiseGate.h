#pragma once
#include "Biquad.h"
#include <cmath>

// Fast expander/gate with hysteresis and smoothed gain so phrases never chop.
// Operates on a linked (max-of-channels) detector; gain is applied per sample.
class NoiseGate {
public:
    void prepare(double sampleRate) {
        sampleRate_ = sampleRate;
        // 4 ms attack on a mean-absolute envelope: fast enough for word onsets,
        // but steady hiss (whose peaks poke ~12 dB above its RMS) reads as one
        // low level and stays under the threshold instead of chattering the gate.
        attack_.setTimeConstant(sampleRate, 4.0);
        release_.setTimeConstant(sampleRate, 140.0);  // 140 ms — natural phrase tails
    }

    // detectorInput: absolute linked level for this frame (max |L|,|R|)
    // thresholdDb: -80..0. Returns linear gain 0..1.
    inline float processGain(float detectorInput, float thresholdDb) {
        env_ = detectorInput > env_
               ? attack_.process(detectorInput)
               : release_.process(detectorInput);
        const float envDb = linearToDb(env_);
        // 3 dB hysteresis band avoids chattering at the threshold edge.
        const bool open = gateOpen_ ? (envDb > thresholdDb - 3.0f) : (envDb > thresholdDb);
        gateOpen_ = open;
        const float target = open ? 1.0f : 0.0f;
        // Smooth the gain itself (5 ms) so opening/closing is click-free.
        gain_ += 0.35f * (target - gain_); // ~per-sample smoothing at audio rate is plenty with the envelope above
        return gain_;
    }

    void reset() { env_ = 0; gain_ = 0; gateOpen_ = false; attack_.reset(); release_.reset(); }

private:
    double sampleRate_ = 48000;
    OnePole attack_, release_;
    float env_ = 0, gain_ = 0;
    bool gateOpen_ = false;
};
