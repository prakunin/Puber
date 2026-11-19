# Design delta: detail-poster-trailer-flicker

ux-designer, 2026-11-19, base 76a8bc2a. Paths are relative to `app/src/main/java/com/kino/puber/` unless they start with `docs/`.

## Governing stand

No stand governs transitions. `docs/design/catalogue/` and `docs/design/content/` fix the panel's and the hero's geometry and scrims. Neither of them, nor `player-controls`, states any motion (`docs/design/catalogue/README.md` lists a `media` toggle only, see research.md: Related work). This run changes no layout, so it is not blocked. The durations below are new numbers, taken from the app's existing motion. The owner judges them by eye at gate 2 (brief.md#AC-4).

## Transitions

| Moment | Base (76a8bc2a) | Target | Duration / easing |
|---|---|---|---|
| Trailer URL arrives, before the first frame | A new over-copy shows an opaque `PurpleGrey40` skeleton frame, then the poster | The same still stays on screen at alpha 1. Nothing changes visibly. | none |
| First video frame | `AnimatedVisibility` `fadeOut()`: default spring, `StiffnessMediumLow` | The still's alpha goes from 1 to 0 over the playing video and rests at exactly 0 (brief.md#AC-7) | `tween(300, easing = FastOutSlowInEasing)`. 300 ms matches `BUFFERING_FADE_IN_MS` (`PlayerProgressBar.kt:335`), and the easing matches the panel motion (`EpisodesPanel.kt:173`, `PlayerSidePanel.kt:114`). |
| Trailer ends, or focus leaves a playing card | The under-still is re-created: skeleton frame, then the poster | The still's alpha snaps back to 1 in the frame the player leaves. The SurfaceView hole closes in that same frame, so there is no video to fade from. | instant |
| Card A to card B (warm or cold) | Poster A, then a full skeleton (`Loading`, `imageUrl = ""`), then B crossfading in from nothing | Poster A is held through `Loading`, then crossfades directly to B | 200 ms, linear: Coil's default (`CrossfadeTransition.Factory`, `sipush 200`), which is what cold loads already use. One number for warm and cold. |
| First poster after the screen opens | Skeleton until `onSuccess`, then the image | unchanged (brief.md#AC-5) | unchanged |

Named constants: `POSTER_FADE_OUT_MS = 300` and `POSTER_CROSSFADE_MS = 200`, both private to `VideoItemGridDetails.kt`. The three callers share one component and must move together, so these are not per-screen parameters.

## Finding for the architect: warm card changes would hard-cut

Coil 3.6.3 `CrossfadeTransition.Factory.create` returns `Transition.Factory.NONE` when `result.dataSource == MEMORY_CACHE` (from the bytecode of `coil-core-android-3.6.3.aar`). The factory has no `alwaysCrossfade` flag. Catalogue wide posters are prefetched (`ContentListScreenContent.kt:157-163`), so most A-to-B changes are memory hits. With `crossfade(true)` plus `useExistingImageAsPlaceholder(true)`, those changes would be an instant cut, which fails the warm half of brief.md#AC-4.

The Compose painter accepts any factory whose `create` returns a `CrossfadeTransition` (`AsyncImagePainter_androidKt.maybeNewCrossfadePainter`: `instanceof CrossfadeTransition`). The smallest fix is a small `Transition.Factory` that always returns `CrossfadeTransition(target, result, POSTER_CROSSFADE_MS)`, set through `.transitionFactory(...)` on the poster request only. Open: whether a synchronous memory hit still passes through a `Loading` state that carries the existing image as the start painter. If it does not, the crossfade starts from empty and the fix falls back to a Compose-level crossfade between the held and the new still. The implementer verifies this on the device.

## Must look unchanged

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

## Focus

N/A: no new focusable element. The still and the player are not focusable, and focus order and focus visibility stay as on base.

## Shared constants

`MediaScrim`, `FullBleedMediaWidthFraction`, `ScrimAlphaAtTextEdge`, `ScrimEndFraction`, `LocalSkeletonColor` and the `SkeletonAsyncImage` defaults are shared by the catalogue, the details hero and favourites (and by grids, rows and the hero carousel for the skeleton). This run must not retune any of them. The keep-previous and skeleton-only-first behaviour is a new `SkeletonAsyncImage` parameter that defaults to off (state.md Q2).

## Device check after implementation

`screencap` does not capture the SurfaceView (`.claude/skills/designing-a-screen/SKILL.md`), so the trailer moments are checked by a person watching. What I measure:

| Check | How | Expected |
|---|---|---|
| Panel at rest, before the trailer, on the catalogue, the details hero and favourites | screencap, px / 2 | Frame, scrim stops and bottom fade as in the "unchanged" table, within 1 dp |
| Mid card change (screencap within 150-350 ms of a D-pad Right) | burst of screencaps during repeated Right presses | No frame where the media area is uniform `PurpleGrey40` (#625B71), except on the first focus after opening the screen |
| Trailer playing, after the fade | screencap: the video area is the white hole | The hole fills the fitted video rect, and no poster pixels sit around it in the letterbox (brief.md#AC-7) |
| Trailer playing | screencap | No `surfaceVariant` tint over the hole region, and the scrims and bottom fade are still drawn over it |
| Trailer start, fade, end and warm/cold A-to-B crossfade | owner watching the emulator; `adb screenrecord` attempted once to see if it captures the video layer | 300 ms fade with no black or skeleton frame; 200 ms crossfade on warm changes as well, with no hard cut |
