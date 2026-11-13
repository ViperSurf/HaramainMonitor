# Haramain Monitor

Ultra-low-latency live microphone monitoring for Android, built for crisp vocal
recitation. Native Oboe (AAudio) duplex engine, a 9-stage C++ DSP chain,
explicit hardware routing, and a foreground service so monitoring survives
the screen turning off.

**Defaults on first launch — Haramain (Original), the user's specified
settings:** Room 95% · Mix 52% · Compressor 70% · De-Esser 50% · Gate −45 dB ·
Safety ON (−3 dBFS ceiling).

## Presets

| Preset | Room | Mix | Comp | De-Esser | Gate | Character |
|---|---|---|---|---|---|---|
| Haramain | 100% | 45% | 65% | 45% | −48 dB | Masjid al-Haram, research-based (below) |
| Masjid an-Nabawi | 82% | 38% | 58% | 42% | −48 dB | Gentler, sweeter, more intimate |
| Studio Dry | 12% | 8% | 60% | 35% | −55 dB | Near-dry practice/monitoring |
| Haramain (Original) | 95% | 52% | 70% | 50% | −45 dB | The user's own specified settings (boot default) |
| Custom | — | — | — | — | — | Your saved combination (DataStore) |

### Preset research notes

Why "Haramain" is NOT just a name — what the published acoustics say:

- **Masjid al-Haram's raw space rings for almost 6 seconds.** Its long-time
  head of audio, Ahmad Tekin Topuzdag, describes the Grand Mosque's sound
  reverberating ~6 s, with strong room resonances at **212 Hz and 320 Hz**
  (right on Arabic vowel frequencies) that had to be notched out, and a
  processor chain of **gates, limiters, compressors and reverbs** —
  the same stage order this app uses. (topuzdag.com/proaudio.htm)
- **But what worshippers hear is engineered clarity, not raw echo.** A 2025
  *Sound & Vibration* study of the Grand Mosque measured marble-driven late
  reflections arriving after 100 ms that form echoes and *decrease*
  intelligibility, against background noise as high as 90 dB(A) — the whole
  electro-acoustic design (≈7,000+ speakers with millisecond digital delay
  alignment) exists to keep the direct voice dominant. Hence the Haramain
  preset: maximum tail (Room 100%) with a *disciplined* mix (45%, below the
  Original's 52%). (academia.edu/156990437)
- **Masjid an-Nabawi is deliberately the gentler of the two.** Its sound was
  rebuilt after complaints of echo and feedback; accounts of both mosques'
  systems consistently describe Madinah's as sweeter, softer and more
  enclosed/intimate than Makkah's — hence a shorter tail (82%) and lower
  mix (38%). (witnesstv.net, 2026)
- **Preferred reverb for recitation is moderate.** Worshipper-preference
  research (Elkhateeb & Eldakdoky, 2022) found desired mid-frequency
  reverberation for Quran recitation around **1.4–1.8 s** — the presets aim
  at that *perceived* space, not the raw 6 s architecture.
  (researchgate.net/publication/369635142)

## Architecture

```
┌─────────────────────────── Kotlin / Compose ───────────────────────────┐
│ MainActivity            permission prompts, hosts the Compose UI       │
│ ui/MonitorScreen        routing bar, toggle, meters, presets, sliders  │
│ ui/components/          PrecisionControl: slider + [-]/[+] steppers,   │
│   PrecisionControl        numeric readout, double-tap = reset          │
│ ui/MonitorViewModel     owns AudioParams state, preset logic           │
│ data/PresetStore        DataStore: Custom preset + last-used params    │
│ service/MicMonitorService  FOREGROUND SERVICE (microphone|mediaPlayback)│
│ service/MonitorState    shared flows between UI and Service            │
│ audio/AudioRouter       AudioDeviceInfo enumeration, hot-plug,         │
│                           platform NoiseSuppressor/AEC attach          │
│ audio/NativeAudioEngine JNI facade (singleton)                         │
└────────────────────────────────┬───────────────────────────────────────┘
                                 │ JNI (float-array param block + setters)
┌────────────────────────────────▼─────────────── C++ ───────────────────┐
│ AudioEngine (Oboe)      LowLatency + Exclusive duplex streams, device  │
│                           ids, native sample rate, 2-burst buffer,     │
│                           error-callback self-heal to default route    │
│ dsp/DspChain            the 9 stages below, lock-free atomic params    │
└─────────────────────────────────────────────────────────────────────────┘
```

## DSP chain (exact signal order)

1. **Mic Boost** — 0–200% → 0–+24 dB, tanh soft-knee saturation
2. **Static Remover** — steep 85 Hz HPF (24 dB/oct) + HF hiss attenuation that
   only bites while the voice is quiet
3. **Noise Gate** — −80–0 dB threshold, mean-absolute envelope, hysteresis
4. **Noise Suppression** — min-statistics noise floor + Wiener-style gain;
   Android's platform `NoiseSuppressor`/`AcousticEchoCanceler` are also
   attached to the input session when the device offers them
5. **De-Esser** — split-band, 5–8 kHz sidechain
6. **Compressor** — 3 ms attack / 180 ms release, auto makeup gain
7. **Haramain EQ + Reverb** — −1.5 dB low shelf, +3.5 dB presence at
   3.4 kHz, +1 dB air; Freeverb (8 combs + 4 allpasses per channel) with
   12–40 ms pre-delay so the dry voice stays upfront
8. **Stereo Balance** — constant-power pan, L100–Center–R100
9. **Safety + Brickwall Limiter** — Safety ON: −3 dBFS ceiling, −12 dBFS cap
   when speaker-out + built-in mic risks feedback; Safety OFF: −0.5 dBFS
   brickwall only

> Gate tuning note: the gate reads the *boosted* signal, so its threshold must
> sit above your mic's boosted hiss floor. Typical phone mic: −45 dB default is
> right. Loud USB hiss (≈ −50 dB RMS): raise the gate toward −38 dB.
> **Distant mic?** Don't drop the gate to −80 dB (that's gate fully open — all
> the static returns). The middle ground is −55 to −60 dB plus more Mic Boost.
> The gate itself now has a 150 ms hold, 6 dB hysteresis and smooth 90 ms
> closing ramps, so a voice hovering near the threshold no longer chatters.

## Routing

Inputs: Bottom Mic · Back Mic · Wired Headset · USB · Bluetooth SCO/LE.
Outputs: Wired · USB-C DAC · Bluetooth A2DP/LE · Earpiece · Loud Speaker.
Selections are pushed to Oboe via `setDeviceId()`; unplugging the routed
device falls back to the system default natively and the UI badges resync.
Bluetooth output works but adds 100 ms+ of codec latency — wired/USB is the
low-latency path. (Back-mic detection is a heuristic: Android doesn't expose
mic position, so the secondary built-in mic is identified by device name.)

## Screen-off behavior

`MicMonitorService` runs as a foreground service typed
`microphone|mediaPlayback`, holds a `PARTIAL_WAKE_LOCK` and audio focus, and
posts a persistent notification with **Mute** and **Stop** actions that work
from the lock screen. The Activity can be swiped away; monitoring continues.

## Install on your phone (no Android Studio needed)

Every push to `main` triggers the **Build installable APK** workflow
(`.github/workflows/build-apk.yml`), which compiles the app in the cloud:

1. Open this repo's **Actions** tab → latest green run → download the
   **HaramainMonitor-debug-apk** artifact — or grab `app-debug.apk` from the
   rolling **dev-latest** pre-release under **Releases**.
2. Open the APK on your phone and install it (Android may ask you to allow
   "install from this app" for your browser or Files app — that's the normal
   sideload prompt).
3. Future builds install **over the top**: all builds are signed with the
   committed dev keystore (`keystore/debug.keystore`, dev-only, password
   `android`), so there's no signature-mismatch uninstall. Never ship a
   store build with this key.

## Build locally

Gradle wrapper is included (Gradle 8.9, distribution checksum pinned).
Needs JDK 17 + Android SDK 35, build-tools 35.0.0, NDK 26.3.11579264 and
CMake 3.22.1 — Android Studio's SDK Manager can install all of them.
Oboe comes from Prefab (`com.google.oboe:oboe:1.9.0`, `buildFeatures.prefab = true`).

```
./gradlew assembleDebug
# APK lands in app/build/outputs/apk/debug/app-debug.apk
```

## Host DSP test (no Android needed)

The DSP chain is portable C++17 and is verified on the host:

```
g++ -std=c++17 -O2 -I app/src/main/cpp app/src/main/cpp/test/dsp_chain_test.cpp -o /tmp/dsp_test && /tmp/dsp_test
```

Verified behavior: silence stays silent; a 0 dBFS signal at 200% boost is held
to exactly −0.5 dBFS with safety off and under −3 dBFS with safety on; hiss at
the typical phone-mic floor is crushed below −68 dB by the default gate;
hard-left balance silences the right channel; mute is exact zero.

## Known limits

- Latency readout is an estimate from stream buffer sizes (input + output +
  one burst); true round-trip varies by device, typically 10–20 ms wired.
- `AudioEngine::getAudioSessionId()` uses Oboe's session id (API 31+ AAudio);
  on older devices platform effects simply don't attach and the C++ stages
  carry the full load.

## v1.1 — Phase 1 (latency, recording, tone, lock)

- **Lower latency:** duplex is now input-callback → lock-free ring buffer →
  output callback (no blocking read in the output callback). The output buffer
  starts at **one burst** and auto-grows by a burst only if the device reports
  xruns (checked 1×/s, max 4 bursts). The latency readout is **measured** from
  the streams' hardware timestamps (buffer math as fallback).
- **Bass / Treble Boost:** user low-shelf (200 Hz) and high-shelf (9 kHz)
  trims, ±12 dB, layered on the fixed Haramain EQ voicing.
- **Recording:** tap of the post-DSP stereo mix written to WAV on a writer
  thread (app-private `Music/recordings/`). Rec from the app or the
  notification. The **Library** plays, shares, deletes and exports any WAV to
  M4A/AAC with the platform encoder. Recordings never leave the phone unless
  you share them.
- **Settings lock:** one-tap lock makes all audio controls read-only
  (Start/Stop, Mute and Rec keep working); optional PIN stored as a SHA-256 hash.
- **Theme:** System / Dark / Light.
- **Resume & recovery:** if the app process dies while monitoring, reopening
  the app resumes automatically (explicit stops don't). If the native engine
  dies mid-session, the service retries up to 3 times before stopping cleanly.

## v1.2 — Auto Setup, save locations, Simple Studio

- **Auto Setup** (Presets card): a two-step calibration. Step 1 measures the
  room while you breathe without speaking (gate is set above your breath);
  step 2 (optional) measures your recitation. Guardrails: boost is derived
  from measured peaks and capped so peaks land near −9 dBFS (6 dB below the
  safety ceiling — the limiter stays out), suppression is capped at 60, and
  the gate can never sit above your voice's average level. The result is
  reviewable before applying and saves as an **Auto** preset chip. The native
  analyzer measures the RAW input (RMS / peak / >4 kHz RMS) pre-DSP.
- **Simple Studio** preset: clean, nearly dry speech (Room 10 / Mix 6).
- **Save locations:** every Library item has a one-tap **Save to Downloads**
  (MediaStore, Android 10+), and App Settings gains a **Recording folder**
  picker (SAF) — finished recordings are automatically copied to the folder
  you choose, which can be a Downloads or Google Drive folder.

## v1.3 — Calmer UI, honest mic labels

- **Collapsible sections:** Routing, Recording, Presets, Vocal Chain,
  Haramain Space, Safety and App Settings now start closed and open on tap,
  so the first screen is just Start/Stop, Mute and the meters. The Recording
  header still shows a red ● REC badge while a recording is running.
- **Mute is unmissable:** while muted, the header reads "🔇 MUTED", a red
  banner sits above the meters with its own Unmute button, and the Mute
  button itself turns into a solid red UNMUTE.
- **Built-in mic labels fixed:** Android does not tell apps which physical
  mic is which — the old name heuristic labelled one phone's *bottom* mic
  "Back Mic". Built-ins are now all "Built-in Mic", with identical names
  numbered ("Microphone (2)") so each stays individually selectable. USB and
  Bluetooth routing is unchanged. (Custom presets stay a single slot by
  design — between it and the Auto preset, named libraries would add
  management clutter without new capability.)
