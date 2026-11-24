# Gate - catalogue-poster-grid - round 1

**Decision:** CONCERNS
**Head:** f41bd66f   **Base:** ad2c7f0c

## Done as asked

The diff does what the spec asks: new `VideoItemPoster` (2:3 `posters.medium`, HD / 4K badge, PUB / KP / IMDb pills, local title, original line with a year that never ellipsizes), Show all in 6 columns (`ShowAllScreenContent.kt`), mapper fields defaulted so other cards keep their look. Outside the spec, from review fixes: a `TvDialogFocusRestorer` on Show all (triage-1 #2), load more 4 rows ahead keyed on `items.size` (triage-1 #6, triage-2 #1). Nothing excluded is in: R-check at head gives an empty `Models.kt` / `ContentCacheRepository.kt` diff, no `Color(` in `VideoItemPoster.kt`, one `4CAF50` val in `Color.kt:32`, no `hasAc3` / `hasSubtitles` / `"CC"` / `"AC3"`.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | M1, owner approval | stand committed de9217e4 before the port (5e4f7185); owner quote in spec Decisions; qa NOT CHECKED carried over | NOT CHECKED (manual, owner) |
| AC-2 | `mapShortItem_qualityBadge*` (5) | check.full green at f41bd66f; red runs in plan Implementation record | PASS |
| AC-3 | four mapper tests, `mapShortItem_yearEmpty_whenYearIsZero`, `videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole` and two separate-text tests | unit green; VideoItemPosterTest 7/7 on the box, mutation red runs recorded; `long-original-ellipsis-year-whole.png`, `r2-menu-back-focus.png` ("Snowbound for the Holida… · 2026") | PASS |
| AC-4 | split / keepsRawTitle / usesOriginalAsLocalTitle tests | unit green | PASS |
| AC-5 | `mapShortItem_pillRatingsPubWithoutTrailingZero`, rounding tests, `buildRatings_keepsPubDecimalText` | unit green; qa R2-1 pills at most one decimal | PASS |
| AC-6 | R-check | re-run at head, see Done as asked | PASS |
| AC-7 | M1, M5, R2-2 | qa round 1 and 2 screenshots; ux-designer-2 "VISUAL: matches"; owner look in Manual checks | PASS (owner look pending) |
| AC-8 | `VideoItemPosterUrlsTest` (3) | unit green | PASS |
| AC-9 | VideoItemPosterTest long-select / select tests, `showAllMenuOpenAndBackReturnsFocusToTheOpeningCard`, R2-3..R2-5 | box itest green, red runs recorded; `r2-menu-open.png`, `r2-menu-back-focus.png` sampled: card framed, rail collapsed | PASS |
| AC-10 | `ShowAllScreenContentFocusTest`, M4 | 6/6 on the box (qa on 3759abcf plus the uncommitted load-more test; implementer 6/6 on 210540e6) | PASS |
| AC-11 | `ShowAllRailCloseFocusTest` | 6/6 on the box at 3759abcf | PASS |
| AC-12 | guard itest classes, R2-6..R2-8, `mapHeroItems_*` | 29 guard tests green alone on the box; Episode schedule cards not visible (worktree build has no TMDB token) | PASS; Episode schedule cards NOT CHECKED (manual, needs TMDB token) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| KP / IMDb pills unrounded | ux-1 F1, triage-1 #1 | fixed | 507cc91c; qa R2-1 |
| Menu close sends focus to the rail | state-path-1 F1, tests-1 F3, triage-1 #2 | fixed | 0389f9f0, 9b05a36a; red run recorded; R2-3..R2-5 |
| Ellipsis test cannot catch a cut year | tests-1 F1, triage-1 #3 | fixed | 7eebd3b3; red with weight on the year (plan record) |
| P0 red tests without red run | tests-1 F2, triage-1 #4 | fixed | test-design marks four tests `guard`; instrumented red runs in plan record (9b05a36a) |
| AC-12 M6 half done | tests-1 F4, triage-1 #5 | fixed | qa round 2 guard classes, R2-6..R2-8 |
| Load-more threshold 2 rows at 6 columns | blind-1 F1, triage-1 #6 | fixed | 0389f9f0 |
| Scrim 62 % too weak | ux-1 F2, triage-1 #7 | fixed | d09ae058; ux-designer-2 matches |
| Year 0, " / Orig" | edge-1 unproven, triage-1 #8 | fixed | 507cc91c |
| Russian-locale test cannot fail | blind-1 F2, triage-1 #9 | fixed | comment at `VideoItemUIMapperTest.kt:416` |
| Wider gap before " · year" after an ellipsis | ux-1 F3, triage-1 #10 | accepted (nit) | visible in `r2-menu-back-focus.png`, cosmetic |
| Three-part titles, auto-repeat reliance, fallback walk untested | blind-1 / tests-1 unproven, triage-1 #11-#13 | rejected | spot-checked #11 (text stays correct) and #12 (mutation red run proves the test can fail): reasons accepted |
| Load more stalls after a short page | edge-2 F1, state-path-2 F1, triage-2 #1 | fixed | 210540e6; red run recorded |
| Menu-close test green on head; test-design row names | tests-2 F1, F2, triage-2 #2, #3 | fixed | qa round 2; test-design AC-9 row |
| `LocalContentFocusActive` not gated while the menu is open | edge-2, state-path-2, triage-2 #4 | accepted | traced, no focus steal; consistency only |
| Raw title exactly " / " | edge-2, triage-2 #5 | rejected | not in data; result is an empty title line, no crash |
| One extra LoadMore per trigger | state-path-3 F1, triage-3 #1 | accepted (minor) | one page prefetch, list state correct |
| Test-design AC-7 row, evidence sha vs test count | tests-3, triage-3 #2, #3 | fixed / accepted | test-design row names `ShowAllScreenContentFocusTest` 12 -> 24 |
| Codex review | review-codex-1 | skipped every round | usage limit; owner standing instruction |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | `posters.medium` replaces the wide poster in the grid; image request and URL list remembered per item; `mapShortItem` now builds split titles and pill ratings for every screen that uses it (small allocation, no hot loop); one extra page prefetched near the end (accepted) |
| Reliability | Empty states: no badge, no pills, no original line keep the reserved height; year 0 and empty local part handled. A combined itest run crashed the app process inside `ShowAllScreenContentFocusTest` (qa round 2 observation 1), no crash text read, every class green alone afterwards |
| Maintainability | AGENTS.md conventions held (pure content, TV Material3 `Card`, strings in `strings.xml`, colours named in `Color.kt`); `PosterImage` repeats the fallback walk of `CardPoster` (planned); no dead code: `VideoItemHorizontal` still has nine callers |
| Security / privacy | No secrets in code or evidence; `tools/mobile-evidence-audit.sh` on the evidence dir: passed, 102 files (re-run here). The stand `docs/design/showall/index*.html` embeds titles and real unwatched-episode counters taken from the account's app-data snapshot (README "Content"), so it shows which series the account follows |
| TV | One focus target per tile, frame 2 dp, scale 1.05, first row not clipped (`r2-grid-focused-first.png`); Back from the menu returns to the opening card |

check.full: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` at f41bd66f, clean tree: BUILD SUCCESSFUL (run here, qa-report older than the last commit).

## Manual checks for the owner

| # | Item | Steps |
|---|------|-------|
| 1 | AC-1 stand approval | Open `docs/design/showall/index.html` in the worktree; confirm the "Agreed 2026-11-24" paragraph of `README.md` is what you approved |
| 2 | AC-7 look | Open `docs/superpowers/evidence/2026-11-24-catalogue-poster-grid-screens/r2-grid-focused-first.png` and `r2-grid-focused-row2-col3.png`; compare with the stand; judge the pill over busy poster lettering ("Стажер") and the gap before " · year" after an ellipsis |
| 3 | AC-12 Episode schedule cards | After merge, on a main-checkout build with the TMDB token on 192.168.1.110:5555: a serial's details, calendar button; cards look as before (no badges, pills or split titles) |

## Must fix

none

## Concerns for the owner

1. Head production code (210540e6, load-more key) was never installed on the box by qa: device evidence is for 3759abcf; the load-more path has only the implementer's instrumented red / green run, and `ShowAllRailCloseFocusTest` was not re-run after it. A paging walk to the end of a long Show all list on the head APK would close this.
2. Unattributed app-process crash during a combined itest run (qa round 2 observation 1); worth a look if it recurs.
3. Accepted minors: one-page prefetch (triage-3 #1), `LocalContentFocusActive` not gated while the menu is open (triage-2 #4), wider gap before " · year" (triage-1 #10).
4. The stand files carry account-derived viewing data (followed series with unwatched counters); keep or anonymise before merge.
5. Untriaged pre-existing qa observations: first entry into Show all focuses the expanded rail; Back from the expanded rail once left the app (round 1 observations 1 and 5). Candidates for `/task:new`.
