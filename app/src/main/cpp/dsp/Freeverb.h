#pragma once
#include <cmath>
#include <vector>
#include <algorithm>

// Stage 7b — Cathedral reverb: Schroeder/Freeverb topology.
// 8 parallel feedback comb filters -> 4 series all-pass filters, per channel,
// with the classic Freeverb tunings scaled by roomSize. A short pre-delay
// (12–40 ms, grows with room size) keeps the dry voice upfront and intelligible
// while the tail blooms behind it — the Masjid al-Haram character.
// roomSize 0..1: feedback 0.70..0.96 (decay), comb scale 0.9..1.35.
class Freeverb {
    struct Comb {
        std::vector<float> buf; size_t idx = 0; float filterStore = 0;
        float damp1 = 0, damp2 = 0;
        inline float process(float in, float feedback) {
            float out = buf[idx];
            filterStore = out * damp2 + filterStore * damp1;
            buf[idx] = in + filterStore * feedback;
            idx = (idx + 1) % buf.size();
            return out;
        }
    };
    struct AllPass {
        std::vector<float> buf; size_t idx = 0;
        inline float process(float in) {
            float buffered = buf[idx];
            float out = -in + buffered;
            buf[idx] = in + buffered * 0.5f;
            idx = (idx + 1) % buf.size();
            return out;
        }
    };

public:
    void prepare(double sampleRate) {
        sampleRate_ = sampleRate;
        // Freeverb base tunings @44.1k, stereo spread 23 samples.
        static const int combTunings[8] = {1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617};
        static const int apTunings[4] = {556, 441, 341, 225};
        for (int ch = 0; ch < 2; ++ch) {
            for (int i = 0; i < 8; ++i) {
                int len = static_cast<int>((combTunings[i] + ch * 23) * sampleRate / 44100.0);
                combs_[ch][i].buf.assign(std::max(len, 1), 0.0f);
                combs_[ch][i].idx = 0;
                combs_[ch][i].damp1 = 0.2f; combs_[ch][i].damp2 = 0.8f;
            }
            for (int i = 0; i < 4; ++i) {
                int len = static_cast<int>((apTunings[i] + ch * 23) * sampleRate / 44100.0);
                allPass_[ch][i].buf.assign(std::max(len, 1), 0.0f);
                allPass_[ch][i].idx = 0;
            }
        }
        preDelay_.assign(static_cast<size_t>(sampleRate * 0.05), 0.0f); // up to 50 ms
        preIdx_ = 0;
    }

    // inL/inR dry; roomSize01 & wetDry01 from UI; writes wet-mixed outL/outR.
    inline void process(float inL, float inR, float roomSize01, float wet01,
                        float &outL, float &outR) {
        const float feedback = 0.70f + roomSize01 * 0.26f;         // 0.70..0.96 long cathedral tail
        const float inputGain = 0.015f * (0.7f + roomSize01 * 0.6f);
        // Pre-delay: 12 ms + roomSize * 28 ms.
        const size_t delaySamples = static_cast<size_t>((0.012 + roomSize01 * 0.028) * sampleRate_);
        const size_t readIdx = (preIdx_ + preDelay_.size() - std::min(delaySamples, preDelay_.size() - 1)) % preDelay_.size();
        const float mono = (inL + inR) * 0.5f;
        preDelay_[preIdx_] = mono;
        preIdx_ = (preIdx_ + 1) % preDelay_.size();
        const float delayed = preDelay_[readIdx];

        float wetL = 0, wetR = 0;
        for (int i = 0; i < 8; ++i) {
            wetL += combs_[0][i].process(delayed * inputGain, feedback);
            wetR += combs_[1][i].process(delayed * inputGain, feedback);
        }
        for (int i = 0; i < 4; ++i) {
            wetL = allPass_[0][i].process(wetL);
            wetR = allPass_[1][i].process(wetR);
        }
        const float wetGain = wet01 * 0.9f;
        const float dryGain = 1.0f - wet01 * 0.35f;                // gentle dry duck as mix rises; voice stays upfront
        outL = inL * dryGain + wetL * wetGain;
        outR = inR * dryGain + wetR * wetGain;
    }

    void reset() {
        for (int ch = 0; ch < 2; ++ch) {
            for (auto &c : combs_[ch]) { std::fill(c.buf.begin(), c.buf.end(), 0.0f); c.filterStore = 0; }
            for (auto &a : allPass_[ch]) std::fill(a.buf.begin(), a.buf.end(), 0.0f);
        }
        std::fill(preDelay_.begin(), preDelay_.end(), 0.0f);
    }

private:
    double sampleRate_ = 48000;
    Comb combs_[2][8];
    AllPass allPass_[2][4];
    std::vector<float> preDelay_;
    size_t preIdx_ = 0;
};
