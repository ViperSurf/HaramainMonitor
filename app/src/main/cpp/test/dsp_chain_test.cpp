// Host-side sanity test for the Haramain DSP chain (no Android/Oboe needed).
#include "../dsp/DspChain.h"
#include <cstdio>
#include <cstdlib>
#include <cmath>
#include <random>

static int failures = 0;
static void check(const char *name, bool ok, double detail) {
    std::printf("[%s] %-46s detail=%.5f\n", ok ? "PASS" : "FAIL", name, detail);
    if (!ok) ++failures;
}

int main() {
    const double SR = 48000.0;
    DspParams haramain;  // defaults = Haramain Cathedral preset

    // 1) Silence in -> (near) silence out, no NaNs, gate stays shut.
    {
        DspChain chain; chain.prepare(SR);
        double maxOut = 0; bool finite = true;
        for (int i = 0; i < 48000; ++i) {
            float l = 0, r = 0; chain.processFrame(l, r, haramain);
            if (!std::isfinite(l) || !std::isfinite(r)) finite = false;
            maxOut = std::max(maxOut, (double)std::max(std::fabs(l), std::fabs(r)));
        }
        check("silence stays silent & finite", finite && maxOut < 1e-4, maxOut);
    }

    // 2) Voice-like 220 Hz sine at -12 dBFS -> finite, under -3 dBFS safety ceiling (0.7079).
    {
        DspChain chain; chain.prepare(SR);
        double maxOut = 0; bool finite = true;
        for (int i = 0; i < 96000; ++i) {
            float x = 0.25f * (float)std::sin(2 * M_PI * 220.0 * i / SR);
            float l = x, r = x; chain.processFrame(l, r, haramain);
            if (!std::isfinite(l) || !std::isfinite(r)) finite = false;
            if (i > 4800) maxOut = std::max(maxOut, (double)std::max(std::fabs(l), std::fabs(r)));
        }
        check("sine finite & under safety ceiling 0.708", finite && maxOut <= 0.709, maxOut);
        check("sine actually passes signal (>0.05)", maxOut > 0.05, maxOut);
    }

    // 3) Full-scale blast + boost -> brickwall still holds at -3 dBFS.
    {
        DspChain chain; chain.prepare(SR);
        DspParams p = haramain; p.micBoostPercent = 200.0f;
        double maxOut = 0;
        for (int i = 0; i < 96000; ++i) {
            float x = 0.99f * (float)std::sin(2 * M_PI * 220.0 * i / SR);
            float l = x, r = x; chain.processFrame(l, r, p);
            if (i > 4800) maxOut = std::max(maxOut, (double)std::max(std::fabs(l), std::fabs(r)));
        }
        check("0 dBFS + 200% boost limited to ceiling", maxOut <= 0.709, maxOut);
    }

    // 4) Safety OFF -> ceiling becomes -0.5 dBFS (0.9441).
    {
        DspChain chain; chain.prepare(SR);
        // Isolate the limiter: no compressor, no reverb, boost slams the saturator.
        DspParams p = haramain; p.safetyOn = false; p.micBoostPercent = 200.0f;
        p.compressorPercent = 0.0f; p.wetDryPercent = 0.0f;
        double maxOut = 0;
        for (int i = 0; i < 96000; ++i) {
            float x = 0.99f * (float)std::sin(2 * M_PI * 220.0 * i / SR);
            float l = x, r = x; chain.processFrame(l, r, p);
            if (i > 4800) maxOut = std::max(maxOut, (double)std::max(std::fabs(l), std::fabs(r)));
        }
        check("safety off: ceiling -0.5 dBFS (<=0.945)", maxOut <= 0.946 && maxOut > 0.71, maxOut);
    }

    // 5) Hard-left balance -> right channel silent.
    {
        DspChain chain; chain.prepare(SR);
        DspParams p = haramain; p.balancePercent = -100.0f; p.wetDryPercent = 0.0f;
        double maxR = 0, maxL = 0;
        for (int i = 0; i < 96000; ++i) {
            float x = 0.25f * (float)std::sin(2 * M_PI * 220.0 * i / SR);
            float l = x, r = x; chain.processFrame(l, r, p);
            if (i > 48000) { maxR = std::max(maxR, (double)std::fabs(r)); maxL = std::max(maxL, (double)std::fabs(l)); }
        }
        check("balance L100: right silent, left audible", maxR < 1e-3 && maxL > 0.05, maxR);
    }

    // 6a) Typical phone-mic hiss floor (-58 dB RMS) with the default preset:
    //     the gate (threshold -45) must crush it.
    {
        std::mt19937 rng(42); std::normal_distribution<float> gauss(0.0f, 0.00126f);
        DspChain chain; chain.prepare(SR);
        double sumSq = 0; int n = 0;
        for (int i = 0; i < 96000; ++i) {
            float x = gauss(rng);
            float l = x, r = x; chain.processFrame(l, r, haramain);
            if (i > 48000) { sumSq += (double)l * l; ++n; }
        }
        double rmsOutDb = 10.0 * std::log10(sumSq / n + 1e-12);
        check("hiss -58 dB in -> below -68 dB out", rmsOutDb < -68.0, rmsOutDb);
    }

    // 6b) Loud hiss (-50 dB RMS, cheap USB mic) needs the gate raised to -38 dB —
    //     exactly what the user's threshold control is for. Then it crushes too.
    {
        std::mt19937 rng(42); std::normal_distribution<float> gauss(0.0f, 0.0032f);
        DspParams p = haramain; p.gateThresholdDb = -38.0f;
        DspChain chain; chain.prepare(SR);
        double sumSq = 0; int n = 0;
        for (int i = 0; i < 96000; ++i) {
            float x = gauss(rng);
            float l = x, r = x; chain.processFrame(l, r, p);
            if (i > 48000) { sumSq += (double)l * l; ++n; }
        }
        double rmsOutDb = 10.0 * std::log10(sumSq / n + 1e-12);
        check("loud hiss + gate -38 dB -> below -62 dB out", rmsOutDb < -62.0, rmsOutDb);
    }

    // 7) Mute -> exact zero.
    {
        DspChain chain; chain.prepare(SR);
        DspParams p = haramain; p.muted = true;
        float l = 0.5f, r = 0.5f; chain.processFrame(l, r, p);
        check("muted = exact zero", l == 0.0f && r == 0.0f, l);
    }

    std::printf(failures == 0 ? "\nALL DSP TESTS PASSED\n" : "\n%d FAILURES\n", failures);
    return failures == 0 ? 0 : 1;
}
