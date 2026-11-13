#pragma once
#include "Biquad.h"
#include <cmath>

// Expander/gate voiced for distant-mic recitation, where the voice hovers
// NEAR the threshold and a naive gate chatters (the "weird chopped noise"
// artifact). Three defenses:
//   1. Mean-absolute envelope (4 ms / 140 ms) — steady hiss reads as one low
//      level instead of peaks poking over the threshold.
//   2. Wide 6 dB hysteresis — once open, the level must fall well below the
//      threshold before closing is even considered.
//   3. 150 ms HOLD + smooth gain ramps (1.5 ms open / 90 ms close) — short
//      dips between syllables never slam the gate; no clicks, no tremolo.
class NoiseGate {
public:
    void prepare(double sampleRate) {
        sampleRate_ = sampleRate;
        attack_.setTimeConstant(sampleRate, 4.0);
        release_.setTimeConstant(sampleRate, 100.0);   // env decay toward silence (hold + ramps add the rest)
        gainAttack_.setTimeConstant(sampleRate, 1.5);
        gainRelease_.setTimeConstant(sampleRate, 90.0);
        holdSamples_ = static_cast<int>(sampleRate * 0.150);
    }

    // detectorInput: absolute linked level for this frame (max |L|,|R|)
    // thresholdDb: -80..0. Returns linear gain 0..1.
    inline float processGain(float detectorInput, float thresholdDb) {
        // Single envelope state, attack/release coefficients (no dual-state jumps).
        env_ += (detectorInput > env_ ? attack_.coeff : release_.coeff) * (detectorInput - env_);
        const float envDb = linearToDb(env_);

        if (envDb > thresholdDb) {
            gateOpen_ = true;
            holdCounter_ = holdSamples_;            // refresh hold while voice is present
        } else if (envDb < thresholdDb - 6.0f) {    // 6 dB hysteresis band
            if (holdCounter_ > 0) --holdCounter_;   // hold first…
            else gateOpen_ = false;                 // …then allow closing
        }
        // Inside the hysteresis band: keep the previous state.

        const float target = gateOpen_ ? 1.0f : 0.0f;
        gain_ = target > gain_
                ? gain_ + gainAttack_.coeff * (target - gain_)
                : gain_ + gainRelease_.coeff * (target - gain_);
        return gain_;
    }

    bool isOpen() const { return gateOpen_; }

    void reset() {
        env_ = 0; gain_ = 0; gateOpen_ = false; holdCounter_ = 0;
        attack_.reset(); release_.reset();
    }

private:
    double sampleRate_ = 48000;
    OnePole attack_, release_, gainAttack_, gainRelease_;
    float env_ = 0, gain_ = 0;
    bool gateOpen_ = false;
    int holdSamples_ = 7200, holdCounter_ = 0;
};
