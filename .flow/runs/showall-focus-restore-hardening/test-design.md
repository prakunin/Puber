## Test design

Base 35bfbbec. Production citations are `ShowAllScreenContent.kt:NN` at base. Levels (role addendum): `unit` (JUnit 5, JVM, no Robolectric), `instrumented` (only `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=<fqcn>`, run by qa-device), `device` (manual, D-pad, evidence file).

### Risk profile

P = probability 1-3, I = impact 1-3. Score >= 6 needs a test at the level where it fails.

| # | Risk | P | I | Score | Covered by |
|---|------|---|---|-------|------------|
| R1 | `pendingEntry` outlives the first grid focus, or a loop ignores `contentFocusActive()`: a reload or append steals focus from the open rail | 2 | 3 | 6 | U-AC4, U-AC6a/b/c at unit (decision logic); D-M2 at device |
| R2 | Race itself: reload drops the opener inside the entry window and nothing is focused (the defect) | 3 | 3 | 9 | U-AC1a/b/c (red first); not reproducible in instrumented (attempt 1 normally attaches first) |
| R3 | Fallback C masks a broken targeted path, or fires early / twice / while content focus is inactive (lands on first card) | 2 | 3 | 6 | U-AC5a/b/c; I-AC2 asserts the exact neighbour, so a C landing on another card is red |
| R4 | Seam refactor (D6) changes behaviour or recomposition (`restoreId`/`pendingEntry` read observed, so focus moves recompose the grid) | 2 | 2 | 4 | I-AC8 (existing opener / live / inactive cases stay green); review of `Snapshot.withoutReadObservation` |
| R5 | Fake clock does not model recomposition, so JVM tests prove logic, not attach timing (architect risk 3) | 3 | 2 | 6 | Accepted at unit level by design; attach timing covered by I-AC2, I-AC8, D-M1, D-M2. Stated in the test KDoc |
| R6 | AC-7 viewport flake: row 0 still displayed on a tall emulator, so the "deep" case is not deep | 2 | 2 | 4 | Assert card 1 absent before restore; fall back to 8 Down presses (24 items, 3 columns) if it is still shown |
| R7 | Re-entry while rail is open: loops stop at the first check but `pendingEntry` stays true (one frame per reload) | 1 | 2 | 2 | Accepted; U-AC4 pins the stop. Noted, no extra test |
| R8 | Body leaves composition (Loading/Error) and returns: fresh memory restores again | 1 | 2 | 2 | Accepted; same behaviour as base (`LaunchedEffect(Unit)`) |
| R9 | Test passes for the wrong reason: AC-2 rewrite still runs the live path, or AC-7 guard stays `>= DeepRows` (checked by M-AC7: 2 Down presses, index 6, red under the new `>= 15` guard) | 2 | 2 | 4 | Each rewritten androidTest is seen red once against a reverted fallback/guard (AC-8), recorded in the evidence |

### Unit seam (JVM) shared setup

`GridFocusMemoryTest` in `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt`. No `MainDispatcherExtension`, no MockK for the class under test, no `FakeResourceProvider` (no strings). `VideoItemUIState` is a plain data class: `VideoItemUIState(id = n, title = "Card n", imageUrl = "", bigImageUrl = "")`.

- Real `GridFocusMemory`, constructed with `lastFocusedId = mutableStateOf<Int?>(saved)`, `lastFocusedIndex = mutableStateOf(idx)`, `items = { itemsVar }`, `contentFocusActive = { activeVar }`, and two counting lambdas: `requestRestore` and `requestGrid`.
- `requestRestore` models "the requester is attached only to the target card": `{ restoreCalls++; itemsVar.any { it.id == memory.restoreId } && memory.restoreId == target }`. Where the test names the card it expects to be focused, the lambda records `memory.restoreId` per call in a list, so the assertion is on the card that got the request, not on a call count alone.
- Fake `MonotonicFrameClock`: `withFrameNanos(block)` runs `block(frame * 16_000_000L)`, increments a frame counter, then calls an optional `onFrame(n)` hook and `currentCoroutineContext().ensureActive()` (so a cancel issued in the hook is observed at once). Tests run `runTest { withContext(clock) { ... } }`; no sleeps, no real time.
- Items change the way `LaunchedEffect(items)` does it: the hook mutates `itemsVar`, cancels the running `reconcile()` job, and a new `launch { memory.reconcile() }` starts. A helper `changeItems(newList)` does cancel + relaunch.
- Test names follow the repo form `subject_behaviour`.

### Traceability

| AC | Level | Priority | Test | Proves |
|----|-------|----------|------|--------|
| AC-1 | unit | P0 | `GridFocusMemoryTest.reloadDroppingTheOpenerBeforeAnyAttemptRetargetsToTheNeighbour` (U-AC1a): saved id 5, index 4, items 1..10; `onFrame(1)` replaces items with 5 removed and restarts the loop. Assert `requestRestore` succeeded on card 6 (index 4), `restoreId == 6`, `requestGrid` never called | The restart re-resolves the target and focuses the neighbour before any attempt had succeeded |
| AC-1 | unit | P0 | `...reloadOnTheLastAttemptStillGetsAFreshBudget` (U-AC1b): same, change fired in `onFrame(3)`. Assert focus lands on card 6 within a fresh 3-attempt budget (frames 4..6 or fewer) | The budget is per list change, not shared; closes the "reload on attempt 3" hole option A left |
| AC-1 | unit | P0 | `...openerDroppedAsLastItemRetargetsToTheNewLastCard` (U-AC1c): saved id 10 at index 9 of 10, a reload leaves 1..9 | Clamped position under the race, end-of-list edge |
| AC-2 | instrumented | P0 | `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry` (rewrite of `:86-111`): `var shown = items` (plain var, read inside `setContent`); after the Down presses, `shown = items.filterIndexed { i, _ -> i != index }`, then `emulateSavedInstanceStateRestore()`; assert `shown[index]` is focused. Also assert the removed card's title does not exist | Only the restored composition sees the new list, so the construction-time resolver (entry fallback) runs, not the live-grid path. Red-once mutation M-AC2 (resolver `else` -> `null`): nothing is focused, so the test is red by timeout waiting for `shown[index]` to be focused. ~~Earlier text "C would focus the first card" is superseded (readiness-1 must-fix 1): with `else` -> null no fallback card is focused~~ |
| AC-2 | unit | P1 | `GridFocusMemoryTest.openerAbsentAtConstructionFocusesTheClampedIndexCard` (U-AC2a): saved id 5 index 4, list built without 5. Assert `restoreId == items[4].id` before `reconcile()` and the request lands on it; second case index 40 on a 10-card list, lands on the last card | JVM mirror of the above, deterministic, includes the clamp |
| AC-3 | unit | P0 | `GridFocusMemoryTest.resolveRestoreId_*` (one `@ParameterizedTest` or seven tests; `junit-jupiter-params` is already used in `app/src/test`): present -> the opener; gone mid-list (id 5 gone, index 4) -> the card now at index 4; gone and was last (id 10 gone, index 9, 9 cards) -> new last; index beyond a shorter list (index 40) -> last; empty list -> null; null id -> null; null id with non-zero index -> null | The pure resolver, all branches including both clamp ends |
| AC-4 | unit | P0 | `GridFocusMemoryTest.contentFocusFlippingOffBetweenAttemptsStopsFurtherRequests` (U-AC4): `requestRestore` always fails; `onFrame(1)` leaves `active = true`, `onFrame(2)` sets `active = false`. Assert `restoreCalls == 1` and `requestGrid` not called. Companion `contentFocusInactiveFromTheStartMakesNoRequest`: `restoreCalls == 0`, `requestGrid == 0` | The per-attempt re-check. Red if it is reverted to a value captured once before the loop (verified once, recorded) |
| AC-5 | unit | P0 | `...everyTargetedAttemptFailingWhileActiveCallsTheGridRequesterOnce` (U-AC5a): `requestRestore` always false, active; run to completion. Assert `restoreCalls == 3`, `gridCalls == 1`, and call order (grid call recorded after the third restore call, via a shared event list) | C exists, runs once, only after the last targeted attempt |
| AC-5 | unit | P0 | `...gridRequesterIsNotCalledWhenContentFocusIsInactive` (U-AC5b): active false from attempt 1 or flipped at attempt 3. Assert `gridCalls == 0` | C respects the rail |
| AC-5 | unit | P1 | `...gridRequesterIsNotCalledWhileTargetedAttemptsAreStillPendingOrAfterASuccess` (U-AC5c): success on attempt 2 -> `gridCalls == 0`; list change cancels the loop on attempt 2 -> `gridCalls == 0` for the cancelled loop; empty list (restoreId null) -> `restoreCalls == 0`, `gridCalls == 0` | No parallel fallback, no C after success, no C for the cancelled coroutine, no C on an empty list |
| AC-6 | unit | P0 | `...appendWhileFocusIsInTheGridMakesNoRequest` (U-AC6a): call `onGridFocusChanged(true)` then `onCardFocused(3, 2)`, change items by appending cards; run `reconcile()`. Assert `restoreCalls == 0 && gridCalls == 0` | Focus already landed: pending cleared, no steal |
| AC-6 | unit | P0 | `...remapKeepingTheFocusedCardMakesNoRequest` (U-AC6b): same, items replaced by copies of the same ids with changed fields | Remap (the `remapLoadedItems` path) does not re-request |
| AC-6 | unit | P1 | `...focusLeavingTheGridToTheRailThenReloadMakesNoRequest` (U-AC6c): construct with a saved id, `onGridFocusChanged(true)` (clears pending), `onGridFocusChanged(false)` (focused card still in list so `inGrid` false), reload that drops the card, `reconcile()`. Assert no request | Pending never re-arms; live path stays gated on `inGrid` (parent M2) |
| AC-6 | unit | P1 | `...firstLaunchWithNoSavedIdMakesNoRequest` (U-AC6d): saved id null; `reconcile()` after items arrive. Assert `restoreCalls == 0`, `gridCalls == 0` | `rememberFocusRequesterOnLaunch` keeps ownership of the first launch |
| AC-6 | unit | P1 | `...liveGridFocusedCardRemovedRetargetsToTheNeighbour` (U-AC6e): `onGridFocusChanged(true)`, `onCardFocused(5, 4)`, list drops 5, `reconcile()`. Assert request lands on the card at index 4 | Existing live path (`:198-204`) preserved by the merge into one `reconcile()` |
| AC-7 | instrumented | P0 | `showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry` (rewrite of `:60-83`): local `GridColumns = 3`, `DeepRows = 5`; guard `index >= DeepRows * GridColumns` (15). Before `emulateSavedInstanceStateRestore()`, assert no displayed node with exact text "Card 1" (`onAllNodesWithText("Card 1")` is empty or none `isDisplayed`; exact match, not substring, so "Card 10" does not count). After it, the opener is focused | The opener was really off the viewport; the 3-frame loop has to attach a card that is not composed at restore start. If "Card 1" is still displayed on the qa emulator, raise to 8 Down presses (all 24 items = 8 rows) and use `DeepRows * GridColumns` accordingly |
| AC-8 | instrumented | P0 | `ShowAllScreenContentFocusTest` full class via `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`: opener restored, live neighbour, inactive content unchanged and green; plus the rewrites under AC-2 and AC-7 | No regression from the seam refactor and `reconcile()` merge. Red-once evidence: M-AC2 (resolver `else` -> `null`; AC-2 test red by timeout, nothing focused) and M-AC7 (AC-7 test with 2 Down presses instead of `DeepRows`: index 6 passes the old `>= 5` guard but fails the new `>= 15` guard, so the guard assertion is red). ~~Superseded: "old `>= DeepRows` guard on a tall viewport"~~. Both recorded then reverted |
| AC-9 | device | P0 | D-M1, D-M2 below | Back from Details restores the same card; rail not stolen |
| AC-10 | unit/static | P0 | `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` | Unit tests, detekt, lint |

Count by priority (test rows): P0 = 12 (U-AC1a/b/c, I-AC2, AC-3, AC-4, U-AC5a/b, U-AC6a/b, I-AC7, I-AC8 class run), P1 = 5 (U-AC2a, U-AC5c, U-AC6c/d/e), P2 = 0. AC-9 (device) and AC-10 (check.full) are gates counted under MANUAL.

### Failing-first (bugs)

Defect: the entry restore does not re-resolve `restoreId`, and the live path is gated on `inGrid` (`:178,194-204`). Old code, read with `git -C <wt> show 35bfbbec:app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt`: `restoreOnEntry` calls `requestRestoreFocus()` against the constructor-time `restoreId`; `restoreWhenFocusedCardLeft` returns when `inGrid` is false.

- The failing-first test is `GridFocusMemoryTest.reloadDroppingTheOpenerBeforeAnyAttemptRetargetsToTheNeighbour` (U-AC1a), with U-AC1b alongside it. It cannot compile on the base commit (the seam is `private`, no lambdas), so it is red in this order, matching design-architect "Plan notes":
  1. Step 1: seam refactor that keeps behaviour (D6: `internal`, injected lambdas, `restoreOnEntry` and `restoreWhenFocusedCardLeft` still separate; the AC-3 tests go green here). Add U-AC1a/b/c written against `reconcile()`-shaped calls via a small test adapter that calls `restoreOnEntry()` then `restoreWhenFocusedCardLeft()`. They fail: the entry loop keeps asking for card 5, the live path no-ops with `inGrid == false`, no card is focused, `restoreCalls` all false.
  2. Step 2: D1-D5. U-AC1 goes green; U-AC4, U-AC5, U-AC6 go green at the same time (they exercise `reconcile()`).
  The tester observes the red locally and records it in the evidence file; no commit contains a red test.
- U-AC4 is shown red once by capturing `contentFocusActive()` into a local before the loop, then reverted.
- U-AC5a is shown red on step-1 code (there is no grid fallback: `gridCalls == 0`).
- There is no failing-first instrumented test: the race cannot be made red on a device (attempt 1 normally attaches the opener first). The planner records this in the plan, as the brief requires.

### UNTESTABLE

- The frame ordering after `withFrameNanos` (does the continuation run before or after that frame's recomposition): not testable on the JVM seam (the fake clock has no recomposition) and not deterministic on a device. Not an AC; the design is insensitive to it. Covered only indirectly by I-AC2/I-AC8 and D-M1.
- AC-1 on a real device: the real race (reload inside the ~3 frame window) cannot be forced reliably. Stand-in: U-AC1a/b/c plus D-M3 (best effort). The report must say which was achieved.

### Manual device checks

Run by qa-device with a lease, `adb -s <serial>`, the worktree's own APK installed with `tools/android-apk-install-preserve`, D-pad keyevents only (`input keyevent KEYCODE_DPAD_DOWN` etc.), no taps. Evidence has no tokens or raw logcat.

**D-M1. Back from Details restores the same card (AC-9, M1 regression)**
1. Open a Show all grid (from a section's "Show all").
2. Wait for the first card to be focused. Press DOWN 4 times and RIGHT once so the focused card is below the first viewport.
3. Press CENTER on the card to open Details. Wait until Details is displayed.
4. Press BACK.
- Expected: Show all returns with the same card focused (the focus ring on the same title); no key press needed.
- Must not happen: the first card focused, nothing focused (the next D-pad key is needed to see a focus ring), the rail opened, crash.
5. Press DOWN once. Expected: focus moves to the card below the restored one (the grid is live).

**D-M2. Rail not stolen (AC-9, M2 regression, R1)**
1. From the state in D-M1 step 5, press LEFT until the rail opens (the grid loses focus).
2. Open Details from the rail's destination only if it does not change the Show all stack; otherwise skip to step 3: with the rail open, trigger a Show all list reload (if one of the D-M3 triggers is available).
3. Observe 3 seconds with no input.
- Expected: the rail keeps focus; the grid does not take it back.
- Must not happen: focus jumps into the grid, the rail collapses without input.
4. Open the rail's entry, return to the Show all tab (the tab-stack case from the parent run) and confirm the grid shows a focused card (parent run behaviour) and the rail is not re-opened.

**D-M3. Opener-removed path on a device without playback (AC-9, best effort)**
There is no way to reach the opener-removed path with Back from Details alone. The ShowAllVM reload triggers are `displaySettingsChanges`, `watchStateChanges` (re-pages when hide-watched is on), the bookmark toggle on a card, and `onReturnedContentChanges` (the player returns a `ContentChangeSet`, so it needs playback and is not allowed here). Candidate triggers without playback:
1. Display setting: with a Show all grid in the back stack and the opener card a title that the setting would remove (for example hide-watched with the opener being a watched title), change the setting while Show all is not the visible screen, then return to Show all. This works only if the Settings screen can be reached with the Show all route still on the stack (qa checks the tab stack). The setting is a local preference; qa records its original value and puts it back at the end. If the setting is persisted account-side or the owner has not authorized the toggle, skip and say so.
2. A card bookmark toggle from the grid is an account mutation: not allowed without explicit authorization. Do not use it.
- Expected when step 1 is reachable: after return, the card now at the opener's position is focused; never "nothing focused".
- Must not happen: no focus on return, the first card focused when the opener's neighbour exists, crash.
- If neither trigger is reachable, the report says: "opener-removed path verified by JVM U-AC1/U-AC2 and instrumented I-AC2 only". That meets AC-9 as the brief wrote it.

**D-M4. Instrumented class (AC-8)**
Run `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest` on the leased emulator (never `connected*AndroidTest`). Expected: all five cases pass. Record the two red-once observations (M-AC2: resolver `else` -> `null`, red by timeout; M-AC7: 2 Down presses instead of `DeepRows`, index 6 passes old `>= 5` and fails new `>= 15`; ~~old wording "AC-7 with the old guard on a tall viewport" superseded~~) in the evidence file; both changes are reverted before the commit.

### Perf track

Not on (tracks none).
