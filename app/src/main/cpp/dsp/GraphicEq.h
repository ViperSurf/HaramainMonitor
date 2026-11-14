#pragma once
#include "Biquad.h"
#include <cmath>

// Stage 7a2 — 5-band graphic EQ (60 / 250 / 1k / 4k / 12k, +-12 dB,
// peaking, Q ~ 1.4 ≈ one octave). User tone shaping AFTER the fixed
// voicing EQ and BEFORE the space, so the reverb receives the shaped
// voice. Coefficients rebuild only when a gain actually changes.
class GraphicEq {
public:
    void prepare(double sampleRate) {
        sampleRate_ = sampleRate;
        for (int b = 0; b < 5; ++b) lastGains_[b] = 999.0f;   // force first build
        setGains(0, 0, 0, 0, 0);
    }

    inline void setGains(float g60, float g250, float g1k, float g4k, float g12k) {
        const float g[5] = {g60, g250, g1k, g4k, g12k};
        bool changed = false;
        for (int b = 0; b < 5; ++b) {
            if (std::fabs(g[b] - lastGains_[b]) > 0.01f) { changed = true; break; }
        }
        if (!changed) return;
        static const double freqs[5] = {60.0, 250.0, 1000.0, 4000.0, 12000.0};
        for (int b = 0; b < 5; ++b) {
            lastGains_[b] = g[b];
            for (int ch = 0; ch < 2; ++ch) {
                bands_[ch][b].set(Biquad::Type::Peaking, sampleRate_, freqs[b], 1.41, g[b]);
            }
        }
    }

    inline float process(float x, int ch) {
        for (int b = 0; b < 5; ++b) x = bands_[ch][b].process(x);
        return x;
    }

    void reset() {
        for (int ch = 0; ch < 2; ++ch)
            for (int b = 0; b < 5; ++b) bands_[ch][b].reset();
    }

private:
    double sampleRate_ = 48000;
    Biquad bands_[2][5];
    float lastGains_[5] = {999, 999, 999, 999, 999};
};
