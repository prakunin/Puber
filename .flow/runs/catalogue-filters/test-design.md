# Test design: catalogue-filters

Base b7a7d99e. ACs: `brief.md#Acceptance criteria`; decisions: `design-architect.md`. Levels from `.flow/roles/test-designer.md`: `unit` (JUnit 5, `MainDispatcherExtension`, virtual time), `instrumented` (`make DEVICE=<serial> itest TESTS=<fqcn>`), `device` (TV box `192.168.1.110:5555`, evidence file). Test paths: `app/src/test/kotlin/com/kino/puber/` and `app/src/androidTest/kotlin/com/kino/puber/`.

AC-5 includes the state.md decision (architect Q2): when the new list lands, focus goes to its first card. The `design-ux.md` Focus row "after a close that changed the query: focus stays on «Фильтры»" is superseded by it, also after «Сброс» (plan Q1): focus is on «Фильтры» while the list loads, then on card 1.

## Risk profile

| # | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | `getGenres` decodes a bare list; the server sends `{status, items}` (api-check row 1), so every genre load fails | 3 | 3 | 9 | AC-10 unit, red |
| R2 | A first page, depth restore or walk step of the old query publishes after `changeQuery` (no guard on the first-page collect, `ContentListPagingVM.kt:120`) | 2 | 3 | 6 | AC-8 unit |
| R3 | A filtered first page is read from or written to the unfiltered `cached_payload` entry | 2 | 3 | 6 | AC-7 unit |
| R4 | Sort suffix other than `-` / empty (e.g. `+`) is ignored by the server and looks like a working sort | 2 | 3 | 6 | AC-5 unit on `applyTo` |
| R5 | Cold open lands on «Фильтры» instead of the first card | 2 | 3 | 6 | AC-1 instrumented + device |
| R6 | Up from row-1 columns 3-6 lands on «Сброс» or nowhere; Down from the button lands off row 1 | 2 | 3 | 6 | AC-3 instrumented |
| R7 | Panel close does not restore «Фильтры»; a list landing behind the open panel pulls focus out of the trap | 2 | 3 | 6 | AC-4, AC-5 instrumented |
| R8 | «Сброс» disappears while focused and focus goes nowhere | 2 | 3 | 6 | AC-6 instrumented |
| R9 | Panel close without a change re-pages (list flash, cache bypass) | 2 | 2 | 4 | AC-4 unit |
| R10 | «Аниме» offered with show-anime off; picking it walks every page empty | 2 | 2 | 4 | AC-9 unit |
| R11 | `all_4k` asks `genres` without type and gets the 115-item mixed list | 2 | 2 | 4 | AC-10 unit |
| R12 | Filter lost, or a different card focused, after details / player and Back | 2 | 2 | 4 | AC-11 instrumented + device |
| R13 | Filter leaks into the screen key and splits Voyager state / DI scope | 1 | 3 | 3 | AC-11 guard unit |
| R14 | A browse config's base query drifts from `ContentFilter()` (`updated-`, no genre), so "no filter" sends a different query | 1 | 3 | 3 | AC-5 guard unit |
| R15 | Button shown on shelf / `4k_<type>` / `all_cartoon` / `all_anime` grids | 1 | 2 | 2 | AC-2 unit |
| R16 | Each draft change recomposes `ShowAllContentBody` | 2 | 1 | 2 | Accepted: no perf track; ux visual review on the box |
| R17 | Rail closed after Left from «Фильтры» restores the remembered card, not the button | 2 | 1 | 2 | Accepted: designed behaviour (`design-architect.md` Risks) |
| R18 | Header button and panel missing from the baseline profile until regeneration | 3 | 1 | 3 | No test: next `tools/generate-baseline-profile.sh` run |
| R19 | Process-death restore reopens the grid unfiltered | 1 | 1 | 1 | Accepted: Q2 scope (screen instance) |

## Traceability

New test classes: `data/api/KinoPubApiClientGenresTest.kt` (reuses the `HttpServer` `withServer` / `client` pattern of `KinoPubApiClientFailureTest`), `ui/feature/showall/model/ContentFilterQueryTest.kt`, androidTest `ui/feature/showall/content/ShowAllFilterFocusTest.kt` (pattern of `ShowAllScreenContentFocusTest`: `createComposeRule`, `PuberTheme`, `LocalContentFocusActive`, `performKeyInput`). `ShowAllVMTest` gains a mocked `GenreInteractor` in `createVM`; `ContentListInteractor` is mocked there as today.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 red | `ShowAllFilterFocusTest.coldOpen_focusesTheFirstCardNotFilters` | Fails if the `canFocus` latch is removed and «Фильтры» takes launch focus while the list is `Loading` |
| AC-1 | instrumented | P0 guard | `ShowAllScreenContentFocusTest` (whole class) | Fails if the new header or `ContentFocusGate` breaks first focus, re-entry or load-more on the grid |
| AC-1 | device | P1 | Manual 1 | Real launch path from the entry and the «Все» tile |
| AC-2 | unit | P0 red | `TabTypeConfigTest.isFilterable_trueForEveryBrowseEntryConfig` | Fails if a tab's `browseEntryFor` config is not filterable |
| AC-2 | unit | P0 guard | `TabTypeConfigTest.isFilterable_falseForShelvesAnd4kTypeGrids` | Fails if fresh / hot / popular / `4k_<type>` / `popular_4k` / `all_cartoon` / `all_anime` get the button (guard: the stub already returns false for every config, so it is green on base; protects the negative cases once `isFilterable` turns true for browse configs) |
| AC-2 | unit | P1 | `ShowAllVMTest.filterState_isUnavailableForAShelfConfig` | Fails if `ShowAllVM` reports `available` for a shelf config |
| AC-2 | unit | P1 | `ShowAllVMTest.filterActions_areIgnoredOnAShelfGrid` | Fails if a filter action on a shelf grid calls `observeFirstPage` again |
| AC-2 | instrumented | P1 | `ShowAllFilterFocusTest.unavailableFilter_showsNoFiltersButton` | Fails if the header draws «Фильтры» for `ShowAllFilterState.Unavailable` |
| AC-3 | instrumented | P0 red | `ShowAllFilterFocusTest.upFromFirstAndLastRowOneCard_landsOnFilters` | Fails if the `focusProperties { up }` pin is missing (last column lands on «Сброс» or nowhere) |
| AC-3 | instrumented | P0 red | `ShowAllFilterFocusTest.downFromFilters_returnsToTheRowOneCardItCameFrom` | Fails if Down lands on a card outside row 1 or on nothing |
| AC-3 | instrumented | P1 | `ShowAllRailCloseFocusTest.leftFromFilters_opensTheRail` | Fails if something focusable sits left of «Фильтры» or Left is consumed |
| AC-3 | instrumented + device | P0 guard | `ShowAllRailCloseFocusTest.railOpenedFromFilters_closesWithFocusBackOnFilters`; carrier Manual 2 (rail close) | Open the rail from «Фильтры», close it with Right and with Back: fails if focus lands on a card or nowhere instead of «Фильтры», i.e. if the `headerHoldsFocus` rail fix is reverted. The instrumented test is green on base (the compose host settles the handoff and cannot be made red), so the real stack is gated by Manual 2 |
| AC-3 | device | P1 | Manual 2 | Same paths with the real rail and remote |
| AC-4 | unit | P0 guard | `ShowAllVMTest.filterPanelClosed_withoutAChange_doesNotRepage` | Fails if close calls `changeQuery` when `draft == applied` (counts `observeFirstPage` calls; also after choose-then-revert) (guard: nothing re-pages on base, so it is green there; protects the no-op close once `changeQuery` exists) |
| AC-4 | instrumented | P0 red | `ShowAllFilterFocusTest.filtersOk_opensThePanelOnItsFirstRow` | Fails if the panel opens without focus or focus stays on the grid |
| AC-4 | instrumented | P0 red | `ShowAllFilterFocusTest.panelBack_closesWithFocusOnFilters` | Fails if the header restorer is missing and focus goes to a card or nowhere |
| AC-4 | instrumented | P1 | `ShowAllFilterFocusTest.panelOpen_dpadLeftAndRightStayInsideThePanel` | Fails if the panel is not inside `TvDialogOverlay` |
| AC-4 | instrumented | P1 | `ShowAllFilterFocusTest.subPageBack_returnsToTheDoorItCameFrom` | Fails if Back on «Сортировка» / «Жанр» closes the panel or loses the door focus |
| AC-5 | unit | P0 red | `ContentFilterQueryTest.applyTo_ascending_sendsTheBareFieldName` | Fails if ASC produces `year+` (or any suffix) instead of `year` |
| AC-5 | unit | P0 red | `ContentFilterQueryTest.applyTo_descendingWithGenre_keepsIdTypeAndQuality` | Fails if `applyTo` drops `id`, `type`, `quality` or `animeFilterMode`, or writes the genre other than as its id |
| AC-5 | unit | P0 guard | `ContentFilterQueryTest.applyTo_defaultFilter_returnsTheBaseConfig` | Fails if the default filter changes the query or `isActive` is true for `ContentFilter()` (guard: the stub `applyTo` returns the base config, green on base; protects the default path) |
| AC-5 | unit | P0 guard | `TabTypeConfigTest.browseEntryConfigs_haveTheDefaultFilterQuery` | Fails if a browse config's `sort` stops being `updated-` or gains a `genre` |
| AC-5 | unit | P0 red | `ShowAllVMTest.filterPanelClosed_afterSortAndGenreChosen_repagesOnceWithTheFilteredQuery` | Fails if close re-pages zero or two times, or passes the base config (captures the `SectionConfig` given to `observeFirstPage`) |
| AC-5 | unit | P0 guard | `ContentListInteractorTest.filteredBrowseConfig_sendsSortAndGenreWithTheBaseType` | Fails if `fetchPage` stops forwarding `sort` / `genre` with `type` to `getItems` (mocked `KinoPubApiClient`) |
| AC-5 | unit | P1 guard | `ContentListInteractorTest.filtered4kConfig_sendsSortAndGenreWithTheBaseQuality` | Same for `all_4k` (`quality = "4k"`, no type) |
| AC-5 | instrumented | P0 guard | `ShowAllFilterFocusTest.newListLanding_whilePanelOpen_keepsFocusInThePanel` | Cannot be made red in the compose host (not even with `LocalScreenKey`); real stack gated by Manual 3. Fails if `ContentFocusGate` is removed and the remounted grid's launch request takes focus |
| AC-5 | instrumented | P0 red | `ShowAllFilterFocusTest.newListLanding_afterApply_focusesTheFirstCard` | Fails if focus stays on «Фильтры» or is lost when `Loading` turns to `Content` after close |
| AC-5 | device | P1 | Manual 3 | Order and genre visibly change on the box (automated carriers: the P0 rows above) |
| AC-6 | unit | P0 red | `ShowAllVMTest.filterReset_restoresTheBaseQueryAndRepagesCached` | Fails if reset leaves the filtered config or reads with `cacheable = false` |
| AC-6 | unit | P1 guard | `ShowAllVMTest.filterChange_marksTheNewListWithAnotherQueryGeneration` | Fails if a filter change re-pages without a new query generation, so the old list's focus handoff or stale page could survive |
| AC-6 | unit | P1 red | `ShowAllVMTest.filterState_summarisesTheAppliedGenreAndSort` | Fails if `genreTitle` / `sort` / `direction` / `isActive` show the draft instead of the applied filter |
| AC-6 | instrumented | P0 red | `ShowAllFilterFocusTest.resetPressed_focusesFiltersThenFirstCardWhenListLands` | Fails if «Сброс» leaves while focused and nothing is focused, or if focus stays on «Фильтры» after `Loading` turns to `Content` |
| AC-6 | instrumented | P1 red | `ShowAllFilterFocusTest.resetWithACachedBasePage_movesFocusFromFiltersToTheFirstCard` | Reset answered at once from the cached base page (no `Loading` step): fails if focus stays on «Фильтры» or is lost instead of the first card. Red on the box with the field stubbed (recorded in the itest run), not in the compose host |
| AC-6 | device | P1 | Manual 4 | Summary text and «Сброс» on the box |
| AC-7 | unit | P0 red | `ContentListInteractorTest.observeFirstPage_notCacheable_neitherReadsNorWritesTheStore` | Fails if a filtered read is served from a stored entry or leaves one in `FakePayloadStore` |
| AC-7 | unit | P0 guard | `ContentListInteractorTest.observeFirstPage_afterAFilteredRead_theUnfilteredPageStillComesFromTheStore` | Fails if the filtered read overwrites or evicts the base entry (second base read makes no server call) (guard: no filtered read exists on base, so the base entry is never touched; protects the entry once `cacheable = false` reads exist) |
| AC-7 | unit | P0 red | `ShowAllVMTest.filterPanelClosed_withAFilter_readsTheFirstPageUncached` | Fails if `ShowAllVM` passes `cacheable = true` for an active filter |
| AC-8 | unit | P0 red | `ShowAllVMTest.filterChange_dropsAnOldFirstPageStillInFlight` | Proves the outcome carried by cancellation: old query's `observeFirstPage` flow emits after the change (delayed fake, `StandardTestDispatcher`); fails if its items reach `ShowAllViewState.Content`. Not a proof of the generation guard (see `filterChange_dropsAnOldDepthRestoreStillInFlight` and `filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne`) |
| AC-8 | unit | P0 red | `ShowAllVMTest.filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne` | Change a filter, then refresh before the first page lands: fails if the refresh walks beyond page 1 or reads the old config (generation guard / atomic change + refresh); red on base, where no filter change exists |
| AC-8 | unit | P0 red | `ShowAllVMTest.filterChange_dropsAnOldWalkStepStillInFlight` | Proves the outcome carried by cancellation: fails if a walk step of the old query still publishes after the change. Not a proof of the generation guard (see `filterChange_dropsAnOldDepthRestoreStillInFlight` and `filterChange_thenRefreshBeforeFirstPage_readsOnlyPageOne`) |
| AC-8 | unit | P1 red | `ShowAllVMTest.filterChange_dropsAnOldDepthRestoreStillInFlight` | Fails if `restoreLoadedDepth` reads with the new config or publishes after the change |
| AC-8 | unit | P0 guard | `ShowAllVMTest` existing tests, `SectionVMTest`, `SectionVMRefreshDepthTest` | Fail if the mutable `config` / `changeQuery` changes paging for grids and rows that never filter |
| AC-9 | unit | P0 red | `ShowAllVMTest.genreList_withAnimeHidden_omitsAnime` | Fails if id 25 stays in `filterState.genres` for a `FollowPreference` config with `showAnimeEnabled` false |
| AC-9 | unit | P1 red | `ShowAllVMTest.genreList_withAnimeShown_keepsAnime` | Fails if the drop ignores the preference |
| AC-9 | unit | P1 red | `ContentListInteractorTest.showAnimeEnabled_followsThePreference` | Fails if it reads a value other than `contentPreferences.showAnime` |
| AC-9 | unit | P1 red | `ContentListInteractorTest.followPreference_whenAnimeIsShownAndWatchedHidden_keepsUnwatchedAnime` | `FollowPreference` config with show-anime on: fails if the anime visibility check (`isVisible`) drops anime items while walking pages; red on the unfixed check (red recorded) |
| AC-10 | unit | P0 red | `KinoPubApiClientGenresTest.getGenres_unwrapsTheItemsOfAStatusItemsBody` | Body `{"status":200,"items":[{"id":9,"title":"Драма","type":"movie"}]}` → `[Genre(9, "Драма")]`; fails on base (bare-list decode) |
| AC-10 | unit | P1 guard | `KinoPubApiClientGenresTest.getGenres_sendsTheTypeParameter` | Fails if `type` is not sent as the query parameter |
| AC-10 | unit | P1 guard | `KinoPubApiClientGenresTest.getGenres_refusedRead_failsInsteadOfAnEmptyList` | Fails if a 422 becomes `success(emptyList())` |
| AC-10 | unit | P0 red | `ShowAllVMTest.genreLoadFailure_showsFailedAndKeepsTheGrid` | Fails if a failed load leaves `genres` in `Loading` or changes the list state |
| AC-10 | unit | P1 red | `ShowAllVMTest.genresRetry_loadsAgainAfterAFailure` | Fails if retry does nothing or starts a second concurrent load |
| AC-10 | unit | P1 red | `ShowAllVMTest.genreList_forA4kGrid_requestsThe4kType` | Fails if `all_4k` asks `getGenres(null)` |
| AC-10 | instrumented | P1 | `ShowAllFilterFocusTest.genresFailed_showsTheErrorRowAndFocusableRetry` | Fails if the failed page has no focusable row |
| AC-11 | unit | P0 guard | `ContentListVMTest.browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle` | Fails if the key stops being `ShowAllScreen_all_movie` |
| AC-11 | instrumented | P0 red | `ShowAllScreenContentFocusTest.activeFilter_focusReturnsToTheFocusedCardAfterReentry` | With an active `ContentFilter` and card 3 of row 2 focused, leave to a handoff and re-enter: fails if focus lands on card 1 or on «Фильтры» instead of the remembered card (the new `ContentFocusGate` / launch request overriding the restore). Red on base: the `filter` parameter of `ShowAllScreenContent` does not exist, so the test does not compile |
| AC-11 | device | P1 | Manual 5 | Filter and card survive details and player on the real navigation stack (automated carriers: the P0 rows above) |
| AC-12 | unit | P1 red | `ShowAllVMTest.filteredEmptyPage_keepsTheFilterActive` | Fails if an empty filtered page drops `isActive` or the list state is not `Empty` |
| AC-12 | instrumented | P1 red | `ShowAllFilterFocusTest.filteredEmpty_showsResetAndDownFromFiltersReachesIt` | Fails if the filtered-empty «Сброс» is missing or unreachable |
| AC-12 | device | P1 | Manual 6 | Real narrow genre with hide-watched |
| AC-13 | device | P1 | Manual 7 | Owner judges the look against the stand |

## Bugs

AC-10 is a defect in base code: `git show b7a7d99e:app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt` lines 632-636 decode `genres` as `List<Genre>`. Written first: `KinoPubApiClientGenresTest.getGenres_unwrapsTheItemsOfAStatusItemsBody`; on base it returns a failure.

## Manual checks

TV box `192.168.1.110:5555`, `com.kino.puber.stage`, worktree dev APK via `tools/android-apk-install-preserve`, lease held. Keys via `adb -s 192.168.1.110:5555 shell input keyevent`. Must never happen in any step: crash, focus on nothing (no highlight anywhere), focus on the rail without Left, old list flashing back after a change.

| # | AC | Steps | Expected |
|---|---|---|---|
| 1 | AC-1 | Movies tab: open «Все фильмы» entry, Back; open the «Все» tile, Back; same on 4K | Title + «Фильтры» in the header; first focus on card 1 each time |
| 2 | AC-3 | On a browse grid: focus card in column 1, `DPAD_UP`, `DPAD_DOWN`; column 6, `DPAD_UP`, `DPAD_DOWN`; from «Фильтры» `DPAD_LEFT` (rail opens); close the rail with `DPAD_RIGHT`; again `DPAD_UP` to «Фильтры», `DPAD_LEFT`, close with `BACK` | Up lands on «Фильтры», Down returns to the same row-1 card; Left opens the rail; after each close (Right and Back) focus is back on «Фильтры», not on a card or nowhere (gate for `railOpenedFromFilters_closesWithFocusBackOnFilters`) |
| 3 | AC-4, AC-5 | «Фильтры» `DPAD_CENTER`; `BACK` (no change); reopen, «Сортировка» → «по году», «по возрастанию», `BACK`, «Жанр» → «Драма», `BACK` `BACK`; while the panel is open and a change is being applied, watch for focus jumping to a grid card | Open: focus on «Сортировка», Left/Right do not leave; first Back: panel closes on «Фильтры», grid not reloaded; focus never leaves the panel until it closes (gate step: the new list landing under an open panel must not take focus); after the change: grid reloads oldest-year-first dramas, focus lands on card 1 |
| 4 | AC-6 | With the filter of step 3, read the header; focus «Сброс», `DPAD_CENTER` | Summary «Драма · по году ↑»; after reset: default order, «Сброс» and summary gone, focus on «Фильтры» while the list loads, then on card 1 when it lands |
| 5 | AC-11 | Filter active, open card 3 row 2, `BACK`; play a card, `BACK` to the grid | Same filter and summary, card 3 row 2 focused, no reload to the unfiltered list |
| 6 | AC-12 | Settings: hide watched on; Movies browse grid, pick a narrow genre (e.g. «Аниме» with show-anime on) until empty | Filtered-empty text with «Сброс»; Down from «Фильтры» reaches it; «Сброс» restores the grid |
| 7 | AC-13 | Screenshots of header (no filter, active filter), both panel pages, filtered-empty state into the evidence file | Owner compares to `docs/design/showall/` stand; no overlap of focused button (scale 1.1) and row-1 tile |

## Perf track

N/A: track `perf` is off for this run.
