#pragma once
#include "DspParams.h"
#include "Biquad.h"
#include "StaticRemover.h"
#include "NoiseGate.h"
#include "NoiseSuppressor.h"
#include "DeEsser.h"
#include "Compressor.h"
#include "ParametricEq.h"
#include "GraphicEq.h"
#include "Freeverb.h"
#include <cmath>

// The full real-time chain, in the exact signal order from the spec:
//   1 Mic Boost (soft-knee) -> 2 Static Remover (85 Hz HPF + hiss) ->
//   3 Noise Gate -> 4 Noise Suppression -> 5 De-Esser -> 6 Compressor ->
//   7 Haramain EQ + Reverb -> 8 Stereo Balance ->
//   9 Headphone-Safety / Brickwall Limiter
// Allocation-free after prepare(); safe to run in the Oboe callback.
class DspChain {
public:
    void prepare(double sampleRate) {
        sampleRate_ = sampleRate;
        staticRemover_.prepare(sampleRate);
        gate_.prepare(sampleRate);
        suppressor_.prepare(sampleRate);
        deEsser_.prepare(sampleRate);
        compressor_.prepare(sampleRate);
        eq_.prepare(sampleRate);
        geq_.prepare(sampleRate);
        reverb_.prepare(sampleRate);
        peakEnv_.setTimeConstant(sampleRate, 2.0);
        relEnv_.setTimeConstant(sampleRate, 120.0);
    }

    // Process one stereo frame. Input is typically dual-mono mic (L==R).
    inline void processFrame(float &l, float &r, const DspParams &p) {
        if (p.muted) { l = 0; r = 0; return; }
        if (p.bypass) {
            // A/B compare: the raw mic, untouched except a -0.5 dB
            // brickwall so a sudden shout cannot clip on the way out.
            // Meters and activity readouts stay truthful.
            lastInPeak_ = std::max(std::fabs(l), std::fabs(r));
            l = clampf(l, -0.944f, 0.944f);
            r = clampf(r, -0.944f, 0.944f);
            lastOutPeak_ = std::max(std::fabs(l), std::fabs(r));
            lastGateGain_ = 1.0f; lastNsGain_ = 1.0f; limiterGain_ = 1.0f;
            return;
        }

        // --- 1. Mic Boost: 0..200% -> 0..+24 dB, soft-knee saturation ---
        const float boostDb = (p.micBoostPercent / 100.0f) * 12.0f;
        const float boost = dbToLinear(boostDb);
        l = softSaturate(l * boost);
        r = softSaturate(r * boost);

        // Linked detector for all dynamics stages below.
        float detector = std::max(std::fabs(l), std::fabs(r));

        // --- 2. Static Remover ---
        const float staticAmt = p.staticRemover / 100.0f;
        l = staticRemover_.process(l, 0, staticAmt);
        r = staticRemover_.process(r, 1, staticAmt);
        detector = std::max(std::fabs(l), std::fabs(r));

        // --- 3. Noise Gate ---
        const float gateGain = gate_.processGain(detector, p.gateThresholdDb);
        l *= gateGain; r *= gateGain;
        lastGateGain_ = gateGain;

        // --- 4. Noise Suppression (voice isolation) ---
        lastNsGain_ = 1.0f;
        if (p.noiseSuppressOn && p.noiseSuppress > 0.01f) {
            const float nsGain = suppressor_.processGain(detector, p.noiseSuppress / 100.0f);
            l *= nsGain; r *= nsGain;
            lastNsGain_ = nsGain;
        }

        // --- 5. De-Esser ---
        const float deAmt = p.deEsserPercent / 100.0f;
        l = deEsser_.process(l, 0, detector, deAmt);
        r = deEsser_.process(r, 1, detector, deAmt);

        // --- 6. Compressor ---
        const float compGain = compressor_.processGain(detector, p.compressorPercent / 100.0f);
        l *= compGain; r *= compGain;

        // --- 7. Haramain EQ + Reverb ---
        eq_.setUserGains(p.eqLowGainDb, p.eqHighGainDb);
        eq_.setTone(p.eqWarmthDb, p.eqClarityDb, p.eqAirDb);
        l = eq_.process(l, 0);
        r = eq_.process(r, 1);
        geq_.setGains(p.eqBand60Db, p.eqBand250Db, p.eqBand1kDb, p.eqBand4kDb, p.eqBand12kDb);
        l = geq_.process(l, 0);
        r = geq_.process(r, 1);
        reverb_.setDampingWidth(p.revDampingPercent / 100.0f, p.revWidthPercent / 100.0f);
        // Bluetooth dry-ducking: on wireless, the direct voice arrives a
        // codec-latency late and fights the room sound as a slapback echo.
        // Duck the DRY path only (to 25% at full ducking); the wet Masjid
        // space stays lush — distance without the doubling.
        const float dryScale = p.btDuckOn
            ? 1.0f - 0.75f * clampf(p.btDuckPercent / 100.0f, 0.0f, 1.0f)
            : 1.0f;
        reverb_.process(l, r, p.roomSizePercent / 100.0f, p.wetDryPercent / 100.0f, l, r, dryScale);

        // Master trim
        const float master = dbToLinear(p.masterGainDb);
        l *= master; r *= master;

        // --- 8. Stereo Balance (constant-power pan) ---
        const float pan = clampf(p.balancePercent / 100.0f, -1.0f, 1.0f);
        const float angle = (pan + 1.0f) * 0.78539816339f; // (pan+1) * pi/4
        const float panL = std::cos(angle) * 1.41421356f * 0.70710678f; // normalized constant power
        const float panR = std::sin(angle) * 1.41421356f * 0.70710678f;
        // For dual-mono voice, pan by re-weighting the (identical) channels:
        const float mono = (l + r) * 0.5f;
        l = mono * panL * 1.41421356f;
        r = mono * panR * 1.41421356f;

        // --- 8b. Anti-howl notches (frequencies set by the guard thread) ---
        for (int s = 0; s < 4; ++s) {
            const float f = p.notchFreq[s];
            if (f < 1.0f) continue;
            if (std::fabs(f - notchFreq_[s]) > 1.0f) {
                notchFreq_[s] = f;
                for (int ch = 0; ch < 2; ++ch)
                    notch_[ch][s].set(Biquad::Type::Peaking, sampleRate_, f, 24.0, -15.0);
            }
            l = notch_[0][s].process(l);
            r = notch_[1][s].process(r);
        }

        // --- 9. Safety ceiling + Brickwall limiter ---
        float ceilingDb = -0.5f;                                // hard brickwall, safety off
        if (p.safetyOn) ceilingDb = std::min(ceilingDb, p.safetyCeilingDb);   // default -3 dBFS
        if (p.feedbackRisk) ceilingDb = std::min(ceilingDb, -12.0f);          // speaker + internal mic: hard cap
        limit(l, r, dbToLinear(ceilingDb));

        // Meters (read by JNI via atomics in AudioEngine — here via refs)
        lastInPeak_ = detector;
        lastOutPeak_ = std::max(std::fabs(l), std::fabs(r));
    }

    float lastInPeak() const { return lastInPeak_; }
    float lastOutPeak() const { return lastOutPeak_; }
    // Stage gains of the most recent frame (1.0 = untouched) — the service
    // surfaces their block minima as "processing activity" so the user can
    // SEE the gate / suppressor / limiter working.
    float lastGateGain() const { return lastGateGain_; }
    float lastNsGain() const { return lastNsGain_; }
    float lastLimiterGain() const { return limiterGain_; }
    float lastCompGrDb() const { return compressor_.lastGrDb(); }
    float lastDeEssGain() const { return deEsser_.lastGain(); }

    void reset() {
        staticRemover_.reset(); gate_.reset(); suppressor_.reset();
        deEsser_.reset(); compressor_.reset(); eq_.reset(); geq_.reset(); reverb_.reset();
        for (int ch = 0; ch < 2; ++ch)
            for (int s = 0; s < 4; ++s) notch_[ch][s].reset();
        limiterGain_ = 1.0f;
    }

private:
    static inline float softSaturate(float x) {
        // tanh soft knee: transparent below ~-6 dBFS, gently rounds peaks so
        // boosted quiet mics never produce harsh square clipping.
        return std::tanh(x * 0.85f) * 1.176f;
    }

    inline void limit(float &l, float &r, float ceiling) {
        const float peak = std::max(std::fabs(l), std::fabs(r));
        float target = 1.0f;
        if (peak > ceiling) target = ceiling / peak;
        // Instant attack, smooth release — a true brickwall for peaks.
        if (target < limiterGain_) limiterGain_ = target;
        else limiterGain_ += relEnv_.coeff * (target - limiterGain_);
        if (limiterGain_ > 1.0f) limiterGain_ = 1.0f;
        l *= limiterGain_; r *= limiterGain_;
    }

    double sampleRate_ = 48000;
    StaticRemover staticRemover_;
    NoiseGate gate_;
    NoiseSuppressor suppressor_;
    DeEsser deEsser_;
    Compressor compressor_;
    ParametricEq eq_;
    GraphicEq geq_;
    Biquad notch_[2][4];
    float notchFreq_[4] = {0, 0, 0, 0};
    Freeverb reverb_;
    OnePole peakEnv_, relEnv_;
    float limiterGain_ = 1.0f;
    float lastInPeak_ = 0, lastOutPeak_ = 0;
    float lastGateGain_ = 1.0f, lastNsGain_ = 1.0f;
};
