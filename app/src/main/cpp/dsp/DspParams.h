#pragma once
// Plain snapshot of every user-facing DSP parameter.
// The audio thread takes ONE snapshot per callback (copied from std::atomics in
// AudioEngine) and the whole buffer is processed with it — no locks, no torn reads.
struct DspParams {
    float micBoostPercent   = 50.0f;   // 0..200  -> 0..+24 dB pre-amp gain
    float staticRemover     = 60.0f;   // 0..100  hiss/hum removal depth
    float noiseSuppress     = 40.0f;   // 0..100  voice-isolation depth
    bool  noiseSuppressOn   = true;
    float gateThresholdDb   = -45.0f;  // -80..0 dB
    float deEsserPercent    = 50.0f;   // 0..100  (Haramain default 50)
    float compressorPercent = 70.0f;   // 0..100  (Haramain default 70)
    float roomSizePercent   = 95.0f;   // 0..100  (Haramain default 95)
    float wetDryPercent     = 52.0f;   // 0..100  (Haramain default 52)
    float balancePercent    = 0.0f;    // -100 (L) .. 0 (C) .. +100 (R)
    float masterGainDb      = 0.0f;    // -24..+12 output trim
    bool  safetyOn          = true;    // Headphone Safety mode
    float safetyCeilingDb   = -3.0f;   // ceiling while safety is ON
    bool  feedbackRisk      = false;   // set by Kotlin: speaker output + internal mic
    bool  muted             = false;
};
