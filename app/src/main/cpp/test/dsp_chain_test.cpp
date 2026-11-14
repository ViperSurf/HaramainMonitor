// Host-side sanity test for the Haramain DSP chain (no Android/Oboe needed).
#include "../dsp/DspChain.h"
#include "../dsp/NoiseGate.h"
#include "../dsp/NoiseSuppressor.h"
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
    DspParams haramain;  // defaults = the user-specified (Original) preset

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

    // 5b) EQ user shelves: +12 dB bass trim must lift a 120 Hz tone through
    //     the full chain, relative to the flat trim.
    {
        auto runTone = [&](float eqLow) {
            DspChain chain; chain.prepare(SR);
            DspParams p = haramain;
            p.wetDryPercent = 0.0f; p.noiseSuppressOn = false;
            p.gateThresholdDb = -80.0f; p.safetyOn = false; p.eqLowGainDb = eqLow;
            double sumSq = 0; int n = 0;
            for (int i = 0; i < 48000; ++i) {
                float x = 0.1f * (float)std::sin(2 * M_PI * 120.0 * i / SR);
                float l = x, r = x; chain.processFrame(l, r, p);
                if (i > 24000) { sumSq += (double)l * l; ++n; }
            }
            return std::sqrt(sumSq / n);
        };
        const double rmsBoost = runTone(12.0f);
        const double rmsFlat = runTone(0.0f);
        const double liftDb = 20.0 * std::log10(rmsBoost / std::max(1e-9, rmsFlat));
        check("bass shelf +12 dB lifts 120 Hz by >6 dB", liftDb > 6.0, liftDb);
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

    // 8) REGRESSION (user report: intermittent thin "radio quality" on long
    //    phrases): the suppressor's noise floor must FREEZE during sustained
    //    speech, not chase the voice. Constant 0.2 level for 3 s at 40% depth
    //    must keep the gain near unity (old code sank toward ~0.66).
    {
        // v2.2 contract (the constant-level version encoded the old inert
        // law): steady hiss must be suppressed; modulated "speech" bursts
        // over the same hiss must pass untouched.
        NoiseSuppressor ns; ns.prepare(SR);
        float g = 1.0f;
        for (int i = 0; i < 144000; ++i) g = ns.processGain(0.02f, 0.4f);   // 3 s hiss
        check("suppressor engages on steady hiss", g < 0.8f, g);
        float gBurst = 0.0f;
        for (int cyc = 0; cyc < 6; ++cyc) {
            for (int i = 0; i < 14400; ++i) g = ns.processGain(0.3f, 0.4f);   // 300 ms burst
            gBurst = g;
            for (int i = 0; i < 9600; ++i) g = ns.processGain(0.02f, 0.4f);   // 200 ms gap
        }
        check("suppressor leaves speech bursts untouched", gBurst > 0.9f, gBurst);
    }

    // 9) REGRESSION (user report: far-mic gate chatter "weird noise"):
    //    an 80 ms dip mid-phrase must NOT close the gate (150 ms hold),
    //    but a 400 ms silence must.
    {
        NoiseGate gate; gate.prepare(SR);
        float g = 0;
        for (int i = 0; i < 24000; ++i) g = gate.processGain(0.1f, -45.0f);   // voice: open
        for (int i = 0; i < 3840; ++i) g = gate.processGain(0.0005f, -45.0f); // 80 ms dip
        check("gate holds open through 80 ms dip", g > 0.5f, g);
        for (int i = 0; i < 38400; ++i) g = gate.processGain(0.0005f, -45.0f); // +800 ms silence (env decay + hold + ramp)
        check("gate closes after hold expires", g < 0.2f, g);
    }

    // 7) Mute -> exact zero.
    {
        DspChain chain; chain.prepare(SR);
        DspParams p = haramain; p.muted = true;
        float l = 0.5f, r = 0.5f; chain.processFrame(l, r, p);
        check("muted = exact zero", l == 0.0f && r == 0.0f, l);
    }


    // 14) BT dry-duck: with the Masjid space up, ducking cuts the direct
    //     voice hard but the signal (wet room) remains clearly audible.
    {
        auto steady = [&](bool duck) {
            DspChain chain; chain.prepare(SR);
            DspParams p = haramain;
            p.btDuckOn = duck; p.btDuckPercent = 100.0f;
            double sum = 0; int n = 0;
            for (int i = 0; i < 96000; ++i) {           // 2 s of 300 Hz voice
                float x = 0.15f * std::sin(2.0 * M_PI * 300.0 * i / SR);
                float l = x, r = x; chain.processFrame(l, r, p);
                if (i > 72000) { sum += std::fabs(l); ++n; }   // final 0.5 s
            }
            return sum / n;
        };
        const double dry = steady(false), ducked = steady(true);
        check("BT duck lowers monitor level", ducked < dry * 0.75, ducked / dry);
        check("BT duck keeps the room audible", ducked > dry * 0.15, ducked / dry);
    }

    // 15) Tone sculptor: Warmth +12 dB lifts a 150 Hz tone substantially.
    {
        auto level = [&](float warmth) {
            DspChain chain; chain.prepare(SR);
            DspParams p = haramain;
            p.wetDryPercent = 0.0f; p.roomSizePercent = 0.0f; p.eqWarmthDb = warmth;
            double sum = 0; int n = 0;
            for (int i = 0; i < 48000; ++i) {
                float x = 0.1f * std::sin(2.0 * M_PI * 150.0 * i / SR);
                float l = x, r = x; chain.processFrame(l, r, p);
                if (i > 24000) { sum += std::fabs(l); ++n; }
            }
            return sum / n;
        };
        const double flat = level(0.0f), warm = level(12.0f);
        check("warmth +12 lifts 150 Hz", warm > flat * 1.7, warm / flat);
    }

    // 16) Activity telemetry: a hot, boosted signal must show limiter gain < 1.
    {
        DspChain chain; chain.prepare(SR);
        DspParams p = haramain; p.micBoostPercent = 200.0f; p.wetDryPercent = 0.0f; p.compressorPercent = 0.0f;
        for (int i = 0; i < 4800; ++i) {
            float x = 0.9f * std::sin(2.0 * M_PI * 300.0 * i / SR);
            float l = x, r = x; chain.processFrame(l, r, p);
        }
        check("limiter activity observable", chain.lastLimiterGain() < 0.99f, chain.lastLimiterGain());
        check("gate open for loud voice", chain.lastGateGain() > 0.9f, chain.lastGateGain());
    }

    {
        DspChain c2; c2.prepare(SR);
        DspParams bp;                       // loud processing that bypass must ignore
        bp.micBoostPercent = 200; bp.roomSizePercent = 100; bp.wetDryPercent = 100;
        bp.bypass = true;
        bool ok = true;
        for (int i = 0; i < 4800; ++i) {
            float l = 0.3f, r = 0.3f; c2.processFrame(l, r, bp);
            if (std::fabs(l - 0.3f) > 1e-6f || std::fabs(r - 0.3f) > 1e-6f) ok = false;
        }
        check("A/B bypass passes the raw mic untouched", ok, 0.3f);
        float l2 = 1.2f, r2 = 1.2f; c2.processFrame(l2, r2, bp);
        check("A/B bypass still brickwalls at -0.5 dB", l2 <= 0.944f && l2 > 0.9f, l2);
    }

    {
        // v2.9: graphic EQ band actually boosts its band, not its neighbors.
        auto bandRms = [&](double freq, float g1k) {
            GraphicEq g; g.prepare(SR); g.setGains(0, 0, g1k, 0, 0);
            double acc = 0; int n = 0;
            for (int i = 0; i < 48000; ++i) {
                float x = 0.3f * std::sin(2 * M_PI * freq * i / SR);
                float y = g.process(x, 0);
                if (i > 24000) { acc += double(y) * y; ++n; }
            }
            return std::sqrt(acc / n);
        };
        double boost1k = bandRms(1000, 12) / bandRms(1000, 0);
        double leak100 = bandRms(100, 12) / bandRms(100, 0);
        check("graphic EQ +12 dB at 1 kHz boosts ~4x", boost1k > 3.2 && boost1k < 4.8, boost1k);
        check("graphic EQ 1 kHz band leaves 100 Hz alone", leak100 > 0.8 && leak100 < 1.25, leak100);
    }
    {
        // v2.9: reverb damping darkens/quiets the tail; width 0 = mono wet.
        auto tailRms = [&](float damp01) {
            Freeverb fv; fv.prepare(SR); fv.setDampingWidth(damp01, 1.0f);
            unsigned seed = 777;
            auto nz = [&]() { seed = seed * 1103515245 + 12345; return ((seed >> 16) & 0x7fff) / 16384.0f - 1.0f; };
            double acc = 0; int n = 0;
            for (int i = 0; i < 48000 * 3; ++i) {
                float x = i < 12000 ? nz() * 0.4f : 0.0f;
                float l, r; fv.process(x, x, 0.8f, 1.0f, l, r);
                if (i >= 48000 * 5 / 2) { acc += double(l) * l; ++n; }
            }
            return std::sqrt(acc / n);
        };
        double bright = tailRms(0.0f), dark = tailRms(1.0f);
        check("reverb damping shrinks the late tail", dark < bright * 0.85, dark / bright);
        Freeverb fv; fv.prepare(SR); fv.setDampingWidth(0.5f, 0.0f);
        bool mono = true;
        for (int i = 0; i < 4800; ++i) {
            float x = (i == 0) ? 0.8f : 0.0f;
            float l, r; fv.process(x, x, 0.8f, 0.8f, l, r);
            if (l != r) mono = false;
        }
        check("reverb width 0 makes the wet field mono", mono, 0);
    }

    std::printf(failures == 0 ? "\nALL DSP TESTS PASSED\n" : "\n%d FAILURES\n", failures);
    return failures == 0 ? 0 : 1;
}
