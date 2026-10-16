# Telemetry frames, keys and memory: JankStats windows, key bursts and latency, memory samples

Status: approved
Date: 2026-10-15
Slug: telemetry-frames-keys-memory
Kind: feature
Size: M
Tracks: perf

## Problem / Why

Slice S2 of `usage-telemetry-log` (series brief `.flow/runs/usage-telemetry-log/brief.md`). Adds the
smoothness, responsiveness and memory probes to the S1 session log sink.

### Classification

- **kind:** feature
- **size:** M. One goal (four probe families on the existing sink), but it crosses layers
  (Gradle catalogue, `MainActivity`, `core/telemetry`, the nav hook) and touches risk areas
  (D-pad input, startup/DI wiring, a new dependency, minified `release`). Not S: more than 3 files
  and a new dependency. Not L: nothing here is shippable on its own apart from the sink.
- **tracks:** `perf`. No `ui`: nothing the user sees changes (the Settings toggle exists from S1).
- **Device verification: yes** (D-pad input, startup wiring, build variants, runtime-only
  behaviour). Decided before looking at hardware.

### Findings (code)

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

### Design decisions the plan must carry (analyst recommendations)

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

## Out of scope

Later slices: `net`/`net_media` (telemetry-network), `play` and exit reasons (telemetry-player-exit),
the summary tool (telemetry-summary-tool); `slow_frame` lines, `mem` on screen change, sub-50 ms key
answers in the frames window, image-cache size in `mem`, total frame duration on API 31+ (decisions in
Decisions below).

## Design

Checked against the worktree `/home/user1/orca/workspaces/Puber/telemetry-frames-keys-memory` at
`cd16eb47`. Every `path:line` below was read there. Paths are relative to
`app/src/main/java/com/kino/puber/` unless they start with `app/`, `gradle/` or `docs/`.

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Dependency | Add `metricsPerformance = "1.0.0"` under `[versions]` in `gradle/libs.versions.toml` (block `:1-36`) and `androidx-metrics-performance = { group = "androidx.metrics", name = "metrics-performance", version.ref = "metricsPerformance" }` under `[libraries]` (`:50`). Add `implementation(libs.androidx.metrics.performance)` to `app/build.gradle.kts` next to Timber (`:408`). Plan task 1 compiles the dependency on its own, before any code uses it. | AGENTS.md: versions only come from the catalogue. The artifact is not in `~/.gradle-agents` (searched `caches/modules-2`: no `androidx.metrics`), so the first build has to download it. If it fails, the build is blocked; we do not fall back to a different version. |
| D2 | JankStats API shape | Use `JankStats.createAndTrack(window, listener)` with `OnFrameListener.onFrame(volatileFrameData)`. From `FrameData` read only `frameStartNanos`, `frameDurationUiNanos` and `isJank`. Never read `states`. | The executor overload in the brief (`createAndTrack(window, executor, listener)`) comes from the alpha API. 1.0.0 is believed to have only the two-argument form. **Unverified**: the jar is not cached. Task 1 settles it by compiling a one-line probe and records the real signature in the plan. |
| D3 | Where per-screen attribution comes from | A screen holder: `@Volatile internal var screen: Class<*>?` in `object Telemetry` (`core/telemetry/Telemetry.kt:19-58`). It is written in `recordNav` (`core/ui/navigation/component/NavTelemetry.kt:21-24`) after its existing `isEnabled` check, as `Telemetry.screen = telemetryClassOf(to)`, except when `cmd == NavCmd.ACTIVITY`, because an external activity is not our window. Probes read the holder when an event is built and put the `Class` into the event. `detach` resets the holder to `null`. | Both `FlowComponent` collectors (`FlowComponent.kt:271,751`) and the tab collector (`TabComponent.kt:111`) already go through `recordNav`, so `FlowComponent.kt` (a hot file) is not edited. We do not use `PerformanceMetricsState`, because it allocates a states list per frame and the screen would be named by a string. Resetting the holder on detach means an off-then-on cycle never reuses a stale screen: it writes `scr:""` until the next navigation instead of a wrong name. |
| D4 | Writer `scr` for the new types | New marker `internal sealed interface ScreenScoped { val screen: Class<*>? }` in `TelemetryEvent.kt`, implemented by `Frames`, `Key`, `KeyLat` and `Mem`. `TelemetryWriter.append` (`core/telemetry/TelemetryWriter.kt:109-115`) gets one branch, `is ScreenScoped -> names.owner(event.screen)`, before `else -> lastScr`. `lastScr` keeps being set only by `Nav` (`:126`). | A frames window that closes after a navigation, or later at the next frame, must keep the screen it was measured on (AC-12). `mem` gets the same treatment so all four probe types follow one rule. `names.owner(null)` already returns `""` (`TelemetryNames.kt:11-14`). This is the only change to the writer. |
| D5 | Events, wire fields and caps | Four sealed members in `TelemetryEvent.kt` (`:54-115`) and four `TelemetryEventType` entries (`:13-21`) with caps `FRAMES 12`, `KEY 120`, `KEY_LAT 60`, `MEM 10`. Encoder branches go in `TelemetryEncoder.encode` (`core/telemetry/TelemetryEncoder.kt:37-92`). Wire fields beyond the envelope `t,sid,type,scr`: `frames` = `frames, janky, p50, p90, p99, max` (whole ms). `key` = `key, n, dur_ms`. `key_lat` = `key, lat_ms, frame_ms`. `mem` = `java_kb, java_max_kb, native_kb` plus `pss_kb` (periodic lines only) or `trim` (trim lines only). | The exhaustive `when` and the parameterised field-set test (`app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryEncoderTest.kt:23-57`) enforce the allow-list. Over-cap events go through the existing `dropped` path (`TelemetryWriter.kt:86-98`). `key` on `key_lat` costs nothing and separates D-pad answers from Back answers. |
| D6 | Key-code privacy (AC-11a) | `enum class KeyName(val wire: String)`: `DPAD_UP/DOWN/LEFT/RIGHT/CENTER`, `ENTER` (also `NUMPAD_ENTER`), `BACK`, `MENU`, `MEDIA_PLAY_PAUSE`, `MEDIA_PLAY`, `MEDIA_PAUSE`, `MEDIA_STOP`, `MEDIA_FAST_FORWARD`, `MEDIA_REWIND`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`, `CHANNEL_UP/DOWN`, `PAGE_UP/DOWN`, `MOVE_HOME/END`, `OTHER`. `fun keyNameOf(code: Int): KeyName` (a `when` on `KeyEvent.KEYCODE_*` constants) runs on the main thread when a burst opens. The events carry the enum, never the int. | The raw code never enters an event, so no encoder path can leak it. `KEYCODE_*` are compile-time constants, so a JVM test can pass every letter and digit code through `keyNameOf` and the encoder without Robolectric (none is configured: `app/build.gradle.kts:411-415`). |
| D7 | Key probe hook | Override `dispatchKeyEvent` in `MainActivity` (class at `MainActivity.kt:27`, no key override today): `if (!Telemetry.isEnabled) return super.dispatchKeyEvent(event)`. When on: `val startNs = System.nanoTime(); val handled = super.dispatchKeyEvent(event); KeyProbe.onKey(event.action, event.keyCode, event.repeatCount, event.eventTime, startNs, handled); return handled`. `KeyProbe` is a process-wide `object` that holds a main-thread-only `KeyBurstMachine` and a shared `KeyLatencyMatcher`. | Off is one volatile read and the original call. On, the probe runs after `super`, so it adds nothing to the time it measures, and it knows whether anything consumed the key (D9). It takes primitives, not `KeyEvent`, so it can be unit tested on the JVM. |
| D8 | Key bursts (AC-11) | `KeyBurstMachine` (plain class, main thread only, primitive fields `code, name, screen, startMs, lastMs, n`). `DOWN` with `repeat == 0` closes any open burst and opens a new one with `n = 1`. `DOWN` with `repeat > 0` and the same code does `n++` and `lastMs = eventTime`. `UP` with the same code emits `Key(t, screen, name, n, durMs = upEventTime - startMs)`. A different code, an idle gap over 2 000 ms (checked on the next event), `onPause` or detach also emits, with `durMs = lastMs - startMs`. Emitting = `Telemetry.record(TelemetryEvent.Key(...))`, one allocation per press. | One line per press or burst. All the time values are `KeyEvent.eventTime` (uptime ms), so there are no extra clock reads. Pause and detach come from the lifecycle callback on the main thread, so the machine needs no lock. |
| D9 | Key-to-frame latency (AC-13) | `KeyLatencyMatcher` holds one pending measurement in two `AtomicLong`s (`eventMs`, `dispatchNs`), with `0` meaning empty, plus `@Volatile` `name` and `screen`. It is armed on the main thread only for `DOWN`, `repeat == 0`, `handled == true`, when nothing is pending (`compareAndSet(0, …)`). It is consumed on the FrameMetrics thread in the frame callback: the answering frame is the first with `frameStartNanos >= dispatchNs`. `lat_ms = (frameStart + frameDurationUi) / 1e6 - eventMs`, emitted only when `> 50`. A frame starting more than 1 000 ms after `dispatchNs` drops the pending entry silently. Repeats never re-arm. | Matching on frame start after dispatch is stricter than the brief's "frame end after key time": a frame already in progress when the key arrived cannot reflect it, and `eventTime` is earlier than dispatch. Arming only on a handled key, plus the 1 s expiry, keeps edge presses with no visible change from being "answered" by an unrelated frame seconds later. A stall *before* dispatch is still counted, because `lat_ms` starts at `eventTime`. A stall after dispatch longer than 1 s shows up in `frames.max`. Clock base: `KeyEvent.eventTime` (uptime) and `frameStartNanos` (`System.nanoTime`) are both CLOCK_MONOTONIC on Android. **Unverified**: the first device read must show every `lat_ms` positive and under 1 s. |
| D10 | Frame aggregation (AC-12) | `FrameAggregator` (plain class, JVM-testable): `IntArray(101)` histogram (1 ms buckets 0..99, bucket 100 for `>= 100 ms`), `frames`, `janky`, `maxNs`, `windowStartNs`, `lastEndNs`, `screen`. `@Synchronized onFrame(startNs, uiNs, jank, screen)` closes the open window first when `screen !== this.screen` or `startNs - windowStartNs >= 10 s`, then adds the frame. `@Synchronized close()` is called from pause and detach on the main thread. Close: percentiles by a cumulative walk (the bucket index in whole ms; a percentile that lands in the overflow bucket reports `max`), `max = maxNs / 1e6`. Emits one `Frames` only if `frames > 0`, with `t = Telemetry.nowMs() - (System.nanoTime() - lastEndNs) / 1e6`, then zeroes the arrays in place. | Per frame there is no allocation, no string and no `Telemetry` call: only array and field writes. The monitor is uncontended except on pause. A lazy close with no timer means an idle screen costs nothing. Back-dating `t` to the last frame keeps a window that closes late (after a long idle gap) at the time it was measured. The frame time is UI-thread work only: on API < 31 `FrameData` has no total duration. The spec has to say so. |
| D11 | Frame probe lifecycle | `FrameProbe` (Android glue) owns the `FrameAggregator` and the `JankStats?`. `start(activity)`, main thread: `JankStats.createAndTrack(activity.window, listener)`. `stop()`, main thread: `isTrackingEnabled = false`, drop the reference, `aggregator.close()`. The listener's first line is `if (!Telemetry.isEnabled) return`, then `KeyProbe.matcher.onFrame(...)`, then `aggregator.onFrame(..., Telemetry.screen)`. A new JankStats is created on every resume and released on every pause. | Tracking is tied to the resumed window. Activity recreation (config or locale change) produces pause, a new Activity, then resume, so no tracker survives on an old window. The late-callback guard covers frames that FrameMetrics delivers after stop. |
| D12 | Lifecycle callbacks and runtime toggle | Replace `PauseFlushCallbacks` (`core/telemetry/TelemetryRuntime.kt:143-154`) with `TelemetryActivityCallbacks`, registered at the same place (`:123`). Always: track `resumedActivity: Activity?` (set on resume, cleared on pause and destroy; a plain field with no allocation and no `WeakReference`) and `startedCount`. Only when `Telemetry.isEnabled`: on resume call `frameProbe.start`; on pause call `frameProbe.stop`, `KeyProbe.flush()`, then `sink.requestDrain()`, in that order; on start and stop call `memoryProbe.onForeground(startedCount > 0)`. | Plant at toggle time needs the Activity that is already resumed (the brief's runtime-toggle risk). Holding a strong reference only between resume and pause cannot leak. Closing the windows before the drain means the pause flush writes them. Off cost: two field writes per lifecycle event, which already happens through S1's registered callback. |
| D13 | Planting and threading | New `internal interface TelemetryProbe { fun attach(); fun detach() }`, both idempotent. `TelemetryRuntime` gets a constructor parameter `probes: List<TelemetryProbe> = emptyList()` and `mainPost: (Runnable) -> Unit = { it.run() }`. `plantProbes` (`:91-94`) adds `mainPost { probes.forEach(TelemetryProbe::attach) }`. `detachProbes` (`:97-102`) adds `mainPost { probes.forEach(TelemetryProbe::detach) }` and `Telemetry.screen = null`. `install` (`:109-124`) creates the `ThreadTelemetryExecutor` once, shares it between the sink and `MemoryProbe`, and passes `Handler(Looper.getMainLooper())::post`. A single `ProbeSet` adapter attaches the frame probe (start if `callbacks.resumedActivity != null`) and the memory probe. | `detachProbes` also runs on the writer thread (`onExpired`, `onStopped`: `TelemetrySink.kt:223,234`). JankStats and component callbacks need the main thread. Posting keeps the order (on, off, on ends on). The defaults leave `TelemetryRuntimeTest` (`app/src/test/.../TelemetryRuntimeTest.kt`) compiling and synchronous. |
| D14 | Memory sampling (AC-14) | `MemoryProbe(executor, sampler, registerTrim, unregisterTrim)`. `attach` registers a `ComponentCallbacks2` and, if in the foreground, starts the timer. `onForeground(true)` calls `executor.scheduleRepeating(60_000) { sample(withPss = true, trim = NONE) }` (`core/telemetry/TelemetryExecutor.kt:10,36-44`). `onForeground(false)` and `detach` close the timer and unregister. `onTrimMemory(level)` on main runs `executor.execute { sample(withPss = false, trim = level) }` and ignores `TRIM_MEMORY_UI_HIDDEN`. The sampler, on the writer thread: `Runtime` used and max, `Debug.getNativeHeapAllocatedSize()`, `Debug.getMemoryInfo(reused)` and `.totalPss`, with one reused `Debug.MemoryInfo`. It records `Mem(t, Telemetry.screen, ...)`. | PSS never runs on main or on the FrameMetrics thread. The timer restarts on each foreground entry, so a foreground minute gives exactly one periodic line and the background gives none. `UI_HIDDEN` fires on every backgrounding and is a visibility signal, not memory pressure: logging it would put a line in the background (AC-14). The sampler is injected, so the JVM test needs no `android.os.Debug`. |
| D15 | Image-cache size | Not in S2 (see Open questions). | `javap` of Coil 3.5.0 (`coil-android-3.5.0`) shows that `SingletonImageLoader` exposes only `get(Context)`, which builds the loader if it does not exist yet. Reading the size from the writer thread could make telemetry create the app's image loader. AC-14 does not ask for the field. |
| D16 | R8 | No new keep rule. `scr` comes from screen classes, which `app/proguard-rules.pro:90` already keeps by name. `KeyName` wire strings are constructor arguments, not enum names. The JankStats listener is a direct call with no reflection. JankStats ships its own consumer rules. Device check per AC-S2-R8 on `devBenchmarkRelease`. | AC-S2-R8 is met by the existing rules. A missing rule shows up as an obfuscated `scr`, which the device check catches. |

### Changes by layer

API / repository: none.

ViewModel state and actions: none.

Screen and DI module (process wiring; no Koin change, `core/telemetry/di/TelemetryModule.kt` unchanged):
- `MainActivity.kt:27`: add the `dispatchKeyEvent` override (D7). Nothing else changes; `onCreate` (`:42-61`) stays as it is.
- `core/ui/navigation/component/NavTelemetry.kt:21-24`: set the screen holder in `recordNav` (D3).
- `core/telemetry/Telemetry.kt:19-58`: add the `screen` holder, reset in `resetForTests` (`:52-57`).
- `core/telemetry/TelemetryRuntime.kt`: constructor parameters `probes`, `mainPost` (`:23-27`). Plant and detach of probes (`:91-102`). `install` shares one executor and builds `FrameProbe`, `MemoryProbe` and `TelemetryActivityCallbacks` (`:109-124`). `TelemetryActivityCallbacks` replaces `PauseFlushCallbacks` (`:143-154`).
- `core/telemetry/TelemetryEvent.kt`: four `TelemetryEventType` entries with caps (`:5-21`), `ScreenScoped`, `KeyName`, four sealed members (`:54-115`).
- `core/telemetry/TelemetryEncoder.kt:37-92`: four branches.
- `core/telemetry/TelemetryWriter.kt:110-115`: the `ScreenScoped` branch (D4).
- New files in `core/telemetry/`: `FrameAggregator.kt` (pure), `FrameProbe.kt` (JankStats glue), `KeyProbe.kt` (`KeyProbe` object, `KeyBurstMachine`, `KeyLatencyMatcher`, `keyNameOf`), `MemoryProbe.kt` (probe plus `MemorySampler` interface and `DebugMemorySampler`), `TelemetryProbe.kt` (interface, `TelemetryActivityCallbacks`).
- `PuberApp.kt`: unchanged (`TelemetryRuntime.install` at `:74` keeps its signature). `FlowComponent.kt` and `TabComponent.kt`: unchanged.

Compose content: none.

Resources and build:
- `gradle/libs.versions.toml` (`:1-36`, `:50`), `app/build.gradle.kts` (`:408` area): D1.
- `app/proguard-rules.pro`: unchanged (D16).

Tests (new or extended, all JUnit 5 in `app/src/test/kotlin/com/kino/puber/core/telemetry/`, fakes from `app/src/test/kotlin/com/kino/puber/util/`):
- `FrameAggregatorTest`: percentiles and exact `max` on a crafted feed; rollover at 10 s; close on screen change; close on `close()`; idle gap back-dates `t`; no event when empty. AC-12.
- `KeyBurstMachineTest`: long press gives one line with `n` and `dur_ms`; three presses give three lines with `n=1`; different key; 2 s timeout; pause. AC-11.
- `KeyLatencyMatcherTest`: under and over 50 ms; held key arms once; unhandled key never arms; frame starting before dispatch is skipped; 1 s expiry. AC-13.
- `KeyNameTest`: every `KEYCODE_A..Z`, `KEYCODE_0..9`, `NUMPAD_0..9` maps to `OTHER`, and the encoded line contains no raw code. AC-11a.
- `MemoryProbeTest`: one line per 60 s in the foreground (`ManualTelemetryExecutor`), none in the background, a trim line with its level, `UI_HIDDEN` ignored, the sampler runs on the writer thread (real `ThreadTelemetryExecutor`, as `ThreadTelemetryExecutorTest` does). AC-14, AC-S2-NODIST.
- `TelemetryEncoderTest` (`:23-57`): add the four types to the field-set table.
- `TelemetryWriter` scr test: a `Frames` written after a `Nav` keeps its own `scr`. AC-12.
- `TelemetryRuntimeTest`: probes attach on enable, detach on disable, on auto-off and on storage failure; holder reset. With the flag off, recording events produces nothing. AC-S2-OFF.

### Alternatives rejected

- `PerformanceMetricsState` for the screen: it allocates a states list per frame and names the screen by a string; rejected (D3).
- One `frames` window flushed by a 10 s timer: it wakes up while idle and would need a lock on every frame; a lazy close is enough (D10).
- Writer-side `lastScr` for frames and keys: it mislabels windows that close after a navigation (D4).
- Key bursts folded on the writer thread (an `EventFolder` like `ActFolder`, `EventFolder.kt:11-41`): it would need one event per repeat on main; the machine on main emits once per press.
- Frame-end-after-key-time matching (brief decision 4): it counts frames that were already running when the key arrived (D9).
- `Choreographer.postFrameCallback` per key: it schedules vsync work that would not otherwise happen and does not see the frame's work; JankStats already delivers the frame.
- `ActivityManager.getProcessMemoryInfo`: it is rate-limited from API 29 and goes through binder; `Debug.getMemoryInfo` is in-process.
- A `WeakReference` to the resumed Activity: it allocates on every resume while off; a field cleared on pause is enough (D12).
- Editing `FlowComponent.kt` to write the holder while the log is off: it is a hot file shared with two runs in `impl`; holder reset plus `""` until the next navigation is enough (D3).

### Risks (highest first)

1. **Distorting the measurement** (main thread, FrameMetrics thread). It would live in `MainActivity.dispatchKeyEvent`, the JankStats listener in `FrameProbe.kt`, `FrameAggregator.onFrame` and `DebugMemorySampler`. Mitigation: D7 (probe after `super`, primitives only), D10 (no allocation per frame, uncontended monitor), D14 (PSS only on `puber-telemetry`). The cost of `Debug.getMemoryInfo` is unknown. Measure it once on the device (time around one sample on the writer thread). If it takes more than a few ms on a `low_ram` device, skip PSS there.
2. **D-pad path regression** (`MainActivity.dispatchKeyEvent`). The override must return exactly what `super` returns, on both branches. If it returned `false` or `true` itself, it would break Compose focus and `AppRemoteHotkeyHandler` (`onKeyEvent` hook at `FlowComponent.kt:141-146`). Code review plus a device check that D-pad navigation and Back behave the same with logging on and off.
3. **Runtime toggle and lifecycle**. Lives in `TelemetryRuntime.plantProbes`, `detachProbes` and `TelemetryActivityCallbacks`. A detach run from the writer thread must not touch JankStats off main (D13 posts it). If enabled while the Activity is resumed, tracking must start at once, through `resumedActivity`. If enabled while backgrounded, it starts at the next resume. A JankStats instance left on a destroyed window is prevented by the pause-time stop (D11).
4. **Clock bases and false matches for `key_lat`**. Lives in `KeyLatencyMatcher`. Uptime and `nanoTime` are believed to share a base. This is unverified, and the first device read checks it. Frames delivered late around a navigation can be attributed to the next screen for one or two frames (D10 reads the holder at callback time). This is accepted and stated in the spec.
5. **Software-GPU emulator**. Every frame is janky and slow, so `key_lat` floods into `dropped` and absolute numbers mean nothing. If the emulator window is not hardware-accelerated, JankStats 1.0.0 records nothing (`research.md: 5`, beta03 change), and AC-12 cannot be shown there. The first device step checks that `frames` lines appear at all. If none appear, escalate; do not weaken the code.
6. **Dependency resolution**. `metrics-performance:1.0.0` is not cached. If the download fails, task 1 is blocked. The API shape (D2) is fixed only after it compiles.
7. **Memory growth and leaks**. `FrameProbe` keeps no Activity. `TelemetryActivityCallbacks.resumedActivity` is cleared on pause and destroy. `MemoryProbe` keeps one `Debug.MemoryInfo`. The `ComponentCallbacks2` is unregistered on detach (AC-S2-OFF).
8. **Privacy**. A raw key code could reconstruct typed text. D6 keeps the int out of every event, and AC-7's device scan still applies. `scr` is a class name only.
9. **R8**. The probe names are already kept. A `devBenchmarkRelease` device check confirms it (AC-S2-R8).
10. **Merge friction**. `MainActivity.kt` gets one small method. `PuberApp.kt`, `FlowComponent.kt` and `TabComponent.kt` are untouched.
11. **Baseline profile**. `dispatchKeyEvent` is new on a hot path. Off, it is one flag check. No profile regeneration is needed for S2.

### Open questions

1. **Image-cache size in `mem`** (in the catalogue, not in AC-14). Recommended: leave it out of S2 and add it when `coil-shared-http` or a later run gives a non-creating handle to the loader (for example, `PuberApp` exposing the `MemoryCache` it builds in `newImageLoader` at `PuberApp.kt:100-126`). Blocks: no.
2. **The frame time is UI-thread work only**, because there is no total duration below API 31. Recommended: say so in the spec and the field note, and on API 31+ do not switch to `frameDurationTotalNanos` in S2, so one metric means the same on every device. Blocks: no.

## Test design

Inputs: `brief.md` (AC-11, AC-11a, AC-12, AC-13, AC-14, AC-S2-OFF, AC-S2-NODIST, AC-S2-R8), `design-architect.md`
(D1-D16, risks 1-11), `state.md` (Q1-Q3 answered: no `slow_frame`, no `mem` on screen change, no key fast path),
S1 test design (`docs/superpowers/specs/2026-10-15-telemetry-core.md`, "Test design"), the S1 test seams in
`app/src/test/kotlin/com/kino/puber/core/telemetry/` and `.../util/`, `.flow/roles/test-designer.md`.
Base `cd16eb47`. Kind is feature, so there is no failing-first bug test.

`{run}/baseline.md` did not exist when this was written (qa captures it in parallel). The perf section below states
what it must contain; the numbers are filled in from it.

### Levels and conventions

- `unit`: JUnit 5 under `app/src/test/kotlin/com/kino/puber/core/telemetry/`. Fakes reused: `SinkHarness`
  (`TelemetryTestSupport.kt`), `FakeTelemetryClock`, `FakeTelemetryStore`, `ManualTelemetryExecutor`,
  `FakeSharedPreferences`; the real `ThreadTelemetryExecutor` only where thread identity is the thing under test
  (as `TelemetrySinkTest.encodingAndFileIoRunOnlyOnWriterThread` does). mockk only for collaborators that cannot
  exist on the JVM (an `Activity`), never for the class under test.
- `device`: manual observation on a leased emulator, recorded in `docs/superpowers/evidence/<date>-telemetry-frames-keys-memory-device.md`.
- `static`: grep, detekt, lint, `devBenchmarkRelease` build.
- No `instrumented` test is added: there is no UI change and no Robolectric, and JankStats needs a real window, so
  the glue is covered by a fake tracker (below) plus the device pass.

#### Rules from the S1 review (every test below must satisfy them)

1. **No vacuous asserts.** An "off / none / no line" assertion is always paired, in the same test or its sibling
   with the same setup, with an "on" control that proves the same sequence does produce output. A test that
   asserts `lines.isEmpty()` without such a control does not count.
2. **No fixtures that hide the path.** Use values that distinguish the right answer from the wrong one: the
   key-code sentinel values `n=1`, `dur_ms=100000` cannot collide with any `KEYCODE_*` int; frame feeds put the
   p90 and p99 in different buckets; a screen-change test uses three different screens, not two.
3. **Origin filter `t >= origin`.** Every test that reads `t` from written lines asserts `t >= 0` (session
   origin) and, for back-dated windows, `t <= now`. The S1 bug was a filter that accepted events before the
   origin; here the risk is the `Frames` back-dating (D10) producing a `t` before the sink was enabled.
4. **Global reset.** Every test class that installs a sink, sets `Telemetry.screen`, or touches `KeyProbe`
   state resets in `@AfterEach`: `Telemetry.resetForTests()` (which per D3 also clears `screen`), plus
   `KeyProbe` state reset (see seams), `Timber` forest and default uncaught handler restore where the class
   already does so (`TelemetryRuntimeTest.tearDown` is the template).
5. **Behaviour through the public surface.** Tests assert emitted events, written lines and calls on injected
   collaborators; never a private field or an internal call order other than the documented one (pause order, D12).

#### Seams the implementation must offer

The design names the classes but a few seams are needed to make the tests possible. The plan must carry them.

| Seam | Needed by |
|---|---|
| `FrameAggregator(emit: (TelemetryEvent.Frames) -> Unit, nowMs: () -> Long, nanoTime: () -> Long)`; production passes `Telemetry::record`, `Telemetry::nowMs`, `System::nanoTime` | `FrameAggregatorTest` |
| `KeyLatencyMatcher.arm(eventMs, dispatchNs, name, screen, handled)` / `onFrame(startNs, uiNs, emit)` as pure methods with an `emit` callback | `KeyLatencyMatcherTest` |
| `KeyBurstMachine(emit: (TelemetryEvent.Key) -> Unit)`, methods take primitives (D7/D8) | `KeyBurstMachineTest` |
| `KeyProbe` internal `resetForTests()` (burst machine and matcher back to empty) | rule 4 |
| `internal inline fun dispatchWithProbe(enabled: Boolean, superDispatch: () -> Boolean, probe: (handled: Boolean) -> Unit): Boolean`, called from `MainActivity.dispatchKeyEvent` and holding all of its logic | the D-pad no-regression test; without it `MainActivity` needs an Activity to test |
| `FrameProbe(tracker: FrameTrackerFactory, aggregator, matcher)` where `FrameTrackerFactory.create(activity, listener): FrameTracker` and `FrameTracker.isTrackingEnabled` are a tiny interface; production wraps `JankStats.createAndTrack` | `FrameProbeTest` (JankStats cannot run on the JVM) |
| `MemoryProbe(executor, sampler, registerTrim, unregisterTrim)` as D14; `MemorySampler.sample(withPss: Boolean, trim: Int)` | `MemoryProbeTest` |
| `ManualTelemetryExecutor` records the `periodMs` of each `scheduleRepeating` and exposes `repeatingCount` (test util change; owner: implementer of tests) | AC-14 period 60 000 |
| `TelemetryRuntime(probes = fake list, mainPost = queueing fake)` as D13 | lifecycle tests |
| `TelemetryActivityCallbacks(runtimeState, frameProbe, keyFlush, sink.requestDrain, memoryProbe)` taking those as constructor arguments | `TelemetryActivityCallbacksTest` |

### Risk profile

P = probability 1-3, I = impact 1-3. Score 6 or more needs a test at the level where it would fail.

| # | Risk | P | I | Score | Where it would fail | Covered by |
|---|---|---|---|---|---|---|
| R1 | Main-thread or FrameMetrics-thread work distorts the measurement (allocation per frame, file IO or PSS on a hot thread) | 2 | 3 | 6 | `FrameAggregator.onFrame`, `DebugMemorySampler`, `KeyProbe` | F9, F10, M7, M8, K13, device D8, D9 |
| R2 | `dispatchKeyEvent` returns something other than `super`'s result, or calls `super` twice or not at all: D-pad, Back and `AppRemoteHotkeyHandler` break | 2 | 3 | 6 | `MainActivity` | K1-K4 unit, device D-pad A/B (D5) |
| R3 | Runtime toggle: tracking not started on an already-resumed Activity, not stopped on off, probe detach run on the writer thread touching JankStats, stale tracker on a recreated window | 2 | 3 | 6 | `TelemetryRuntime.plantProbes/detachProbes`, `TelemetryActivityCallbacks`, `FrameProbe` | L1-L12, device D6 |
| R4 | Raw key code reaches a line and reconstructs typed search text (AC-7) | 2 | 3 | 6 | `keyNameOf`, `TelemetryEncoder` | N1-N5, device D11 (scan) |
| R5 | `frames` window labelled with the wrong screen (writer `lastScr`, or holder read at close) | 3 | 2 | 6 | `TelemetryWriter.append`, `FrameAggregator` | W1-W5, F6, F7 |
| R6 | JankStats records nothing on the software-GPU emulator, so AC-12 cannot be shown on the only available device | 2 | 3 | 6 | environment | device D2 gate; unit tests keep the logic proven regardless |
| R7 | `metrics-performance:1.0.0` does not resolve, or the API shape differs (D2) | 2 | 3 | 6 | Gradle | plan task 1 compile; `FrameProbeTest` compiles against the real factory seam |
| R8 | Clock bases of `KeyEvent.eventTime` and `frameStartNanos` differ: `lat_ms` negative or huge, false `key_lat` lines | 3 | 2 | 6 | `KeyLatencyMatcher` | C1-C12 (logic), device D7 (the only place the base can be checked) |
| R9 | Tests pass while the behaviour is broken (S1 lesson) | 2 | 3 | 6 | the tests | conventions above; mutation spot-checks (below) |
| R10 | `Frames` back-dated `t` precedes the session origin or exceeds now | 2 | 2 | 4 | `FrameAggregator.close` | F8, F9b (still P0: it was the S1 bug class) |
| R11 | R8 minification obfuscates `scr` or strips the listener | 2 | 2 | 4 | release build | device D12 (AC-S2-R8); existing `ProguardRulesTest` keeps its guard |
| R12 | Window or memory leak: `resumedActivity` held after pause, trim callback never unregistered | 2 | 2 | 4 | `TelemetryActivityCallbacks`, `MemoryProbe` | L8, L10, M9 |
| R13 | `key_lat` floods on the software GPU into the cap and `meta dropped` | 3 | 1 | 3 | environment | cap test X2; device D8 expects it, not a defect |
| R14 | Trim-memory semantics: AC-14 says "no `mem` in the background" and "an extra `mem` line on `onTrimMemory`", but `MODERATE` and `BACKGROUND` are delivered only to background processes | 2 | 2 | 4 | `MemoryProbe.onTrimMemory` | M5, M6 pin D14's reading; see "Spec gaps" |
| R15 | Late `FrameMetrics` callbacks after `stop()` or after toggle-off emit lines or arm matcher | 2 | 2 | 4 | listener guard | FP5, FP6 |
| R16 | Concurrent arm (main) and answer (FrameMetrics thread) double-emit or lose a pending entry | 1 | 2 | 2 | `KeyLatencyMatcher` | C13 (P2 stress), accepted otherwise |
| R17 | Baseline profile stale for `dispatchKeyEvent` | 1 | 1 | 1 | accepted, no test (D11 of risks) | none |

#### Spec gaps the tests pin (architect or planner to confirm; tests follow the stated reading)

1. **Rounding of ms fields.** D10 says whole ms: percentiles are the bucket index (floor), `max = maxNs / 1e6`
   (integer division, floor). F2 pins floor. If the plan chooses rounding, F2 changes, nothing else.
2. **Back-dated `t` lower bound.** D10 computes `t = nowMs - (nanoTime - lastEndNs)/1e6` with no clamp. A late
   `FrameMetrics` callback delivered after enable could yield `t` below the sink origin. F9b requires a clamp to
   `t >= 0` (origin) and the tests are written to fail without it.
3. **Trim in the background.** D14 runs a trim line unconditionally; AC-14's "no `mem` in the background" is read
   as "no periodic line". M6 pins that reading (a `MODERATE` trim in background is written, periodic is not).
   The device scenario uses `send-trim-memory MODERATE` (a background level) accordingly.
4. **Repeat without an open burst** (DOWN with `repeat > 0` and no burst open, for example logging enabled in the
   middle of a hold). K10 pins: it opens a burst with `n=1` and counts onward; it must not throw or emit
   a line with `n=0`.
5. **Unhandled `super` exception** in `dispatchWithProbe`: K4 pins that it propagates and the probe is not
   called (the key was not dispatched).

### Traceability

Test names are Kotlin method names. Classes are in `app/src/test/kotlin/com/kino/puber/core/telemetry/` unless
marked. P0 must exist before review, P1 should, P2 nice to have.

#### AC-11: key bursts

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-11 | unit | P0 | K5 `KeyBurstMachineTest.longPressEmitsOneKeyWithCountAndDurationOnUp` | DOWN r0, then r1..r9, UP: the emit list is empty after the DOWNs (asserted before the UP) and has exactly one `Key(name=DPAD_DOWN, n=10, durMs=up-start)` after the UP |
| AC-11 | unit | P0 | K6 `KeyBurstMachineTest.threeSinglePressesEmitThreeKeysWithNOne` | three DOWN/UP pairs: three events, each `n=1`, `durMs` equals each UP minus its DOWN (different durations 40, 90, 15 so a shared value fails) |
| AC-11 | unit | P0 | K7 `KeyBurstMachineTest.differentKeyClosesOpenBurstWithDurationFromLastRepeat` | UP held 4 repeats, then DOWN of another key: burst 1 emitted with `n=4`, `durMs = lastRepeat - start`, and the new burst stays open (no emit yet) |
| AC-11 | unit | P0 | K8 `KeyBurstMachineTest.idleGapAboveTwoSecondsEmitsOnNextEventAndBoundaryDoesNot` | gap of 2000 ms keeps the burst; 2001 ms closes it with `durMs = lastMs - startMs`; the emitting event then opens its own burst |
| AC-11 | unit | P0 | K9 `KeyBurstMachineTest.flushEmitsOpenBurstOnceAndEmptyFlushEmitsNothing` | pause/detach flush emits the open burst once; a second flush and a flush with no burst emit nothing |
| AC-11 | unit | P1 | K10 `KeyBurstMachineTest.repeatWithoutOpenBurstOpensOneWithNOneAndNeverThrows` | spec gap 4 |
| AC-11 | unit | P1 | K11 `KeyBurstMachineTest.upOfAKeyThatIsNotOpenIsIgnored` | stray UP emits nothing and does not close the open burst of another key |
| AC-11 | unit | P0 | K12 `KeyBurstThroughSinkTest.heldDpadWritesOneKeyLineWithFields` | `SinkHarness` + installed sink + machine wired to `Telemetry.record`: after drain the store holds one `type=key` line, field set exactly envelope + `{key,n,dur_ms}`, `key == "dpad_down"` wire, `n == 10`, `dur_ms` as fed, `t >= 0`; control: the same feed with the sink off writes no `key` line |
| AC-11 | unit | P1 | K13 `KeyBurstMachineTest.repeatsAfterTheFirstDoNotAllocate` | `ThreadMXBean` allocated-bytes delta over 100 000 repeats stays under 64 KB; negative control inside the test (a loop allocating a `Pair` per iteration exceeds the bound) so the check cannot pass vacuously; skipped with `Assumptions` on a JVM without the bean |
| AC-11 | unit | P0 | K1 `DispatchWithProbeTest.offCallsSuperOnceAndNeverTheProbe` | `enabled=false`, `super` returns `true`, then `false`: returns the same value, `super` called once, probe zero calls |
| AC-11 / R2 | unit | P0 | K2 `DispatchWithProbeTest.onReturnsSupersValueAndProbesAfterSuper` | `enabled=true`, `super` true then false: same return value; call log is `[super, probe]` in that order; probe receives `handled` equal to `super`'s value |
| AC-11 / R2 | unit | P0 | K3 `DispatchWithProbeTest.probeThatThrowsCannotChangeTheReturnValueOrBreakDispatch` | probe throws: dispatch still returns `super`'s value (the key path must survive a telemetry bug); fails if the probe is not guarded |
| AC-11 / R2 | unit | P1 | K4 `DispatchWithProbeTest.superThatThrowsPropagatesAndProbeIsNotCalled` | spec gap 5 |

#### AC-11a: key-code allow-list

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-11a | unit | P0 | N1 `KeyNameTest.everyLetterDigitAndNumpadCodeMapsToOther` | `@ParameterizedTest` over `KEYCODE_A..Z`, `KEYCODE_0..9`, `KEYCODE_NUMPAD_0..9`, plus `KEYCODE_SPACE`, `KEYCODE_DEL`, `KEYCODE_UNKNOWN`, `0`, `9999`, `-1` |
| AC-11a | unit | P0 | N2 `KeyNameTest.allowListIsExactlyTheDocumentedCodes` | loop over codes `0..400`: the code maps to a non-`OTHER` name iff it is in the explicit expected map (D-pad x5, ENTER and NUMPAD_ENTER to `enter`, BACK, MENU, six media keys plus NEXT/PREVIOUS, CHANNEL_UP/DOWN, PAGE_UP/DOWN, MOVE_HOME/END) and each maps to its expected wire string; a code added to the allow-list without a test row fails |
| AC-11a | unit | P0 | N3 `KeyNameTest.encodedKeyAndKeyLatLinesNeverContainTheRawCode` | for every code of N1, build `Key(n=1, durMs=100000)` and `KeyLat(latMs=77777, frameMs=88888)` with `keyNameOf(code)`, encode, and assert no value of the JSON equals the code as number or string; sentinel values chosen so `n`/`dur` cannot equal a code (rule 2); `key == "other"` |
| AC-11a | unit | P1 | N4 `KeyNameTest.wireNamesAreUniqueAndNeverASingleLetterOrDigit` | `KeyName.entries.map(wire)` has no duplicate except none, and no wire has length 1 and none is all digits |
| AC-11a | unit | P1 | N5 `KeyNameTest.eventsCarryTheEnumNotAnInt` | reflection over the `Key` and `KeyLat` constructors: no `Int` parameter named `code`/`keyCode` (the structural guard for D6) |
| AC-11a | device | P0 | D11 below | an AC-7 style scan of the device session finds none of the characters typed into search |

#### AC-12: frame windows

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-12 | unit | P0 | F1 `FrameAggregatorTest.percentilesAndExactMaxOnCraftedFeed` | feed 100 frames: 90 x 8 ms, 9 x 20 ms, 1 x 250 ms, 12 flagged janky: window emits `frames=100`, `janky=12`, `p50=8`, `p90=8`, `p99=20`, `max=250` (p99 differs from p90 and from max, so a wrong rank or off-by-one fails) |
| AC-12 | unit | P0 | F2 `FrameAggregatorTest.percentileInOverflowBucketReportsExactMaxAndSubMsFloors` | 98 x 8 ms + 150 ms + 300 ms: `p99 == max == 300`; a frame of 8 900 000 ns counts in bucket 8 and `max` of a 8.9 ms-only window is 8 (spec gap 1) |
| AC-12 | unit | P0 | F3 `FrameAggregatorTest.windowRollsOverAtTenSecondsInclusive` | frame at start +9 999 999 999 ns joins the window; a frame at exactly +10 000 000 000 ns closes it and opens the next; the closing event excludes the triggering frame, the next window counts it (`frames=1`) |
| AC-12 | unit | P0 | F4 `FrameAggregatorTest.secondWindowStartsFromZeroedHistogram` | window 1 all 90 ms frames, window 2 all 5 ms frames: window 2 `p99 == 5`, `max == 5`, `janky` counts only its own |
| AC-12 | unit | P0 | F5 `FrameAggregatorTest.closeEmitsOpenWindowOnceAndEmptyWindowEmitsNothing` | `close()` with 3 frames emits one event; second `close()` and `close()` on a fresh aggregator emit nothing (control: the first emits) |
| AC-12 | unit | P0 | F6 `FrameAggregatorTest.screenChangeClosesWindowWithTheOldScreen` | frames on `HomeScreen`, one on `HistoryScreen`, then `ScreenScreen`: two emitted events carry `HomeScreen` and `HistoryScreen` (identity `===` on the `Class`), frame counts split 5/1, the third window is still open |
| AC-12 | unit | P0 | F7 `FrameAggregatorTest.nullScreenIsAWindowOfItsOwn` | `screen=null` then a real screen: the null window is closed and carries `null` (written as `scr ""`), not the next screen |
| AC-12 | unit | P0 | F8 `FrameAggregatorTest.idleGapBackDatesTToTheLastFrameNotTheClose` | last frame at `now=T`, clock advances 30 s idle, next frame closes the window: `event.t == T` (within 1 ms), and `t < now`; fails if `t` is the close time |
| AC-12 | unit | P0 | F9 `FrameAggregatorThroughSinkTest.backDatedWindowNeverPrecedesTheSessionOrigin` | late `FrameMetrics` frame whose `lastEndNs` is older than the sink origin: after drain the written `t >= 0` and `t <= now` (spec gap 2; this is the S1 origin-filter lesson) |
| AC-12 | unit | P0 | F10 `FrameAggregatorTest.thousandFramesInsideAWindowCallEmitNeverAndAllocateNothing` | 1000 frames inside one window: `emit` call count 0 (only the close calls it); `ThreadMXBean` delta over 200 000 `onFrame` calls under 64 KB with a negative control as in K13; skipped on a JVM without the bean |
| AC-12 | unit | P0 | F11 `FrameAggregatorTest.listenerShapedCallsMatchGoldenEncodedLine` | window from F1 through `TelemetryEncoder` yields JSON with exactly envelope + `{frames,janky,p50,p90,p99,max}` and the expected numbers |
| AC-12 | unit | P0 | W1 `TelemetryWriterScrTest.framesKeepTheirOwnScreenAfterALaterNav` | `Nav(to=ScreenScreen)` recorded, then `Frames(screen=HomeScreen)`: the `frames` line `scr == "Home"`, not the nav target; control: an `Act` sibling still follows its own VM |
| AC-12 | unit | P0 | W2 `TelemetryWriterScrTest.keyKeyLatAndMemKeepTheirOwnScreen` | same for `Key`, `KeyLat`, `Mem` (parameterised on the four types) |
| AC-12 | unit | P0 | W3 `TelemetryWriterScrTest.screenScopedNullScreenWritesEmptyNotLastNav` | `Frames(screen=null)` after a nav: `scr == ""` (D4: `names.owner(null)`), not the last nav |
| AC-12 | unit | P0 | W4 `TelemetryWriterScrTest.screenScopedEventsDoNotMoveLastScreen` | after `Frames(screen=Other)`, a `meta` line still has the last `nav.to` as `scr` |
| AC-12 | unit | P0 | W5 `TelemetryWriterScrTest.navStillSetsLastScreenAndOldTypesUnchanged` | regression of S1 behaviour (`nav/ready/act` rules) next to the new branch |
| AC-12 | unit | P0 | F12 `NavTelemetryTest.recordNavSetsScreenHolderWhenOnAndNeverWhenOffOrActivity` | on: holder is the `to` class after `PUSH`; `ACTIVITY` command leaves the holder unchanged; off: the holder stays `null` after the same sequence (the on control is the first assertion) |
| AC-12 | unit | P1 | F13 `TelemetryTest.detachResetsScreenHolderToNull` | after disable, `Telemetry.screen == null`; re-enable writes `scr ""` until the next nav (no stale name) |
| AC-12 | device | P0 | D3 below | real `frames` line per active screen, window cadence, correct `scr` across a navigation |

#### AC-13: key-to-frame latency

Helper: `matcher.arm(eventMs, dispatchNs, name, screen, handled)` and `matcher.onFrame(startNs, uiNs, emit)`.
Times are chosen so `eventMs*1e6`, `dispatchNs` and frame times share one base, as D9 assumes.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-13 | unit | P0 | C1 `KeyLatencyMatcherTest.answerOverFiftyMsEmitsLatAndFrameMs` | key at 1000 ms, frame ends at 1070: one `KeyLat(latMs=70, frameMs=<that frame's ui duration>)` with the armed `key` |
| AC-13 | unit | P0 | C2 `KeyLatencyMatcherTest.fiftyIsNotSlowAndFiftyOneIs` | lat 49 and 50: no emit; 51: emit (boundary of `> 50`); each case followed by a control that the matcher is empty again |
| AC-13 | unit | P0 | C3 `KeyLatencyMatcherTest.latencyStartsAtEventTimeNotAtDispatch` | `eventTime` 30 ms before dispatch, frame ends 40 ms after dispatch: lat 70 emits (a version measuring from dispatch would see 40 and fail) |
| AC-13 | unit | P0 | C4 `KeyLatencyMatcherTest.frameStartingBeforeDispatchDoesNotAnswer` | frame with `start < dispatchNs` ignored, pending stays; the next frame with `start >= dispatchNs` answers |
| AC-13 | unit | P0 | C5 `KeyLatencyMatcherTest.heldKeyArmsOnceAndMeasuresFromTheFirstDown` | DOWN r0 arms, repeats 1..20 do not re-arm and do not replace the pending time; a slow frame emits exactly one `KeyLat` with lat from the r0 time |
| AC-13 | unit | P0 | C6 `KeyLatencyMatcherTest.secondDownWhilePendingDoesNotReplaceTheEarliest` | a second r0 DOWN of another key while one is pending: lat is still measured from the first |
| AC-13 | unit | P0 | C7 `KeyLatencyMatcherTest.unhandledKeyNeverArms` | `handled=false`: frames of any length produce nothing; control with `handled=true` produces a line |
| AC-13 | unit | P0 | C8 `KeyLatencyMatcherTest.pendingExpiresAfterOneSecondAndLateFrameEmitsNothing` | frame starting at dispatch + 999 ms answers (and emits if slow); frame at + 1001 ms drops the entry silently (no emit even though `lat` is huge); then a later key arms normally |
| AC-13 | unit | P0 | C9 `KeyLatencyMatcherTest.noFrameMeansNoLine` | arm, no `onFrame`: nothing, and a following `arm` is refused until expiry/answer (documents the one-slot rule) |
| AC-13 | unit | P0 | C10 `KeyLatencyMatcherTest.answeredEntryIsConsumedAndNotEmittedTwice` | after an emit, further slow frames emit nothing |
| AC-13 | unit | P0 | C11 `KeyLatencyMatcherTest.negativeLatencyFromAClockBaseMismatchIsNeverEmitted` | frame end before `eventMs`: no line, matcher cleared (R8: the line would otherwise be negative nonsense) |
| AC-13 | unit | P1 | C12 `KeyLatencyMatcherTest.lineCarriesTheArmedScreenNotTheScreenAtAnswer` | `arm(screen=Home)`, holder changes to another screen, answer: `KeyLat.screen === Home` |
| AC-13 | unit | P2 | C13 `KeyLatencyMatcherConcurrencyTest.armOnOneThreadAnswerOnAnotherNeverDoubleEmits` | 10 000 arm/answer rounds on two threads with a latch: emitted count `<=` arm count, never an exception |
| AC-13 | unit | P0 | C14 `FrameProbeTest.listenerFeedsMatcherThenAggregatorOnlyWhenOn` | see FP block |
| AC-13 | device | P0 | D7 below | clock bases agree on the emulator: every `lat_ms` is positive and under 1000 |

#### AC-14: memory

Executor: `ManualTelemetryExecutor` with `periodMs` recording (seam). Sampler: a fake `MemorySampler` returning fixed
values and logging `(withPss, trim, thread name)`.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-14 | unit | P0 | M1 `MemoryProbeTest.foregroundSchedulesOnePeriodicSampleEverySixtySeconds` | `attach` + `onForeground(true)`: one repeating task with `periodMs == 60_000`; one `runRepeating()` produces exactly one `mem` line with fields `java_kb,java_max_kb,native_kb,pss_kb` and no `trim`; sampler called with `withPss=true` |
| AC-14 | unit | P0 | M2 `MemoryProbeTest.noPeriodicLineInTheBackgroundAndTimerIsCancelled` | `onForeground(true)` then `(false)`: repeating count back to 0 and `runRepeating()` writes no `mem`; never in foreground yet: no timer was scheduled at all (the foreground case of M1 is the control) |
| AC-14 | unit | P0 | M3 `MemoryProbeTest.reenteringForegroundKeepsOneTimerNotTwo` | `onForeground(true)` twice, and background/foreground/background/foreground: repeating count is 1; one tick writes one line |
| AC-14 | unit | P0 | M4 `MemoryProbeTest.trimWritesOneExtraLineWithLevelAndNoPss` | `onTrimMemory(TRIM_MEMORY_RUNNING_LOW)` (10): after `runAll()` one `mem` line with `trim=10`, no `pss_kb`; sampler called with `withPss=false` |
| AC-14 | unit | P0 | M5 `MemoryProbeTest.uiHiddenTrimIsIgnored` | level 20 writes nothing; control: level 10 writes one (D14: `UI_HIDDEN` fires on every backgrounding) |
| AC-14 | unit | P1 | M6 `MemoryProbeTest.backgroundTrimLevelIsWrittenButPeriodicIsNot` | spec gap 3: `onForeground(false)`, `onTrimMemory(60)`: one trim line, no periodic line after `runRepeating()` |
| AC-14 / NODIST | unit | P0 | M7 `MemoryProbeThreadTest.samplerRunsOnlyOnTheWriterThread` | real `ThreadTelemetryExecutor`: `onTrimMemory` from the test thread returns before the sampler runs; the sampler records `Thread.currentThread().name == ThreadTelemetryExecutor.THREAD_NAME` and `isWriterThread`; awaited by `CountDownLatch` with timeout, no sleeps |
| AC-14 / NODIST | unit | P0 | M8 `MemoryProbeThreadTest.periodicSampleRunsOnTheWriterThreadNotOnTheCaller` | with the manual executor, the sampler is not called during `onForeground(true)` itself (only on `runRepeating()`, `isWriterThread == true` inside) |
| AC-14 | unit | P0 | M9 `MemoryProbeTest.detachUnregistersTrimClosesTimerAndLaterTrimIsIgnored` | `attach` registers once (a second `attach` still once); `detach` unregisters, repeating count 0, a trim after detach writes nothing; `detach` twice is safe |
| AC-14 | unit | P1 | M10 `MemoryProbeTest.samplerExceptionDoesNotStopTheTimer` | sampler throws on tick 1, succeeds on tick 2: tick 2 still writes a line, nothing escapes |
| AC-14 | unit | P0 | M11 `TelemetryEncoderTest` rows | `Mem` periodic and `Mem` trim sample rows added to `samples`; the parameterised `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` then demands exact key sets (the trim variant is a second parameterised case so `pss_kb` vs `trim` exclusivity is enforced) |
| AC-14 | device | P0 | D4 below | one line per foreground minute, none in background, trim line |

#### AC-S2-OFF: nothing runs when the log is off

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| OFF | unit | P0 | L1 `TelemetryRuntimeProbesTest.freshProcessWithLogOffNeverAttachesAnyProbe` | fake probe list, queueing `mainPost`: after `start` with no pref, attach 0 and detach 0, main queue empty, store untouched, `executor.useCount == 0` |
| OFF | unit | P0 | L2 `TelemetryRuntimeProbesTest.enablingAttachesOnceOnTheMainThreadQueue` | enable: attach is not run until the main queue drains, then runs exactly once |
| OFF | unit | P0 | L3 `TelemetryRuntimeProbesTest.disablingDetachesAndOnOffOnEndsAttached` | on, off, on with a queued main post: order preserved, final state attached, attach/detach counts 2/1 |
| OFF | unit | P0 | L4 `TelemetryRuntimeProbesTest.autoOffAfterSevenDaysAndStorageFailureAlsoDetach` | expiry (reuse the T25 clock setup) and a throwing store each end with detach called once and `Telemetry.isEnabled == false` |
| OFF | unit | P0 | L5 `TelemetryRuntimeProbesTest.detachRaisedOnTheWriterThreadIsPostedNotRunInline` | trigger `onExpired` inside `executor.runAll()`: `probe.detach` has not run until the main queue drains (a JankStats call off main would otherwise crash on the device) |
| OFF | unit | P0 | L6 `TelemetryRuntimeProbesTest.screenHolderIsNullAfterDetach` | holder set while on, null after off |
| OFF | unit | P0 | L7 `TelemetryRuntimeProbesTest.offAfterOnWritesNothingFurtherAndOpenWindowIsDropped` | on, record 20 frames in an open window, toggle off, drain: the store's line count and file set are unchanged after the toggle and a later `close()` emits no line (the control asserts the 20 frames would have produced a `frames` line when still on, so the window really was open) |
| OFF | unit | P0 | L8 `TelemetryActivityCallbacksTest.resumeWhenOffDoesNotStartTrackingButTracksTheActivity` | mockk `Activity`; off: resume does not call `frameProbe.start`, `memoryProbe.onForeground` untouched; the next enable starts tracking for the already-resumed Activity (L9 control) |
| OFF | unit | P0 | L9 `TelemetryActivityCallbacksTest.enableWhileResumedStartsTrackingOnThatActivity` | resume while off, then enable: exactly one `frameProbe.start(activity)`; enable while no Activity is resumed: none, and the next resume starts it |
| OFF | unit | P0 | L10 `TelemetryActivityCallbacksTest.pauseStopsFrameFlushesKeysThenRequestsDrainInThatOrder` | recorded call order is `frame.stop, keyFlush, requestDrain`; open frame window and open key burst are present in the store after the drain (proves "closing before the drain" end to end) |
| OFF | unit | P0 | L11 `TelemetryActivityCallbacksTest.destroyAndPauseClearTheResumedActivity` | a leaked reference check via a second enable after pause: no `start` on the old Activity |
| OFF | unit | P1 | L12 `TelemetryActivityCallbacksTest.startedCountTreatsTwoActivitiesAsForegroundUntilBothStop` | start A, start B, stop A: still foreground; stop B: `onForeground(false)` once |
| OFF | unit | P0 | L13 `KeyProbeOffTest.offDispatchNeverTouchesTheMachineOrMatcher` | combined with K1: after a feed of 50 key events through `dispatchWithProbe(enabled=false)` the machine and matcher are in their reset state and `Telemetry.record` calls on a counting sink are zero; on control feeds the same events and gets the lines |
| OFF | unit | P0 | L14 `TelemetrySinkTest` addition `memoryAndFrameTypesAreCountedAsDisabledNoOps` | `record(Frames/Key/KeyLat/Mem)` with the flag off leaves encoder, store, clock and executor with zero calls (extends T15 to the new types) |
| OFF | device | P0 | D1 below | empty telemetry directory with the toggle off, cold-start A/B, runtime on/off/on with an Activity already resumed |

#### Probe glue (JankStats) and threading, AC-S2-OFF / AC-S2-NODIST

`FrameProbeTest` uses a fake `FrameTrackerFactory` that records `create(activity, listener)` and exposes the
listener, so the production listener body is exercised without JankStats.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| OFF | unit | P0 | FP1 `FrameProbeTest.startCreatesOneTrackerAndStartTwiceDoesNotCreateASecond` | idempotent start |
| OFF | unit | P0 | FP2 `FrameProbeTest.stopDisablesTrackingClosesAggregatorAndReleasesTheTracker` | `isTrackingEnabled == false`, open window emitted once, a later `stop` is safe |
| OFF | unit | P0 | FP3 `FrameProbeTest.recreatedActivityGetsANewTrackerAndTheOldOneStaysStopped` | start(A), stop, start(B) (config or locale change): two trackers, first disabled, second bound to B |
| OFF | unit | P0 | FP4 `FrameProbeTest.listenerWhileOffTouchesNeitherAggregatorNorMatcher` | listener invoked with the flag off: zero aggregator and matcher changes; control: same invocation on gives a counted frame |
| OFF | unit | P1 | FP5 `FrameProbeTest.lateFrameAfterStopDoesNotEmitOrArm` | listener called after `stop()`: no line, no pending key (R15) |
| AC-13 | unit | P0 | FP6 `FrameProbeTest.listenerFeedsMatcherThenAggregatorWithTheCurrentScreenHolder` | `Telemetry.screen` set to `HomeScreen`: aggregator frame carries it, matcher is called before the aggregator (a slow answer still gets recorded when the aggregator closes a window on the same call) |
| NODIST | unit | P0 | TH1 `FrameThreadingTest.noTelemetryIoOrEncodingOnTheCallbackThread` | listener called from a test thread named `FrameMetricsHandler`, window closed by a screen change, with real `ThreadTelemetryExecutor` and `FakeTelemetryStore`: every `store.calls` thread name equals `ThreadTelemetryExecutor.THREAD_NAME`, encoder spy saw only that thread, and the callback thread name appears nowhere (as S1 T35/T36 did) |
| NODIST | unit | P0 | TH2 `KeyThreadingTest.keyEmitAndLatencyEmitDoNoIoOnTheCallerThread` | burst closed on the "main" test thread and `KeyLat` emitted on the "frame" thread: file IO and JSON only on the writer thread |
| NODIST | static | P0 | TH3 review checklist, `rg` | `FrameAggregator.onFrame` and the listener contain no `Telemetry.` call other than via `emit` on close, no string template, no `Debug.`/`getMemoryInfo`; `DebugMemorySampler` is only referenced from `MemoryProbe` |

#### Caps, caps flood

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-11..14 | unit | P0 | X1 `TelemetryCapsTest.newTypesHaveTheDeclaredPerMinuteCaps` | parameterised: `FRAMES` 12, `KEY` 120, `KEY_LAT` 60, `MEM` 10; cap+1 events in one wall minute leave exactly cap lines and a `meta dropped` with `counts[type] == 1` using the wire names |
| AC-13 | unit | P1 | X2 `TelemetryCapsTest.keyLatFloodIsCountedNotLostSilentlyAndWindowResets` | 200 `KeyLat` in one minute: 60 lines, `dropped` counts 140; after the minute rolls the next one is written |
| all | unit | P0 | X3 `TelemetryEventTypeTest.everyTypeHasAnEncoderSampleAndACap` | extends the existing parameterised test so a new type without a sample or cap fails |

#### AC-S2-R8 and the dependency

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| R8 | static | P0 | S1 `./tools/agentw :app:compileDevDebugKotlin` after plan task 1 | the catalogue entry and `implementation(libs.androidx.metrics.performance)` resolve; the real JankStats API shape is recorded in the plan (D2) |
| R8 | static | P1 | S2 `ProguardRulesTest` unchanged and still green | existing keep rule for screen classes (`proguard-rules.pro:90`) is intact; D16 adds no rule |
| R8 | device | P0 | D12 below | `devBenchmarkRelease`: the four line types appear with readable `scr` |
| deps | static | P1 | S3 grep `rg "metrics" gradle/libs.versions.toml app/build.gradle.kts` | one version, one library alias, one `implementation`; no hardcoded version string in `app/build.gradle.kts` |

### Mutation spot-checks (against R9; run by the test writer or reviewer, recorded in the evidence)

Each one-line change must turn at least the named test red; if it does not, the test is vacuous.

| Mutation | Must fail |
|---|---|
| `Telemetry.record` guarded by nothing in `dispatchWithProbe`, off branch calls the probe | K1, L13 |
| `dispatchWithProbe` returns `true` after probing | K2 |
| `keyNameOf` returns the raw-mapped name for letters (add `KEYCODE_A` to the allow-list) | N1, N2, N3 |
| `ScreenScoped` branch removed from the writer | W1, W2 |
| `lat_ms` measured from dispatch not `eventTime` | C3 |
| threshold `>= 50` instead of `> 50` | C2 |
| window close uses `>` not `>=` for 10 s | F3 |
| `t` not back-dated, or not clamped | F8, F9 |
| `onForeground(true)` schedules a second timer | M3 |
| `UI_HIDDEN` not ignored | M5 |
| `plantProbes` skips the already-resumed Activity | L9 |
| `detachProbes` runs inline on the writer thread | L5 |
| `Telemetry.screen` not reset on detach | L6, F13 |

### Manual / device scenario (`device`, recorded in the evidence file)

Preconditions: leased emulator (`tools/emulator-resource-lock.sh`), `adb -s <serial>` everywhere, the worktree's
own `devDebug` APK installed with `tools/android-apk-install-preserve`, no uninstall, no data clear, account
already paired (a worktree build cannot log in). Release the lease at the end. Host load is stated in the
evidence next to every relative number.

**What is meaningful on a software-GPU emulator, and what is not**

| Meaningful (assert it) | Not meaningful (record, never judge) |
|---|---|
| presence, order and field sets of every line (`jq` key sets as in S1) | jank percentage (about 99 %, every frame janky) |
| one `key` line per held press, `n` and `dur_ms` plausible | absolute `key_lat` values and the `frame_ms` numbers |
| `frames` cadence (about 6 per active minute), `scr` correct across a navigation, `frames/janky` counts consistent | `frames.p50/p90/p99/max` values (they will be 150-300 ms) |
| `mem` once per foreground minute, none in background, trim line | absolute PSS or heap sizes as a quality statement |
| clock-base sanity: every `lat_ms` positive and below 1000 | the share of presses that produced a `key_lat` line (nearly all will) |
| volume under caps, `meta dropped` for `key_lat` is expected here | |
| relative A/B within one build and one load window | any comparison across hosts or sessions |

Steps. Numbers are the evidence ids used above.

1. **D1: off baseline and runtime toggle (AC-S2-OFF).** Fresh install state with the toggle off (do not clear data:
   toggle off in Settings). `adb shell run-as`-free listing of `getExternalFilesDir/telemetry` via the sanctioned
   `adb -s <serial> shell ls` on the external files path: empty or absent before and after navigating Home, a list,
   holding D-pad and a minute in the foreground. Then toggle on while the Activity is resumed (no restart): lines
   for `frames` start within about 10-20 s of scrolling. Toggle off: the file stops growing (size unchanged over
   30 s of scrolling and a held D-pad); toggle on again: a new file. Must not happen: any `*.jsonl` created or
   grown while off; a `frames` or `key` line after the off.
2. **D2: JankStats actually records (gate, R6).** Toggle on, scroll Home for 25 s. Expected: at least one `frames`
   line. If none appears the software window is not tracked: stop, record, and escalate; the unit tests still stand
   but AC-12 then needs the owner's real-TV pass (MANUAL M1). Do not weaken the code.
3. **D3: frames (AC-12).** Scroll Home ~25 s, open History and scroll ~15 s, press Back right after a window
   boundary. Expected: `frames` lines with `scr` `Home` and `History` (their own window each; the line that closes
   right after the navigation still says the old screen), field set exactly `frames,janky,p50,p90,p99,max`,
   `janky <= frames`, `p50 <= p90 <= p99 <= max`, `t` non-decreasing and `>= 0`. Must not happen: a Home window
   labelled `History`, an empty `scr` while a nav has happened, a negative or decreasing `t`.
4. **D4: memory (AC-14).** Stay in the foreground for 125 s on a quiet screen: exactly two periodic `mem` lines
   (fields `java_kb,java_max_kb,native_kb,pss_kb`). `adb shell input keyevent HOME`, wait 130 s: no new periodic
   `mem` line. Resume: the timer restarts (a line about 60 s later). `adb -s <serial> shell am send-trim-memory
   <pkg> MODERATE` while the app is running: one extra `mem` with `trim` and no `pss_kb`. Must not happen: a
   periodic line in the background, two periodic lines in one minute after a background/foreground cycle.
5. **D5: D-pad behaves exactly as before.** With logging on, then off, then on: Home to a list, DPAD_DOWN x10,
   DPAD_RIGHT x5, DPAD_CENTER on an item (opens detail), BACK x2, and a long press (`input keyevent --longpress
   KEYCODE_DPAD_DOWN`). Expected: focus moves one item per press, the same items are reached in both modes
   (record the title or position after each step), Back pops one screen per press, the long press scrolls and
   stops on release. Run the same script on the base build for the comparison when the base APK exists. Must not
   happen: a swallowed or doubled key, a focus jump, Back that needs two presses, an `AppRemoteHotkeyHandler`
   shortcut that stops working. This is the R2 device guard.
6. **D6: key lines (AC-11).** Hold DPAD_DOWN (`--longpress`): one `key` line with `n > 1` and `dur_ms`
   consistent with the hold, not one line per repeat. Then three separate DPAD_DOWN presses: three lines `n=1`.
   Field set exactly `key,n,dur_ms`.
7. **D7: `key_lat` and clock bases (AC-13, R8).** Press DPAD_DOWN 10 times, 1 s apart, with logging on. Expected:
   lines (many on this renderer) with `lat_ms` strictly positive and under 1000, `frame_ms` positive, field set
   `key,lat_ms,frame_ms`. If any `lat_ms` is negative or in the hundreds of thousands, the clock-base assumption
   is wrong: record, escalate, do not change the threshold. A burst of presses fast enough to exceed 60/min must
   show `meta dropped` with `counts.key_lat`; that is the expected flood, not a defect.
8. **D8: volume and caps.** After 10 minutes of mixed use: the per-type line counts per minute stay under the caps
   (frames 12, key 120, key_lat 60, mem 10), the file size stays within the S1 budget arithmetic (bytes per line
   measured, not assumed). Must not happen: a file over 2 MiB or more than the 20 MiB archive cap.
9. **D9: no work on hot threads (NODIST).** With logging on, `adb shell` `strace`-free check is not possible, so
   use a `StrictMode`-free proxy: run `dumpsys gfxinfo` before and after scrolling and a `logcat -s` filtered to
   the package for `Skipped frames` or ANR lines and attach only counts, not raw logcat (AGENTS.md). Expected: no
   ANR, no `Skipped N frames` burst correlated with a `mem` tick (a PSS sample on main would show as one). The
   PSS cost is measured once: log the time around one sample on the writer thread; if above a few ms on a
   `low_ram` device, record it for the plan's follow-up (brief risk 1).
10. **D10: toggling and lifecycle.** Rotate or change locale while logging is on (Activity recreation); scroll:
    `frames` lines keep coming for the new window, no duplicates from a stale tracker (line count per window
    stays one). Background and foreground the app twice: frames resume.
11. **D11: privacy scan (AC-11a, AC-7).** With logging on, type a known string into search (`qwertzuiop12345`), run
    a session, then scan all session files with `tools/mobile-evidence-audit.sh` and `grep` for each typed character
    sequence and for `"key":` values: only allow-list wire names or `other` appear.
12. **D12: minified build (AC-S2-R8).** Build `devBenchmarkRelease` once through `./tools/agentw`, install-preserve,
    toggle on, scroll, hold a key, wait one `mem` period. Expected: `frames`, `key`, `key_lat` and `mem` lines,
    `scr` is a readable class name, no `a.b.c`-style names. Do not generate a baseline profile.

Evidence is only the line structure, counts and the `jq` summaries, not raw files (no account data, no
authenticated responses).

#### MANUAL (owner / real TV, not a blocker for this run)

- M1: real-TV pass: `frames` under a hardware GPU (true jank, true `p99`), `key_lat` realistic, `mem` PSS cost.
  Needs the owner's explicit authorization for a physical device.

### Perf track

Metrics (all relative, one emulator, one build type, alternating sets, same host-load window stated):

| Metric | How measured | Off vs base threshold | On bound |
|---|---|---|---|
| cold start | `am start -W` TotalTime after `am force-stop`, 8 runs per set, first run dropped, alternating BASE and NEW sets, A/B as in the S1 evidence | pooled median delta within `max(5 percent, 2 x SD of baseline runs)` (S1: +74 ms on a 2429 ms median, accepted) | information only, but the on median must not exceed off by more than the same `max(5 percent, 2 x SD)` |
| frame cost on the UI thread | `dumpsys gfxinfo <pkg> reset`, then the fixed script (Home: 6 DOWN, 8 RIGHT, 8 LEFT, 6 UP), 3 runs per mode, record `Total frames rendered`, `Janky frames`, p50/p90/p95/p99 | `p50` of frame time off within the baseline run-to-run spread; jank percentage not worse than 1 point (jank is about 99 percent here, so this is a sanity bound, not evidence) | `p50` on within the same spread of off; if above, record the load and repeat once before failing |
| `dispatchKeyEvent` cost | main-thread timing of `dispatchWithProbe` on/off is not directly observable from outside; use the unit proof (K1: off path is one flag read) plus review, and on the device compare `p50` above | n/a | n/a |
| memory sample cost | one-shot: log `nanoTime` before and after `DebugMemorySampler.sample` on the writer thread (a temporary local measurement, not committed) | n/a | PSS under a few ms on this device, else follow brief risk 1 |
| allocation per frame | unit F10 and K13 (`ThreadMXBean`, with negative control) | zero by construction | under 64 KB over 200 000 calls |
| log volume | bytes per line measured from a real session; file bytes per active minute | n/a | on-device per-minute bytes times 60 min stays under the 20 MiB budget stated in S1 |

`{run}/baseline.md` must contain, captured on the base commit `cd16eb47` build before implementation: cold start
median, SD and 7 runs; the gfxinfo summary (frames, janky, p50/p90/p95/p99, 3 runs); the host load during capture
(`uptime`); the exact script; and the emulator serial and build variant. Pass rule: logging off is within noise of
that baseline by the cold-start and gfxinfo thresholds above (re-measured A/B on the same load if the host load
moved by more than a factor of two, as S1 did); logging on cost is bounded as in the table and recorded as
information beside the off result. A result outside the threshold that disappears on re-measure under equal load
is reported as noise with both sets shown; one that persists is a fail and goes back to the code.

### UNTESTABLE

- `AC-12` "the callback allocates nothing" in the strict sense: only the proxy is testable (F10 and K13 with a
  negative control), the absolute statement stays a code-review item (TH3).
- `AC-S2-NODIST` "no PSS on the main thread" on a real device: unit tests prove the sampler's thread through the
  injected `MemorySampler`, but `DebugMemorySampler` itself (`android.os.Debug`) cannot run on the JVM; covered by
  TH3 review and device D9.
- `AC-13` clock-base agreement (R8) between `KeyEvent.eventTime` and `frameStartNanos`: testable only on a device
  (D7); the unit tests assume a shared base.
- `AC-12` on a real GPU and absolute jank numbers: not available on the emulator (MANUAL M1).
- `MainActivity.dispatchKeyEvent` itself without an Activity: its logic is moved to `dispatchWithProbe` and
  tested there; the one-line call from the override is covered by D5 and review.

### Baseline (qa-device, mode baseline)

Device: emulator-5554 (AVD puber_tv_36, API 36 TV, 1920x1080, software GPU, logged in). Package com.kino.puber.stage.
Build: worktree's own devDebug APK built with `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevDebug`,
versionName 1.10.0-dev, sha256 753bfd91...c8b (on-device base.apk sha256 identical). Installed with
install-preserve: outcome `installed`, `compatible_replace`, destructive_action false. Lease taken and released.
"Log ON" = Settings > For developers > Session log toggled through the D-pad (confirmed on screen "Recording",
`telemetry_preferences.xml` holds `enabled_at_wall_ms`; session files appear in
`/sdcard/Android/data/<pkg>/files/telemetry/`, not in internal `files/`). Log left OFF at the end (prefs `<map />`).
Host load is the dominant noise: the emulator shares the host with other agents, so load average is quoted per block.

#### Cold start (am start -W TotalTime ms; force-stop, 2 s, start, 6 s gap; 12 runs per block)
Debug build, so absolute values are high; time to first activity frame, not to Home content.

| Block | Log | Host load (1 min avg, start -> end) | Runs | Median | Min | Max |
|-------|-----|------|------|--------|-----|-----|
| A | OFF | 5 -> 26 (rising during block) | 1918 1581 1645 1563 1687 3136 2867 2697 3030 2877 2780 2826 | 2738 | 1563 | 3136 |
| B | ON  | 37 -> 34 | 3063 2781 2921 3146 2836 2904 2939 2914 3367 2827 2882 2801 | 2909 | 2781 | 3367 |
| C | OFF | 6 -> 6 (falling) | 1612 1560 1551 1590 1603 1556 1605 1558 1544 1507 1616 1544 | 1559 | 1507 | 1616 |
| D | ON  | 7 -> 3 | 1516 1591 1613 1590 1615 1542 1642 1575 1552 1596 1629 1605 | 1594 | 1516 | 1642 |

Fair A/B is C vs D (same low load, back to back, tight spread ~7%): OFF 1559 ms, ON 1594 ms, delta +35 ms (+2.2%),
inside the within-block spread (about 110 ms). A and B are not comparable (load 5 -> 26 and 35): the same build shows
a bimodal 1.5 s / 2.8 s depending on host load, so any later comparison must be against block C/D or be rerun back
to back at low load. Earlier S1 baseline (7 runs, load unknown): median 2804 ms, the high-load mode.

#### Frames (dumpsys gfxinfo reset; Home; 6 Down, 8 Right, 8 Left, 6 Up with 0.35 s gaps; 3 runs per state)
Software GPU: every frame janky, GPU percentiles 4950 ms. Gross check only.

| Log | Run | Frames | Janky | p50 | p90 | p95 | p99 |
|-----|-----|--------|-------|-----|-----|-----|-----|
| OFF | 1 | 155 | 153 (98.71%) | 200 | 350 | 400 | 750 |
| OFF | 2 | 147 | 146 (99.32%) | 150 | 300 | 350 | 400 |
| OFF | 3 | 158 | 155 (98.10%) | 150 | 300 | 350 | 400 |
| ON  | 1 | 166 | 164 (98.80%) | 150 | 300 | 400 | 550 |
| ON  | 2 | 151 | 150 (99.34%) | 150 | 300 | 350 | 400 |
| ON  | 3 | 159 | 155 (97.48%) | 150 | 250 | 250 | 350 |

Both states within the same range (p50 150-200 ms, p90 250-350 ms, p99 350-750 ms); no discriminating signal.
Host load at those runs: about 11 (OFF) and about 11 (ON).

#### App PSS after 1 min idle on Home (force-stop, start, 75 s, `dumpsys meminfo`)
| Log | TOTAL PSS (KB) | TOTAL RSS (KB) | Java Heap PSS | Native Heap PSS |
|-----|----------------|----------------|---------------|-----------------|
| OFF | 220,568 | 354,308 | 27,720 | 42,052 |
| ON  | 223,737 | 358,324 | 29,844 | 42,740 |

ON - OFF = +3.2 MB PSS (+1.4%), one sample each (single run per state; host load about 11 both).

#### APK size
devDebug `app-dev-debug.apk`: 36,307,602 bytes (S1 base was 36,236,318; +71 KB from the merged telemetry-core
at cd16eb47 vs its own base, expected). Minified not built here (see telemetry-core baseline: assembleDevRelease
needs a keystore in a worktree; devDeploy / devBenchmarkRelease substitute).

#### Method for the later comparison
Same emulator, devDebug, install-preserve, toggle via D-pad. Run the OFF and ON cold-start blocks back to back
(OFF, ON, OFF, ON) at host load below about 8 and compare median TotalTime (12 runs); discard blocks whose load
average crosses 10. Frames: same key sequence, gross check only; the real instrument is the new `frames` event.
PSS: same 75 s idle, take at least 3 samples per state.

#### Notes
- During navigation to Settings one stray OK press opened a Details screen for the Home hero (no playback, left with
  BACK; no account mutation). Later runs used LEFT first to reach the sidebar.
- The telemetry directory holds many session files from these cold starts (device-local, not in git).

### Risks (analyst)

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

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Leave `slow_frame` lines out of S2? | no | answered | Yes; `max` in the frames window covers AC-12. |
| Q2 | Add `mem` sampling on screen change? | no | answered | No; one line per foreground minute plus trim, attributed by `scr`. |
| Q3 | Count key answers under 50 ms inside the frames window? | no | answered | No, skip in S2. |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-10-15 | Architect Q1-Q2: no image-cache size in `mem` for S2; frame time is UI-thread work only (stated in spec), no frameDurationTotalNanos switch in S2 | owner (standing instruction: recommended) |
| 2026-10-15 | Test-design spec pins accepted: ms fields floor; back-dated frames `t` clamped to the session origin; a TRIM_MEMORY_MODERATE trim line is written in background while the periodic line is not; a key repeat with no open burst opens one with n=1; an exception from super propagates and the probe is not called | owner (standing instruction: recommended) |
| 2026-10-15 | Gate 1: spec approved as assembled (M, perf, AC-11, AC-11a, AC-12, AC-13, AC-14, AC-S2-OFF, AC-S2-NODIST, AC-S2-R8) | owner (standing instruction: recommended) |
| 2026-10-15 | Q1-Q3 take the analyst's recommendations | owner (standing instruction: recommended) |
