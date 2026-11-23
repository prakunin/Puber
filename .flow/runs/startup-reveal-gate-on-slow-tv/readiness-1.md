# Readiness 1: startup-reveal-gate-on-slow-tv

Verdict: **READY**

Plan: `docs/superpowers/plans/2026-11-23-startup-reveal-gate-on-slow-tv.md` (untracked in the worktree at the time of reading). Spec: `docs/superpowers/specs/2026-11-23-startup-reveal-gate-on-slow-tv.md`. Base `9158cf82`.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Task 2 | `imagesSettlingAfterReadyUncovers`, `sameRequestFinishedTwice_doesNotUncoverOthers` (P0 guard), `graceAfterHeldDrained_changesNothing` |
| AC-2 | Task 2, Task 3 | `graceWithHeldInFlight_revealsWithCauseGrace` (P0 red), `markReady_withHeldRequests_armsOneGraceOfGraceMs` |
| AC-3 | Task 2 | rewritten `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (P0 red), two P0 guards, `markReady_withNothingHeld_armsNoGrace` |
| AC-4 | Task 2 | `graceFromBeforeAnotherBegin_isDropped` (P0 red), six `RevealGateTest` guards and `StartupRevealTest`, four P1/P2 cases |
| AC-5 | Task 1 | `TelemetryEncoderTest.startupWritesGraceCause` (P0 red), two guards, `StartupMarksTest.graceRevealLineWaitsForHeldDrain` |
| AC-6, AC-7, AC-10 | qa-device after Task 3 | Manual checks steps 1-7, Perf track |
| AC-8 | qa-device, owner | Manual check step 6 |
| AC-9 | qa-device | Manual check step 8 |

## Citations sampled (worktree at base)

| Citation | Resolves to | OK |
|---|---|---|
| `RevealGate.kt:9` | `REVEAL_CAP_MS = 2_500L` | yes |
| `RevealGate.kt:60-67`, `:64` | `markReady` frame block, `held.addAll(inFlight)` | yes |
| `RevealGate.kt:72,75` | `onCap`, `revealNow` first-cause-wins | yes |
| `RevealGate.kt:81-86`, `:84` | `onImageFinished`, `onImagesIdle` line with `inFlight.isEmpty()` | yes |
| `RevealGate.kt:109`, `:113` | cover rule with `inFlight.isEmpty()`, `onRevealed(..., inFlight.size)` | yes |
| `RevealObserver.kt:4-8` | `RevealCause` enum | yes |
| `StartupReveal.kt:13`, `:49-51`, `:75-79` | `frameHook`, `installFrameWait`, `resetForTests` | yes |
| `PuberApp.kt:82` | `StartupReveal.installFrameWait()` | yes |
| `app/build.gradle.kts:352` | `implementation(libs.androidx.core.ktx)` (core 1.19.1, has `HandlerCompat.createAsync`) | yes |
| `StartupMarks.kt:31`, `:84`, `:109-115` | `schedule` param, `markRevealCause`, `emitWhenComplete` (only `FORCED` emits without ready) | yes |
| `TelemetryEncoder.kt:73` | `put("cause", it.wire)` | yes |
| `TelemetryEncoderTest.kt:136`, `StartupMarksTest.kt:14-20,31-35,217` | named tests and fixtures | yes |
| `RevealGateTest.kt:231-251` | `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd` (expects `revealed:READY:0` after `c` ends) | yes |
| `MainActivity.kt:83-86` | cap `LaunchedEffect` | yes |

## Checks

| Check | Result |
|---|---|
| Build green per task | Task 1 adds an enum value; the only `RevealCause` branch in main is the `==` at `StartupMarks.kt:115`, no exhaustive `when`. Task 2 adds defaulted constructor params, existing callers compile. Task 3 consumes Task 2. |
| Order | Task 1 -> 2 -> 3 by `Consumes`/`Produces` |
| Existing tests under the new cover rule | Every other `RevealGateTest`, `StartupImageActivityTest` and `StartupImageActivityUnmeasuredTest` case starts its requests before the ready frame, so they stay valid; only the rewritten test asserted waiting on a post-ready request |
| P0 unit rows | Each names the mutation it catches, is marked `red` or `guard`, names a backticked test |
| P0 device rows (AC-6, AC-7) | Manual by nature; defects named as R3, R4, R7 in the Risk profile, pass bounds in the Perf track |
| Detekt | `LongParameterList` is inactive (`config/detekt/detekt.yml:121-122`); the constructor reaches 6 params |
| Out of scope | Plan leaves `REVEAL_CAP_MS`, `MainActivity.kt`, `StartupSplashHost.kt`, `StartupSettled`, `StartupMarks`/`TelemetryEncoder` production code and the baseline profiles untouched |
| Blocking open questions | None; Q1-Q4 answered |

## Notes for the implementer and qa

| # | Note |
|---|---|
| 1 | The spec cites the baseline as `release-startup-compilation-device.md:139-260`; the file has 259 lines. |
| 2 | Task 3 has no JVM test of the `graceHook` wiring; R3 is proved only by `grace` causes and the AC-6 max bound on the box. |
