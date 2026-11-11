# Design (architect): collection-detail-inplace-refresh

Base 0e4449e2. ACs: `brief.md#acceptance-criteria`. Citations verified in the worktree.

## Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Where does the refresh become silent? | `loadItems()` publishes `Loading` only when `stateValue !is CollectionDetailViewState.Content` | One function keeps first load, Retry (AC-3) and return (AC-1) on one path; the `when` in `CollectionDetailScreenContent.kt:46-52` then never leaves the `Content` branch on a return, so focus and grid state survive with no Compose change |
| How are loads sequenced? | One `loadJob: Job?` field; `loadItems()` cancels it before launching the next | Same pattern as `FavoriteVM.kt:28,42-43`; a cancelled fetch never publishes (AC-5). Cancellation does not reach `dispatchError` |
| Heart-revert guard (Q2) | `savedSinceLastPublish: MutableSet<Int>`; `setItemSaved` adds `item.id`. The load job, after the fetch, calls `awaitPendingMutations()`, maps the items, and for ids in the set copies `isSaved` from the `Content` on screen; then publishes and clears the set | After the await, the on-screen `isSaved` is the settled value (`actualSaved` or the reverted one, `CollectionDetailVM.kt:111-124`). Map, patch and publish run with no suspension point, so no toggle can interleave. Narrow form of `HomeVM.kt:639-660` |
| Why the set lives from publish to publish, not from fetch start (amended by A1: also cleared when a load starts with no save running) | Every toggle before a publish was awaited by that publish, and the next fetch starts after it, so the server already holds it; anything later is in the set | Covers a toggle whose request was in flight when the fetch was sent |
| Failure of a silent refresh (Q3) | No change: the launch's handler calls `dispatchError` (`CollectionDetailVM.kt:37-43`), which shows the message while `Content` is on screen; the set is kept for the next publish | AC-4 holds on existing code |
| Empty change set | `onReturnedContentChanges` guard at `CollectionDetailVM.kt:99` unchanged | AC-8 |

## Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: `CollectionInteractor.getCollectionItems` (`CollectionInteractor.kt:25-29`) used as is |
| ViewModel state and actions | `CollectionDetailVM.kt`: add fields `loadJob: Job?` and `savedSinceLastPublish` next to `pendingMutations` (`:34`). Rewrite `loadItems()` (`:69-80`) per Decisions rows 1-3. `setItemSaved` (`:104-105`) adds `item.id` to the set before `updateSavedItem`. `launchMutation` (`:154-166`) and `awaitPendingMutations` (`:168-174`) are called, not edited (overlap with `vm-mutation-queue`). `CollectionDetailViewState.kt` unchanged |
| Screen and DI module | N/A: `CollectionDetailScreen.kt` and its module unchanged |
| Compose content | N/A: `CollectionDetailScreenContent.kt` unchanged (brief Out). AC-7 adds an instrumented test to `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`, reusing `Detail(shown)` (`:97-107`) and `focusAwayFromTheFirstCard()` (`:109-118`) with a `mutableStateOf` list swap as at `:59-61` |
| Resources | N/A |
| Tests (unit) | `app/src/test/kotlin/com/kino/puber/ui/feature/collections/detail/vm/CollectionDetailVMTest.kt`: replace `returnedChanges_reloadItems` (`:70-82`) with a state-sequence test over `testStateFlow` (`PuberVM.kt:71-72`); add tests for AC-3, AC-4, AC-5, AC-6 per test-design |

## Alternatives rejected

| Alternative | Reason |
|-------------|--------|
| B. Typed remap without a request | Owner chose A (state.md Q1); needs raw items kept in the VM |
| C. Hoist focus and grid state above `when (state)` | Spinner stays; brief Out |
| Only await pending mutations | A toggle sent after the fetch request still loses to the older payload (brief Risks) |
| Per-id override map with values (`HomeVM.savedOverrides`) | The on-screen state already holds the value after the await; a set of ids is enough |

## Risks

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 1 | Focus or scroll still lost on device if the return arrives while the screen is re-entering and the grid restores from saved state; unit tests cannot show it | Focus and D-pad | `FocusTargetState.kt:50-55` (`rememberSaveable(rowKey)`), `CollectionDetailScreenContent.kt:59-65,81`; AC-2 on device, AC-7 instrumented |
| 2 | Heart reverts if a code path publishes `Content` without the set patch or adds a suspension point between `awaitPendingMutations()` and `updateViewState` | Concurrency | `loadItems()` in `CollectionDetailVM.kt:69-80` |
| 3 | A slow save delays the refreshed grid by its duration (the load waits on `awaitPendingMutations`) | Lifecycle / latency | `CollectionDetailVM.kt:168-174` |
| 4 | Merge conflict with `vm-mutation-queue`, which replaces `launchMutation`/`awaitPendingMutations`; the load job's call to `awaitPendingMutations()` must move with it | Rollout | `CollectionDetailVM.kt:154-174` |
| 5 | A refetched list missing the focused card moves focus to a neighbour, not the top | Data shape | `resolveFocusedItemId` (`FocusTargetResolver.kt:10`); covered by AC-7 |

## Open questions

None.

## Amendment A1

Adopted by the owner (state.md Decisions, 2026-11-11). Plan: `docs/superpowers/plans/2026-11-11-collection-detail-inplace-refresh.md`.

- Defect: heart card 42 on the grid (save settles), open 42 in Details, remove the bookmark, Back. The refetch says `isSaved=false`, but 42 is still in `savedSinceLastPublish`, so the grid shows the stale `true`.
- Rule: `loadItems()` also clears `savedSinceLastPublish` when a load starts while no save is running. A set entry then survives only while its save is pending, so a later server value wins.
- Constraint: the "no save running" check reads only `pendingMutations` (`CollectionDetailVM.kt:34`); it does not edit `launchMutation` or `awaitPendingMutations`, which stay with `vm-mutation-queue`.
- Tests: P2 `toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh` keeps its name and AC but its save is still pending at the second return; new guard `gridToggleBeforeReturn_showsServerSavedValue` (AC-6).

### Risk added

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 6 | The check misreads "no save running" (a finished job left in `pendingMutations`, or a save launched between the check and the fetch), so the set is cleared too early and the heart reverts, or kept too long and shows a stale value | Concurrency | `loadItems()` and `pendingMutations` in `CollectionDetailVM.kt:34,69-80` |

## Addendum after verify round 1

Input: `qa-report.md#round-1`, defect 1 and observation 2. Worktree HEAD cad9f616. `FocusTargetState.kt` and `CollectionDetailScreenContent.kt` are unchanged since base, so their lines hold at both. Compose UI 1.12.0 (BOM 2026.08.00).

### Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Defect 1: test or production? | Production. The test stays as written, with a better failure message | Its expectation is AC-7 and the resolver contract (`FocusTargetResolver.kt:4-8,20-23`: Card 10 at index 9 leaves, Card 11 takes index 9). Its seam (`mutableStateOf` swap, D-pad focus) is the passing `...StaysOnTheFocusedCard...` (`CollectionDetailScreenContentFocusTest.kt:98-111`), and ShowAll passes the same event 5 of 5 on the same emulator (`ShowAllScreenContentFocusTest.kt:125-147`, evidence `2026-11-11-grid-focus-restore-after-back-device.md:67`) |
| Why the context-menu test passes and this one fails | They take different paths. In the menu test the removed card does not hold focus: the menu does, and content focus is parked (`CollectionDetailScreenContent.kt:56-58`). The neighbour is requested only after BACK flips `contentFocusActive` (`FocusTargetState.kt:240-247`), once the grid has settled. Here the removed card holds focus | Removing a focused lazy item deactivates its slot for reuse. `FocusTargetNode.onReset` then calls `clearFocus(force, refresh, clearOwnerFocus = true, Exit)` and clears the view's focus too; `onDetach` passes `clearOwnerFocus = false` (verified in the `ui-android` 1.12.0 bytecode) |
| Where production loses the neighbour | The pending neighbour request in the shared helper is fragile in three ways, and the code allows each: (1) it makes one `requestFocus()` after one frame and drops the result (`FocusTargetState.kt:240-246`, `:302-307`); (2) the requester follows `focusedItemId` (`:76-80`, `CollectionDetailScreenContent.kt:98-99`), so a card focused while the request waits for its frame rewrites the id (`:161`) and drags the requester off the neighbour; (3) `ReconcilePublishedItemsEffect` re-resolves from the live `focusedItemId` (`:203-207`), so a card focused between composition and the effect cancels the neighbour. Home, which makes the request without the frame wait (`HomeScreenContent.kt:424-430`), passes its removal tests | Which of the three fires in this test is not settled by the code. The fix closes all three |
| Fix | In `FocusTargetState.kt`: (a) the effect resolves to the `targetItemId` computed in composition, not to a fresh resolve; (b) while a pending request exists and its card is in `items`, the returned `targetItemId` (where the requester sits) is the pending id; (c) the pending request retries over up to `ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS` frames until `requestFocus()` returns true. The first-load (`:234-239`), anchor (`:248-256`) and tab re-entry (`:273-293`) effects are unchanged | Smallest change that leaves `CollectionDetailScreenContent.kt` alone (brief Out). ShowAll's recipe that passes is the same retry (`ShowAllScreenContent.kt:243-251`) |
| AC impact | None. AC-7 holds as written. The plan's scope constraint widens from `CollectionDetailVM.kt` only to include `FocusTargetState.kt` | The test design row's mark for `...MovesToTheNeighbour...` reads `guard`; the test fails on base and HEAD before the fix, so `red` describes it (test-designer's file) |
| Observation 2: stale watched mark | Not a defect of this change, and not this run. Recorded as a follow-up idea | Details does not return before its write has been acknowledged: `closeDetails` awaits its mutations (`DetailsVM.kt:601-611`) and `setMovieWatched` returns after the server response (`DetailsInteractor.kt:109-133`). So the refetch starts after the ack, and no client race exists. `CollectionDetailVM` never writes watched, so its heart-only await does not matter. A stale mark after the ack comes from the refetch reading watch fields that lag the write: `resolve` prefers the item's own fields over the local index (`WatchStateRepository.kt:218-223`). That is server read-after-write latency, and base refetches the same way after its `Loading` |

### Changes by layer (addendum)

| Layer | Change |
|-------|--------|
| Compose shared focus helper | `main/core/ui/uikit/component/moviesList/FocusTargetState.kt`: `rememberReconciledItemFocus` (`:39-126`) passes `targetItemId` into `ReconcilePublishedItemsEffect` (`:192-220`), which uses it in place of the resolve at `:203-207`. It returns the pending id as `targetItemId` (`:122-125`) while that card is in `items`. `RequestReconciledItemFocusEffects` pending effect (`:240-247`) calls a new retrying request in place of `requestAfterComposition` (`:302-307`) |
| Instrumented test | `CollectionDetailScreenContentFocusTest.kt:124`: on timeout, fail with the titles that hold focus (or `none`), so a further failure names the mechanism |
| VM, screen, content, resources | N/A: unchanged |

### Alternatives rejected

| Alternative | Reason |
|-------------|--------|
| Fix in `CollectionDetailScreenContent.kt` (ShowAll-style own memory) | Brief Out |
| Change the test to park focus or send BACK, as the menu test does | It would test a different path than AC-7 names |
| Only the retry (c) | It leaves mechanisms (2) and (3) open, and a third verify round would be spent learning which one fired |
| Patch watched like `isSaved` | `CollectionDetailVM` has no watched write to patch from. The value would come from `contentChanges`, which is Details' data, and that lies outside the run |

### Risks (addendum)

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 7 | The shared helper now behaves differently for Home rows, SectionRow, VideoGrid and Bookmarks when a focused card leaves: up to 3 extra frames of requests, and the requester pinned to the pending card | Focus, rollout | `FocusTargetState.kt:192-247`. Instrumented regression list in the plan, Task 3 |
| 8 | The fix does not close the actual path, and the guard stays red | Focus | Task 3 stop rule. The new failure message names the focused card |
| 9 | A user D-pad press inside the up-to-3-frame retry window is overridden by the neighbour request | Focus, D-pad | `FocusTargetState.kt:240-247`. The retry stops at the first successful request |

### Open questions (addendum)

1. Follow-up idea, not this run: stale watched marks after a return. After a Watched change in `contentChanges`, prefer the local index over a refetched item's own watch fields for those ids (`WatchStateRepository.kt:223`). Recommended: capture it with `/task:new`. Blocks: no.

## Addendum after review round 1

Input: `triage-1.md` rows 1-5. Worktree HEAD a021e92c; `FocusTargetState.kt` lines below are at a021e92c, `CollectionDetailScreenContent.kt` is unchanged since base.

### Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Rows 1-3: how is Task 3 kept off the other callers? | Opt-in parameter `holdPendingFocusTarget: Boolean = false` on `rememberReconciledItemFocus` (`FocusTargetState.kt:40-48`). It gates all three Task 3 changes: (a) the effect's composition-time resolve (`:208`), (b) the pending id as returned target (`:81-83`, `:127`), (c) the retrying request (`:245`, `:303-311`). Off, each line is the base code. `CollectionDetailScreenContent.kt:59-65` passes `true`; no other caller changes | Home, SectionRow, VideoGrid, Bookmarks, EpisodesPanel and Details get base behaviour back, so rows 1 and 2 cannot occur there. CollectionDetail runs exactly the d1dea378 code that turned AC-7 green 3 of 3 |
| Why rows 1 and 2 cannot occur on CollectionDetail | It passes `isTargetRow = true` and no `initialFocusedItemId` (`CollectionDetailScreenContent.kt:59-65`) | Row 1 needs a non-target row (the pending effect at `:241-248` clears the entry once content focus is active). Row 2 needs `ReconcileInitialItemFocusEffect` (`:177-194`), which does nothing when `initialFocusedItemId` is null. The KDoc on the parameter states both preconditions |
| Which of (a), (b), (c) does AC-7 need? | Not settled by the code. The opt-in keeps all three, on CollectionDetail only | Which path fires depends on whether the removed slot's `onReset` focus clear and any re-entry focus land before or after the published effect runs. The code does not fix that order, and no device run has isolated it (review-tests-1 Unproven 1) |
| Scope | Production scope adds one named argument in `CollectionDetailScreenContent.kt`, the file brief Out names. No state is hoisted (option C stays out) | Without a call-site argument, the helper can tell CollectionDetail apart only by a rowKey string compare |
| Row 2 guard | Instrumented `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` in `VideoGridInitialFocusTest`, built on `changedInitialFocusedItemIdRefocusesTheRetainedRow` (`VideoGridInitialFocusTest.kt:65-90`). One `runOnIdle` changes the row's items and `initialFocusedItemId` together | VideoGrid passes `initialFocusedItemId` through to the helper (`VideoGridUIState.kt:417-424`). Traced on a021e92c, focus ends on Item 1 because `:208` writes the stale id back. On base it ends on Item 3 |
| Rows 1 and 3 guard | Instrumented `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` in `VideoGridInitialFocusTest`: rows `top` (1, 2, 3) and `bottom` (11, 12, 13); Item 11 is removed while focus is on Item 1. Down to Item 12, Right to Item 13, then `LocalContentFocusActive` false and back to true: Item 13 keeps focus | VideoGrid sets `isTargetRow` per row (`VideoGridUIState.kt:228`). Traced on a021e92c, the pending effect re-runs on the `contentFocusActive` key (`:241`) and requests the requester, which is still on the stale neighbour (`:81-83`). On base the requester follows `focusedItemId`. `HomeSectionRemovalFocusTest` is red before this change, so it cannot serve as the seam |
| Row 4 | `CollectionDetailVM` keeps `saveJobItemIds: MutableMap<Job, Int>`. `setItemSaved` (`CollectionDetailVM.kt:121-146`) registers the `Job` that `launchMutation` returns (`:172-184`). At load start, `loadItems()` drops jobs that are no longer active and runs `savedSinceLastPublish.retainAll(<ids of the remaining jobs>)`. This replaces the A1 line `:75` | A settled save no longer outlives its own save because another save is pending. When no save is active the set empties, the same as A1. `launchMutation` and `awaitPendingMutations` stay unedited |
| Row 5 | The fetch in `toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch` (`CollectionDetailVMTest.kt:199-218`) returns 42 and 43. While the save is pending, the state still lists only 42. After the save it lists 42 and 43 | Removing `awaitPendingMutations()` (`CollectionDetailVM.kt:79`) then publishes 43 early, so the test goes red |

### Changes by layer (addendum 2)

| Layer | Change |
|-------|--------|
| ViewModel state and actions | `CollectionDetailVM.kt`: field `saveJobItemIds` next to `savedSinceLastPublish` (`:36`); line `:75` replaced as in the Row 4 decision; in `setItemSaved` the `launchMutation` result (`:124`) is registered with `item.id` |
| Compose shared focus helper | `FocusTargetState.kt`: add the parameter, and add the gates at `:81-83`, `:208` and `:245` |
| Compose content | `CollectionDetailScreenContent.kt:59-65`: `holdPendingFocusTarget = true` |
| Tests | `CollectionDetailVMTest.kt`: new `settledToggleWhileAnotherSaveIsPending_showsServerSavedValue`; row 5 strengthening. `VideoGridInitialFocusTest.kt`: the two guards |
| API, screen and DI, resources | N/A: unchanged |

### Alternatives rejected

| Alternative | Reason |
|-------------|--------|
| Pending id wins only while it equals `focusedItemId` (edge F1 fix) | It undoes (b) in the very case (b) exists for: a card focused while the request waits rewrites `focusedItemId` |
| Gate on `isTargetRow` alone | Home's target row, SectionRow and EpisodesPanel still change, and row 2 stays open |
| Revert (a), keep (b) and (c) shared | Row 1 stays open, and AC-7 is not proven without (a) |
| Narrow to the needed subset by device bisection | It costs device rounds, and other callers would gain nothing once the change is opt-in |

### Risks (addendum 2)

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 10 | Either new guard passes on a021e92c because the seam does not reach the defect (focus enters the row elsewhere, or effect order differs on the device), so it guards nothing | Focus, tests | `VideoGridInitialFocusTest.kt`. Each guard asserts its preconditions, and qa-device runs it red against the a021e92c helper |
| 11 | A future caller with several rows or an `initialFocusedItemId` sets `holdPendingFocusTarget = true` and reintroduces rows 1-2 | Focus, rollout | KDoc of the parameter, `FocusTargetState.kt:40-48` |
| 12 | `saveJobItemIds` keeps finished jobs between loads | Memory | `CollectionDetailVM.kt`. Pruned at every load start, and bounded by the toggles made between two loads |

Risks 7 and 9 now apply to CollectionDetail only.
