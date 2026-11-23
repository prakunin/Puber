# Design (architect): startup-reveal-gate-on-slow-tv

Base `9158cf82`. Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`. ACs: `brief.md#acceptance-criteria`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| What keeps the splash after `ready` | Cover rule becomes `begun && forcedCause == null && !revealedOnce && !(ready && held.isEmpty())` (`core/startup/RevealGate.kt:109`); `inFlight` leaves the rule | `held` is already the set present at `ready` (`RevealGate.kt:64`) and already drains in `onImageFinished` (`RevealGate.kt:83`); AC-1, AC-3 |
| How G ends the hold | New `RevealCause.GRACE("grace")` in `core/startup/RevealObserver.kt:4-8`; new `RevealGate.onGrace(generation)` sets `forcedCause = GRACE` only when `generation` matches, `ready` is true and `forcedCause == null` | Reuses the existing first-cause-wins path (`RevealGate.kt:72,75,111-113`), so a later cap, forced reveal or G changes neither cause nor reveal; generation guard is the same one `markReady` uses (`RevealGate.kt:55-61`); AC-2, AC-4 |
| Value of G | `internal const val REVEAL_GRACE_MS = 300L` beside `REVEAL_CAP_MS` (`RevealGate.kt:9`), passed as constructor param `graceMs`; the spec fixes the final value from the S1 set and the code constant follows it | One place for the number; tests inject their own |
| Where G is armed | In the `markReady` frame block (`RevealGate.kt:60-67`) right after `held.addAll(inFlight)`, when `begun && forcedCause == null && held.isNotEmpty()`; it captures `markedIn` and calls the injected scheduler | The ready edge is the only moment `held` is known; an empty `held` reveals at once and needs no timer |
| Scheduler shape | New constructor param `scheduleGrace: (delayMs: Long, task: () -> Unit) -> Unit`, default no-op; called inside the gate lock, so it must only enqueue, never run `task` inline | Same pattern as `afterFrame` (`RevealGate.kt:20`) and `StartupMarks.schedule` (`core/telemetry/StartupMarks.kt:31`); existing tests keep the no-op and are unaffected |
| Production timer thread | `StartupReveal` gets a `graceHook` beside `frameHook` (`core/startup/StartupReveal.kt:13`), default no-op on the JVM, set in `installFrameWait()` (`StartupReveal.kt:49-51`, called at `PuberApp.kt:82`) to an async main-looper handler (`HandlerCompat.createAsync(Looper.getMainLooper()).postDelayed`, androidx.core); `resetForTests()` (`StartupReveal.kt:76-79`) restores the no-op | The fade and the `revealed` mark run on main anyway (`MainActivity.kt:81,88`), so an off-main flip draws no earlier; an async message is not held behind the Choreographer sync barrier, unlike the Compose `delay` of the cap (`MainActivity.kt:83-86`) |
| Telemetry | No change to `StartupMarks` or `TelemetryEncoder`: `markRevealCause` stores any cause (`StartupMarks.kt:84-88`), the encoder writes `cause.wire` (`core/telemetry/TelemetryEncoder.kt:73`); a `grace` line is written on held drain or the 5 s bound like `cap` (`StartupMarks.kt:111-115`) | AC-5 holds by the enum value; the line keeps `ready_drained` for the evidence tables |
| `inFlight` on `onRevealed` | Unchanged: total requests in flight (`RevealGate.kt:113`) | AC-2 asks for the requests still in flight |
| Cap and `revealNow` | Unchanged (`RevealGate.kt:9,72,75`, `MainActivity.kt:83-86`) | Q2; AC-4 |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data path changes |
| ViewModel state and actions | N/A: Home still calls `StartupReveal.markReady()` (`StartupReveal.kt:59`) as today |
| Gate (`core/startup`) | `RevealGate.kt`: `REVEAL_GRACE_MS`, params `graceMs` and `scheduleGrace`, arming in `markReady`, `onGrace(generation)`, cover rule on `held`; KDoc of `markReady` (`RevealGate.kt:49-53`) and class (`RevealGate.kt:11-15`) updated. `RevealObserver.kt`: `GRACE`, KDoc line 3. `StartupReveal.kt`: `graceHook`, wiring into the gate constructor (`StartupReveal.kt:15-39`), install and reset |
| Screen and DI module | N/A: `MainActivity.kt` and Koin modules unchanged |
| Compose content | N/A: `StartupSplashHost.kt` fade unchanged |
| Resources | N/A |
| Tests (`app/src/test/kotlin/com/kino/puber/core/startup/`) | `RevealGateTest`: `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` asserts reveal at the drain of `b`, not of `c` (rewritten, AC-3/AC-4); new cases for AC-1 to AC-4 with a collecting scheduler; `RecordingRevealObserver` needs no change (logs `revealed:GRACE:n`). `TelemetryEncoderTest`, `StartupMarksTest`: a `GRACE` case (AC-5). Names and counts belong to test-design.md |

## Alternatives rejected

| Alternative | Why not |
|---|---|
| G timer as a `LaunchedEffect` in `MainActivity` next to the cap | Needs the ready time exposed to Compose and runs behind the frame; the gate already owns the ready edge |
| G timer on a background thread or executor | Adds a thread at startup; the visible reveal still waits for main |
| Time-based rule (`clock() - readyMs >= G` in `update`) without a timer | Nothing guarantees an `update` call after G |
| Hold only images visible on screen (per-request tagging) | Needs Coil request tagging per composable; larger than the AC set |
| Separate `graceExpired` flag beside `forcedCause` | Second path for first-cause-wins; `forcedCause` already gives it |

## Risks

| Risk | Where | Covered by |
|---|---|---|
| Earlier reveal moves `onSplashGone` and the deferred-work latch earlier (`MainActivity.kt:91`, `core/startup/StartupSettled.kt:46`), so slots (TvHome +0.5 s ... WatchState catch-up +2.0 s) overlap the held decodes still running and the first key | `StartupSettled.kt:12,18`, `ui/feature/main/vm/MainVM.kt` slots | AC-7, AC-10 |
| G overshoot: the async message still waits for the current main-thread message (Home composition, image callbacks), so `revealed - ready` exceeds G on a busy start | `StartupReveal` grace hook | AC-6 max bound |
| Stale or doubled G: a G posted for one `begin()` firing after `reset()`/`begin()`, or after a cap reveal | `RevealGate.onGrace` generation and `forcedCause == null` guards | AC-4 unit |
| Concurrency: `onGrace` on main races `onImageFinished` from Coil threads draining `held` | Both in `update` under `lock` (`RevealGate.kt:107`); whichever runs first wins the cause | AC-1/AC-2 unit |
| Skeletons at fade end for held images (reverses startup-splash-preload Q2) | `StartupSplashHost.kt:35-44`, Home hero and cards | AC-8, owner |
| `startup` line gains `"cause":"grace"`; evidence scripts or tables that split only `ready`/`cap` misfile it | `RevealObserver.kt:4-8`, `TelemetryEncoder.kt:73` | AC-5; evidence tables list the value |
| Baseline profile: the new `RevealGate` constructor signature and lambdas are not in the generated profiles (`app/src/main/generated/baselineProfiles/baseline-prof.txt:28173`, `startup-prof.txt:17698`), so they run interpreted/JIT in S1 | `app/src/main/generated/baselineProfiles/` | AC-6; Q1 below |

## Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | Regenerate the baseline profile (`tools/generate-baseline-profile.sh`) so the changed `RevealGate` methods stay listed? | No: a few cold methods on the reveal path; regenerate in a later profile run if AC-6 misses | no |
