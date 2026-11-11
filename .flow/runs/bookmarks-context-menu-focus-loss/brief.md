# Brief: bookmarks-context-menu-focus-loss

Base 8b116d22. Analyst, 2026-11-11. Builds on `research.md` (cited as research.md: section). Layout follows `showall-rail-close-focus-shift/brief.md`.

kind: bug. size: S. tracks: none. Device verification: **yes** (TV focus, overlay, D-pad).

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.

## Request
Bookmarks: when the focused card leaves the list while its context menu is open, focus may move out of the menu onto the neighbour card. CollectionDetail had the same exposure (D2 in `grid-focus-restore-after-back`) and is fixed. Favourites, on by default, has the same exposure (research.md: 3, "Other screens").

## Root cause (plausible: traced in code, not reproduced on a device; research.md: 3)
1. The menu is an overlay in the screen's own composition (`core/ui/uikit/component/TvDialogOverlay.kt:46-54`). Its `focusGroup()` does not trap focus.
2. While the menu holds focus, the grid's `onFocusChanged` sets `rowHasFocusRef[0] = false` (`ui/feature/bookmarks/component/BookmarksScreenContent.kt:122-124`).
3. A new `items` list without the focused card makes `ReconcilePublishedItemsEffect` publish `PendingItemFocus(neighbour)` (`core/ui/uikit/component/moviesList/FocusTargetState.kt:221-237`).
4. The pending effect requests focus when `contentFocusActive` is true (`FocusTargetState.kt:264-270`). Bookmarks never lowers `LocalContentFocusActive` while its menu is open, so the request runs and the neighbour card takes focus from the menu. The menu stays drawn with no focused action.
5. Favourites has the same chain through `VideoGrid` (`core/ui/uikit/component/moviesList/VideoGridUIState.kt:417-424`) with an unguarded menu (`ui/feature/favorites/content/FavoriteScreenContent.kt:67,86,91-95`).

The trigger is a list update inside the open-menu window: a removal still in flight when the menu is reopened, or a reload after Details or the player returns (Bookmarks: `ui/feature/bookmarks/vm/BookmarksVM.kt:111-162`; Favourites: `ui/feature/favorites/vm/FavoriteVM.kt:41-50,101-104,121-129`). The menu's own "Remove" does not trigger it: the menu closes before the action runs (`core/ui/uikit/component/TvContextMenu.kt:116-119`).

## Scope
In: research option B (research.md: 6). Bookmarks and Favourites provide `LocalContentFocusActive = outer && contextMenuItem == null` around the helper, as `ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:54-58` does. One instrumented test per screen.
Out: see Deferred ideas.

## Acceptance criteria
- AC-1. Given Bookmarks with a card focused and its context menu open, When the state drops that card, Then the menu's first action keeps focus and no card is focused (instrumented test, red on base, green after).
- AC-2. Given AC-1's state, When Back closes the menu, Then the neighbour card at the same index is focused (instrumented test).
- AC-3. Given Favourites with a card focused and its context menu open, When the grid state drops that card, Then the first action keeps focus and no card is focused; after Back the neighbour card is focused (instrumented test, red on base, green after).
- AC-4. Given Bookmarks or Favourites with an unchanged list, When the menu is opened and closed with Back, Then focus returns to the card that opened it (instrumented test for both; device smoke on Favourites).
- AC-5. Given Bookmarks or Favourites with the menu open, When "Remove" is chosen, Then the menu closes, focus is on a card, and after the removal lands the neighbour card is focused (device on Favourites).
- AC-6. Given the side rail is open over either tab, When it closes, Then focus behaviour is unchanged from base (the outer `LocalContentFocusActive` value still gates the helper).
- AC-7. Existing tests stay green: `CollectionDetailScreenContentFocusTest`, `VideoGridInitialFocusTest`, unit tests, detekt, lint.

## Plan (size S: two production files plus tests)
1. `ui/feature/bookmarks/component/BookmarksScreenContent.kt`, `BookmarksContent` (`:65-167`): read `LocalContentFocusActive.current` and wrap from `rememberReconciledItemFocus` (`:73`) through the grid in `CompositionLocalProvider(LocalContentFocusActive provides (outer && contextMenuItem == null))`. Keep `VideoItemContextMenuDialog` (`:156`) and the `TvDialogFocusRestorer` (`:80-94`) as they are; on close, its fallback `itemFocus.focusRequester.requestFocus()` (`:90`) and the helper's re-run effect (`FocusTargetState.kt:258-263`) aim at the same card.
2. `ui/feature/favorites/content/FavoriteScreenContent.kt`, `FavoriteScreenContentBody` (`:62-96`): same provider around the `Column` that holds `VideoGrid` (`:68-90`), the menu outside it.
3. `app/src/androidTest/kotlin/com/kino/puber/ui/feature/bookmarks/component/BookmarksScreenContentFocusTest.kt` (new): AC-1, AC-2, AC-4, cloned from `CollectionDetailScreenContentFocusTest.kt:60-96`. Show it red on base first.
4. `app/src/androidTest/kotlin/com/kino/puber/ui/feature/favorites/content/FavoriteScreenContentFocusTest.kt` (new): AC-3, AC-4 with a `FavoriteViewState.Content` built from a single-row `VideoGridUIState`; `VideoGridInitialFocusTest.kt` shows how to build one.
5. Run instrumented tests through `make DEVICE=<serial> itest TESTS=<class>` under a lease. Device smoke (qa-device, install-preserve) for AC-4 and AC-5 on Favourites.

## Risks
- Risk area TV focus: by the role rule this is M. Sized S because it copies a fix already proven on CollectionDetail (883e5a0d) into two screens, with no change to the shared helper. The conductor may treat it as M.
- Favourites has no `TvDialogFocusRestorer`. With the guard, closing the menu flips `contentFocusActive` to true and the helper's entry effect (`FocusTargetState.kt:258-263`) requests the target row's card. AC-4 checks that this is the card that opened the menu and not a different row's card (`VideoGridUIState.kt:195,228`: `isTargetRow` follows `focusedRowKey`).
- `LocalContentFocusActive` is a static local: each open and close recomposes the subtree once (research.md: 7).
- A device repro of the race needs a slow network; evidence for AC-1 to AC-3 comes from the instrumented tests.

## Open questions
1. Scope: Bookmarks and Favourites (B), Bookmarks only (A), or a fix inside the helper (C, size M)? Recommended B. Blocks: yes.
2. Bookmarks is off by default (`domain/model/TabType.kt:20`). Turn it on in device settings on the leased emulator for a device smoke? Recommended no: instrumented tests for Bookmarks, device smoke on Favourites only, no preference change on a shared device. Blocks: no.

## Deferred ideas
| Idea | Where |
|---|---|
| Home rows and ContentList rows have the same unguarded menu over the helper; Home's provider interacts with `ClaimHeroFocusEffect` and `HomeSectionRemovalFocusTest` is already red | `ui/feature/home/component/HomeScreenContent.kt:182,192-202,295-302,424-430`; `ui/feature/contentlist/content/ContentListScreenContent.kt:69,111-120` |
| A menu left open over a card that has gone (or whose removal is in flight) should close by itself; its actions now act on the removed item | `BookmarksScreenContent.kt:70,156-164`; `BookmarksVM.kt:154-162` |
| A removed Bookmarks card comes back: `setItemSaved` does not update `loadedItems`, so a later `cardDisplayChanges` re-map restores it (`unverified` on device) | `BookmarksVM.kt:58-64,154-162` |
