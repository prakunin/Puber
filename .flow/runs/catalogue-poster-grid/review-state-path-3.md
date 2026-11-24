LENS: state-path  ROUND: 3  BASE: 3759abcf
### F1 [minor] Each page appended near the end sends one more LoadMore, so the grid always fetches one page ahead
- Where: ShowAllScreenContent.kt:107-121
- Problem: the effect restarts on items.size before the grid is measured and reads the old shouldLoadMore = true, sending one extra LoadMore; stops after one page.
- Fix: read the flag after measure (withFrameNanos + snapshotFlow), or accept one page of prefetch.

Checked, no break: duplicate sends while loading, refresh replacing the first page, hide-watched shrinks, emptied pages, focus.
