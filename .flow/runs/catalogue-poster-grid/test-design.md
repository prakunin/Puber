# Test design: catalogue-poster-grid

Inputs: `brief.md` (ACs, risks, owner decisions D1-D4), `design-architect.md` (names, files), `design-ux.md` (stand controls). Architect Q1 takes its recommended answer. Levels per `.flow/roles/test-designer.md`; `instrumented` runs only through `make DEVICE=<serial> itest [TESTS=<fqcn>]`; device steps on the TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only.

## Risk profile

| ID | Risk | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Original and year drawn as one ellipsized `Text`: a long original cuts the year away (brief risk "Year never ellipsizes") | 3 | 2 | 6 | `videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole` (instrumented), M5 |
| R2 | Focus restore / rail handoff break: the requester lands on an inner node, not the tile | 2 | 3 | 6 | `ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest` (instrumented), M4 |
| R3 | Focus-test fixtures stop reaching row 2+ at 6 columns (24 items = 4 rows), failing for fixture reasons or passing vacuously | 3 | 2 | 6 | Fixture counts derived from `SHOW_ALL_GRID_COLUMNS`; the `>= DeepRows * GridColumns` assertion (`ShowAllScreenContentFocusTest.kt:79`) stays |
| R4 | New `VideoItemUIState` fields or a changed `buildRatings` / `title` / `year` alter other screens (AC-12) | 2 | 3 | 6 | Guard unit tests on `title`, `year` and `ratings`, existing card androidTests, M6 |
| R5 | New card loses key-up select or long-press context menu | 2 | 3 | 6 | `VideoItemPosterTest` (instrumented), M3 |
| R6 | PUB pill formatted with the device locale ("8,3" on a Russian box) | 2 | 2 | 4 | `mapShortItem_pillRatingsUseDotDecimal_underRussianLocale` |
| R7 | 4K items carry `quality` below 2160 | 2 | 2 | 4 | Threshold unit tests; `mapShortItem_qualityBadgeUhd_whenSectionIs4kAndQualityBelow2160`; M5 on a 4K-section item |
| R8 | Title split wrong on a bare "/" (`Fate/Zero`) | 2 | 2 | 4 | `mapShortItem_keepsBareSlashInLocalTitle` |
| R9 | Wide or big poster requested first by the grid instead of `posters.medium` (D1) | 2 | 2 | 4 | `VideoItemPosterUrlsTest` |
| R10 | Focus frame or lift clipped on the top row / column 1, or the 1.05 lift overlaps neighbours across 12 dp gaps | 2 | 2 | 4 | M2 (owner judges, README "Open items" 3) |
| R11 | Poster lift recomposes the grid on every D-pad move | 1 | 2 | 2 | Accepted: lift read in `graphicsLayer { }`; M2 notes visible stutter |
| R12 | More tiles on screen raises bitmap memory | 1 | 2 | 2 | Accepted: `posters.medium` decodes less than the wide poster it replaces |
| R13 | KP / IMDb pills show raw API text ("5.978"), triage-1 #1 | 3 | 2 | 6 | `mapShortItem_pillRatingsRoundKpAndImdbToOneDecimal`, `mapShortItem_pillRatingsKpAndImdbDropTrailingZero` |
| R14 | Closing the context menu in Show all sends focus to the rail, triage-1 #2 | 3 | 3 | 9 | `showAllMenuOpenAndBackReturnsFocusToTheOpeningCard` (instrumented), M3 |
| R15 | Year 0 drawn as "· 0"; " / Orig" draws Orig twice, triage-1 #8 | 1 | 2 | 2 | Fixed anyway: `mapShortItem_yearEmpty_whenYearIsZero`, `mapShortItem_usesOriginalAsLocalTitle_whenLocalPartEmpty` |
| R16 | Load-more effect keyed only on the threshold flag: a page of under 24 items lands while the flag stays true, no new `LoadMore`, paging stalls, triage-2 #1 | 3 | 3 | 9 | `showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold` (instrumented), M7 |

## Traceability

Unit tests: `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt` (extend `testItem` with `quality`, `year`), `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterUrlsTest.kt` (new). Instrumented: `app/src/androidTest/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterTest.kt` (new, style of `TvContextMenuLongPressTest`), the two Show all focus tests. Rows from fix round 1 (triage-1 #1, #2, #8) are `red` against head `f68f3524`. The original-title line is asserted as two values: mapper fields `originalTitle` and `year` (unit), and their rendering as separate text nodes (instrumented).

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | device | P0 | M1 (owner approves `docs/design/showall/README.md` before the port) | port started without agreed numbers |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeUhd_whenQualityIs2160` | fails if the UHD threshold is missing or above 2160 |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeHd_whenQualityIs1080` | fails if 1080 maps to none or UHD |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeHd_whenQualityIs720` | fails on an off-by-one at the HD lower bound (`> 720`) |
| AC-2 | unit | P0 guard | `mapShortItem_qualityBadgeNull_whenQualityIs719` | fails if the HD bound is lowered |
| AC-2 | unit | P0 guard | `mapShortItem_qualityBadgeNull_whenQualityNull` | fails if null defaults to a badge |
| AC-2 | unit | P1 | `mapShortItem_qualityBadgeUhd_whenSectionIs4kAndQualityBelow2160` | fails if the 4K-section rule is ignored |
| AC-3 | unit | P0 red | `mapShortItem_originalTitleAndYear_whenTitleHasOriginalAndYear` | "Рус / Orig", 2020 gives `originalTitle` "Orig", `year` "2020"; fails if the year is folded into `originalTitle` (one value cannot keep the year whole) |
| AC-3 | unit | P0 guard | `mapShortItem_yearOnly_whenTitleHasNoOriginal` | "Рус", 2020 gives "" and "2020"; fails if the year is dropped with the missing original |
| AC-3 | unit | P0 red | `mapShortItem_originalOnly_whenYearNull` | "Рус / Orig", null gives "Orig" and ""; fails on "null" or a stray separator in either value |
| AC-3 | unit | P0 guard | `mapShortItem_noOriginalAndNoYear_whenBothMissing` | "Рус", null gives "" and ""; fails if either value is non-blank |
| AC-3 | unit | P0 red | `mapShortItem_yearEmpty_whenYearIsZero` | "Рус / Orig", 0 gives `year` ""; fails if a year of 0 or below is kept (card draws "· 0") |
| AC-3 | instrumented | P0 red | `videoItemPoster_showsOriginalAndYearAsSeparateTexts` | text nodes "Orig" and "· 2020"; fails if the line is one node or the separator is missing |
| AC-3 | instrumented | P0 red | `videoItemPoster_showsYearAlone_whenNoOriginalTitle` | node "2020" and no node containing "·"; fails on a leading "· 2020" |
| AC-3, AC-7 | instrumented | P0 red | `videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole` | 80-char original, year 2020: `GetTextLayoutResult` of the original node has `isLineEllipsized(0)`; the year node has `!didOverflowWidth` and a width of at least its `getLineRight(0)`; fails on one shared `Text` or a weighted year (shown red once with the weight on the year) |
| AC-4 | unit | P0 red | `mapShortItem_splitsLocalAndOriginalTitle_whenTitleHasSeparator` | "Рус / Orig" gives `localTitle` "Рус", `originalTitle` "Orig"; fails without trim or split |
| AC-4 | unit | P0 red | `mapShortItem_leavesOriginalTitleEmpty_whenTitleHasNoSeparator` | "Рус" gives "Рус" and ""; fails if the original repeats the title |
| AC-4 | unit | P0 red | `mapShortItem_usesOriginalAsLocalTitle_whenLocalPartEmpty` | " / Orig" gives `localTitle` "Orig", `originalTitle` ""; fails if the local title is empty or Orig is drawn twice |
| AC-4 | unit | P1 | `mapShortItem_keepsBareSlashInLocalTitle` | "Fate/Zero" stays whole; fails if the split is on "/" instead of " / " |
| AC-4, AC-12 | unit | P0 guard | `mapShortItem_keepsRawTitle` | `title` stays "Рус / Orig"; fails if `title` is rewritten for every card |
| AC-3, AC-12 | unit | P0 guard | `mapShortItem_keepsYearText` | `year` stays "2020" with no separator; fails if `year` is reformatted for every card (`CardYear`, `VideoItemUIState.kt:264`) |
| AC-5 | unit | P0 red | `mapShortItem_pillRatingsPubWithoutTrailingZero` (100, 83) | "10" and "8.3"; fails on `Float.toString()` ("10.0") |
| AC-5 | unit | P1 | `mapShortItem_pillRatingsUseDotDecimal_underRussianLocale` | sets `Locale("ru")`, restores after; fails on `String.format` with the default locale |
| AC-5 | unit | P1 | `mapShortItem_pillRatingsOrderPubKpImdb` | fails if pills reuse the `buildRatings` order |
| AC-5 | unit | P0 red | `mapShortItem_pillRatingsRoundKpAndImdbToOneDecimal` (KP "7.43", IMDb "6.871") | "7.4" and "6.9"; fails if the raw API text is passed through |
| AC-5 | unit | P0 red | `mapShortItem_pillRatingsKpAndImdbDropTrailingZero` (KP "5.978", IMDb "8.0") | "6" and "8"; fails on "6.0" or "8.0" |
| AC-5 | unit | P1 | `mapShortItem_pillRatingsKpAndImdbUseDotDecimal_underRussianLocale` (KP "7.43") | sets `Locale("ru")`, restores after; fails on "7,4" from `String.format` with the default locale |
| AC-5 | unit | P1 | `mapShortItem_pillRatingsSkipMissingRatings` | one rating gives one pill; fails if blank or zero ratings make pills |
| AC-5, AC-12 | unit | P0 guard | `buildRatings_keepsPubDecimalText` (100 to "10.0", 83 to "8.3") | fails if the shared PUB text changes for Home, hero and details |
| AC-5, AC-12 | unit | P0 guard | `buildRatings_keepsRawKpAndImdbText` (KP "5.978", IMDb "7.43") | values unchanged; fails if the pill rounding moves into the shared `buildRatings` |
| AC-6 | review | P0 | R-check below | colour literal in the card; `Models.kt` or `mergeSparse` touched; CC / AC3 state or strings added |
| AC-6 | unit | P0 guard | `everyFieldOfAnItemSurvivesAMergeOverASparseRecord`, `theFixtureSetsEveryFieldItemDeclares` (`ItemMergeCoverageTest.kt`, unchanged) | fails if an `Item` field is added without its merge line |
| AC-7 | device | P0 | M1, M5 | grid differs from the stand; year cut off |
| AC-8 | unit | P0 red | `posterCardImageUrls_startsWithTheStandTier` | first URL is `mediumImageUrl` (D1), then big; fails on the `VideoItemHorizontal` order |
| AC-8 | unit | P0 red | `posterCardImageUrls_neverContainsWideUrl` | wide also placed in `imageFallbackUrls`; fails if wide is only dropped from the head |
| AC-8 | unit | P1 | `posterCardImageUrls_dropsBlankUrlsAndFallsBack` | blank medium and big fall through to `imageFallbackUrls`; fails on an empty first URL |
| AC-9 | instrumented | P0 red | `videoItemPoster_longSelectOpensContextMenuWithoutClick` | held Enter calls `onContextMenu` once and `onClick` never; fails if `onTvContextMenuKey` is not applied |
| AC-9 | instrumented | P1 | `videoItemPoster_selectKeyUpInvokesClickOnce` | fails if select fires on key-down or twice |
| AC-9 | instrumented | P0 red | `showAllMenuOpenAndBackReturnsFocusToTheOpeningCard` (`ShowAllRailCloseFocusTest`) | long press (held select) on the card at row 6, column 0 (`DeepRows` = 5 down presses), then Back: that card is focused; fails if `ShowAllScreenContent` provides no `LocalTvDialogFocusRestorer` (focus on the rail or the first card) |
| AC-9 | device | P0 | M2, M3 | frame on more or fewer than one tile; menu key does nothing |
| AC-7, triage-2 #1 | instrumented | P0 red | `showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold` (`ShowAllScreenContentFocusTest`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/`) | state of 12 items (6 columns), focus moved to the last row so the 4-row threshold is met and `CommonAction.LoadMore` is counted once; then the state is replaced with 24 items (page of 12, last rows still inside the 4-row threshold): the count reaches 2; fails on an effect keyed only on `shouldLoadMore` (flag stays true, count stays 1, paging stalls). Red on head `3759abcf` |
| AC-4, AC-7 | instrumented | P1 | `videoItemPoster_showsBadgesPillsAndBothTitles` | text nodes "4K", "10", "Рус", "Orig"; fails if a state field is not drawn |
| AC-4, AC-6, AC-12 | instrumented | P1 | `videoItemPoster_showsNoBadgesAndNoOriginalTitle_whenStateHasNone` | default state draws no badge text (no "CC", "AC3", "HD", "4K") and no original line, falls back to `title`; fails if defaults render |
| AC-10 | instrumented | P0 guard | `ShowAllScreenContentFocusTest` (the five existing tests) | `ItemCount` derived from `SHOW_ALL_GRID_COLUMNS`; fails if the requester leaves the tile |
| AC-10, AC-11 | instrumented | P0 guard | `ShowAllRailCloseFocusTest` (all five tests) | same fixture rule; fails if rail close lands off the remembered card |
| AC-10 | device | P0 | M4 | focus restore on real data |
| AC-12 | unit | P1 guard | `mapHeroItems_prefersWidePosterAndFallsBackToBigPosters` | hero URL order unchanged |
| AC-12 | instrumented | P1 guard | `VideoGridInitialFocusTest`, `FavoriteScreenContentFocusTest`, `HomeFocusTraversalTest`, `SectionRowFocusTraversalTest`, `EpisodeScheduleScreenContentTest` | full `make DEVICE=<serial> itest`; fails if shared card code changed behaviour |
| AC-12 | device | P0 | M6 | | other screens differ from before |
| M7 | In a section with more than 100 items open Show all; hold `K 20` to the bottom and keep pressing down 3 times after each page lands | Each page appends and the grid keeps growing until the section ends; threshold stays 4 rows | list stops growing while focus is on the last rows; spinner stuck |

R-check (AC-6), in the worktree against base `ad2c7f0c`:

| Command | Expected |
|---|---|
| `git diff ad2c7f0c -- app/src/main/java/com/kino/puber/data/api/models/Models.kt app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt` | empty |
| `rg -n 'Color\(0x' app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPoster.kt` | no match |
| `rg -n '4CAF50' app/src/main/java/com/kino/puber/core/ui/uikit/theme/Color.kt` | one named `val` |
| `rg -n -i 'hasAc3\|hasSubtitles\|"AC3"\|"CC"' app/src/main` | no match |

## Bugs

N/A: feature run.

## Manual checks

Device `192.168.1.110:5555` under a lease; worktree dev APK via `tools/android-apk-install-preserve`; `K` = `adb -s 192.168.1.110:5555 shell input keyevent`. Screenshots with account data stay out of Git; run `tools/mobile-evidence-audit.sh` on the evidence dir.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | Owner opens the `docs/design/showall/` stand; then on the box open Catalogue, a section, Show all; screenshot | Stand approved first; 6 columns, HD / 4K badge top start, pills at poster foot, titles under poster as in the stand | port merged before approval; 16:9 cards; a CC or AC3 badge |
| M2 | In Show all: `K 22` x3, `K 20`, `K 21` until column 1, `K 21` once more (rail), `K 22` (back); screenshot after each | Exactly one tile framed (#938F99, 2 dp) and lifted; rail opens from column 1; returning focuses the remembered card; first-row frame and lift not clipped | two framed tiles; no frame; focus lost; visible stutter while moving |
| M3 | Focus a card; `K --longpress 23`; `K 4`; then `K 82` (menu); `K 4` | `VideoItemContextMenuDialog` opens both ways; Back returns focus to the same card | details opening on long-press; focus on another card |
| M4 | Focus row 2, column 3; `K 23`; on details `K 4` | Same card focused | focus on the first card or the rail; crash |
| M5 | Find a title without " / " but with a year, one with a long original title, one with one rating, one long Russian title, a 4K item, a watched item, a serial with new episodes | Year alone under the first; long original ends in an ellipsis and " · <year>" stays whole; one pill; Russian title wraps to 2 lines then ellipsizes; 4K badge in the named green; eye top end; counter and progress bar as before | year cut or missing; leading "·"; empty gap line; "10.0"; overlapping badge and eye |
| M6 | Before installing the worktree APK, screenshot Home rows, a catalogue row, Favourites, Episode schedule, player episode panel; repeat after install | Pairs identical apart from content changes | new badges, split titles, "·" year lines or new rating text outside Show all |

## Perf

N/A: track `perf` off (`brief.md` Q3).
