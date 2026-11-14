# Haramain Monitor

Ultra-low-latency live microphone monitoring for Android, built for crisp vocal
recitation. Native Oboe (AAudio) duplex engine, a 9-stage C++ DSP chain,
explicit hardware routing, and a foreground service so monitoring survives
the screen turning off.

**Defaults on first launch — Haramain (Original), the user's specified
settings:** Room 95% · Mix 52% · Compressor 70% · De-Esser 50% · Gate −45 dB ·
Safety ON (−3 dBFS ceiling).

## Feature reference (v3.3) — the full list

### Live monitoring & sound
- Ultra-low-latency duplex engine (Oboe/AAudio, device-native sample rate,
  exclusive low-latency streams); runs in a foreground service with the
  screen off.
- 9-stage native DSP chain, in order: Mic Boost (soft-knee) -> Static
  Remover (85 Hz high-pass + quiet-only hiss cut) -> Noise Gate (hold +
  hysteresis) -> Noise Suppression (minimum-statistics, floor-relative,
  depth to ~-16 dB) -> De-Esser (split-band, sibilance only) -> Compressor
  (soft knee, auto makeup) -> EQ + 5-band Graphic EQ + reverb (Freeverb
  with damping and stereo width) -> Stereo Balance -> anti-howl notch
  bank -> Safety/Brickwall Limiter (-3 dBFS with Safety on).
- Fixed vocal voicing EQ + user Bass/Treble + 3-band Vocal Tone Sculptor
  (Warmth / Clarity / Air) + 5-band Graphic EQ (60/250/1k/4k/12k).
- Presets: Masjid al-Haram (default; Room 95, Mix 52, Comp 70, De-Esser
  50, Gate -45), Masjid an-Nabawi, Historic Stone Dome, Local Masjid
  Hall, Lead (Imam), Studio Dry, Simple Studio, Auto (calibration),
  Custom (your saved tweaks).
- Processing Activity readout under the meters: Gate %, Suppression %,
  Limiter % and the howl-notch indicator. (Compressor and De-esser
  work silently — readouts for them were tried in v3.3 and removed in
  v3.4: at real levels they sit near zero and added confusion, not
  information.)
- Hold-to-hear-raw A/B compare ("hold to hear raw mic").
- Anti-howl Feedback Guard: FFT detector + harmonic veto parks a narrow
  notch on a ringing frequency before it becomes a howl (12 s expiry).
- Start/stop anti-pop fade (~40 ms); session timer in the meters panel.

### Routing & devices
- Explicit input/output pickers; feedback-risk detection (speaker +
  internal mic caps the limiter at -12 dBFS).
- Monitor Ears: Stereo / Left Only / Right Only (recordings stay stereo).
- Buffer modes: Ultra-Low / Balanced / Stable, with a live telemetry
  pill (buffer frames, device-native rate, xrun count, latency ms).
- Bluetooth suite: Usage::Game output path, dry-ducking for BT delay,
  2-burst buffer floor; platform noise suppression never attaches,
  platform echo cancellation only on speaker routes.
- Per-microphone memory: each mic remembers its own sound (toggleable).
- Teach-it-once mic naming: rename entries, group the phone's duplicate
  entries into physical mics ("Bottom Mic 1/2", "Back Mic 1/2/3"), hide
  entries you never use.
- Auto-start when your mic connects / auto-stop when your mic or
  output disconnects (3 s grace for re-plug or re-pick) — a dropped
  mic never silently becomes the phone mic. Both are keyed to the
  device the engine ACTUALLY routed to (ground truth even on the
  system-default route) and scoped per connection: This mic / Any
  external / Bluetooth only / USB only. If Android blocks a background
  start you get a tap-to-start notification instead.
- Quick Settings tile: tap to start/stop (optimistic), long-press opens
  the app.

### Recording & Library
- Post-DSP stereo WAV recording (or Dry Vocal raw-input mode).
- Auto-record on monitoring start; voice-activated recording (one file
  per take, gate-driven); auto-stop monitoring when quiet (never stops
  a recording in progress).
- Auto-record pairs well with Trim: record hands-free, then cut the
  dead air off both ends of each take with one tap — or turn on
  Auto-trim and every finished recording gets its _trimmed copy
  automatically.
- Library: play / share / delete / export M4A / save to Downloads /
  auto-copy finished takes to a folder you pick (SAF).
- Library review tools: 0.5x / 0.75x / 1x playback with pitch
  preserved, progress readout, A-B phrase loop (Set A / Set B / Loop).
- Silence Trim (edges only): floor-relative analysis writes a
  "<name>_trimmed.wav" beside the original; originals are never
  touched.
- Waqf marks: "Find waqf marks" on a playing take lists its likely
  phrase endings (level-based envelope analysis, cached in a sidecar
  file); tap a mark to jump playback there. A mirror of where you
  stopped — no pitch judgement.

### Practice
- Practice log: total time, session count, today's time (sessions of
  5 s+ count), stored per-day for 90 days.
- Daily practice goal (Off / 5 / 10 / 15 / 20 / 30 min) with a streak
  counter for consecutive goal days.
- Pitch indicator (mirror only — shows your voice's pitch, no scoring).
- Auto Setup calibration: speak for a few seconds and the app measures
  your voice and room, then sets Boost/Gate/Suppression for you.

### App & safety
- Collapsible sections (Routing and Presets open by default; your
  layout is remembered), every control has a plain-language help text.
- Settings lock with PIN; settings export/import as JSON (mic names,
  section layout, preferences — PIN and recording folder excluded).
- Themes: system / light / dark; themed app icon (brass crescent + mic
  on deep emerald) with monochrome and round variants.
- Earbud / lock-screen mute: media buttons and the system media
  controls toggle mute while monitoring (media session owned by the
  service).

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

## v1.4 — Teach-it-once mic names

Android exposes one logical entry per (physical mic × source profile) and
cannot say which entries share a mic — on the reference phone, 2 entries are
the bottom mic and 3 are the back mic. Routing gains **Name & organize
mics**: every input entry can be renamed (persisted by device id) or hidden
from the picker. A Select button routes an entry live so a tap test against
the input meter identifies it. The currently selected entry is never hidden
out from under an active route.

## v1.5 — Group mic labels, remembered sections, settings export

- **Mic groups:** the mic manager gains one-tap **Bottom Mic** / **Back Mic**
  chips. Entries sharing a physical name are numbered live in the picker
  (Bottom Mic 1/2, Back Mic 1/2/3); a lone member shows the plain name.
- **Sections remember:** collapsible section open/closed state is persisted,
  so the layout survives closing and reopening the app.
- **Export / Import settings:** one JSON file via the system picker carrying
  presets, mic names/hidden entries, theme, lock state (never the PIN),
  resume preference and section layout — move to a new phone in one step.

## v2.0 — Masjid acoustic spaces, Bluetooth suite, pitch & tone

- **Preset lineup (per spec):** Masjid al-Haram (default — the user's exact
  numbers: Room 95, Mix 52, Comp 70, De-Esser 50), Masjid an-Nabawi
  (82/42/65/50), Historic Stone Dome (75/38/65/45), Local Masjid Hall
  (45/25/60/45), Studio Dry (Room 0, Mix 0 — true zero-reverb isolation),
  plus Simple Studio, Custom and Auto. Terminology audit: the repo was
  swept clean of all non-Masjid space naming (verified by full-text search).
- **UI:** Hardware Routing and Presets open by default; sections animate
  with a 180 ms animateContentSize tween; meters are isolated in their own
  composable and painted via drawBehind (meter updates never recompose the
  screen); live audio-path telemetry pill (route, sample rate, latency,
  xruns).
- **Latency:** streams stay at the device's native sample rate (AAudio MMAP
  fast path — never forced, never downsampled); a Buffer/Latency selector
  (Ultra-Low 1× / Balanced 2× / Stable 4× bursts) retunes the output buffer
  live without restarting the service; the xrun auto-grower still guards
  above the chosen floor.
- **Bluetooth suite:** split-path guidance (BT output + phone/USB mic, with
  a one-tap fix when a BT mic would force 16 kHz call mode), Usage::Game
  low-latency hint on BT outputs, and BT Dry Monitor Ducking — the dry
  voice ducks (to 25% at full) while the Masjid reverb stays lush, killing
  the wireless slapback echo.
- **Recitation tools:** worker-thread autocorrelation pitch indicator
  (note + Hz, toggleable), a 3-band Vocal Tone Sculptor (Warmth 150 Hz /
  Clarity 3 kHz / Air 10 kHz, ±12 dB steppers), and a Practice Recorder
  source toggle (Processed Mix vs Dry Vocal).

## v2.1 — See the processing work; buffer modes with visible effect

Field-report driven (v2.0.1 on a real phone + USB/Bluetooth mics):

- **Processing Activity readout** in the master card: live gain-reduction
  percentages for Gate, Suppression and Limiter, straight from the native
  chain. The subtle stages are now provable: silence pins Gate at 100%,
  steady room noise moves Suppression, hot peaks engage the Limiter.
- **Buffer modes** now also resize the INPUT buffer, and the telemetry
  pill shows the live buffer size (`buf 192f`), so Ultra-Low/Balanced/
  Stable visibly apply. (On most phones one burst is ~4 ms, so the modes
  trade single-digit-to-~12 ms against glitch resistance; Bluetooth codec
  latency, 200-400 ms, dwarfs them — that delay is the radio link, not
  the app.)
- **Bluetooth mics:** the platform NoiseSuppressor/AEC no longer stack on
  SCO inputs (the headset + call path already run their own; stacking
  caused warbling). BT mic bandwidth remains the 16 kHz call channel —
  a hard platform limit; phone/USB mics are the quality path.
- Pitch thread is now restart-safe against the stream-error recovery
  path (join-before-relaunch).

## v2.2 — The suppressor actually suppresses; Bluetooth stability pass

- **Noise suppression was inert (field-caught, host-proven).** The floor
  tracker could never learn a real noise level: seeded at 1e-5, it could
  only rise within 1.6x of itself, while real hiss sits 100x+ higher, so
  the Wiener gain never left 1.0 and the Activity meter honestly read 0%.
  Two candidate laws failed host tests before the keeper: minimum-
  statistics over 0.25 s blocks (smoothed block minima). Verified: steady
  hiss now settles at the full depth gain (0.66 at 40%) while modulated
  speech bursts pass at 0.99. The stale constant-level unit test was
  rewritten to the real contract.
- **Activity readout is now read-and-hold:** the native side accumulates
  the worst stage gains between polls and resets on read, so brief
  suppression/limiter dips can't slip between 50 ms UI polls.
- **Bluetooth stability:** BT outputs get a 2-burst buffer floor (a
  1-burst app buffer starves the radio link into dropouts) and the xrun
  auto-grower may climb to 8 bursts on BT (4 elsewhere). A clock-drift
  guard drops oldest ring frames when input and output device clocks
  diverge (split-path phone-mic -> BT-out), preventing the slow latency
  creep that ends in dropout bursts.
- **Buffer on hardware change:** route changes now re-assert the user's
  buffer mode on the new device, and the pill shows frames AND mode
  (`buf 192f · 1x`) — frame counts differ across devices because burst
  sizes differ (96f and 192f can both be Ultra-Low 1x).

## v2.3 — Auto-stop when quiet + tooltips everywhere

- **Auto-stop (default ON, 10 min):** the service tracks a fast-attack /
  slow-decay average of the raw input peak; any voice-level sound resets
  the clock, while steady room tone (a fan included) does not. After the
  chosen stretch of quiet (Off / 5 / 10 / 15 / 30 min in App Settings)
  monitoring stops by itself. A recording in progress is never stopped.
- **Tooltips:** an (i) beside every slider, toggle, section and the
  buffer/pitch rows opens a one-or-two-sentence explanation, and the
  Presets section now carries an "About this space" panel describing
  the selected space (Masjid al-Haram, an-Nabawi, Stone Dome, Local
  Masjid Hall, Studio Dry, Simple Studio, Custom, Auto).

## v2.4 — A/B compare, per-microphone memory, auto-record

- **A/B compare:** a hold button under the master controls ("Hold to
  hear raw mic") bypasses the entire chain while held — the untouched
  microphone, protected only by a -0.5 dB brickwall — so the subtle
  stages can be judged in one second. Momentary by design; never saved
  into presets. Host-tested: raw passthrough + brickwall verified.
- **Per-microphone memory (default on):** switching input devices saves
  the current sound under the mic you are leaving and restores the
  sound saved for the mic you picked (full params + preset kind, in
  PresetStore device slots). Toggle in App Settings.
- **Auto-record (default off):** recording starts by itself whenever
  monitoring starts, in the Recording section; enabling it mid-session
  starts a recording immediately. Auto-stop still never interrupts a
  recording.

## v2.5 — Smoothness pass + the performance budget

Audit of every hot path before adding more features. The native audio
core was already clean and is unchanged: the output callback allocates
nothing and takes no locks; recordings are written by a dedicated
thread from a lock-free ring; pitch analysis runs on a worker thread
at ~90 ms cadence and only when enabled; telemetry polls at 20 Hz and
only the isolated meters panel recomposes at that rate.

Two real offenders, both fired by slider drags, both fixed:
- The params collector wrote all 19 keys to DataStore on EVERY
  emission (dozens per second while dragging). Persistence is now
  debounced: only the settled value (600 ms quiet) is saved.
- The foreground notification was rebuilt and reposted on every
  emission. It now refreshes only when its actual content changes
  (mute flip; start/stop/record have their own call sites).

**The standing budget for every future feature:**
1. The audio callback gets O(1) flag checks only — no allocation, no
   locks, no I/O, no per-sample work beyond the existing chain.
2. Analysis runs on worker threads, decimated (pitch: 90 ms), gated
   by an off switch.
3. UI-rate state (20 Hz max) lives only in the meters panel.
4. Disk writes are debounced or event-only, never per-interaction.
5. A feature that cannot meet this budget does not ship always-on;
   it ships behind a toggle that defaults off.

## v2.6 — Quick Settings tile + voice-activated recording

- **Quick Settings tile** (MonitorTileService): start/stop monitoring
  from the shade, with live state. Pure system surface: it sends the
  same intents the notification uses and syncs via
  requestListeningState on start/stop. If an Android version refuses
  a foreground-service start from a tile, the tap falls back to
  opening the app. First ever start should be from the app so the
  microphone permission is granted there.
- **Voice-activated recording** (Recording section): starts a take
  when the voice crosses the user's own Noise Gate threshold and
  stops it after 4 s of quiet (1 s minimum take), one file per take.
  Implemented entirely in the service on the existing voice EMA —
  zero audio-path changes. Mutually exclusive with Record
  Automatically (enforced in the view model and guarded in the
  service); Auto-stop still never interrupts a take.

## v2.7 — Tile responsiveness + release build (smoothness, round 2)

- **Tile state was stale by design accident:** the tile re-read the
  service state immediately after sending the intent, i.e. before the
  service had changed anything, and Android only guarantees a refresh
  when the shade reopens. The tile now flips OPTIMISTICALLY on tap
  and reconciles with the service truth on the next listen cycle;
  the service still pokes requestListeningState on start/stop.
- **New tile icon:** headphones glyph instead of a microphone (the
  mic glyph collides with Android's own mic-in-use indicators).
- **dev-latest now ships a RELEASE build** (non-debuggable), signed
  with the same dev keystore so it installs over the debug builds
  with data intact. Debug builds of Compose apps carry heavy runtime
  overhead; this is the single biggest UI-smoothness lever available.
  R8/minify remains a separate future step (JNI keep-rules).

## v2.8 — Tile long-press opens the app; anti-pop fades

- Long-pressing the Quick Settings tile now opens Haramain Monitor
  itself (QS_TILE_PREFERENCES intent-filter on MainActivity) instead
  of the system's App info page.
- Start and stop ease through a ~40 ms master fade in the engine, so
  monitoring no longer begins or ends with a click/pop. The fade is
  applied before the recording tap, so recordings fade too.

## v2.9 — Graphic EQ, reverb damping/width, auto-start, practice log

- 5-band graphic EQ (60 / 250 / 1k / 4k / 12k, +-12 dB, Q 1.41) in its
  own section; shapes the voice after the fixed voicing EQ and before
  the space. Host-verified: +12 dB at 1 kHz measures 3.98x, neighbor
  leak 1.009x.
- Reverb Damping and Width controls in Haramain Space. Damping drives
  the Freeverb comb feedback lowpass (50 reproduces the classic fixed
  value exactly, so existing presets sound unchanged); width cross-
  mixes the wet field (100 = original, 0 = mono wet).
- Auto-start when my mic connects (App Settings, default off): when
  the selected input transitions absent -> present while the app
  process is alive, monitoring starts.
- Practice log (App Settings): total monitored time, session count,
  and today's time, recorded once per session at stop (>= 5 s).
- Icon completion: the launcher mark (brass crescent + mic on emerald)
  gains a monochrome layer for Android 13 themed icons and a round
  variant. (The mark itself shipped with the original build.)
- Param block grows to 24 floats; no new JNI functions.

## v3.0 — Latency cut, Lead preset, single-ear monitor, anti-howl guard

- Latency: the platform NoiseSuppressor never attaches anymore (the
  native chain suppresses better) and the platform AEC attaches only
  on feedback-risk (speaker) routes. On USB/wired headphone routes the
  record path is now effect-free. The AEC handle is also stored and
  released properly now (it previously leaked per attach).
- Lead (Imam) preset: voice in front (Room 60 / Mix 30, Comp 65,
  Gate -50), space underneath.
- Monitor Ears (Hardware Routing): Stereo / Left Only / Right Only —
  summed mono to one ear so the other stays open to the room.
  Recordings are always full stereo. Param block grows to 26 floats.
- Feedback Guard (Safety section, default on): a worker-thread FFT
  detector (4096-pt, Hann) watches for a sustained narrowband ring;
  the engine cuts a -15 dB notch (Q 24, up to 4 slots) at that exact
  frequency, shown in the Activity line, self-expiring after ~12 s.
  A harmonic veto keeps held vowels from tripping it. Host tests:
  catches a 2.3 kHz ring at 2296.9 Hz (nearest FFT bin), ignores a
  vibrato vowel stack, notch cuts 0.178x at center and passes 0.999x
  at 300 Hz. Full host suite 22/22.
- Session timer under the meters, feeding the practice log.
- Bass/Treble stay: they are the beginner-friendly layer over the
  Graphic EQ, whose help text now says so.

## v3.1 — Auto-stop on disconnect

- New App Settings toggle: "Stop when my mic or output disconnects"
  (default off). The router now signals when a removal takes out the
  routed input or output; the service gives a 3 second grace (a
  re-plug or a quick re-pick cancels it) and then stops monitoring
  instead of falling back to another device — a dropped mic while
  leading should never silently become the phone mic in a pocket.

## v3.2 — Media-button mute, Library review tools, goal + streak, silence trim

- Earbud / lock-screen mute: the service now owns a media session.
  Headset-hook and play/pause media buttons toggle mute; play =
  unmute, pause = mute, stop = stop monitoring. Android's media
  controls (lock screen / Quick Settings) pick the session up and
  reflect live vs muted state from its playback state.
- Library review: per-take playback controls while a take plays —
  speed 0.5x / 0.75x / 1x with pitch preserved, progress readout,
  and an A-B phrase loop (Set A / Set B / Loop) for drilling.
- Practice goal + streak: daily goal chips (Off..30 min, default
  10) in App Settings; per-day history (90 days) powers a streak
  counter in the practice summary.
- Silence trimming (edges only): a Trim action on WAV takes in the
  Library. Floor-relative analysis (100 ms slices, speech line =
  15th-percentile floor + 12 dB, 0.3 s padding), non-destructive
  "<name>_trimmed.wav" output; the original is never touched.

## v3.3 — Proof for the quiet stages; connection automation hardened

- Compressor and De-esser now report to the Activity readouts (native
  activity array widened to 7 values): "Compressor -X.X dB - De-esser
  -X.X dB" under the meters. Help texts for the subtle stages say
  plainly what to expect (and what not to).
- Auto-start: a blocked background start (Android 12+/14 microphone
  FGS rules) no longer fails silently or kills the collector — it
  posts a "Your microphone connected" notification; tapping it starts
  monitoring from the foreground.
- Stop-on-disconnect rebuilt state-based: a routed side that had a
  concrete device and lost it arms the 3 s grace. Event ordering can
  no longer make it miss.
- README gained the full grouped Feature reference above.

## v3.4 — Automation keyed to the real device; auto-trim; waqf marks

- ROOT CAUSE found for "auto start/stop don't work": both were keyed
  to the picker selection, which stays -1 (system default) in real
  use while Android routes the USB mic by default. v3.4 keys both to
  the engine's ACTUAL routed devices (new native route-id readout):
  stop fires when the in-use device changes away or a removal drops
  a concrete selection; start fires on matching new devices.
- Automation scope: This mic / Any external / Bluetooth only / USB
  only, shared by auto-start and auto-stop.
- Auto-trim toggle: every finished recording gets a _trimmed copy
  automatically (background thread, originals untouched).
- Waqf marks in the Library (see Feature reference).
- Compressor/De-esser Activity readout line removed (v3.3's
  experiment): the values hover at ~0.0 dB at real levels and read
  as noise; the stages keep working silently.
