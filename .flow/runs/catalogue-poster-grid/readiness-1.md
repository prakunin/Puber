# Readiness 1: catalogue-poster-grid

Verdict: **READY**

Inputs: spec and plan in the worktree (HEAD `de9217e4` plus the uncommitted spec line and plan), `test-design.md`, code at base `ad2c7f0c`.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | Global constraints (stand `de9217e4`, spec Decisions) | M1 |
| AC-2 | Task 1 | five P0 red quality tests; 4K-section clause dropped by spec Decisions 2026-11-24 |
| AC-3 | Task 1 (mapper), Task 2 (card) | four P0 red unit, three P0 red instrumented |
| AC-4 | Task 1, Task 2 | two P0 red, `mapShortItem_keepsRawTitle` guard |
| AC-5 | Task 1 | `mapShortItem_pillRatingsPubWithoutTrailingZero` red, `buildRatings_keepsPubDecimalText` guard |
| AC-6 | Tasks 2, 3 (R-check) | R-check, `ItemMergeCoverageTest` guard |
| AC-7 | Tasks 2, 3 | M1, M5, `videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole` |
| AC-8 | Task 2 | `posterCardImageUrls_startsWithTheStandTier`, `posterCardImageUrls_neverContainsWideUrl` |
| AC-9 | Tasks 2, 3 | `videoItemPoster_longSelectOpensContextMenuWithoutClick`, M2, M3 |
| AC-10 | Task 3 | `ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest` guards, M4 |
| AC-11 | Task 3 | `ShowAllRailCloseFocusTest` guard |
| AC-12 | Tasks 1-3 (untouched list) | title / year / ratings guards, `mapHeroItems_prefersWidePosterAndFallsBackToBigPosters`, AC-12 itest classes, M6 |

## Citations sampled (at `ad2c7f0c`)

| Citation | Resolves to |
|---|---|
| `ShowAllScreenContent.kt:44`, `:105`, `:111-113`, `:117-130`, `:254` | `GRID_COLUMNS = 3`, `GridCells.Fixed`, padding / spacing, `VideoItemHorizontal` call, `resolveRestoreId` |
| `VideoItemUIMapper.kt:17`, `:29-54`, `:52`, `:114-122`, `:124`, `:148` | `RATING_PERCENT_TO_TEN_SCALE`, `mapShortItem` (`imageUrl` = medium), `year`, `buildRatings` (`Float.toString`), `isValidRating`, `formatTitle` |
| `VideoItemUIState.kt:50-71`, `:73`, `:83`, `:174`, `:295`, `:325`, `:345` | state class, `VideoItemPresentation`, `VideoItem`, `CardPoster`, private `TopEndBadge`, `WatchProgressBar`, `WatchedIndicatorBadge` |
| `VideoItemHorizontal.kt:66-74`, `:89`, `:193`, `:209` | modifier chain, wide-big-medium order, private `onSelectKeyClick`, `isSelectKey` |
| `TvContextMenu.kt:319`, `PuberTheme.kt:43`, `Color.kt:28-29`, `RatingUIState.kt:34-47`, `:52-58`, `:62` | `onTvContextMenuKey`, TV `darkColorScheme`, `KinopoiskOrange` / `ImdbYellow`, KP / IMDB / PUB, `brandColor`, `Rating` |
| `Models.kt:50`, `:60`, `:73` | `year`, `quality`, `ratingPercentage: Int?` |
| `ShowAllScreenContentFocusTest.kt:79`, `:197-199`; `ShowAllRailCloseFocusTest.kt:195-198`; `VideoItemUIMapperTest.kt:20`, `:315`, `:319`; `ContentListPagingVM.kt:349` | assertion, fixture constants, `FakeResourceProvider`, `testItem`, `year` param, `mapItems` |
| `index.src.html:264-270`, `:490`, `:513`, `:525`; README "Starting numbers" | `START` (rowGap 16 matches spec), `badgeHTML`, `pillHTML`, `tileHTML` |
| `com.adamglin.phosphoricons.fill.ThumbsUp` | `fill/ThumbsupKt.class` in phosphor-icon-android 1.0.0 aar |

## Order and build

Task 1 (state + mapper) -> Task 2 (card, unused until Task 3, all new fields defaulted) -> Task 3 (grid swap, fixtures, full gate). Each task ends on a green fast gate; no task leaves the build red.

## Scope

No CC / AC3, `Models.kt` and `ContentCacheRepository` untouched, Home / catalogue cards untouched: inside Out of scope. Open questions: none blocking (Q1-Q3 answered, architect Q1-Q3 on recommendation).

## Notes for the implementer (non-blocking)

| # | Note |
|---|---|
| 1 | `test-design.md` R7 and AC-2 still name the 4K-section rule; the dropped test is not written (spec Decisions). |
| 2 | Spec "Tests" table names a unit `VideoItemPosterTest.kt` with `originalLine` cases; the plan's `VideoItemPosterUrlsTest` plus instrumented `VideoItemPosterTest` govern. |
| 3 | In `mapShortItem_pillRatingsUseDotDecimal_underRussianLocale` use `Locale.forLanguageTag("ru")` as `PlayerUIMapperTest.kt:62` does; `Locale("ru")` is deprecated. |
