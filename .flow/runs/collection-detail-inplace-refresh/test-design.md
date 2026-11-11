# Test design: collection-detail-inplace-refresh

Base 0e4449e2. Verify round 1 addendum: HEAD cad9f616; review round 1 addendum (Task 4): HEAD a021e92c. ACs: `brief.md#acceptance-criteria`. Design: `design-architect.md`. Levels: `.flow/roles/test-designer.md`.

## Risk profile

| # | Risk | Source | P | I | Score | Covered by |
|---|------|--------|---|---|-------|------------|
| R1 | Focus or scroll still lost on device after a return | design risk 1 | 2 | 3 | 6 | Manual 1-3 (only level where re-entry timing shows), AC-7 instrumented tests |
| R2 | Heart reverts: refreshed payload predates a toggle, or a publish path skips the patch | brief risk 1, design risk 2 | 2 | 3 | 6 | AC-6 unit tests |
| R3 | Failed silent refresh replaces the grid with `Error` | brief AC-4 | 2 | 3 | 6 | `failedRefresh_keepsContentAndShowsMessage` |
| R4 | Two quick returns publish out of order | brief root cause 4 | 2 | 2 | 4 | `overlappingRefreshes_publishOnlyTheLatestFetch` |
| R5 | The `!is Content` guard drops `Loading` on first load or Retry | design decision 1 | 2 | 2 | 4 | AC-3 unit guards |
| R6 | `savedSinceLastPublish` outlives its save, so a Details bookmark change after a settled grid toggle is masked by the stale on-screen value (amendment A1: cleared when a load starts with no save active) | design decision 4, A1 | 2 | 3 | 6 | `gridToggleBeforeReturn_showsServerSavedValue`, `refreshAfterPublishedToggle_showsServerSavedValue`, `settledToggleWhileAnotherSaveIsPending_showsServerSavedValue` (triage-1 row 4: settled save of A stays in the set while B is pending) |
| R7 | A slow save delays the refreshed grid | design risk 3 | 2 | 1 | 2 | Accepted: the stale grid stays usable meanwhile |
| R8 | Conflict with `vm-mutation-queue` on `awaitPendingMutations` | design risk 4 | 2 | 1 | 2 | Accepted: AC-6 tests fail if the call is lost in that merge |
| R9 | The shared focus helper change leaks to callers other than CollectionDetail: after Task 4 it is opt-in (`holdPendingFocusTarget`, default false, set only in `CollectionDetailScreenContent.kt`), so a caller regresses only if the default flips or a gate is missing (triage-1 rows 1-3) | design addendum 2, risk 7 | 1 | 3 | 3 | The helper-caller classes (AC-7 guard rows) plus the two VideoGrid guards, which fail when a gate is missing |
| R10 | The Task 3 fix does not close the real path and the neighbour test stays red (design addendum risk 8) | design addendum risk 8 | 2 | 2 | 4 | `collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent`, whose failure message now names the focused cards |
| R11 | A D-pad press inside the retry window is overridden by the neighbour request (design addendum risk 9) | design addendum risk 9 | 1 | 2 | 2 | Accepted: the retry stops at the first successful request, at most 3 frames |
| R12 | A new VideoGrid guard passes on a021e92c because the seam does not reach the defect, so it guards nothing (design addendum 2, risk 10) | design addendum 2, risk 10 | 2 | 2 | 4 | qa-device runs `VideoGridInitialFocusTest` against the a021e92c helper: both guards must fail there (qa handoff "Task 4, guards red on a021e92c") |

## Traceability

Unit tests: `app/src/test/kotlin/com/kino/puber/ui/feature/collections/detail/vm/CollectionDetailVMTest.kt`. Instrumented: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`; the helper-caller classes are named fully qualified.

| AC | Level | Priority | Mark | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | unit | P0 | red | `returnedChanges_publishRefetchedContentWithoutLoading` | Replaces `returnedChanges_reloadItems`. States recorded after the Details return contain no `Loading`, and the last is `Content` with the second fetch's ids; fails if `loadItems()` publishes `Loading` while `Content` is shown |
| AC-1 | unit | P2 | red | `playerReturn_publishRefetchedContentWithoutLoading` | Same through `ItemPlayed`; fails if the player listener takes another path |
| AC-3 | unit | P1 | guard | `firstLoad_publishesLoadingBeforeContent` | The `StateFlow` dedups the initial `Loading` (`initialViewState`), so the recorder sees `Loading` once; proves the first `Content` follows `Loading` with nothing in between. It cannot see the `!is Content` guard; that guard is covered by `retry_publishesLoadingAfterError`. Fails if an extra state (for example `Error`) lands between them |
| AC-3 | unit | P1 | guard | `retry_publishesLoadingAfterError` | First fetch throws (state `Error`), `RetryClicked` with a pending second fetch leaves `Loading`; fails if the guard skips `Loading` when leaving `Error` |
| AC-4 | unit | P0 | red | `failedRefresh_keepsContentAndShowsMessage` | Second fetch throws: state stays the first `Content`, `testMessageValue` carries the error message; fails if `Loading` precedes the fetch (base: `Error`) |
| AC-5 | unit | P0 | red | `overlappingRefreshes_publishOnlyTheLatestFetch` | Two returns, fetches as `CompletableDeferred`; second completes first, then the first: final `Content` holds the second's ids, no state after it holds the first's, `testMessageValue` null; fails if `loadJob` is not cancelled or a cancellation reaches `dispatchError` |
| AC-6 | unit | P0 | red | `toggleDuringRefresh_keepsSavedValueWhenSaveSettledFirst` | Refresh pending, heart on card 42 to true, save succeeds, fetch lands with `isSaved=false`: card 42 stays true; fails if the set patch is removed |
| AC-6 | unit | P0 | red | `toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch` | Fetch returns 42 and 43; while the save is pending the state still lists only `[42]`, after the save it lists `[42, 43]` and card 42 is true. Catches a missing `awaitPendingMutations()` (`CollectionDetailVM.kt:79`): without the await, 43 is published early (the patch alone masks the missing await in the other AC-6 tests) |
| AC-6 | unit | P1 | guard | `failedToggleDuringRefresh_showsRevertedValue` | Save fails during the refresh: published card 42 shows the original value; fails if the patch copies the requested value instead of the settled on-screen one |
| AC-6 | unit | P1 | guard | `refreshAfterPublishedToggle_showsServerSavedValue` | Return 1: heart on card 42 to true, save settles, fetch 1 lands (publish; the "42 true" setup is not asserted, base shows false). Return 2: heart on card 43 with its save still pending when the return starts, so the load does not clear the set; the save then settles and fetch 2 lands with 42 `isSaved=false`: card 42 shows false. Passes on base; fails if the set is not cleared on publish (R6), since 42 stays masked as true |
| AC-6 | unit | P1 | guard | `gridToggleBeforeReturn_showsServerSavedValue` | A1. Content shown, heart on card 42 to true, save succeeds; Details return, fetch reports `isSaved=false`: card 42 shows false (base reloads from the server, so it passes there); fails if the set is not cleared when a load starts with no save active |
| AC-6 | unit | P2 | red | `toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh` | A1 shape. Return 1, heart on 42 with the save pending, fetch 1 fails; return 2 with the save still pending; save succeeds, fetch 2 lands with `isSaved=false`: card 42 true; fails if the set is cleared on failure or on a load that starts while a save is active (base shows false) |
| AC-6 | unit | P1 | red | `settledToggleWhileAnotherSaveIsPending_showsServerSavedValue` | First load 42, 43. Toggle 42 to true, save 42 succeeds. Toggle 43 to true, save 43 pending. Return, fetch lands with both `isSaved=false`, then save 43 succeeds: card 42 false, card 43 true. Red on a021e92c (A1 clears the set only when no save is active, so 42 stays masked as true); passes after the `saveJobItemIds` change; fails if settled saves are not dropped at load start |
| AC-7 | instrumented | P1 | guard | `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced` | Focus a card in row 4 or lower by D-pad Down from the first card, swap `shown` for a list with that card's `isSaved` flipped: same card focused and displayed; fails if `Content` to `Content` recomposition resets focus or grid state |
| AC-7 | instrumented | P1 | red | `collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` | Same, the swapped list drops the focused card: the next card is focused, not `Card 1`; fails if reconciliation falls back to the top or the neighbour request is lost. Red evidence: `qa-report.md#round-1`, `ComposeTimeoutException` 3 of 3 on cad9f616 (`FocusTargetState.kt` unchanged since base); passes after Task 3 |
| AC-7 | instrumented | P1 | guard | `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` | `VideoGridInitialFocusTest`. One `runOnIdle` sets `initialFocusedItemId = 3` and replaces the retained row's items together: `Item 3` focused. Green on base, red on a021e92c (focus ends on `Item 1`: the stale composition-time id is written back); fails if the opt-in gate on the effect's resolve is missing (triage-1 row 2). Marked `guard` per plan: no `red` run on a base-equivalent helper |
| AC-7 | instrumented | P1 | guard | `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` | `VideoGridInitialFocusTest`. Rows `top` (1, 2, 3) and `bottom` (11, 12, 13); `Item 11` removed while focus is on `Item 1`; Down to `Item 12`, Right to `Item 13`, `LocalContentFocusActive` false then true: `Item 13` keeps focus. Green on base, red on a021e92c (`Item 12`: the pending requester stays on the stale neighbour); fails if the requester gate (`requestTargetId`) or the retry gate is missing (triage-1 rows 1 and 3). Marked `guard` per plan |
| AC-7 | instrumented | P1 | guard | `com.kino.puber.ui.feature.home.component.HomeSectionRemovalFocusTest` | Helper regression (R9): Home row removal: focus lands on the neighbour card; fails if the pinned requester or retry breaks the removal path. Green on cad9f616; a failure is compared with a cad9f616 run before it is filed against Task 3. Its `removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch` is red before this change (run `home-section-removal-focus-test-red`) and is not evidence against Task 4 |
| AC-7 | instrumented | P1 | guard | `com.kino.puber.ui.feature.home.component.HomeFocusTraversalTest` | Helper regression (R9): Home D-pad traversal; fails if the retry window overrides a user press or the pinned requester steals focus. Green on cad9f616; a failure is compared with a cad9f616 run before it is filed against Task 3 |
| AC-7 | instrumented | P1 | guard | `com.kino.puber.ui.feature.home.component.HomeCollectionBackFocusTest` | Helper regression (R9): Home focus restore after Back from a collection; fails if the resolve passed to the effect (`targetItemId`) loses the restored card. Green on cad9f616; a failure is compared with a cad9f616 run before it is filed against Task 3 |
| AC-7 | instrumented | P1 | guard | `com.kino.puber.ui.feature.contentlist.content.SectionRowFocusTraversalTest` | Helper regression (R9): SectionRow traversal; fails if the pending-id requester changes row focus movement. Green on cad9f616; a failure is compared with a cad9f616 run before it is filed against Task 3 |
| AC-7 | instrumented | P1 | guard | `com.kino.puber.core.ui.uikit.component.moviesList.VideoGridInitialFocusTest` | Helper regression (R9): VideoGrid first-load focus; fails if the first-load effect is disturbed by the new retrying request. Green on cad9f616; a failure is compared with a cad9f616 run before it is filed against Task 3 |
| AC-7 | instrumented | P1 | guard | `com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest` | Helper regression (R9): Show all grid focus when its card leaves; fails if the default (opt-out) behaviour changed |
| AC-7 | instrumented | P1 | guard | `com.kino.puber.ui.feature.collections.component.CollectionsScreenContentFocusTest` | Helper regression (R9): Collections opener focus; fails if the default behaviour changed |
| AC-8 | unit | P1 | guard | `emptyReturnedChanges_doNotRefetch` | Null and empty change sets: `getCollectionItems` called once; fails if the guard at `CollectionDetailVM.kt:99` is dropped |
| AC-8 | unit | P1 | guard | `returnedChanges_areForwardedToParentOnBack` | Existing test, unchanged |
| AC-8 | unit | P1 | guard | `back_waitsForSaveAndForwardsBookmarkChange` | Existing test, unchanged |
| AC-2 | instrumented | P0 | guard | `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced` | Automated half of AC-2: the same test as the AC-7 row, held at P0 for AC-2; focus and grid state survive a `Content` to `Content` swap, which is what the in-place refresh relies on |
| AC-2 | device | P0 | red | Manual 1-3 | Device half: spinner, top focus or reset scroll after Back on base; gone after. Re-entry timing shows only on a device |

### Fixtures

| Need | How |
|---|---|
| State sequence | Collector on `vm.testStateFlow` in `backgroundScope` with `UnconfinedTestDispatcher(testScheduler)`, as `SectionVMTest.kt:693-695` |
| Distinct fetch results | `mapper.mapShortItemList` answers `firstArg<List<Item>>().map { videoItem(it.id, isSaved = ...) }`; each fetch returns its own ids or `isSaved` (map `Item` bookmark fields or a per-call answer list) |
| Error path | `ErrorHandler` stub where `proceedInvoke(any(), any())` invokes its second argument with `ErrorEntity(message = "refresh failed", code = "test")`; the current `createVM` stubs only `proceed` |
| Return listener | Capture `navigateForResult` listener with `slot()`, as `returnedChanges_areForwardedToParentOnBack` |
| Instrumented VideoGrid guards | Build on `changedInitialFocusedItemIdRefocusesTheRetainedRow` (`VideoGridInitialFocusTest.kt:65-90`); `state` as `mutableStateOf`; `var active by mutableStateOf(true)` provided through `LocalContentFocusActive`; key press as `CollectionDetailScreenContentFocusTest.kt:175-180`; each guard asserts its preconditions |
| Instrumented swap | `var shown by mutableStateOf(items)` and `Detail(shown)` (`CollectionDetailScreenContentFocusTest.kt:60-61,97-107`); reach the deep card with D-pad Down, no `requestFocus` on the target |

## Bug: failing-first test

`returnedChanges_publishRefetchedContentWithoutLoading`, written first. Red on base: `git show 0e4449e2:app/src/main/java/com/kino/puber/ui/feature/collections/detail/vm/CollectionDetailVM.kt` lines 69-70 publish `Loading` before every fetch, including the one from `onReturnedContentChanges` (`:98-102`). Every other `red` row is run on the unchanged VM first as well.

AC-7 Task 4 guards (`changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow`, `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns`): green on base 0e4449e2, red on a021e92c (traced, `design-architect.md` addendum 2); no device for the implementer, so qa-device runs them against the a021e92c helper (R12). `settledToggleWhileAnotherSaveIsPending_showsServerSavedValue` is run red on the a021e92c VM first.

AC-7 neighbour test: `collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` is `red` on cad9f616 (qa round 1, 3 of 3); the implementer runs it on the unchanged `FocusTargetState.kt` only if a device is at hand, otherwise qa-device's round 1 is the evidence.

## Manual checks

Emulator lease, worktree dev APK via `tools/android-apk-install-preserve`, logged-in account, a collection with 12 or more items. Keys via `adb -s <serial> shell input keyevent`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| 1 | Open the collection; `DPAD_DOWN` x3, `DPAD_RIGHT`; note the card; `DPAD_CENTER` to Details; toggle watched; `BACK`. Repeat 3 times | Same card focused, rows above it still scrolled off, card shows the new watched state | Full-screen spinner, focus on the first card, grid at the top, crash |
| 2 | As 1, toggling the bookmark in Details instead | Same as 1, card heart matches Details | Same as 1; heart showing the old value |
| 3 | As 1, start playback from Details or the card, play a few seconds, `BACK` to the grid | Same as 1 | Same as 1 |
| 4 | Open a collection from Collections (first load) | Spinner, then the grid with the first card focused | A blank screen before the grid, focus on a card other than the first |

## perf

N/A: track off.
