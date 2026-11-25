LENS: edge  ROUND: 2  BASE: f68f3524

### F1 [major] Raising the load-more threshold to 24 lets Show all stop paging when a short page arrives
- Where: ShowAllScreenContent.kt:51-52 (LOAD_MORE_THRESHOLD = LOAD_MORE_ROWS * SHOW_ALL_GRID_COLUMNS) and :107-119 (shouldLoadMore, LaunchedEffect(shouldLoadMore))
- Problem: LoadMore fires only on a false->true flip; a new page under 24 items with the user near the end keeps the flag true and paging stops. Short pages are real with hide-watched (ContentListInteractor.kt:150-165 fetchFilteredPage).
- Failure scenario: page 1 = 18 visible items, page 2 = 20; 17 > 38-25 stays true; the list stops at 38 though the server has more.
- Fix: LaunchedEffect(shouldLoadMore, state.items.size) { if (shouldLoadMore) onAction(CommonAction.LoadMore) }; notifyLoadNextPage already returns on isFullDataNext; add a content test: 18 items, append 20, a second LoadMore is sent.

Holds: menu restorer matches Favorites/Bookmarks; pill rounding uses Locale.ROOT; year/title guards tested.

### Unproven
- ShowAllScreenContent.kt:122 LocalContentFocusActive not gated while the menu is open (Favorites and Bookmarks gate it).
- VideoItemUIMapper.kt:197 a raw title of exactly " / " gives empty title and original.
