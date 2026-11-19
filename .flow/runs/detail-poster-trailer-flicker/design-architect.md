# Design (architect): detail-poster-trailer-flicker

Base 76a8bc2a, option A (state.md Q1-Q3). Paths are relative to `app/src/main/java/com/kino/puber/`. Timings and the visual delta are owned by `design-ux-designer.md`; this file cites them.

## Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | How does one still survive trailer start and stop? | `VideoDetailsPoster` composes `if (trailerUrl != null) TrailerPreviewPlayer(...)` first, then a single `PosterStill` call that is never conditional. No explicit `key`. | The compiler wraps an `if` with composable calls in a replace group on both branches (base bytecode of `VideoDetailsPoster`: `startReplaceGroup`/`endReplaceGroup` emitted in the else branch too), so the following still keeps its slot and its `SkeletonAsyncImage`/`AsyncImagePainter` state. Drawn after the player, it stays over the SurfaceView hole. |
| D2 | How does the still give way to video? | `Modifier.graphicsLayer { alpha = ... }` on the whole `PosterStill` modifier (before `.placeholder`, so the layer also covers `SkeletonAsyncImage`'s 0.32 `surfaceVariant` background). `fade = animateFloatAsState(if (trailerUrl != null && trailerRendered) 0F else 1F, spec)` with `tween(POSTER_FADE_OUT_MS, FastOutSlowInEasing)` towards 0 and `snap()` towards 1; the lambda reads `if (trailerUrl == null \|\| !trailerRendered) 1F else fade.value`. Replaces `AnimatedVisibility` and the second copy. | Rests at exactly 0 (AC-7). The composition-time `1F` makes the return happen in the same frame the player leaves; `animateFloatAsState` alone would lag one frame and show an empty panel (AC-3). Lambda read keeps the animation in the draw phase. |
| D3 | How is the old poster held across `Loading` without hiding a card that has no poster? | New parameter `isLoading: Boolean` on `VideoDetailsPoster`, passed `state.isLoading` from `VideoItemGridDetails` (side-by-side call) and `VideoDetailsMedia` (both calls). A private `rememberHeldUrls(imageUrls, isLoading)` returns the last committed list when `isLoading && imageUrls.isEmpty()`, else `imageUrls`; the last value lives in a plain `remember { }` holder updated in `SideEffect`. `urlIndex` and the request are keyed on the returned list. | `Loading.imageUrl = ""` and a loaded item with no posters (`VideoItemUIMapper.kt:89` `firstOrEmpty()`) look the same by URL alone; only `isLoading` separates them. Returning the same list instance keeps `urlIndex` and the request untouched through `Loading`, so Coil does nothing until the new card lands; the new list resets `urlIndex` to 0 as on base (AC-6). No snapshot write in composition. |
| D4 | How does A crossfade to B, warm or cold? | `rememberPosterRequest` builds with `useExistingImageAsPlaceholder(true)` (`@OptIn(ExperimentalCoilApi::class)`) and `.transitionFactory(PosterCrossfade)` instead of `crossfade(true)`. `PosterCrossfade` is a private `Transition.Factory` returning `CrossfadeTransition(target, result, POSTER_CROSSFADE_MS)` for a `SuccessResult` and `Transition.Factory.NONE.create(target, result)` otherwise. | Coil 3.6.3 `CrossfadeTransition.Factory.create` returns `NONE` on `DataSource.MEMORY_CACHE`, with no flag to force it (coil-core-android-3.6.3 bytecode), so warm changes would hard-cut. A memory hit still passes the old image as the start: `RealImageLoader.execute` calls `target.onStart` before the interceptor chain, the Compose target turns that into `State.Loading(currentPainter)` when `useExistingImageAsPlaceholder` is set, and `maybeNewCrossfadePainter` starts from a previous `Loading` painter for any factory whose result `instanceof CrossfadeTransition` (coil-compose-core-android-3.6.3 bytecode). The ux-designer's fallback (Compose-level crossfade) is not needed. |
| D5 | Opt-in on `SkeletonAsyncImage` (Q2) | New last parameter `skeletonOnlyUntilFirstImage: Boolean = false`. A `hasShownImage` flag, `remember(model != null)`, set in `onSuccess`; the skeleton box shows `isLoading && !(skeletonOnlyUntilFirstImage && hasShownImage)`. Only `PosterStill` passes `true`. | Default unchanged for every other caller (AC-9). Keying on `model != null` restores the skeleton after a no-poster card, where `AsyncImage` left composition and has no image to keep. |
| D6 | Constants | `POSTER_FADE_OUT_MS = 300`, `POSTER_CROSSFADE_MS = 200`, private in `VideoItemGridDetails.kt` (design-ux-designer.md: Transitions). | One component drives all three screens. |

## Changes by layer

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

## Alternatives rejected

| Alternative | Reason |
|-------------|--------|
| Hold the last non-null request regardless of `isLoading` | Keeps the previous card's poster forever on a card with no posters. |
| `key("still") { }` around the still | Redundant with the compiler's replace group (D1). |
| `Modifier.zIndex` to draw a still composed before the player | Relies on SurfaceView hole order under zIndex; positional order already works on base. |
| Compose-level crossfade between two stills | Coil already gives old to new once the factory is forced (D4). |
| `crossfade(true)` plus `useExistingImageAsPlaceholder` | Memory hits hard-cut (D4). |
| Remove the still after the fade instead of alpha 0 | Re-creates it on trailer end, which is root cause #3. |

## Risks

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

## Open questions

| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Add a Robolectric/Compose UI test harness to cover `rememberHeldUrls` and the still's identity? | No; extract `rememberHeldUrls`'s choice as a pure function only if the test designer wants a unit test, and verify the rest on device. | no |
