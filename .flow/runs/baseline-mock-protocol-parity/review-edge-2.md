# Review edge, round 2

LENS: edge  ROUND: 2  BASE: 08f50b81
No blockers or majors; round 1 fixes hold (code read only).

### F1 [minor] Browse still depends on prefetch timing for a required route; the 1.5 s rest narrows the race but does not close it
- Where: BaselineProfileGenerator.kt:100-102 (NEIGHBOUR_DWELL_WAIT_MS = 1_500L); BaselineMockBackend.kt:189 (/v1/items/2 required = browse)
- Problem: only the DetailsPrefetcher neighbour warm requests id 2 (750 ms after focus), then waits on awaitSettled(), awaitForeground() and a shared permit; OK before hand-off cancels it.
- Failure scenario: a loaded emulator -> missing=[GET /v1/items/2] -> exit 2 without a code change (run 4 passed 11/11).
- Fix: bounded wait on backend.verify().matchedRoutes for GET /v1/items/2 (~5 s) before OK, or log the warm time.

### F2 [minor] The folder fixture test checks only that the list is non-empty, not the title the bookmark-icon fill depends on
- Where: BaselineFixtureContentTest.kt (bookmarkFoldersFixture_listsTheWatchLaterFolder_soTheLookupFillsTheBookmarkIcon)
- Fix: assert response.folders.any { it.title == WatchLaterBookmarkInteractor.FOLDER_TITLE }.

### F3 [nit] itemFixtures_carryWatchedAndBookmarkedMovie name no longer matches (bookmarked one is the series)
- Fix: rename, e.g. itemFixtures_carryWatchedListItemAndWatchlistedSeries.

Holds: chunking decided inside the dispatch lock; 404 never chunks; DoH builds its own response; no reference to empty_bookmark_folders; folder route in every scenario; Cyrillic title matches FOLDER_TITLE; DoH Date check on a fresh client; AC-8 holds.
