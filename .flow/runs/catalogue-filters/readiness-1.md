# Readiness 1: catalogue-filters

Verdict: **NOT READY**

Inputs: spec and plan at `/home/dev/orca/workspaces/Puber/catalogue-filters` HEAD 87ff9632, `test-design.md` (mtime 2026-11-24 17:07), `state.md`.

## Checks

| Check | Result | Evidence |
|---|---|---|
| Every AC has a task and a Test design row | Pass | Plan `## Coverage`: AC-1..AC-13 each name tasks and rows; AC-13 is Manual 7 only (look, owner) |
| Cited `path:line` resolve | Pass (30 sampled, 0 wrong) | Table below |
| No task leaves the build red; order | Pass | Task 4 changes the `ContentListPagingVM` param, both subclasses (`SectionVM.kt:31-39` positional) and the only `ShowAllVM(` callers (`ShowAllScreen.kt:44`, `ShowAllVMTest.kt:338`) in one commit; existing `observeFirstPage(any(), any())` stubs (`SectionVMTest.kt:77`, `ShowAllVMTest.kt:327`) still match with `cacheable` defaulting to true; Task 6 needs Tasks 4-5, Task 7 needs 5-6; `ShowAllScreenContent` keeps a default `filter` so androidTest call sites compile |
| P0 rows: defect named, `red`/`guard`, automated test in backticks | Fail | Fix 2 |
| Plan, spec and test design agree | Fail | Fix 1 |
| Inside Out of scope | Pass | Plan Global constraints exclude items 2-4, `getCountries`, new `getItems` params, key/args change; no task touches them |
| Blocking open questions answered | Pass | `state.md` Q1-Q3 answered; architect Q1-Q2 and plan Q1 in Decisions |

## Citations sampled

| Citation | Claimed | Found |
|---|---|---|
| `KinoPubApiClient.kt:632-636` | `getGenres` decodes a bare list | yes |
| `KinoPubApiClient.kt:479-482` | `getBookmarks` unwraps `ApiResponseList` | yes |
| `KinoPubApiClient.kt:253-269` | `getItems` params type, sort, page, quality, genre, conditions | yes |
| `KinoPubApiClient.kt:131` | `ignoreUnknownKeys` | yes |
| `KinoPubApiClient.kt:641-643` | `getCountries` | yes |
| `Models.kt:11-15`, `:197-200` | `ApiResponseList`, `Genre(id, title)` | yes |
| `ContentFilter.kt:6-16`, `:18-26`, `:28-31` | data class, `SortField`, `SortDirection` (ASC = "") | yes |
| `ContentListPagingVM.kt:33` | `private val config` | yes |
| `ContentListPagingVM.kt:72` | `walkGeneration` | yes |
| `ContentListPagingVM.kt:111-114`, `:116`, `:165`, `:194`, `:216` | `resetPaging`, `onLoadFirstPage`, `publishFirstPage`, `restoreLoadedDepth`, its `loadPage(config, page)` | yes |
| `ContentListPagingVM.kt:249-257`, `:321-341` | `walkStep` / `publishWalkStep`, `resumeWalkAfterPause` (checks `:255`, `:337`) | yes |
| `PagingVM.kt:160-169` | `resetPaging` cancels and restarts | yes |
| `ContentListInteractor.kt:48-49`, `:56`, `:63-67`, `:107-123`, `:180-188` | `hideWatchedEnabled`, `observeFirstPage`, shortcut flow, `cacheKey`, `fetchPage` | yes |
| `TabTypeConfig.kt:12-25`, `:102-108`, `:145-150`, `:169-175`, `:194-200`, `:210-215` | `browseEntryFor`, `all_$type` (two), `all_cartoon`, `all_anime`, `all_4k` | yes |
| `GenreInteractor.kt:16-26`, `modules.kt:42` | `getGenres(type)` cached per type; `singleOf(::GenreInteractor)` | yes |
| `CacheKeys.kt:39`, `CacheTtl :64` | genres key per type, 5 min | yes |
| `ContentGenres.kt:4` | `ANIME_GENRE_ID = 25` | yes |
| `ShowAllScreen.kt:36`, `:43-53`, `:57-67` | key, VM factory, `Content` | yes |
| `ShowAllHeader.kt:15-27` | `HeaderPadding` top 12, non-focusable title | yes |
| `ShowAllScreenContent.kt:57-87`, `:68-73`, `:94`, `:103-111`, `:147-163`, `:267-274` | content, Empty branch, launch request, dialog restorer, grid items, `onHandoff` | yes |
| `FocusOnLaunchRequester.kt:40-48` | `ContentFocusGate` | yes |
| `TvDialogOverlay.kt` restorer, trap, `BackHandler` | `TvDialogFocusRestorer`, `LocalTvDialogFocusRestorer`, `TrapDirections` | yes |
| `TvContextMenu.kt:77-92` | delayed focus request loop | yes |
| `BrowseAllEntry.kt:30-31`, `:78-101` | `EntryHeight` / `EntryHorizontalPadding`, the `Button` | yes |
| `PlayerGlass.kt:26`, `:33`; `ResumeDialog.kt:112-114`; `PlayerInfoPanel.kt:125` | enum, modifier, both callers, KDoc link (grep finds no other user) | yes |
| `PlayerSidePanel.kt:80-86`, `:125`, `:223`, `:238` | `Panel*` constants, `IntrinsicSize.Max`, section header, item | yes |
| `ContentListVMTest.kt:605`, `TabTypeConfigTest.kt:15`, `ContentListInteractorTest.kt:71`, `:125`, `:142` | key guard test, test class, `FakePayloadStore`, `observedPages`, class | yes |
| `main_screen_strings.xml:43`, `values-en/strings.xml:188`, `paging-and-filters.md:128`, `Makefile:23` | `show_all_title_shelf` (both), the resetPaging line, `GRADLE ?= ./gradlew` | yes |

## Must fix

1. Sync the three copies of the test design. `test-design.md` now names `ShowAllFilterFocusTest.resetPressed_focusesFiltersThenFirstCardWhenListLands` (AC-6, P0 red, also fails if focus stays on «Фильтры» after `Loading` → `Content`), extends the AC-5 note for plan Q1, and changes Manual 4 Expected to "focus on «Фильтры» while the list loads, then on card 1". The spec's `## Test design` still has `resetPressed_movesFocusToFilters` and the old Manual 4, and the plan's Task 6 test table and `## Coverage` AC-6 row name the old test. The plan tells the implementer to read only the plan and the spec, and qa-device runs "Manual checks 1-7 of the spec's Test design", so the plan Q1 decision would not reach the code or the device check. Update the spec's Test design and plan Task 6 / Coverage to match `test-design.md`.
2. Two P0 rows name no automated test: `AC-5 | device | P0 red | Manual 3` and `AC-11 | device | P0 red | Manual 5` (in `test-design.md` and the spec). Either make them P1 device rows, or name the automated test that carries the P0 (for AC-11 the only automated restore test, `ShowAllScreenContentFocusTest.activeFilter_focusReturnsToTheFocusedCardAfterReentry`, is P1) and mark that row `red` / `guard` with the defect it catches.

## Notes (not blocking)

- Plan Task 7 builds each panel page as `Column` + `verticalScroll` (reason: `PlayerSidePanel.kt:125` `IntrinsicSize.Max`); the spec's Architect changes table and Risks row still say one keyed `LazyColumn`. Align the spec when fixing 1 so review does not flag it as drift.
- Task 5 moves `PlayerGlass` and refactors `PlayerSidePanel.kt`; the player-overlays-port worktree edits the same player overlays. Expect a merge conflict for whichever lands second.
