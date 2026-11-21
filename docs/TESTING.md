# Haramain Monitor — Integrated Testing Manual

This manual covers **automated regression testing** (run on every CI build and locally before pushing) and **manual on-device verification** (run on the reference phone before publishing a release).

---

## Part 1: Automated Regression Testing

### Quick Local Verification (One Command)
Run all automated checks locally before committing or pushing:
- **Windows (PowerShell):** `.\scripts\verify.ps1` (naming check + `./gradlew testReleaseUnitTest` + C++ DSP suite on attached `x86_64` emulator)
- **Linux / macOS / Git Bash:** `./scripts/verify.sh` (naming check + `g++` host DSP suite + `./gradlew testReleaseUnitTest`)

### 1.1 Host C++ DSP Suite (`app/src/main/cpp/test/dsp_chain_test.cpp`)
Tests the portable C++17 DSP chain without Android/Oboe:
```bash
g++ -std=c++17 -O2 -I app/src/main/cpp app/src/main/cpp/test/dsp_chain_test.cpp -o /tmp/dsp_test && /tmp/dsp_test
```
**Covered regressions:**
- Silence stays silent and finite (no NaNs/denormals).
- Voice-level 220 Hz sine passes above 0.05 and stays under `-3 dBFS` safety ceiling.
- `0 dBFS` + 200% boost is held at `<= -3 dBFS` (Safety ON) and `<= -0.5 dBFS` (Safety OFF).
- `feedbackRisk = true` caps output at `<= -12 dBFS`.
- Hard-left balance silences right channel; `muted = true` produces exact `0.0f`.
- Bass shelf (+12 dB), Tone Sculptor Warmth (+12 dB), and 5-band Graphic EQ (+12 dB @ 1 kHz) lift target bands without leaking into neighbors.
- Noise Gate crushes `-58 dB` phone-mic hiss and `-50 dB` USB hiss (with gate `-38 dB`), and holds open through an 80 ms mid-phrase dip.
- Noise Suppression engages on steady hiss (`gain < 0.8`) while leaving modulated speech bursts untouched (`gain > 0.9`).
- Bluetooth dry-ducking lowers monitor level while keeping the wet room audible.
- Reverb damping darkens the late tail; width `0` produces a mono wet field.
- Feedback Guard `HowlDetector` catches a sustained 2.3 kHz ring, ignores a vibrato vowel stack, cuts at the notch frequency, and re-engages cleanly after notch expiry.
- A/B bypass passes raw mic untouched except for `-0.5 dBFS` brickwall.

### 1.2 Kotlin JVM Unit Tests (`app/src/test/java/com/haramain/monitor/`)
Run with Gradle (requires JDK 17 + Android SDK):
```bash
./gradlew testReleaseUnitTest
```
**Covered suites:**
- `AudioParamsTest`: verifies default values (`HARAMAIN == ORIGINAL`), 26-float JNI array layout order, and `matchesPreset()` distinctness for all 7 built-in presets (`HARAMAIN`, `NABAWI`, `STONE_DOME`, `LOCAL_HALL`, `LEAD`, `STUDIO`, `SIMPLE`).
- `AutoCalibratorTest`: verifies hot-mic 0 dB boost, quiet-mic `+18 dB` / `-9 dBFS` peak cap, gate clamping below `voice.rmsDb + boostDb - 6 dB`, and preservation of reverb/EQ settings.
- `WavTrimmerTest`: verifies edge-silence trimming with 0.3 s room-tone padding on synthetic PCM16 stereo WAVs and `NoSpeech` on steady floor noise.
- `WaqfAnalyzerTest`: verifies phrase-end timestamp detection and `.waqf` sidecar caching.

### 1.3 Pre-Release CI & Binary Verification Checklist
Every release build in `.github/workflows/build-apk.yml` must pass:
1. **Host DSP suite:** `ALL DSP TESTS PASSED` (exit 0).
2. **JVM unit tests:** `./gradlew testReleaseUnitTest` (exit 0).
3. **Naming rule check:** zero matches for the banned pair across `app/`, `README.md`, `CHANGELOG.md`, `docs/`.
4. **Release APK build:** `./gradlew assembleRelease` green via `gh run view`.
5. **SHA-256 parity:** release asset SHA-256 equals CI run artifact SHA-256.
6. **JNI symbol dump:** `nm -D lib/arm64-v8a/libharamain_audio.so` shows all **29** `Java_com_haramain_monitor_audio_NativeAudioEngine_native*` symbols plain and unmangled.

---

## Part 2: Manual On-Device Test Plan

Perform on the reference Android device (with USB mic, wired headphones, and Bluetooth earbuds).

### A. Launch, Permissions & Presets
| ID | Test | Steps | Expected Result |
|---|---|---|---|
| A1 | First-launch permissions | Fresh install or clear storage; open app. | Prompts for Microphone, Notifications (Android 13+), Bluetooth Connect (Android 12+). |
| A2 | Default preset | Check Presets section on first launch. | **Masjid al-Haram** is selected (Room 95%, Mix 52%, Comp 70%, De-Esser 50%, Gate −45 dB). |
| A3 | All 7 built-in presets | Tap each preset chip (`Masjid al-Haram`, `Masjid an-Nabawi`, `Historic Stone Dome`, `Local Masjid Hall`, `Lead (Imam)`, `Studio Dry`, `Simple Studio`). | Each chip stays highlighted after tap (including **Lead (Imam)**) and updates sliders/about text. |
| A4 | Custom & Auto exclusivity | Run **Auto Setup**, then tap **Auto** chip; then tweak a slider and tap **Save Custom**. | **Auto** highlights alone when Auto params are active; tweaking a slider switches to **Custom**. |
| A5 | Per-mic memory | Enable "Remember settings per microphone", set distinct presets on Phone Mic vs USB Mic, switch between them. | Each mic restores its own saved preset and params. |

### B. Hardware Routing, Bluetooth & Buffer Modes
| ID | Test | Steps | Expected Result |
|---|---|---|---|
| B1 | Buffer mode persistence | Set Buffer Mode to Balanced (2×) or Stable (4×), stop monitoring, restart app, start monitoring. | Selected buffer mode persists across restarts; telemetry pill reflects `buf Nf · Mx`. |
| B2 | Bluetooth 2-burst floor | Connect Bluetooth output, set Buffer Mode to Ultra-Low (1×), start monitoring. | Telemetry pill shows at least 2× burst frames (BT floor enforced); no choppy dropout burst. |
| B3 | Bluetooth dry-ducking | On BT output, toggle Dry-Ducking ON and speak. | Direct voice drops while Masjid reverb tail remains audible. |
| B4 | Monitor Ears | Toggle Stereo / Left Only / Right Only while monitoring and recording. | Headphones play selected ear(s); resulting WAV recording remains full stereo. |
| B5 | Mic Teaching | Rename a mic, group duplicate built-in entries (`Bottom Mic`), hide an unused entry. | Picker shows grouped names (`Bottom Mic 1`, `Bottom Mic 2`) and hides unused entries (never hides active mic). |

### C. Connection Automation (v3.4+)
| ID | Test | Steps | Expected Result |
|---|---|---|---|
| C1 | Auto-stop on disconnect | Leave picker on System Default, scope **This mic**, enable "Stop when my mic or output disconnects", start monitoring on USB mic, unplug USB mic. | Monitoring stops within ~3 s instead of silently falling back to phone mic. |
| C2 | Manual System Default switch | Pick concrete USB mic while monitoring, then manually pick **System Default**. | Monitoring continues without triggering a false 3 s disconnect stop. |
| C3 | Auto-start on connect | Enable "Auto-start when my mic connects", stop monitoring, plug in USB mic (test both foreground and backgrounded app). | Foreground: monitoring starts automatically. Background (Android 12+/14): high-priority "Your microphone connected" notification appears; tapping starts monitoring. |

### D. Feedback Safety & Directed Listening Tests
| ID | Test | Steps | Expected Result |
|---|---|---|---|
| D1 | Speaker howl test | Route Internal Mic + Loud Speaker (Safety ON, Feedback Guard ON), raise volume gently near ring threshold, then hold a steady vowel ("aaah"). | Feedback Guard notches the pure ring (`Act` notch Hz appears and expires after ~12 s) without notching the held vowel; limiter caps at `−12 dBFS`. |
| D2 | Static Remover | Toggle Static Remover 0% vs 100% in a room with low rumble/hiss. | Pauses between phrases are noticeably cleaner at 60–100% without thinning the voice. |
| D3 | Noise Suppression | Run a steady fan/AC, let it learn for ~2 s of quiet, speak phrases with 1 s gaps. | `Suppression %` rises during gaps (lowering fan floor) and returns near 0% while speaking. |
| D4 | De-Esser | Pronounce sharp "s/sh/th" sibilants at 0% vs 80%. | Harsh sibilance softens at 50–80% while vowels stay bright. |
| D5 | Compressor | Speak softly then loudly at 0% vs 70%. | Soft phrases come up and loud peaks level out smoothly without pumping. |
| D6 | A/B Bypass | Hold "Hold to hear raw mic". | Immediate raw passthrough; releasing immediately restores DSP chain. |

### E. Recording, Trim, Waqf Marks & Library
| ID | Test | Steps | Expected Result |
|---|---|---|---|
| E1 | Auto-record stop-race check | Enable "Record automatically", start monitoring (recording starts), tap Stop monitoring. | Both monitoring and recording stop cleanly; recording does NOT restart during teardown. |
| E2 | Voice-activated recording | Enable "Voice-activated recording", speak a phrase, stay silent > 4 s. | Starts on voice above gate threshold, stops after 4 s quiet, saves take ≥ 1 s. |
| E3 | Auto-trim & manual Trim | Record a take with 2 s silence before/after recitation; test manual Trim and Auto-trim. | `<name>_trimmed.wav` created with ~0.3 s padding on each edge; original WAV untouched. |
| E4 | Waqf marks | Open take in Library, play, tap "Find waqf marks", tap a mark chip. | Lists phrase-ending timestamps; tapping a chip seeks playback to that exact pause. |
| E5 | Library player & A-B loop | Test Pause/Resume, Seek slider, `−10s`/`+10s`, `0.5×`/`0.75×`/`1×`, Set A / Set B / Loop A-B, and let a take play to the end. | Loop repeats A→B cleanly without breaking; finished take stays loaded at end; Export M4A shows real duration; Save to Downloads & SAF folder copy work. |

### F. System Integration & Practice
| ID | Test | Steps | Expected Result |
|---|---|---|---|
| F1 | Quick Settings tile | Tap tile to start/stop; long-press tile. | Optimistic state flip reconciles with service; long-press opens app. |
| F2 | Earbud / lock-screen mute | Press play/pause or headset hook on earbuds or lock-screen media controls while monitoring. | Toggles Mute without stopping monitoring; notification & UI stay in sync. |
| F3 | Voice Steadiness | Enable Voice Steadiness, hold a steady madd vowel. | Displays Hz readout only (no note names or scoring). |
| F4 | Practice log & streak | Monitor ≥ 5 s, stop, check Practice log & Daily goal. | Session time, count, today's time, and streak update accurately. |
| F5 | Settings Lock/PIN & Transfer | Set PIN, Lock settings, try moving sliders, Unlock; Export JSON, reset a setting, Import JSON. | Controls read-only while locked; Export/Import restores presets, mic names, theme, layout (excluding PIN & SAF folder). |
