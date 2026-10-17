# Readiness 1: showall-focus-restore-hardening

Verdict: **NOT READY** (one plan fix; everything else holds)

Read: spec `docs/superpowers/specs/2026-10-17-showall-focus-restore-hardening.md`
(cdc5a87c), plan `docs/superpowers/plans/2026-10-17-showall-focus-restore-hardening.md`
(767872e4), worktree at base 35bfbbec plus those two doc commits (`git diff 35bfbbec --stat`
shows only the two docs).

## AC coverage

| AC | Plan task | Test design row | Note |
|----|-----------|-----------------|------|
| AC-1 | Task 2 (U-AC1a/b/c, red on Step A) | yes | Traced by hand against the Step A adapter: old job cancelled in `onFrame(1)`, new job asks for card 5 three times, live path no-ops on `inGrid == false` -> red; against Step C `reconcile()` -> `restore:6:true` |
| AC-2 | Task 2 (U-AC2a) + Task 3 (androidTest rewrite) | yes | |
| AC-3 | Task 1 (7 resolver cases) | yes | |
| AC-4 | Task 2 (U-AC4 + red-once with a captured value) | yes | Captured-once revert gives 3 restore events instead of 1 -> red, as claimed |
| AC-5 | Task 2 (U-AC5a/b/c) | yes | |
| AC-6 | Task 2 (U-AC6a-e) | yes | |
| AC-7 | Task 3 | yes | red-once mutation is not workable, see Fix 1 |
| AC-8 | Task 3 (compile) + qa-device class run | yes | depends on Fix 1 |
| AC-9 | handed to qa-device (Task 3 Done when, D-M1..D-M3) | yes | |
| AC-10 | Task 3 full gate | yes | |

## Citations sampled (all resolve at base)

- `ShowAllScreenContent.kt:43,45` `GRID_COLUMNS`, `RESTORE_FOCUS_ATTEMPTS`; `:80` `rememberFocusRequesterOnLaunch()`; `:84` `rememberGridFocusMemory(state.items)`; `:107-108`, `:119-120`, `:125` grid/card modifiers; `:158-159` the two effects; `:163-213` `private class GridFocusMemory` with `restoreFocus` at `:169`, `inGrid` at `:178`, per-attempt check at `:209`, `runCatching` at `:210`; `:216-220` `private fun resolveRestoreId` - all match.
- `FocusTargetState.kt:298` `if (requestFocus()) return` - matches (Boolean return).
- `VideoItemUIState.kt:49-53` data class `VideoItemUIState(id, title, imageUrl, bigImageUrl, ...)` - matches.
- `app/build.gradle.kts:414-416` `junit5`, `junit5.launcher`, `coroutines.test` - matches.
- `SubtitleTrackMergerTest.kt:11-12,26` `ParameterizedTest`/`CsvSource` - matches. `AppUpdateInteractorTest.kt:18,38` `runTest` - matches.
- `config/detekt/detekt.yml:756-759` `ReturnCount max: 3`; `:829-832` `UnusedPrivateProperty active` - matches.
- `ShowAllScreen.kt:59` calls `ShowAllScreenContent(state, onAction)` - matches.
- `FocusTargetResolver.kt:10-24` `resolveFocusedItemId(previousItems, updatedItems, focusedItemId)` - matches (needs the previous list, as D7/D8 say).
- `ShowAllScreenContentFocusTest.kt` cases at `:33-57/:59-83/:85-111/:113-135/:137-161`, companion `ItemCount = 24`, `DeepRows = 5` at `:184-189` - matches.
- Weak: `SessionSaveableStateTest.kt:3,28-30` is cited (plan line 70, spec D6) as proof that `mutableStateOf`/`Snapshot` work in JVM tests. It only imports `MutableState` and gets `null` back from a saver; no JVM test in `app/src/test` creates `mutableStateOf` or calls `withFrameNanos`. I checked feasibility independently: `runtime-android-1.12.0` `classes.jar` (the BOM 2026.08.00 runtime) references no `android/*` class, so snapshot state and `withFrameNanos` with an injected `MonotonicFrameClock` run on the plain JVM. Not blocking.
- `androidx.compose.ui.test.isDisplayed(SemanticsNodeInteraction): Boolean` exists in `ui-test-android-1.12.0` (`AssertionsKt`), so the AC-7 assertion compiles.

## Build and order

- Task 1 is a behaviour-preserving seam plus pure resolver tests; it compiles and commits green. Task 2 keeps the unused `requestGrid` uncommitted until Step C uses it (detekt `UnusedPrivateProperty`), and the red runs stay local. Task 3 only touches the androidTest. Dependency order 1 -> 2 -> 3 is correct; no task leaves the build red.
- `reconcile()` has 2 explicit returns, `requestRestoreFocus()` 3: within `ReturnCount max 3`.
- Scope: one production file, one new JVM test, one androidTest. Nothing from Out of scope (live-path C, other grids, first-entry rail, shared helper) is planned. Q1-Q4 are answered; none blocks.

## Must fix

1. **The AC-7 red-once mutation cannot be red** (plan lines 78-79 and 322-324, also spec Test design AC-8 row and D-M4). The plan has qa run the rewritten deep-card test "with the old `>= DeepRows` guard" and expects red. The old guard is weaker than the new one (`>= 5` instead of `>= 15`), and the new "card 1 not displayed" assertion stays in place, so this mutation can only stay green. qa then cannot produce the evidence AC-8 requires ("the AC-2 and AC-7 rewrites have each been seen red once"). Define a mutation that shows the new guard catches what the old one let through. For example, temporarily replace the 5 Down presses with 2: focus lands on index 6. The old guard accepts that (`6 >= 5`), but the new guard (`>= 15`) and/or the off-viewport assertion fail. Record it in Task 3 "Done when" and in the hand-off to qa-device, and note it under Deviations as the conductor's correction of the spec's D-M4 wording.

## Non-blocking notes (for the implementer and the reviewers)

- Spec risk 2 and the I-AC2 row say that with the resolver's `else` branch reverted, "C would focus the first card". It would not: `restoreId` would be null, and `reconcile()` returns before C. Nothing gets focused, so the test still goes red (timeout). The red-once for AC-2 is valid; only the explanation is wrong.
- Task 1's checks leave out detekt. It is first run in Task 2. A detekt finding introduced in Task 1 would sit in a committed task until then. Consider adding `:app:detektAll` to Task 1's checks.
- The plan's test names (`reloadDropping...`) do not follow the `subject_behaviour` form that the spec's test design asks for. Cosmetic; detekt `FunctionNaming` excludes tests.
