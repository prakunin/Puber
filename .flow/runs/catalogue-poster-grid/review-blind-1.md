LENS: blind  ROUND: 1  BASE: ad2c7f0c

### F1 [minor] The load-more threshold was not adjusted when the grid went from 3 to 6 columns
- Where: ShowAllScreenContent.kt:48 (LOAD_MORE_THRESHOLD = 12), used at the shouldLoadMore derivedStateOf
- Problem: the threshold counts items; at 3 columns 12 items = 4 rows of lead, at 6 columns only 2.
- Failure scenario: holding D-pad down on a slow TV box reaches the end of the grid and waits for the page.
- Fix: LOAD_MORE_THRESHOLD = LOAD_MORE_ROWS * SHOW_ALL_GRID_COLUMNS with LOAD_MORE_ROWS = 4, or keep 12 with a comment.

### F2 [nit] The "Russian locale" pill test cannot fail
- Where: VideoItemUIMapperTest.kt mapShortItem_pillRatingsUseDotDecimal_underRussianLocale
- Problem: buildPillRatings uses Int division and string templates; the test passes whatever the locale; it changes the process-wide default locale.
- Fix: drop it, or keep it with a comment that it guards a future switch to locale-aware formatting.

### Unproven
- buildPillRatings: KP and IMDB pill values are raw API strings, not rounded like PUB (e.g. 7.582).
- VideoItemPosterTest.videoItemPoster_longSelectOpensContextMenuWithoutClick relies on auto-repeat within 500 ms.
- splitItemTitle: "Рус / Orig / Alt" puts "Orig / Alt" in the original line.
