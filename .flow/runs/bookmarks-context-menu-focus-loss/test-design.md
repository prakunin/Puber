# Test design: bookmarks-context-menu-focus-loss

Base 8b116d22. Levels: `instrumented` (via `make DEVICE=<serial> itest TESTS=<fqcn>`), `device`. No unit level: the defect is Compose focus, no VM or mapper changes.
Test dir: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/`.

## Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Neighbour card steals focus from the open menu (Bookmarks) | 2 | 3 | 6 | AC-1 test, level instrumented |
| Same, Favourites (default-on tab) | 2 | 3 | 6 | AC-3 test |
| Favourites has no `TvDialogFocusRestorer`: after the guard, menu close may focus another row's card (`isTargetRow` follows `focusedRowKey`) | 2 | 3 | 6 | AC-4 Favourites test with two rows; AC-5 Favourites test |
| Bookmarks: restorer fallback and helper entry effect both request focus on close | 2 | 2 | 4 | AC-4 Bookmarks test; remove path not automated (see Dropped) |
| Outer `LocalContentFocusActive=false` (rail open) stops gating the helper | 1 | 3 | 3 | AC-6 Bookmarks test; Favourites not automated (see Dropped) |
| Static local recomposes subtree per open/close | 2 | 1 | 2 | accepted |
| Race not reproducible on device (needs slow network) | 3 | 1 | 3 | accepted; instrumented state change is the evidence |

## Traceability

Entry paths are real: the menu is opened by a long Enter press on the focused card, closed by `KEYCODE_BACK` or by selecting its Remove action (AC-5; the test then drops the card from `items` / `gridState`) through instrumentation (as `CollectionDetailScreenContentFocusTest`). The list change is a Compose state swap of `items` / `gridState`, as in the reference test. No test requests focus on the menu or the neighbour by hand.

| AC | Level | Priority | Mark | Test | Proves |
|---|---|---|---|---|---|
| AC-1 | instrumented | P0 | red | `BookmarksScreenContentFocusTest.bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Fails if the `contextMenuItem == null` provider is removed: the neighbour card is focused and the first action is not |
| AC-2 | instrumented | P0 | guard | `BookmarksScreenContentFocusTest.bookmarksBackAfterItemChangeFocusesTheNeighbourCard` | Fails if close leaves focus on no card or the first card; passes on base because the neighbour already holds focus, so it guards the close path only |
| AC-3 | instrumented | P0 | red | `FavoriteScreenContentFocusTest.favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Fails if the Favourites provider is removed: a card is focused and the first action is not |
| AC-3 | instrumented | P1 | guard | `FavoriteScreenContentFocusTest.favoriteBackAfterItemChangeFocusesTheNeighbourCard` | Split from the test above; same mark as the AC-2 Bookmarks counterpart: the neighbour already holds focus on base, so it guards the close path only; fails if Back leaves no card or the first card focused |
| AC-4 | instrumented | P0 | guard | `BookmarksScreenContentFocusTest.bookmarksMenuOpenAndBackReturnsFocusToTheOpeningCard` | Fails if close focuses a different card or none (restorer vs. helper conflict) |
| AC-4 | instrumented | P0 | guard | `FavoriteScreenContentFocusTest.favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard` | Two rows, menu opened on a card in the non-first row; fails if close focuses the first row's card or none. Run on base first: if red there, record it and reclassify as red |
| AC-5 | instrumented | P1 | red | `FavoriteScreenContentFocusTest.favoriteRemoveFromMenuFocusesTheNeighbourCard` | Menu's own Remove closes the menu, then the card is dropped; fails if no card holds focus or focus goes to the first row instead of the neighbour. Red on base, green on HEAD (qa-report.md "Review fix round 1"). Known simplification (review-tests-2 F1): the test drops the card synchronously in the click, not after the menu closes |
| AC-6 | instrumented | P1 | guard | `BookmarksScreenContentFocusTest.bookmarksDoesNotTakeFocusWhileOuterContentFocusIsInactive` | With outer `LocalContentFocusActive=false`, content composes and no card takes focus; fails if the helper stops reading the outer value (`true` hard-coded) |
| AC-7 | instrumented/unit | P0 | guard | `CollectionDetailScreenContentFocusTest`, `VideoGridInitialFocusTest`, `./tools/agentw` unit + detekt + lint | Existing behaviour unchanged |

Bookmarks tests need `Bookmark` folders only for the chip row: use one folder (or none; `folders.size > 1` gates the chips) with `selectedFolderId` set. Favourites content: `FavoriteViewState.Content(gridState, selectedItem)` with `VideoDetailsUIState` built minimally; copy the `VideoGridUIState` construction from `VideoGridInitialFocusTest`.

The first menu action in the tests is found by its string as in the reference (`R.string.context_menu_watch_movie`); if `VideoItemUIState` defaults yield another first action for these tests, use that action's string.

## Dropped

Owner decision (state.md Decisions, last row). Both were red on base and on HEAD (qa-report.md "Review fix round 1"); deferred to idea runs.

| Test | AC | Deferred to |
|---|---|---|
| `BookmarksScreenContentFocusTest.bookmarksRemoveFromMenuFocusesTheNeighbourCard` | AC-5 Bookmarks | `bookmarks-remove-from-menu-focus` |
| `FavoriteScreenContentFocusTest.favoriteDoesNotTakeFocusWhileOuterContentFocusIsInactive` | AC-6 Favourites | `favourites-launch-focus-ignores-rail` |

No automated coverage in this run: AC-5 for Bookmarks, AC-6 for Favourites.

## Failing-first

`bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` and `favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`. Write them first, run each on the unchanged code and capture the failing assertion (action not focused / a card focused). Base check by reading: `git -C <wt> show 8b116d22:app/src/main/java/com/kino/puber/ui/feature/bookmarks/component/BookmarksScreenContent.kt` has no `LocalContentFocusActive` read; `FavoriteScreenContent.kt` neither.

## Manual checks (device, Favourites; Bookmarks tab stays off, no preference change)

1. Open Favourites, focus a card, long-press Select, press Back. Expect: menu closes, focus returns to the same card. Must not happen: focus on another card or on none, a crash.
2. Open the side rail over Favourites and close it. Expect: focus returns as on base. Must not happen: focus stuck in the rail or on no card.

## UNTESTABLE

None.
