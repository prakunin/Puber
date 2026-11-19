# Remove the detail panel's poster flash

Status: approved
Date: 2026-11-19
Slug: detail-poster-trailer-flicker
Kind: bug
Size: M
Tracks: ui

## Problem / Why

### Request

1. Remove the flash in the catalogue detail panel when the poster gives way to the trailer.
2. When cards are switched before a trailer starts, the poster should change through a smooth transition instead of a flash or a jump (research.md: Request).

### Root cause

The visible flash is the skeleton, not the video. The skeleton is opaque `PurpleGrey40` (`core/ui/uikit/component/modifier/Placeholder.kt:19`), and the panel draws it every time a still is created, or its request changes or becomes `null`.

| # | Moment | Cause | Status |
|---|--------|-------|--------|
| 1 | Card change | `ContentListVM.kt:71` publishes `VideoDetailsUIState.Loading` 150 ms after focus. Its `imageUrl = ""` (`VideoDetailsUIState.kt:28`) leaves `imageUrls` empty, so the request is `null` (`VideoItemGridDetails.kt:433-440, 557-567`). `PosterStill` then covers the whole panel with `placeholder(visible = request == null)` (`VideoItemGridDetails.kt:579`) until the details land. Next, the new poster crossfades from nothing (`crossfade(true)`, `:564`). `FavoriteVM.kt:110` follows the same path. | confirmed (code trace) |
| 2 | Trailer URL arrives | The over-copy inside `AnimatedVisibility` (`VideoItemGridDetails.kt:478-490`) is a new `SkeletonAsyncImage`. Its `isLoading` starts `true` (`SkeletonAsyncImage.kt:28`), and its skeleton is drawn over the image (`:60-64`) until `onSuccess`, even on a memory-cache hit. That one is drawn over the SurfaceView hole. | plausible (code trace; whether a cache hit clears it before the first draw is unverified) |
| 3 | Trailer ends or focus leaves a playing card | `stopTrailerPreview()` (`ContentListVM.kt:132-136`) sets `previewTrailerUrl = null`. The under-still was removed once the first frame rendered (`VideoItemGridDetails.kt:446`), so it is created fresh and starts on the skeleton as in #2. On focus loss, #1 follows 150 ms later. | confirmed (code trace) for re-creation; skeleton frame as #2 |
| 4 | First video frame | The over-copy runs `fadeOut()` (`VideoItemGridDetails.kt:481`). This is the intended transition and not a defect. | confirmed (code trace) |

None of this has been observed on a device. `screencap` blanks the video layer (research.md: Related work), so the first frames only show to a human watching.

## Acceptance criteria

| ID | Given | When | Then | Checked by |
|----|-------|------|------|------------|
| AC-1 | Focus rests on a catalogue card that has a trailer and auto-trailer on | the trailer URL arrives and until the first video frame | the card's poster stays on screen with no skeleton or black frame | device, human eye (owner judges) |
| AC-2 | AC-1 has reached the first video frame | the poster gives way | it fades out over the playing video, with no flash between poster and video | device, human eye (owner judges) |
| AC-3 | A trailer is playing | it ends, or focus moves to another card | the poster returns without a skeleton frame | device, human eye |
| AC-4 | The panel shows card A's poster | focus moves to card B faster than the 2 s trailer dwell, repeatedly | the poster crossfades A to B with no skeleton or empty frame between, both when B's details are warm and when they are cold | device, human eye (owner judges smoothness) |
| AC-5 | The panel has never shown a poster (first focus after screen open) | details are still loading | the skeleton shows until the first image, as on base | device |
| AC-6 | A card whose first wide poster URL fails | its details are shown | the panel falls back to the next URL (`urlIndex` advances) and the previous card's poster does not stay | device or Compose test |
| AC-7 | A fitted (letterboxed) catalogue trailer is playing | after the fade completes | no part of the poster is visible around the video | device, human eye |
| AC-8 | The details screen hero with a trailer, and the favourites panel | the trailer starts or ends, or the favourites card changes | the behaviour of AC-1 to AC-4 holds there too | device, human eye |
| AC-9 | Grids, rows, the hero carousel and collection cards (`SkeletonAsyncImage` users without the opt-in) | their images load | they show the skeleton as on base | code review: default parameter unchanged |
| AC-10 | The change | `check.full` runs | it passes, and `ContentListVMTest`, `FavoriteVMTest` and `DetailsVMTrailerPreviewTest` stay green unchanged | qa |

## Out of scope

In scope:
- `VideoDetailsPoster` (`VideoItemGridDetails.kt:416-547`), `rememberPosterRequest` (`:556-568`) and `PosterStill` (`:570-585`).
- An opt-in parameter on `SkeletonAsyncImage` (`core/ui/uikit/component/SkeletonAsyncImage.kt:20`).

The fix is option A (research.md: Options):
- One still that persists, drawn after a conditional player and faded by alpha on `trailerRendered`.
- The last non-null request is held while `imageUrl` is empty.
- Requests are built with `useExistingImageAsPlaceholder(true)` and `crossfade`.
- The skeleton shows only before the first successful image.

It fixes the catalogue, the details hero (`DetailsScreenContent.kt:189-196`) and favourites (`FavoriteScreenContent.kt:99`) at once.

Out of scope:
- The text column's skeleton on card change (option C; see Q1).
- TextureView or `media3-ui-compose` (option B; see Q3).
- VM state.

## Design

### Architect

Base 76a8bc2a, option A (state.md Q1-Q3). Paths are relative to `app/src/main/java/com/kino/puber/`. Timings and the visual delta are owned by `design-ux-designer.md`; this file cites them.

### Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | How does one still survive trailer start and stop? | `VideoDetailsPoster` composes `if (trailerUrl != null) TrailerPreviewPlayer(...)` first, then a single `PosterStill` call that is never conditional. No explicit `key`. | The compiler wraps an `if` with composable calls in a replace group on both branches (base bytecode of `VideoDetailsPoster`: `startReplaceGroup`/`endReplaceGroup` emitted in the else branch too), so the following still keeps its slot and its `SkeletonAsyncImage`/`AsyncImagePainter` state. Drawn after the player, it stays over the SurfaceView hole. |
| D2 | How does the still give way to video? | `Modifier.graphicsLayer { alpha = ... }` on the whole `PosterStill` modifier (before `.placeholder`, so the layer also covers `SkeletonAsyncImage`'s 0.32 `surfaceVariant` background). `fade = animateFloatAsState(if (trailerUrl != null && trailerRendered) 0F else 1F, spec)` with `tween(POSTER_FADE_OUT_MS, FastOutSlowInEasing)` towards 0 and `snap()` towards 1; the lambda reads `if (trailerUrl == null \|\| !trailerRendered) 1F else fade.value`. Replaces `AnimatedVisibility` and the second copy. | Rests at exactly 0 (AC-7). The composition-time `1F` makes the return happen in the same frame the player leaves; `animateFloatAsState` alone would lag one frame and show an empty panel (AC-3). Lambda read keeps the animation in the draw phase. |
| D3 | How is the old poster held across `Loading` without hiding a card that has no poster? | New parameter `isLoading: Boolean` on `VideoDetailsPoster`, passed `state.isLoading` from `VideoItemGridDetails` (side-by-side call) and `VideoDetailsMedia` (both calls). A private `rememberHeldUrls(imageUrls, isLoading)` returns the last committed list when `isLoading && imageUrls.isEmpty()`, else `imageUrls`; the last value lives in a plain `remember { }` holder updated in `SideEffect`. `urlIndex` and the request are keyed on the returned list. | `Loading.imageUrl = ""` and a loaded item with no posters (`VideoItemUIMapper.kt:89` `firstOrEmpty()`) look the same by URL alone; only `isLoading` separates them. Returning the same list instance keeps `urlIndex` and the request untouched through `Loading`, so Coil does nothing until the new card lands; the new list resets `urlIndex` to 0 as on base (AC-6). No snapshot write in composition. |
| D4 | How does A crossfade to B, warm or cold? | `rememberPosterRequest` builds with `useExistingImageAsPlaceholder(true)` (`@OptIn(ExperimentalCoilApi::class)`) and `.transitionFactory(PosterCrossfade)` instead of `crossfade(true)`. `PosterCrossfade` is a private `Transition.Factory` returning `CrossfadeTransition(target, result, POSTER_CROSSFADE_MS)` for a `SuccessResult` and `Transition.Factory.NONE.create(target, result)` otherwise. | Coil 3.6.3 `CrossfadeTransition.Factory.create` returns `NONE` on `DataSource.MEMORY_CACHE`, with no flag to force it (coil-core-android-3.6.3 bytecode), so warm changes would hard-cut. A memory hit still passes the old image as the start: `RealImageLoader.execute` calls `target.onStart` before the interceptor chain, the Compose target turns that into `State.Loading(currentPainter)` when `useExistingImageAsPlaceholder` is set, and `maybeNewCrossfadePainter` starts from a previous `Loading` painter for any factory whose result `instanceof CrossfadeTransition` (coil-compose-core-android-3.6.3 bytecode). The ux-designer's fallback (Compose-level crossfade) is not needed. |
| D5 | Opt-in on `SkeletonAsyncImage` (Q2) | New last parameter `skeletonOnlyUntilFirstImage: Boolean = false`. A `hasShownImage` flag, `remember(model != null)`, set in `onSuccess`; the skeleton box shows `isLoading && !(skeletonOnlyUntilFirstImage && hasShownImage)`. Only `PosterStill` passes `true`. | Default unchanged for every other caller (AC-9). Keying on `model != null` restores the skeleton after a no-poster card, where `AsyncImage` left composition and has no image to keep. |
| D6 | Constants | `POSTER_FADE_OUT_MS = 300`, `POSTER_CROSSFADE_MS = 200`, private in `VideoItemGridDetails.kt` (design-ux-designer.md: Transitions). | One component drives all three screens. |

### Changes by layer

| Layer | Change |
|-------|--------|
| API / repository | N/A: no data change. |
| ViewModel state and actions | N/A: `ContentListVM.kt:71` and `FavoriteVM.kt:110` keep publishing `Loading` (Q1). |
| Screen and DI module | N/A. |
| Compose content | See the rows below. |
| Resources | N/A. |

| File | Symbol | Change |
|------|--------|--------|
| `core/ui/uikit/component/SkeletonAsyncImage.kt:20` | `SkeletonAsyncImage` | Add `skeletonOnlyUntilFirstImage` (D5); `onSuccess` at `:45` also sets `hasShownImage`; skeleton `Box` at `:60-64` uses the new visibility. |
| `core/ui/uikit/component/details/VideoItemGridDetails.kt:243` | `VideoItemGridDetails` side-by-side `VideoDetailsPoster` call | Pass `isLoading = state.isLoading`. |
| `VideoItemGridDetails.kt:272`, `:288` | `VideoDetailsMedia` calls of `VideoDetailsPoster` | Pass `isLoading = state.isLoading`. |
| `VideoItemGridDetails.kt:416` | `VideoDetailsPoster` | Add `isLoading` (D3). `:433-440` wrap `imageUrls` with `rememberHeldUrls`. Replace `:444-491` with the D1 order and the D2 alpha; keep `onError` advancing `urlIndex` on the single still. Scrims `:493-544` unchanged and still after the still. |
| `VideoItemGridDetails.kt:556` | `rememberPosterRequest` | D4 builder; update the KDoc at `:547-554` (one still, not two copies). |
| `VideoItemGridDetails.kt:571` | `PosterStill` | Pass `skeletonOnlyUntilFirstImage = true`; keep the outer `placeholder(visible = request == null)` at `:580` (AC-5). |
| `VideoItemGridDetails.kt:3-5` | imports | Drop `AnimatedVisibility`, `EnterTransition`, `fadeOut`, `coil3.request.crossfade`; add `animateFloatAsState`, `tween`, `snap`, `FastOutSlowInEasing`, `graphicsLayer`, `SideEffect`, `ExperimentalCoilApi`, `useExistingImageAsPlaceholder`, `transitionFactory`, `Transition`, `CrossfadeTransition`, `SuccessResult`. |

Callers `ContentListScreenContent.kt:166`, `FavoriteScreenContent.kt:99`, `DetailsScreenContent.kt:189` are unchanged; they pick the fix up through `state` (AC-8).

### Alternatives rejected

| Alternative | Reason |
|-------------|--------|
| Hold the last non-null request regardless of `isLoading` | Keeps the previous card's poster forever on a card with no posters. |
| `key("still") { }` around the still | Redundant with the compiler's replace group (D1). |
| `Modifier.zIndex` to draw a still composed before the player | Relies on SurfaceView hole order under zIndex; positional order already works on base. |
| Compose-level crossfade between two stills | Coil already gives old to new once the factory is forced (D4). |
| `crossfade(true)` plus `useExistingImageAsPlaceholder` | Memory hits hard-cut (D4). |
| Remove the still after the fade instead of alpha 0 | Re-creates it on trailer end, which is root cause #3. |

### Risks

| # | Risk | Lives in | Mitigation |
|---|------|----------|------------|
| R1 | Trailer moments cannot be captured: `screencap` blanks the SurfaceView, so AC-1, AC-2, AC-3, AC-7 rest on a person watching. | evidence | qa reports them `NOT CHECKED` unless the owner watches; ux-designer's screencap rows cover the non-video parts. |
| R2 | A one-frame gap when the trailer ends if the alpha is read only from the animation. | `VideoDetailsPoster`, D2 | Composition-time `1F` when no trailer is rendered, plus `snap()` back. |
| R3 | A failed new URL: the old poster stays as Coil's placeholder until the error lands, then `isFailed` alpha 0 hides it and `urlIndex` advances; the fallback loads without a start image (Error state carries no painter). | `SkeletonAsyncImage.kt:49-53`, `VideoDetailsPoster` `onError` | Accepted; AC-6 holds once the error arrives. |
| R4 | Recomposition: alpha read in composition would recompose the panel every animation frame. | D2 | Read `fade.value` inside the `graphicsLayer` lambda; hold the `State`, not a delegated value. |
| R5 | `useExistingImageAsPlaceholder` is `@ExperimentalCoilApi`; the `Loading(existing painter)` path and `instanceof CrossfadeTransition` check are internal to coil-compose 3.6.3. | `rememberPosterRequest` | A Coil bump must re-check AC-4 on device. |
| R6 | Memory: the still keeps its bitmap while the trailer plays (base dropped the under-still). | `VideoDetailsPoster` | One wide poster per panel; negligible. |
| R7 | The previous card's poster shows under the new card's text skeleton for one detail fetch. | D3 | Accepted in brief.md Risks. |
| R8 | No Compose test harness exists in `app/src/test`, so D1 identity and D3 hold have no automated check. | tests | See Q1. |

### Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Add a Robolectric/Compose UI test harness to cover `rememberHeldUrls` and the still's identity? | No; extract `rememberHeldUrls`'s choice as a pure function only if the test designer wants a unit test, and verify the rest on device. | no |

### ux-designer

ux-designer, 2026-11-19, base 76a8bc2a. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `docs/`.

### Governing stand

No stand governs transitions. `docs/design/catalogue/` and `docs/design/content/` fix the panel's and the hero's geometry and scrims. Neither of them, nor `player-controls`, states any motion (`docs/design/catalogue/README.md` lists a `media` toggle only, see research.md: Related work). This run changes no layout, so it is not blocked. The durations below are new numbers, taken from the app's existing motion. The owner judges them by eye at gate 2 (brief.md#AC-4).

### Transitions

| Moment | Base (76a8bc2a) | Target | Duration / easing |
|---|---|---|---|
| Trailer URL arrives, before the first frame | A new over-copy shows an opaque `PurpleGrey40` skeleton frame, then the poster | The same still stays on screen at alpha 1. Nothing changes visibly. | none |
| First video frame | `AnimatedVisibility` `fadeOut()`: default spring, `StiffnessMediumLow` | The still's alpha goes from 1 to 0 over the playing video and rests at exactly 0 (brief.md#AC-7) | `tween(300, easing = FastOutSlowInEasing)`. 300 ms matches `BUFFERING_FADE_IN_MS` (`PlayerProgressBar.kt:335`), and the easing matches the panel motion (`EpisodesPanel.kt:173`, `PlayerSidePanel.kt:114`). |
| Trailer ends, or focus leaves a playing card | The under-still is re-created: skeleton frame, then the poster | The still's alpha snaps back to 1 in the frame the player leaves. The SurfaceView hole closes in that same frame, so there is no video to fade from. | instant |
| Card A to card B (warm or cold) | Poster A, then a full skeleton (`Loading`, `imageUrl = ""`), then B crossfading in from nothing | Poster A is held through `Loading`, then crossfades directly to B | 200 ms, linear: Coil's default (`CrossfadeTransition.Factory`, `sipush 200`), which is what cold loads already use. One number for warm and cold. |
| First poster after the screen opens | Skeleton until `onSuccess`, then the image | unchanged (brief.md#AC-5) | unchanged |

Named constants: `POSTER_FADE_OUT_MS = 300` and `POSTER_CROSSFADE_MS = 200`, both private to `VideoItemGridDetails.kt`. The three callers share one component and must move together, so these are not per-screen parameters.

### Finding for the architect: warm card changes would hard-cut

Coil 3.6.3 `CrossfadeTransition.Factory.create` returns `Transition.Factory.NONE` when `result.dataSource == MEMORY_CACHE` (from the bytecode of `coil-core-android-3.6.3.aar`). The factory has no `alwaysCrossfade` flag. Catalogue wide posters are prefetched (`ContentListScreenContent.kt:157-163`), so most A-to-B changes are memory hits. With `crossfade(true)` plus `useExistingImageAsPlaceholder(true)`, those changes would be an instant cut, which fails the warm half of brief.md#AC-4.

The Compose painter accepts any factory whose `create` returns a `CrossfadeTransition` (`AsyncImagePainter_androidKt.maybeNewCrossfadePainter`: `instanceof CrossfadeTransition`). The smallest fix is a small `Transition.Factory` that always returns `CrossfadeTransition(target, result, POSTER_CROSSFADE_MS)`, set through `.transitionFactory(...)` on the poster request only. Open: whether a synchronous memory hit still passes through a `Loading` state that carries the existing image as the start painter. If it does not, the crossfade starts from empty and the fix falls back to a Compose-level crossfade between the held and the new still. The implementer verifies this on the device.

### Must look unchanged

| Item | Value | Where |
|---|---|---|
| Layer order | player, then the still, then the horizontal scrim or the 48 dp side gradient, then the bottom fade. Scrims stay over the video at all times. | `VideoItemGridDetails.kt:446-545` |
| Catalogue scrim | edge 0.10 at alpha 0.90, clear at 0.45, bottom fade 50 dp | `MediaScrim.Catalogue`, `:101-106` |
| Details scrim | edge 0.10 at alpha 0.50, clear at 0.50, bottom fade 48 dp | `MediaScrim.Details`, `:108-113` |
| Media width | catalogue 0.75; details hero 0.655 | `:75`; `DetailsScreenContent.kt:830` |
| Catalogue frame | 675 x 380 dp at 16:9, overflowing the 270 dp panel by 110 dp | `docs/design/catalogue/README.md`, `ExpandedMediaLayout` |
| Details hero media | 628.8 x 418 dp at x 331.2 | `docs/design/content/README.md` |
| Still scale and alignment | `ContentScale.FillWidth`; `TopCenter` when `alignMediaToTop`, otherwise `Center` | `PosterStill`, `:570-585` |
| Trailer scale | catalogue fit and top-aligned | `ContentListScreenContent.kt:166-174` |
| Skeleton | `PurpleGrey40`, 1200 ms fade highlight; `SkeletonAsyncImage` default behaviour for every other caller (brief.md#AC-9) | `Placeholder.kt:19`, `SkeletonAsyncImage.kt` |

Alpha must apply to the whole `PosterStill`, including `SkeletonAsyncImage`'s `surfaceVariant` 0.32 background (`SkeletonAsyncImage.kt:33`). If only the `AsyncImage` fades, that tint stays over the playing trailer and greys it.

### Focus

N/A: no new focusable element. The still and the player are not focusable, and focus order and focus visibility stay as on base.

### Shared constants

`MediaScrim`, `FullBleedMediaWidthFraction`, `ScrimAlphaAtTextEdge`, `ScrimEndFraction`, `LocalSkeletonColor` and the `SkeletonAsyncImage` defaults are shared by the catalogue, the details hero and favourites (and by grids, rows and the hero carousel for the skeleton). This run must not retune any of them. The keep-previous and skeleton-only-first behaviour is a new `SkeletonAsyncImage` parameter that defaults to off (state.md Q2).

### Device check after implementation

`screencap` does not capture the SurfaceView (`.claude/skills/designing-a-screen/SKILL.md`), so the trailer moments are checked by a person watching. What I measure:

| Check | How | Expected |
|---|---|---|
| Panel at rest, before the trailer, on the catalogue, the details hero and favourites | screencap, px / 2 | Frame, scrim stops and bottom fade as in the "unchanged" table, within 1 dp |
| Mid card change (screencap within 150-350 ms of a D-pad Right) | burst of screencaps during repeated Right presses | No frame where the media area is uniform `PurpleGrey40` (#625B71), except on the first focus after opening the screen |
| Trailer playing, after the fade | screencap: the video area is the white hole | The hole fills the fitted video rect, and no poster pixels sit around it in the letterbox (brief.md#AC-7) |
| Trailer playing | screencap | No `surfaceVariant` tint over the hole region, and the scrims and bottom fade are still drawn over it |
| Trailer start, fade, end and warm/cold A-to-B crossfade | owner watching the emulator; `adb screenrecord` attempted once to see if it captures the video layer | 300 ms fade with no black or skeleton frame; 200 ms crossfade on warm changes as well, with no hard cut |

## Test design

Base 76a8bc2a. ACs: `brief.md#Acceptance criteria`; decisions D1-D6 and risks R1-R8: `design-architect.md`. Levels from `.flow/roles/test-designer.md`. No Compose UI harness this run (state: architect Q1); the decisions D2, D3, D4 and D5 are pulled into pure seams with JUnit 5 unit tests, everything rendered goes to `device`.

### Test seams the implementer exposes

All `internal`, no Compose runtime needed; the composables call them, so the unit tests exercise the shipped logic.

| Seam | File | Replaces |
|---|---|---|
| `heldPosterUrls(previous: List<String>?, current: List<String>, isLoading: Boolean): List<String>` | `VideoItemGridDetails.kt` | D3 choice inside `rememberHeldUrls` |
| `posterFadeTarget(hasTrailer: Boolean, trailerRendered: Boolean): Float` and `posterStillAlpha(hasTrailer: Boolean, trailerRendered: Boolean, fade: Float): Float` | `VideoItemGridDetails.kt` | D2 target and `graphicsLayer` lambda |
| `PosterCrossfade` (`Transition.Factory`) | `VideoItemGridDetails.kt` | D4 factory, `internal` instead of `private` |
| `skeletonVisible(isLoading: Boolean, skeletonOnlyUntilFirstImage: Boolean, hasShownImage: Boolean): Boolean` | `SkeletonAsyncImage.kt` | D5 skeleton `Box` condition |

Test files: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` and `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImageVisibilityTest.kt`. Style of `FocusTargetResolverTest` (plain JUnit 5, no dispatcher extension needed).

### Risk profile

| Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|
| Warm A-to-B change hard-cuts: Coil's crossfade factory returns `NONE` on a memory hit | D4, ux finding | 3 | 2 | 6 | `posterCrossfade_returnsCrossfade_whenResultFromMemoryCache`; M4 |
| Hold ignores `isLoading`: a card with no posters keeps the previous card's poster | D3 | 2 | 3 | 6 | `heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters` |
| Still loses slot identity across trailer start/stop, skeleton returns (root causes #2, #3) | D1, brief Risks | 2 | 3 | 6 | M2, M3 (no harness; device is the level where it fails) |
| Trailer moments not capturable by `screencap` | R1 | 3 | 2 | 6 | M2, M3, M5 need the owner watching; otherwise `NOT CHECKED` |
| Held list is a new instance: `urlIndex` and request reset during `Loading` | D3 | 2 | 2 | 4 | `heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls` |
| One-frame empty panel when the trailer ends | R2 | 2 | 2 | 4 | `posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero`; M3 |
| `surfaceVariant` tint left over the playing trailer | ux delta | 2 | 2 | 4 | M5 screencap |
| Failed new URL freezes on the old poster | R3, AC-6 | 2 | 2 | 4 | `heldPosterUrls_returnsCurrent_whenNewCardArrivesDuringLoading`; M6 |
| Coil bump changes the experimental placeholder path | R5 | 2 | 2 | 4 | `posterCrossfade_*` tests; M4 on any Coil bump |
| Still not at alpha 0 at rest, poster shows around a fitted trailer | AC-7 | 1 | 3 | 3 | `posterFadeTarget_isZero_whenTrailerRendered`; M5 |
| Default `SkeletonAsyncImage` behaviour changes for grids, rows, hero | AC-9 | 1 | 3 | 3 | `skeletonVisible_staysVisibleWhileLoading_whenOptInOff` |
| Skeleton not restored after a no-poster card (`remember(model != null)`) | D5 | 2 | 1 | 2 | Accepted: the outer `placeholder(request == null)` covers that frame |
| Alpha read in composition recomposes per frame | R4 | 2 | 1 | 2 | Accepted: review item |
| Poster bitmap held during the trailer | R6 | 1 | 1 | 1 | Accepted |
| Old poster under the new text skeleton | R7 | 1 | 1 | 1 | Accepted in brief |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-4 | unit | P0 red | `heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls` | Fails if the hold is removed (root cause #1: empty list, skeleton) or if a copy is returned (`urlIndex`/request rebuilt during `Loading`); asserts `assertSame` |
| AC-4 | unit | P0 red | `heldPosterUrls_returnsCurrent_whenNewCardArrivesDuringLoading` | Fails if a non-empty list during `isLoading` is swallowed by the hold (details hero loads with a URL) |
| AC-6 | unit | P0 red | `heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters` | Fails if the hold ignores `isLoading` (rejected alternative 1): previous poster stays on a no-poster card |
| AC-5 | unit | P0 red | `heldPosterUrls_returnsCurrent_whenNothingHeldYet` | Fails if a null `previous` is not handled: first focus must show the empty list and so the skeleton |
| AC-3 | unit | P0 red | `posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero` | Fails if the lambda returns `fade` alone (R2: one empty frame on trailer end) |
| AC-1 | unit | P0 red | `posterStillAlpha_isOpaque_whenTrailerNotRenderedYet` | Fails if the still fades before the first frame (black shutter shows) |
| AC-2, AC-7 | unit | P0 red | `posterFadeTarget_isZero_whenTrailerRendered` | Fails if the target at rest is not exactly 0F (poster around a letterboxed trailer) |
| AC-2 | unit | P1 | `posterStillAlpha_followsFade_whenTrailerRendered` | Fails if the animated value is ignored (hard cut instead of the 300 ms fade) |
| AC-1, AC-3, AC-4 | unit | P0 red | `skeletonVisible_hidden_whenOptInAndImageShownBefore` | Fails if the opt-in does not suppress the skeleton after the first image (root causes #2, #3) |
| AC-5 | unit | P0 red | `skeletonVisible_shown_whenOptInButNoImageYet` | Fails if the opt-in hides the first-load skeleton |
| AC-9 | unit | P0 red | `skeletonVisible_staysVisibleWhileLoading_whenOptInOff` | Fails if the default path changes for other callers (`hasShownImage = true`, opt-in off, loading: visible) |
| AC-4 | unit | P1 | `posterCrossfade_returnsCrossfade_whenResultFromMemoryCache` | Fails if `crossfade(true)`'s factory is used: Coil returns `NONE` on `MEMORY_CACHE` (warm hard cut); asserts `is CrossfadeTransition` |
| AC-4 | unit | P1 | `posterCrossfade_returnsCrossfade_whenResultFromNetwork` | Fails if the factory only handles memory hits (cold change cuts) |
| AC-6 | unit | P1 | `posterCrossfade_returnsNone_whenResultIsError` | Fails if an `ErrorResult` is crossfaded (fallback URL would fade from a stale painter) |
| AC-10 | unit | P0 guard | `ContentListVMTest`, `FavoriteVMTest`, `DetailsVMTrailerPreviewTest` unchanged | Fails if the change leaks into VM state (`git diff 76a8bc2a -- app/src/test/kotlin/com/kino/puber/ui/feature` shows no edit to these files) |
| AC-10 | unit | P0 guard | `check.full` in the worktree | Fails on any compile, detekt or lint regression (incl. missing `@OptIn(ExperimentalCoilApi::class)`) |
| AC-1 | device | P0 | M2 | Skeleton or black frame between URL arrival and first frame |
| AC-2 | device | P0 | M2 | Flash between poster and video at the fade |
| AC-3 | device | P0 | M3 | Skeleton frame or empty panel on trailer end or focus leave |
| AC-4 | device | P0 | M1, M4 | Skeleton or hard cut on warm and cold A-to-B |
| AC-5 | device | P1 | M1 step 1 | First-load skeleton missing |
| AC-6 | device | P2 | M6 | Old poster frozen after a failed URL |
| AC-7 | device | P0 | M5 | Poster pixels or tint around the fitted video |
| AC-8 | device | P1 | M7 | Behaviour differs on the details hero or favourites |

`posterCrossfade_*` build an `ImageRequest` with `mockk<Context>(relaxed = true)`, a fake `TransitionTarget` and a fake `coil3.Image`. If Coil cannot build them off-device (no Android stubs in `app/build.gradle.kts` `testOptions`), the implementer drops these three and AC-4 warm rests on M4; record that in the plan.

### Failing-first test

`heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls`, written first. On base the choice is inline (`git show 76a8bc2a:app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt`, lines 433-440 build `imageUrls` from `imageUrl` alone), so `Loading`'s `""` yields an empty list: the test does not compile on base and asserts the held list after. The bug itself is only visible on device (M1, M4).

### Manual checks (device)

Owner watching the emulator for M2, M3, M5 video moments and M4 smoothness (`screencap` blanks the SurfaceView, R1); attempt `adb -s <serial> shell screenrecord` once and record whether it captures the video layer. Auto-trailer on. Keys: `adb -s <serial> shell input keyevent` with `DPAD_RIGHT`, `DPAD_LEFT`, `DPAD_DOWN`, `DPAD_CENTER`, `BACK`.

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| M1 | 1. Launch, open a catalogue grid. 2. Watch the panel on first focus. 3. `DPAD_RIGHT` five times, under 1 s apart; screencap burst 150-350 ms after each press. | Step 2: skeleton until the first poster (AC-5). Step 3: each poster crossfades from the previous one. | Any burst frame with the media area uniform `#625B71`; an empty frame between posters |
| M2 | 1. Rest focus on a card with a trailer for over 2 s. 2. Watch from URL arrival to first frame and through the fade. | Poster stays at full opacity, then fades out over the playing video in about 300 ms | Skeleton frame, black shutter frame, a flash at the switch |
| M3 | 1. Let a trailer play to its end. 2. Start another trailer, then `DPAD_RIGHT` while it plays. | In both cases the poster is back in the same frame the video leaves; in 2 it then crossfades to the next card | Skeleton frame, empty panel frame, focus loss |
| M4 | 1. Scroll a row once so posters are prefetched (warm). 2. `DPAD_RIGHT`/`DPAD_LEFT` repeatedly. 3. `DPAD_DOWN` into a row not visited yet (cold) and repeat. | 200 ms crossfade on warm and cold changes | Hard cut on warm changes; skeleton between posters |
| M5 | 1. Play a fitted (letterboxed) catalogue trailer. 2. Screencap after the fade. | Hole fills the fitted rect; scrims and bottom fade drawn over it | Poster pixels in the letterbox; `surfaceVariant` tint over the hole |
| M6 | 1. Focus a card whose first wide poster URL fails (block its host or pick a known broken item). | Next URL loads; previous card's poster is gone | Previous poster frozen; crash |
| M7 | 1. Open a details screen with a trailer, repeat M2 and M3 on the hero. 2. Open favourites, repeat M1 and M4 on its panel. | Same results as M1-M4 | Any difference from the catalogue behaviour; focus loss on return (`BACK`) |

M6 needs a reproducible broken URL; if none is available, qa reports it `NOT CHECKED` and AC-6 rests on the unit rows.

### perf track

N/A: perf track off.

### UNTESTABLE

None. AC-1, AC-2, AC-3, AC-4 smoothness and AC-7 at the video layer depend on a person watching (R1).

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Text-column skeleton too, or poster only? | yes | answered | Poster only |
| Q2 | Keep-previous-image behaviour global or opt-in? | no | answered | Opt-in, used only by PosterStill |
| Q3 | Move the trailer to a TextureView? | yes | answered | No; keep the SurfaceView |
| Q4 | Add a Compose UI test harness? | no | answered | No; pure-function seams with unit tests, device checks for the rest |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-19 | Q1: poster only (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Q2: opt-in on SkeletonAsyncImage, used only by PosterStill (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Q3: keep SurfaceView, option A (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Q4 (architect): no Compose UI test harness (recommended) | owner (standing instruction: recommended) |
| 2026-11-19 | Gate 1: approved as designed (M, ui, AC-1..AC-10) | owner (standing instruction: recommended) |
