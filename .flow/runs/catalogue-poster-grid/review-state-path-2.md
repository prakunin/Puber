LENS: state-path  ROUND: 2  BASE: f68f3524
### F1 [major] Load more can stop for good: the 4-row threshold (24) is larger than a page, so one appended page no longer resets shouldLoadMore
- Where: ShowAllScreenContent.kt:51-52 (LOAD_MORE_THRESHOLD = 24) with the trigger at :107-119
- Problem: page size 20 (pagination.perpage); after a page the bound moves to oldTotal - 5; if lastVisible is above it, the flag stays true and no further LoadMore is sent. ContentListPagingVM does not resend for short pages.
- Failure scenario: 40 items, hold Down, LoadMore in flight while focus reaches index 39; page lands, total 60, 39 > 35 stays true; the grid ends at 59 with no spinner.
- Fix: LaunchedEffect(shouldLoadMore, state.items.size) { if (shouldLoadMore) onAction(CommonAction.LoadMore) } (notifyLoadNextPage checks isFullDataNext; paginator ignores LoadNext while loading), or keep the threshold below the page size; add a test.

Checked, no failure: the menu-close restorer (round 1 fix) holds on open, close, Back, page arrival and save toggle. LocalContentFocusActive gating absent but no path steals focus while the menu is open; adding it would only be for consistency.
