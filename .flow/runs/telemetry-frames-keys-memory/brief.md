# Brief: telemetry-frames-keys-memory (slice S2 of usage-telemetry-log)

Triage of 2026-10-15 against master `cd16eb47` (S1 telemetry-core merged). Builds on
`research.md` (inherited; cited as `research.md: <section>`), the series brief
`.flow/runs/usage-telemetry-log/brief.md` (catalogue, AC-11..AC-14, owner answers) and the S1 spec
`docs/superpowers/specs/2026-10-15-telemetry-core.md`. The code wins where they differ.

## Classification

- **kind:** feature
- **size:** M. One goal (four probe families on the existing sink), but it crosses layers
  (Gradle catalogue, `MainActivity`, `core/telemetry`, the nav hook) and touches risk areas
  (D-pad input, startup/DI wiring, a new dependency, minified `release`). Not S: more than 3 files
  and a new dependency. Not L: nothing here is shippable on its own apart from the sink.
- **tracks:** `perf`. No `ui`: nothing the user sees changes (the Settings toggle exists from S1).
- **Device verification: yes** (D-pad input, startup wiring, build variants, runtime-only
  behaviour). Decided before looking at hardware.

## Findings (code)

Dependency
- `gradle/libs.versions.toml` has no `metrics-performance` (grep for `metrics|jank|tracing`: no
  hit; also none in `app/build.gradle.kts`). It must be added as version + library alias
  (`androidx.metrics:metrics-performance`, stable 1.0.0 per `research.md: 5`). Not verified:
  that 1.0.0 resolves in the `agentw` Gradle cache; the plan's first step builds it.
- Compose BOM is `2026.08.00` (`libs.versions.toml:7`); JankStats is not in the BOM.

Sink API (S1, `app/src/main/java/com/kino/puber/core/telemetry/`)
- `Telemetry.isEnabled` / `record` / `nowMs` (`Telemetry.kt:23-58`). Probe shape is
  `if (Telemetry.isEnabled) Telemetry.record(...)`; events carry primitives, enums, `Class`
  references only; names and JSON are made on the writer thread (`TelemetryEvent.kt:49-52`).
- A new event type needs: a `TelemetryEventType` entry with its per-minute cap
  (`TelemetryEvent.kt` top of `TelemetryNames.kt`: `TelemetryEventType`, caps at lines 3-9), a
  sealed member (`TelemetryEvent.kt:49-115`), one branch in the exhaustive `when` of
  `TelemetryEncoder.encode` (`TelemetryEncoder.kt:36-93`). The spec promises the writer, store and
  controller stay untouched; one exception below.
- `TelemetryWriter.append` assigns `scr` from the event for `nav/ready/act` and otherwise the last
  `nav.to` seen at WRITE time (`TelemetryWriter.kt:110-126`). That is wrong for `frames`: a
  window closed after a navigation would be labelled with the new screen. `frames`, `key` and
  `key_lat` events must carry their own screen `Class<*>` and the writer must use it (small change
  to `append`, plus `names.owner`). `mem` may use `lastScr`.
- Probes that exist only while recording are planted in `TelemetryRuntime.plantProbes` and removed
  in `detachProbes` (`TelemetryRuntime.kt:96-111`), including on auto-off, runtime toggle and
  storage failure. New probes (frame, memory) follow that pattern, so they cost nothing off.
- `Application.ActivityLifecycleCallbacks` is already registered at install
  (`PauseFlushCallbacks`, `TelemetryRuntime.kt:140-154`), but it is a no-op class when off; the
  new probes must not rely on it being given an Activity at enable time (a runtime toggle happens
  while an Activity is already resumed).
- The single writer thread has `scheduleRepeating` (`TelemetryExecutor.kt:33-41`): a fit for the
  60 s memory sample, off the main thread.
- Folding precedent: `EventFolder` / `ActFolder` (`EventFolder.kt`) fold on the writer thread.

Hooks
- `MainActivity.kt:27-87` overrides no key method. `Activity.dispatchKeyEvent` is called by the
  decor view before the view hierarchy, so it sees every key whether or not Compose consumes it.
  Keys delivered to other windows (dialog windows, IME) do not pass it.
- `NavTelemetry.recordNav` (`core/ui/navigation/component/NavTelemetry.kt:21-24`) is already the
  single function behind both `FlowComponent` collectors (`FlowComponent.kt:271,751`). The
  current-screen holder for frames can be set there with no edit to `FlowComponent.kt` (hot file in
  other runs). While off, the holder is never written.
- No `onTrimMemory`/`ComponentCallbacks2` anywhere (`rg onTrimMemory` empty). A probe can
  `registerComponentCallbacks` while recording; `PuberApp.kt` need not change.
- Image cache size: Coil `MemoryCache` is built in `PuberApp.newImageLoader`
  (`PuberApp.kt:99-126`, 15 % of heap); `SingletonImageLoader.get(context).memoryCache?.size`
  is readable from the writer thread (thread-safe in Coil; not verified for 3.5.0).

Existing numbers
- Emulator renders in software, so jank is about 99 % (`telemetry-core/qa-report.md`, Perf row:
  off 99.38 %, on 99.03 %). Absolute jank % and `key_lat` are not meaningful there.

## Design decisions the plan must carry (analyst recommendations)

1. **Frames.** `JankStats.createAndTrack(window, executor, listener)` created only while
   recording and the Activity is resumed; the listener copies `frameDurationUiNanos` into a
   fixed `IntArray` histogram (1 ms buckets to 100 ms, one overflow bucket) and increments
   `frames` and `janky` (from `FrameData.isJank`). No list, string, event or `FrameData` held in
   the callback. A window closes lazily in the callback when 10 s have passed, or on screen
   change or pause: p50/p90/p99 from the histogram, `max` kept exactly; then one primitive event
   is enqueued. No timer. Screen comes from the nav holder (a `@Volatile Class<*>?`), not from
   `PerformanceMetricsState`/`FrameData.states` (the states list is allocated per frame). The
   frame duration is the UI-thread part only; say so in the spec and the field note.
2. **key.** In `MainActivity.dispatchKeyEvent`, before `super`: `if (Telemetry.isEnabled)
   KeyProbe.onKey(event)`. A small primitive state machine on the main thread (no allocation per
   repeat): DOWN repeat 0 opens a burst, repeats add to `n`, UP (or a different key code, pause,
   or a 2 s idle timeout checked on the next event/flush) emits ONE `key` event with
   code, `n` and `dur_ms`. A single press is a burst of 1.
3. **Key code allow-list (privacy, not in the series ACs).** A physical or remote keyboard sends
   letter and digit key codes that would reproduce typed search text, which AC-7 forbids. The
   encoder maps only an allow-listed enum (D-pad, centre/enter, back, menu, media keys, channel
   up/down, page up/down, home) to a wire name; every other code is `other`. Never the raw int.
4. **key_lat.** Pending key = the earliest unanswered DOWN, stored as `eventTime` (uptime
   millis, CLOCK_MONOTONIC) in an `AtomicLong`. In the JankStats callback: the first frame whose
   end (`frameStartNanos + frameDurationUiNanos`) is after the key time answers it;
   `lat_ms = frameEnd - eventTime`; if `lat_ms > 50` emit `key_lat` with `lat_ms` and that frame's
   `frame_ms`; otherwise nothing. This is "key arrival to end of UI work of the first frame that
   reflects it", so it excludes GPU/composition/present time (FrameData has no total duration
   below API 31). The clocks agree in principle (both CLOCK_MONOTONIC); that is `unverified` and
   the first device check compares them (a `lat_ms` that is negative or hundreds of seconds means
   a base mismatch). A key held down is one pending entry, not one per repeat (the burst start is
   the measured moment). Cap `key_lat` at 60/min.
5. **mem.** A `MemoryProbe` planted with the other probes. While at least one Activity is
   started (foreground), `executor.scheduleRepeating(60_000)` samples on the writer thread:
   Java heap used/max (`Runtime`), native heap (`Debug.getNativeHeapAllocatedSize`), PSS
   (`Debug.getMemoryInfo().totalPss`, at most once per sample, never from main), image-cache size
   if available. `onTrimMemory(level)` adds one line with the level and the same fields minus PSS.
   Timer cancelled when the app is backgrounded or the probe is detached.
6. **Caps** (declared in `TelemetryEventType`): `frames` 12/min, `key` 120/min, `key_lat`
   60/min, `mem` 10/min. Over cap goes through the existing `meta dropped` path.
7. **Lifecycle.** A `FrameProbe` implements `ActivityLifecycleCallbacks`: tracking on resume
   when enabled, off on pause (window flushed). Because plant happens at toggle time, the probe
   needs the resumed Activity: keep a weak reference registered in `TelemetryRuntime.install`
   (cheap, always) so `plantProbes` can start tracking an already-resumed Activity.

## Acceptance criteria

Series IDs kept. "Off" means the Session log toggle is off.

- **AC-11** Given a long D-pad press on a list, when logging is on, Then one `key` line records
  the burst count (`n`) and duration (`dur_ms`), not one line per repeat. Given three separate
  single presses, Then three `key` lines with `n=1`.
- **AC-11a** (new, from decision 3) Given any key code outside the allow-list (letters, digits,
  or an unknown code), when pressed with logging on, Then the line says `other`; the raw code is
  not in any field. A unit test feeds every `KeyEvent` letter and digit code through the encoder
  and finds none of them in the output. `AC-7` scan of a device session still finds nothing.
- **AC-12** Given a scroll on Home or History, when 10 s have passed, Then a `frames` line for
  that screen (its own `scr`, correct even if the user navigated right after the window closed)
  holds `frames`, `janky`, `p50`, `p90`, `p99`, `max` in ms. The JankStats callback only copies
  numbers into a preallocated aggregator (checked in code review: no allocation, no `Telemetry`
  call except on window close, no formatting; unit test of the aggregator with a fake frame feed
  incl. window rollover, screen change and an idle gap).
- **AC-13** Given a key press whose first answering frame ends more than 50 ms after the key's
  event time, when logging is on, Then a `key_lat` line records `lat_ms` and that frame's
  `frame_ms`. Given an answer within 50 ms, Then no `key_lat` line. Given a held key, Then one
  pending measurement, not one per repeat (unit test with injected frame feed and clock).
- **AC-14** Given one foreground minute, when logging is on, Then exactly one `mem` line is
  written, sampled off the main thread (unit test: sampler runs on the writer thread). Given
  `onTrimMemory(level)`, Then an extra `mem` line with that level. Given the app in the
  background, Then no `mem` line.
- **AC-S2-OFF** (support for the series AC-2/AC-21) Given the toggle is off (fresh process, and
  after switching on then off at runtime), Then no `JankStats` instance is tracking, no memory
  timer is scheduled, no component callback is registered, `MainActivity.dispatchKeyEvent` does
  a single flag check, and the telemetry directory does not change (unit tests on the probe
  lifecycle with a fake executor; device: empty directory plus a cold start A/B like S1's).
- **AC-S2-NODIST** (support for AC-21, owner judges the final number) Given logging on, when
  scrolling, Then no file IO, JSON or PSS runs on the main thread or the FrameMetrics thread
  (unit test on thread names as S1 T35/T36 did; StrictMode-free code review).
- **AC-S2-R8** Given the minified `release`/`benchmarkRelease` build, when logging is on, Then
  `frames`, `key`, `key_lat` and `mem` lines appear with readable `scr` and nothing obfuscated
  (JankStats consumer rules do not strip the listener; device check as S1 D5).

## Risks

- **Distorting the measurement** (highest): main-thread work in `dispatchKeyEvent`, allocation
  in the JankStats callback, PSS on any hot thread. Mitigations are decisions 1, 2, 5; PSS cost
  is `unverified` (`research.md: 5`), so measure it once on device and, if it exceeds a few
  ms, skip it on `low_ram` or lengthen the period.
- **Privacy**: key codes can leak typed text (decision 3). Also the `frames` screen name is a
  class name only (S1 rule).
- **Writer `scr` mismatch** for event types with their own screen (see Findings): forgetting it
  mislabels `frames` windows; covered by an AC-12 unit test.
- **Software-GPU emulator**: every frame janky and every frame slow, so the 50 ms threshold
  fires constantly and floods `key_lat` and the cap/`dropped` path. Not a defect of the code;
  it shapes verification (below).
- **Runtime toggle**: switching on while an Activity is resumed must start tracking without a
  restart, and off must stop; JankStats on the wrong window after Activity recreation
  (config change, locale change via `AppLanguageProvider`) must not leak the old window.
- **Clock bases** for `key_lat` (decision 4) are unverified until the first device read.
- **JankStats availability**: 1.0.0 resolves in the offline `~/.gradle-agents` cache? Unknown;
  a failed dependency fetch blocks the build, not a reason to downgrade the plan.
- **Hot files**: `MainActivity.kt` (small), `PuberApp.kt` untouched, `FlowComponent.kt`
  untouched. Low merge risk with `player-overlays-port` and `upstream-sync-triage`.
- **Player screen**: the player window is the same Activity window, so frames and keys are
  covered; keys in dialog windows are not (accepted, state in the spec).

## Test notes

Unit (JUnit 5, fake clock, fake executor, as S1):
- frame aggregator (histogram percentiles, rollover at 10 s, close on screen change and pause,
  p99 and max exact on a crafted feed, zero allocation by construction), key burst machine
  (repeat counting, UP, different key, timeout, pause), key_lat matching (answer under and over
  50 ms, held key, no frame arrives), key allow-list mapping, memory sampler (thread, period,
  foreground only, trim line), encoders for the four types (fields exactly the allow-list; extends
  S1's field-set test), caps, probe lifecycle off/on/off.

Device (leased emulator, `adb -s`, install-preserve, S1 procedures):
- The emulator is a software renderer. Do NOT judge jank percentage, absolute latency or
  per-frame ms. Verify instead: line structure and field sets (`jq` key sets as S1 did), one
  `key` line per held press (hold DPAD_DOWN with `adb shell input keyevent --longpress`, or
  `input keyevent` repeated, and count lines), `frames` window cadence (about 6 lines per
  minute per active screen, correct `scr` across a navigation), `mem` once per foreground
  minute and absence in background (`input keyevent HOME`), `onTrimMemory` via
  `adb shell am send-trim-memory <pkg> MODERATE`, volume under the cap (with `meta dropped` if
  `key_lat` floods, which is expected here), no new files with the toggle off, runtime toggle
  with the Activity already resumed, and the clock-base sanity check from decision 4 (every
  `lat_ms` positive and under a few seconds).
- Relative checks are allowed on one emulator and one build: cold-start median off vs on (S1's
  A/B, +74 ms accepted as noise), `frames` p50 on vs off within host-load noise. State the load.
- Minified `devBenchmarkRelease` once for AC-S2-R8.
- A real-TV pass (key latency under hardware GPU, true jank) is owner/MANUAL, needs the owner's
  authorization for a physical device; list it under MANUAL, not as a blocker.

## Open questions

1. **`slow_frame` lines** (catalogue: individual frames over 100 ms, at most 20 per window).
   Recommended: leave out of S2. AC-12 is satisfied by the `max` field, and slow-frame lines would
   add a fifth event type and a second per-frame path in the callback. Add later if the
   S5 summary tool proves `max` is not enough. Blocks: no.
2. **`mem` on screen change** (catalogue lists it; AC-14 asks only for minute plus trim).
   Recommended: not in S2; `mem` lines carry `scr` (the last `nav.to`), which attributes growth
   per screen well enough at one line per minute, and a PSS sample per navigation is the
   expensive thing the catalogue warned about. Blocks: no.
3. **`key_lat` fast path.** Principle 1/catalogue says answers within 50 ms are "counted in the
   frame window". Recommended: skip it in S2 (no extra fields on `frames`); the window already
   shows jank and the `key_lat` rate shows slow answers. Adding `key_n`/`key_slow` to `frames`
   changes its allow-list, so do it only if the owner wants a ratio. Blocks: no.

Decided without asking: key-code allow-list (privacy, decision 3); JankStats screen state is an
own volatile and not `PerformanceMetricsState` (allocation, R8); lazy window close, no timer.
