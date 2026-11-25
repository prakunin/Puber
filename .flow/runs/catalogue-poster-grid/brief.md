# Brief: catalogue-poster-grid

Kind: feature   Size: M   Tracks: ui   Device verification: yes (TV UI, focus, D-pad)

## Request

Rework the Show all grid after the KinoPub web grid (`reference-web-grid.png`): 2:3 poster from `posters.medium/big` instead of the wide poster, badges CC / AC3 / HD / 4K top start, rating pills (KinoPub likes, Kinopoisk, IMDb) at the poster foot, Russian title and original title under the poster, framed tile that marks focus. Order the owner set: design stand through `designing-a-screen` first, then the Compose port. Part 1 of 3 (then catalogue-browse-all, catalogue-filters).

Owner decisions 2026-11-24 after the stand (`docs/design/showall/README.md`, "Agreed" paragraph, in the worktree):

| # | Decision |
|---|---|
| D1 | The stand's starting numbers (README "Starting numbers": 6 columns, `posters.medium`, sizes, focus frame #938F99) are the spec values |
| D2 | Only HD / 4K badges ship. CC and AC3 move to run `catalogue-card-audio-subtitle-badges`; `Item.subtitles` and its `mergeSparse` line are not added |
| D3 | The original-title line carries the release year: "Orig · 2020", the year alone when there is no original title; the original ellipsizes, the year never does |
| D4 | The 4K green #4CAF50 becomes a named colour in `Color.kt` beside `KinopoiskOrange` / `ImdbYellow` |

## Findings

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

## Risks

- **Year never ellipsizes.** One `Text` with the joined string ellipsizes the year away; the card needs the original as a weighted, ellipsized `Text` and " · 2020" as a separate unweighted one. Checked on device under AC-7.
- **TV focus.** 6 columns change neighbour geometry for the index fallback in `resolveRestoreId` (`ShowAllScreenContent.kt:254`) and the rail handoff; the two device tests are the guard (AC-10, AC-11).
- **Focus scale 1.05** lets the focused tile overlap its neighbours across 12 dp gaps; judged on the TV (README "Open items" 3).
- **Shared state.** New `VideoItemUIState` fields need defaults so other mappers and screens compile and render unchanged (AC-12).

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Column count, card size, poster tier | Answered by the owner (D1): 6 columns, stand sizes, `posters.medium` | no |
| Q2 | No int `subtitles` on list items: what happens to CC | Answered by the owner (D2): no CC / AC3 in this run, follow-up run `catalogue-card-audio-subtitle-badges` | no |
| Q3 | Keep track `perf` set in `state.md` | No: the goal is the look; the wide-decode cost is owned by card-image-size-tier, and AC-8 covers the grid's poster tier | no |

Assumed from `research.md: Open questions` Q2-Q5: watched eye, progress bar and unwatched counter stay (top end, poster foot); 4K/HD thresholds as in AC-2; KinoPub pill uses the web thumbs-up mark; the card is used by Show all only (reused later by catalogue-browse-all).
