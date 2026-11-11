# CollectionDetail refreshes in place after a returned change - plan

**Spec:** `docs/superpowers/specs/2026-11-11-collection-detail-inplace-refresh.md`
**Goal:** A Details or player return with `Content` on screen refetches and publishes a new `Content` with no `Loading`, in one load job, without reverting a heart toggled meanwhile.

**Verify round 1** (design-architect.md, "Addendum after verify round 1"): the AC-7 neighbour guard fails in production, not in the test. Task 3 fixes the shared helper's pending neighbour request. Observation 2 (stale watched mark) is outside this run.

**Review round 1** (design-architect.md, "Addendum after review round 1"; `.flow/runs/collection-detail-inplace-refresh/triage-1.md` rows 1-5): Task 3 regressed other callers of the shared helper. Task 4 puts it behind an opt-in that only CollectionDetail sets, adds two guards on VideoGrid, keys the heart patch to active saves, and strengthens one AC-6 test.

**Design amendment A1** (spec Amendment A1, adopted in state.md Decisions): Task 1 also clears `savedSinceLastPublish` when a load starts while no save is active (step 3). It reshapes `toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh` and `refreshAfterPublishedToggle_showsServerSavedValue` and adds the guard `gridToggleBeforeReturn_showsServerSavedValue`.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/user1/orca/workspaces/Puber/collection-detail-inplace-refresh && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`.
- Full gate: `cd /home/user1/orca/workspaces/Puber/collection-detail-inplace-refresh && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`.
- One unit class: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests com.kino.puber.ui.feature.collections.detail.vm.CollectionDetailVMTest`. androidTest compile: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
- Every command runs in the worktree through `./tools/agentw`, never `./gradlew` (the guard hook refuses it).
- The implementer has no device: never `connected*AndroidTest`, never adb. Instrumented runs and device checks belong to qa-device (Verification handoff).
- One task = green fast gate + green tests of the touched code + one commit, made by the implementer. Message: one imperative English sentence, no prefixes, never a `Co-Authored-By` line. No commit carries a red test; red runs are made on uncommitted code (production change stashed) and recorded under Implementation record > Red run.
- Detekt over production sources: `ReturnCount` max 3 (`config/detekt/detekt.yml:756-759`); `LongMethod` and `TooManyFunctions` exclude test sources (`:113-115`, `:157-159`).
- Scope: production change in `CollectionDetailVM.kt` (Tasks 1 and 4) and `FocusTargetState.kt` (Tasks 3 and 4, the pending-request path). Task 4 also adds one named argument in `CollectionDetailScreenContent.kt` and nothing else there. `launchMutation` and `awaitPendingMutations` are called, never edited (overlap with run `vm-mutation-queue`). No change to `CollectionDetailViewState.kt`, `FocusTargetResolver.kt`, `VideoGridUIState.kt`, any other helper caller, the screen, DI, resources, or any other VM.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else. Paths are relative to the worktree; `main/` = `app/src/main/java/com/kino/puber/`, `test/` = `app/src/test/kotlin/com/kino/puber/`, `aTest/` = `app/src/androidTest/kotlin/com/kino/puber/`. Lines are at base 0e4449e2.

---

## Tasks

### Task 1: Refresh CollectionDetail in place in a single load job

**Covers:** AC-1, AC-3, AC-4, AC-5, AC-6, AC-8
**Files:**
- Modify: `main/ui/feature/collections/detail/vm/CollectionDetailVM.kt` - fields next to `pendingMutations` (`:34`); rewrite `loadItems()` (`:69-80`); one line in `setItemSaved` (`:104-128`). Relies on `dispatchError` (`:37-43`), `RetryClicked -> loadItems()` (`:64`), `onReturnedContentChanges` (`:98-102`), `updateSavedItem` (`:130-138`), `launchMutation` (`:154-166`), `awaitPendingMutations` (`:168-174`); `PuberVM.launch` (`main/core/ui/PuberVM.kt:102-111`) whose `CoroutineExceptionHandler` (`:113-115`, `main/core/coroutine/CoroutinesErrorHandler.kt:10-14`) never sees a `CancellationException`.
- Test: `test/ui/feature/collections/detail/vm/CollectionDetailVMTest.kt`

**Consumes:** `CollectionInteractor.getCollectionItems(id): List<Item>` (`main/domain/interactor/collections/CollectionInteractor.kt:25-29`); `VideoItemUIMapper.mapShortItemList` (`main/core/ui/model/VideoItemUIMapper.kt:25`); `testStateFlow`, `testStateValue`, `testMessageValue` (`main/core/ui/PuberVM.kt:59-76`).
**Produces:** no new API; `loadItems()` semantics below.

Production shape (the order is load-bearing: nothing suspends between `awaitPendingMutations()` returning and `updateViewState`):

```kotlin
private var loadJob: Job? = null
private val savedSinceLastPublish = mutableSetOf<Int>()

private fun loadItems() {
    if (stateValue !is CollectionDetailViewState.Content) {
        updateViewState(CollectionDetailViewState.Loading)
    }
    if (pendingMutations.none(Job::isActive)) savedSinceLastPublish.clear()
    loadJob?.cancel()
    loadJob = launch {
        val items = interactor.getCollectionItems(collectionId)
        awaitPendingMutations()
        publishItems(mapper.mapShortItemList(items))
    }
}
```

`publishItems(fetched)`: for each fetched item whose id is in `savedSinceLastPublish` and present in the on-screen `Content`, copy the on-screen `isSaved`; publish `Content(title = collectionTitle, items = patched)`; clear the set. A failed fetch throws before `publishItems`, so the set is kept (spec Design, failure row).

Fixtures (one change to the existing test file, existing tests keep passing):

| Need | How |
|---|---|
| Queued fetches | `private val fetches = ArrayDeque<CompletableDeferred<List<Item>>>()`; `setup` (`:45-57`) stubs `coEvery { interactor.getCollectionItems(7) } coAnswers { fetches.removeFirstOrNull()?.await() ?: listOf(item(42)) }`; helper `queueFetch()` adds and returns a new deferred. A failure is `completeExceptionally(IllegalStateException("refresh failed"))` |
| Saved value through the mapper | `item(id, saved = false) = Item(id = id, title = "Movie $id", type = ItemType.MOVIE, inWatchlist = saved)` (`inWatchlist`: `main/data/api/models/Models.kt:64`); mapper stub in `setup` becomes `every { mapper.mapShortItemList(any()) } answers { firstArg<List<Item>>().map { videoItem(it.id, isSaved = it.inWatchlist == true) } }` |
| `videoItem` | `videoItem(id: Int, isSaved: Boolean = false) = VideoItemUIState(id, "Item $id", "", "", isSaved = isSaved)` (replaces `:128`) |
| Error path | `createVM` (`:118-126`) error handler also stubs `every { proceedInvoke(any(), any()) } answers { secondArg<((ErrorEntity) -> Unit)?>()?.invoke(ErrorEntity(message = "refresh failed", code = "test")) }`, as `test/ui/feature/history/vm/HistoryVMStoredFirstPageTest.kt:87-90` |
| State sequence | Inside `runTest`: `backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.testStateFlow.collect(states::add) }`, as `test/ui/feature/contentlist/vm/SectionVMTest.kt:693-695`; after each `complete` call `mainDispatcher.dispatcher.scheduler.advanceUntilIdle()` (as `:111`) |
| Return listener | `vm.onAction(CommonAction.ItemSelected(videoItem(42)))` then `verify { router.navigateForResult<ContentChangeSet>(any(), RESULT_CONTENT_CHANGED, capture(listener)) }` (as `:76-77`); the player path uses `CommonAction.ItemPlayed` |
| Toggle with a controlled save | `val save = CompletableDeferred<Result<Boolean>>()`; `coEvery { savedItemInteractor.setSaved(42, false, true) } coAnswers { save.await() }` (as `:102-103`); toggle with `vm.onAction(CommonAction.ItemSavedChanged(videoItem(42), true))` |

Tests (names and marks from `test-design.md#traceability`; message checks use `testMessageValue?.message`, `SnackbarMessage` is not a data class):

| AC | P | Mark | Test | Setup and assertion |
|---|---|---|---|---|
| AC-1 | P0 | red | `returnedChanges_publishRefetchedContentWithoutLoading` | Replaces `returnedChanges_reloadItems` (`:70-82`). First load lands; record from the return on; fetch 2 queued with items 42, 43. After the listener call `testStateValue` is still the first `Content`; after completing fetch 2: no `Loading` recorded, last state `Content` with ids 42, 43 |
| AC-1 | P2 | red | `playerReturn_publishRefetchedContentWithoutLoading` | Same through `ItemPlayed` |
| AC-3 | P1 | guard | `firstLoad_publishesLoadingBeforeContent` | Recorder started before `testOnStart()`; states are `Loading`, then `Content` |
| AC-3 | P1 | guard | `retry_publishesLoadingAfterError` | First fetch fails: state `Error("refresh failed")`; queue a pending fetch, `RetryClicked`: state `Loading` |
| AC-4 | P0 | red | `failedRefresh_keepsContentAndShowsMessage` | Fetch 2 fails: state equals the first `Content`, message `"refresh failed"` |
| AC-5 | P0 | red | `overlappingRefreshes_publishOnlyTheLatestFetch` | Two returns, fetch 1 (id 1) and fetch 2 (id 2) pending; complete 2, then 1: last state has id 2, no recorded state after it has id 1, `testMessageValue` null |
| AC-6 | P0 | red | `toggleDuringRefresh_keepsSavedValueWhenSaveSettledFirst` | Return, toggle 42 to true, save succeeds, fetch lands with 42 `saved=false`: card 42 `isSaved` true |
| AC-6 | P0 | red | `toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch` | Return, toggle, fetch lands with false while the save is pending, then the save succeeds: no `Content` recorded after the toggle shows 42 false; final 42 true |
| AC-6 | P1 | guard | `failedToggleDuringRefresh_showsRevertedValue` | Return, toggle, save completes `Result.failure(IllegalStateException("save failed"))`, fetch lands with false: card 42 false |
| AC-6 | P1 | guard | `refreshAfterPublishedToggle_showsServerSavedValue` | First load with 42, 43. Return 1, toggle 42 to true, save 42 succeeds, fetch 1 lands with 42 false (publishes 42 true; not asserted, base shows false). Toggle 43 to true with its save pending (`setSaved(43, false, true)` on a second deferred); return 2 while save 43 is pending, fetch 2 lands with 42 false, then save 43 succeeds: card 42 false. Catches a missing clear in `publishItems` (A1's clear in `loadItems()` is skipped because save 43 is active) |
| AC-6 | P1 | guard | `gridToggleBeforeReturn_showsServerSavedValue` | A1. Content shown, toggle 42 to true, save succeeds; return, fetch with 42 false: card 42 false |
| AC-6 | P2 | red | `toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh` | A1 shape. Return 1, toggle with the save pending, fetch 1 fails; return 2 with the save still pending; save succeeds, fetch 2 lands with false: card 42 true |
| AC-8 | P1 | guard | `emptyReturnedChanges_doNotRefetch` | Listener called with `null` and `ContentChangeSet.empty()`: `coVerify(exactly = 1) { interactor.getCollectionItems(7) }` |
| AC-8 | P1 | guard | `returnedChanges_areForwardedToParentOnBack` | Existing (`:84-98`), unchanged |
| AC-8 | P1 | guard | `back_waitsForSaveAndForwardsBookmarkChange` | Existing (`:100-116`), unchanged |

- [x] Write the fixtures and every test above on the unchanged VM; run the unit class. Expected: every `red` row fails, every `guard` row passes; `returnedChanges_publishRefetchedContentWithoutLoading` is written first. Record the result under Red run; a guard red on base goes to the conductor, not into the test
- [x] Add `loadJob` and `savedSinceLastPublish` after `pendingMutations` (`:34`)
- [x] Rewrite `loadItems()` (`:69-80`) to the shape above, with `publishItems` as a private function after it
- [x] In `setItemSaved` add `savedSinceLastPublish += item.id` before `updateSavedItem(item.id, saved)` (`:105`)
- [x] Run the unit class (all green), the fast gate and `:app:detektAll`; commit

**Done when:** the unit class passes with every row above, `:app:compileDevDebugKotlin` and `:app:detektAll` pass, and `git diff 0e4449e2 --stat -- app/src/main` lists only `CollectionDetailVM.kt`.

---

### Task 2: Guard focus across a Content-to-Content swap in CollectionDetail

**Covers:** AC-7; the automated half of AC-2 (`collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced`)
**Files:**
- Test: `aTest/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt` - add two tests and one helper. Relies on `items` (`:34-36`, 24 cards), `Detail(shown)` (`:96-107`), the `mutableStateOf` swap (`:60-61`), `focusedWith` (`:120-121`), `performDirection` (`:125-130`), constants (`:132-138`). The grid has 3 columns (`main/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:82`); a removed focused card hands focus to the card now at its index (`main/core/ui/uikit/component/moviesList/FocusTargetResolver.kt:10-23`).

**Consumes:** Task 1 is not needed; the composable is unchanged.
**Produces:** nothing.

| AC | P | Mark | Test | Steps and assertion |
|---|---|---|---|---|
| AC-7, AC-2 | P1 | guard | `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced` | `var shown by mutableStateOf(items)`; focus `Card 10` via the helper; `shown = shown.map { if (it.title == title) it.copy(isSaved = !it.isSaved) else it }`; advance `SettleMillis`, `waitForIdle`; `waitUntil` focused `Card 10`, then `assertIsFocused()` and `assertIsDisplayed()` |
| AC-7 | P1 | guard | `collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` | Same, `shown = shown.filter { it.title != title }`: `Card 11` focused and displayed; `Card 1` not focused |

- [x] Add helper `focusCardInRowFour(): String`: `requestFocus()` on `Card 1`, then `performDirection(Key.DirectionDown)` three times on the focused node with `mainClock.advanceTimeBy(SettleMillis)` after each; return the focused title and assert it is `Card 10` (row 4). No `requestFocus` on the target card
- [x] Add the two tests above
- [x] Run the androidTest compile and the fast gate; commit
- [ ] Hand the run of the class to qa-device (Verification handoff)

**Done when:** `:app:compileDevDebugAndroidTestKotlin` passes and qa-device reports both tests green on an emulator (`qa-report.md`).

---

### Task 3: Land focus on the neighbour when the focused card leaves a replaced list

**Covers:** AC-7 (`collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent`, failing 3 of 3 in `qa-report.md#round-1`)
**Files** (lines at HEAD cad9f616. `FocusTargetState.kt` is unchanged since base):
- Modify: `main/core/ui/uikit/component/moviesList/FocusTargetState.kt`
  - `rememberReconciledItemFocus` (`:39-126`): `targetItemId` is computed at `:76-80`, `pendingFocus` is declared at `:56`, the effects are called at `:93-101` and the state is returned at `:122-125`.
  - `ReconcilePublishedItemsEffect` (`:192-220`): the resolve at `:203-207` is replaced.
  - `RequestReconciledItemFocusEffects` (`:222-257`): the pending effect at `:240-247` is changed.
  - `requestAfterComposition` (`:302-307`) and `ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS` (`:309`) are reused.
- Modify: `aTest/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt:124`: the failure message only. The test name, steps and assertions do not change.

**Consumes:** `resolveFocusedItemId` (`main/core/ui/uikit/component/moviesList/FocusTargetResolver.kt:10-24`), unchanged.
**Produces:** no new API. `ReconciledItemFocusState.targetItemId` (`FocusTargetState.kt:22-32`) holds the pending card's id while a pending request names a card in `items`.

Why (design-architect.md, addendum): when a focused lazy card is removed, its slot is reset and `FocusTargetNode.onReset` clears focus, the view's focus included. Three paths can then lose the neighbour, and the fix closes all three:

| # | Path | Change |
|---|---|---|
| a | `ReconcilePublishedItemsEffect` re-resolves from the live `focusedItemId` (`:203-207`). A card focused between composition and the effect cancels the neighbour | Add a parameter `resolvedItemId: Int?` and pass `targetItemId` from `:76-80` at the call `:93-101`. In the effect, `val nextFocusedItemId = resolvedItemId` replaces `:203-207`. The rest of the effect stays as it is |
| b | The requester sits on `targetItemId` (`:122-125` into `CollectionDetailScreenContent.kt:98-99`). A card focused while the request waits for its frame rewrites `focusedItemId` (`:161`) and moves the requester | In `rememberReconciledItemFocus`, after `:76-80`: `val requestTargetId = pendingFocus.value.itemId?.takeIf { id -> items.any { it.id == id } } ?: targetItemId`. Pass `requestTargetId` as the first argument at `:122-123`. `canRestoreInactiveContent` (`:81-85`) and the other effects keep using `targetItemId` |
| c | The pending request is one `requestFocus()` after one frame, with the result dropped (`:240-246`, `:302-307`) | Add `private suspend fun FocusRequester.requestUntilFocused(awaitFirstFrame: Boolean)`. It awaits one frame if asked, then tries up to `ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS` times with `withFrameNanos { }` between tries and returns on the first `runCatching { requestFocus() }.getOrDefault(false)` that is true. The pending effect at `:244` calls it in place of `requestAfterComposition`, and `pendingFocus.value = PendingItemFocus()` stays after the call. The other callers of `requestAfterComposition` (`:237`) do not change |

- [x] Change the test's failure message at `:124`. Wrap the `waitUntil` in `runCatching`. On failure, call `assertTrue("expected $neighbour focused, focused: <titles>", false)`, where `<titles>` lists the `items` titles for which `focusedWith` is true, or `none`
- [x] Apply a, b and c in `FocusTargetState.kt`
- [x] Run the androidTest compile, the fast gate, `:app:detektAll` and `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests com.kino.puber.core.ui.uikit.component.moviesList.FocusTargetResolverTest`, then commit
- [ ] Hand the instrumented runs in the Verification handoff, Task 3 rows, to qa-device

**Stop rule:** if qa-device reports the neighbour guard still red, do not widen the change, and in particular do not touch `CollectionDetailScreenContent.kt`. Report the focused titles from the new message to the conductor.

**Done when:** `:app:compileDevDebugAndroidTestKotlin`, `:app:compileDevDebugKotlin` and `:app:detektAll` pass, and `git diff cad9f616 --stat -- app/src/main` lists only `FocusTargetState.kt`. qa-device then reports `CollectionDetailScreenContentFocusTest` green 3 of 3 and the shared-helper classes green (`qa-report.md`).

### Task 4: Keep the Task 3 focus change on CollectionDetail only, and key the heart patch to active saves

**Covers:** AC-7 (the neighbour test stays green; the other callers get their base behaviour back), AC-6 (`triage-1.md` rows 4-5)
**Files** (lines at HEAD a021e92c):
- Modify: `main/core/ui/uikit/component/moviesList/FocusTargetState.kt`
  - `rememberReconciledItemFocus` parameters `:40-48`, `requestTargetId` `:81-83`, effect calls `:96-116`, return `:126-129`.
  - `ReconcilePublishedItemsEffect` `:196-221`: the line `val nextFocusedItemId = resolvedItemId` is at `:208`.
  - `RequestReconciledItemFocusEffects` `:223-258`: the pending effect is at `:241-248`, and the call is at `:245`.
  - `requestUntilFocused` `:303-311`, `requestAfterComposition` `:313-318`.
  - Base text of the two gated spots that existed at base: `git show 0e4449e2:app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt`, lines 203-207 (the effect's resolve) and 244 (`requestAfterComposition` in the pending effect).
- Modify: `main/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:59-65`: one argument.
- Modify: `main/ui/feature/collections/detail/vm/CollectionDetailVM.kt`: fields `:34-36`, `loadItems()` `:71-82` (A1 line `:75`), `setItemSaved` `:121-146` (`launchMutation {` at `:124`), `launchMutation` `:172-184` (returns the `Job`, not edited).
- Test: `test/ui/feature/collections/detail/vm/CollectionDetailVMTest.kt`: `toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch` `:199-218`; the pattern of `refreshAfterPublishedToggle_showsServerSavedValue` `:237-263`; helpers `queueFetch` `:372`, `controlledSave` `:374-376`, `settle` `:378`, `recordStates` `:380-386`, `returnListener` `:388-397`, `cardSaved` `:399-400`, `item` `:402-403`, `videoItem` `:405-406`, `changed` `:370`.
- Test: `aTest/core/ui/uikit/component/moviesList/VideoGridInitialFocusTest.kt`: seam `changedInitialFocusedItemIdRefocusesTheRetainedRow` `:65-90`, `videoItem` `:249-257`, `row` `:259-264`. `LocalContentFocusActive` is `main/core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:30` (`staticCompositionLocalOf { true }`). VideoGrid passes `initialFocusedItemId` and a per-row `isTargetRow` to the helper (`main/core/ui/uikit/component/moviesList/VideoGridUIState.kt:228-231`, `:417-424`).

**Consumes:** `resolveFocusedItemId` (`main/core/ui/uikit/component/moviesList/FocusTargetResolver.kt:10-24`), unchanged.
**Produces:** `rememberReconciledItemFocus(..., holdPendingFocusTarget: Boolean = false, ...)`. When false, the helper behaves exactly as at base 0e4449e2. When true, it behaves as at a021e92c.

Focus helper (rows 1-3):

| # | Spot | Change |
|---|---|---|
| 1 | Parameters `:40-48` | Add `holdPendingFocusTarget: Boolean = false` after `restoreAcrossProcess`. KDoc: "Opt-in for a single grid that is always the target row and passes no `initialFocusedItemId`. The pending neighbour stays the requester's card until its request lands, the published-items effect keeps the id resolved in composition, and the request retries over up to `ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS` frames. Off, focus reconciles as before." |
| 2 | `:81-83` | `val requestTargetId = if (holdPendingFocusTarget) { <current expression> } else targetItemId` |
| 3 | `:196-221`, `:208` | `ReconcilePublishedItemsEffect` gains `holdPendingFocusTarget: Boolean`, passed at `:96-105`. `val nextFocusedItemId = if (holdPendingFocusTarget) resolvedItemId else resolveFocusedItemId(previousItems = previousItems.value, updatedItems = items, focusedItemId = focusedItemId.value)` |
| 4 | `:223-258`, `:245` | `RequestReconciledItemFocusEffects` gains `retryPendingRequest: Boolean`, passed `holdPendingFocusTarget` at `:106-116`. At `:245`: `if (retryPendingRequest) focusRequester.requestUntilFocused(requestAfterFrame) else focusRequester.requestAfterComposition(requestAfterFrame)` |
| 5 | `CollectionDetailScreenContent.kt:59-65` | Add `holdPendingFocusTarget = true,` after `requestAfterFrame = true,` |

VM (row 4):

```kotlin
private val saveJobItemIds = mutableMapOf<Job, Int>()   // next to savedSinceLastPublish (:36)

// loadItems(), replacing :75
saveJobItemIds.keys.removeAll { !it.isActive }
savedSinceLastPublish.retainAll(saveJobItemIds.values.toSet())

// setItemSaved, :124
val job = launchMutation { /* body unchanged */ }
saveJobItemIds[job] = item.id
```

Tests (names go into `test-design.md#traceability` with the same names; test-designer's file):

| AC | P | Mark | Test | Setup and assertion |
|---|---|---|---|---|
| AC-6 | P1 | red | `settledToggleWhileAnotherSaveIsPending_showsServerSavedValue` | As `:237-247`: first load 42, 43. Toggle 42 to true, save 42 succeeds, `settle()`. `controlledSave(43)`, toggle 43 to true (pending). `queueFetch()`, `listener(changed)`, the fetch completes with `item(42)`, `item(43)` (both false), `settle()`, save 43 succeeds, `settle()`: `cardSaved(42)` false and `cardSaved(43)` true. Red on a021e92c: save 43 is active at load start, so 42 stays in the set and is patched to true |
| AC-6 | P0 | red | `toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch` (strengthened) | The fetch completes with `listOf(item(42, saved = false), item(43))`. After that `settle()`, while the save is pending: `(vm.testStateValue as Content).items.map { it.id }` equals `[42]`. After the save and `settle()`: ids `[42, 43]`, and both existing assertions stay. Red if `awaitPendingMutations()` (`CollectionDetailVM.kt:79`) is removed |
| AC-7 | P1 | guard | `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` | As `:65-90`, but `state` is a `mutableStateOf`. After `Item 1` is focused, one `runOnIdle` sets `initialFocusedItemId = 3` and replaces the row "retained" with items `videoItem(1)`, `videoItem(2).copy(isWatched = true)`, `videoItem(3)`: `Item 3` focused. Green on base, red on a021e92c (Item 1) |
| AC-7 | P1 | guard | `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` | `var active by mutableStateOf(true)`. Rows `top` (1, 2, 3) and `bottom` (11, 12, 13). Content: `CompositionLocalProvider(LocalContentFocusActive provides active) { VideoGrid(state = state, initialFocusedItemId = 1) }` inside `PuberTheme`. Assert `Item 1` focused. `runOnIdle`: `bottom` becomes (12, 13). Press Down on the focused node and assert `Item 12` focused. Press Right and assert `Item 13` focused. `runOnIdle { active = false }`, `waitForIdle()`, `runOnIdle { active = true }`: `Item 13` focused, `Item 12` not focused. Key press as `aTest/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt:175-180`. Green on base, red on a021e92c (Item 12) |

- [x] Write the two VM tests on the unchanged VM and run the unit class. Expected: `settledToggle...` fails. To check that the strengthened test detects a missing await, comment out `:79` temporarily: it fails, then restore the line. Record both under Implementation record > Red run
- [x] Apply VM row 4; run the unit class (all green)
- [x] Apply focus rows 1-5
- [x] Add the two instrumented guards; run the androidTest compile
- [x] Run the full gate and `:app:compileDevDebugAndroidTestKotlin`; commit once
- [ ] Hand the Task 4 rows of the Verification handoff to qa-device

**Done when:** the unit class passes, the full gate and `:app:compileDevDebugAndroidTestKotlin` pass, and `git diff a021e92c --stat -- app/src/main` lists only `FocusTargetState.kt`, `CollectionDetailScreenContent.kt` and `CollectionDetailVM.kt`. In `git diff 0e4449e2 -- app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt`, every changed behaviour sits in a branch taken only when `holdPendingFocusTarget` is true. qa-device then reports the Task 4 rows green (`qa-report.md`).

---

## Verification handoff (qa-device)

| What | Command or procedure | Expected |
|---|---|---|
| AC-7 and the automated half of AC-2, instrumented | `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest` | All tests of the class pass, including the three existing ones |
| AC-2 on base (manual half) | `test-design.md#manual-checks` rows 1-3 on a 0e4449e2 dev APK | Spinner and focus on the first card (red) |
| AC-2 after | Manual rows 1-4 on the worktree dev APK, installed with `tools/android-apk-install-preserve`, 3 repeats each for rows 1-3 | As the Expected column of `test-design.md#manual-checks` |
| Task 3, AC-7 | `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest`, 3 runs | All five tests pass 3 of 3 |
| Task 3, shared-helper regression | The same command with `TESTS=` set to each of `com.kino.puber.ui.feature.home.component.HomeSectionRemovalFocusTest`, `com.kino.puber.ui.feature.home.component.HomeFocusTraversalTest`, `com.kino.puber.ui.feature.home.component.HomeCollectionBackFocusTest`, `com.kino.puber.ui.feature.contentlist.content.SectionRowFocusTraversalTest`, `com.kino.puber.core.ui.uikit.component.moviesList.VideoGridInitialFocusTest` | Each class passes. A failure is compared with a run on cad9f616 before it is filed against Task 3 |
| Task 3, AC-2 recheck | Manual rows 1 and 2 on the new worktree APK, 3 repeats each | Unchanged from round 1 |
| Task 4, guards red on a021e92c | In the worktree: `git checkout a021e92c -- app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`, run `TESTS=com.kino.puber.core.ui.uikit.component.moviesList.VideoGridInitialFocusTest`, then `git checkout HEAD -- <the same two paths>` | `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` and `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` fail, and the other 7 pass. A guard that passes here guards nothing (design risk 10): report it to the conductor |
| Task 4, AC-7 | `TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest`, 3 runs | All five tests pass 3 of 3 |
| Task 4, helper regression | `TESTS=` set to each of `com.kino.puber.core.ui.uikit.component.moviesList.VideoGridInitialFocusTest`, `com.kino.puber.ui.feature.home.component.HomeFocusTraversalTest`, `com.kino.puber.ui.feature.home.component.HomeCollectionBackFocusTest`, `com.kino.puber.ui.feature.contentlist.content.SectionRowFocusTraversalTest`, `com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`, `com.kino.puber.ui.feature.collections.component.CollectionsScreenContentFocusTest`, `com.kino.puber.ui.feature.home.component.HomeSectionRemovalFocusTest` | Each passes. The exception is `HomeSectionRemovalFocusTest.removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch`, which is red before this change (run `home-section-removal-focus-test-red`): it fails the same way as in round 2 |
| Task 4, AC-2 and AC-6 recheck | Manual rows 1 and 2 on the new worktree APK, 3 repeats each | Unchanged from round 1 |

---

## Implementation record

<!-- owner: implementer -->

**Commits:**
- 22aff7ce Refresh CollectionDetail in place in a single load job without reverting a toggled heart (Task 1)
- Task 2 commit: the two instrumented focus tests and this record (subject: "Guard focus across a Content swap in CollectionDetail")

**Red run** (P0 `red` tests on the unchanged code, 16 tests in the class, 7 failed, 9 passed):
- red: returnedChanges_publishRefetchedContentWithoutLoading - assertSame: state after the return is `Loading`, not the first `Content`
- red: playerReturn_publishRefetchedContentWithoutLoading - assertTrue: a `Loading` was published after the return
- red: failedRefresh_keepsContentAndShowsMessage - expected the first `Content`, was `Error`
- red: overlappingRefreshes_publishOnlyTheLatestFetch - expected ids [2], was [1]
- red: toggleDuringRefresh_keepsSavedValueWhenSaveSettledFirst - expected true, was false
- red: toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch - expected true, was false
- red: toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh - expected true, was false
- guards passed on base as designed: firstLoad_publishesLoadingBeforeContent, retry_publishesLoadingAfterError, failedToggleDuringRefresh_showsRevertedValue, refreshAfterPublishedToggle_showsServerSavedValue, gridToggleBeforeReturn_showsServerSavedValue, emptyReturnedChanges_doNotRefetch, and the two existing AC-8 tests
- Task 2 instrumented tests: compile only (qa-device runs them)

**Task 3 (verify round 1 fix):**
- Commit: "Keep the neighbour request alive when a focused card leaves a replaced list" (sha in `git log`)
- Red run: n/a. The failing test is instrumented and already red 3 of 3 in `qa-report.md#round-1`; no device here.
- Applied a, b, c in `FocusTargetState.kt` as planned. `requestUntilFocused` tries first and waits a frame between tries (after the optional first frame), at most `ROOT_ANCHOR_FOCUS_REQUEST_ATTEMPTS` tries.
- Test: only the failure message of `...MovesToTheNeighbour...` changed; it names the focused titles or `none`.
- Check: `testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:compileDevDebugAndroidTestKotlin` -> BUILD SUCCESSFUL.
- Reviewers: the shared helper serves Home rows, SectionRow, VideoGrid and Bookmarks (risk 7); qa-device runs their instrumented classes.

**Deviations from the plan:**
- `refreshAfterPublishedToggle_showsServerSavedValue`: the "42 true" precondition after fetch 1 is not asserted, as the plan says; the order is return 1, toggle 42, save 42, fetch 1, toggle 43, return 2.
- Test helpers added beyond the fixture table: `startedVM`, `returnListener`, `controlledSave`, `cardSaved`, `settle`, `recordStates`. The `vm` field is shared by the tests.
- `publishItems` takes the mapped `List<VideoItemUIState>` and looks up on-screen cards by id.

**Files touched outside the plan:** none.

**For the reviewers:**
- Nothing suspends between `awaitPendingMutations()` returning and `updateViewState` in `loadItems()` / `publishItems`.
- The `pendingMutations.none(Job::isActive)` check at load start (A1, risk 6): a finished job removes itself in `launchMutation`'s `finally`, and the check runs on the main thread.
- Task 2 tests are unrun on a device.

**Check:** `testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:compileDevDebugAndroidTestKotlin` - BUILD SUCCESSFUL in 3m 27s.

**Blocked:** none.

## Readiness

<!-- owner: final-judge, mode readiness; the conductor pastes readiness-<n>.md here. READY | NOT READY and why. -->

# Readiness 2: collection-detail-inplace-refresh

Verdict: **READY**

Worktree HEAD 835e55ad, base 0e4449e2, clean tree. Change since round 1 (`git diff 2fbf378e HEAD`): plan and spec docs only. Spec Test design and `test-design.md` traceability rows are identical (`diff` empty).

## Round 1 fix list

| Fix | Status | Evidence |
|---|---|---|
| 1. `refreshAfterPublishedToggle_showsServerSavedValue` catches its mutation | Resolved | Spec `:139`, `test-design.md:33`, plan `:83`. Trace against the plan's `loadItems()` shape: save 43 is active at return 2, so the A1 clear at `:46` of the shape is skipped; without the clear in `publishItems`, 42 stays in the set and fetch 2's false is masked as true, so the test fails. With the clear, 42 shows false. On base (`CollectionDetailVM.kt:69-80,130-138`) the toggle of 42 during `Loading` is a no-op and fetch 2 publishes 42 false: guard passes. Unasserted precondition is stated |
| 2. Handler citation | Resolved | Plan `:30` cites `main/core/coroutine/CoroutinesErrorHandler.kt:10-14`; that range is `DefaultExceptionHandler` returning a `CoroutineExceptionHandler` |
| 3. AC-2 P0 names an automated test | Resolved | Spec `:147`, `test-design.md:41`: P0 guard `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced`; device half `Manual 1-3` kept as manual by nature (`:148`/`:42`). Plan Task 2 `:102,111` and handoff `:127` cover it |
| 4. Plan line 6 | Resolved | "pending conductor confirmation" gone; A1 recorded as adopted, matching state.md Decisions (2026-11-11, Plan Q1) |

## Regression checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | Yes, unchanged from round 1; AC-2 now also in Task 2 |
| Citations resolve (resampled at HEAD) | `CollectionDetailVM.kt:34,37-43,64,69-80,98-102,104-128,130-138,154-166,168-174`; `PuberVM.kt:102-111,113-115`; `CollectionDetailVMTest.kt:45-57,70-82,100-116,118-126,128`: all resolve |
| Build stays green per task, order by dependency | Yes, task bodies unchanged apart from the rows above |
| P0 rows name defect, mark, test | Yes. AC-2 instrumented row names its defect by reference to the AC-7 row (`:142`) |
| Out of scope, blocking questions | Unchanged; none open |

Note (non-blocking): plan Task 2 row `:111` lists the shared test at P1 while the spec holds it at P0 for AC-2; priority does not change the work.

**Task 4 (review round 1 fix):**
- Commit: "Keep the pending focus target on CollectionDetail only and track active saves by id" (sha in `git log`)
- Red run (unit, production unchanged):
  - red: settledToggleWhileAnotherSaveIsPending_showsServerSavedValue - AssertionFailedError at the card 42 `false` assertion (42 stays in the set, patched to true)
  - green on base: toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch (strengthened) - the patch and await both hold on HEAD; one-off with `awaitPendingMutations()` commented out at `CollectionDetailVM.kt:79`: it fails at the `[42]` ids assertion (`CollectionDetailVMTest.kt:212`), then the line was restored
- Instrumented guards `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` and `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns`: compile only; qa-device runs them red on the a021e92c helper
- Deviation: detekt `CyclomaticComplexMethod` (15 > 14) on `RequestReconciledItemFocusEffects`, so the plan's if/else at `:245` sits in a private `FocusRequester.requestPending(retry, awaitFrame)`. Same behaviour.
- Look at: the opt-in gates in `FocusTargetState.kt` (three spots, each off = base text), and `saveJobItemIds` pruning in `loadItems()`.

## Squash

The ten run commits were squashed into three before ship. Old SHAs in this plan and the evidence map as follows; commit 3 holds the docs and cannot list its own SHA.

| New commit | Old commits |
|---|---|
| 1. `4ce8a72d` Refresh CollectionDetail in place in a single load job without reverting a toggled heart | `22aff7ce`, VM part of `48ff9085` |
| 2. `18988650` Keep focus across a Content swap in CollectionDetail and drop the pending target when a card leaves | `cad9f616`, `d1dea378`, focus part of `48ff9085` |
| 3. docs (this commit) | `529f4e93`, `2fbf378e`, `835e55ad`, `40e597b0`, `a021e92c`, `28f986b0` |
