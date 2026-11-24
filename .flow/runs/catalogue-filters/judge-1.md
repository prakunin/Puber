# Gate - catalogue-filters - round 1

**Decision:** CONCERNS
**Head:** 184e0163   **Base:** b7a7d99e

## Done as asked

The diff (`git diff b7a7d99e...184e0163`) delivers item 1 of `brief.md#Split`: «Фильтры» / «Сброс» / summary in `ShowAllHeader.kt`, side panel with Sort (five fields, both directions) and Genre in `ShowAllFilterPanel.kt`, apply on close (`ShowAllVM.closeFilterPanel`), `changeQuery` with query generation and `publishLock` in `ContentListPagingVM.kt`, uncached filtered first pages (`ContentListInteractor.observeFirstPage(cacheable)`), genres `{status, items}` unwrap (`KinoPubApiClient.kt:632-637`), filter only on the seven browse configs (`TabTypeConfig.isFilterable`; `all_cartoon` / `all_anime` excluded per architect Q1), screen key unchanged. Nothing from items 2-4 (country, year, status, persistence, ratings, quality) is in the diff. Extra, inside the plan: `TvCompactButton` and `TvSidePanel*` lifted to uikit (player panel now delegates), `PlayerGlass` renamed `PanelGlass`, `FollowPreference` anime fix in `isVisible` (triage-1 #1).

## Traceability

Named tests: all 55 test names in `test-design.md` exist under `app/src/`. check.full re-run by the judge at 184e0163: BUILD SUCCESSFUL (`testDevDebugUnitTest` executed, detekt and lint up to date). Instrumented XML at head: `evidence/2026-11-24-catalogue-filters-itest-r2/` 14/9/8, 0 failures.

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `ShowAllFilterFocusTest`, Manual 1 | itest-r2; device r2 Manual 1 (entry and tile, Movies) | PASS |
| AC-2 | `TabTypeConfigTest.isFilterable_*`, `ShowAllVMTest.filterState_isUnavailableForAShelfConfig`, `ShowAllFilterFocusTest.unavailableFilter_showsNoFiltersButton`; device: shelf grid without the button | unit and itest green; no shelf grid opened on the box in either round (qa r1 «PASS» cites only browse grids) | NOT VERIFIED (device leg) |
| AC-3 | `ShowAllRailCloseFocusTest`, Manual 2 | itest-r2; r2 shots 20-25 (Up/Down col 1 and 6, Left opens rail, Right closes onto «Фильтры») | PASS |
| AC-4 | `ShowAllVMTest.filterPanelClosed_withoutAChange_doesNotRepage`, Manual 3 | r2 shots 30-31 | PASS |
| AC-5 | `ContentListInteractorTest`, `ShowAllVMTest`, `ShowAllFilterFocusTest.newListLanding_*`, Manual 3 | r2 shots 32-40 (1903, 1906 ... dramas, card 1 framed) | PASS |
| AC-6 | `ShowAllVMTest.filterReset_*`, `ShowAllFilterFocusTest.reset*`, Manual 4 | r2 shots 41-44; Series reset described, frames deleted | PASS |
| AC-7 | `ContentListInteractorTest.observeFirstPage_notCacheable_*`, `..._afterAFilteredRead_*` | unit, check.full green | PASS |
| AC-8 | `ShowAllVMTest.filterChange_*` | unit, check.full green | PASS |
| AC-9 | `ShowAllVMTest.genreList_withAnimeHidden_omitsAnime` | unit, check.full green | PASS |
| AC-10 | `KinoPubApiClientGenresTest`, `ShowAllVMTest.genre*`, `ShowAllFilterFocusTest.genresFailed_*` | unit and itest; device genre lists on Movies, Series, 4K | PASS |
| AC-11 | `ContentListVMTest.browseAllAndAllShelfShowAll_*`, `ShowAllScreenContentFocusTest.activeFilter_*`, Manual 5 | itest-r2; r2 shots 70, 72 (details leg) | PASS; player leg NOT CHECKED (manual 2 below) |
| AC-12 | `ShowAllVMTest.filteredEmptyPage_keepsTheFilterActive`, `ShowAllFilterFocusTest.filteredEmpty_*`, Manual 6 | r2 shots 66-67, reset restores Series grid | PASS |
| AC-13 | Manual 7 | shots listed in the device evidence | NOT CHECKED (owner, manual 1 below) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| T1 #1 FollowPreference drops anime in walks | codex | fixed | 3d913e2e; `ContentListInteractor.kt:190` |
| T1 #2 rail from «Фильтры» closes onto a card | state-path | fixed | c0afd1af (`headerHoldsFocus`); r2 shot 25 |
| T1 #3 old page published after `changeQuery` | edge, codex, tests | fixed | 68abf4dc (`publishLock` around change and publish) |
| T1 #4 cached reset skips Loading | state-path | fixed | c0afd1af (`key(state.queryGeneration)`) |
| T1 #5 refresh restores old depth | state-path | fixed | 68abf4dc (`changeQuery` zeroes depth counters) |
| T1 #6, #7 P0 tests could not fail | tests | fixed | 426b5e99; test-design rows re-marked guard with carriers |
| T1 #8 manual routes not run | tests | fixed | a2f07358 (r2 Manual 1 tile, Manual 6 Series) |
| T1 #9 panel glass shows poster text | ux-designer | accepted minor | owner choice under AC-13 |
| T1 #10-#12 unproven | edge, blind | rejected | spot-checked #10 and #12: #10 needs show-anime switched off while the grid stays open, and the walk ends in the filtered-empty state; #12 is closed by `publishLock` and the captured generation |
| T1 #13 4K reset lands in a stored-page error | qa, codex | deferred | run `cached-payload-row-too-big` |
| T2 #1 no guard for the rail fix | tests | fixed | 184e0163; Manual 2 extended, run in r2 |
| T2 #2 spec test table stale | tests | fixed | 184e0163 |
| T2 #3 query generation read at render time | codex, edge | accepted minor | one stale frame, no stale final data |
| T2 #4 pre-fix stored first pages without anime | edge | accepted minor | only show-anime + hide-watched users, until TTL or refresh |
| QA r2 Back with the rail open pops Show all | qa | deferred | run `showall-rail-back-exits-app` (created from catalogue-poster-grid; reproduces from a card) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Filtered pages skip the payload store (no per-combination rows); one re-page per panel close; genres cached 5 min per type. No hot-path concern found |
| Reliability | Error row and Retry for genres, filtered-empty state with «Сброс», generation guard for in-flight reads. Filter is lost on process death (by design, item 3). Unfiltered 4K stays broken by the deferred stored-page error |
| Maintainability | Player panel code moved verbatim to `core/ui/uikit/component/panel/`; `PlayerPanelItem` / `PlayerPanelSectionHeader` remain as one-line delegates. Conventions in `AGENTS.md` kept (pure content, Koin `get()`, strings in `main_screen_strings.xml` and `values-en`) |
| Security / privacy | No secrets in the diff; `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` passed (355 files). Accidental playback on the box (about 3 s, evidence round 2 header) may have written watch progress to the owner's account |

## Manual checks for the owner

1. AC-13 look: open «Все фильмы», «Фильтры», both panel pages, an active filter and a filtered-empty grid on the TV box; compare with `docs/design/showall/` scene «catalogue-filters»; decide the panel material (glass with poster lettering showing through, shots 13, 07, 09) or a plain surface.
2. AC-11 player leg: with «Драма» applied on «Все сериалы», play a card, Back to the grid; expect the same summary and the same card focused.
3. Player side panel after the lift: in the player open the settings panel; expect the same size, glass and rows as before.

## Must fix

none

## Concerns for the owner

1. AC-2 device leg NOT VERIFIED: no shelf grid (e.g. Movies «Популярное» → «Показать все», or the `4k_movie` shelf) was opened on the box to see the header without «Фильтры». The automated chain config → VM → header is green.
2. Possible watch-progress write from the accidental playback on the box during qa; check the played title in the stage app's history.
3. Accepted minors T2 #3 and T2 #4 stay in the code (see Findings).
