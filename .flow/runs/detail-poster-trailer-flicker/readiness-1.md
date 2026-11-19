# Readiness 1: detail-poster-trailer-flicker

Base 76a8bc2a. Plan `docs/superpowers/plans/2026-11-19-detail-poster-trailer-flicker.md` (untracked in wt), spec at wt `17270f95`.

**Verdict: READY**

## AC coverage

| AC | Plan task | Test design row |
|----|-----------|-----------------|
| AC-1 | 2, 3 | `posterStillAlpha_isOpaque_whenTrailerNotRenderedYet`, `skeletonVisible_hidden_whenOptInAndImageShownBefore`; M2 |
| AC-2 | 3 | `posterFadeTarget_isZero_whenTrailerRendered`, `posterStillAlpha_followsFade_whenTrailerRendered`; M2 |
| AC-3 | 2, 3 | `posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero`; M3 |
| AC-4 | 1, 2, 4 | `heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls`, `posterCrossfade_*`; M1, M4 |
| AC-5 | 1, 2 | `heldPosterUrls_returnsCurrent_whenNothingHeldYet`, `skeletonVisible_shown_whenOptInButNoImageYet`; M1 |
| AC-6 | 1, 4 | `heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters`, `posterCrossfade_returnsNone_whenResultIsError`; M6 |
| AC-7 | 3 | `posterFadeTarget_isZero_whenTrailerRendered`; M5 |
| AC-8 | 3 | M7 |
| AC-9 | 2 | `skeletonVisible_staysVisibleWhileLoading_whenOptInOff` |
| AC-10 | 4 | full gate; empty `git diff 76a8bc2a -- app/src/test/kotlin/com/kino/puber/ui/feature` |

## Citations sampled (base 76a8bc2a)

| Citation | Resolves to |
|----------|-------------|
| `VideoItemGridDetails.kt:416` | `private fun VideoDetailsPoster(` |
| `VideoItemGridDetails.kt:433-440` | `imageUrls` remember, `urlIndex`, `currentUrl`, `rememberPosterRequest` |
| `VideoItemGridDetails.kt:444-491` | conditional under-still, `TrailerPreviewPlayer`, `AnimatedVisibility` copy |
| `VideoItemGridDetails.kt:243`, `:272`, `:288` | the three `VideoDetailsPoster(` calls |
| `VideoItemGridDetails.kt:556`, `:564`, `:571`, `:580` | `rememberPosterRequest`, `.crossfade(true)`, `PosterStill`, `placeholder(visible = request == null)` |
| `SkeletonAsyncImage.kt:20`, `:28`, `:45`, `:60-64` | function, `isLoading` remember, `onSuccess`, skeleton `Box` |
| `VideoDetailsUIState.kt:21`, `:28` | `isLoading: Boolean = false`; `Loading.imageUrl = ""` (`Loading` sets `isLoading = true`, `:39`) |
| `ContentListVM.kt:71`, `FavoriteVM.kt:110`, `ContentListVM.kt:132-136` | `selectedItem = VideoDetailsUIState.Loading` (both); `stopTrailerPreview` |
| `VideoItemUIMapper.kt:89` | `imageUrl = widePosterUrls.firstOrEmpty()` |
| `TrailerPreviewPlayer.kt:42`, `FocusTargetResolverTest.kt:6` | `internal fun TrailerPreviewPlayer(`; `internal class FocusTargetResolverTest` |
| Coil 3.6.3 APIs (javap of cached aars) | `coil3.compose.useExistingImageAsPlaceholder(Builder, Boolean)`, `coil3.request.transitionFactory`, `CrossfadeTransition(target, result, Int)`, `Transition.Factory.NONE`, `SuccessResult`/`ErrorResult` constructors all present |

## Other checks

| Check | Result |
|-------|--------|
| Build stays green per task, dependency order | Yes: Task 1 adds a defaulted parameter; 2 a defaulted parameter; 3 consumes 1 and 2; 4 consumes 3. Each task ends on the fast gate. |
| P0 rows name defect, mark, test in backticks | Yes. Rows tagged `P0 red` but expected green on the base stub are re-recorded as `guard` by the plan's red-run rule (Global constraints). Device P0 rows name M-checks, no automated test exists by design (Q4). |
| Inside Out of scope | Yes: only `VideoItemGridDetails.kt`, `SkeletonAsyncImage.kt` and two new test files; VM, `VideoDetailsUIState`, player and shared constants untouched. |
| Blocking open questions | Q1, Q3 answered (state.md Decisions). |

## Non-blocking notes

1. Spec Root cause #1 cites `VideoItemGridDetails.kt:579` for the outer placeholder; the line is `:580` (the plan uses `:580`).
2. D4 calls `PosterCrossfade` private; the test design and plan make it `internal` for the tests. The plan wins.
