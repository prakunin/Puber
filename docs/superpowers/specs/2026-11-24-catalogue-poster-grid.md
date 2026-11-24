# Vertical poster card with badges and ratings for the Show all grid

Status: approved
Date: 2026-11-24
Slug: catalogue-poster-grid
Kind: feature
Size: M
Tracks: ui

## Problem / Why

Rework the Show all grid after the KinoPub web grid (`reference-web-grid.png`): 2:3 poster from `posters.medium/big` instead of the wide poster, badges CC / AC3 / HD / 4K top start, rating pills (KinoPub likes, Kinopoisk, IMDb) at the poster foot, Russian title and original title under the poster, framed tile that marks focus. Order the owner set: design stand through `designing-a-screen` first, then the Compose port. Part 1 of 3 (then catalogue-browse-all, catalogue-filters).

Owner decisions 2026-11-24 after the stand (`docs/design/showall/README.md`, "Agreed" paragraph, in the worktree):

| # | Decision |
|---|---|
| D1 | The stand's starting numbers (README "Starting numbers": 6 columns, `posters.medium`, sizes, focus frame #938F99) are the spec values |
| D2 | Only HD / 4K badges ship. CC and AC3 move to run `catalogue-card-audio-subtitle-badges`; `Item.subtitles` and its `mergeSparse` line are not added |
| D3 | The original-title line carries the release year: "Orig · 2020", the year alone when there is no original title; the original ellipsizes, the year never does |
| D4 | The 4K green #4CAF50 becomes a named colour in `Color.kt` beside `KinopoiskOrange` / `ImdbYellow` |



Current state, badge sources, poster sizes and options: `research.md: Current state in code`, `Badge and rating sources`, `Poster sizes (measured)`, `Options`. This brief takes Option A. Checked and corrected against the code at ad2c7f0c:

| Where | Fact |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:44` | `GRID_COLUMNS = 3` (research cites :108) |
| `ShowAllScreenContent.kt:104-130` | `LazyVerticalGrid`, padding 16 dp, gaps 16/32 dp, `VideoItemHorizontal` at :117 with restore requester and `onCardFocused` |
| `ShowAllScreenContent.kt:152-251, 254` | `rememberGridFocusMemory`, `GridFocusMemory`, `resolveRestoreId`: card-agnostic (id + index), stay as they are |
| `app/src/main/java/com/kino/puber/data/api/models/Models.kt:60-61, 66, 69, 73` | `Item.quality`, `ac3`, `imdbRating`, `kinopoiskRating`, `ratingPercentage`; no item-level `subtitles` (only `List<SubtitleLink>` on files, :285, :433, :558) |
| `Models.kt:50` | `Item.year: Int?`; `VideoItemUIMapper.kt:52, 67, 95` already map it to `VideoItemUIState.year` as a string |
| `app/src/main/java/com/kino/puber/core/ui/uikit/theme/Color.kt:28-29` | `KinopoiskOrange`, `ImdbYellow`; no 4K green yet |
| `app/src/main/java/com/kino/puber/core/ui/model/VideoItemUIMapper.kt:33-54` | `mapShortItem` passes `item.title` raw ("Рус / Orig") |
| `VideoItemUIMapper.kt:114-122` | `buildRatings`: KP, IMDb, PUB; PUB = `ratingPercentage / 10` via `Float.toString()` ("10.0") |
| `VideoItemUIMapper.kt:148-150` | private `formatTitle()` splits on "/" (hero only) |
| `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:84-89` | URL order wide, big, medium |
| `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemUIState.kt:49-71` | `VideoItemUIState`: no badge or original-title fields; shared by every card mapper |
| `docs/design/` | stands: catalogue, content, player-controls; none for Show all |

Reference image, read off: pills wrap two per row; an item with one rating shows one pill; no original-title line when the title has no "/" (Каменская); a long Russian title wraps to two lines and the original title ellipsizes to one; the focused tile gets a green outline and its poster lifts.

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | the run | the stand step ends | `docs/design/showall/` holds `index.src.html`, the built page and a README of agreed numbers (columns, tile, poster, badge, pill, title sizes, focus frame), made per `.claude/skills/designing-a-screen/SKILL.md` and approved by the owner before the port starts (owner judges) |
| AC-2 | an `Item` with `quality` >= 2160 (or a 4K section), with 720..2159, and below 720 or null | mapped by `VideoItemUIMapper.mapShortItem` | quality badge is 4K, HD, none respectively (unit test) |
| AC-3 | an `Item` with title "Рус / Orig" and year 2020; "Рус" and 2020; "Рус / Orig" and null year; "Рус" and null year | mapped by `mapShortItem` | original-title line "Orig · 2020"; "2020"; "Orig"; no line (unit test on the mapper output; original and year stay separate values so the card can ellipsize only the original) |
| AC-4 | title "Рус / Orig", and title "Рус" | mapped | title "Рус" with original "Orig"; title "Рус" with empty original (unit test) |
| AC-5 | `ratingPercentage` 100 and 83 | mapped | KinoPub pill text "10" and "8.3" (unit test); other screens keep their rating text unchanged |
| AC-6 | the change | reviewed | the 4K badge colour is a named constant in `Color.kt`, no colour literal in the card; `Models.kt` and `mergeSparse` unchanged; no CC / AC3 badge (diff observation) |
| AC-7 | Show all open on the TV box | the grid draws | each card is the new poster card in 6 columns, HD / 4K badge top start, pills at the poster foot, titles under it, a long original ellipsized with the year still whole, matching the stand (owner judges) |
| AC-8 | Show all open | the grid loads | card image requests carry `posters.medium` (D1), never `posters.wide` (unit test on the card URL order, or Coil request log on device) |
| AC-9 | a card focused with D-pad | focus moves between cards | the focused tile shows the stand's focus frame, the previous loses it; long-press / menu key still opens `VideoItemContextMenuDialog` |
| AC-10 | focus on a card in row 2+, column > 1 | open details, press Back | focus returns to the same card (`ShowAllScreenContentFocusTest` and `ShowAllRailCloseFocusTest` pass via `make DEVICE=<serial> itest`) |
| AC-11 | the side rail opened from Show all | the rail closes | focus lands on the previously focused card (`ShowAllRailCloseFocusTest`) |
| AC-12 | Home rows, catalogue rows, favourites, schedule, player episode panel | opened after the change | look and behave as before (card types and their URL order unchanged) |

## Out of scope

CC and AC3 badges (run catalogue-card-audio-subtitle-badges); browsing a whole content type (catalogue-browse-all); filters (catalogue-filters); the wide-poster decode cost on Home and catalogue rows (card-image-size-tier).

## Design

### Architect


Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/src/test`, `app/src/androidTest` or `docs/`. Line numbers are at ad2c7f0c. Visual values come from `docs/design/showall/README.md` "Starting numbers" (agreed, brief.md#D1) and, where the README table is silent, from the stand's `index.src.html` (`START` at :264-270, `badgeHTML` :490, `pillHTML` :513, `tileHTML` :525).

#### Decisions

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

##### Grid constants (`ShowAllScreenContent.kt`)

| Constant | Value |
|---|---|
| `SHOW_ALL_GRID_COLUMNS` (`internal const`, replaces `GRID_COLUMNS` at `:44`) | 6 |
| `GridContentPadding` (`:111`) | 16 dp all sides |
| `GridColumnSpacing` (`:112`) | 12 dp |
| `GridRowSpacing` (`:113`) | 16 dp |

##### Card constants (`VideoItemPosterDefaults`)

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

#### Changes by layer

##### API / repository

N/A: `Models.kt` and `ContentCacheRepository` are unchanged (brief.md#D2, AC-6).

##### ViewModel state and mapping

| File | Change |
|---|---|
| `core/ui/uikit/component/moviesList/VideoItemUIState.kt:50-71` | Fields `qualityBadge`, `localTitle`, `originalTitle`, `pillRatings`, all defaulted; `VideoQualityBadge` enum next to `VideoItemPresentation` (`:73`) |
| `core/ui/model/VideoItemUIMapper.kt:29-54` | `mapShortItem` fills the new fields: quality per thresholds, titles via `splitItemTitle`, `pillRatings = buildPillRatings(item)`; `year` (`:52`) as today |
| `core/ui/model/VideoItemUIMapper.kt:114-122` | Untouched; `buildPillRatings` added beside it, reusing `isValidRating` (`:124`) and `RATING_PERCENT_TO_TEN_SCALE` (`:17`) |

`ShowAllVM` and `ContentListPagingVM.mapItems` (`ContentListPagingVM.kt:349`) need no change.

##### Screen and DI module

N/A: `ShowAllScreen.kt` already scopes `VideoItemUIMapper` and `ShowAllVM`.

##### Compose content

| File | Change |
|---|---|
| `core/ui/uikit/theme/Color.kt:29` | `KinoPubGreen`, `OnKinoPubGreen`, `PosterChipScrim` after `ImdbYellow` |
| `core/ui/uikit/component/moviesList/VideoItemPoster.kt` (new) | `fun VideoItemPoster(state, onClick, modifier = Modifier, onContextMenu: (() -> Unit)? = null)`; `VideoItemPosterDefaults`; `posterCardImageUrls`; `originalLine` / `OriginalLine`; private poster image with fallback walk (as `CardPoster`, `VideoItemUIState.kt:174`, fed by `posterCardImageUrls`); quality badge top start; `RatingPill` `FlowRow` at the poster foot; title and original line under the poster; previews (4K, HD, no badge, no original, long original with year) |
| `core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:193` | `onSelectKeyClick` private to internal |
| `core/ui/uikit/component/moviesList/VideoItemUIState.kt:295` | `TopEndBadge` private to internal |
| `ui/feature/showall/content/ShowAllScreenContent.kt:44, :105, :111-113` | Grid constants above |
| `ui/feature/showall/content/ShowAllScreenContent.kt:117-130` | `VideoItemHorizontal` to `VideoItemPoster`, same modifier chain, `onClick`, `onContextMenu` |

##### Resources

| File | Change |
|---|---|
| `res/values/strings.xml` | Badge labels `HD`, `4K` with `translatable="false"` |

##### Tests

| File | Change | AC |
|---|---|---|
| `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt` | Quality 2160 / 1080 / 720 / 719 / null; title with and without `" / "` (`localTitle`, `originalTitle`, `title` unchanged); `originalTitle` and `year` for the four AC-3 inputs; PUB pill 100 and 83; `ratings` PUB still "10.0" for 100 | AC-2, AC-3, AC-4, AC-5 |
| `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterTest.kt` (new) | `posterCardImageUrls` puts medium first and never returns `wideImageUrl`; `originalLine` gives "Orig" + " · 2020", "" + "2020", "Orig" + "", null | AC-3, AC-8 |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt:197-199` | `GridColumns` reads `SHOW_ALL_GRID_COLUMNS`; `ItemCount` derived from it so `DeepRows` rows exist (24 items are 4 rows at 6 columns; `:79` then fails) | AC-10 |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt:195-198` | `ItemCount` derived from `SHOW_ALL_GRID_COLUMNS` so `ScrollRows` rows exist | AC-10, AC-11 |

#### Alternatives rejected

- Parameterise `VideoItem` with badges and an under-poster caption: fixed 120 x 180 dp (`PuberTheme.kt:14-15`) and three other callers (research Option B).
- Change `title` to the Russian part in `mapShortItem`: changes Home, search and catalogue cards (AC-12).
- Change PUB formatting in `buildRatings`: changes Home, hero and details text (AC-5).
- One joined "Orig · 2020" string in the state: a single `Text` ellipsizes the year away (brief.md#D3).
- Poster-only lift through `graphicsLayer`: the stand scales the whole tile, which `CardDefaults.scale` already does.
- Section-driven 4K flag through `ContentListPagingVM.mapItems`: ground truth shows `quality` = 2160 is enough.

#### Risks

| Risk | Kind | Where | Guard |
|---|---|---|---|
| Focus restore and rail handoff break with the new tile: the focus target must stay the tile, not an inner node | focus | `ShowAllScreenContent.kt:117-127`, card root | Card is the only focusable; AC-10/AC-11 tests via `make DEVICE=<serial> itest` |
| Both androidTests fail for fixture reasons at 6 columns, masking a real regression | focus / test | `ShowAllScreenContentFocusTest.kt:197-199`, `ShowAllRailCloseFocusTest.kt:195-198` | Counts derived from `SHOW_ALL_GRID_COLUMNS` |
| Year cut when the original is long: `weight(1f)` with `fill = true` or a joined string pushes the year out | correctness | original line `Row` in `VideoItemPoster.kt` | `fill = false` on the original only; preview with a long original; device check under AC-7 |
| Rows of uneven height when a title wraps or an original is absent | layout | title / original `Text` in `VideoItemPoster.kt` | `minLines = 2` and the empty reserved original line |
| Title split wrong for a bare `/` (`Fate/Zero`) or `" / "` inside the Russian part | correctness | `splitItemTitle` in `VideoItemUIMapper.kt` | Split once on `" / "` only |
| Sub-10 sp text (badge 7 sp, original 8 sp, pill 8 sp) unreadable at TV distance | legibility | `VideoItemPosterDefaults` | Owner judges on the box (AC-7) |
| Show all is not in the baseline profile; the new card JITs on first open | performance | `baselineprofile/` (no Show all journey) | Same as today for `VideoItemHorizontal` in Show all |

#### Open questions

None: Q1-Q3 answered on recommendation (`localTitle` / `originalTitle` with raw `title`; quality value only; web pill order).


### UX (design delta before the stand; numbers superseded by docs/design/showall/README.md "Agreed")


#### Governing stand

**None.** No `docs/design/<screen>/` covers Show all (`docs/design/` holds catalogue, content, player-controls; `docs/design/showall/` does not exist at ad2c7f0c in main or the worktree). `docs/design/catalogue/README.md` governs the catalogue tab rows only. The port must not start until `docs/design/showall/` exists with an owner-approved README (brief.md#AC-1). Until then every number below is a stand starting value, not an agreed one.

#### Ground truth the stand must take (designing-a-screen step 1-2)

| What | Source | Why |
|---|---|---|
| Frame of today's Show all grid, 3 columns of `VideoItemHorizontal` | `screencap` on 192.168.1.110:5555, overlays off/on | the "было" column and the real content width |
| Closed rail width | same frame, measured in px / 2 | `MainScreenComponent.kt:304` hardcodes 60 dp; catalogue README says no measurement backs it; the column maths depends on it |
| Items with real `quality`, `ac3`, `subtitles`, `rating_percentage`, KP, IMDb, title with and without "/" | `databases/puber.db` `cached_payload` on the box | badges and pills from data, not placeholders; settles the `subtitles` type (brief.md Risks, Q2) and the 4K `quality` value |
| Posters | `posters.medium` and `posters.big` of the same items | the stand shows the tier the code will load |
| At least: one item with one rating (one pill), one with no "/" in the title, one long Russian title (two lines), one 4K, one watched (eye), one part-watched (progress bar), one serial with an unwatched count | cache | each card state the grid can draw |

#### Starting geometry (stand opens on these)

Content width at density 2.0: 960 dp screen - 60 dp closed rail = 900 dp (to be replaced by the measured rail).

| Parameter | Today (`ShowAllScreenContent.kt`) | Stand start |
|---|---|---|
| columns | 3 (`GRID_COLUMNS`) | 6 (web) |
| grid edge padding | 16 dp all sides | 16 dp |
| gap between columns / rows | 16 / 32 dp | 12 / 12 dp |
| tile width | derived | derived: (900 - 2 x edge - 5 x gap) / columns, ~134.7 dp at the start values |
| tile inner padding | n/a | 5 dp |
| poster | 16:9 wide, 150 dp high (`PuberTheme.Defaults.HorizontalVideoItemHeight`) | 2:3, tile width - 2 x padding, ~124.7 x 187 dp |
| image tier | wide, big, medium (`VideoItemHorizontal.kt:89`) | medium if poster <= 125 dp (250 px) wide, else big (brief.md Q1); stand readout names it |

Smaller than the web on purpose (skill default "smaller than feels right"); the 16 dp edge is inside the 48 dp TV safe zone, as in the catalogue and content stands.

#### What the stand must let the owner dial

Every row is a slider or switch with a readout of the value that applied (clamped values shown as clamped).

| Group | Controls |
|---|---|
| Grid | columns (4-8); edge padding; column gap; row gap; readouts: tile width dp, poster px, chosen tier (medium/big), rows visible on 540 dp |
| Tile at rest | fill colour/alpha on `OledBackground`; frame width, colour, alpha (web: thin grey frame, 0 means none); corner radius; inner padding |
| Tile focused | frame width and colour (switch: `colorScheme.border` `#938F99`, `primary` `Purple80`, web green); poster lift dp (translation up) or scale; tile fill change on focus |
| Poster | corner radius; foot scrim height and alpha behind the pills (0 = none, as on the web) |
| Badges (CC, AC3, HD, 4K) | height; type size; horizontal padding; gap; corner; inset from poster top/start; plain fill/outline colour; 4K accent fill (web green or theme) |
| Rating pills | height; type size; icon size; inner padding; gap; row gap; pills per row (2 web, 3 one row); alignment (centre web, start); inset from poster foot; fill alpha; KinoPub mark (thumbs-up vector, web) |
| Watched marks | eye badge at top end and its inset (today 6 dp padding + 4 dp, 16 dp icon); progress bar 3 dp at poster foot, and the pills' clearance above it |
| Titles | poster -> title gap; title size, line height, weight, max lines (2); original title size, line height, opacity, max lines (1); title -> original gap |
| Text block height | switch: reserved for 2 + 1 lines (pinned, tiles in a row equal) or content height (web) |

Switches the stand also needs: card with a single rating; title without "/"; long title; 4K vs HD vs none; focus walked by arrow keys across rows, including column 1 to the rail.

#### Focus order and visibility

| Element | Focusable | Order / D-pad | Focus visibility |
|---|---|---|---|
| Poster card (tile) | yes, one stop per card | row-major; Left/Right within a row, Up/Down within a column; Left from column 1 hands off to the rail (`LocalContentFocusHandoff`), Right from the rail returns to the remembered card (`GridFocusMemory`, unchanged) | the dialled focus frame plus lift; the tile at rest must read as unfocused at arm's length (rest frame visibly weaker than focus frame) |
| Badges, pills, titles, eye, progress bar | no | - | - |

- Focus frame and lift must not be clipped: the top row needs edge padding >= lift + frame width; the stand shows the first row focused.
- `Card` focused scale stays 1 unless the owner dials scale instead of lift (today `CardDefaults.scale(pressedScale = 1f, focusedScale = 1f)`, `VideoItemHorizontal.kt:77`).
- Long-press / menu key opens `VideoItemContextMenuDialog` as today (`onTvContextMenuKey`); select on key-up (`onSelectKeyClick`) carries over to the new card.

#### Shared constants: parameterise, never retune in place

| Constant / component | Shared with | Rule for this change |
|---|---|---|
| `VideoItemUIState` (`VideoItemUIState.kt:49-71`) | every card mapper | new fields (quality badge, ac3, subtitles, original title) get defaults that render nothing |
| `VideoItemUIMapper.buildRatings` PUB text (`VideoItemUIMapper.kt:114-122`) | Home, catalogue, details, favourites | "10" / "8.3" format only for the poster card (brief.md#AC-5) |
| `Rating` (`RatingUIState.kt`: 14 dp icon, `bodySmall`, raster `ic_kinopub`) | all rating rows | pills are a new composable or new parameters; do not change the existing defaults; never tint `ic_kinopub` |
| `PuberTheme.Defaults.VideoItemWidth/Height` (120 x 180) | favourites, schedule, player episodes (`VideoItem`) | not reused for the poster card; card size comes from the grid cell |
| `VideoItemHorizontal` | ten screens | left unchanged; Show all stops calling it |
| `Type.kt` styles (`titleSmall`, `labelSmall`, `bodySmall`) | app-wide | new named styles for badge, pill, title, original title |
| `WatchedIndicatorBadge`, `WatchProgressBar` | `VideoItem`, `VideoItemHorizontal` | reuse as is; a size change becomes a parameter |
| `GRID_COLUMNS`, grid padding and gaps (`ShowAllScreenContent.kt`) | Show all only (catalogue-browse-all will reuse) | retune in place to the stand; name them so part 2 takes the same values |

#### Open (to be closed on the stand)

All numbers above; the image tier; the focus colour; whether tiles in a row share a height; the real closed-rail width.


## Test design


Inputs: `brief.md` (ACs, risks, owner decisions D1-D4), `design-architect.md` (names, files), `design-ux.md` (stand controls). Architect Q1 takes its recommended answer. Levels per `.flow/roles/test-designer.md`; `instrumented` runs only through `make DEVICE=<serial> itest [TESTS=<fqcn>]`; device steps on the TV box `192.168.1.110:5555`, package `com.kino.puber.stage` only.

#### Risk profile

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

#### Traceability

Unit tests: `app/src/test/kotlin/com/kino/puber/core/ui/model/VideoItemUIMapperTest.kt` (extend `testItem` with `quality`, `year`), `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterUrlsTest.kt` (new). Instrumented: `app/src/androidTest/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPosterTest.kt` (new, style of `TvContextMenuLongPressTest`), the two Show all focus tests. The original-title line is asserted as two values: mapper fields `originalTitle` and `year` (unit), and their rendering as separate text nodes (instrumented).

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | device | P0 | M1 (owner approves `docs/design/showall/README.md` before the port) | port started without agreed numbers |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeUhd_whenQualityIs2160` | fails if the UHD threshold is missing or above 2160 |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeHd_whenQualityIs1080` | fails if 1080 maps to none or UHD |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeHd_whenQualityIs720` | fails on an off-by-one at the HD lower bound (`> 720`) |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeNull_whenQualityIs719` | fails if the HD bound is lowered |
| AC-2 | unit | P0 red | `mapShortItem_qualityBadgeNull_whenQualityNull` | fails if null defaults to a badge |
| AC-2 | unit | P1 | `mapShortItem_qualityBadgeUhd_whenSectionIs4kAndQualityBelow2160` | fails if the 4K-section rule is ignored |
| AC-3 | unit | P0 red | `mapShortItem_originalTitleAndYear_whenTitleHasOriginalAndYear` | "Рус / Orig", 2020 gives `originalTitle` "Orig", `year` "2020"; fails if the year is folded into `originalTitle` (one value cannot keep the year whole) |
| AC-3 | unit | P0 red | `mapShortItem_yearOnly_whenTitleHasNoOriginal` | "Рус", 2020 gives "" and "2020"; fails if the year is dropped with the missing original |
| AC-3 | unit | P0 red | `mapShortItem_originalOnly_whenYearNull` | "Рус / Orig", null gives "Orig" and ""; fails on "null" or a stray separator in either value |
| AC-3 | unit | P0 red | `mapShortItem_noOriginalAndNoYear_whenBothMissing` | "Рус", null gives "" and ""; fails if either value is non-blank |
| AC-3 | instrumented | P0 red | `videoItemPoster_showsOriginalAndYearAsSeparateTexts` | text nodes "Orig" and "· 2020"; fails if the line is one node or the separator is missing |
| AC-3 | instrumented | P0 red | `videoItemPoster_showsYearAlone_whenNoOriginalTitle` | node "2020" and no node containing "·"; fails on a leading "· 2020" |
| AC-3, AC-7 | instrumented | P0 red | `videoItemPoster_ellipsizesLongOriginalAndKeepsYearWhole` | 80-char original, year 2020: `GetTextLayoutResult` of the original node has `isLineEllipsized(0)`, the year node does not and shows "· 2020" within the tile bounds; fails on one shared `Text` or a weighted year |
| AC-4 | unit | P0 red | `mapShortItem_splitsLocalAndOriginalTitle_whenTitleHasSeparator` | "Рус / Orig" gives `localTitle` "Рус", `originalTitle` "Orig"; fails without trim or split |
| AC-4 | unit | P0 red | `mapShortItem_leavesOriginalTitleEmpty_whenTitleHasNoSeparator` | "Рус" gives "Рус" and ""; fails if the original repeats the title |
| AC-4 | unit | P1 | `mapShortItem_keepsBareSlashInLocalTitle` | "Fate/Zero" stays whole; fails if the split is on "/" instead of " / " |
| AC-4, AC-12 | unit | P0 guard | `mapShortItem_keepsRawTitle` | `title` stays "Рус / Orig"; fails if `title` is rewritten for every card |
| AC-3, AC-12 | unit | P0 guard | `mapShortItem_keepsYearText` | `year` stays "2020" with no separator; fails if `year` is reformatted for every card (`CardYear`, `VideoItemUIState.kt:264`) |
| AC-5 | unit | P0 red | `mapShortItem_pillRatingsPubWithoutTrailingZero` (100, 83) | "10" and "8.3"; fails on `Float.toString()` ("10.0") |
| AC-5 | unit | P1 | `mapShortItem_pillRatingsUseDotDecimal_underRussianLocale` | sets `Locale("ru")`, restores after; fails on `String.format` with the default locale |
| AC-5 | unit | P1 | `mapShortItem_pillRatingsOrderPubKpImdb` | fails if pills reuse the `buildRatings` order |
| AC-5 | unit | P1 | `mapShortItem_pillRatingsSkipMissingRatings` | one rating gives one pill; fails if blank or zero ratings make pills |
| AC-5, AC-12 | unit | P0 guard | `buildRatings_keepsPubDecimalText` (100 to "10.0", 83 to "8.3") | fails if the shared PUB text changes for Home, hero and details |
| AC-6 | review | P0 | R-check below | colour literal in the card; `Models.kt` or `mergeSparse` touched; CC / AC3 state or strings added |
| AC-6 | unit | P0 guard | `everyFieldOfAnItemSurvivesAMergeOverASparseRecord`, `theFixtureSetsEveryFieldItemDeclares` (`ItemMergeCoverageTest.kt`, unchanged) | fails if an `Item` field is added without its merge line |
| AC-7 | device | P0 | M1, M5 | grid differs from the stand; year cut off |
| AC-8 | unit | P0 red | `posterCardImageUrls_startsWithTheStandTier` | first URL is `mediumImageUrl` (D1), then big; fails on the `VideoItemHorizontal` order |
| AC-8 | unit | P0 red | `posterCardImageUrls_neverContainsWideUrl` | wide also placed in `imageFallbackUrls`; fails if wide is only dropped from the head |
| AC-8 | unit | P1 | `posterCardImageUrls_dropsBlankUrlsAndFallsBack` | blank medium and big fall through to `imageFallbackUrls`; fails on an empty first URL |
| AC-9 | instrumented | P0 red | `videoItemPoster_longSelectOpensContextMenuWithoutClick` | held Enter calls `onContextMenu` once and `onClick` never; fails if `onTvContextMenuKey` is not applied |
| AC-9 | instrumented | P1 | `videoItemPoster_selectKeyUpInvokesClickOnce` | fails if select fires on key-down or twice |
| AC-9 | device | P0 | M2, M3 | frame on more or fewer than one tile; menu key does nothing |
| AC-4, AC-7 | instrumented | P1 | `videoItemPoster_showsBadgesPillsAndBothTitles` | text nodes "4K", "10", "Рус", "Orig"; fails if a state field is not drawn |
| AC-4, AC-6, AC-12 | instrumented | P1 | `videoItemPoster_showsNoBadgesAndNoOriginalTitle_whenStateHasNone` | default state draws no badge text (no "CC", "AC3", "HD", "4K") and no original line, falls back to `title`; fails if defaults render |
| AC-10 | instrumented | P0 guard | `ShowAllScreenContentFocusTest` (all five tests) | `ItemCount` derived from `SHOW_ALL_GRID_COLUMNS`; fails if the requester leaves the tile |
| AC-10, AC-11 | instrumented | P0 guard | `ShowAllRailCloseFocusTest` (all five tests) | same fixture rule; fails if rail close lands off the remembered card |
| AC-10 | device | P0 | M4 | focus restore on real data |
| AC-12 | unit | P1 guard | `mapHeroItems_prefersWidePosterAndFallsBackToBigPosters` | hero URL order unchanged |
| AC-12 | instrumented | P1 guard | `VideoGridInitialFocusTest`, `FavoriteScreenContentFocusTest`, `HomeFocusTraversalTest`, `SectionRowFocusTraversalTest`, `EpisodeScheduleScreenContentTest` | full `make DEVICE=<serial> itest`; fails if shared card code changed behaviour |
| AC-12 | device | P0 | M6 | other screens differ from before |

R-check (AC-6), in the worktree against base `ad2c7f0c`:

| Command | Expected |
|---|---|
| `git diff ad2c7f0c -- app/src/main/java/com/kino/puber/data/api/models/Models.kt app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt` | empty |
| `rg -n 'Color\(0x' app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemPoster.kt` | no match |
| `rg -n '4CAF50' app/src/main/java/com/kino/puber/core/ui/uikit/theme/Color.kt` | one named `val` |
| `rg -n -i 'hasAc3\|hasSubtitles\|"AC3"\|"CC"' app/src/main` | no match |

#### Bugs

N/A: feature run.

#### Manual checks

Device `192.168.1.110:5555` under a lease; worktree dev APK via `tools/android-apk-install-preserve`; `K` = `adb -s 192.168.1.110:5555 shell input keyevent`. Screenshots with account data stay out of Git; run `tools/mobile-evidence-audit.sh` on the evidence dir.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | Owner opens the `docs/design/showall/` stand; then on the box open Catalogue, a section, Show all; screenshot | Stand approved first; 6 columns, HD / 4K badge top start, pills at poster foot, titles under poster as in the stand | port merged before approval; 16:9 cards; a CC or AC3 badge |
| M2 | In Show all: `K 22` x3, `K 20`, `K 21` until column 1, `K 21` once more (rail), `K 22` (back); screenshot after each | Exactly one tile framed (#938F99, 2 dp) and lifted; rail opens from column 1; returning focuses the remembered card; first-row frame and lift not clipped | two framed tiles; no frame; focus lost; visible stutter while moving |
| M3 | Focus a card; `K --longpress 23`; `K 4`; then `K 82` (menu); `K 4` | `VideoItemContextMenuDialog` opens both ways; Back returns focus to the same card | details opening on long-press; focus on another card |
| M4 | Focus row 2, column 3; `K 23`; on details `K 4` | Same card focused | focus on the first card or the rail; crash |
| M5 | Find a title without " / " but with a year, one with a long original title, one with one rating, one long Russian title, a 4K item, a watched item, a serial with new episodes | Year alone under the first; long original ends in an ellipsis and " · <year>" stays whole; one pill; Russian title wraps to 2 lines then ellipsizes; 4K badge in the named green; eye top end; counter and progress bar as before | year cut or missing; leading "·"; empty gap line; "10.0"; overlapping badge and eye |
| M6 | Before installing the worktree APK, screenshot Home rows, a catalogue row, Favourites, Episode schedule, player episode panel; repeat after install | Pairs identical apart from content changes | new badges, split titles, "·" year lines or new rating text outside Show all |

#### Perf

N/A: track `perf` off (`brief.md` Q3).


## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Column count, card size, poster tier | Answered by the owner (D1): 6 columns, stand sizes, `posters.medium` | no |
| Q2 | No int `subtitles` on list items: what happens to CC | Answered by the owner (D2): no CC / AC3 in this run, follow-up run `catalogue-card-audio-subtitle-badges` | no |
| Q3 | Keep track `perf` set in `state.md` | No: the goal is the look; the wide-decode cost is owned by card-image-size-tier, and AC-8 covers the grid's poster tier | no |

Assumed from `research.md: Open questions` Q2-Q5: watched eye, progress bar and unwatched counter stay (top end, poster foot); 4K/HD thresholds as in AC-2; KinoPub pill uses the web thumbs-up mark; the card is used by Show all only (reused later by catalogue-browse-all).

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | Stand docs/design/showall agreed as it opens ("давай пока как есть, так и делаем"); year in the original-title line; no CC / AC3 in this run (follow-up catalogue-card-audio-subtitle-badges) | owner |
| 2026-11-24 | Brief Q3 (no perf track) and architect Q1-Q3 on recommendation | owner (standing instruction: recommended) |
| 2026-11-24 | Drop the conditional P1 test mapShortItem_qualityBadgeUhd_whenSectionIs4kAndQualityBelow2160: quality value only, every cached 4K item carries 2160 (architect plan Q1, recommended) | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved | owner (standing instruction: recommended) |
