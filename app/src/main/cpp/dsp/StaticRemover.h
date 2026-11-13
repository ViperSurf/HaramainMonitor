#pragma once
#include "Biquad.h"
#include <cmath>

// Stage 2 — Static Remover.
//  a) Steep 85 Hz high-pass (two cascaded biquads = 24 dB/oct) kills rumble,
//     AC hum fundamentals and handling noise.
//  b) Hiss reducer: constant analog hiss lives mostly above ~6 kHz and is most
//     audible in silence. We split off the HF band, track the broadband signal
//     envelope, and attenuate ONLY the HF band, ONLY when the voice is quiet —
//     a time-domain approximation of spectral gating that never dulls loud,
//     clear speech. `amount` (0..100) sets the max HF cut (0..-18 dB).
class StaticRemover {
public:
    void prepare(double sampleRate) {
        for (int ch = 0; ch < 2; ++ch) {
            hp1_[ch].set(Biquad::Type::HighPass, sampleRate, 85.0, 0.7071);
            hp2_[ch].set(Biquad::Type::HighPass, sampleRate, 85.0, 0.7071);
            hfSplit_[ch].set(Biquad::Type::HighPass, sampleRate, 6000.0, 0.5);
        }
        env_.setTimeConstant(sampleRate, 25.0);
    }

    inline float process(float x, int ch, float amount01) {
        float y = hp2_[ch].process(hp1_[ch].process(x));
        if (amount01 <= 0.001f) return y;
        const float hf = hfSplit_[ch].process(y);
        const float level = env_.process(std::fabs(y));       // shared envelope (ch0 drives, fine for mono-ish voice)
        // When the signal is near the hiss floor (< -45 dBFS), cut HF hardest.
        const float quiet = clampf((linearToDb(level) + 45.0f) / 30.0f, 0.0f, 1.0f); // 0 = silent, 1 = speaking
        const float hfGain = 1.0f - amount01 * (1.0f - quiet) * 0.87f;                  // up to ~-18 dB in silence
        return y - hf + hf * hfGain;
    }

    void reset() {
        for (int ch = 0; ch < 2; ++ch) { hp1_[ch].reset(); hp2_[ch].reset(); hfSplit_[ch].reset(); }
        env_.reset();
    }

private:
    Biquad hp1_[2], hp2_[2], hfSplit_[2];
    OnePole env_;
};
