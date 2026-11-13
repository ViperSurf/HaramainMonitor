#pragma once
#include "Biquad.h"
#include <cmath>

// Stage 6 — Vocal compressor. amount 0..100 maps musically:
//   threshold -30 dB -> -8 dB, ratio 1.5:1 -> 6:1, knee 12 dB -> 4 dB.
// Fast attack (3 ms) catches recitation peaks; medium release (180 ms) keeps
// phrasing natural. Makeup gain is automatic: half the average reduction,
// capped at +12 dB, so quieter passages lift without pumping.
class Compressor {
public:
    void prepare(double sampleRate) {
        attack_.setTimeConstant(sampleRate, 3.0);
        release_.setTimeConstant(sampleRate, 180.0);
        grSmooth_.setTimeConstant(sampleRate, 250.0);
    }

    // detectorInput: linked absolute level. Returns gain to apply (incl. makeup).
    inline float processGain(float detectorInput, float amount01) {
        if (amount01 <= 0.001f) return 1.0f;
        const float threshold = -30.0f + amount01 * 22.0f;      // -30..-8 dB
        const float ratio = 1.5f + amount01 * 4.5f;              // 1.5..6
        const float knee = 12.0f - amount01 * 8.0f;              // 12..4 dB
        const float levelDb = linearToDb(std::max(detectorInput, 1e-6f));
        const float over = levelDb - threshold;
        float grDb = 0.0f;
        if (over > knee * 0.5f) {
            grDb = (over - knee * 0.5f) * (1.0f - 1.0f / ratio);
        } else if (over > -knee * 0.5f) {
            const float t = (over + knee * 0.5f) / knee;         // soft-knee region
            grDb = t * t * knee * 0.5f * (1.0f - 1.0f / ratio);
        }
        const float target = dbToLinear(-grDb);
        // Single gain state, smoothed with the attack/release coefficients.
        gain_ = target < gainState_ ? gainState_ + attack_.coeff * (target - gainState_)
                                    : gainState_ + release_.coeff * (target - gainState_);
        gainState_ = gain_;
        const float avgGrDb = grSmooth_.process(grDb);
        const float makeupDb = clampf(avgGrDb * 0.5f, 0.0f, 12.0f);
        return gain_ * dbToLinear(makeupDb);
    }

    void reset() { gain_ = 1; gainState_ = 1; attack_.reset(); release_.reset(); grSmooth_.reset(); }

private:
    OnePole attack_, release_, grSmooth_;
    float gain_ = 1.0f, gainState_ = 1.0f;
};
