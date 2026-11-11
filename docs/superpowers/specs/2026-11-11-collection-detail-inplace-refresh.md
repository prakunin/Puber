# CollectionDetail refreshes in place after a returned change

Status: approved
Date: 2026-11-11
Slug: collection-detail-inplace-refresh
Kind: bug
Size: M
Tracks: none

## Problem / Why

CollectionDetail: after Details or the player returns any content change, the screen flashes a full-screen spinner and rebuilds the grid; focus lands on the first card at the top instead of the card the user opened.

### Root cause (confirmed by code trace)

1. `CollectionDetailVM.kt:98-102`: `onReturnedContentChanges` calls `loadItems()` for any non-empty change set.
2. `CollectionDetailVM.kt:69-70`: `loadItems()` always publishes `CollectionDetailViewState.Loading` before fetching.
3. `CollectionDetailScreenContent.kt:46-52`: `Loading` is a separate `when` branch, so the `is Content` branch is disposed. With it go `rememberReconciledItemFocus` (`:59-65`, focused id in `rememberSaveable(rowKey)` scoped to that branch, `FocusTargetState.kt:40-101`) and the `LazyVerticalGrid`'s default grid state (`:81`). The new `Content` composes fresh: first card, scroll 0.
4. Secondary: `CollectionDetailVM.kt:71` launches unsequenced, so two quick returns can publish out of order (research.md: Risks).

### Risks (analyst)

- Heart revert (AC-6): the fetched payload can predate a toggle made during the fetch. Awaiting `pendingMutations` (`CollectionDetailVM.kt:168-174`) before publishing is not enough on its own: a toggle that starts after the request was sent still gets the old server value. The ids toggled since the load began need their current `isSaved` carried into the new list (the narrow form of `HomeVM.kt:639-655`).
- Overlap with idea run `vm-mutation-queue` on `launchMutation`/`awaitPendingMutations` (`CollectionDetailVM.kt:154-174`): keep this change outside those two functions.
- Device-only: focus and scroll survival show only at runtime (AC-2); needs an emulator lease and install-preserve.

## Acceptance criteria

- AC-1. Given `Content` is shown, When Details returns a non-empty change set, Then the VM publishes no `Loading` and the next state is a `Content` with the refetched items (unit test on the state sequence; replaces `CollectionDetailVMTest.kt:71-82`, red on base).
- AC-2. Given the grid scrolled so a card in row 3 or lower is focused, When the user opens it, toggles watched in Details and presses Back, Then the same card is focused, the scroll position is kept, no spinner shows, and the card shows the new watched state (device, 3 repeats; same for a bookmark toggle in Details and for Back from the player after playback).
- AC-3. Given the first load or `Error` with Retry, When loading starts, Then `Loading` is published first, as on base (unit test).
- AC-4. Given `Content` is shown, When the in-place refresh fails, Then the grid stays as it was and the error message is shown; the state does not become `Error` (unit test; existing `dispatchError`, `CollectionDetailVM.kt:37-43`).
- AC-5. Given a refresh is in flight, When a second return starts another refresh, Then only the second fetch's result is published (unit test with two `CompletableDeferred` fetches completing in reverse order).
- AC-6. Given a refresh is in flight, When the user toggles a card's heart and the fetch lands afterwards with the old saved value, Then the card keeps the toggled value (unit test; see Q2).
- AC-7. Given the composable shows `Content` with a non-first card focused, When a new `Content` with changed items replaces it (no `Loading` between), Then focus stays on that card, or on its neighbour if the card left the list (instrumented test in `CollectionDetailScreenContentFocusTest.kt`, the review-blind-1 F1 gap).
- AC-8. A null or empty change set triggers no fetch, and Back still forwards the merged `contentChanges` (`CollectionDetailVM.kt:150`); existing VM tests stay green.

## Out of scope

In: a return with `Content` on screen refreshes in place (research.md: Options, option A): fetch, publish a new `Content`, no `Loading`; a single load job; a just-toggled heart is not reverted by the refresh.
Out: `Loading` on first load and Retry (unchanged); typed remap without a request (option B); hoisting UI state in the composable (option C); `CollectionDetailScreenContent.kt` changes.

## Design

Base 0e4449e2. ACs: `brief.md#acceptance-criteria`. Citations verified in the worktree.

### Decisions

| Question | Decision | Why |
|----------|----------|-----|
| Where does the refresh become silent? | `loadItems()` publishes `Loading` only when `stateValue !is CollectionDetailViewState.Content` | One function keeps first load, Retry (AC-3) and return (AC-1) on one path; the `when` in `CollectionDetailScreenContent.kt:46-52` then never leaves the `Content` branch on a return, so focus and grid state survive with no Compose change |
| How are loads sequenced? | One `loadJob: Job?` field; `loadItems()` cancels it before launching the next | Same pattern as `FavoriteVM.kt:28,42-43`; a cancelled fetch never publishes (AC-5). Cancellation does not reach `dispatchError` |
| Heart-revert guard (Q2) | `savedSinceLastPublish: MutableSet<Int>`; `setItemSaved` adds `item.id`. The load job, after the fetch, calls `awaitPendingMutations()`, maps the items, and for ids in the set copies `isSaved` from the `Content` on screen; then publishes and clears the set | After the await, the on-screen `isSaved` is the settled value (`actualSaved` or the reverted one, `CollectionDetailVM.kt:111-124`). Map, patch and publish run with no suspension point, so no toggle can interleave. Narrow form of `HomeVM.kt:639-660` |
| Why the set lives from publish to publish, not from fetch start (amended by A1: also cleared when a load starts with no save running) | Every toggle before a publish was awaited by that publish, and the next fetch starts after it, so the server already holds it; anything later is in the set | Covers a toggle whose request was in flight when the fetch was sent |
| Failure of a silent refresh (Q3) | No change: the launch's handler calls `dispatchError` (`CollectionDetailVM.kt:37-43`), which shows the message while `Content` is on screen; the set is kept for the next publish | AC-4 holds on existing code |
| Empty change set | `onReturnedContentChanges` guard at `CollectionDetailVM.kt:99` unchanged | AC-8 |

### Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: `CollectionInteractor.getCollectionItems` (`CollectionInteractor.kt:25-29`) used as is |
| ViewModel state and actions | `CollectionDetailVM.kt`: add fields `loadJob: Job?` and `savedSinceLastPublish` next to `pendingMutations` (`:34`). Rewrite `loadItems()` (`:69-80`) per Decisions rows 1-3. `setItemSaved` (`:104-105`) adds `item.id` to the set before `updateSavedItem`. `launchMutation` (`:154-166`) and `awaitPendingMutations` (`:168-174`) are called, not edited (overlap with `vm-mutation-queue`). `CollectionDetailViewState.kt` unchanged |
| Screen and DI module | N/A: `CollectionDetailScreen.kt` and its module unchanged |
| Compose content | N/A: `CollectionDetailScreenContent.kt` unchanged (brief Out). AC-7 adds an instrumented test to `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`, reusing `Detail(shown)` (`:97-107`) and `focusAwayFromTheFirstCard()` (`:109-118`) with a `mutableStateOf` list swap as at `:59-61` |
| Resources | N/A |
| Tests (unit) | `app/src/test/kotlin/com/kino/puber/ui/feature/collections/detail/vm/CollectionDetailVMTest.kt`: replace `returnedChanges_reloadItems` (`:70-82`) with a state-sequence test over `testStateFlow` (`PuberVM.kt:71-72`); add tests for AC-3, AC-4, AC-5, AC-6 per test-design |

### Alternatives rejected

| Alternative | Reason |
|-------------|--------|
| B. Typed remap without a request | Owner chose A (state.md Q1); needs raw items kept in the VM |
| C. Hoist focus and grid state above `when (state)` | Spinner stays; brief Out |
| Only await pending mutations | A toggle sent after the fetch request still loses to the older payload (brief Risks) |
| Per-id override map with values (`HomeVM.savedOverrides`) | The on-screen state already holds the value after the await; a set of ids is enough |

### Risks

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 1 | Focus or scroll still lost on device if the return arrives while the screen is re-entering and the grid restores from saved state; unit tests cannot show it | Focus and D-pad | `FocusTargetState.kt:50-55` (`rememberSaveable(rowKey)`), `CollectionDetailScreenContent.kt:59-65,81`; AC-2 on device, AC-7 instrumented |
| 2 | Heart reverts if a code path publishes `Content` without the set patch or adds a suspension point between `awaitPendingMutations()` and `updateViewState` | Concurrency | `loadItems()` in `CollectionDetailVM.kt:69-80` |
| 3 | A slow save delays the refreshed grid by its duration (the load waits on `awaitPendingMutations`) | Lifecycle / latency | `CollectionDetailVM.kt:168-174` |
| 4 | Merge conflict with `vm-mutation-queue`, which replaces `launchMutation`/`awaitPendingMutations`; the load job's call to `awaitPendingMutations()` must move with it | Rollout | `CollectionDetailVM.kt:154-174` |
| 5 | A refetched list missing the focused card moves focus to a neighbour, not the top | Data shape | `resolveFocusedItemId` (`FocusTargetResolver.kt:10`); covered by AC-7 |

### Open questions

None.

### Amendment A1

Adopted by the owner (state.md Decisions, 2026-11-11). Plan: `docs/superpowers/plans/2026-11-11-collection-detail-inplace-refresh.md`.

- Defect: heart card 42 on the grid (save settles), open 42 in Details, remove the bookmark, Back. The refetch says `isSaved=false`, but 42 is still in `savedSinceLastPublish`, so the grid shows the stale `true`.
- Rule: `loadItems()` also clears `savedSinceLastPublish` when a load starts while no save is running. A set entry then survives only while its save is pending, so a later server value wins.
- Constraint: the "no save running" check reads only `pendingMutations` (`CollectionDetailVM.kt:34`); it does not edit `launchMutation` or `awaitPendingMutations`, which stay with `vm-mutation-queue`.
- Tests: P2 `toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh` keeps its name and AC but its save is still pending at the second return; new guard `gridToggleBeforeReturn_showsServerSavedValue` (AC-6).

#### Risk added

| # | Risk | Area | Lives in |
|---|------|------|----------|
| 6 | The check misreads "no save running" (a finished job left in `pendingMutations`, or a save launched between the check and the fetch), so the set is cleared too early and the heart reverts, or kept too long and shows a stale value | Concurrency | `loadItems()` and `pendingMutations` in `CollectionDetailVM.kt:34,69-80` |

## Test design

Base 0e4449e2. ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Levels: `.flow/roles/test-designer.md`.

### Risk profile

| # | Risk | Source | P | I | Score | Covered by |
|---|------|--------|---|---|-------|------------|
| R1 | Focus or scroll still lost on device after a return | design risk 1 | 2 | 3 | 6 | Manual 1-3 (only level where re-entry timing shows), AC-7 instrumented guards |
| R2 | Heart reverts: refreshed payload predates a toggle, or a publish path skips the patch | brief risk 1, design risk 2 | 2 | 3 | 6 | AC-6 unit tests |
| R3 | Failed silent refresh replaces the grid with `Error` | brief AC-4 | 2 | 3 | 6 | `failedRefresh_keepsContentAndShowsMessage` |
| R4 | Two quick returns publish out of order | brief root cause 4 | 2 | 2 | 4 | `overlappingRefreshes_publishOnlyTheLatestFetch` |
| R5 | The `!is Content` guard drops `Loading` on first load or Retry | design decision 1 | 2 | 2 | 4 | AC-3 unit guards |
| R6 | `savedSinceLastPublish` outlives its save, so a Details bookmark change after a settled grid toggle is masked by the stale on-screen value (amendment A1: cleared when a load starts with no save active) | design decision 4, A1 | 2 | 3 | 6 | `gridToggleBeforeReturn_showsServerSavedValue`, `refreshAfterPublishedToggle_showsServerSavedValue` |
| R7 | A slow save delays the refreshed grid | design risk 3 | 2 | 1 | 2 | Accepted: the stale grid stays usable meanwhile |
| R8 | Conflict with `vm-mutation-queue` on `awaitPendingMutations` | design risk 4 | 2 | 1 | 2 | Accepted: AC-6 tests fail if the call is lost in that merge |

### Traceability

Unit tests: `app/src/test/kotlin/com/kino/puber/ui/feature/collections/detail/vm/CollectionDetailVMTest.kt`. Instrumented: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`.

| AC | Level | Priority | Mark | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | unit | P0 | red | `returnedChanges_publishRefetchedContentWithoutLoading` | Replaces `returnedChanges_reloadItems`. States recorded after the Details return contain no `Loading`, and the last is `Content` with the second fetch's ids; fails if `loadItems()` publishes `Loading` while `Content` is shown |
| AC-1 | unit | P2 | red | `playerReturn_publishRefetchedContentWithoutLoading` | Same through `ItemPlayed`; fails if the player listener takes another path |
| AC-3 | unit | P1 | guard | `firstLoad_publishesLoadingBeforeContent` | Recorded states start `Loading` then `Content`; fails if the guard suppresses the first spinner |
| AC-3 | unit | P1 | guard | `retry_publishesLoadingAfterError` | First fetch throws (state `Error`), `RetryClicked` with a pending second fetch leaves `Loading`; fails if the guard skips `Loading` when leaving `Error` |
| AC-4 | unit | P0 | red | `failedRefresh_keepsContentAndShowsMessage` | Second fetch throws: state stays the first `Content`, `testMessageValue` carries the error message; fails if `Loading` precedes the fetch (base: `Error`) |
| AC-5 | unit | P0 | red | `overlappingRefreshes_publishOnlyTheLatestFetch` | Two returns, fetches as `CompletableDeferred`; second completes first, then the first: final `Content` holds the second's ids, no state after it holds the first's, `testMessageValue` null; fails if `loadJob` is not cancelled or a cancellation reaches `dispatchError` |
| AC-6 | unit | P0 | red | `toggleDuringRefresh_keepsSavedValueWhenSaveSettledFirst` | Refresh pending, heart on card 42 to true, save succeeds, fetch lands with `isSaved=false`: card 42 stays true; fails if the set patch is removed |
| AC-6 | unit | P0 | red | `toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch` | Fetch lands while the save is pending, then the save succeeds: no `Content` published after the toggle shows card 42 false; fails if the load does not await `pendingMutations` before publishing |
| AC-6 | unit | P1 | guard | `failedToggleDuringRefresh_showsRevertedValue` | Save fails during the refresh: published card 42 shows the original value; fails if the patch copies the requested value instead of the settled on-screen one |
| AC-6 | unit | P1 | guard | `refreshAfterPublishedToggle_showsServerSavedValue` | Return 1: heart on card 42 to true, save settles, fetch 1 lands (publish; the "42 true" setup is not asserted, base shows false). Return 2: heart on card 43 with its save still pending when the return starts, so the load does not clear the set; the save then settles and fetch 2 lands with 42 `isSaved=false`: card 42 shows false. Passes on base; fails if the set is not cleared on publish (R6), since 42 stays masked as true |
| AC-6 | unit | P1 | guard | `gridToggleBeforeReturn_showsServerSavedValue` | A1. Content shown, heart on card 42 to true, save succeeds; Details return, fetch reports `isSaved=false`: card 42 shows false (base reloads from the server, so it passes there); fails if the set is not cleared when a load starts with no save active |
| AC-6 | unit | P2 | red | `toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh` | A1 shape. Return 1, heart on 42 with the save pending, fetch 1 fails; return 2 with the save still pending; save succeeds, fetch 2 lands with `isSaved=false`: card 42 true; fails if the set is cleared on failure or on a load that starts while a save is active (base shows false) |
| AC-7 | instrumented | P1 | guard | `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced` | Focus a card in row 4 or lower by D-pad Down from the first card, swap `shown` for a list with that card's `isSaved` flipped: same card focused and displayed; fails if `Content` to `Content` recomposition resets focus or grid state |
| AC-7 | instrumented | P1 | guard | `collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` | Same, the swapped list drops the focused card: the next card is focused, not `Card 1`; fails if reconciliation falls back to the top |
| AC-8 | unit | P1 | guard | `emptyReturnedChanges_doNotRefetch` | Null and empty change sets: `getCollectionItems` called once; fails if the guard at `CollectionDetailVM.kt:99` is dropped |
| AC-8 | unit | P1 | guard | `returnedChanges_areForwardedToParentOnBack` | Existing test, unchanged |
| AC-8 | unit | P1 | guard | `back_waitsForSaveAndForwardsBookmarkChange` | Existing test, unchanged |
| AC-2 | instrumented | P0 | guard | `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced` | Automated half of AC-2: the same test as the AC-7 row, held at P0 for AC-2; focus and grid state survive a `Content` to `Content` swap, which is what the in-place refresh relies on |
| AC-2 | device | P0 | red | Manual 1-3 | Device half: spinner, top focus or reset scroll after Back on base; gone after. Re-entry timing shows only on a device |

#### Fixtures

| Need | How |
|---|---|
| State sequence | Collector on `vm.testStateFlow` in `backgroundScope` with `UnconfinedTestDispatcher(testScheduler)`, as `SectionVMTest.kt:693-695` |
| Distinct fetch results | `mapper.mapShortItemList` answers `firstArg<List<Item>>().map { videoItem(it.id, isSaved = ...) }`; each fetch returns its own ids or `isSaved` (map `Item` bookmark fields or a per-call answer list) |
| Error path | `ErrorHandler` stub where `proceedInvoke(any(), any())` invokes its second argument with `ErrorEntity(message = "refresh failed", code = "test")`; the current `createVM` stubs only `proceed` |
| Return listener | Capture `navigateForResult` listener with `slot()`, as `returnedChanges_areForwardedToParentOnBack` |
| Instrumented swap | `var shown by mutableStateOf(items)` and `Detail(shown)` (`CollectionDetailScreenContentFocusTest.kt:60-61,97-107`); reach the deep card with D-pad Down, no `requestFocus` on the target |

### Bug: failing-first test

`returnedChanges_publishRefetchedContentWithoutLoading`, written first. Red on base: `git show 0e4449e2:app/src/main/java/com/kino/puber/ui/feature/collections/detail/vm/CollectionDetailVM.kt` lines 69-70 publish `Loading` before every fetch, including the one from `onReturnedContentChanges` (`:98-102`). Every other `red` row is run on the unchanged VM first as well.

### Manual checks

Emulator lease, worktree dev APK via `tools/android-apk-install-preserve`, logged-in account, a collection with 12 or more items. Keys via `adb -s <serial> shell input keyevent`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Open the collection; `DPAD_DOWN` x3, `DPAD_RIGHT`; note the card; `DPAD_CENTER` to Details; toggle watched; `BACK`. Repeat 3 times | Same card focused, rows above it still scrolled off, card shows the new watched state | Full-screen spinner, focus on the first card, grid at the top, crash |
| 2 | As 1, toggling the bookmark in Details instead | Same as 1, card heart matches Details | Same as 1; heart showing the old value |
| 3 | As 1, start playback from Details or the card, play a few seconds, `BACK` to the grid | Same as 1 | Same as 1 |
| 4 | Open a collection from Collections (first load) | Spinner, then the grid with the first card focused | A blank screen before the grid, focus on a card other than the first |

### perf

N/A: track off.

## Open questions

1. Refresh shape: A (silent refetch), B (typed remap plus refetch) or C (hoisted UI state)? Recommended A (research.md: Options). Blocks: no.
2. Heart-revert guard: carry the current `isSaved` for ids toggled during the load (after awaiting pending mutations), or only await pending mutations? Recommended carry plus await: awaiting alone leaves the race in Risks open. Blocks: no (AC-6 holds either way; the answer picks the mechanism).
3. On a failed in-place refresh, keep the stale grid and show the message? Recommended yes (AC-4, current `dispatchError`). Blocks: no.

All three answered with the recommended option (see Decisions).

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-11 | Size M, tracks none, kind bug | owner (standing instruction: recommended) |
| 2026-11-11 | Q1 refresh shape A: silent refetch while Content is shown | owner (standing instruction: recommended) |
| 2026-11-11 | Q2 await pending mutations and keep the current isSaved for ids toggled during the load | owner (standing instruction: recommended) |
| 2026-11-11 | Q3 a failed in-place refresh keeps the stale grid and shows the message | owner (standing instruction: recommended) |
| 2026-11-11 | Gate 1: approve | owner (standing instruction: recommended) |
| 2026-11-11 | Plan Q1: amendment A1 adopted (set also cleared when a load starts with no save running) | owner (standing instruction: recommended) |
