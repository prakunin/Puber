# Brief: detail-poster-trailer-flicker

Base 76a8bc2a. Analyst, 2026-11-19. Builds on `research.md` (cited as research.md: section). The plugin has no brief template, so this brief uses the analyst job layout. Paths are relative to `app/src/main/java/com/kino/puber/`.

kind: bug. size: M. tracks: ui. Device verification: **yes** (TV UI, trailer surface layering; runtime-only ACs).

## Request
1. Remove the flash in the catalogue detail panel when the poster gives way to the trailer.
2. When cards are switched before a trailer starts, the poster should change through a smooth transition instead of a flash or a jump (research.md: Request).

## Root cause
The visible flash is the skeleton, not the video. The skeleton is opaque `PurpleGrey40` (`core/ui/uikit/component/modifier/Placeholder.kt:19`), and the panel draws it every time a still is created, or its request changes or becomes `null`.

| # | Moment | Cause | Status |
|---|--------|-------|--------|
| 1 | Card change | `ContentListVM.kt:71` publishes `VideoDetailsUIState.Loading` 150 ms after focus. Its `imageUrl = ""` (`VideoDetailsUIState.kt:28`) leaves `imageUrls` empty, so the request is `null` (`VideoItemGridDetails.kt:433-440, 557-567`). `PosterStill` then covers the whole panel with `placeholder(visible = request == null)` (`VideoItemGridDetails.kt:579`) until the details land. Next, the new poster crossfades from nothing (`crossfade(true)`, `:564`). `FavoriteVM.kt:110` follows the same path. | confirmed (code trace) |
| 2 | Trailer URL arrives | The over-copy inside `AnimatedVisibility` (`VideoItemGridDetails.kt:478-490`) is a new `SkeletonAsyncImage`. Its `isLoading` starts `true` (`SkeletonAsyncImage.kt:28`), and its skeleton is drawn over the image (`:60-64`) until `onSuccess`, even on a memory-cache hit. That one is drawn over the SurfaceView hole. | plausible (code trace; whether a cache hit clears it before the first draw is unverified) |
| 3 | Trailer ends or focus leaves a playing card | `stopTrailerPreview()` (`ContentListVM.kt:132-136`) sets `previewTrailerUrl = null`. The under-still was removed once the first frame rendered (`VideoItemGridDetails.kt:446`), so it is created fresh and starts on the skeleton as in #2. On focus loss, #1 follows 150 ms later. | confirmed (code trace) for re-creation; skeleton frame as #2 |
| 4 | First video frame | The over-copy runs `fadeOut()` (`VideoItemGridDetails.kt:481`). This is the intended transition and not a defect. | confirmed (code trace) |

None of this has been observed on a device. `screencap` blanks the video layer (research.md: Related work), so the first frames only show to a human watching.

## Scope
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

## Risks
- Size M, not research's S: one shared component drives three screens, the change reorders the trailer surface layering, and Q1 and Q3 are open design decisions.
- Evidence: the trailer ACs (AC-1, AC-2, AC-3, AC-7) need a person watching the emulator or TV. Whether `adb screenrecord` captures the SurfaceView layer is unverified (research.md: Risks). The qa will have to report them `NOT CHECKED` unless the owner watches.
- Composable identity: keeping one still across trailer start and stop depends on the conditional player not shifting the still's group. The still must keep its position, or carry an explicit `key`, for its `SkeletonAsyncImage` state to survive.
- Fitted trailer (`85bfd1d2`): at partial alpha during the fade, the larger poster shows around a letterboxed trailer, as the current `fadeOut` already does. At rest the alpha must be 0, or the still must be removed after the fade.
- A held request plus a failed new URL must not freeze on the old poster. `urlIndex` is reset by `remember(imageUrls)` (`VideoItemGridDetails.kt:438`).
- For up to one detail fetch, the old poster shows under the new card's text skeleton.
- `useExistingImageAsPlaceholder` is `@ExperimentalCoilApi` (Coil 3.6.3, `gradle/libs.versions.toml:25`). It needs an opt-in and may change in a Coil bump.

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | Should the text column's skeleton flash on card change also go (VM keeps or seeds details instead of `Loading`), or only the poster? | Poster only. If the owner still sees the text flash afterwards, open a separate idea run. | yes |
| Q2 | Should "keep the previous image, skeleton only on first load" be global in `SkeletonAsyncImage` or opt-in for the detail poster? | Opt-in, used only by `PosterStill`. | no |
| Q3 | Should the trailer move to a TextureView for a true video fade-in? | No. Keep the SurfaceView for TV resolution and power, and fade the still over the playing video. | yes |
