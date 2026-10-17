# Readiness 2: showall-focus-restore-hardening

Verdict: **READY**

Read: spec `docs/superpowers/specs/2026-10-17-showall-focus-restore-hardening.md`
(cdc5a87c, still the committed wording), plan
`docs/superpowers/plans/2026-10-17-showall-focus-restore-hardening.md` (767872e4 plus
the uncommitted revision, `git diff` +60/-25, plan file only). Worktree head
767872e4 on base 35bfbbec, no production changes yet.

## Round 1 must-fix 1: AC-7 red-once mutation, resolved

The plan now defines M-AC7 (Task 3, "Red-once mutations", plan lines 336-344):
`repeat(DeepRows) {` becomes `repeat(2) {` in the deep-card test only. I traced it
against the androidTest at base: `awaitAnyFocus()` lands on card 1 (index 0; the
first case at `:49` relies on the same start). Two Down presses in a 3-column grid
reach index 6. The new guard `>= DeepRows * GridColumns` (15) fails with
"focus moved below the first viewport". The old guard `>= DeepRows` (`:75`)
accepts 6, so the old test would have stayed green. The mutation therefore shows
exactly the gap AC-7 closes. Task 3 keeps `repeat(DeepRows)` as the single line
to mutate (line 319). Done-when (lines 358-360) has qa record the failure message.
The deviation note is added for the Implementation record (lines 327-331).

M-AC2 re-checked against the planned `reconcile()` (lines 260-268) and the code
at base. With `else -> null`, the restored composition has saved id 16 absent, so
`restoreId` is null. `pendingEntry` is true, `reconcile()` returns at line 265
before `requestGrid()`, and `rememberFocusRequesterOnLaunch` does not refire
because `isFocusRequested` is `rememberSaveable`
(`FocusOnLaunchRequester.kt:51-61`). Nothing is focused, so the neighbour wait
times out and the test goes red. The plan's explanation (lines 83-85, 349-350) is
now correct. Round 1's note on Risk 2 wording is addressed in the plan.

Both mutations end with a revert, a clean `git diff --exit-code` and a green
re-run (lines 351-352). Neither is committed.

## Other round 1 notes, addressed

- Task 1 now runs `:app:detektAll` and lists it in Done when (lines 143-148).
- The JVM test names now use the `subject_behaviour` form `reconcile_...`, with U-ids
  in comments (lines 204-242).

## AC coverage (unchanged from round 1, re-checked)

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | Task 2 U-AC1a/b/c | yes |
| AC-2 | Task 2 U-AC2a + Task 3 rewrite + M-AC2 | yes |
| AC-3 | Task 1, 7 resolver cases | yes |
| AC-4 | Task 2 U-AC4 + captured-value red-once | yes |
| AC-5 | Task 2 U-AC5a/b/c | yes |
| AC-6 | Task 2 U-AC6a-e | yes |
| AC-7 | Task 3 rewrite + M-AC7 | yes (row wording being corrected) |
| AC-8 | Task 3 compile + qa-device class run with both red-once | yes (row wording being corrected) |
| AC-9 | qa-device D-M1..D-M3 | yes |
| AC-10 | Task 3 full gate | yes |

## Citations sampled this round (all resolve at 767872e4)

- `ShowAllScreenContent.kt:18` `remember` import, `:26` `FocusRequester` import,
  `:43` `GRID_COLUMNS = 3`, `:45` `RESTORE_FOCUS_ATTEMPTS = 3`, `:80`
  `rememberFocusRequesterOnLaunch()`, `:84` `rememberGridFocusMemory(state.items)`,
  `:107-108` grid requester and `onGridFocusChanged`, `:120`
  `focusRequester(focusMemory.restoreFocus)`, `:158-159` the two effects, `:169`
  `restoreFocus`, `:178` `inGrid`, `:209-210` per-attempt check and `runCatching`,
  `:216` `resolveRestoreId`. All match.
- `FocusTargetState.kt:298` `if (requestFocus()) return`. Matches.
- `app/build.gradle.kts:414-416` junit5, launcher, coroutines.test. Matches.
- `config/detekt/detekt.yml:756-759` `ReturnCount max: 3`; `:829-832`
  `UnusedPrivateProperty active`. Match.
- androidTest `:59-83`, `:75` old guard, `:85-111`, `:184-189` companion. Match.

## Build, order, scope

- Order 1 -> 2 -> 3 holds. Each task ends green with one commit. Step A of Task 2
  stays uncommitted until Step C uses `requestGrid`. `reconcile()` has 2 returns
  and `requestRestoreFocus()` has 3, which is within `ReturnCount`.
- Scope is still one production file, one new JVM test and one androidTest.
  Nothing from Out of scope is planned. No blocking open question.

## Must fix

None.

## Conditions and notes (not blocking)

1. Spec AC-8 (line 58), the AC-2/AC-8 Test design rows (lines 263, 276), R9
   (line 244) and D-M4 (line 329) still have the old wording. They say "old
   `>= DeepRows` guard" and "C would focus the first card". The conductor must
   copy the corrected wording into the spec before qa-device runs, because qa
   reads D-M4. Until then the plan's M-AC7/M-AC2 take precedence, and the plan
   records this as a deviation.
2. The R6 fallback in Task 3 (lines 360-363) uses "8 Down presses" with the guard
   `>= 7 * GridColumns`. With 24 items there are 8 rows, so 7 presses reach the
   last row. The 8th press, made from the last row, may move focus out of the grid
   or do nothing. If the fallback is needed, use 7 presses (guard `>= 21`). This
   applies only if qa reports that card 1 is still displayed.
3. Readiness 1's note on `SessionSaveableStateTest.kt` as a weak precedent for
   JVM snapshot state still stands. JVM snapshot state is feasible, as verified in
   round 1.
