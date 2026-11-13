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
        floor_ = 1e-4f;
        blockMin_ = 1e9f;
        blockCount_ = 0;
        blockLen_ = std::max(1, static_cast<int>(sampleRate / 4));   // 0.25 s blocks
    }

    // Returns the suppression gain to apply this sample.
    inline float processGain(float detectorInput, float amount01) {
        const float level = env_.process(detectorInput);
        // Minimum-statistics floor (v2.2, second fix): the floor is the
        // smoothed minimum of the envelope over 0.25 s blocks. Hiss blocks
        // pin it to the noise level within ~1 s; speech blocks contain
        // micro-gaps, so their block minima stay at the noise too and the
        // floor never chases the voice (the v1 "radio quality" collapse).
        //
        // Two earlier laws failed in the field, both verified on host:
        // (1) rise-only-within-1.6x from a 1e-5 seed: real hiss is 100x+
        //     the seed, so the floor stuck at ~0 and gain never moved.
        // (2) fall-fast-to-minimum from a 1.0 seed: the envelope's own
        //     attack transient (first samples ~0) poisoned the floor to
        //     ~2e-5 before the hiss even arrived — same stuck-at-zero end.
        if (level < blockMin_) blockMin_ = level;
        if (++blockCount_ >= blockLen_) {
            blockCount_ = 0;
            floor_ += 0.3f * (blockMin_ - floor_);
            blockMin_ = level;
        }
        const float noise = std::max(floor_, 1e-6f);
        const float sig = std::max(level, 1e-6f);
        const float minGain = 1.0f - amount01 * 0.85f;             // depth: up to -16 dB of background
        float g = clampf(1.0f - (noise * noise) / (sig * sig), minGain, 1.0f);
        if (amount01 <= 0.001f) g = 1.0f;
        return gainSmooth_.process(g);
    }

    void reset() {
        env_.reset(); gainSmooth_.reset();
        floor_ = 1e-4f; blockMin_ = 1e9f; blockCount_ = 0;
    }

private:
    OnePole env_, gainSmooth_;
    float floor_ = 1e-4f;
    float blockMin_ = 1e9f;
    int blockCount_ = 0;
    int blockLen_ = 12000;
};
