#pragma once
#include "Biquad.h"

// Stage 7a — Haramain clarity EQ (fixed musical voicing, part of the preset
// character rather than a user slider):
//   low shelf 200 Hz, -1.5 dB  — keeps chest resonance from muddying the reverb
//   presence peak 3.4 kHz, +3.5 dB — the Masjid al-Haram articulation band
//   high shelf 9 kHz, +1.0 dB — air, kept gentle so the de-esser's job stays easy
class ParametricEq {
public:
    void prepare(double sampleRate) {
        for (int ch = 0; ch < 2; ++ch) {
            low_[ch].set(Biquad::Type::LowShelf, sampleRate, 200.0, 0.7, -1.5);
            presence_[ch].set(Biquad::Type::Peaking, sampleRate, 3400.0, 0.9, 3.5);
            air_[ch].set(Biquad::Type::HighShelf, sampleRate, 9000.0, 0.7, 1.0);
        }
    }
    inline float process(float x, int ch) {
        return air_[ch].process(presence_[ch].process(low_[ch].process(x)));
    }
    void reset() { for (int ch = 0; ch < 2; ++ch) { low_[ch].reset(); presence_[ch].reset(); air_[ch].reset(); } }

private:
    Biquad low_[2], presence_[2], air_[2];
};
