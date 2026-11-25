# Triage round 1: catalogue-poster-grid

Base ad2c7f0c, reviewed head f68f3524. Lenses: blind, edge, tests, state-path, ux-designer. codex: skipped, usage limit (owner: do not wait).

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | ux F1, blind unproven, qa obs 2 | major | KP / IMDb pills show raw API text ("5.978") | yes, device frame and buildPillRatings passes raw strings | fix | one decimal with dot, trailing ".0" dropped, pills only; mapper tests |
| 2 | state-path F1, tests F3, qa obs 1 | major | Closing the context menu in Show all sends focus to the rail | yes, no LocalTvDialogFocusRestorer in ShowAllScreenContent (also at base) | fix | predates the run, but the menu is an AC-9 surface and the fix pattern exists (FavoriteScreenContent.kt:70-96); add the menu-close focus test; M3 re-run |
| 3 | tests F1 | major | Ellipsis test cannot catch a cut year | yes, the year Text clips, isLineEllipsized always false | fix | assert !didOverflowWidth and width >= getLineRight(0); show red once with the weight on the year |
| 4 | tests F2 | major | P0 red tests without a red run | yes, plan record | fix | mark the four field-default unit tests guard in test-design (test-designer); record one red run per instrumented VideoItemPosterTest test against a temporary mutation (not committed) |
| 5 | tests F4 | minor | AC-12 M6 half done | yes | fix | qa round 2: run the five AC-12 guard classes via make itest, open Episode schedule and the player episode panel |
| 6 | blind F1, edge unproven | minor | LOAD_MORE_THRESHOLD = 12 is two rows at 6 columns | yes | fix | LOAD_MORE_ROWS = 4 times SHOW_ALL_GRID_COLUMNS |
| 7 | ux F2 | minor | Pill scrim 62 % too weak over bright posters | yes, measured | fix | PosterChipScrim 0.62 -> 0.78 (ux range 0.75-0.80, owner standing instruction: recommended); stand gets the same value and a scrim slider; README updated |
| 8 | edge unproven | minor | year 0 shows "· 0"; " / Orig" shows Orig twice | code path verified, data not seen | fix | cheap: year <= 0 as no year; empty local part uses the original as the title and no original line; unit tests |
| 9 | blind F2 | nit | Russian-locale test cannot fail | yes | fix | one-line comment: guards a future switch to locale-aware formatting |
| 10 | ux F3 | nit | Wider gap before " · year" after an ellipsis | measured | accept | listed for the owner |
| 11 | blind unproven | - | three-part titles "A / B / C" | not seen in data | reject | no such data seen; original line shows "B / C", which is still correct text |
| 12 | blind unproven | - | long-select test relies on auto-repeat in 500 ms | passed 7/7 on the box | reject | green on device; item 4's red run covers that it can fail |
| 13 | tests unproven | - | poster fallback walk untested | not seen failing | reject | VideoItemPosterUrlsTest covers the order; failures not observed |
