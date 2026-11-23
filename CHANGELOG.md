# Changelog

All notable changes to Haramain Monitor. Newest first. Through
v4.0 this history lived at the bottom of the README; it moved
here on 2026-11-16.

## Unreleased — Web PWA (WASM + AudioWorklet) & F-Droid polish

- **Cross-device Web PWA (`web/`):**
  - Added an offline-first Progressive Web App (`web/index.html`, `web/styles.css`, `web/app.js`, `web/manifest.webmanifest`, `web/sw.js`) for iOS, iPadOS, macOS, Windows, Linux, and Android via GitHub Pages.
  - Added a C++17 → WebAssembly bridge (`web/wasm/dsp_wasm.cpp`, `web/wasm/build.sh`) compiling `app/src/main/cpp/dsp/` (`DspChain.h`, `DspParams.h`, `HowlDetector.h`) into standalone `dsp.wasm` with zero Android/Oboe dependencies.
  - Added `AudioWorkletProcessor` (`web/worklet/haramain-processor.js`) and Web Audio engine (`web/js/audio-engine.js`, `web/js/presets.js`) supporting all 7 built-in presets, 9-stage DSP sliders, Monitor Ears (Stereo / L Only / R Only), Mute, A/B Bypass, Anti-Howl Feedback Guard, input/output device selection (`setSinkId` where supported), JSON settings backup, and 16-bit stereo WAV recording (processed or dry).
  - Added GitHub Pages deployment workflow (`.github/workflows/deploy-pages.yml`) to compile `dsp.wasm` with Emscripten and publish `web/` automatically on push to `main`.
- **F-Droid & Release Build Polish:**
  - Enabled R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`) for `release` builds in `app/build.gradle.kts`.
  - Trimmed Fastlane changelog `fastlane/metadata/android/en-US/changelogs/25.txt` under F-Droid's 500-character limit.

## v4.0 — Material 3 redesign, 3-accent palettes, About & Support, reliability & tests

- **Bug fix (Feedback Guard):** `AudioEngine` now feeds `mPitchRing`
  whenever `aHowlGuardOn` is enabled, not only when Voice Steadiness
  (`mPitchEnabled`) is enabled — previously the Feedback Guard FFT
  thread starved with 0 frames whenever Voice Steadiness was off.
- **Bug fix (Lead (Imam) preset):** added `PresetKind.LEAD` to
  `MonitorViewModel.detectPreset()` so selecting Lead (Imam) stays
  highlighted instead of flipping to Custom.
- **Bug fix (Bluetooth buffer floor):** `AudioEngine::setBufferMode()`
  now preserves the 2-burst Bluetooth floor when `mUsageGame` is active.
- **Bug fix (Disconnect automation):** manually switching the input or
  output picker back to System Default (`-1`) re-baselines instead of
  triggering a false 3-second disconnect stop.
- **UI fix (Library & Presets):** wrapped `RecordingsActivity` in a
  themed `Surface` so text/icons have proper contrast in all themes;
  `Custom` preset chip no longer highlights alongside `Auto` when the
  saved `Auto` preset is active.
- **DSP & Portability:** reset stale notch frequency tracking in
  `DspChain` when a notch expires; added `<algorithm>` include in
  `Biquad.h` for standalone `clang++` / `libc++` builds.
- **Testing:** added JVM unit test suites (`AudioParamsTest`,
  `AutoCalibratorTest`, `WavTrimmerTest`, `WaqfAnalyzerTest`), expanded
  `dsp_chain_test.cpp` (feedbackRisk cap, notch re-arming), wired both
  plus JNI symbol & naming checks into CI, and added the integrated
  testing manual (`docs/TESTING.md`).
- **Material 3 Redesign & 3-Accent Color System (`Theme.kt`, `MonitorScreen.kt`, `RecordingsActivity.kt`):**
  - Pure `#000000` OLED dark mode with two selectable 3-accent palettes:
    **Studio Orange** (default: Black/Espresso + Studio Orange + Amber-Bronze + Silver-Gray)
    and **Haramain Emerald** (Emerald + Warm Brass + Teal-Cyan).
  - Left-aligned Top App Bar with Library shortcut & 3-dot overflow menu (`⋮`),
    3-tab Bottom Navigation Bar (**Monitor** · **Sound** · **Settings**),
    Snackbar + Banner + Dialog feedback tiers, and Library Search + per-take
    3-dot kebab menu.
  - Added **Reset all settings** (with confirmation dialog) in App Settings
    and overflow menu.
- **About & Support section (`AboutSection.kt`):** collapsible section
  with dynamic version/build readout, one-tap Copy Diagnostics, inline
  What's New changelog, FAQ & Quick Guide, pre-filled GitHub Bug Report
  and Feature Request deep-links (`.github/ISSUE_TEMPLATE/`), live
  System & Diagnostics status, third-party licenses, and offline Privacy
  Policy & Honesty Disclaimer.
- **Docs & Store Readiness:** updated `README.md` with all 9 presets,
  exact parameters, plain-English vocal/reverb overview, uniform screenshots,
  walkthrough demo, and the current v4.0 DSP chain; added Apache 2.0
  `LICENSE`, `PRIVACY.md`, `fastlane/metadata/android/en-US/` store
  listings/screenshots, `RETROSPECTIVE.md`, and optional env-var release
  signing in `app/build.gradle.kts`.

## v3.6 — Voice Steadiness; playback stays loaded; QoL pass

- The pitch indicator is now Voice Steadiness: same tracker, honest
  job. Hz readout only (note name removed — recitation has no right
  note, and the voice should barely move); help text frames it as a
  breath mirror for held sounds and a starting-level check.
- Library: a take that plays to the end stays loaded there instead
  of closing, so Set B / Loop / scrub still work at the end.
  Pressing play again restarts (or jumps to A if a loop is armed).
- QoL under the hood, no behavior changes intended: the service's
  ~11 settings collectors now launch once per service lifetime
  instead of stacking duplicates on every start (the class of bug
  behind the v3.5 stop race), with buffer mode / pitch / BT usage
  re-applied deterministically from caches at each engine start;
  the Library list loads off the main thread, and M4A exports now
  show their real durations.
## v3.5 — Auto-record stop race fixed; Library player grows up

- Bug (field report): with Record Automatically on, stopping
  monitoring could turn recording ON, and recording could continue
  after the stop. Cause: the practice-log DataStore write inside
  stopMonitoring re-fired the auto-record collector while isRunning
  was still true and recording had just been stopped; stacked
  duplicate collectors from repeated startMonitoring calls made it
  likelier. Fixes: a central guard in startRecordingInternal (never
  start while stopped/stopping), isRunning=false + stopping flag
  set FIRST in stopMonitoring, a single deduplicated auto-record
  collector, and distinctUntilChanged on the setting flow.
- Library player: pause/resume (position kept), seek slider,
  -10s/+10s skip buttons, Reset A-B. The A-B loop no longer breaks
  out after a while: seeks are cooldown-limited and reaching the
  end of the file with a loop armed returns to A instead of
  finishing playback.
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
## v3.1 — Auto-stop on disconnect

- New App Settings toggle: "Stop when my mic or output disconnects"
  (default off). The router now signals when a removal takes out the
  routed input or output; the service gives a 3 second grace (a
  re-plug or a quick re-pick cancels it) and then stops monitoring
  instead of falling back to another device — a dropped mic while
  leading should never silently become the phone mic in a pocket.
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
## v2.8 — Tile long-press opens the app; anti-pop fades

- Long-pressing the Quick Settings tile now opens Haramain Monitor
  itself (QS_TILE_PREFERENCES intent-filter on MainActivity) instead
  of the system's App info page.
- Start and stop ease through a ~40 ms master fade in the engine, so
  monitoring no longer begins or ends with a click/pop. The fade is
  applied before the recording tap, so recordings fade too.
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
## v2.0.1 — Hotfix: v2.0 start crash

- v2.0 as first built crashed on Start monitoring: four new JNI
  functions had landed after the closing brace of the extern "C"
  block in native-lib.cpp, so their symbols were C++-mangled and
  the Kotlin side hit UnsatisfiedLinkError. The compile was green;
  only running the app showed it.
- Fix: all JNI functions live inside the single extern "C" block
  that runs to end-of-file. This failure created the standing rule
  verified on every release since: dump the built APK's
  libharamain_audio.so symbol table and confirm every JNI name is
  plain before calling a build shipped.
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
## v1.5 — Group mic labels, remembered sections, settings export

- **Mic groups:** the mic manager gains one-tap **Bottom Mic** / **Back Mic**
  chips. Entries sharing a physical name are numbered live in the picker
  (Bottom Mic 1/2, Back Mic 1/2/3); a lone member shows the plain name.
- **Sections remember:** collapsible section open/closed state is persisted,
  so the layout survives closing and reopening the app.
- **Export / Import settings:** one JSON file via the system picker carrying
  presets, mic names/hidden entries, theme, lock state (never the PIN),
  resume preference and section layout — move to a new phone in one step.
## v1.4 — Teach-it-once mic names

Android exposes one logical entry per (physical mic × source profile) and
cannot say which entries share a mic — on the reference phone, 2 entries are
the bottom mic and 3 are the back mic. Routing gains **Name & organize
mics**: every input entry can be renamed (persisted by device id) or hidden
from the picker. A Select button routes an entry live so a tap test against
the input meter identifies it. The currently selected entry is never hidden
out from under an active route.
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

## v1.0 — First build (2026-11-12)

The original Haramain Monitor, built to the founding spec: hear
your own voice with as little delay as Android allows, through a
recitation-focused effects chain, in spaces named for the Haramain.

- Native engine: Google Oboe (AAudio) duplex streams, LowLatency +
  Exclusive, Float, at the device's native sample rate (never
  forced — that keeps the fast MMAP path), 2-burst buffers.
- 9-stage C++ DSP chain in the spec's exact order: Mic Boost
  (0-200%, tanh soft-knee) -> Static Remover (85 Hz high-pass +
  quiet-only hiss cut) -> Noise Gate -> Noise Suppression (native
  stage, plus the platform suppressor attached when available)
  -> De-Esser -> Compressor -> Haramain EQ (+3.5 dB @ 3.4 kHz
  presence) with Freeverb room reverb -> constant-power Stereo
  Balance -> Safety / Brickwall Limiter.
- Every parameter is a lock-free std::atomic, snapshotted once per
  audio callback — the UI can never block or glitch the audio.
- Foreground service monitoring with explicit input/output device
  pickers, live input/output meters, master gain and mute.
- The original preset lineup (Masjid al-Haram default) with all
  settings persisted in DataStore; Jetpack Compose UI with
  sliders, steppers, readouts and double-tap reset.
- App icon: brass crescent with a microphone cut-out on deep
  emerald #0B3D2A.
