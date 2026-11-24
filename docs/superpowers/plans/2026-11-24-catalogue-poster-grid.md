# Vertical poster card with badges and ratings for the Show all grid - plan

**Spec:** `docs/superpowers/specs/2026-11-24-catalogue-poster-grid.md`
**Goal:** Show all draws a 6-column grid of a new 2:3 `VideoItemPoster` card (HD / 4K badge, rating pills, local and original titles with year) while every other screen keeps its card.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/dev/orca/workspaces/Puber/catalogue-poster-grid && ./tools/agentw :app:compileDevDebugKotlin`; full gate: `cd /home/dev/orca/workspaces/Puber/catalogue-poster-grid && ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Never `./gradlew` in the worktree; set `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/src/test`, `app/src/androidTest`, `app/src/main/res` or `docs/`. Line numbers are at base `ad2c7f0c`.
- Visual numbers: spec "Card constants" and "Grid constants" tables (source `docs/design/showall/README.md` "Starting numbers", `docs/design/showall/index.src.html:264-270` `START`). AC-1 is met before this plan: the stand is committed (`de9217e4`) and approved (spec "Decisions"); every task consumes it, none changes it.
- Instrumented tests are compiled here with `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and run only by qa-device: `make DEVICE=<serial> itest [TESTS=<fqcn>]` from the worktree. Never `connected*AndroidTest`.
- Unit tests: JUnit 5 (`org.junit.jupiter.api.Test`, `Assertions.assertEquals`), `FakeResourceProvider` as in `VideoItemUIMapperTest.kt:20`.
- Untouched (AC-6, AC-12): `data/api/models/Models.kt`, `data/cache/ContentCacheRepository.kt`, `VideoItem` (`VideoItemUIState.kt:83`), `VideoItemHorizontal` body, `Rating` (`RatingUIState.kt:62`), `buildRatings` (`VideoItemUIMapper.kt:114-122`). No CC / AC3 state, strings or badge.

---

## Tasks

### Task 1: Map quality badge, split titles and pill ratings into the card state

**Covers:** AC-2, AC-3 (mapper half), AC-4, AC-5, AC-12 (guards)
**Files:**
- Modify: `core/ui/uikit/component/moviesList/VideoItemUIState.kt` - add to `VideoItemUIState` (`:50-71`), after `scheduledReleaseDate` (`:70`): `val qualityBadge: VideoQualityBadge? = null`, `val localTitle: String = ""`, `val originalTitle: String = ""`, `val pillRatings: List<RatingUIState> = emptyList()`; add `enum class VideoQualityBadge { HD, UHD }` after `VideoItemPresentation` (`:73-76`).
- Modify: `core/ui/model/VideoItemUIMapper.kt` - in `mapShortItem` (`:29-54`) set the four fields; `year` (`:52`) and `title` (`:35`) stay as they are. Add beside `buildRatings` (`:114`) a public `fun buildPillRatings(item: Item): List<RatingUIState>`; add top-level `internal fun splitItemTitle(raw: String): Pair<String, String>` and `private const` thresholds next to `RATING_PERCENT_TO_TEN_SCALE` (`:17`).
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt` - add `quality: Int? = null` to `testItem` (`:315-357`) and pass it to `Item` (`Models.kt`, `Item.quality`); `year` is already a parameter (`:319`).

**Consumes:** `Item.quality`, `Item.year`, `Item.ratingPercentage`, `Item.kinopoiskRating`, `Item.imdbRating`; `isValidRating` (`VideoItemUIMapper.kt:124`); `RatingUIState.KP/IMDB/PUB` (`RatingUIState.kt:34-47`).
**Produces:** `VideoQualityBadge`; `VideoItemUIState.qualityBadge / localTitle / originalTitle / pillRatings`; `VideoItemUIMapper.buildPillRatings(item)`; `splitItemTitle(raw)`.

Rules:

| Field | Rule |
|---|---|
| `qualityBadge` | `quality >= 2160` UHD; `quality >= 720` HD; else or null: null |
| `localTitle`, `originalTitle` | `splitItemTitle(item.title)`: split once on `" / "` (`split(" / ", limit = 2)`), trim each part; no separator gives `raw.trim()` and `""`. A bare `/` (`Fate/Zero`) does not split |
| `pillRatings` | Order PUB, KP, IMDb. PUB only when `ratingPercentage > 0`; text from integer arithmetic, no `Float.toString` and no `String.format`: `p % 10 == 0` gives `"${p / 10}"`, else `"${p / 10}.${p % 10}"` (100 to "10", 83 to "8.3"). KP and IMDb when `isValidRating()`, value as given |

- [ ] Write the failing tests below (P0 red first) and run them red on the unchanged mapper (they do not compile until the fields exist: add the fields with defaults first, then run red)
- [ ] Add the state fields, enum, `splitItemTitle`, `buildPillRatings`, thresholds; fill them in `mapShortItem`
- [ ] Run the fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.model.VideoItemUIMapperTest'`; commit

| Test (`VideoItemUIMapperTest`) | Priority | Input | Expected |
|---|---|---|---|
| `mapShortItem_qualityBadgeUhd_whenQualityIs2160` | P0 red | quality 2160 | UHD |
| `mapShortItem_qualityBadgeHd_whenQualityIs1080` | P0 red | 1080 | HD |
| `mapShortItem_qualityBadgeHd_whenQualityIs720` | P0 red | 720 | HD |
| `mapShortItem_qualityBadgeNull_whenQualityIs719` | P0 red | 719 | null |
| `mapShortItem_qualityBadgeNull_whenQualityNull` | P0 red | null | null |
| `mapShortItem_originalTitleAndYear_whenTitleHasOriginalAndYear` | P0 red | "Рус / Orig", 2020 | `originalTitle` "Orig", `year` "2020" |
| `mapShortItem_yearOnly_whenTitleHasNoOriginal` | P0 red | "Рус", 2020 | "", "2020" |
| `mapShortItem_originalOnly_whenYearNull` | P0 red | "Рус / Orig", null | "Orig", "" |
| `mapShortItem_noOriginalAndNoYear_whenBothMissing` | P0 red | "Рус", null | "", "" |
| `mapShortItem_splitsLocalAndOriginalTitle_whenTitleHasSeparator` | P0 red | "Рус / Orig" | `localTitle` "Рус", `originalTitle` "Orig" |
| `mapShortItem_leavesOriginalTitleEmpty_whenTitleHasNoSeparator` | P0 red | "Рус" | "Рус", "" |
| `mapShortItem_keepsBareSlashInLocalTitle` | P1 | "Fate/Zero" | "Fate/Zero", "" |
| `mapShortItem_keepsRawTitle` | P0 guard | "Рус / Orig" | `title` "Рус / Orig" |
| `mapShortItem_keepsYearText` | P0 guard | year 2020 | `year` "2020" |
| `mapShortItem_pillRatingsPubWithoutTrailingZero` | P0 red | `ratingPercentage` 100, then 83 | PUB "10", "8.3" |
| `mapShortItem_pillRatingsUseDotDecimal_underRussianLocale` | P1 | `Locale.setDefault(Locale("ru"))`, restored in `finally`; 83 | "8.3" |
| `mapShortItem_pillRatingsOrderPubKpImdb` | P1 | 90, KP "7.2", IMDb "7.1" | classes PUB, KP, IMDB in that order |
| `mapShortItem_pillRatingsSkipMissingRatings` | P1 | KP "7.2", IMDb "", `ratingPercentage` 0 | one pill, KP |
| `buildRatings_keepsPubDecimalText` | P0 guard | 100, then 83 | `ratings` PUB "10.0", "8.3" |

**Done when:** the unit command above passes; the P0 red tests were red on the unchanged mapping (recorded under Red run); fast gate green.

---

### Task 2: Add the VideoItemPoster card

**Covers:** AC-1 (consumes the approved stand), AC-3 (card half), AC-4, AC-6, AC-7, AC-8, AC-9
**Files:**
- Modify: `core/ui/uikit/theme/Color.kt` - after `ImdbYellow` (`:29`): `val KinoPubGreen = Color(0xFF4CAF50)`, `val OnKinoPubGreen = Color(0xFF0B1F0C)`, `val PosterChipScrim = Color.Black.copy(alpha = 0.62f)`, with a one-line KDoc naming the stand.
- Modify: `res/values/strings.xml` - `poster_card_badge_hd` = `HD`, `poster_card_badge_4k` = `4K`, both `translatable="false"`.
- Modify: `core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:193` - `private fun Modifier.onSelectKeyClick` to `internal`, body unchanged (`Key.isSelectKey`, `:209`, stays private).
- Modify: `core/ui/uikit/component/moviesList/VideoItemUIState.kt:295` - `private fun TopEndBadge` to `internal`, body unchanged.
- Create: `core/ui/uikit/component/moviesList/VideoItemPoster.kt`.
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterUrlsTest.kt` (new, unit).
- Test: `app/src/androidTest/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterTest.kt` (new, instrumented; style of `app/src/androidTest/kotlin/com/kino/puber/core/ui/uikit/component/TvContextMenuLongPressTest.kt`: `createComposeRule` from `junit4.v2`, `PuberTheme`, `performKeyInput { keyDown; advanceEventTime(500); keyUp }`).

**Consumes:** Task 1 fields; `onTvContextMenuKey` (`TvContextMenu.kt:319`); `onSelectKeyClick` (`VideoItemHorizontal.kt:193`); `TopEndBadge` (`VideoItemUIState.kt:295`), `WatchProgressBar` (`:325`); `SkeletonAsyncImage` with the fallback walk of `CardPoster` (`VideoItemUIState.kt:173-205`); `R.drawable.ic_kinopoisk`, `R.drawable.ic_imdb` and their tints as in `RatingUIState.kt:52-58`; `com.adamglin.phosphoricons.fill.ThumbsUp` (`PhosphorIcons.Fill.ThumbsUp`, phosphor-icon-android 1.0.0, class `ThumbsupKt`).
**Produces:**

```kotlin
@Composable
fun VideoItemPoster(
    state: VideoItemUIState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onContextMenu: (() -> Unit)? = null,
)
internal object VideoItemPosterDefaults // spec "Card constants", KDoc links docs/design/showall/README.md
internal fun posterCardImageUrls(state: VideoItemUIState): List<String>
internal data class OriginalLine(val original: String, val yearText: String)
internal fun originalLine(originalTitle: String, year: String): OriginalLine?
```

| Piece | Behaviour |
|---|---|
| `posterCardImageUrls` | `listOf(imageUrl, bigImageUrl) + imageFallbackUrls`, drop blanks and every URL equal to `wideImageUrl`, `distinct()` |
| `originalLine` | `private const val YEAR_SEPARATOR = " · "`. ("Orig", "2020") -> ("Orig", " · 2020"); ("", "2020") -> ("", "2020"); ("Orig", "") -> ("Orig", ""); ("", "") -> null |
| Tile | One `androidx.tv.material3.Card`, the only focusable; modifier order as `VideoItemHorizontal.kt:66-74`: `modifier`, `onTvContextMenuKey(onOpen = onContextMenu)` when non-null, `onSelectKeyClick(onClick)`; `onClick = onClick`. `CardDefaults.shape(RoundedCornerShape(TileCorner))`, `colors` container and focused container `surfaceVariant` at `TileFillAlpha`, `border` rest 1 dp `border` at 22 %, focused 2 dp `colorScheme.border`, `scale(focusedScale = FocusScale)`, `glow` none. Width from the caller (fills the grid cell) |
| Poster | `Column(padding(TilePadding))` > `Box(fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(PosterCorner)))` holding the image fed by `posterCardImageUrls` (remember keyed on `state.id` and the list, index advanced in `onError` as `CardPoster` does), `ContentScale.Crop` |
| Quality badge | `TopStart`, inset `BadgeInset`; HD: `PosterChipScrim` fill, `onSurface` label, 1 dp `onSurface` stroke at 0.28; UHD: `KinoPubGreen` fill, `OnKinoPubGreen` label; text from the two string resources; none when `qualityBadge == null` |
| Top end | `TopEndBadge(state, Modifier.align(Alignment.TopEnd))` |
| Pills | `FlowRow(maxItemsInEachRow = 2)`, centred, `BottomCenter`, `padding(bottom = PillInset, horizontal = PillRowPaddingHorizontal)`, spacing `PillSpacing`; one private `RatingPill` per `state.pillRatings`: `PosterChipScrim` fill, `onSurface` text; PUB icon `ThumbsUp` tinted `KinoPubGreen`, KP / IMDb drawables tinted `KinopoiskOrange` / `ImdbYellow`. Add `@OptIn(ExperimentalLayoutApi::class)` only if the compiler asks |
| Progress | `WatchProgressBar(state.progressPercent, state.isWatched, Modifier.align(Alignment.BottomCenter))` |
| Title | `Spacer(PosterToTitle)`; `Text(state.localTitle.ifBlank { state.title }, minLines = 2, maxLines = 2, Ellipsis)`, 10 / 12 sp, `FontWeight.Medium`, `titleSmall` tracking, `onSurface` |
| Original line | `Spacer(TitleToOriginal)`; `originalLine(...)` non-null: `Row` of original `Text` (`Modifier.weight(1f, fill = false)`, `maxLines = 1`, Ellipsis) and `yearText` `Text` (no weight, `maxLines = 1`, `softWrap = false`), both 8 / 10 sp `onSurfaceVariant`, the original `Text` omitted when blank; null: one empty `Text` with `minLines = 1` in the same style to keep the height |
| Previews | 4K, HD, no badge, no original, long original with year |

No `Color(0x...)` literal in `VideoItemPoster.kt`; colours come from `MaterialTheme.colorScheme` and `Color.kt`.

- [ ] Write `VideoItemPosterUrlsTest` and run it red (compile-red until `posterCardImageUrls` exists: add the function returning `emptyList()` first)
- [ ] Add colours, strings, visibility changes, `VideoItemPoster.kt`
- [ ] Write `VideoItemPosterTest` (instrumented); compile it with `./tools/agentw :app:compileDevDebugAndroidTestKotlin`
- [ ] Run the fast gate and `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.moviesList.VideoItemPosterUrlsTest'`; commit

| Test | Level, priority | Fixture | Expected |
|---|---|---|---|
| `posterCardImageUrls_startsWithTheStandTier` | unit, P0 red | medium, big, wide set | `[medium, big]`, medium first |
| `posterCardImageUrls_neverContainsWideUrl` | unit, P0 red | wide also in `imageFallbackUrls` | no element equals wide |
| `posterCardImageUrls_dropsBlankUrlsAndFallsBack` | unit, P1 | medium and big blank, fallbacks `[a]` | `[a]` |
| `videoItemPoster_showsOriginalAndYearAsSeparateTexts` | instr., P0 red | localTitle "Рус", originalTitle "Orig", year "2020" | unmerged node text "Orig" and a separate node containing "· 2020" |
| `videoItemPoster_showsYearAlone_whenNoOriginalTitle` | instr., P0 red | originalTitle "", year "2020" | node "2020"; no node containing "·" |
| `videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole` | instr., P0 red | 80-char original, year "2020", card 135 dp wide | original node `GetTextLayoutResult` `isLineEllipsized(0)`; year node not ellipsized, text contains "· 2020", bounds inside the card |
| `videoItemPoster_longSelectOpensContextMenuWithoutClick` | instr., P0 red | counters for both callbacks | held Enter: `onContextMenu` 1, `onClick` 0 |
| `videoItemPoster_selectKeyUpInvokesClickOnce` | instr., P1 | | after keyDown `onClick` 0; after keyUp 1 |
| `videoItemPoster_showsBadgesPillsAndBothTitles` | instr., P1 | UHD, pill PUB "10", "Рус" / "Orig" | nodes "4K", "10", "Рус", "Orig" |
| `videoItemPoster_showsNoBadgesAndNoOriginalTitle_whenStateHasNone` | instr., P1 | `VideoItemUIState(id, title = "Card 1", imageUrl = "", bigImageUrl = "")` | no "CC", "AC3", "HD", "4K"; no original line node with text; node "Card 1" |

**Done when:** the unit command passes; `:app:compileDevDebugAndroidTestKotlin` passes; fast gate green; `rg -n 'Color\(0x' app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPoster.kt` prints nothing.

---

### Task 3: Switch Show all to the poster card in 6 columns

**Covers:** AC-6, AC-7, AC-8, AC-9, AC-10, AC-11, AC-12
**Files:**
- Modify: `ui/feature/showall/content/ShowAllScreenContent.kt:44` - `private const val GRID_COLUMNS = 3` becomes `internal const val SHOW_ALL_GRID_COLUMNS = 6` (used at `:105`); add private `GridContentPadding = 16.dp`, `GridColumnSpacing = 12.dp`, `GridRowSpacing = 16.dp` used at `:111-113`.
- Modify: `ShowAllScreenContent.kt:117-130` - `VideoItemHorizontal(...)` becomes `VideoItemPoster(state = item, onClick = clickCallback, modifier = <same chain :119-127>, onContextMenu = { contextMenuItem = item })`; swap the import at `:38`. `rememberGridFocusMemory`, `GridFocusMemory`, `resolveRestoreId` (`:151-258`) unchanged.
- Modify: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt:197-199` - declare `DeepRows = 5` first, then `GridColumns = SHOW_ALL_GRID_COLUMNS`, `ItemCount = (DeepRows + 3) * SHOW_ALL_GRID_COLUMNS` (8 rows, as today); the assertion at `:79` stays.
- Modify: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt:195-198` - declare `DeepRows`, `Repeats`, `ScrollRows = 7` first, then `ItemCount = (ScrollRows + 1) * SHOW_ALL_GRID_COLUMNS` (8 rows, as today).

**Consumes:** `VideoItemPoster` (Task 2).
**Produces:** `SHOW_ALL_GRID_COLUMNS` (`internal`, reused by catalogue-browse-all).

- [ ] Edit the grid and the two fixtures
- [ ] Run the fast gate, `./tools/agentw :app:compileDevDebugAndroidTestKotlin` and the full gate; commit

R-check (AC-6), from the worktree:

| Command | Expected |
|---|---|
| `git diff ad2c7f0c -- app/src/main/java/com/kino/puber/data/api/models/Models.kt app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt` | empty |
| `rg -n 'Color\(0x' app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPoster.kt` | no match |
| `rg -n '4CAF50' app/src/main/java/com/kino/puber/core/ui/uikit/theme/Color.kt` | one named `val` |
| `rg -n -i 'hasAc3\|hasSubtitles\|"AC3"\|"CC"' app/src/main` | no match |

**Done when:** full gate green (includes `ItemMergeCoverageTest` and `mapHeroItems_prefersWidePosterAndFallsBackToBigPosters` unchanged and passing); androidTest compiles; the R-check table holds; `rg -n 'VideoItemHorizontal' app/src/main/java/com/kino/puber/ui/feature/showall` prints nothing. Device steps (spec Manual checks M1-M6, `make DEVICE=192.168.1.110:5555 itest` for `VideoItemPosterTest`, `ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest` and the AC-12 guard classes) belong to qa-device at verify.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** Task 1 `5e4f7185`, Task 2 `02aa3f14`, Task 3 and this record: the last commit.

**Red run** (P0 `red` tests on the unchanged code; Task 1 with the new state fields added with defaults, Task 2 with an `emptyList()` stub):

- red: mapShortItem_qualityBadgeUhd_whenQualityIs2160 - expected UHD but was null
- red: mapShortItem_qualityBadgeHd_whenQualityIs1080 - expected HD but was null
- red: mapShortItem_qualityBadgeHd_whenQualityIs720 - expected HD but was null
- red: mapShortItem_originalTitleAndYear_whenTitleHasOriginalAndYear - expected Orig but was empty
- red: mapShortItem_originalOnly_whenYearNull - expected Orig but was empty
- red: mapShortItem_splitsLocalAndOriginalTitle_whenTitleHasSeparator - expected Рус but was empty
- red: mapShortItem_pillRatingsPubWithoutTrailingZero - expected PUB(10) but was []
- red (P1): leavesOriginalTitleEmpty, keepsBareSlashInLocalTitle, pillRatingsUseDotDecimal_underRussianLocale, pillRatingsOrderPubKpImdb, pillRatingsSkipMissingRatings - expected value but was default
- green on base: mapShortItem_qualityBadgeNull_whenQualityIs719, mapShortItem_qualityBadgeNull_whenQualityNull, mapShortItem_yearOnly_whenTitleHasNoOriginal, mapShortItem_noOriginalAndNoYear_whenBothMissing - the field default (null, "") equals the expected value; they guard the thresholds and the empty case
- guards green on base: mapShortItem_keepsRawTitle, mapShortItem_keepsYearText, buildRatings_keepsPubDecimalText
- red: posterCardImageUrls_startsWithTheStandTier - expected [medium, big] but was []
- red: posterCardImageUrls_neverContainsWideUrl - after adding the positive assertion `[medium, big, other]`; the first version (only `assertFalse(contains)`) passed vacuously on the stub
- red (P1): posterCardImageUrls_dropsBlankUrlsAndFallsBack - expected [a] but was []
- instrumented P0 red rows (`VideoItemPosterTest`): not run, no device by order; compiled only. The card file did not exist, so there is no base behaviour to run them against.

**Deviations from the plan:**

- `VideoItemPosterTest` imports no `assertExists` and checks the year bounds with left/right comparison (`Rect.contains` takes an `Offset`).
- Extra `private const val HD_STROKE_ALPHA`, `PillLineHeight`, `BadgeLineHeight`, `PillImdbIconWidth` in the card file, from the spec table values (line heights, IMDb 10 dp).

**Files touched outside the plan:** none.

**For the reviewers:**

- `VideoItemPoster.kt` against the stand numbers: Card focus frame and scale, FlowRow pills, the empty one-line original keeping the height.
- `QualityBadge` and `RatingPill` sizes (fixed `height`, no vertical padding) clip nothing at 7 / 8 sp; confirm on the TV.
- The instrumented tests (long select without click, ellipsis) were compiled only; qa runs them.
- `detektDevDebugUnitTest` prints "7 compiler errors found during analysis" yet the task is green; not caused by this diff (not checked at base).

**Review round 1 fixes** (triage-1 #1-#3, #6-#9, #4 part two):

| # | Fix |
|---|---|
| 1 | KP / IMDb pill text rounded to one decimal, `.0` dropped (`5.978` -> `6`) |
| 2 | Show all provides a `TvDialogFocusRestorer` like Favorites; test `showAllMenuOpenAndBackReturnsFocusToTheOpeningCard` in `ShowAllRailCloseFocusTest` (the same test without the rail passed on base, so it was dropped) |
| 3 | Ellipsis test asserts `!didOverflowWidth` and `width >= getLineRight(0)` for the year |
| 6 | `LOAD_MORE_THRESHOLD = 4 * SHOW_ALL_GRID_COLUMNS` |
| 7 | `PosterChipScrim` 0.62 -> 0.78; stand: `scrim` slider (start 78), README row |
| 8 | year <= 0 is no year; empty local part makes the original the title, no original line |
| 9 | comment on the Russian-locale test |

**Red run, review round 1** (unit, new tests on the unchanged mapper): red: mapShortItem_pillRatingsRoundKpAndImdbToOneDecimal, mapShortItem_pillRatingsKpAndImdbDropTrailingZero, mapShortItem_yearEmpty_whenYearIsZero, mapShortItem_usesOriginalAsLocalTitle_whenLocalPartEmpty - AssertionFailedError on each.

**Red run, instrumented** (box 192.168.1.110:5555, `make itest`, temporary uncommitted mutations of `VideoItemPoster.kt`, all reverted; first the unmutated class ran 7/7 and `ShowAllScreenContentFocusTest` 6/6 green):

- red: videoItemPoster_showsOriginalAndYearAsSeparateTexts - mutation: original and year in one Text; "Failed: assertExists"
- red: videoItemPoster_showsYearAlone_whenNoOriginalTitle - mutation: year-only line gets the " · " prefix; "Failed: assertExists"
- red: videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole - mutation: weight moved from the original to the year; "year overflows its width" (with the separate-text mutation it fails on assertExists)
- red: videoItemPoster_longSelectOpensContextMenuWithoutClick - mutation: `onTvContextMenuKey` removed; "expected:<1> but was:<0>"
- red: videoItemPoster_selectKeyUpInvokesClickOnce - mutation: `onSelectKeyClick { onClick(); onClick() }`; "expected:<1> but was:<2>"
- red: videoItemPoster_showsBadgesPillsAndBothTitles - mutation: UHD badge not rendered; "Failed: assertExists"
- red: videoItemPoster_showsNoBadgesAndNoOriginalTitle_whenStateHasNone - mutation: HD badge rendered when the quality is null; "Failed: assertDoesNotExist"
- red: showAllMenuOpenAndBackReturnsFocusToTheOpeningCard - base behaviour (restorer provider set to null); ComposeTimeoutException, focus did not return to the opening card
- the other five rail-close tests stay green with and without the restorer (guards)

**Review round 2 fix** (triage-2 #1): the load-more effect is keyed on `(shouldLoadMore, state.items.size)`, `LOAD_MORE_ROWS = 4` kept, so a page under 24 items that lands inside the threshold sends `LoadMore` again. Test `showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold` in `ShowAllScreenContentFocusTest`.

**Red run, review round 2** (box 192.168.1.110:5555, `make itest`): red: showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold - with the effect keyed on `shouldLoadMore` only, `ComposeTimeoutException: Condition still not satisfied after 5000 ms` at the wait after the append (no second `LoadMore`); green with the fix, and the class runs 6/6 green. The test waits for the first `LoadMore`, then for a count above the one before the append. A first draft that waited for exactly 1 and 2 timed out on the first wait even with the fix; it was replaced.

**Blocked:** none.

## Readiness

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

