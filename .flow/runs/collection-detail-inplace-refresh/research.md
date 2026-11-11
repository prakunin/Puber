# Research: collection-detail-inplace-refresh

## Request

"CollectionDetail reloads through Loading after Details returns content changes (`CollectionDetailVM.kt` onReturnedContentChanges -> loadItems -> Loading), which disposes the Content branch and drops the restored focus (rememberReconciledItemFocus, merged in eb67973c) and the grid scroll; focus lands on the first card." Source: grid-focus-restore-after-back (design-architect.md R2, review-blind-1.md F1, triage-1.md #1).

## Understanding

When the user returns from Details or the player with any change (watched, bookmark, watchlist, progress), CollectionDetail swaps its screen to a full-screen spinner and then rebuilds the grid. Because the grid, its scroll state and the remembered focused card all live inside the `Content` branch, they are thrown away, so the user lands on card 1 at the top. The fix is to refresh the items while `Content` stays on screen. The focus reconciler already handles a changed item list inside a live composition.

## Current state in code

- `ui/feature/collections/detail/vm/CollectionDetailVM.kt:69-80`: `loadItems()` always publishes `Loading` first, then `Content`. It is used by `onStart` (:45-47), Retry (:64) and returns (:101).
- `CollectionDetailVM.kt:98-102`: `onReturnedContentChanges` merges the changes (forwarded to the parent on Back, :150) and calls `loadItems()` for any non-empty set, whatever its types.
- `CollectionDetailVM.kt:71`: the launch is not cancelled or sequenced, so two quick returns run two fetches that can land out of order.
- `CollectionDetailVM.kt:37-43`: `dispatchError` already shows a message (and keeps the grid) when the state is `Content`. A failed in-place refresh therefore keeps the stale grid.
- `CollectionDetailVM.kt:104-138`: the optimistic save toggle patches `isSaved` in place (`updateSavedItem`) without reloading.
- `domain/interactor/collections/CollectionInteractor.kt:25-29`: `getCollectionItems` always hits the network (`collections/view`, `KinoPubApiClient.kt:555`). It then merges into the content cache. There is no paging and no cache read.
- `core/ui/model/VideoItemUIMapper.kt:29-53`: `isWatched` and progress come from `WatchStateRepository` (:111-112) when one is injected; CollectionDetail injects it (`CollectionDetailScreen.kt:48`). `isSaved` comes from the server payload (`inWatchlist`/`subscribed`, or `bookmarks`).
- `collections/detail/component/CollectionDetailScreenContent.kt:46-65`: `when (state)` puts `Loading` in a separate branch. `rememberReconciledItemFocus` and the `LazyVerticalGrid` (default grid state) sit inside `is Content`.
- `core/ui/uikit/component/moviesList/FocusTargetState.kt:40-101`: the focused id is held in `rememberSaveable(rowKey)`. `resolveFocusedItemId(previousItems, updatedItems, …)` reconciles a changed list in place (a neighbour when the focused card leaves). This only works while the composition survives.
- `core/content/ContentChange.kt:3-9`: change types are `PlaybackProgress`, `Watched`, `Bookmark`, `Watchlist`, `History`.
- Emitters: Details sends `Watched`, `Watchlist` and `Bookmark` (`details/vm/DetailsVM.kt:229,248,423,442,468,503-505`). The player sends `Watched` and `PlaybackProgress` (`player/vm/PlayerVM.kt:1086-1201,1857`).
- Tests: `app/src/test/.../collections/detail/vm/CollectionDetailVMTest.kt:71-82` (`returnedChanges_reloadItems` checks only the second fetch, not the state sequence). Also `CollectionDetailScreenTest.kt` and the instrumented `app/src/androidTest/.../CollectionDetailScreenContentFocusTest.kt` (its tests never go through `Loading`, per review-blind-1 F1).

### How other screens handle a returned ContentChangeSet

| Screen | Return handler | Loading flash? | Mechanism |
|--------|----------------|----------------|-----------|
| Show all | `showall/vm/ShowAllVM.kt:110-115` | No | `refreshFirstPage()` uses Paginator `Refreshing(data)` (`core/paginator/Paginator.kt:192-199`), mapped to `Content` (`ShowAllVM.kt:136-138`); also remaps from loaded items on `watchStateChanges` (:58-68) |
| Bookmarks | `bookmarks/vm/BookmarksVM.kt:111-118` | Only a full reload when Bookmark/Watchlist changed (`loadBookmarks` publishes `Content`, not `Loading`); otherwise `loadFolderItems` updates `items` in place with `copy` (:141-152) | Remaps loaded items on `CardDisplayChanges` (:46-64) |
| Home | `home/vm/HomeVM.kt:234-238` | No | `silentRefresh(HomeRefreshTargets.from(changes))` refetches only the affected rows (:683-693). `savedOverrides` keep a pending toggle from being reverted by the refresh (:250-254, :639-655) |
| History | `history/vm/HistoryVM.kt:507,513,764-770` | `Loading` only when there is no content (:416-420) | `requestResumeRefresh` pages in place |
| ContentList | `contentlist/vm/ContentListVM.kt:152-171` | No | Invalidate cache, coordinator refresh, refetch focused details only if affected |
| Favorite | `favorites/vm/FavoriteVM.kt:101-104` | No | `loadData(force = true)` republishes `Content` |
| Search | `search/vm/SearchVM.kt:128-131` | Yes, by design | `restartSearch()` (tracked as idea run `search-reset-after-details`) |

### Can an item leave a collection?

Not through any change a returning screen reports. Collection membership is the server's curated list from `collections/view`, and no emitter changes it. The reported types change only how a card is drawn: `isSaved`, `isWatched` and progress. A refetch can still return a different set if the server's list changed meanwhile; the reconciler handles that while `Content` stays composed.

## Related work

| Slug / ref | Relation |
|------------|----------|
| grid-focus-restore-after-back (done; eb67973c, 883e5a0d) | Source. Added `rememberReconciledItemFocus` to CollectionDetail; Design Q2 deferred this run (`design-architect.md` R2, `review-blind-1.md` F1, `triage-1.md` #1) |
| collection-detail-early-entry-test (idea) | Same screen, an instrumented focus-entry test; does not overlap |
| vm-mutation-queue (idea) | Replaces `CollectionDetailVM`'s `launchMutation`/`awaitPendingMutations`. Would conflict on the same file but not in purpose |
| tab-refresh-while-covered-flash (idea) | Same class of bug (refresh flash) on tabs, other screens |
| search-reset-after-details (idea) | The one screen that resets after Details on purpose |

Duplicate: none.

## External findings

External findings: off (local research only).

## Options

| Option | Change | Trade-offs | Size |
|--------|--------|------------|------|
| A. Silent refetch (recommended) | `loadItems(showLoading = stateValue !is Content)`: on a return with `Content` on screen, fetch and publish a new `Content` with no `Loading`. Cancel or replace the previous load job | Mirrors Favorite/Bookmarks/Show all. One network call per return, as today. Fixes focus and scroll with no Compose change. Needs a guard so an in-flight optimistic save is not overwritten by older server data (as Home's `savedOverrides`), or at least the load must run after pending mutations | S |
| B. Typed refresh | `Watched`/`PlaybackProgress` only: remap the held `Item` list (watch state comes from `WatchStateRepository`), no request. `Bookmark`/`Watchlist`: silent refetch as in A | Saves a request on the commonest return (the player). Needs the raw items kept in the VM, as Bookmarks does. Assumes `WatchStateRepository` is already updated when the return fires (`unverified`) | S |
| C. Hoist UI state | Move the focus memory and `rememberLazyGridState` above `when (state)` in `CollectionDetailScreenContent` and keep the VM as is | The spinner flash stays. Hoisting `rememberReconciledItemFocus` out of the `Content` branch changes its `previousItems`/effects lifetime across a `Loading` gap. That path is untested and more fragile than A | M |

Recommended: A. B can be added later if the extra request matters.

## Risks

- **Revert of a fresh toggle.** If the user toggles a heart while a silent refetch is in flight, the older payload can land after the optimistic `updateSavedItem` and flip the heart back. Today the same race exists but is hidden behind the spinner. Options: await `pendingMutations` before publishing, or keep per-id overrides as `HomeVM.kt:639-655` does.
- **Out-of-order loads.** Two returns in a row run two unsequenced launches (`CollectionDetailVM.kt:71`). Keep a single job (cancel previous), as `FavoriteVM.kt:41-52` does.
- **Retry path.** Retry from `Error` must still show `Loading`; only the in-`Content` refresh is silent.
- **Device-only behaviour.** Focus and scroll survival show only at runtime. Per AGENTS.md this needs on-device verification (open card 8, toggle watched in Details, Back) and an instrumented test going Content -> refreshed Content (review-blind-1 F1 fix note).
- `CollectionDetailVMTest.kt:71-82` must change to assert no `Loading` is published on a return.

## Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Refresh shape: silent refetch (A), typed remap plus refetch (B), or hoisted UI state (C)? | A |
| Q2 | Guard against a refresh reverting a just-made save toggle: await pending mutations before publishing, or keep per-id overrides? | Await pending mutations (smaller; the screen already tracks them) |
| Q3 | On a silent refresh failure, keep the stale grid and show the message (current `dispatchError` behaviour for `Content`)? | Yes |

## Sources

- Files: `CollectionDetailVM.kt`, `CollectionDetailScreenContent.kt`, `CollectionDetailScreen.kt`, `CollectionDetailViewState.kt`, `CollectionInteractor.kt`, `VideoItemUIMapper.kt`, `VideoItemUIState.kt`, `FocusTargetState.kt`, `ContentChange.kt`, `CardDisplayChanges.kt`, `Paginator.kt`, `PagingVM.kt`, `ContentListPagingVM.kt`, `ShowAllVM.kt`, `BookmarksVM.kt`, `HomeVM.kt`, `HistoryVM.kt`, `ContentListVM.kt`, `FavoriteVM.kt`, `SearchVM.kt`, `DetailsVM.kt`, `PlayerVM.kt`, `CollectionDetailVMTest.kt`.
- Commits: eb67973c, 883e5a0d, c2a81dd6 (`git log -- app/src/main/java/com/kino/puber/ui/feature/collections/detail/`).
- Runs: `.flow/runs/grid-focus-restore-after-back/` (`state.md`, `design-architect.md`, `review-blind-1.md`, `triage-1.md`), `collection-detail-early-entry-test`, `vm-mutation-queue`, `tab-refresh-while-covered-flash`, `search-reset-after-details`.
