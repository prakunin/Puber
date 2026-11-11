# Research: bookmarks-context-menu-focus-loss

Base: master 8b116d22. Paths below are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `.flow/`.

## 1. Request

Bookmarks may lose context-menu focus when the focused card is removed while its menu is open, the same exposure CollectionDetail had (D2 in grid-focus-restore-after-back; fixed there by providing `LocalContentFocusActive = contentFocusActive && contextMenuItem == null`, see CollectionDetailScreenContent.kt on master). Source run folder: .flow/runs/grid-focus-restore-after-back/ (design-architect.md addendum after verify round 1, open question 5).

## 2. Understanding

The exposure is real in code, but the trigger is narrow. Bookmarks renders its menu as an overlay in the same composition, and nothing parks the helper's focus requests while that overlay is open. If the list loses the focused card while the menu is open, the helper requests the neighbour card, and the neighbour takes focus away from the menu. The menu's own "Remove" action does not cause this, because the menu closes before the action runs. The trigger is a list update that lands while the user has the menu open: a removal still in flight from an earlier menu action, or a reload after returning from Details or the player. Favourites, which is on by default, and the Home rows use the same helper with the same unguarded menu. History, Show all and CollectionDetail are guarded.

## 3. Current state in code

**How the menu is rendered**

- The menu is an overlay, not a dialog window. `VideoItemContextMenuDialog` → `TvContextMenuDialog` → `TvDialogOverlay`: a full-screen `Box` with a scrim and `.focusGroup()` in the caller's composition (`core/ui/uikit/component/TvContextMenu.kt:95`, `core/ui/uikit/component/TvDialogOverlay.kt:46-54`). After 100 ms the first action requests focus, with up to 4 retries (`TvContextMenu.kt:77-92`).
- Every menu action calls `dismiss()` before `onAction(action)` (`TvContextMenu.kt:116-119`). In Bookmarks, `dismiss` sets `contextMenuItem = null` (`ui/feature/bookmarks/component/BookmarksScreenContent.kt:158`). This covers "Remove from bookmarks" and "Mark watched".
- The Bookmarks `TvDialogFocusRestorer` saves the grid's focused child when the menu opens, through `onTvContextMenuKey` (`TvContextMenu.kt:325-328`). It restores that child, or falls back to the helper target, when the overlay is disposed (`BookmarksScreenContent.kt:80-94`, `TvDialogOverlay.kt:38-42`). It does nothing while the menu stays open.

**What the helper does on removal while the menu is open** (`core/ui/uikit/component/moviesList/FocusTargetState.kt`)

| Step | Code |
|---|---|
| The menu takes focus, so the grid's `onFocusChanged` sets `rowHasFocusRef[0] = false` | `BookmarksScreenContent.kt:122-124` |
| A new `items` list without `focusedItemId` resolves to the neighbour at the same index, else the previous card, else the first card | `FocusTargetResolver.kt:10-24` |
| `ReconcilePublishedItemsEffect` publishes `PendingItemFocus(neighbour)` | `FocusTargetState.kt:221-237` |
| The pending effect requests focus when `contentFocusActive \|\| canRestoreInactiveContent`. `contentFocusActive` is true for a tab while the rail is closed (`ui/feature/main/component/MainScreenComponent.kt:138`) and defaults to true (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:30`). Bookmarks never overrides it | `FocusTargetState.kt:264-270` |
| `requestFocus()` on the neighbour card succeeds: the overlay's `focusGroup` does not trap focus. The menu stays drawn, because `contextMenuItem` is still set, but no action in it has focus | inference from the code, `unverified` on device |

**Paths that can remove a Bookmarks card while its menu is open** (`ui/feature/bookmarks/vm/BookmarksVM.kt`)

| Path | Can it remove the card under the open menu? | Code |
|---|---|---|
| A menu "Remove" action on the same card | Not directly: the menu closes first, and focus goes back to the card through the restorer. The removal lands after the network call | `BookmarksVM.kt:154-162`, `TvContextMenu.kt:116-119` |
| Reopening the menu on that card while its removal is still in flight | Yes. There is no in-flight guard, and the card still shows `isSaved = true`, so the menu reopens and the removal lands under it | `BookmarksVM.kt:154-162` |
| A reload after Details or the player returns changes (`loadBookmarks` or `loadFolderItems`) | Yes, if the user opens the menu before the network answers and the reload drops the card. `loadBookmarks` also resets to the first folder, which changes `rowKey` and resets the helper rather than publishing a neighbour | `BookmarksVM.kt:111-152`, `BookmarksScreenContent.kt:74` |
| `cardDisplayChanges` re-map | No: it re-maps `loadedItems` and never drops a card | `BookmarksVM.kt:48-64` |
| Switching folders | Not while the menu is open: switching needs a chip click | `BookmarksVM.kt:88-93` |
| A background or periodic refresh | None in this VM | `BookmarksVM.kt` (whole file) |

**Is the tab reachable?** `TabType.Bookmarks` has `enabled = false` (`domain/model/TabType.kt:20`). That flag only sets the default menu (`data/preferences/NavigationPreferencesRepository.kt:159`). A user can turn the tab on in device settings (`NavigationPreferencesRepository.kt:120-125`, `ui/feature/device/settings/vm/DeviceSettingsVM.kt:472`).

**Other screens that use the helper or have a context menu**

| Screen | Helper | Menu guard | Can the focused card leave while the menu is open? | Exposed |
|---|---|---|---|---|
| CollectionDetail | yes (`ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:59`) | `LocalContentFocusActive` is false while the menu is open (`:54-58`) | yes | no (fixed in 883e5a0d) |
| Favourites (tab on by default) | yes, through `VideoGrid` (`core/ui/uikit/component/moviesList/VideoGridUIState.kt:417-439`) | none (`ui/feature/favorites/content/FavoriteScreenContent.kt:64-95`) | yes: a forced reload after an unsave or a returned change (`ui/feature/favorites/vm/FavoriteVM.kt:101-104,121-129`), and a second emission from the cache-backed flow (`:41-50`) | yes, same mechanism (`unverified` on device) |
| Home rows | yes (`ui/feature/home/component/HomeScreenContent.kt:424-430`) | none. The screen-level menu state is at `:182,295-302` | yes: `silentRefresh` after a save toggle (`ui/feature/home/vm/HomeVM.kt:596-617`), on `OnResume` (`:190`), and after returned changes (`:234-238`) refetches the rows. `cardDisplayChanges` republishes the rows (`:166-174`) | yes in code, `unverified`. Here `rowHasFocusRef` is cleared only by `!contentFocusActive \|\| !isTargetRow` (`FocusTargetState.kt:76-78`), because the row has no `onFocusChanged` |
| ContentList rows (Movies, Series and other catalogue tabs) | yes (`ui/feature/contentlist/content/SectionRow.kt:124-128`) | none (`ui/feature/contentlist/content/ContentListScreenContent.kt:69,111-120`) | only if a refresh of the first page drops the card (`ui/feature/contentlist/vm/SectionVM.kt:53-82`). A save toggle does not remove catalogue items | low, `unverified` |
| History | no: it has its own focus | the card requests focus only when `menuUiKey == null` (`ui/feature/history/component/HistoryScreenContent.kt:330`, `ui/feature/history/component/HistoryCard.kt:49-55`) | n/a | no |
| Show all | no: `GridFocusMemory` | it requests only while the grid holds focus or an entry is pending (`ui/feature/showall/content/ShowAllScreenContent.kt:160,219-227`) | n/a | no |
| Search | no | n/a | n/a | no |

**Tests**

- There is no Bookmarks content test. `BookmarksScreenContent` is not referenced in `app/src/androidTest` or `app/src/test`.
- `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` (`app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt:60-96`) is the template to reuse. It opens the menu with a long Enter press, removes the focused card from the state, asserts that the first action keeps focus, then presses Back and expects the neighbour.

## 4. Related work

| Item | Relation |
|---|---|
| Run `grid-focus-restore-after-back` (done) | Source: D2 analysis and decisions D15–D17, open question 5 (`.flow/runs/grid-focus-restore-after-back/design-architect.md:151-191`) |
| Commit 883e5a0d | Added the CollectionDetail menu guard (`contextMenuItem == null`) |
| Run `collection-detail-inplace-refresh` (done), commit 18988650 | Changed `FocusTargetState.kt` (`holdPendingFocusTarget`, pending cleared when the card leaves). Its log: "Bookmarks focus NOT CHECKED (no test, tab off)" (`.flow/runs/collection-detail-inplace-refresh/state.md:59`) |
| Run `home-section-removal-focus-test-red` (idea) | Same helper. `HomeSectionRemovalFocusTest` already fails on master, so a Home change cannot rely on that test as a guard |
| Run `focus-request-helper` (idea) | Same area (one `tryRequestFocus()`), no overlap in behaviour |

DUPLICATE_OF: none.

## 5. External findings

External findings: off (local research only).

## 6. Options

| # | Option | Trade-offs | Size |
|---|---|---|---|
| A | Bookmarks only: in `BookmarksContent`, read `LocalContentFocusActive` and provide `outer && contextMenuItem == null` around the helper call and the grid, as `CollectionDetailScreenContent.kt:54-58` does. Add an instrumented test cloned from the CollectionDetail menu test | Smallest change and the same pattern as CollectionDetail. Fixes a tab that is off by default and leaves Favourites and Home exposed. Interaction with the existing `TvDialogFocusRestorer`: on close, the restorer's synchronous `restoreFocusedChild` and the helper's re-run entry request (`FocusTargetState.kt:258-263`) both aim at the same card, so the second is a no-op once `rowHasFocusRef` is true | S |
| B (recommended) | A, plus the same provider in `FavoriteScreenContentBody` around `VideoGrid` (`FavoriteScreenContent.kt:64-95`) and a Favourites menu test. Home goes to a separate idea run | Covers the default-on tab with the same exposure for 2 production files. Home stays out because its provider placement interacts with `ClaimHeroFocusEffect`, which reads `LocalContentFocusActive` (`HomeScreenContent.kt:192-202`), and its removal test is already red | S (4 files with tests) |
| C | Fix in the helper: park pending requests while a context menu is open, either with a new `LocalContextMenuOpen` local read in `FocusTargetState.kt:264-270`, or by having `TvDialogOverlay` publish its open state | One fix for every helper caller, including Home and ContentList. It touches a shared, often-revised file and every caller's behaviour, and needs a regression run on Home, SectionRow, VideoGrid, Bookmarks and CollectionDetail | M |

## 7. Risks

| Risk | Where |
|---|---|
| The trigger needs a list update inside the open-menu window: network latency after a removal or a returned change. A device repro needs a slow network or a forced state change, so on-device evidence may come only from the instrumented test | `BookmarksVM.kt:111-162` |
| A Bookmarks device check needs the tab turned on in device settings on the leased emulator. That is a persisted preference change on a shared device | `NavigationPreferencesRepository.kt:120-125` |
| The menu stays drawn over a card that is gone (`contextMenuItem` holds the removed item). Its actions would act on the removed item. This is a separate behaviour that the fix does not cover | `BookmarksScreenContent.kt:70,156-164` |
| Each menu open and close recomposes the subtree under the provider once, because `LocalContentFocusActive` is a static local | `FocusOnLaunchRequester.kt:30` |
| Separate defect, not focus: `setItemSaved` does not update `loadedItems`, so a later `cardDisplayChanges` re-map brings a removed card back (`unverified` on device) | `BookmarksVM.kt:58-64,154-162` |

## 8. Open questions

| # | Question | Recommended answer |
|---|---|---|
| Q1 | Scope: Bookmarks only (A), Bookmarks and Favourites (B), or a helper-level fix (C)? | B. Record Home, and ContentList at low priority, as an idea run |
| Q2 | Is the instrumented test enough without a device repro, given the tab is off by default and the trigger is a network race? | Yes: the instrumented menu test (state change while the menu is open) plus a device smoke check that opening and closing the menu still returns focus to the card |
| Q3 | Should a stale open menu (the card left under it) close by itself? | No, not in this run; record it as an idea |

## 9. Sources

- Files: `ui/feature/bookmarks/component/BookmarksScreenContent.kt`, `ui/feature/bookmarks/vm/BookmarksVM.kt`, `ui/feature/bookmarks/component/BookmarksScreen.kt`, `core/ui/uikit/component/moviesList/FocusTargetState.kt`, `core/ui/uikit/component/moviesList/FocusTargetResolver.kt`, `core/ui/uikit/component/TvContextMenu.kt`, `core/ui/uikit/component/TvDialogOverlay.kt`, `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt`, `ui/feature/home/component/HomeScreenContent.kt`, `ui/feature/home/vm/HomeVM.kt`, `core/ui/uikit/component/moviesList/VideoGridUIState.kt`, `ui/feature/favorites/content/FavoriteScreenContent.kt`, `ui/feature/favorites/vm/FavoriteVM.kt`, `ui/feature/contentlist/content/SectionRow.kt`, `ui/feature/contentlist/content/ContentListScreenContent.kt`, `ui/feature/contentlist/vm/SectionVM.kt`, `ui/feature/contentlist/vm/ContentListRefreshCoordinator.kt`, `ui/feature/history/component/HistoryScreenContent.kt`, `ui/feature/history/component/HistoryCard.kt`, `ui/feature/history/vm/HistoryVM.kt`, `domain/model/TabType.kt`, `data/preferences/NavigationPreferencesRepository.kt`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt`
- Commits: 883e5a0d, 18988650, 8b116d22
- Runs: `grid-focus-restore-after-back`, `collection-detail-inplace-refresh`, `home-section-removal-focus-test-red`, `focus-request-helper`
