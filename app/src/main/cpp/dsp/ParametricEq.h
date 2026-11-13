#pragma once
#include "Biquad.h"
#include <cmath>

// Stage 7a — Haramain clarity EQ. A fixed musical voicing (the preset
// character) PLUS user low/high shelf trims ("boost the lows / highs"):
//   low shelf 200 Hz:  -1.5 dB base + user trim (-12..+12)
//   presence peak 3.4 kHz: +3.5 dB — the Masjid al-Haram articulation band
//   high shelf 9 kHz:  +1.0 dB base + user trim (-12..+12)
class ParametricEq {
public:
    void prepare(double sampleRate) {
        sampleRate_ = sampleRate;
        lastLowUser_ = 999.0f; lastHighUser_ = 999.0f;   // force coefficient build
        setUserGains(0.0f, 0.0f);
        for (int ch = 0; ch < 2; ++ch) {
            presence_[ch].set(Biquad::Type::Peaking, sampleRate, 3400.0, 0.9, 3.5);
        }
    }

    // Called once per frame from the chain; rebuilds shelf coefficients only
    // when a trim actually moved (>0.01 dB), so it's free in steady state.
    inline void setUserGains(float lowUserDb, float highUserDb) {
        if (std::fabs(lowUserDb - lastLowUser_) < 0.01f &&
            std::fabs(highUserDb - lastHighUser_) < 0.01f) return;
        lastLowUser_ = lowUserDb; lastHighUser_ = highUserDb;
        for (int ch = 0; ch < 2; ++ch) {
            low_[ch].set(Biquad::Type::LowShelf, sampleRate_, 200.0, 0.7, -1.5 + lowUserDb);
            air_[ch].set(Biquad::Type::HighShelf, sampleRate_, 9000.0, 0.7, 1.0 + highUserDb);
        }
    }

    inline float process(float x, int ch) {
        return air_[ch].process(presence_[ch].process(low_[ch].process(x)));
    }
    void reset() { for (int ch = 0; ch < 2; ++ch) { low_[ch].reset(); presence_[ch].reset(); air_[ch].reset(); } }

private:
    Biquad low_[2], presence_[2], air_[2];
    double sampleRate_ = 48000;
    float lastLowUser_ = 999.0f, lastHighUser_ = 999.0f;
};
