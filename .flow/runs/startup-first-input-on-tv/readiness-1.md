# Readiness 1: startup-first-input-on-tv

Verdict: **NOT READY**

Base c506d0bd. Spec and plan as in the worktree at f4e982e8 (plan untracked).

## Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass. AC-1: Tasks 1, 2, 5, 8. AC-2: Tasks 1-4. AC-3: Task 6. AC-4: Task 8. AC-7: Task 9. AC-9: full gate in Tasks 5, 9, 12. AC-5, AC-6, AC-8: qa-device verify after Task 12 (plan Global constraints), Manual checks 2-4 |
| Citations resolve at c506d0bd | Pass, all sampled: `RevealGate.kt:45-50,52,55,57,78,79`, `StartupImageActivity.kt:16-41`, `StartupReveal.kt:14-19,27,53-58`, `KeyProbe.kt:9,71-106,108-119,126-132`, `MainActivity.kt:44,59-71,73-79,90,93`, `TelemetryEncoder.kt:67-73,114-118`, `TelemetryEvent.kt:130-139,207-215`, `StartupSettled.kt:12-19,26-59`, `StartupKeyFilter.kt:25-33`, `domain/di/modules.kt:86`, `PuberApp.kt:8,68,109`; tests `KeyLatencyMatcherTest.kt:25,48,57,66,74,70,88,101,107`, `FrameProbeTest.kt:148`, `DispatchWithProbeTest.kt:13,23,38,46,54`, `StartupSettledTest.kt:24,40,58,72,88,103`, `StartupSettledTestSupport.kt:6`, `DetailsPrefetcherTest.kt:260`, `TelemetryEncoderTest.kt:73,128,134`, `StartupImageActivityTest.kt:18,29,41,51`, `RevealGateTest.kt:13,83-136`, `KeyProbeGateTest.kt:29-30` |
| No task leaves the build red; dependency order | Pass. Every caller of a changed signature is in the task that changes it (`dispatchWithProbe`, `KeyProbe.onKey`, `StartupImageActivity(...)`, `onImagesBusy`, `KeyLat.latMs` asserts, `onSplashGone` users via `unsettledStartup` and `DetailsPrefetcherTest.kt:260`). `StartupRevealTest.kt:20` and `StartupImageLoaderTest.kt:17` still compile after Task 5. `KeyProbeGateTest` cases survive Task 3 (`:37-61`) |
| P0 rows: defect named, red/guard, backticked test | Pass except Fix 2 |
| Out of scope respected; blocking questions answered | Pass. No fade or poster-size change; B-Q1, B-Q2, D-Q1, D-Q2 answered |

## Fix list

1. Spec contradicts the amended AC-1. Design Decisions row "Cause when the grace ends" still says `cause: ready` with `in_flight` > 0, and the "New `startup` fields" row lists only `ready`/`cap`/`forced`; the AC-1 amendment, spec Decisions (Design Q1), plan Task 8 and `RevealGateTest.graceExpiry_reportsCauseGrace` use `grace`. Update both rows to `grace` (phase B) and mark the Design open question 1 as answered.
2. `KeyProbeGateTest.droppedKeyDoesNotBlockTheNextHandledKey` (P0 red) does not catch its named mutation once Task 3 lands: with the pending list, a dropped key that still arms no longer blocks the next key, it only adds its own `key_lat`. Its red state in Task 4 then rests on the compile failure alone. Plan Task 4 must state the assertion: exactly one `KeyLat` after the frame, with the handled key's latency (none for the dropped key).

## Notes for implementation

- Task 6 step 5: the dev flavor of both 8428c946 and base takes `versionCode = Versions.DebugVersionCode` (`app/build.gradle.kts:54`, `buildSrc/src/main/kotlin/Versions.kt:22`, 2090000000), so installing the reference APK is not a downgrade; the plan's "not measured" branch should not apply.
