# Test design: detail-poster-trailer-flicker

Base 76a8bc2a. ACs: `brief.md#Acceptance criteria`; decisions D1-D6 and risks R1-R8: `design-architect.md`. Levels from `.flow/roles/test-designer.md`. No Compose UI harness this run (state: architect Q1); the decisions D2, D3, D4 and D5 are pulled into pure seams with JUnit 5 unit tests, everything rendered goes to `device`.

## Test seams the implementer exposes

All `internal`, no Compose runtime needed; the composables call them, so the unit tests exercise the shipped logic.

| Seam | File | Replaces |
|---|---|---|
| `heldPosterUrls(previous: List<String>?, current: List<String>, isLoading: Boolean): List<String>` | `VideoItemGridDetails.kt` | D3 choice inside `rememberHeldUrls` |
| `posterFadeTarget(hasTrailer: Boolean, trailerRendered: Boolean): Float` and `posterStillAlpha(hasTrailer: Boolean, trailerRendered: Boolean, fade: Float): Float` | `VideoItemGridDetails.kt` | D2 target and `graphicsLayer` lambda |
| `PosterCrossfade` (`Transition.Factory`) | `VideoItemGridDetails.kt` | D4 factory, `internal` instead of `private` |
| `skeletonVisible(isLoading: Boolean, skeletonOnlyUntilFirstImage: Boolean, hasShownImage: Boolean): Boolean` | `SkeletonAsyncImage.kt` | D5 skeleton `Box` condition |

Test files: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` and `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImageVisibilityTest.kt`. Style of `FocusTargetResolverTest` (plain JUnit 5, no dispatcher extension needed).

## Risk profile

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

## Traceability

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

## Failing-first test

`heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls`, written first. On base the choice is inline (`git show 76a8bc2a:app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt`, lines 433-440 build `imageUrls` from `imageUrl` alone), so `Loading`'s `""` yields an empty list: the test does not compile on base and asserts the held list after. The bug itself is only visible on device (M1, M4).

## Manual checks (device)

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

## perf track

N/A: perf track off.

## UNTESTABLE

None. AC-1, AC-2, AC-3, AC-4 smoothness and AC-7 at the video layer depend on a person watching (R1).
