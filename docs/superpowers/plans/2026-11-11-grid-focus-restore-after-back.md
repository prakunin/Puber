# Grid focus restore after Back (Collections, CollectionDetail, Show all live fallback) - plan

**Spec:** `docs/superpowers/specs/2026-11-11-grid-focus-restore-after-back.md`
**Goal:** After a root push and Back, CollectionDetail focuses the card that opened Details, Collections keeps restoring its opener as on base (guarded, not changed), and Show all focuses its grid when the focused card leaves the list and every restore attempt fails.

**Delta after baseline:** the Collections production change is withdrawn; Task 2 is now a guard test only. Reasons, the AC delta and the dropped test rows: `.flow/runs/grid-focus-restore-after-back/design-architect.md#delta-after-baseline` (main checkout). Tasks 1 and 3 are unchanged except AC-5 now covers CollectionDetail only.

**After verify round 1:** Task 4 fixes `qa-report.md#round-1` D1 (test seam) and D2 (context menu). Reasons: `.flow/runs/grid-focus-restore-after-back/design-architect.md#addendum-after-verify-round-1` (main checkout), decisions D14-D16.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/user1/orca/workspaces/Puber/grid-focus-restore-after-back && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`.
- Full gate: `cd /home/user1/orca/workspaces/Puber/grid-focus-restore-after-back && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- androidTest compile: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`. One unit class: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests <fqcn>`.
- Every command runs in the worktree through `./tools/agentw`, never `./gradlew` (the guard hook refuses it).
- The implementer never touches a device and never runs `connected*AndroidTest`. Instrumented runs belong to qa-device: `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=<fqcn>`.
- One task = green fast gate + green tests of the touched code + one commit. Message: one imperative English sentence, no prefixes, never a `Co-Authored-By` line. The implementer stages and proposes the message; the conductor commits.
- No commit carries a red test. Red runs are made on uncommitted code and recorded under Implementation record > Red run.
- Detekt over production and unit-test sources: `ReturnCount` max 3 (`config/detekt/detekt.yml:756-759`), `ComplexCondition` max 3 (`:81-83`), `UnusedPrivateProperty` active (`:829-832`). `MagicNumber` and `FunctionNaming` exclude test sources (`:680-682`, `:337-341`).
- Scope: the two production files (`ShowAllScreenContent.kt`, `CollectionDetailScreenContent.kt`) and the test files named in the tasks. No change to `CollectionsScreenContent.kt` or any Collections production file, `FocusTargetState.kt`, Bookmarks, History, Search, `FlowComponent`, `MainScreenComponent`, VMs, DI, screens or resources. No `CollectionsFocusMemory`. No `TvDialogFocusRestorer` in CollectionDetail (spec D2); the `LocalContentFocusActive` override of Task 4 is the only dialog-related change. No shared grid helper (spec Out of scope).
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else. Paths are relative to the worktree; `main/` means `app/src/main/java/com/kino/puber/`, `test/` means `app/src/test/kotlin/com/kino/puber/`, `aTest/` means `app/src/androidTest/kotlin/com/kino/puber/`. Lines are at base e9184b79.

## Preconditions

| # | What | Owner |
|---|---|---|
| P1 | Done: device baseline on e9184b79 in `docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-baseline.md`. Collections, Bookmarks and History restore on base; CollectionDetail does not | qa-device |
| P2 | Red checkpoint (below), between Task 1 and Task 2 | implementer writes, qa-device runs |

### Red checkpoint (no commit)

1. On unchanged production code, write the two androidTest files of Tasks 2 and 3 exactly as specified there.
2. Run the androidTest compile; it must pass.
3. Stop and report to the conductor. qa-device runs on a leased emulator:
   `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.collections.component.CollectionsScreenContentFocusTest`, the same for `...collections.detail.component.CollectionDetailScreenContentFocusTest`.
4. Expected (marks in the Mark column of the task tables): `red` tests fail, `guard` tests pass. A guard red on base is a finding for the conductor, not a test to adjust; for the Collections guard it means the test seam does not reproduce the device's host entry (design delta R9). The result goes into Red run.

---

## Tasks

### Task 1: Show all runs the grid fallback on the live path

**Covers:** AC-7
**Files:**
- Modify: `main/ui/feature/showall/content/ShowAllScreenContent.kt` - in `GridFocusMemory.reconcile()` (`:219-227`), line `:226` `if (pendingEntry && contentFocusActive()) requestGrid()` becomes `if ((pendingEntry || focusedCardLeft) && contentFocusActive()) requestGrid()`; `focusedCardLeft` is the local at `:221`. Rewrite the comment at `:225` to cover both the pending entry and the focused card leaving the list. Nothing else in the file changes.
- Test: `test/ui/feature/showall/content/GridFocusMemoryTest.kt`

**Consumes:** `GridFocusMemory` (`ShowAllScreenContent.kt:181-251`); test `Harness` (`GridFocusMemoryTest.kt:372-415`), `run` (`:354-357`), `FakeFrameClock` (`:359-370`).
**Produces:** nothing later tasks use.

- [x] Replace `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingNeverFocusesTheGrid` (`GridFocusMemoryTest.kt:241-249`) with `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid`: same setup (`Harness(null, 0, cards(1..10), restoreOverride = { false })`, `onGridFocusChanged(true)`, `onCardFocused(5, 4)`, `itemsVar = without(5, cards(1..10))`, `run(h)`); expects `listOf("restore:6:false", "restore:6:false", "restore:6:false", "grid")`.
- [x] Add `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingWithContentInactiveNeverFocusesTheGrid`: same setup plus `onFrame = { if (it == 3) activeVar = false }`; expects `listOf("restore:6:false", "restore:6:false")` and `"grid" !in h.events`.
- [x] Red run: the unit class on unchanged production code; the first new case fails (no `grid`), the second passes. Record it.
- [x] Change `:225-226`.
- [x] Run `GridFocusMemoryTest` (every case green) and the fast gate; commit: `Focus the Show all grid when the focused card leaves the list and every restore attempt fails`.

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests com.kino.puber.ui.feature.showall.content.GridFocusMemoryTest` passes and `git diff HEAD~1 --stat` lists only these two files.

---

### Task 2: Collections guard test (no production change)

**Covers:** AC-1, AC-9 (guard)
**Files:**
- Test (new, written at the red checkpoint): `aTest/ui/feature/collections/component/CollectionsScreenContentFocusTest.kt`
- No production file.

**Consumes:**
- `CollectionsScreenContent(state, onCollectionClick, onLoadMore)` (`main/ui/feature/collections/component/CollectionsScreenContent.kt:32-37`); `CollectionsViewState.Content(collections)` (`main/ui/feature/collections/model/CollectionsViewState.kt:12-14`), `CollectionUIState` (`:20-27`).
- Mechanism under guard, unchanged: saveable index `CollectionsScreenContent.kt:58`, fallback requester `:59` on the card at that index `:75,82-83`, grid `focusRestorer(savedItemFocusRequester)` `:69`, index written on focus `:88`.
- Host shape to model: `Box(Modifier.focusRequester(...).focusRestorer().focusGroup())` (`main/ui/feature/main/component/MainScreenComponent.kt:296-302`).
- Harness: `StateRestorationTester` as `aTest/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt:35-59`; helpers `focusedTitle`, `onAllNodesFocusedWith`, `performDirection` and constants as `:181-202`.

**Produces:** nothing later tasks use.

Test (JUnit 4, `createComposeRule()` from `androidx.compose.ui.test.junit4.v2`, `PuberTheme`). Fixture: `CollectionUIState(id = n, title = "Collection $n", imageUrl = "", wideImageUrl = "", count = 1)` for `n` in `1..24`. Content: `val host = remember { FocusRequester() }`; `Box(Modifier.focusRequester(host).focusRestorer().focusGroup()) { CollectionsScreenContent(CollectionsViewState.Content(collections), onCollectionClick = {}, onLoadMore = {}) }` inside `CompositionLocalProvider(LocalContentFocusActive provides true)`; keep `host` in a test field written from the composition so the test can call it after the restore.

| Test | Mark | Steps | Expect |
|---|---|---|---|
| `collectionsHostEntryAfterReentryFocusesTheFocusedCard` | guard | `onNodeWithText("Collection 1").requestFocus()`; DOWN, RIGHT (settle each); assert the focused title is not `Collection 1`; `emulateSavedInstanceStateRestore()`; `waitForIdle()`; `host.requestFocus()` on the UI thread | the same title focused (`waitUntil` + `assertIsFocused`) |

Steps:
- [x] Red checkpoint already holds the file; qa-device reported it green on base.
- [x] Fast gate and androidTest compile; commit: `Guard the Collections opener focus after returning from a collection`.

**Done when:** fast gate and androidTest compile pass, the red checkpoint recorded this test green on base, and `git diff HEAD~1 --stat` lists only this test file.

---

### Task 3: CollectionDetail remembers and restores the focused card

**Covers:** AC-2, AC-3, AC-5 (CollectionDetail context menu), AC-9
**Files:**
- Modify: `main/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt` - `Content` branch (`:46-86`)
- Test (new, written at the red checkpoint): `aTest/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`

**Consumes:**
- `rememberReconciledItemFocus(rowKey, items, isTargetRow, initialFocusedItemId, requestAfterFrame, restoreAcrossProcess, onRowEmpty): ReconciledItemFocusState` (`main/core/ui/uikit/component/moviesList/FocusTargetState.kt:39-48`); `ReconciledItemFocusState` (`:22-32`): `targetItemId`, `focusRequester`, `rowHasFocusRef`, `onItemFocused`.
- Wiring to copy: `main/ui/feature/bookmarks/component/BookmarksScreenContent.kt:73-79` (call), `:118-124` (grid modifier), `:127`, `:135-147` (card modifier).
- `VideoItemHorizontal(modifier, state, onClick, ..., onContextMenu)` (`main/core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:48-63`).

**Produces:** nothing later tasks use.

- [x] After `contextMenuItem` (`:47`) add `val itemFocus = rememberReconciledItemFocus(rowKey = "collection_detail", items = state.items, isTargetRow = true, requestAfterFrame = true, onRowEmpty = {})`.
- [x] Grid modifier `:68` becomes `Modifier.fillMaxSize().focusRestorer(itemFocus.focusRequester).onFocusChanged { itemFocus.rowHasFocusRef[0] = it.hasFocus }`.
- [x] `VideoItemHorizontal` at `:72-76` gains `modifier = Modifier.then(if (item.id == itemFocus.targetItemId) Modifier.focusRequester(itemFocus.focusRequester) else Modifier).onFocusChanged { if (it.isFocused) itemFocus.onItemFocused(item.id) }`.
- [x] Fast gate, androidTest compile; commit: `Restore the focused card when returning to a collection`.

Instrumented tests, `CollectionDetailScreenContentFocusTest`: fixture `VideoItemUIState(id = n, title = "Card $n", imageUrl = "", bigImageUrl = "")` for `n` in `1..24`, state `CollectionDetailViewState.Content(title = "Collection", items = shown)` (`main/ui/feature/collections/detail/model/CollectionDetailViewState.kt:11-14`), `onAction = {}`, `onItemClick = {}`, inside `CompositionLocalProvider(LocalContentFocusActive provides true)`. Harness and helpers as in Task 2, without the host box. Reentry tests start with `onNodeWithText("Card 1").requestFocus()`, then DOWN and RIGHT.

| Test | Mark | Steps | Expect |
|---|---|---|---|
| `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` | red | `StateRestorationTester`; focus move; `emulateSavedInstanceStateRestore()`; nothing requested | the same title focused |
| `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` (replaced in Task 4) | red | landing before the first restore attempt: `mainClock.autoAdvance = false`, `emulateSavedInstanceStateRestore()`, `advanceTimeByFrame()` until `Card 1` exists, `onNodeWithText("Card 1").requestFocus()`, assert it holds focus, `autoAdvance = true` | the same title focused |
| `collectionDetailFocusesTheFirstCardOnFirstLoad` | red | plain `setContent`, no focus request | `Card 1` focused within the timeout |
| `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | guard | `shown` is `mutableStateOf`; focus move; long OK on the focused card as `TvContextMenuLongPressTest.kt:48-57` (`keyDown(Key.Enter)`, `advanceEventTime(500)`, wait for the first action, `advanceEventTime(50)`, `keyUp(Key.Enter)`); `shown = shown.filter { it.id != focusedId }`; advance 500 ms; `waitForIdle()` | the first menu action (`R.string.context_menu_watch_movie`, read from the target context) has a focused parent, as `TvContextMenuLongPressTest.kt:159-169`; no `Card n` node is focused |

If the default-landing variant cannot put the landing before the first attempt with clock control, stop and record it under Blocked; do not drop the landing or reorder it after the restore. If `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` is red after the change, stop and record it under Blocked (a dialog restorer is outside this design, D2).

**Done when:** fast gate and androidTest compile pass; `git diff HEAD~1 --stat` lists only the production file and this test file.

---

### After Task 3 (conductor, qa-device)

- Full gate green.
- qa-device runs the two new classes plus `com.kino.puber.ui.feature.home.component.HomeCollectionBackFocusTest`, `com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`, `com.kino.puber.ui.feature.showall.content.ShowAllRailCloseFocusTest`, `com.kino.puber.ui.feature.history.component.HistoryScreenContentTest` through `make ... itest`; all green (AC-1 guard, AC-3, AC-8, AC-9).
- qa-device installs the worktree APK with `tools/android-apk-install-preserve` and runs Manual 2-7 of the spec's Test design, with the delta: Manual 2 expects the base result (AC-1 guard), Manual 6 checks AC-6 only.

---

### Task 4: CollectionDetail keeps focus in the context menu; restore test enters through a host

**Covers:** AC-2, AC-5, AC-9 (verify round 1, D1 and D2)
Lines in this task are at HEAD 5ea2899e, not base.
**Files:**
- Modify: `main/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt` - `Content` branch (`:50-116`)
- Modify: `aTest/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt` - replace the test at `:50-69`; add a host composable next to `Detail` (`:111-122`)

**Consumes:**
- `LocalContentFocusActive` (`main/core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:30`, `staticCompositionLocalOf { true }`).
- Parking inside the helper, unchanged: inactive content clears `rowHasFocusRef` (`main/core/ui/uikit/component/moviesList/FocusTargetState.kt:69-71`); the entry request (`:234-239`) and the pending request (`:240-247`) are keyed on `contentFocusActive` and wait while it is false.
- Host shape and call: `aTest/ui/feature/collections/component/CollectionsScreenContentFocusTest.kt:38-49` (test field `host`, `Box(Modifier.focusRequester(requester).focusRestorer().focusGroup())`) and `:69` (`composeRule.runOnUiThread { host!!.requestFocus() }`).
- Clock-held frame loop to reuse: the test file `:56-62`.

**Produces:** nothing later tasks use.

Production change (D15):
- After `contextMenuItem` (`:51`) add `val contentFocusActive = LocalContentFocusActive.current`.
- Wrap the helper call (`:52-58`) and the `Box` (`:59-115`) in `CompositionLocalProvider(LocalContentFocusActive provides (contentFocusActive && contextMenuItem == null)) { ... }`, with a one-line comment: the open menu parks the card focus requests as the rail does.
- Imports: `androidx.compose.runtime.CompositionLocalProvider`, `com.kino.puber.core.ui.uikit.component.modifier.LocalContentFocusActive`. Nothing else in the file changes.

Test change (D14): delete `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` (`:50-69`) and add:

| Test | Mark | Steps | Expect |
|---|---|---|---|
| `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` | red | `StateRestorationTester`; content = `Detail(items)` inside the host box; focus move (`focusAwayFromTheFirstCard()`); `mainClock.autoAdvance = false`; `emulateSavedInstanceStateRestore()`; `advanceTimeByFrame()` until `Card 1` exists (bound `MaxLandingFrames`); `runOnUiThread { host!!.requestFocus() }`; `autoAdvance = true` | the same title focused (`waitUntil` + `assertIsFocused`) |

The other three tests in the class are untouched.

Steps:
- [x] Write the test change; androidTest compile.
- [ ] Red run (no commit): set the production file to base with `git show e9184b79:app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt > app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`; stop and report. qa-device runs `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest`. Expected: the new test fails at its focus wait. Then `git checkout -- app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`. If the new test passes on base, stop and record it under Blocked.
- [x] Production change; fast gate; androidTest compile (full gate left to the conductor).
- [ ] Stop and report. qa-device runs the same class on the changed code. Expected: all four tests pass, including `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` (red at HEAD 5ea2899e, `qa-report.md#round-1`).
- [ ] Commit: `Keep focus in the collection context menu when its items change and enter the restore test through a host`.

**Done when:** qa-device reports `CollectionDetailScreenContentFocusTest` 4/4 on the changed code and the new test red on base; full gate green; `git diff HEAD~1 --stat` lists only the two files of this task.

### After Task 4 (conductor, qa-device)

- qa-device reruns `com.kino.puber.ui.feature.home.component.HomeCollectionBackFocusTest` (AC-3) on the worktree APK.
- Manual 3, 4 and 7 again on the worktree APK. Manual 7 adds one observation (design D16, not an AC): after BACK closes the menu, the opener card is focused.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- Task 1: edbdd0f5 Focus the Show all grid when the focused card leaves the list and every restore attempt fails
- Task 2: 7102569d Guard the Collections opener focus after returning from a collection
- Task 3: 5ea2899e Restore the focused card when returning to a collection

**Red run** (P0 `red` tests on the unchanged code):

- red: `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid` - `AssertionFailedError` at `GridFocusMemoryTest.kt:242` (events lack `grid`).
- guard, green on base: `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingWithContentInactiveNeverFocusesTheGrid` - the behaviour (no grid request while content is inactive) exists on base, as the plan expects.
- red: `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` - `ComposeTimeoutException` (5000 ms) at the focus wait after `emulateSavedInstanceStateRestore`.
- red: `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` - `ComposeTimeoutException` (5000 ms) at the focus wait.
- red: `collectionDetailFocusesTheFirstCardOnFirstLoad` - `ComposeTimeoutException` (5000 ms) at the wait for the first card.
- guard, green on base: `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`, `collectionsHostEntryAfterReentryFocusesTheFocusedCard` - qa-device red checkpoint (device emulator-5554). Not rerun after the change: the implementer cannot run instrumented tests, so the context-menu guard is unverified on the changed code (readiness note 2: the neighbour request may race the dialog).

- dropped after verify round 2 (D3): `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard`, deferred to idea run `collection-detail-early-entry-test`.

- review round 1, #4 (F2): `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` now also closes the menu with BACK (`sendKeyDownUpSync` into the menu's `BackHandler`) and asserts the removed card's neighbour is focused (D16). Not run by the implementer; qa-device runs it.

**Deviations from the plan:** none.

**Files touched outside the plan:** none.

**For the reviewers:** the context-menu guard needs the post-change device run; without `TvDialogFocusRestorer` it is the at-risk test.

**Blocked:** none.

**Squash** (13 commits to 4, before ship; the code tree is unchanged):

| New commit | Old commits |
|---|---|
| 1a4109ba Show all fallback | edbdd0f5 |
| 883e5a0d CollectionDetail restore | 5ea2899e, 3e1530f0, f7d887a7, 4efeed73 |
| 194428c7 Collections guard test | 7102569d |
| docs commit (its own SHA cannot be listed here) | 511590c1, 91358af6, 4faa15b4, 3fe58218, c907907f, cfd0a1f3, 5b020264 |

The SHAs in **Commits** above are the pre-squash ones.

## Readiness

Readiness applies to the pre-delta plan; delta reviewed by the conductor.

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

# Readiness 1 - grid-focus-restore-after-back

Spec 511590c1, plan 91358af6, base e9184b79. Worktree has no production diff against base.

**Verdict: READY**

## AC coverage

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 | Task 2 | `collectionsFocusReturnsToTheFocusedCardAfterReentry`, `collectionsFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry`, `restoreEntry_*` |
| AC-2 | Task 3 | `collectionDetailFocusReturnsToTheFocusedCardAfterReentry`, `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` |
| AC-3 | Task 3, After Task 3 | `collectionDetailFocusesTheFirstCardOnFirstLoad`, `HomeCollectionBackFocusTest` |
| AC-4 | Task 2 | `resolveCollectionRestoreId_*`, `collectionsFocusReturnsToTheSameCollectionAfterPagesAppendBeforeReentry` |
| AC-5 | Tasks 2, 3 | `collectionsPendingRestoreDoesNotTakeFocusFromTheRail`, `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` |
| AC-6 | Task 2 | `collectionsRailCloseKeepsFocusOnTheLowerRowCard` |
| AC-7 | Task 1 | `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid` (+ content-inactive case) |
| AC-8 | P1, After Task 3 | existing Show all / History classes, Manual 1 and 5 |
| AC-9 | Tasks 2, 3, red checkpoint | the three new androidTest classes |

## Citations sampled (base e9184b79)

| Citation | Resolves |
|---|---|
| `ShowAllScreenContent.kt:221,226` | `focusedCardLeft` local; `if (pendingEntry && contentFocusActive()) requestGrid()` |
| `ShowAllScreenContent.kt:189-193` | unobserved `restoreId` read via `Snapshot.withoutReadObservation` |
| `GridFocusMemoryTest.kt:241-249`, `:359-415` | the case AC-7 replaces; `FakeFrameClock`, `Harness` |
| `CollectionsScreenContent.kt:58,59,69,72-75,83,88` | index memory, requester, `focusRestorer`, `onLoadMore`, target, wiring as the plan's table |
| `CollectionDetailScreenContent.kt:46-47,68,72-76` | `Content` branch, `contextMenuItem`, grid modifier, `VideoItemHorizontal` |
| `FocusTargetState.kt:40-48,49,142-170,234-239` | helper signature, first-card default, first-focus redirect, entry request |
| `BookmarksScreenContent.kt:73-79,118-124,135-147` | helper call, grid modifier, card modifier |
| `MainScreenComponent.kt:137-139`, `FocusOnLaunchRequester.kt:30` | rail sets `LocalContentFocusActive`; default `true` |
| `CollectionsViewState.kt:12-14,20-27`, `CollectionDetailViewState.kt:11-14` | state shapes used by the fixtures |
| `detekt.yml:81-83,337-341,680-682,756-759,829-832` | `ComplexCondition` 3, `FunctionNaming`/`MagicNumber` test excludes, `ReturnCount` 3, `UnusedPrivateProperty` |
| `ShowAllRailCloseFocusTest.kt:147-154,172-175,194-201,207-211`, `TvContextMenuLongPressTest.kt:48-57,159-169` | helpers the new tests copy |
| `CollectionDetailVM.kt:69-70,98-102` | R2 reload through `Loading` |

All resolve. `Makefile` takes `GRADLE ?=`, so `make GRADLE=./tools/agentw ... itest` is valid.

## Order and build

Task 1 is independent; the red checkpoint writes androidTest files against existing `internal` composables only; Tasks 2 and 3 touch separate files. Each task ends on fast gate and its tests. Scope list matches spec Out of scope (no `FocusTargetState`, Bookmarks, History, Search, `FlowComponent`, shared helper). Blocking questions Q1 and D-Q1 are answered; P1 baseline is a precondition with a stop rule.

## Notes (non-blocking)

1. Test design AC-8 `device | P0` row has no `red`/`guard` mark and no automated test; it is manual by nature. Relabel it `manual` when the spec is next touched.
2. `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` may turn red after Task 3: `ReconcilePublishedItemsEffect` can request the neighbour while the dialog window holds focus, and D2 omits `TvDialogFocusRestorer`. The plan's stop-to-Blocked rule covers it; expect a design addendum if it fires.
3. Task 2 commits the two Collections androidTest classes before qa-device runs them (after Task 3). If any is red there, the fix lands as a follow-up commit, not a test edit.
