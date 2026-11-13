#pragma once
#include "Biquad.h"
#include <cmath>

// Stage 4 — Voice-isolation noise suppression (broadband, C++ side).
// Minimum-statistics style noise-floor tracker: the floor only adapts while the
// signal is low (pauses), then a Wiener-style gain g = max(minGain, 1 - N/S)
// attenuates steady background (fans, traffic wash) while leaving voice peaks
// untouched. This runs alongside Android's platform NoiseSuppressor effect,
// which AudioRouter attaches to the input session when the device offers it.
class NoiseSuppressor {
public:
    void prepare(double sampleRate) {
        env_.setTimeConstant(sampleRate, 10.0);
        gainSmooth_.setTimeConstant(sampleRate, 30.0);
        noiseEnv_.state = 1e-5f;
    }

    // Returns the suppression gain to apply this sample.
    inline float processGain(float detectorInput, float amount01) {
        const float level = env_.process(detectorInput);
        // Track the floor from below: rises very slowly, falls fast toward minima.
        if (level < noiseEnv_.state) noiseEnv_.state = level;
        else noiseEnv_.state += 0.00002f * (level - noiseEnv_.state);
        const float noise = std::max(noiseEnv_.state, 1e-6f);
        const float sig = std::max(level, 1e-6f);
        const float minGain = 1.0f - amount01 * 0.85f;             // depth: up to -16 dB of background
        float g = clampf(1.0f - (noise * noise) / (sig * sig), minGain, 1.0f);
        if (amount01 <= 0.001f) g = 1.0f;
        return gainSmooth_.process(g);
    }

    void reset() { env_.reset(); gainSmooth_.reset(); noiseEnv_.state = 1e-5f; }

private:
    OnePole env_, gainSmooth_, noiseEnv_;
};
