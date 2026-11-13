#pragma once
#include "Biquad.h"
#include <cmath>

// Stage 5 — De-esser. A 5–8 kHz band-pass sidechain watches for sibilance;
// when the band envelope exceeds a level relative to the broadband signal,
// only the HF portion of the signal is ducked (split-band design), so vowels
// stay full while "s"/"sh" spikes are tamed. amount 0..100 -> up to 10 dB cut.
class DeEsser {
public:
    void prepare(double sampleRate) {
        for (int ch = 0; ch < 2; ++ch) {
            band_[ch].set(Biquad::Type::BandPass, sampleRate, 6500.0, 0.6);
            hfSplit_[ch].set(Biquad::Type::HighPass, sampleRate, 5000.0, 0.5);
        }
        bandEnv_.setTimeConstant(sampleRate, 4.0);
        gainSmooth_.setTimeConstant(sampleRate, 6.0);
    }

    inline float process(float x, int ch, float broadbandLevel, float amount01) {
        if (amount01 <= 0.001f) return x;
        const float band = std::fabs(band_[ch].process(x));
        const float env = (ch == 0) ? bandEnv_.process(band) : bandEnv_.state;
        // Sibilance = band energy disproportionate to the whole signal.
        // Voiced conservatively (0.85 trigger ratio, ~-7 dB max HF cut): a
        // hotter trigger was ducking strong vowels on close mics and thinning
        // the voice intermittently.
        const float excess = env - broadbandLevel * 0.85f;
        float cut = 0.0f;
        if (excess > 0.0f) cut = clampf(excess * 4.0f, 0.0f, 1.0f) * amount01 * 0.55f;
        const float hf = hfSplit_[ch].process(x);
        const float g = (ch == 0) ? gainSmooth_.process(1.0f - cut) : gainSmooth_.state;
        return x - hf + hf * g;
    }

    void reset() {
        for (int ch = 0; ch < 2; ++ch) { band_[ch].reset(); hfSplit_[ch].reset(); }
        bandEnv_.reset(); gainSmooth_.reset();
    }

private:
    Biquad band_[2], hfSplit_[2];
    OnePole bandEnv_, gainSmooth_;
};
