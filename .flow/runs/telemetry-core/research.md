> Inherited from usage-telemetry-log/research.md (2026-10-15); this run is slice S1 (telemetry-core) of that split. Owner answers Q1-Q3 are in usage-telemetry-log/brief.md, section 'Owner answers'.

# Research: usage-telemetry-log

Researched 2026-10-15 against master `39903d31`.

## 1. Request

> смотри, я хочу что программа логировала работу; что нажималось, и сколько открывало и что, какие запросы были; все нажатия, действия, ошибки; сценарии пользования. мне это нужно временно, возможно по галочке, предоставлять данные для улучшения программы. как использовать, я думаю что мы будем анализировать работу, искать места для оптимизации как скорости так и памяти, особенно плавности и отзывчивости. поэтому надо проанализировать что собирать в каком объеме чтобы действительно это был полезный лог а не мусор

## 2. Understanding

The owner wants a temporary, opt-in recorder on the device. It would note what the user did (keys, actions, screens opened), how long each step took, which network requests went out and what failed. The recorded sessions would then be read offline to find where the app is slow, janky or heavy on memory. The hard part is the selection, more than the plumbing. A raw stream of every key and every request turns into noise. A useful log ties each user step to a measured cost: a key press to the frame that answered it, a screen open to the first content, a request to its latency, a screen to its dropped frames and memory. It must not reveal what the account watches, and its volume must stay bounded. There is no backend, so the data stays on the device until someone exports it.

## 3. Current state in code

Logging today exists only in debug builds, and nothing is persisted:

- `app/src/main/java/com/kino/puber/PuberApp.kt:88-92`: Timber is planted (`LinkingDebugTree`) only when `BuildConfig.DEBUG`. In `deploy`, `release`, `nonMinifiedRelease` and `benchmarkRelease` every `log(...)` call is dropped. No other tree exists.
- `app/src/main/java/com/kino/puber/core/logger/Logger.kt:7-20`: the `Any.log(message)` and `Any.log(throwable, message)` helpers. Every message and throwable passes through `SensitiveRequestLogPolicy` first. About 40 call sites exist (VM refresh failures, token refresh, prefetch, paging, watch-state sync, crypto prefs). This is a ready-made, already-sanitized error stream that a file-backed tree could capture.
- `app/src/main/java/com/kino/puber/core/logger/SensitiveRequestLogPolicy.kt:64-279`: redaction of tokens, `client_secret`, `code`, Bearer headers, user-agent, and every query value on `/history`, `/watching`, `/items/{id}`, `/oauth2`, `/user`, `/device(s)`. It also redacts the item id in `/items/<id>`. It is the existing privacy contract, and any telemetry must reuse it, not invent a new one.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerProgressDiagnostics.kt:5-15`: an explicit precedent: "accepts no throwable or media parameters so progress failures cannot add account viewing identity to application logs". `tmdb-result-handling` recorded a matching owner decision (Q1: no logging in `SkipSegmentService`, privacy guards untouched).
- `app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt:10-14`: `DefaultExceptionHandler` is the coroutine failure sink for every `PuberVM.launch` (`core/ui/PuberVM.kt:86-99`). It only calls `log(t)`.
- No `Thread.setDefaultUncaughtExceptionHandler`, `ApplicationExitInfo`, `onTrimMemory`, `JankStats`, FrameMetrics, `androidx.tracing`, `reportFullyDrawn`/`ReportDrawn*` or OkHttp `EventListener` anywhere in `app/src/main` (grep). `gradle/libs.versions.toml` has no `metrics-performance` or `tracing` artifact.

Network: one place catches almost every request:

- `app/src/main/java/com/kino/puber/data/di/modules.kt:57-61`: a single shared `OkHttpClient` (DoH DNS). KinoPub (`data/api/KinoPubApiClient.kt:129-133`, Ktor on the OkHttp engine with `preconfigured`), TMDB (`TmdbApiClient.kt:88-89`), TheIntroDb, IntroDbApp, the endpoint and latency probes all build on it. An `eventListenerFactory` on this builder sees DNS, connect, TLS, TTFB, bytes, status and failure for all of them.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:626-645`: the player builds its media client with `okHttpClient.newBuilder()`, so it would inherit that listener. HLS segments would then flood the log at roughly one request per few seconds of playback. They must be classified and aggregated, not written one line each.
- `data/api/KinoPubApiClient.kt:157`: `CurlLogger` (full request/response dump) is installed only in DEBUG. It is the wrong shape for telemetry (bodies up to 1 MiB, `KtorPlugins.kt:26,58-96`) and must not be reused for it.
- Coil uses `coil-network-ktor3` (`app/build.gradle.kts` deps; `PuberApp.kt:94-120`) and does not share the OkHttp client (open run `coil-shared-http`). Image loads are invisible to an OkHttp listener and would need Coil's own event listener (`unverified` for the exact Coil 3.5.0 API).

Navigation, actions, keys, view state: central hooks exist:

- `core/ui/navigation/component/FlowComponent.kt:225-226`: `FlowCommandRunner` collects `router.events()` and already does `router.log("router command: $event")`. It is the single point where every `NavigateTo/Replace/NewRoot/Back/BackTo/FinishFlow` executes, so it is the place to stamp "screen open requested". A second collector is at `:708`, and tab routing is at `TabComponent.kt:104`.
- `core/ui/PuberVM.kt:82-84`: `updateViewState` is the one setter for every screen's state. Recording only class transitions there (for example `Loading -> Content`, by state type, not payload) gives time-to-content per screen at near-zero cost.
- `core/ui/PuberVM.kt:135-140`: `onAction` is open. 19 VMs override it, and the same 19 files call `super.onAction` (file count, not per-override verified). The action class name is the safe thing to record. Payloads carry item ids and must not be recorded.
- `MainActivity.kt:26-87` overrides no `dispatchKeyEvent`. The only global key hook is the Compose `onKeyEvent` in `FlowComponent.kt:140-145`, which wires `AppRemoteHotkeyHandler` (`ui/feature/root/component/App.kt:156`). That hook sees only keys nobody consumed, so `Activity.dispatchKeyEvent` is the correct tap for "every press".
- `MainActivity.kt:45-57`: the splash composes first and `App()` follows a `LaunchedEffect`. Startup has natural marks (process start, `Application.onCreate`, splash frame, `App` composed, Home first content).
- `core/ui/navigation/PuberScreen.kt:7-10`: screen `key` defaults to `javaClass.simpleName`. In minified `release` (`app/build.gradle.kts:181-189`) class names may be obfuscated. Telemetry needs explicit stable names or a retrace step. `deploy` (`:198-206`) is not minified.

Player: diagnostics exist, but only on screen:

- `PlaybackController.kt:213-270` (`Player.Listener`: state, errors, recovery), `:272-310` (`AnalyticsListener`: load host, format switches), `:421-431` (`ExoPlayer.Builder`, `addAnalyticsListener`), `:157` (`@OptIn(UnstableApi::class)` already in place).
- `ui/feature/player/vm/VideoRenderDiagnostics.kt` (render rate, lateness, decoder transitions) and `ui/feature/player/component/DebugOverlay.kt` feed the on-screen debug overlay. The overlay is toggled through `PlayerPreferencesRepository.kt:86-88` (`debugOverlayEnabled`, SharedPreferences) and the Settings "Developer" section (`ui/feature/device/settings/model/DeviceSettingsUiModels.kt:15`; toggle at `DeviceSettingsVM.kt:145,320-322`).

There is an on-device file precedent:

- `ui/feature/player/component/TimedActionDebugTrace.kt:10-37` appends JSONL to `getExternalFilesDir(null)/timed_action_debug.jsonl` on a single-thread daemon executor, gated to the `debug` and `deploy` build types. That is the exact write path and export route (`adb pull`) a session log would use. It is unbounded (no rotation), which a telemetry log must not copy.

Memory context:

- `app/src/main/AndroidManifest.xml:30`: `largeHeap="true"` (audit item M5).
- Image memory cache is 15 % of the heap, image disk cache is 100 MB (`PuberApp.kt:55-56`), the media cache is 512 MiB (`data/di/modules.kt:77-78`).
- minSdk 24, targetSdk 36, compileSdk 37 (`buildSrc/src/main/kotlin/Versions.kt:6-12`). FrameMetrics (API 24) is available on every supported device. `ApplicationExitInfo` (API 30) is not.

Export/share:

- `core/system/ContentSharer.kt:9-15` shares text only (`ACTION_SEND`, `EXTRA_TEXT`). No FileProvider exists, so sharing a file would be new work.

## 4. Related work

- **No duplicate.** No run, spec or plan covers collecting a usage or performance log. `DUPLICATE_OF: none`.
- `network-diagnostics` (done, PR #16; `docs/superpowers/plans/2026-08-21-network-diagnostics.md`): an on-demand speed and latency test, later replaced by the media-server speed test (`d3fa6576`). It is related in that it measures the network, but it is active and user-triggered rather than passive. `domain/interactor/diagnostics/NetworkDiagnosticsInteractor.kt` is its remnant.
- `memory-and-performance-fixes` (done): `docs/superpowers/evidence/2026-09-08-memory-and-performance-audit.md` was a **static** audit (M1-M5, P1-P12) with no runtime numbers. T1 was reverted after a device observation (Ken Burns at about 60 % CPU on KM6, `8eee2e53`). That is exactly the kind of finding a session log would surface systematically. Its TV pass is still pending (`device-verification-debt`, blocked on device).
- `perf-deferred` (idea): P3 next-episode warm-up, M4 VM outliving its Koin scope, P5 R8/largeHeap, P8-P12. Telemetry would supply the before/after numbers each of these needs (warm-up: player join time; M4/P5: heap and PSS per screen; P10: History focus-step frame cost).
- `startup-benchmark-entry` (idea): a compliant route to run `baselineprofile/src/main/java/com/kino/puber/baselineprofile/StartupBenchmarks.kt`. It is complementary: a lab measurement against field-session data. Startup marks added here would also show in Macrobenchmark traces if they are emitted as trace sections.
- `dependency-prune` (idea) plans to drop `ktor-client-logging`, which is unused (no `io.ktor.client.plugins.logging` import in `app/src/main`). Telemetry should not resurrect it.
- `app-coroutine-scope` (idea): "one injected application scope with an exception handler". It overlaps the error capture point, and the two should agree on one sink.
- `playback-controller-split` (idea): extract text-track matching and **debug info** from `PlaybackController`. If it lands first, player telemetry hooks onto the extracted debug-info unit.
- `coil-shared-http` (idea): once Coil uses the shared OkHttp client, image requests are seen by the same network listener for free.
- `player-overlays-port` and `upstream-sync-triage` are in `impl`. `PlaybackController.kt` and `FlowComponent.kt` are hot files, so expect merge friction (`46af3266`, `b110fa7b` are recent touches on PuberVM/FlowComponent paths).

## 5. External findings

- **JankStats** (`androidx.metrics:metrics-performance`). Stable `1.0.0`, 8 Oct 2025; `1.0.0-beta03` (27 Aug 2025) stopped recording on non-hardware-accelerated windows. The listener runs on the FrameMetrics thread on API 24+, and `FrameData` is reused per frame (copy, do not keep). `PerformanceMetricsState.getHolderForHierarchy(view).state?.putState(key, value)` tags frames with UI state (screen name, "scrolling", "player overlay"). Compose sample: remember the holder from `LocalView`. The default jank threshold is 2x the refresh interval (`jankHeuristicMultiplier`). On API 31+ it adds `frameOverrunNanos`. Google's own guidance is to **aggregate** per state and report in batches (sample `JankStatsAggregator`, report on pause), not to log every frame. Sources: https://developer.android.com/jetpack/androidx/releases/metrics, https://developer.android.com/topic/performance/jankstats
- **OkHttp EventListener.** `EventListener.Factory.create(Call)` returns a listener per call. Events: `callStart/End/Failed`, `dnsStart/End`, `connectStart/End/Failed`, `secureConnectStart/End`, `connectionAcquired/Released`, request/response headers and body start/end. Connection events fire only for new connections, not pooled reuse. The factory may return `EventListener.NONE` to sample (the official sample samples about 10 %). Public since OkHttp 3.11; the project is on 5.4.0, and cache events are not confirmed for 5.4 from the docs read (`unverified`). Sources: https://github.com/square/okhttp/blob/master/docs/features/events.md, https://javadoc.io/static/com.squareup.okhttp3/okhttp/3.14.7/okhttp3/EventListener.html, https://www.javadoc.io/static/com.squareup.okhttp3/okhttp/3.14.9/okhttp3/EventListener.Factory.html
- **Media3 PlaybackStats / PlaybackStatsListener.** Annotated `@UnstableApi` (the project already opts in). It gives per-playback join (startup) time (`getMeanJoinTimeMs`, `totalValidJoinTimeMs`), rebuffers (`totalRebufferCount`, `getTotalRebufferTimeMs`, `maxRebufferTimeMs`), `totalDroppedFrames`, video/audio format history, bandwidth (`totalBandwidthBytes`, `getMeanBandwidth`), and fatal/non-fatal error counts and history. This one summary per playback replaces per-segment logging. Read from the `release` branch source, not pinned to 1.11.0 (`unverified` for exact 1.11.0 signatures). Source: https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/analytics/PlaybackStats.java
- **Startup TTID/TTFD.** TTID is automatic (the `Displayed` line in logcat). TTFD needs `reportFullyDrawn`, or in Compose `ReportDrawn()`, `ReportDrawnWhen { predicate }` or `ReportDrawnAfter { suspend }`, plus `fullyDrawnReporter.addReporter/removeReporter` for async content. Source: https://developer.android.com/topic/performance/vitals/launch-time
- **Custom trace sections** (`androidx.tracing`, `trace("name") { }`) appear in Perfetto when the app is `profileable` and are captured by Macrobenchmark automatically. They are useful as a twin of the log marks for lab deep-dives. The artifact version was not read from its release page (`unverified`). Source: https://developer.android.com/topic/performance/tracing/custom-events
- **ApplicationExitInfo** (`ActivityManager.getHistoricalProcessExitReasons`) gives the reason for the last exits (ANR, crash, native crash, low memory and so on), PSS/RSS at exit, and an ANR trace stream. It is API 30+. The fetched reference page was truncated, so the details come from the model's own knowledge and are `unverified`. On API 24-29 TV boxes it is simply absent. Source: https://developer.android.com/reference/android/app/ApplicationExitInfo
- **Memory sampling cost.** `ActivityManager.getProcessMemoryInfo` is "significantly rate-limited" from Android 10 and returns the previous sample if called too often. No exact interval was found (`unverified`). `Debug.getMemoryInfo` (PSS) is the in-process alternative, and its cost was not measured in any source found (`unverified`; treat it as expensive and sample it at most about once a minute off the main thread). The memory index page lists ProfilingManager for heap dumps in the field. Sources: https://developer.android.com/topic/performance/memory, https://qiita.com/messhi/items/4ecad44568defa817714 (secondary), https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo

## 6. Options

### A. Structured local session journal, opt-in, bounded (recommended)

This option adds one `core/telemetry` sink: an event model, an in-memory buffer, and a background writer of JSONL into rotating files under `getExternalFilesDir("telemetry")`, read with `adb pull`. A Settings toggle in the "Developer" section turns it on, default off, and it switches itself off after N days (the "temporary" requirement). When off, every probe is a single volatile-flag check.

What it collects (each item exists to answer a perf question):

| Stream | Hook | Recorded | Not recorded |
|---|---|---|---|
| Session/startup | `PuberApp.onCreate`, `MainActivity`, Home `ReportDrawnWhen` | build type/version, device model, API, RAM class, `isLowRamDevice`; process-start→onCreate→first frame→Home content (ms) | account, android id |
| Navigation | `FlowComponent.kt:225` collector | command type, from/to screen name, t | screen args (item ids) |
| Screen readiness | `PuberVM.updateViewState` (`PuberVM.kt:82`) | state-class transitions per screen with t (time-to-content, time-to-error) | state payload |
| Keys | `MainActivity.dispatchKeyEvent` | keyCode, down/up, repeat bursts collapsed (one line per long-press with count and duration), current screen, eventTime | focused item identity |
| Key→frame | key eventTime + next JankStats frame | latency to the first frame after the key, plus that frame's duration | — |
| Actions | `PuberVM.onAction` base | action class name per screen | action payload |
| Frames | JankStats + `PerformanceMetricsState` (screen, overlay, scrolling) | per screen per 10 s window: frames, janky, p50/p90/p99/max ms; individual frames over 100 ms | per-frame lines |
| Network | `eventListenerFactory` on `modules.kt:58` | per API call: route template (`/v1/items/{id}`), method, status, DNS/connect/TLS/TTFB/total ms, bytes, reused connection, failure class; media segments aggregated per minute (count, bytes, p90 ms, failures) | query strings, hosts' tokens, bodies, raw URLs |
| Memory | timer (60 s foreground) + screen change + `onTrimMemory` | Java heap used/max, native heap, PSS (sparse), GC count (`unverified` API), image cache size, trim level | heap dumps |
| Player | `PlaybackController` + `PlaybackStatsListener` | per playback: join ms, rebuffers count/ms, dropped frames, bitrate/format switches, errors, recovery path | title, item/episode id, stream URL |
| Errors | a Timber tree planted when on; `DefaultExceptionHandler`; uncaught handler chain; `ApplicationExitInfo` on next start (API 30+) | exception type, sanitized message, top app frames | full traces with URLs |

Volume estimate (`unverified`, by arithmetic): about 0.3-0.5 MB of JSONL per active hour (keys and frame windows dominate). Cap it at 2 MB per file and 20 MB total, keep the newest files, and gzip closed files (optional).

- Pros: answers the owner's questions directly; every line joins to a screen and a time; privacy rules mirror `SensitiveRequestLogPolicy`; reuses the `TimedActionDebugTrace` export route; no backend.
- Cons: touches startup, DI, network, UI root, player and Settings, so device verification is mandatory. It adds one dependency (`metrics-performance` 1.0.0). It needs stable screen and action names under R8. It meets hot files in two `impl` runs.
- Size: **M** (crosses layers, touches networking, startup and the player). It may be split into M plus S (player and exit info as a follow-up) if triage wants smaller gates.

### B. File-backed Timber tree plus JankStats only

This option plants a rotating-file Timber tree when the toggle is on, so all existing `log(...)` lines persist in every build type. It adds JankStats per-screen aggregates and a few `log()` calls at navigation and key points.

- Pros: smallest change; reuses the sanitizer automatically.
- Cons: free-text lines, no schema, so analysis means grepping. There is no request timing, no time-to-content, no memory and no player stats. This is close to the noise the owner wants to avoid.
- Size: **S**.

### C. Lab-only instrumentation: trace sections plus Perfetto/Macrobenchmark

This option adds `androidx.tracing` sections at the same marks (navigation, state ready, key handling, request, player join) and makes the build `profileable`. The analysis happens in Perfetto captures and `startup-benchmark-entry` runs.

- Pros: precise frame and thread attribution; nothing persisted; zero cost when not tracing.
- Cons: does not record real usage scenarios over days, and needs adb plus a capture session each time. It does not meet "log what the user did".
- Size: **S** (it is a natural add-on to A: the same marks emit both a trace section and a journal event).

**Recommended: A.** Its marks should also emit trace sections (C) where it is cheap, so lab captures and field logs share names.

## 7. Risks

- **Privacy.** Item ids, titles, search queries, history and stream URLs reveal viewing identity, and the project has explicitly guarded against that twice (`PlayerProgressDiagnostics`, the `tmdb-result-handling` Q1 decision). Every stream needs an allow-list of fields, not a deny-list. Search text must never be logged, only its length or keystroke count.
- **Self-inflicted jank.** Writing on the main thread, per-frame lines, or formatting JSON in the JankStats callback would distort the very metric being measured. Writes must go off-thread and in batches, and the frame callback must only copy numbers into an aggregator.
- **Media flood.** The player's OkHttp client inherits the shared listener (`PlaybackController.kt:626`). Without classification it adds a line every few seconds per playback.
- **R8 names.** `release` is minified, so `simpleName` of screens, states and actions may be obfuscated. This needs explicit names or a mapping-based retrace.
- **Storage.** `getExternalFilesDir` can be null (the existing trace returns early). TV boxes have small storage, so hard caps and auto-off are required.
- **Overlap.** Possible conflicts with `player-overlays-port` and `upstream-sync-triage` (in `impl`) on `PlaybackController`/`FlowComponent`, and design overlap with `app-coroutine-scope` (error sink).
- **Device verification** is mandatory per AGENTS.md (startup, networking, settings UI, player). `device-verification-debt` shows device time is scarce.
- **API gaps.** `ApplicationExitInfo` exists only on API 30+. The PSS sampling cost and rate limits are unverified.

## 8. Open questions

1. **Who records, and how does the data leave the device?** Only the owner's own devices with adb, or also other users? Recommended: the owner and testers only. Files go in app-specific external storage and are pulled with `adb pull`. There is no upload and no share-sheet in this run; a FileProvider share can follow if needed.
2. **Content identity in the log?** Recommended: none. Screen names, route templates and content type (movie/series) only; no titles, item or episode ids, search text, or hosts beyond a class (api / tmdb / media-cdn / image). Hosts of media shards could be kept as a hash if CDN comparison matters.
3. **Which builds carry the toggle?** Recommended: all build types including `prod` release, default off, under Settings → Developer. It switches itself off after 7 days, with a visible "recording" indicator in Settings. If it lived only in `debug` or `deploy`, it would miss how the shipped build behaves.
4. **Scope of the first run.** Recommended: everything in Option A except the gzip of closed files. If triage wants a smaller first gate, split the player summary and `ApplicationExitInfo` into a follow-up S run.
5. **Analysis tooling.** Recommended: include a small offline summary script under `tools/` (per-screen time-to-content, key→frame p90, jank per screen, slowest routes, memory peaks). Writing it is what proves the log is useful rather than noise.

## 9. Sources

Files:
- `AGENTS.md`
- `docs/architecture/README.md`
- `docs/architecture/ui-and-compose.md`
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `buildSrc/src/main/kotlin/Versions.kt`
- `app/src/main/AndroidManifest.xml`
- `PuberApp.kt`
- `MainActivity.kt`
- `core/logger/{Logger,SensitiveRequestLogPolicy,LinkingDebugTree}.kt`
- `core/coroutine/CoroutinesErrorHandler.kt`
- `core/error/DefaultErrorHandler.kt`
- `core/ui/PuberVM.kt`
- `core/ui/navigation/{AppRouter,PuberScreen,AppRemoteHotkeyHandler}.kt`
- `core/ui/navigation/component/FlowComponent.kt`
- `core/system/ContentSharer.kt`
- `data/di/modules.kt`
- `data/api/{KinoPubApiClient,KtorPlugins}.kt`
- `data/repository/PlayerPreferencesRepository.kt`
- `ui/feature/player/vm/{PlaybackController,VideoRenderDiagnostics,PlayerProgressDiagnostics}.kt`
- `ui/feature/player/component/TimedActionDebugTrace.kt`
- `ui/feature/device/settings/model/DeviceSettingsUiModels.kt`
- `ui/feature/device/settings/vm/DeviceSettingsVM.kt`
- `baselineprofile/.../StartupBenchmarks.kt`

Docs: `docs/superpowers/evidence/2026-09-08-memory-and-performance-audit.md`, `docs/superpowers/plans/2026-09-08-memory-and-performance-fixes.md`.

Runs: `network-diagnostics`, `perf-deferred`, `memory-and-performance-fixes`, `startup-benchmark-entry`, `dependency-prune`, `app-coroutine-scope`, `playback-controller-split`, `coil-shared-http`, `device-verification-debt`, `tmdb-result-handling`, `player-overlays-port`, `upstream-sync-triage`.

Commits: `39903d31` (HEAD), `8eee2e53` (T1 revert), `d3fa6576` (speed test), `c0663e0d` (render-timing diagnostics), `46af3266`, `b110fa7b`.

URLs:
- https://developer.android.com/jetpack/androidx/releases/metrics
- https://developer.android.com/topic/performance/jankstats
- https://github.com/square/okhttp/blob/master/docs/features/events.md
- https://javadoc.io/static/com.squareup.okhttp3/okhttp/3.14.7/okhttp3/EventListener.html
- https://www.javadoc.io/static/com.squareup.okhttp3/okhttp/3.14.9/okhttp3/EventListener.Factory.html
- https://github.com/androidx/media/blob/release/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/analytics/PlaybackStats.java
- https://developer.android.com/topic/performance/vitals/launch-time
- https://developer.android.com/topic/performance/tracing/custom-events
- https://developer.android.com/reference/android/app/ApplicationExitInfo
- https://developer.android.com/topic/performance/memory
- https://developer.android.com/reference/android/app/ActivityManager.MemoryInfo
- https://qiita.com/messhi/items/4ecad44568defa817714
