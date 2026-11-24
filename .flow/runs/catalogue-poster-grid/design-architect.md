# Design (architect): catalogue-poster-grid

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/src/test`, `app/src/androidTest` or `docs/`. Line numbers are at ad2c7f0c. Visual values come from `docs/design/showall/README.md` "Starting numbers" (agreed, brief.md#D1) and, where the README table is silent, from the stand's `index.src.html` (`START` at :264-270, `badgeHTML` :490, `pillHTML` :513, `tileHTML` :525).

## Decisions

| Question | Decision | Why |
|---|---|---|
| New card or extend an existing one | New `VideoItemPoster` in `core/ui/uikit/component/moviesList/VideoItemPoster.kt`; `VideoItem` (`VideoItemUIState.kt:83`) and `VideoItemHorizontal` (`VideoItemHorizontal.kt:48`) untouched | AC-12: both are drawn by other screens; research Option A |
| Where the visual numbers live | `internal object VideoItemPosterDefaults` in the card file, values in the table "Card constants"; grid numbers as constants at `ShowAllScreenContent.kt:44` (table "Grid constants"); KDoc on each object links the stand README | One place per number |
| Where the colours live | Theme tokens of the TV `darkColorScheme` (`PuberTheme.kt:43`, defaults kept for these tokens) plus three new named colours in `core/ui/uikit/theme/Color.kt` after `ImdbYellow` (`:29`): `KinoPubGreen = Color(0xFF4CAF50)` (4K badge fill, KinoPub pill thumb), `OnKinoPubGreen = Color(0xFF0B1F0C)` (4K label), `PosterChipScrim = Color.Black.copy(alpha = 0.62f)` (badge and pill fill) | brief.md#D4, AC-6: no colour literal in the card |
| Card width | Tile fills its grid cell (`GridCells.Fixed(SHOW_ALL_GRID_COLUMNS)`), poster `aspectRatio(2f / 3f)` inside the tile padding | The column count alone sets the size, as on the web |
| Poster tier (AC-8) | `internal fun posterCardImageUrls(state): List<String>` in the card file: `imageUrl` (medium), then `bigImageUrl`, then `imageFallbackUrls`, minus `wideImageUrl`, blanks dropped, distinct | brief.md#D1 fixes `posters.medium`; pure function, unit-testable; wide never requested |
| Badge state (AC-2) | One new field `qualityBadge: VideoQualityBadge? = null`, `enum class VideoQualityBadge { HD, UHD }`; `mapShortItem` sets UHD for `quality >= 2160`, HD for `>= 720`, else null; thresholds as `private const` in `VideoItemUIMapper.kt` | Quality value only: README "Ground truth" shows every 4K-section item carries 2160; no section input |
| Titles (AC-4) | `title` stays raw; new fields `localTitle: String = ""`, `originalTitle: String = ""` from `internal fun splitItemTitle(raw): Pair<String, String>` in `VideoItemUIMapper.kt` (split once on `" / "`, trim each part; no separator gives `raw.trim()` and `""`); the card shows `localTitle.ifBlank { title }` | `title` feeds Home rows, search and the context menu (AC-12); the fallback keeps androidTest fixtures (`title = "Card N"`) findable |
| Year in the original line (AC-3) | No new year field: `VideoItemUIState.year` (`VideoItemUIState.kt:68`) is already filled by `mapShortItem` (`VideoItemUIMapper.kt:52`). The card file holds `internal fun originalLine(originalTitle, year): OriginalLine?` with `data class OriginalLine(val original: String, val yearText: String)`: ("Orig", " · 2020"), ("", "2020"), ("Orig", ""), null; separator `private const val YEAR_SEPARATOR = " · "` | brief.md#D3: original and year stay separate values so only the original ellipsizes |
| Original line layout | A `Row`: original `Text` with `Modifier.weight(1f, fill = false)`, `maxLines = 1`, `TextOverflow.Ellipsis`; `yearText` `Text` unweighted, `maxLines = 1`, `softWrap = false`. When `originalLine` is null an empty one-line `Text` keeps the reserved height | README "Agreed": title area reserved for 2 + 1 lines; the year never ellipsizes |
| KinoPub pill text (AC-5) | `buildRatings` (`VideoItemUIMapper.kt:114-122`) unchanged; new field `pillRatings: List<RatingUIState> = emptyList()` from new `buildPillRatings(item)`: order PUB, KP, IMDb; PUB text = `ratingPercentage / 10` without a trailing `.0` (100 to "10", 83 to "8.3") | "Other screens keep their rating text"; web order (Q3 answered) |
| Pill drawing | Private `RatingPill` in the card file: PUB draws Phosphor `com.adamglin.phosphoricons.fill.ThumbsUp` (class `ThumbsupKt` in phosphor-icon-android 1.0.0) tinted `KinoPubGreen`; KP and IMDb use `R.drawable.ic_kinopoisk` / `ic_imdb` tinted `KinopoiskOrange` / `ImdbYellow` as `RatingUIState.kt:52` does; `FlowRow` with `maxItemsInEachRow = 2`, centred | `Rating` (`RatingUIState.kt:62`) is a bare row with the `ic_kinopub` raster; restyling it changes every screen |
| Focus frame and scale (AC-9) | Tile is one `androidx.tv.material3.Card`: `CardDefaults.shape(RoundedCornerShape(TileCorner))`, `CardDefaults.colors` container `surfaceVariant` at `TileFillAlpha` (focused the same), `CardDefaults.border(border = 1 dp `border` at 22 %, focusedBorder = 2 dp `colorScheme.border`)`, `CardDefaults.scale(focusedScale = FocusScale)`, `CardDefaults.glow` none | The stand scales the whole tile (`index.src.html:582`); the Card animates scale in its own layer and stays the single focus target `ShowAllScreenContent` attaches its requester and `onFocusChanged` to |
| Select and long-press | Card applies `onTvContextMenuKey` (`TvContextMenu.kt:319`) and the key-up select of `VideoItemHorizontal.kt:193`; that private `onSelectKeyClick` becomes `internal`, body unchanged | Same press semantics, so long-press keeps opening the menu (AC-9) |
| Watched marks | Reuse `WatchedIndicatorBadge` (`VideoItemUIState.kt:345`) top end and `WatchProgressBar` (`:325`, 3 dp) at the poster foot; unwatched counter via `TopEndBadge` (`:295`, private), which becomes `internal` | brief "Assumed"; the stand draws them with today's sizes |
| Scroll | No `BringIntoViewSpec` change | README "Open items" 3 judges edge vs pivot on the TV after the port |
| Focus memory | `rememberGridFocusMemory`, `GridFocusMemory`, `resolveRestoreId` (`ShowAllScreenContent.kt:152-258`) unchanged; only the composable at `:117` and the grid numbers at `:44, :111-113` change | They key on id and list index only (`:254-258`) |

### Grid constants (`ShowAllScreenContent.kt`)

| Constant | Value |
|---|---|
| `SHOW_ALL_GRID_COLUMNS` (`internal const`, replaces `GRID_COLUMNS` at `:44`) | 6 |
| `GridContentPadding` (`:111`) | 16 dp all sides |
| `GridColumnSpacing` (`:112`) | 12 dp |
| `GridRowSpacing` (`:113`) | 16 dp |

### Card constants (`VideoItemPosterDefaults`)

| Constant | Value | Colour |
|---|---|---|
| `TilePadding` | 5 dp | |
| `TileCorner` / `PosterCorner` | 10 dp / 7 dp | |
| `TileFillAlpha` | 0.14 | `surfaceVariant` |
| `TileBorderWidth` / `TileBorderAlpha` | 1 dp / 0.22 | `border` |
| `FocusBorderWidth` | 2 dp | `border` |
| `FocusScale` | 1.05f | |
| `BadgeHeight` / `BadgeFontSize` / `BadgeSpacing` | 11 dp / 7 sp (line 11 sp, bold, `labelSmall` tracking) / 3 dp | HD: `PosterChipScrim`, label `onSurface`, 1 dp inner stroke `onSurface` at 0.28; 4K: `KinoPubGreen`, label `OnKinoPubGreen`, no stroke |
| `BadgePaddingHorizontal` / `BadgeInset` / `BadgeCorner` | 4 dp / 5 dp / 3 dp | |
| `PillHeight` / `PillFontSize` / `PillIconSize` / `PillSpacing` / `PillInset` | 13 dp / 8 sp (line 13 sp, weight 500, `bodySmall` tracking) / 8 dp (IMDb 10 x 8 dp) / 3 dp / 5 dp from the poster foot | `PosterChipScrim` fill, text `onSurface` |
| `PillPaddingHorizontal` / `PillIconGap` / `PillRowPaddingHorizontal` | 4.55 dp / 2.6 dp / 2 dp (stand: 0.35 and 0.2 x pill height) | |
| `TitleFontSize` / `TitleLineHeight` | 10 sp / 12 sp, weight 500, `titleSmall` tracking, `minLines = 2`, `maxLines = 2`, ellipsis | `onSurface` |
| `OriginalFontSize` / `OriginalLineHeight` | 8 sp / 10 sp, `bodySmall` tracking, 1 line | `onSurfaceVariant` |
| `PosterToTitle` / `TitleToOriginal` | 5 dp / 1 dp | |

## Changes by layer

### API / repository

N/A: `Models.kt` and `ContentCacheRepository` are unchanged (brief.md#D2, AC-6).

### ViewModel state and mapping

| File | Change |
|---|---|
| `core/ui/uikit/component/moviesList/VideoItemUIState.kt:50-71` | Fields `qualityBadge`, `localTitle`, `originalTitle`, `pillRatings`, all defaulted; `VideoQualityBadge` enum next to `VideoItemPresentation` (`:73`) |
| `core/ui/model/VideoItemUIMapper.kt:29-54` | `mapShortItem` fills the new fields: quality per thresholds, titles via `splitItemTitle`, `pillRatings = buildPillRatings(item)`; `year` (`:52`) as today |
| `core/ui/model/VideoItemUIMapper.kt:114-122` | Untouched; `buildPillRatings` added beside it, reusing `isValidRating` (`:124`) and `RATING_PERCENT_TO_TEN_SCALE` (`:17`) |

`ShowAllVM` and `ContentListPagingVM.mapItems` (`ContentListPagingVM.kt:349`) need no change.

### Screen and DI module

N/A: `ShowAllScreen.kt` already scopes `VideoItemUIMapper` and `ShowAllVM`.

### Compose content

| File | Change |
|---|---|
| `core/ui/uikit/theme/Color.kt:29` | `KinoPubGreen`, `OnKinoPubGreen`, `PosterChipScrim` after `ImdbYellow` |
| `core/ui/uikit/component/moviesList/VideoItemPoster.kt` (new) | `fun VideoItemPoster(state, onClick, modifier = Modifier, onContextMenu: (() -> Unit)? = null)`; `VideoItemPosterDefaults`; `posterCardImageUrls`; `originalLine` / `OriginalLine`; private poster image with fallback walk (as `CardPoster`, `VideoItemUIState.kt:174`, fed by `posterCardImageUrls`); quality badge top start; `RatingPill` `FlowRow` at the poster foot; title and original line under the poster; previews (4K, HD, no badge, no original, long original with year) |
| `core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:193` | `onSelectKeyClick` private to internal |
| `core/ui/uikit/component/moviesList/VideoItemUIState.kt:295` | `TopEndBadge` private to internal |
| `ui/feature/showall/content/ShowAllScreenContent.kt:44, :105, :111-113` | Grid constants above |
| `ui/feature/showall/content/ShowAllScreenContent.kt:117-130` | `VideoItemHorizontal` to `VideoItemPoster`, same modifier chain, `onClick`, `onContextMenu` |

### Resources

| File | Change |
|---|---|
| `res/values/strings.xml` | Badge labels `HD`, `4K` with `translatable="false"` |

### Tests

| File | Change | AC |
|---|---|---|
| `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt` | Quality 2160 / 1080 / 720 / 719 / null; title with and without `" / "` (`localTitle`, `originalTitle`, `title` unchanged); `originalTitle` and `year` for the four AC-3 inputs; PUB pill 100 and 83; `ratings` PUB still "10.0" for 100 | AC-2, AC-3, AC-4, AC-5 |
| `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterTest.kt` (new) | `posterCardImageUrls` puts medium first and never returns `wideImageUrl`; `originalLine` gives "Orig" + " · 2020", "" + "2020", "Orig" + "", null | AC-3, AC-8 |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt:197-199` | `GridColumns` reads `SHOW_ALL_GRID_COLUMNS`; `ItemCount` derived from it so `DeepRows` rows exist (24 items are 4 rows at 6 columns; `:79` then fails) | AC-10 |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt:195-198` | `ItemCount` derived from `SHOW_ALL_GRID_COLUMNS` so `ScrollRows` rows exist | AC-10, AC-11 |

## Alternatives rejected

- Parameterise `VideoItem` with badges and an under-poster caption: fixed 120 x 180 dp (`PuberTheme.kt:14-15`) and three other callers (research Option B).
- Change `title` to the Russian part in `mapShortItem`: changes Home, search and catalogue cards (AC-12).
- Change PUB formatting in `buildRatings`: changes Home, hero and details text (AC-5).
- One joined "Orig · 2020" string in the state: a single `Text` ellipsizes the year away (brief.md#D3).
- Poster-only lift through `graphicsLayer`: the stand scales the whole tile, which `CardDefaults.scale` already does.
- Section-driven 4K flag through `ContentListPagingVM.mapItems`: ground truth shows `quality` = 2160 is enough.

## Risks

| Risk | Kind | Where | Guard |
|---|---|---|---|
| Focus restore and rail handoff break with the new tile: the focus target must stay the tile, not an inner node | focus | `ShowAllScreenContent.kt:117-127`, card root | Card is the only focusable; AC-10/AC-11 tests via `make DEVICE=<serial> itest` |
| Both androidTests fail for fixture reasons at 6 columns, masking a real regression | focus / test | `ShowAllScreenContentFocusTest.kt:197-199`, `ShowAllRailCloseFocusTest.kt:195-198` | Counts derived from `SHOW_ALL_GRID_COLUMNS` |
| Year cut when the original is long: `weight(1f)` with `fill = true` or a joined string pushes the year out | correctness | original line `Row` in `VideoItemPoster.kt` | `fill = false` on the original only; preview with a long original; device check under AC-7 |
| Rows of uneven height when a title wraps or an original is absent | layout | title / original `Text` in `VideoItemPoster.kt` | `minLines = 2` and the empty reserved original line |
| Title split wrong for a bare `/` (`Fate/Zero`) or `" / "` inside the Russian part | correctness | `splitItemTitle` in `VideoItemUIMapper.kt` | Split once on `" / "` only |
| Sub-10 sp text (badge 7 sp, original 8 sp, pill 8 sp) unreadable at TV distance | legibility | `VideoItemPosterDefaults` | Owner judges on the box (AC-7) |
| Show all is not in the baseline profile; the new card JITs on first open | performance | `baselineprofile/` (no Show all journey) | Same as today for `VideoItemHorizontal` in Show all |

## Open questions

None: Q1-Q3 answered on recommendation (`localTitle` / `originalTitle` with raw `title`; quality value only; web pill order).
