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
    float eqLowGainDb       = 0.0f;    // -12..+12 user low-shelf (bass) trim, added to preset voicing
    float eqHighGainDb      = 0.0f;    // -12..+12 user high-shelf (treble) trim, added to preset voicing
    float btDuckPercent     = 50.0f;   // 0..100 Bluetooth dry-monitor ducking amount
    bool  btDuckOn          = false;   // duck the dry voice on BT (wet reverb stays lush)
    float eqWarmthDb        = 0.0f;    // -12..+12 tone sculptor: low shelf 150 Hz
    float eqClarityDb       = 0.0f;    // -12..+12 tone sculptor: peak 3 kHz
    float eqAirDb           = 0.0f;    // -12..+12 tone sculptor: high shelf 10 kHz
    float masterGainDb      = 0.0f;    // -24..+12 output trim
    bool  safetyOn          = true;    // Headphone Safety mode
    float safetyCeilingDb   = -3.0f;   // ceiling while safety is ON
    bool  feedbackRisk      = false;   // set by Kotlin: speaker output + internal mic
    float eqBand60Db        = 0.0f;    // graphic EQ bands (v2.9), +-12 dB
    float eqBand250Db       = 0.0f;
    float eqBand1kDb        = 0.0f;
    float eqBand4kDb        = 0.0f;
    float eqBand12kDb       = 0.0f;
    float revDampingPercent = 50.0f;   // reverb tail HF damping; 50 == the classic fixed value
    float revWidthPercent   = 100.0f;  // reverb stereo width; 100 == full (original behavior)
    bool  muted             = false;
    bool  bypass            = false;   // A/B compare: raw mic, brickwall only (momentary, never persisted)
};
