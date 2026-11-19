LENS: tests  ROUND: 1  BASE: 76a8bc2a

The two new test classes pass when re-run at bd2cf869: VideoDetailsPosterStateTest ran 11 tests and SkeletonAsyncImageVisibilityTest ran 3, with 0 failures and 0 skipped. I did not re-run the full check myself; it passed when qa ran it at bd2cf869. AC-10 holds: `ContentListVMTest`, `FavoriteVMTest` and `DetailsVMTrailerPreviewTest` are not changed in this diff. The Implementation record shows a red run on stubs for every P0 `red` row, and each row that was green on base is marked `guard`.

### F1 [major] No test or device check would catch a regression of the actual fix for root causes #2 and #3
- Where: app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:492-504 (the single `PosterStill` after the player), app/src/main/java/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImage.kt:30,49; the qa device evidence `docs/superpowers/*/2026-11-19-detail-poster-trailer-flicker-device.md`
- Problem: The unit tests only cover pure helpers (`skeletonVisible`, `posterStillAlpha`, `posterFadeTarget`). AC-1 and AC-3 depend on four other things:
  - the still keeping its slot when the trailer starts or stops (D1);
  - `hasShownImage` surviving because of that;
  - `PosterStill` passing `skeletonOnlyUntilFirstImage = true`;
  - `onSuccess` setting `hasShownImage = true`.

  None of these is tested, and the spec accepted that only because M2 and M3 on the device would cover them. The device evidence reports AC-1, AC-2, AC-3, AC-7 and the trailer part of AC-8 as `NOT CHECKED`, because no video frame ever rendered. AC-5 is `NOT CHECKED` too. So these ACs have a documented manual check that was never actually done.
- Failure scenario: Someone wraps the still back in `if (trailerUrl == null || !trailerRendered)`, or removes `hasShownImage = true` from `onSuccess`, or drops `skeletonOnlyUntilFirstImage = true` from `PosterStill`. The skeleton frame on trailer start or end comes back (root causes #2 and #3), and all 14 tests stay green.
- Fix: Do not count AC-1, AC-2, AC-3, AC-5 and AC-7 as verified. Either get the owner to watch M2, M3, M5 and M1 step 1 on a device that renders video before gate 2, or record them as open at gate 2. If a cheap seam is wanted, a JVM test can at least assert that `PosterStill` passes the opt-in, e.g. by extracting `posterStillSkeletonOptIn = true` as an `internal const`.

### F2 [major] The `posterCrossfade_*` tests do not check that the poster request actually uses `PosterCrossfade`, so the AC-4 warm-change fix is unguarded
- Where: app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt:76-95; app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:576-579
- Problem: The tests call `PosterCrossfade.create(...)` directly. The test design says `posterCrossfade_returnsCrossfade_whenResultFromMemoryCache` "fails if `crossfade(true)`'s factory is used", but it does not. Nothing checks that `rememberPosterRequest` sets `.transitionFactory(PosterCrossfade)` or `.useExistingImageAsPlaceholder(true)`. On the device, M4 only reached "PASS on screencap level (crossfade smoothness not observable)", which cannot tell a crossfade from a hard cut.
- Failure scenario: `rememberPosterRequest` goes back to `.crossfade(true)`, or loses `useExistingImageAsPlaceholder`. Warm card changes then hard-cut, or crossfade from an empty start (AC-4 warm). All tests pass and the screencap check still passes.
- Fix: Move the builder into `internal fun posterRequest(context: Context, url: String): ImageRequest` and call it from `rememberPosterRequest`. Then add a test that builds it with `mockk<Context>(relaxed = true)` and asserts `request.transitionFactory === PosterCrossfade` and that the `useExistingImageAsPlaceholder` extra is `true`. If Coil cannot build the request off-device, record that M4's warm half is still unchecked.

### F3 [minor] Nothing pins the fade target to 1 before the first frame, so a hard cut at the first frame would go unnoticed
- Where: VideoDetailsPosterStateTest.kt:65-68; VideoItemGridDetails.kt:463-471, 683-684
- Problem: The only test of `posterFadeTarget` checks the `0F` case. The `1F` branch (trailer present but not rendered yet, or no trailer) and the choice between `tween` and `snap` are untested. `posterStillAlpha` returns 1 whenever the trailer is not rendered, so a wrong target is invisible until `trailerRendered` turns true.
- Failure scenario: `posterFadeTarget` is changed to always return `0F`, or `tween` is replaced by `snap()`. `fade` is then already at 0 when the first frame arrives, the poster vanishes in one frame instead of a 300 ms fade (AC-2), and every test stays green.
- Fix: Add `posterFadeTarget_isOne_whenTrailerNotRendered` (`hasTrailer = true, trailerRendered = false` -> `1F`) and `posterFadeTarget_isOne_whenNoTrailer`.

### F4 [minor] `posterCrossfade_returnsNone_whenResultIsError` only checks what the transition is not
- Where: VideoDetailsPosterStateTest.kt:90-95
- Problem: `assertFalse(transition is CrossfadeTransition)` passes for any non-crossfade transition, including one that misbehaves. The row is meant to prove the `Transition.Factory.NONE` fallback.
- Failure scenario: The error branch returns some other custom `Transition` that still fades or keeps a stale painter (AC-6 fallback). The test stays green.
- Fix: Assert the class of `Transition.Factory.NONE.create(target, result)`, e.g. `assertEquals(Transition.Factory.NONE.create(target, err)::class, transition::class)`.

### Unproven
- VideoItemGridDetails.kt:675-680 `rememberHeldUrls`: the `SideEffect` write and the holder surviving between Loading frames are untested. This would become real if a recomposition where Loading starts before the previous `SideEffect` ran (a skipped commit) were shown to happen.
