# Research: detail-poster-directional-change

## 1. Request

Owner (state.md Decisions and Log): on the Android TV detail panel (the hero/details area above the carousels on Home/catalogue rows and the details screen header that shows the focused card's poster/backdrop, trailer preview, title, description, metadata), when focus moves between cards the content changes abruptly. Wanted: (1) poster/backdrop changes with a short directional shift (40-60 dp) plus crossfade, ~250 ms, entering from the side the carousel moves toward, old leaving the opposite way and fading; (2) the text - title, description, metadata (year, rating, genres, etc.) and other text of the panel - must also change smoothly, matching the poster transition. Today "нет плавности в появлении текста".

Hard constraints: keep the 1.12.1 gains of `detail-poster-trailer-flicker` (no skeleton flash, held poster during Loading) and do not reintroduce `trailer-video-hidden-regression` (nothing may draw over a rendered SurfaceView trailer; the SurfaceView is redrawn once it gets a size).

## 2. Understanding

When focus moves to another card, the panel above the rows should slide its picture and its text a short distance in the direction of travel while crossfading from the old card to the new one, instead of the picture crossfading in place and the text dropping to a skeleton and popping back. The panel only knows the new card's details (an id), not where the card sits, so a direction source has to be added. The text currently has no hold or animation at all: it goes through the `Loading` skeleton on every card change.

## 3. Current state in code

Paths relative to `app/src/main/java/com/kino/puber/`.

### Which screens have the panel

| Screen | Call | Layout | Changes with focus |
|---|---|---|---|
| Catalogue (ContentList tabs) | `ui/feature/contentlist/content/ContentListScreenContent.kt:166-175` | full-bleed media + text over it (`fullBleedMedia`, `expandMediaIntoContent`), trailer | yes |
| Favourites | `ui/feature/favorites/content/FavoriteScreenContent.kt:99-103` | text left, poster right (`Row`), no `trailerUrl` | yes |
| Details hero | `ui/feature/details/component/DetailsScreenContent.kt:189-231` | `VideoDetailsMedia` + its own text column (`HeroTitle`, `ChipRow`, `PlotBlock`) | no: one item per screen; similar items open a new screen (`DetailsVM.kt:177`) |
| Home | none: `ui/feature/home/component/HomeScreenContent.kt` has a `HeroCarousel` and rows, no `VideoItemGridDetails` | - | - |

### Panel composables (`core/ui/uikit/component/details/VideoItemGridDetails.kt`)

- `VideoItemGridDetails` `:228-277`: full-bleed branch draws `VideoDetailsMedia` then `VideoDetailsDescription` (text after media so it survives over the SurfaceView, comment `:248-250`); side-by-side branch is a `Row` of description and `VideoDetailsPoster`.
- `VideoDetailsMedia` `:284-326`, `ExpandedMediaLayout` `:334-354` (catalogue media placed at the right edge, 16:9, overflows below).
- `VideoDetailsDescription` `:357-437`: title `:374-381`, ratings row `:383-388`, facts `:391-397`, duration `:401-406`, plot `:410-434`. Every piece uses `placeholder(visible = state.isLoading)`; the plot is replaced by skeleton bars while loading. No animation, no hold.
- `VideoDetailsPoster` `:441-567`: `rememberHeldUrls` keeps the previous URL list while `Loading` (`:464`, `:694-708`, hold capped at `POSTER_HOLD_MS = 3_000` `:74`); one `PosterStill` drawn after the conditional `TrailerPreviewPlayer` (`:482-513`); still alpha from `posterStillAlpha`/`posterFadeTarget` (`:710-719`), 300 ms fade after the first frame; scrims after the still `:515-565`; box `clipToBounds()` `:457`.
- `posterRequest` `:585-594`: `useExistingImageAsPlaceholder(true)` + `PosterCrossfade` (`:681-688`, 200 ms, also on memory-cache hits).
- `PosterStill` `:597-614` -> `SkeletonAsyncImage` with `skeletonOnlyUntilFirstImage = true` (`core/ui/uikit/component/SkeletonAsyncImage.kt:20-79`; `hasShownImage` keyed on `model != null` `:30`).
- `Rating` draws its own placeholder from `RatingUIState.isLoading` (`core/ui/uikit/component/RatingUIState.kt:85`).
- `VideoDetailsUIState.Loading` (`VideoDetailsUIState.kt:24-40`): `id = 0`, `imageUrl = ""`, fake title/description, `isLoading = true`.

### Trailer (`TrailerPreviewPlayer.kt`)

- `AndroidView` with `PlayerView` (SurfaceView) `:129-147`; `onRenderedFirstFrame` `:89-91`; the redraw-on-size listener from `trailer-video-hidden-regression` `:136`, `:150-159`.
- The catalogue clears `previewTrailerUrl` the moment focus moves (`ContentListVM.kt:64`), so on a card change the player leaves composition and the still snaps to alpha 1 before any new details land. The trailer URL is published at the earliest 2 s after focus (`TRAILER_PREVIEW_DELAY_MS`, `ContentListVM.kt:68`, `:89-93`).

### How focus reaches the panel

| Step | Catalogue | Favourites |
|---|---|---|
| Card focus | `SectionRow.kt:151-161`: `itemsIndexed` knows `index`; reports `onItemFocused(item)` only (no index, no row) | `VideoGridUIState.kt:443-458`: `indexR` known, `onItemFocused(focusedItem)` only |
| Action | `CommonAction.ItemFocused(item)` (`ContentListScreenContent.kt:375`) | `CommonAction.ItemFocused` (`FavoriteScreenContent.kt:111`) |
| VM | `ContentListVM.onItemFocused` `:61-95`: cancel previous job, wait `FOCUS_DETAILS_DEBOUNCE_MS = 150`, publish `Loading`, `getItemDetails` (cache-backed, `ContentListInteractor.kt:190-192` -> `ItemDetailsRepository.getItemDetailsCacheOnly` `:54-56`), publish mapped details | `FavoriteVM.onItemFocused` `:106-118`: publish `Loading` at once (no debounce), fetch, publish |
| State | `ContentListViewState.selectedItem` (`ContentListViewState.kt:11`) | `FavoriteViewState.Content.selectedItem` |
| Panel | `VideoItemGridDetails(state = state.selectedItem)` | same |

Consequences:
- Text sequence today on a card change: old text for 150 ms (catalogue) -> skeleton -> new text, with a hard cut at each step. This is the "нет плавности в появлении текста".
- Rapid presses are debounced (the job is cancelled), so the panel jumps from the shown card to the last focused card; intermediate cards never show. A direction must be computed between the shown card and the new card, not from the last key press.
- Whether a warm `getItemDetails` lands in the same frame as `Loading` (so the skeleton is never drawn) is `unverified`.
- A details refresh after returning from Details republishes the same `id` (`ContentListVM.kt:161-169`); the details hero refreshes in place the same way. A transition keyed on `id` does not fire for these.
- No mapper fetches text asynchronously after the details land: `VideoItemUIMapper.mapDetailedItem` (`core/ui/model/VideoItemUIMapper.kt:82-98`) is synchronous; TMDB is not used by the panel (it was dropped from the details hero in `0192929e`).

### Position data that could give a direction

- `FocusNeighbourhood` (`core/ui/uikit/component/moviesList/FocusNeighbourhood.kt:15-56`) holds every registered row's `order` and its ordered item ids; fed by `DetailsPrefetchRow` (`ContentListScreenContent.kt:157-163`) and told about each focus through `ReconciledItemFocusState.onItemFocused` -> `prefetchSurface?.onItemFocused(rowKey, itemId)` (`FocusTargetState.kt:184`). It is the existing place that knows (row order, index) for a focused card, but it is keyed by `rowKey`, and the same film can sit in two rows, so a lookup by id alone is ambiguous.
- Rows do not wrap (no wrap-around in `SectionRow` or `VideoGrid`'s `LazyRow`).
- Focus restored by the system (tab re-entry, Back from Details, `focusRestorer`, `FocusTargetState.kt:162-176` redirect) also reports `ItemFocused`; usually same id, otherwise no D-pad direction behind it.
- The `HeroCarousel` reports no `ItemFocused` (`ContentListScreenContent.kt:301-304`), so the panel keeps its card while the hero is focused.

### Existing animation patterns

| Where | Pattern |
|---|---|
| `ui/feature/player/component/PlayerTimedActionOverlay.kt:242-262` | `AnimatedContent` with `fadeIn + slideInVertically togetherWith fadeOut + slideOutVertically`, `contentKey` |
| `ui/feature/player/component/ResumeDialog.kt:69-85` | `AnimatedContent`, fade + scale |
| `ui/feature/player/component/PlayerSidePanel.kt:113`, `EpisodesPanel.kt:172` | `fadeIn(tween(...)) + slideIn*`, `FastOutSlowInEasing` |
| `VideoItemGridDetails.kt:472-480` | `animateFloatAsState` read in `graphicsLayer` lambda |

No stand states motion: `docs/design/catalogue/README.md` and `docs/design/content/README.md` define geometry and scrims only.

### Tests in this area

| Test | Covers |
|---|---|
| `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` | `heldPosterUrls`, `posterFadeTarget`, `posterStillAlpha`, `posterRequest`, `PosterCrossfade` |
| `.../details/TrailerSurfaceRedrawTest.kt` | `surfaceNeedsRedraw` |
| `.../component/SkeletonAsyncImageVisibilityTest.kt` | `skeletonVisible` |
| `.../moviesList/FocusNeighbourhoodTest.kt` | neighbour prediction |
| `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/ContentListVMTest.kt`, `favorites/vm/FavoriteVMTest.kt` | `selectedItem` sequence, trailer gate |
| `app/src/androidTest/.../contentlist/content/SectionRowFocusTraversalTest.kt` | row focus (instrumented, `make itest`) |

No Compose UI test covers the panel; the source run decided not to add a Robolectric harness (its Q4).

## 4. Related work

| Run / doc | Relation |
|---|---|
| `detail-poster-trailer-flicker` (done, 1.12.1, `c992db1f`) | Source run. Spec `docs/superpowers/specs/2026-11-19-detail-poster-trailer-flicker.md`: D1 one persistent still after the player, D2 alpha fade, D3 held URLs, D4 forced Coil crossfade, D5 skeleton only until first image. Its Q1 kept the text skeleton out of scope and its R7 accepted the old poster beside the new card's text skeleton; this run reverses that for text. Its research option C (VM stops publishing `Loading` or seeds from the card) is the text option not taken then. |
| `trailer-video-hidden-regression` (done, 1.12.2, `989e8826`) | Root cause was a late SurfaceView layout with no traversal, not alpha over the hole (its diagnosis.md ruled the alpha hypothesis out). Fix T keeps the redraw in the player, independent of sibling drawing. Spec `docs/superpowers/specs/2026-11-19-trailer-video-hidden-regression.md`; fallback B (TextureView) not taken. |
| `detail-poster-fallback-empty-frame` (idea) | Same `PosterStill`/`SkeletonAsyncImage` code: a failed primary URL leaves an empty frame. Touches the same lines; sequence the two runs or fold it in. |
| `focus-trailer-preview` (done), `docs/superpowers/specs/2026-08-15-details-prefetch-design.md` | Origin of the 2 s trailer gate and the details prefetch that makes most card changes warm. |

Duplicate: none.

## 5. External findings

| Finding | Source |
|---|---|
| `AnimatedContent` keeps both contents composed during a transition; `transitionSpec` can compare `targetState` and `initialState` to pick a direction (counter example uses `slideInVertically + fadeIn togetherWith slideOutVertically + fadeOut`); `SizeTransform(clip = false)` stops clipping a faded slide; target draws over initial by default (`targetContentZIndex`); `ExitTransition.KeepUntilTransitionsFinished` exists. Project uses compose animation 1.12.1 (BOM `2026.09.00`, `gradle/libs.versions.toml:7`). | https://developer.android.com/develop/ui/compose/animation/composables-modifiers (read 2026-11-20) |
| Coil Compose: custom `Transition` implementations do not work with `AsyncImage`/`rememberAsyncImagePainter`; only `CrossfadeTransition` has internal support. Custom effects must observe `AsyncImagePainter.state`. So a directional transition cannot be a Coil `Transition`; it has to be Compose-side. Project Coil 3.6.3. | https://coil-kt.github.io/coil/compose/ (read 2026-11-20) |
| TV design guidance for the immersive list: the background follows focus and updates as the user moves through the row; 16:9 backgrounds. It states no motion spec. | https://developer.android.com/design/ui/tv/guides/components/immersive-list (read 2026-11-20) |

## 6. Options

Direction in all options: same row, index up -> new content enters from the right (end) and the old leaves left; index down -> mirrored; first focus, focus restore, unknown position -> crossfade with no shift. Row change: see Q1.

| # | What | Trade-offs | Size |
|---|---|---|---|
| **A (recommended)** | UI only. (1) A small position tracker in each screen's content (`ContentListScreenContent`, `FavoriteScreenContent`) records `(rowOrder, index)` per focused id from the row callbacks that already know the index; a pure `panelDirection(from, to)` gives `Start / End / Up / Down / None`, passed to `VideoItemGridDetails` as a new parameter (default `None`, so the details hero is untouched). (2) Text: hold the last loaded details through `Loading` with the same rule and cap as the poster (`heldPosterUrls`, 3 s), then `AnimatedContent` keyed on `id` with shift + fade in the given direction; first load keeps the skeleton. (3) Poster: keep the single `PosterStill` (1.12.1 D1/D5 intact); on a card change, draw a transient outgoing copy of the previous image (from Coil memory cache, plain `AsyncImage`, no skeleton) that slides out and fades, and give the still a `graphicsLayer` translation + alpha enter, started on the new image's `onSuccess`; Coil's 200 ms crossfade is switched off for card changes so the blend is not doubled. | No VM or action contract change; VM tests stay as they are; one shared component serves both screens. Two images drawn for 250 ms on TV hardware. `posterRequest`/`PosterCrossfade` tests change deliberately. Text and poster can start at different moments when the image is cold (Q3). | M |
| B | VM-driven. `ItemFocused` carries row and index; `ContentListVM`/`FavoriteVM` stop publishing `Loading` while previous details exist (hold with timeout in the VM), compute the direction and publish it in state; composables animate on that state. Poster as in A. | Direction and hold are unit-testable in VM tests. Changes `CommonAction.ItemFocused`, used by many screens, and two VMs plus their tests; the poster hold in `VideoItemGridDetails` then duplicates the VM hold. | M (borders L) |
| C | Minimal. Text: hold through `Loading` and crossfade on `id` (no shift). Poster: keep the single still and Coil crossfade, add only a short translation of the whole still layer toward the direction while it crossfades. | Smallest diff, no second image layer. Old and new move together, so "old leaves the opposite way" (owner decision 2026-11-19) is not met; the shifted layer bares a strip at the panel edge. | S |

## 7. Risks

| Risk | Where | Note |
|---|---|---|
| Drawing over a rendered trailer | `VideoDetailsPoster` draw order | A card change clears the trailer first (`ContentListVM.kt:64`), so transitions normally run with no player. Edge: cold details + cold image landing near the 2 s gate can start a trailer while the poster transition runs. The outgoing layer must leave composition when done and the still's trailer fade must keep precedence; the player's redraw fix does not depend on siblings (`TrailerPreviewPlayer.kt:136`). |
| 1.12.1 regressions | `SkeletonAsyncImage`, `rememberHeldUrls` | A second `SkeletonAsyncImage` or a still re-created per card restarts `hasShownImage` and brings the skeleton flash back. Same for an `AnimatedContent` wrapping `PosterStill`. |
| Wrong card's text visible | text hold | Holding text through `Loading` shows the previous card's title for the fetch time (up to 3 s) while the new card is focused; the poster already does this (source run R7). |
| Direction ambiguity | tracker | Same id in two rows; debounced jumps across several cards; focus restore with no D-pad input. Use the row key of the focus event, not an id lookup. |
| Edge strips | `VideoDetailsPoster` `clipToBounds` | A 40-60 dp shift bares the panel background at one edge; the left scrim hides the left edge in full-bleed, the right edge is the screen edge. |
| TV performance | two full-width bitmaps + two text columns for 250 ms | Device GPU headroom `unverified`; animated values must be read in `graphicsLayer` lambdas (`docs/architecture/ui-and-compose.md` Performance rules). |
| Text layout jump | `AnimatedContent` around `VideoDetailsDescription` | Different plot heights; the column is `fillMaxHeight`, so size should not change, but `SizeTransform` and clip need a decision. |
| Evidence | qa | Card changes run with no SurfaceView, so screencap bursts can capture the motion; trailer-overlap edge case is human-eye only. |

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | On a row change (Up/Down), shift vertically or only crossfade? | Crossfade only. The request ties the side to the carousel's horizontal travel, and the catalogue media is top-aligned and overflows below, so a vertical shift bares the top edge. |
| Q2 | While the new card's details load, keep the old text (then switch with the transition) or show the skeleton as today? | Keep the old text, with the poster's 3 s cap; skeleton only on first focus and after the cap. |
| Q3 | Should the text wait for the new poster image so both move together? | No. Text moves when details land, the poster when its image is ready; on warm cards (prefetched) they coincide. |
| Q4 | Same shift distance for text as for the poster? | Same direction and 250 ms; text shift at the lower end (about 40 dp or less), ux-designer sets the number. |

## 9. Sources

- Files: `VideoItemGridDetails.kt`, `VideoDetailsUIState.kt`, `TrailerPreviewPlayer.kt`, `SkeletonAsyncImage.kt`, `RatingUIState.kt`, `ContentListScreenContent.kt`, `SectionRow.kt`, `ContentListVM.kt`, `ContentListViewState.kt`, `ContentListInteractor.kt`, `ItemDetailsRepository.kt`, `VideoItemUIMapper.kt`, `FavoriteVM.kt`, `FavoriteScreenContent.kt`, `DetailsScreenContent.kt`, `DetailsVM.kt`, `HomeScreenContent.kt`, `FocusNeighbourhood.kt`, `FocusTargetState.kt`, `DetailsPrefetchSurfaceState.kt`, `VideoGridUIState.kt`, `PlayerTimedActionOverlay.kt`, `ResumeDialog.kt`, `gradle/libs.versions.toml`, `docs/architecture/ui-and-compose.md`, `docs/design/README.md`, `docs/design/catalogue/README.md`, `docs/design/content/README.md`.
- Commits: `c992db1f`, `db7a524c`, `989e8826`, `e921fca7`, `0192929e`.
- Runs: `detail-poster-trailer-flicker`, `trailer-video-hidden-regression`, `detail-poster-fallback-empty-frame`, `focus-trailer-preview`.
- Specs: `docs/superpowers/specs/2026-11-19-detail-poster-trailer-flicker.md`, `docs/superpowers/specs/2026-11-19-trailer-video-hidden-regression.md`.
- URLs: https://developer.android.com/develop/ui/compose/animation/composables-modifiers, https://coil-kt.github.io/coil/compose/, https://developer.android.com/design/ui/tv/guides/components/immersive-list
