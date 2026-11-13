#pragma once
#include <cmath>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

// RBJ-cookbook biquad, Direct Form II Transposed — cache friendly, stable,
// and cheap enough to run several per sample in the real-time callback.
class Biquad {
public:
    enum class Type { LowPass, HighPass, Peaking, LowShelf, HighShelf, BandPass };

    void set(Type type, double sampleRate, double freq, double q, double gainDb = 0.0) {
        const double w0 = 2.0 * M_PI * freq / sampleRate;
        const double alpha = std::sin(w0) / (2.0 * q);
        const double cosw = std::cos(w0);
        const double A = std::pow(10.0, gainDb / 40.0);
        double b0, b1, b2, a0, a1, a2;
        switch (type) {
            case Type::LowPass:
                b0 = (1 - cosw) / 2; b1 = 1 - cosw; b2 = (1 - cosw) / 2;
                a0 = 1 + alpha; a1 = -2 * cosw; a2 = 1 - alpha; break;
            case Type::HighPass:
                b0 = (1 + cosw) / 2; b1 = -(1 + cosw); b2 = (1 + cosw) / 2;
                a0 = 1 + alpha; a1 = -2 * cosw; a2 = 1 - alpha; break;
            case Type::Peaking:
                b0 = 1 + alpha * A; b1 = -2 * cosw; b2 = 1 - alpha * A;
                a0 = 1 + alpha / A; a1 = -2 * cosw; a2 = 1 - alpha / A; break;
            case Type::LowShelf: {
                const double s = 2 * std::sqrt(A) * alpha;
                b0 = A * ((A + 1) - (A - 1) * cosw + s);
                b1 = 2 * A * ((A - 1) - (A + 1) * cosw);
                b2 = A * ((A + 1) - (A - 1) * cosw - s);
                a0 = (A + 1) + (A - 1) * cosw + s;
                a1 = -2 * ((A - 1) + (A + 1) * cosw);
                a2 = (A + 1) + (A - 1) * cosw - s; break;
            }
            case Type::HighShelf: {
                const double s = 2 * std::sqrt(A) * alpha;
                b0 = A * ((A + 1) + (A - 1) * cosw + s);
                b1 = -2 * A * ((A - 1) + (A + 1) * cosw);
                b2 = A * ((A + 1) + (A - 1) * cosw - s);
                a0 = (A + 1) - (A - 1) * cosw + s;
                a1 = 2 * ((A - 1) - (A + 1) * cosw);
                a2 = (A + 1) - (A - 1) * cosw - s; break;
            }
            case Type::BandPass: // constant 0 dB peak gain
                b0 = alpha; b1 = 0; b2 = -alpha;
                a0 = 1 + alpha; a1 = -2 * cosw; a2 = 1 - alpha; break;
        }
        b0_ = b0 / a0; b1_ = b1 / a0; b2_ = b2 / a0; a1_ = a1 / a0; a2_ = a2 / a0;
    }

    inline float process(float x) {
        const double out = b0_ * x + s1_;
        s1_ = b1_ * x - a1_ * out + s2_;
        s2_ = b2_ * x - a2_ * out;
        return static_cast<float>(out);
    }

    void reset() { s1_ = s2_ = 0.0; }

private:
    double b0_ = 1, b1_ = 0, b2_ = 0, a1_ = 0, a2_ = 0;
    double s1_ = 0, s2_ = 0;
};

// One-pole smoother used for envelopes and coefficient-free low/high passes.
struct OnePole {
    float coeff = 0.0f;   // 0..1, higher = faster
    float state = 0.0f;
    void setTimeConstant(double sampleRate, double ms) {
        coeff = static_cast<float>(1.0 - std::exp(-1.0 / (sampleRate * ms / 1000.0)));
    }
    inline float process(float x) { state += coeff * (x - state); return state; }
    void reset() { state = 0.0f; }
};

inline float dbToLinear(float db) { return std::pow(10.0f, db / 20.0f); }
inline float linearToDb(float lin) { return 20.0f * std::log10(std::max(lin, 1e-7f)); }
inline float clampf(float v, float lo, float hi) { return v < lo ? lo : (v > hi ? hi : v); }
