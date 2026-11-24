# Design delta: catalogue-poster-grid

## Governing stand

**None.** No `docs/design/<screen>/` covers Show all (`docs/design/` holds catalogue, content, player-controls; `docs/design/showall/` does not exist at ad2c7f0c in main or the worktree). `docs/design/catalogue/README.md` governs the catalogue tab rows only. The port must not start until `docs/design/showall/` exists with an owner-approved README (brief.md#AC-1). Until then every number below is a stand starting value, not an agreed one.

## Ground truth the stand must take (designing-a-screen step 1-2)

| What | Source | Why |
|---|---|---|
| Frame of today's Show all grid, 3 columns of `VideoItemHorizontal` | `screencap` on 192.168.1.110:5555, overlays off/on | the "было" column and the real content width |
| Closed rail width | same frame, measured in px / 2 | `MainScreenComponent.kt:304` hardcodes 60 dp; catalogue README says no measurement backs it; the column maths depends on it |
| Items with real `quality`, `ac3`, `subtitles`, `rating_percentage`, KP, IMDb, title with and without "/" | `databases/puber.db` `cached_payload` on the box | badges and pills from data, not placeholders; settles the `subtitles` type (brief.md Risks, Q2) and the 4K `quality` value |
| Posters | `posters.medium` and `posters.big` of the same items | the stand shows the tier the code will load |
| At least: one item with one rating (one pill), one with no "/" in the title, one long Russian title (two lines), one 4K, one watched (eye), one part-watched (progress bar), one serial with an unwatched count | cache | each card state the grid can draw |

## Starting geometry (stand opens on these)

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

## What the stand must let the owner dial

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

## Focus order and visibility

| Element | Focusable | Order / D-pad | Focus visibility |
|---|---|---|---|
| Poster card (tile) | yes, one stop per card | row-major; Left/Right within a row, Up/Down within a column; Left from column 1 hands off to the rail (`LocalContentFocusHandoff`), Right from the rail returns to the remembered card (`GridFocusMemory`, unchanged) | the dialled focus frame plus lift; the tile at rest must read as unfocused at arm's length (rest frame visibly weaker than focus frame) |
| Badges, pills, titles, eye, progress bar | no | - | - |

- Focus frame and lift must not be clipped: the top row needs edge padding >= lift + frame width; the stand shows the first row focused.
- `Card` focused scale stays 1 unless the owner dials scale instead of lift (today `CardDefaults.scale(pressedScale = 1f, focusedScale = 1f)`, `VideoItemHorizontal.kt:77`).
- Long-press / menu key opens `VideoItemContextMenuDialog` as today (`onTvContextMenuKey`); select on key-up (`onSelectKeyClick`) carries over to the new card.

## Shared constants: parameterise, never retune in place

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

## Open (to be closed on the stand)

All numbers above; the image tier; the focus colour; whether tiles in a row share a height; the real closed-rail width.
