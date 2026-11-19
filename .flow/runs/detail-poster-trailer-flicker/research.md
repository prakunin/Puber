# Research: detail-poster-trailer-flicker

## 1. Request

Translated from Russian: "In the catalogue (e.g. Series), when focus rests on a card, the detail panel's poster is replaced by the trailer, and the screen visibly flashes at the moment the poster gives way to video playback. Make that transition unnoticeable. Second: when switching cards fast enough that the trailer never starts, posters change with a flash/jump. Make it a smooth transition."

## 2. Understanding

The catalogue detail panel (and the details-screen hero, which uses the same component) shows a still that a trailer replaces after a 2 s dwell. Every time the panel's still is created or its URL changes, it starts on an opaque skeleton frame. That happens when the trailer starts, when it ends or is stopped, and on every card change, where the VM also clears the panel to `Loading` (empty image URL) before the next card's details arrive. The flashes the owner sees are these skeleton frames, not the video itself. Both requests can be fixed in the poster composable without dropping the `SurfaceView`.

## 3. Current state in code

Paths are relative to `app/src/main/java/com/kino/puber/`.

| Where | What it does now |
|---|---|
| `core/ui/uikit/component/details/VideoItemGridDetails.kt:446-459` | The "under" still is composed only while `trailerUrl == null \|\| !trailerRendered`. It leaves composition on the first frame and is created fresh when the trailer ends or focus moves, which costs a new `SkeletonAsyncImage` with a skeleton frame. |
| `VideoItemGridDetails.kt:461-471` | `TrailerPreviewPlayer` is added only when `trailerUrl != null`. |
| `VideoItemGridDetails.kt:473-490` | A second `PosterStill` inside `AnimatedVisibility(visible = !trailerRendered, enter = None, exit = fadeOut())`, drawn after the player so that it shows over the SurfaceView hole. It is a new composable instance at the moment the trailer URL arrives. |
| `VideoItemGridDetails.kt:442` | `trailerRendered` is `remember(trailerUrl)`, so it resets on every URL change. |
| `VideoItemGridDetails.kt:432-438, 556-568` | Fallback URL list plus `rememberPosterRequest`: one `ImageRequest` per URL with `crossfade(true)`. An empty `imageUrl` gives a `null` request. |
| `VideoItemGridDetails.kt:570-585` | `PosterStill` wraps `SkeletonAsyncImage` with an outer `placeholder(visible = request == null)`. |
| `core/ui/uikit/component/SkeletonAsyncImage.kt:28` | `isLoading` is `remember(model) { model != null }`, so it starts `true` for every new instance or model, even on a Coil memory-cache hit. |
| `SkeletonAsyncImage.kt:60-64` | The skeleton `Box` is drawn **over** the `AsyncImage` until `onSuccess` arrives (at least one frame). |
| `core/ui/uikit/component/modifier/Placeholder.kt:19` | The skeleton colour is `PurpleGrey40`, which is opaque. This is the flash. |
| `SkeletonAsyncImage.kt:33` | The background is `surfaceVariant` at 0.32 alpha. Over the SurfaceView hole it lets the black shutter through. |
| `core/ui/uikit/component/details/TrailerPreviewPlayer.kt:125-139` | `PlayerView` in `AndroidView`, default surface (SurfaceView), shutter black until `onRenderedFirstFrame` (`:85-87`). |
| `ui/feature/contentlist/vm/ContentListVM.kt:61-94` | On focus: `previewTrailerUrl = null` at once, then after `FOCUS_DETAILS_DEBOUNCE_MS = 150` (`:221`) `selectedItem = VideoDetailsUIState.Loading` (`:71`), then the mapped details. |
| `core/ui/uikit/component/details/VideoDetailsUIState.kt:24-40` | `Loading.imageUrl = ""`, so the request is `null` and the panel shows a full skeleton between two posters. Details are often prefetched warm (`ContentListScreenContent.kt:157-163`), so the skeleton lasts only a few frames, which reads as a flash. |
| `core/ui/model/VideoItemUIMapper.kt:89-90` | The detail still is `widePosterUrls.first`, with fallbacks `wide.drop(1) + big`. |
| `ui/feature/contentlist/content/ContentListScreenContent.kt:166-174` | Catalogue: `fullBleedMedia = true, expandMediaIntoContent = true`, so the trailer is `scaleToFit` and top-aligned (`VideoItemGridDetails.kt:272-282`). |
| `ui/feature/details/component/DetailsScreenContent.kt:189-196` | The details hero uses the same `VideoDetailsMedia` with a trailer, so any change here affects it too. |
| `ui/feature/favorites/content/FavoriteScreenContent.kt:99` | Favourites: same panel without a trailer. Card changes go through `FavoriteVM.kt:110` → `Loading`, the same poster flash. |

Flash sequences derived from the code (not yet observed on a device):

| Moment | What is drawn |
|---|---|
| Trailer URL arrives (2 s dwell) | The SurfaceView hole opens, and the fresh over-copy shows an opaque skeleton for at least one frame, then a placeholder fade to the poster. |
| First video frame | The over-copy fades out (`fadeOut()` default). This is the only intended transition. |
| Trailer ends, or focus moves off a playing card | The player is removed and the under-still is re-created: skeleton, then the old poster. |
| Card change | Old poster, then (after 150 ms) skeleton (`Loading`), then the new poster with a Coil crossfade from nothing. |

Other users of `SkeletonAsyncImage` that a change to it would also affect:

- `ui/feature/collections/component/CollectionCard.kt:65`
- `core/ui/uikit/component/HeroCarousel.kt:233`
- `core/ui/uikit/component/moviesList/VideoItemHorizontal.kt:104`
- `core/ui/uikit/component/moviesList/VideoItemUIState.kt:194`, the `VideoItem` card on every grid and row

Tests:

- No Compose or UI test covers `VideoDetailsPoster`, `SkeletonAsyncImage` or `TrailerPreviewPlayer`.
- `src/test/.../contentlist/vm/ContentListVMTest.kt` and `FavoriteVMTest.kt` cover the `Loading` publication path.
- `DetailsVMTrailerPreviewTest.kt` covers the details trailer gate.

## 4. Related work

| Ref | Relevance |
|---|---|
| Run `focus-trailer-preview` (done), plan `docs/superpowers/plans/2026-08-19-focus-trailer-preview.md` | Introduced the trailer over the still (`5d196ed9`). |
| Commit `34652167` "Keep the still on screen until the trailer's first frame" | The reason for the double still: the PlayerView shutter is black and the SurfaceView punches a hole, so the still must be drawn after the player. It dropped a `fadeIn` that "could never animate a SurfaceView". |
| `docs/superpowers/evidence/2026-08-19-focus-trailer-preview-smoke.md` | Item I1 (still holds until the first frame) was never verified on hardware: `screencap` blanks the video layer, so it needs a human watching the TV. The same constraint applies to this run's evidence. |
| Commit `85bfd1d2` | Made the under-still conditional (`!trailerRendered`) so a fitted trailer does not show the larger poster around it. This is the source of the re-creation on trailer end. |
| Commit `c0663e0d` | Moved `PosterStill` onto `SkeletonAsyncImage` and added `rememberPosterRequest`. This is where the opaque skeleton frame entered the panel. |
| Commit `c472b068`, `50656a4d` | Trailer `scaleToFit` and top alignment in the catalogue. |
| Runs `catalogue-single-carousel`, `details-single-screen` (done) | Layout only; no decision on still/trailer layering. |
| `docs/design/catalogue/` stand | Has a `media` toggle (still / trailer / none) but no transition spec. |
| Run `media3-hagc-removal` (idea) | Touches the media3 version. It matters only if option B adds `media3-ui-compose`. |

Possible duplicate: none.

## 5. External findings

| Finding | Source |
|---|---|
| SurfaceView is preferred for video: lower power, accurate frame timing, HDR, and full display resolution on Android TV boxes whose UI layer is upscaled. TextureView is recommended only for animation before API 24. Since Android 14, PlayerView uses `SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT`. PlayerView inside `AndroidView` is "not guaranteed compatible" with Compose. | https://developer.android.com/media/media3/ui/surface (current docs, read 2026-11-19) |
| PlayerView's surface type is chosen by the XML attribute `surface_type` (`surface_view` / `texture_view`); there is no programmatic setter in the docs. Switching would need an inflated layout (setter absence `unverified` in 1.11.0 sources). | same page |
| `media3-ui-compose` offers `PlayerSurface` and `ContentFrame(player, surfaceType, contentScale, alignment, keepContentOnReset, artwork, subtitles, shutter)` with `SURFACE_TYPE_TEXTURE_VIEW`. The signature is from current docs; whether 1.11.0 has `alignment`/`subtitles` is `unverified`. The artifact is not a project dependency today (`gradle/libs.versions.toml:116-120`). | https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/ContentFrame.composable, https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/package-summary |
| Coil `ImageRequest.Builder.useExistingImageAsPlaceholder(true)` (`@ExperimentalCoilApi`, Compose only) uses the image currently shown by `AsyncImage` as the placeholder, so `crossfade` runs old → new. Added in Coil 3.4.0; the project has 3.6.3 (`gradle/libs.versions.toml:25`). | https://coil-kt.github.io/coil/api/coil-compose-core/coil3.compose/use-existing-image-as-placeholder.html, https://raw.githubusercontent.com/coil-kt/coil/main/CHANGELOG.md |
| Whether Coil 3 skips crossfade on a memory-cache hit, and whether `AsyncImage` resolves a memory hit within the first frame: `unverified`. `SkeletonAsyncImage` draws its skeleton for at least one frame either way. | N/A |

## 6. Options

| # | What | Trade-offs | Size |
|---|---|---|---|
| **A (recommended)** | Fix it in Compose and keep the SurfaceView. (1) In `VideoDetailsPoster`, compose `TrailerPreviewPlayer` conditionally **before** one still that is never removed, so the still keeps its identity while the trailer starts and stops. Fade it with an animated `graphicsLayer { alpha }` on `trailerRendered` instead of `AnimatedVisibility` plus a second copy. (2) Keep the last non-null poster request while the state's `imageUrl` is empty (`Loading`), and build requests with `useExistingImageAsPlaceholder(true)` + `crossfade`, so card changes crossfade old → new. (3) Give `SkeletonAsyncImage` an opt-in to show the skeleton only before the first successful image, used only by `PosterStill`. | Smallest change. No new dependency, and the TV keeps full-resolution video. The fade over the video is still Compose drawing over a hole, which already works for the gradients and scrim. The text column still flashes to its skeleton on card change. Uses an experimental Coil API. Fixes the catalogue, details hero and favourites at once. | S |
| B | Switch the trailer to a TextureView, either via `media3-ui-compose` `ContentFrame(surfaceType = SURFACE_TYPE_TEXTURE_VIEW, shutter = {})` or a PlayerView inflated from XML with `surface_type="texture_view"`. Then crossfade the video layer over a single still with ordinary alpha. Card-change crossfade as in A(2). | The truest crossfade: the video itself fades in. Costs more power and loses frame-timing accuracy, and on TVs that upscale a 1080p UI the trailer drops to UI resolution. Adds a new dependency (Compose path) or a layout resource. The details-hero and catalogue comments that depend on "drawn after the SurfaceView" would need revisiting. | M |
| C | A plus a VM change: `ContentListVM` / `FavoriteVM` stop publishing `VideoDetailsUIState.Loading` on card change. They keep the previous details, or seed the panel from the focused card's own `VideoItemUIState` (title, wide image), until the new details land. | Removes the text-column flash too. Touches VM state contracts and existing VM tests, and stale text could show briefly for the wrong card if not seeded. | M |

## 7. Risks

- **Evidence.** `adb screencap` blanks the SurfaceView (smoke evidence above), and it is `unverified` whether `adb screenrecord` captures the video layer. Acceptance needs a person watching the emulator or TV, or a recording method proven first.
- **Fitted trailer.** With A, a fully transparent still must not let the larger poster show around a letterboxed trailer, which is the concern behind `85bfd1d2`. Alpha 0 meets that; a partial alpha during the fade does not.
- **Fallback URLs.** Holding the previous request across `Loading` must not interfere with `urlIndex`, which is reset by `remember(imageUrls)`. A failed new URL must still advance to its fallback.
- **Shared component.** Changing `SkeletonAsyncImage` defaults would alter every card grid and the hero carousel. Keep the new behaviour opt-in.
- **Experimental API.** `useExistingImageAsPlaceholder` is `@ExperimentalCoilApi`, so it needs an opt-in and may change in a Coil bump.
- **Wrong poster for a moment.** Keeping the old poster while the new card's details load briefly shows the previous card's picture under the new card's text skeleton.

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Should the text column's skeleton flash on card change also go (option C), or posters only? | Posters only (option A). Record the text-column flash as a separate idea run if the owner still sees it after A. |
| Q2 | Should the "keep previous image, skeleton only on first load" behaviour be global in `SkeletonAsyncImage`, or limited to the detail poster? | Limited to the detail poster, through an opt-in parameter. |
| Q3 | Should the trailer move to a TextureView for a true video fade-in (option B)? | No. Keep the SurfaceView for TV resolution and power; the still fading over the playing video is enough. |

## 9. Sources

Files:

- `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImage.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/modifier/Placeholder.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoDetailsUIState.kt`
- `app/src/main/java/com/kino/puber/core/ui/model/VideoItemUIMapper.kt`
- `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListVM.kt`
- `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt`
- `app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt`
- `app/src/main/java/com/kino/puber/ui/feature/favorites/vm/FavoriteVM.kt`
- `app/src/main/java/com/kino/puber/data/repository/ItemDetailsRepository.kt`
- `gradle/libs.versions.toml`
- `docs/architecture/ui-and-compose.md`

Commits: `5d196ed9`, `34652167`, `85bfd1d2`, `c0663e0d`, `c472b068`, `50656a4d`.

Runs:

- `focus-trailer-preview`
- `catalogue-single-carousel`
- `details-single-screen`
- `media3-hagc-removal`

Docs:

- `docs/superpowers/plans/2026-08-19-focus-trailer-preview.md`
- `docs/superpowers/evidence/2026-08-19-focus-trailer-preview-smoke.md`
- `docs/design/catalogue/`

URLs:

- https://developer.android.com/media/media3/ui/surface
- https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/package-summary
- https://developer.android.com/reference/kotlin/androidx/media3/ui/compose/ContentFrame.composable
- https://coil-kt.github.io/coil/api/coil-compose-core/coil3.compose/use-existing-image-as-placeholder.html
- https://raw.githubusercontent.com/coil-kt/coil/main/CHANGELOG.md
