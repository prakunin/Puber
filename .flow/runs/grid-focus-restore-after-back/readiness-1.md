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
