# Research: catalogue-poster-grid

## Request

"Vertical poster card with badges and ratings for the Show all grid". Log line, 2026-11-24: owner request: rework the Show all grid after the KinoPub web grid (screenshot in reference-web-grid.png): vertical poster (posters.medium/big instead of the 4K wide poster), badges CC / AC3 / HD / 4K top left, ratings KinoPub likes / Kinopoisk / IMDb on the poster bottom, Russian title and original title under it, focus frame. Design stand first through designing-a-screen, then port to Compose and switch ShowAllScreen (LazyVerticalGrid of VideoItemHorizontal today, ShowAllScreenContent.kt) to it. Check which badge fields Item carries (subtitles, ac3, quality). Related: card-image-size-tier (4K wide decode cost; this run removes it from the grid). Part 1 of 3 with catalogue-browse-all and catalogue-filters.

## Understanding

Show all today is a 3-column grid of 16:9 cards that load the wide (up to 4K) poster. The owner wants the web look instead: a 2:3 poster card in more columns, with quality/sound/subtitle badges top left, three rating pills at the poster foot, and the Russian plus original title below the poster, all inside a framed tile that marks focus. The work is a new design stand, a new card in the uikit, three or four new UI-state fields fed from list-endpoint fields (one of them, the subtitle count, not yet in the `Item` model), and swapping the card in `ShowAllScreenContent` without breaking its focus memory.

## Current state in code

Paths are under `app/src/main/java/com/kino/puber/`.

| Where | What it does now |
|---|---|
| `ui/feature/contentlist/vm/ContentListVM.kt:141` | Each catalogue section opens `ShowAllScreen(config)` |
| `ui/feature/showall/ShowAllScreen.kt:33` | Key `ShowAllScreen_${config.id}`; DI scope builds `ShowAllVM` and a scoped `VideoItemUIMapper` (`:36-52`) |
| `ui/feature/showall/vm/ShowAllVM.kt:118-143` | Maps every paginator state to `ShowAllViewState.Content(items = mapItems(...))` through `ContentListPagingVM` |
| `ui/feature/showall/model/ShowAllViewState.kt:338-341` | `Content(items: List<VideoItemUIState>, isLoadingMore)` |
| `ui/feature/showall/content/ShowAllScreenContent.kt:108` | `GRID_COLUMNS = 3` |
| `ui/feature/showall/content/ShowAllScreenContent.kt:168-196` | `LazyVerticalGrid(GridCells.Fixed(3))`, padding 16 dp, gaps 16/32 dp, `VideoItemHorizontal` per item with context menu |
| `ui/feature/showall/content/ShowAllScreenContent.kt:215-322` | `GridFocusMemory`: restore after Back, rail handoff, reload fallback; card-agnostic (id + index), must be kept |
| `domain/interactor/contentlist/ContentListInteractor.kt:180-186` | Pages come from `GET items` or `GET items/<shortcut>` (`data/api/KinoPubApiClient.kt:253-289`) |
| `core/ui/model/VideoItemUIMapper.kt:29-54` | `mapShortItem`: `imageUrl` = medium, `bigImageUrl` = big, `wideImageUrl` = wide; title passed raw ("Рус / Orig") |
| `core/ui/model/VideoItemUIMapper.kt:114-122` | Ratings in order KP, IMDb, PUB; PUB = `ratingPercentage / 10` as `Float.toString()` (100 prints "10.0") |
| `core/ui/model/VideoItemUIMapper.kt:148-150` | `formatTitle()` splits on "/" (details only, private) |
| `core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:89` | URL order wide, big, medium: the grid decodes the wide poster first |
| `core/ui/uikit/component/moviesList/VideoItemUIState.kt:50-71` | UI state; no badge, quality or original-title fields |
| `core/ui/uikit/component/moviesList/VideoItemUIState.kt:83-129` | Existing vertical card `VideoItem`: fixed 120 x 180 dp (`core/ui/uikit/theme/PuberTheme.kt:14-15`), medium poster first (`:174-210`), ratings stacked in an over-poster caption (`:212-281`), watched / unwatched badge top end (`:295-322`) |
| `core/ui/uikit/component/moviesList/VideoGridUIState.kt:477-530` | `VideoItem` callers: favourites, episode schedule, player episodes panel (via `VideoGrid`) |
| `core/ui/uikit/component/RatingUIState.kt:30-99` | `Rating` row: 14 dp icon + `bodySmall`; `ic_kinopub` is a raster and is never tinted (`:55-57`) |
| `data/api/models/Models.kt:46-97` | `Item`: `quality: Int?` (:60), `ac3: Int?` (:61), `imdbRating` (:66), `kinopoiskRating` (:69), `langs: String?` (:71), `ratingPercentage` (:73); no item-level `subtitles` |
| `data/api/models/Models.kt:215-220` | `Posters(small, medium, big, wide)` |
| `data/api/models/Models.kt:274-286` | Subtitle lists exist only on `Video` / `Episode` (details payload) |
| `data/cache/ContentCacheRepository.kt:443-480` | `mergeSparse` copies each `Item` field by name; a new field needs its own line |
| `data/api/KinoPubApiClient.kt:130-134` | `Json { ignoreUnknownKeys; isLenient }` |
| `ui/feature/details/model/DetailsScreenUIMapper.kt:310-313, 430-440` | Details already derive surround sound (`ac3 == 1`) and quality, but from the details payload's files |
| `ui/feature/contentlist/model/TabTypeConfig.kt:73,182,188` | 4K sections request `quality = "4k"` |

Tests around it: `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt`, `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`, `.../ShowAllRailCloseFocusTest.kt`.

### Badge and rating sources (list endpoint)

| Element | Field | In `Item` | Source |
|---|---|---|---|
| CC | `subtitles` (count of subtitle tracks) | no | kinoapi.com list sample |
| AC3 | `ac3` (0/1) | yes, :61 | kinoapi.com; changelog 14.02.2017 |
| HD / 4K | `quality` (int, sample 1080; for serials the quality most episodes have) | yes, :60 | kinoapi.com; the 4K value (2160?) `unverified` |
| KinoPub likes | `rating_percentage` (already the PUB pill); web grid values 0.8 to 10 match /10 | yes, :73 | web mapping `unverified` |
| Kinopoisk | `kinopoisk_rating` | yes, :69 | in use |
| IMDb | `imdb_rating` | yes, :66 | in use |
| Original title | part after "/" in `title` ("Название / Оригинальное название") | yes, :47 | kinoapi.com |

### Poster sizes (measured)

Fetched `https://m.staticpop.net/poster/item/<size>/<id>.jpg` for items 30 and 111985 on 2026-11-24 (host from `docs/design/content/README.md`):

| Size | Pixels | Bytes |
|---|---|---|
| small | 165 x 250 | 13-18 KB |
| medium | 250 x 375 | 25-37 KB |
| big | 500 x 750 | 94-123 KB |
| wide | 1365 x 768, 1712 x 964 (varies; 3840 x 2160 seen in card-image-size-tier) | 45-198 KB |

At density 2.0 a 125 dp poster is 250 px (medium 1:1); anything wider needs big.

## Related work

| Run / doc | Stage | Relation |
|---|---|---|
| card-image-size-tier | idea | 4K wide decode cost on cards; this run removes wide from the grid only, Home and rows keep it |
| catalogue-browse-all | idea | Part 2: pages a whole content type in this run's grid |
| catalogue-filters | idea | Part 3: filter panel on that grid (`domain/model/ContentFilter.kt`) |
| grid-focus-restore-after-back, showall-focus-restore-hardening, showall-rail-close-focus-shift | done | Built `GridFocusMemory` and its device tests; the new card must keep them green |
| showall-entry-restore-foreign-landing, showall-first-entry-rail-focus | idea | Open Show all focus defects; adjacent, not covered here |
| design-open-items | idea | Stand defects; catalogue `FadeGradient` overhang, unrelated to this grid |
| `docs/design/catalogue/README.md` | stand | Governs the catalogue tab rows (`VideoItemHorizontal` parameters), not the Show all grid |
| `docs/design/content/README.md` | stand | Details screen; source of the device-cache and artwork method |
| `.claude/skills/designing-a-screen/SKILL.md` | skill | Stand workflow: device ground truth, `docs/design/<screen>/index.src.html`, `build.py`, README of agreed numbers, then port |

No stand covers Show all. Duplicate: none.

## External findings

| Finding | Source |
|---|---|
| List item carries `quality` (int), `ac3`, `subtitles` (count), `langs` (count), `posters.small/medium/big`, title "Рус / Orig"; no `wide` or `rating_percentage` in the sample (both arrive in practice: `Models.kt`) | https://kinoapi.com/api_video.html (API 1.3) |
| `ac3` added 14.02.2017; 4K support 15.12.2018; quality filter 22.01.2019 | https://kinoapi.com/changelog.html |
| Quality reference: ids with `quality` 480, 720, ... | https://kinoapi.com/api_references.html |
| Docs are dated (last changelog entries 2020); field presence on today's API is `unverified` until checked in a device `cached_payload` | same |

## Options

| Option | What | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | New stand `docs/design/showall/`; new uikit card (e.g. `VideoItemPoster`) with framed tile, poster, badges, rating pills, two title lines; new fields on `VideoItemUIState` with defaults (quality badge, ac3, has subtitles, original title); `Item.subtitles: Int?` plus its `mergeSparse` line; `ShowAllScreenContent` switches card and column count | No other screen changes; reusable by catalogue-browse-all; touches API model, cache merge and TV focus | M |
| B | Extend existing `VideoItem` with optional badges and an under-poster caption | Less new code, but `VideoItem` is fixed at 120 x 180 dp and drawn by favourites, schedule and player panel; needs parameters everywhere it is shared | M |
| C | Switch Show all to the existing `VideoItem` as is, no badges, no stand | Removes the wide decode at once; does not deliver the requested look | S |

## Risks

- **TV focus.** `GridFocusMemory` and the two device tests run on this grid; a different column count changes neighbour geometry for the reload fallback (`resolveRestoreId` by index) and rail handoff.
- **Shared UI state.** `VideoItemUIState` feeds many screens; new fields need defaults so other mappers are untouched.
- **API shape.** If item-level `subtitles` is not an int on some endpoint, decoding of the whole `Item` fails. Keep it nullable and confirm the type in a device `cached_payload` before the port. Old cache entries lack the field, so CC is absent until the item is re-fetched.
- **Image tier.** Medium upscales on a card wider than 125 dp; big costs about 3x the bytes of medium but is about 1/20 of a 4K wide decode.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Column count and card size | Start the stand at 6 columns as on the web and dial it there; pick medium or big from the resulting poster width |
| Q2 | Keep watched eye, progress bar and unwatched-episode counter on the new card | Yes, top end and poster foot as today; badges stay top start |
| Q3 | Quality thresholds | 4K when `quality >= 2160` or type `4k`, HD when `>= 720`, nothing below |
| Q4 | KinoPub rating pill | Keep `rating_percentage / 10`, printed without a trailing ".0", with the thumbs-up mark of the web grid instead of `ic_kinopub` |
| Q5 | Other grids (favourites, collections) adopt the card | No; Show all only, reused by catalogue-browse-all |

## Sources

- Files: those in the table above; `docs/design/README.md`; `docs/design/catalogue/README.md`; `docs/design/content/README.md`; `.claude/skills/designing-a-screen/SKILL.md`; `AGENTS.md`; `.flow/runs/catalogue-poster-grid/reference-web-grid.png`.
- Commits: 1a4109ba, 95fd3a8e, a50c4b2f, b0b1740b (Show all focus); eb6c9b16 (card progress mapping); 6b75a325 (section page cache).
- Runs: card-image-size-tier, catalogue-browse-all, catalogue-filters, grid-focus-restore-after-back, showall-focus-restore-hardening, showall-rail-close-focus-shift, showall-entry-restore-foreign-landing, showall-first-entry-rail-focus, design-open-items, startup-splash-preload.
- URLs: https://kinoapi.com/, https://kinoapi.com/api_video.html, https://kinoapi.com/changelog.html, https://kinoapi.com/api_references.html, https://m.staticpop.net/poster/item/{small,medium,big,wide}/{30,111985}.jpg (2026-11-24).
