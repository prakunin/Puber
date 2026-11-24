LENS: edge  ROUND: 1  BASE: ad2c7f0c

No findings.

### Unproven
- ShowAllScreenContent.kt:48 load-more with 6 columns: LOAD_MORE_THRESHOLD = 12 is now 2 rows; LaunchedEffect(shouldLoadMore) fires only on a flip, so a small appended page could leave the flag true and stop paging. Real if a filtered or FreshSectionPager page can return fewer than ~12 items without being filled.
- VideoItemUIMapper.kt:194 splitItemTitle splits only on " / "; "Рус /Orig" keeps the raw title and an empty original.
- VideoItemUIMapper.kt:194 " / Orig" gives localTitle "" and the card falls back to the raw title plus the original line, showing Orig twice.
- VideoItemUIMapper.kt:58 year 0 would show "Orig · 0".
