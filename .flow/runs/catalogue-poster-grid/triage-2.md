# Triage round 2: catalogue-poster-grid

Incremental f68f3524..3759abcf. Lenses: edge, tests, state-path. codex: skipped, usage limit (owner: do not wait). ux re-check after qa round 2 screenshots.

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | edge F1, state-path F1, tests F3 | major | 24-item load-more threshold with a flip-only LaunchedEffect stalls paging after a page under 24 items | yes, ShowAllScreenContent.kt:107-119 keys only on shouldLoadMore; regression from round-1 fix #6 | fix | key the effect on (shouldLoadMore, state.items.size); keep 4 rows; add a content test: flag still true after an append sends LoadMore again |
| 2 | tests F1 | minor | menu-close test not recorded green on head | qa round 2 runs it | fix | qa round 2 (already briefed) records it |
| 3 | tests F2 | minor | test-design AC-9 row names the wrong class and setup | yes | fix | test-designer updates the row |
| 4 | edge unproven, state-path note | - | LocalContentFocusActive not gated while the menu is open | traced: no path steals focus | accept | consistency only; listed for the owner |
| 5 | edge unproven | - | raw title exactly " / " | no data | reject | not seen in data |
