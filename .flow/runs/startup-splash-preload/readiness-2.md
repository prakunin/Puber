# Readiness 2 (delta 1): startup-splash-preload

Final judge, 2026-11-21. Scope: plan Tasks 9-10 against spec `Architect delta 1` and the gate-1 delta decision (spec Decisions, state.md). Tasks 1-8: `readiness-1.md`. Citations read with `git show 948a279a:<path>`; Coil API read with `javap` on `coil-core-api.jar` 3.6.3.

**Verdict: READY**

## AC coverage

The plan's `AC coverage (delta 1)` table holds: every amended or new AC (AC-4, AC-5, AC-6, AC-7, AC-10, AC-12, AC-13, AC-14, AC-15) maps to Task 9, Task 10 or qa-device after Task 10. All ten names in spec `Tests to add` appear in the Task 9 and Task 10 tables, each marked `red` or `guard` with steps that state the defect (missing latch, inert `onImagesBusy`, edge reporting, unbalanced removal).

## Citations sampled (at 948a279a)

| Citation | Resolves to |
|---|---|
| `core/startup/RevealGate.kt:10-48`, `:15`, `:21-24`, `:26-28`, `:30-32`, `:34-38`, `:40-43` | class; `covered`; `begin`; `markReady`; `onCap`; `remainingCapMs`; `reset` |
| `core/startup/StartupReveal.kt:8`, `:15`, `:21-22` | `gate`; `markReady`; `resetForTests` |
| `ui/feature/root/component/App.kt:183-185` | prompt comment; `if (open) StartupReveal.markReady()` |
| `PuberApp.kt:100-126`, `:125` | `newImageLoader`; `.build()` |
| `test/.../core/startup/RevealGateTest.kt:12-63`, `:32-37` | six existing tests; `markReady_uncoversImmediately` |
| `MainActivity.kt:62`, `:76-84` | covered-key drop; `begin()` and cap effect |
| `core/ui/PuberVM.kt:94` | `StartupReveal.markReady()` on `StartupContentState` |
| coil3 `EventListener` | abstract class, public no-arg constructor; `resolveSizeEnd(ImageRequest, Size)`, `onSuccess`, `onError`, `onCancel(ImageRequest)`; `ImageLoader.Builder.eventListener(EventListener)`; `Size.ORIGINAL` |

`mockk` is on `testImplementation` (`app/build.gradle.kts:421`), so the `mockk<ImageRequest>()` fixtures build.

## Build and order

Task 9 compiles alone (`revealNow` and its `App.kt` caller land together); Task 10 consumes `RevealGate.onImagesBusy` from Task 9. The existing `RevealGateTest` rows stay green under the new rule: `markReady_doesNothingBeforeBegin` and `reset_uncoversAndForgetsTheStart` hold because `covered` needs `begun` and `reset()` clears it. `PuberVMRevealTest` and `HomeVMTest` share the global `StartupReveal`; `resetForTests()` clearing `imagesBusy` and the in-flight set (Task 10) keeps them independent.

## Notes for implementation and qa (non-blocking)

| # | Note |
|---|---|
| 1 | AC-15 and the AC-4/6/12 amendments live only in the spec `AC delta (gate 1)` table; `brief.md` and the spec AC list still show the old text. The gate reads the delta table as the AC of record. |
| 2 | Device rows (M1 amended, M3, M8, AC-13, AC-15) need emulator-5554 or another AVD; state.md Log records a Room v4 install there, so a head install would be a schema downgrade. qa-device checks the on-device DB version first and asks the owner before installing over v4. |
| 3 | A Home that keeps issuing image requests (pager or LazyRow prefetch) holds `imagesBusy` until the cap; AC-13 `revealed` median is the check, and the evidence reports how many starts revealed at the cap. |

## Open questions

None blocking: delta Q1-Q3 answered (spec Decisions, 2026-11-21 gate 1 delta, delta Q2, delta Q3).
