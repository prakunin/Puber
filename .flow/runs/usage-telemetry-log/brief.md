# Brief: usage-telemetry-log

Triage of 2026-10-15 against master `39903d31`. Builds on `research.md` (cited as
`research.md: <section>`). Spot-checked in code: `PuberVM.updateViewState`
(`core/ui/PuberVM.kt:82`), open `onAction` (`:135`), the single shared `OkHttpClient`
(`data/di/modules.kt:57-61`, no event listener), the router collector with
`router.log("router command: $event")` (`core/ui/navigation/component/FlowComponent.kt:225-226`),
and the unbounded JSONL precedent (`ui/feature/player/component/TimedActionDebugTrace.kt:10-37`).
All hold as research.md describes them.

## Classification

- **kind:** feature
- **size:** L (more than one shippable goal; see SPLIT). Research rated the whole as M. It
  is M per slice: one new `core/telemetry` sink, then five independent probe families (UI
  flow, frames/keys/memory, network, player/exit, analysis tool). Each family crosses layers
  and touches a risk area (startup, networking, playback, persistence, ViewModel wiring),
  so one gate would be too large and too hot (`PlaybackController.kt` and `FlowComponent.kt`
  are in flight in `player-overlays-port` and `upstream-sync-triage`).
- **tracks:** `perf` (the goal is finding slow, janky, heavy places; the log is the
  measuring instrument). `ui` only for the Settings toggle row, which is small and follows
  the existing "Developer" section pattern, so no `ux-designer` design delta is expected.
  Tracks: perf (ui for the toggle row in slice 1, owner judges).
- **Device verification: yes** (startup, DI, networking, persistence, player, D-pad input,
  Settings UI, build variants). Decided before looking at hardware.

## Root need

The owner needs evidence from real use, not guesses: which screens are slow to show content,
which key presses are answered late, where frames drop, which requests are slow or fail,
how memory grows over a session. The deliverable is a log that answers those questions
directly, bounded in size, switched off by default, temporary, and free of titles, search text, URLs and tokens
(content ids are allowed by owner decision Q2).
"What to collect and how much" is the main design problem (the owner asked for it
explicitly), so the catalogue below is part of the acceptance contract.

## Principles (how noise is avoided)

1. Every event answers a perf question and carries a duration or a count. Events that only
   say "something happened" are dropped.
2. Aggregate on the device, log summaries: key repeats become one burst line, frames become
   a window summary, media segments become a per-minute row, a playback becomes one summary.
3. Allow-list fields per event type. No payloads: titles, search text, URLs, query
   strings, tokens are never fields. Content ids (item, season, episode) are allowed as
   explicit typed fields where they help join a scenario (owner decision Q2).
4. Every line carries `t` (monotonic ms since session start), `sid` (session id) and `scr`
   (stable screen name), so any two streams join by time and screen.
5. Hard bounds: per-stream rate cap with a `dropped` counter event, file rotation, total
   cap, auto-off.
6. Off means free: a single volatile flag check per probe, no allocation, no listener work.

## Event catalogue and volume budget

Assumption (`unverified`, arithmetic only): an active hour is about 1000 key presses, 300
navigation/state/action events, 150-250 API calls, 40 minutes of playback. Line sizes are
about 60-200 bytes of JSON. Measure on device in slice 1 and correct the numbers.

| Type | Hook | Fields (allow-list) | Rate and bound | Est. per active hour |
|---|---|---|---|---|
| `session` | `PuberApp.onCreate`, `MainActivity` | build type, versionName, API level, device model, RAM class, `isLowRamDevice`, refresh rate | 1 per process start | < 1 KB |
| `startup` | process start, `Application.onCreate`, splash frame, `App` composed, Home first content (`ReportDrawnWhen`) | marks in ms from process start | 1 per cold start | < 1 KB |
| `nav` | `FlowCommandRunner` (`FlowComponent.kt:225`), `TabComponent.kt:104` | command type, from `scr`, to `scr` | every command, about 100/h | ~15 KB |
| `ready` | `PuberVM.updateViewState` (`PuberVM.kt:82`) | `scr`, state class (Loading/Content/Error), ms since screen open | state-class change only, not every update | ~20 KB |
| `act` | base `PuberVM.onAction` (`PuberVM.kt:135`) | `scr`, action class name | one per action; high-frequency actions (focus, scroll) are coalesced per second with a count | ~25 KB |
| `key` | `MainActivity.dispatchKeyEvent` | keyCode, `scr`, burst count and duration (repeats collapsed to one line per long press) | one line per press or burst | ~50 KB |
| `key_lat` | key `eventTime` + next frame | ms from key event to first frame after it, that frame's duration | only when latency > 50 ms, else counted in the frame window | ~10 KB |
| `frames` | JankStats + `PerformanceMetricsState` (`scr`, overlay, scrolling) | per 10 s window per screen: frames, janky, p50/p90/p99/max ms | one per window while screen active; individual frames > 100 ms as `slow_frame`, at most 20 per window | ~45 KB |
| `net` | OkHttp `eventListenerFactory` on `modules.kt:58` | class (api/tmdb/introdb/media/image), method, route template (`/v1/items/{id}`), status, DNS/connect/TLS/TTFB/total ms, bytes, reused connection, failure class | every non-media call; media segments are not logged individually | ~40 KB |
| `net_media` | same listener, classified by the player client (`PlaybackController.kt:626`) | per minute: count, bytes, p90 ms, failures | 1 per minute of playback | ~5 KB |
| `mem` | 60 s foreground timer, screen change, `onTrimMemory` | Java heap used/max, native heap, PSS (at most once a minute, off main thread), image-cache size, trim level | 1 per minute plus screen changes, never from the main thread | ~10 KB |
| `play` | `PlaybackController` Player/Analytics listeners + `PlaybackStatsListener` | per playback: join ms, rebuffer count and ms, dropped frames, format switches, error count, recovery path, content kind (movie/series) | 1 summary per playback, plus 1 per player error | ~2 KB |
| `err` | Timber tree planted when on, `DefaultExceptionHandler`, uncaught-handler chain, `ApplicationExitInfo` on next start (API 30+) | exception type, message through `SensitiveRequestLogPolicy`, top 5 app frames (no URLs), exit reason | per error, rate-capped 20 per minute, identical errors folded into a count | ~5 KB |
| `meta` | the sink | `dropped` counters by type, rotation, auto-off | on drop or rotation | < 1 KB |

Total estimate: about 0.25-0.4 MB per active hour, in line with research.md's 0.3-0.5 MB.
Bounds: 2 MB per file, 20 MB total (oldest files deleted first), automatic switch-off
after 7 days, writes batched off the main thread (single writer thread, flush every few
seconds and on pause).

Deliberately not collected: titles, genres, search text (only
its length), URLs, hosts beyond a class, query strings, request or response bodies, account
or device identifiers, heap dumps, per-frame lines, per-HLS-segment lines, every
focus-move payload, raw logcat.

## Acceptance criteria

Slice tags in brackets say which run of the SPLIT delivers the criterion.

- **AC-1** [S1] Given a fresh install, when the user opens Settings, Then the Developer
  section shows a "Session log" toggle that is off, and no telemetry file exists.
  Switching it on shows a visible "recording" indicator in Settings with the time left until
  auto-off.
- **AC-2** [S1] Given the toggle is off, when the app runs through startup, navigation and
  playback, Then no telemetry file is created or written and no probe allocates (unit test
  on the sink flag; device observation of the empty directory).
- **AC-3** [S1] Given the toggle is on, when the app is used, Then JSONL files appear under
  `getExternalFilesDir("telemetry")`, each line holding `t`, `sid`, `type` and the
  catalogue fields of that type and no others, readable with `adb pull`.
- **AC-4** [S1] Given the toggle was switched on 7 days ago (injected clock in a unit
  test), when the app starts or the next event is recorded, Then recording is off again and
  the toggle shows off.
- **AC-5** [S1] Given a stream that produces more than its cap, when the cap is hit, Then
  excess events are dropped and a `meta` line reports the count per type; given 20 MB of
  files, when a new file opens, Then the oldest file is deleted first (unit tests with a
  fake clock and fake storage).
- **AC-6** [S1] Given the sink is writing, when a screen is opened and a list is scrolled
  with the D-pad, Then no write, JSON formatting or file IO runs on the main thread
  (unit test on the writer's thread; StrictMode-style check on device).
- **AC-7** [S1] Given any recorded event, when the log is searched for any title,
  search text, URL, token or query value used during the session, Then none is found. A
  unit test feeds events built from sensitive inputs through the allow-list encoder, and
  the device session is scanned with `tools/mobile-evidence-audit.sh`.
- **AC-8** [S1] Given `release`, `deploy` and `debug` builds, when screens, states and
  actions are logged, Then names are stable and readable, not obfuscated (explicit names or
  a verified mapping; check the minified `release` build on device).
- **AC-9** [S1] Given a cold start with logging on, when Home shows its first content,
  Then the log holds a `startup` event with the marks in order, and `nav`, `ready` and
  `act` lines for the screens opened, each `ready` carrying the ms since its screen opened.
- **AC-10** [S1] Given a failure in a ViewModel coroutine, a token refresh failure or an
  uncaught exception, when logging is on, Then an `err` line records the type, a sanitized
  message and top app frames, and none of the existing `log(...)` call sites change
  behaviour when logging is off.
- **AC-11** [S2] Given a long D-pad press on a list, when logging is on, Then one `key`
  line records the burst count and duration, not one line per repeat.
- **AC-12** [S2] Given a scroll on Home or History, when 10 s have passed, Then a `frames`
  line for that screen holds frame count, janky count and p50/p90/p99/max; the JankStats
  callback only copies numbers into an aggregator (no allocation-heavy work, reviewed in
  code).
- **AC-13** [S2] Given a key press answered later than 50 ms, when logging is on, Then a
  `key_lat` line records the latency to the next frame and that frame's duration.
- **AC-14** [S2] Given one foreground minute, when logging is on, Then one `mem` line
  appears, collected off the main thread, and `onTrimMemory` adds a line with the level.
- **AC-15** [S3] Given Home loads, when logging is on, Then each API call produces one
  `net` line with a route template (no ids, no query), status and phase timings; TMDB and
  intro-database calls carry their class; a failed call carries a failure class.
- **AC-16** [S3] Given a 5-minute playback, when logging is on, Then the log holds at most
  one `net_media` line per minute for segments, and none per segment.
- **AC-17** [S4] Given a playback that ends, when logging is on, Then one `play` line holds
  join time, rebuffer count and time, dropped frames, format switches, error count and
  content kind and the content id, and no title or stream URL.
- **AC-18** [S4] Given a previous process killed by an ANR or low-memory (API 30+ device),
  when the app starts with logging on, Then an `err` line records the exit reason; on API
  24-29 nothing is written and nothing fails.
- **AC-19** [S5] Given a pulled log, when `tools/` summary script runs on it, Then it prints
  per-screen time-to-content, key-to-frame p90, jank per screen, slowest routes and memory
  peaks (unit test on a fixture file).
- **AC-20** [all] Given the work is complete, when a 30-minute device session is recorded,
  Then the log volume is within the budget above and the owner can read a summary that
  names at least one slow place (owner judges whether the log is useful rather than noise).
- **AC-21** [all] Given the new code, when the unit suite and detekt run, Then they pass,
  and no probe adds measurable jank with logging off (owner judges; compare `frames` p90
  with logging off and on).

## SPLIT

Suggested order; each item is a shippable run with its own gate.

1. **telemetry-core** (M, perf): `core/telemetry` sink (event model, allow-list encoder,
   bounded rotating JSONL writer, caps, auto-off, `meta`), Settings toggle with indicator,
   `session`/`startup`, `nav`, `ready`, `act`, `err` (Timber tree plus uncaught chain). AC-1
   to AC-10. Everything else depends on it. Stable-names decision (AC-8) is made here.
2. **telemetry-frames-keys-memory** (M, perf): `metrics-performance` 1.0.0, `frames`,
   `key`, `key_lat`, `mem`. AC-11 to AC-14.
3. **telemetry-network** (M): OkHttp `eventListenerFactory`, route templating, media
   classification and aggregation. AC-15, AC-16. Order flexible with 2.
4. **telemetry-player-exit** (S): `PlaybackStatsListener` summary and
   `ApplicationExitInfo`. AC-17, AC-18. Merge after `player-overlays-port` lands
   (or hook the debug-info unit from `playback-controller-split`).
5. **telemetry-summary-tool** (S): offline script in `tools/` plus a fixture test. AC-19.
   Can start right after 1 with the first event types, and prove the log useful early.
   AC-20 and AC-21 close the series.

## Risks

- **Privacy** (highest): two owner decisions already guard viewing identity
  (`PlayerProgressDiagnostics.kt:5-15`, `tmdb-result-handling` Q1). Fields are an
  allow-list, the encoder takes typed event classes (no free map), messages pass
  `SensitiveRequestLogPolicy` (`core/logger/SensitiveRequestLogPolicy.kt:64-279`).
- **Self-inflicted jank**: main-thread writes, per-frame work in the JankStats callback or
  PSS sampling on the main thread would distort the measured numbers. Writer thread,
  batching, aggregation only.
- **Media flood**: the player client inherits the shared listener
  (`PlaybackController.kt:626-645`); classify before logging.
- **R8 names**: minified `release` (`app/build.gradle.kts:181-189`) obfuscates
  `simpleName`; needs explicit names or retrace (`PuberScreen.kt:7-10`).
- **Storage**: `getExternalFilesDir` can be null, TV boxes are small; caps and auto-off.
- **Merge friction** with `player-overlays-port` and `upstream-sync-triage` on
  `PlaybackController.kt` and `FlowComponent.kt`; slices 1 and 4 are most exposed.
- **Overlap**: `app-coroutine-scope` (one error sink) and `coil-shared-http` (image
  requests visible to the same listener for free; until then image loads are not logged).
- **Unverified**: PSS cost and rate limit, Media3 1.11.0 `PlaybackStats` signatures,
  Coil 3.5.0 event API, OkHttp 5.4 cache events, volume estimates. Each is checked on
  device or in the slice that uses it.
- **Device time** is scarce (`device-verification-debt`); five slices each need a device
  pass, so batch them where the lease allows.

## Owner answers (2026-10-15)

- **Q1 export:** `adb pull` plus a share/export action in Settings > Developer (FileProvider
  share of the closed log files, zipped). No server upload. Adds to S1 scope:
  **AC-1a** [S1] Given logging has produced files, when the user picks "Share log" in
  Settings > Developer, Then the Android share sheet opens with one archive of the closed
  files and nothing outside the log directory is exposed (FileProvider path restricted to
  that directory).
- **Q2 content identity:** content ids may be logged; titles and search text may not
  (search text only as length). Deliberate exception, for this opt-in log only, to the
  earlier rule in `PlayerProgressDiagnostics` / `tmdb-result-handling` Q1. AC-7 and AC-17
  amended above.
- **Q3 toggle:** as recommended - all build types including `prod` release, default off,
  Settings > Developer, auto-off after 7 days, visible indicator.
- **Split:** accepted; five runs created (telemetry-core, telemetry-frames-keys-memory,
  telemetry-network, telemetry-player-exit, telemetry-summary-tool).

## Open questions (as asked; answers above)

1. **Who records, and how does the data leave the device?** Recommended: the owner and
   testers only; files in app-specific external storage, pulled with `adb pull`. No upload,
   no share sheet in this series; a FileProvider share is a later run if needed. Wording of
   the toggle: "Session log (for diagnostics)", not "share data". Blocks: no.
2. **May the log hold content identity (titles, ids, search text)?** Recommended: no.
   Screen names, route templates, content kind (movie/series) and host class only; search
   text only as a length. This keeps the existing privacy decisions intact. Blocks: yes
   (changes the allow-list and AC-7).
3. **Which builds carry the toggle, and for how long?** Recommended: all build types
   including `prod` release, default off, under Settings > Developer, auto-off after 7 days
   with a visible indicator. If it were `debug`/`deploy` only, the shipped build would not
   be measured. Blocks: no.

Decided without asking (reasons in research.md: 8): first run does not include gzip of
closed files; the analysis script is its own slice (S5) because it proves the log is
useful.

## Return

KIND: feature  SIZE: L  TRACKS: perf (ui for the toggle row)
