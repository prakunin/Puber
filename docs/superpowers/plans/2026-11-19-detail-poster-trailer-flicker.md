# Remove the detail panel's poster flash - plan

**Spec:** `docs/superpowers/specs/2026-11-19-detail-poster-trailer-flicker.md`
**Goal:** One persistent poster still in `VideoDetailsPoster` that is held across `Loading`, fades by alpha over the trailer, crossfades between cards and shows its skeleton only before the first image.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `./tools/agentw :app:compileDevDebugKotlin`; full gate: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Worktree only, never `./gradlew`; prefix `AGENT_GRADLE_SERIAL=1` when another worktree may build.
- Touched tests: `./tools/agentw :app:testDevDebugUnitTest --tests '<fully.qualified.Class>'`.
- One task = green fast gate + green tests of the touched code + one commit, message per `One imperative English sentence, no prefixes; no Co-Authored-By line`, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths below are relative to `app/src/main/java/com/kino/puber/` unless they start with `app/`. Line numbers are at base `76a8bc2a`; later tasks shift them in `VideoItemGridDetails.kt`, so find symbols by name.
- Out of scope (spec: Out of scope): no edit to any VM, `VideoDetailsUIState`, the text column, `TrailerPreviewPlayer`, scrims or shared constants (`MediaScrim`, `FullBleedMediaWidthFraction`, `LocalSkeletonColor`). `ContentListVMTest`, `FavoriteVMTest`, `DetailsVMTrailerPreviewTest` stay byte-identical (AC-10).
- Seams are `internal` top-level functions/objects in the main file; composables call them so tests exercise the shipped logic. Tests are plain JUnit 5 (`org.junit.jupiter.api`), style of `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetResolverTest.kt:6`; no dispatcher extension.
- Red run per task (implementer rule): write the task's P0 `red` tests, add each seam as a stub that returns the base behaviour named in the task, run, record `red: <name> - <failure line>` or `green on base: <name> - <why>`, then implement. Expected outcomes are listed per task; a row expected green on base is recorded as `guard`.
- Device checks M1-M7 (spec: Test design, Manual checks) are qa's, not plan tasks.

---

## Tasks

### Task 1: Hold the last poster URLs while the details load

**Covers:** AC-4, AC-5, AC-6 (spec D3)
**Files:**
- Modify: `core/ui/uikit/component/details/VideoItemGridDetails.kt`
  - `VideoDetailsPoster` (`:416`): add parameter `isLoading: Boolean = false` after `imageFallbackUrls`. After the `imageUrls` `remember` (`:433-437`) add `val heldUrls = rememberHeldUrls(imageUrls, isLoading)`; key `urlIndex` (`:438`) on `heldUrls` and read `currentUrl` (`:439`) and the `onError` bound (`:454`) from `heldUrls`.
  - Add `internal fun heldPosterUrls(previous: List<String>?, current: List<String>, isLoading: Boolean): List<String>` = `if (isLoading && current.isEmpty() && previous != null) previous else current`.
  - Add `@Composable private fun rememberHeldUrls(imageUrls: List<String>, isLoading: Boolean): List<String>`: a plain `remember { PosterUrlHolder() }` (private class with `var last: List<String>? = null`), `val held = heldPosterUrls(holder.last, imageUrls, isLoading)`, `SideEffect { holder.last = held }`, return `held`. No snapshot state write in composition.
  - Pass `isLoading = state.isLoading` at the three calls: side-by-side `:243`, `VideoDetailsMedia` expanded `:272`, `VideoDetailsMedia` box `:288`. `VideoDetailsUIState.isLoading` is `VideoDetailsUIState.kt:21`; `Loading.imageUrl = ""` is `:28`.
  - Imports: `androidx.compose.runtime.SideEffect`.
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` (new, package `com.kino.puber.core.ui.uikit.component.details`, `internal class`).

**Consumes:** `VideoDetailsUIState.isLoading` (`core/ui/uikit/component/details/VideoDetailsUIState.kt:21`).
**Produces:** `heldPosterUrls(...)`; `VideoDetailsPoster(isLoading)`.

| Test | Priority | Fixture | Assert | Expected on base stub (`= current`) |
|---|---|---|---|---|
| `heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls` (failing-first) | P0 red | `previous = listOf("a", "a2")`, `current = emptyList()`, `isLoading = true` | `assertSame(previous, result)` | red |
| `heldPosterUrls_returnsCurrent_whenNewCardArrivesDuringLoading` | P0 red | `previous = listOf("a")`, `current = listOf("b")`, `isLoading = true` | `assertSame(current, result)` | green on base (guard against a hold that swallows a URL) |
| `heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters` | P0 red | `previous = listOf("a")`, `current = emptyList()`, `isLoading = false` | `assertSame(current, result)` | green on base (guard against ignoring `isLoading`) |
| `heldPosterUrls_returnsCurrent_whenNothingHeldYet` | P0 red | `previous = null`, `current = emptyList()`, `isLoading = true` | `assertSame(current, result)` | green on base (guard for AC-5) |

- [x] Write `heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls` first, then the other three rows
- [x] Add the stub `heldPosterUrls(...) = current`; red run; record each row
- [x] Implement the seam, `rememberHeldUrls`, the parameter and the three call sites
- [x] Run the fast gate and `VideoDetailsPosterStateTest`; commit, e.g. `Hold the previous poster while the next card's details load`

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.details.VideoDetailsPosterStateTest'` passes 4 tests and the fast gate is green.

---

### Task 2: Show the poster skeleton only before its first image

**Covers:** AC-1, AC-3, AC-4, AC-5, AC-9 (spec D5)
**Files:**
- Modify: `core/ui/uikit/component/SkeletonAsyncImage.kt`
  - `SkeletonAsyncImage` (`:20`): add last parameter `skeletonOnlyUntilFirstImage: Boolean = false`. Add `var hasShownImage by remember(model != null) { mutableStateOf(false) }` next to `isLoading` (`:28`); `onSuccess` (`:45`) also sets `hasShownImage = true`; the skeleton `Box` (`:60-64`) uses `placeholder(visible = skeletonVisible(isLoading, skeletonOnlyUntilFirstImage, hasShownImage))`.
  - Add `internal fun skeletonVisible(isLoading: Boolean, skeletonOnlyUntilFirstImage: Boolean, hasShownImage: Boolean): Boolean` = `isLoading && !(skeletonOnlyUntilFirstImage && hasShownImage)`.
- Modify: `core/ui/uikit/component/details/VideoItemGridDetails.kt` `PosterStill` (`:571`): pass `skeletonOnlyUntilFirstImage = true`; keep the outer `placeholder(visible = request == null)` (`:580`, AC-5).
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImageVisibilityTest.kt` (new, package `com.kino.puber.core.ui.uikit.component`).

**Consumes:** nothing new.
**Produces:** `SkeletonAsyncImage(skeletonOnlyUntilFirstImage)`; `skeletonVisible(...)`. Every other caller keeps the default `false` (AC-9).

| Test | Priority | Fixture `(isLoading, optIn, hasShownImage)` | Assert | Expected on base stub (`= isLoading`) |
|---|---|---|---|---|
| `skeletonVisible_hidden_whenOptInAndImageShownBefore` | P0 red | `(true, true, true)` | `assertFalse` | red |
| `skeletonVisible_shown_whenOptInButNoImageYet` | P0 red | `(true, true, false)` | `assertTrue` | green on base (guard for AC-5) |
| `skeletonVisible_staysVisibleWhileLoading_whenOptInOff` | P0 red | `(true, false, true)` | `assertTrue` | green on base (guard for AC-9) |

- [x] Write the three tests; add the stub `skeletonVisible(...) = isLoading`; red run; record each row
- [x] Implement the parameter, `hasShownImage`, the seam and the `PosterStill` opt-in
- [x] Run the fast gate and `SkeletonAsyncImageVisibilityTest`; commit, e.g. `Show the poster skeleton only until its first image`

**Done when:** `./tools/agentw :app:testDevDebugUnitTest --tests 'com.kino.puber.core.ui.uikit.component.SkeletonAsyncImageVisibilityTest'` passes 3 tests, the fast gate is green, and `git diff 76a8bc2a -- app/src/main/java/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImage.kt` shows the new parameter defaulting to `false`.

---

### Task 3: Keep one still over the trailer and fade it by alpha

**Covers:** AC-1, AC-2, AC-3, AC-7, AC-8 (spec D1, D2, D6)
**Files:**
- Modify: `core/ui/uikit/component/details/VideoItemGridDetails.kt` `VideoDetailsPoster` (`:416`)
  - Add `private const val POSTER_FADE_OUT_MS = 300` near the other constants (`:58-81`).
  - Add `internal fun posterFadeTarget(hasTrailer: Boolean, trailerRendered: Boolean): Float` = `if (hasTrailer && trailerRendered) 0F else 1F`, and `internal fun posterStillAlpha(hasTrailer: Boolean, trailerRendered: Boolean, fade: Float): Float` = `if (!hasTrailer || !trailerRendered) 1F else fade`.
  - Replace `:444-491` (the conditional under-still, the player and the `AnimatedVisibility` copy) with, in this order: `val fade = animateFloatAsState(targetValue = posterFadeTarget(hasTrailer, trailerRendered), animationSpec = if (hasTrailer && trailerRendered) tween(POSTER_FADE_OUT_MS, easing = FastOutSlowInEasing) else snap(), label = ...)` holding the `State` (no `by`); `if (trailerUrl != null) TrailerPreviewPlayer(...)` with the arguments of `:462-471` unchanged; then one unconditional `PosterStill(...)` with the arguments of `:447-458` and modifier `Modifier.fillMaxHeight().fillMaxWidth().graphicsLayer { alpha = posterStillAlpha(hasTrailer, trailerRendered, fade.value) }`. No `key`. `hasTrailer = trailerUrl != null`.
  - Rewrite the comments at `:444-445` and `:473-477`: the player is a `SurfaceView` hole, so the still is drawn after it, stays the same composable across trailer start and stop, and rests at alpha 0 over a rendered trailer.
  - `trailerRendered` stays `remember(trailerUrl)` (`:442`); `TrailerPreviewPlayer` is `core/ui/uikit/component/details/TrailerPreviewPlayer.kt:42`.
  - Scrims `:493-544` unchanged, still after the still.
  - Imports: drop `AnimatedVisibility` (`:3`), `EnterTransition` (`:4`), `fadeOut` (`:5`); add `androidx.compose.animation.core.FastOutSlowInEasing`, `androidx.compose.animation.core.animateFloatAsState`, `androidx.compose.animation.core.snap`, `androidx.compose.animation.core.tween`, `androidx.compose.ui.graphics.graphicsLayer`.
- Test: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` (extend).

**Consumes:** Task 1's `heldUrls` and `onError`; Task 2's `PosterStill` opt-in.
**Produces:** `posterFadeTarget(...)`, `posterStillAlpha(...)`.

| Test | Priority | Fixture | Assert | Expected on base stub |
|---|---|---|---|---|
| `posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero` | P0 red | `(hasTrailer = false, trailerRendered = false, fade = 0F)` | `assertEquals(1F, result)` | red (stub `= fade`) |
| `posterStillAlpha_isOpaque_whenTrailerNotRenderedYet` | P0 red | `(true, false, fade = 0F)` | `assertEquals(1F, result)` | red (stub `= fade`) |
| `posterFadeTarget_isZero_whenTrailerRendered` | P0 red | `(true, true)` | `assertEquals(0F, result)` exactly | red (stub `= 1F`) |
| `posterStillAlpha_followsFade_whenTrailerRendered` | P1 | `(true, true, fade = 0.4F)` | `assertEquals(0.4F, result)` | not in the red run |

- [x] Write the four tests; add stubs `posterStillAlpha(...) = fade`, `posterFadeTarget(...) = 1F`; red run; record each P0 row
- [x] Implement the seams and the new body
- [x] Run the fast gate and `VideoDetailsPosterStateTest`; commit, e.g. `Keep one poster still over the trailer and fade it by alpha`

**Done when:** `VideoDetailsPosterStateTest` passes 8 tests, the fast gate is green, and `grep -n "AnimatedVisibility" app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt` prints nothing.

---

### Task 4: Crossfade every poster change, memory hits included

**Covers:** AC-4, AC-6 (spec D4, D6)
**Files:**
- Modify: `core/ui/uikit/component/details/VideoItemGridDetails.kt`
  - Add `private const val POSTER_CROSSFADE_MS = 200`.
  - Add `internal object PosterCrossfade : Transition.Factory` whose `create(target: TransitionTarget, result: ImageResult)` returns `CrossfadeTransition(target, result, POSTER_CROSSFADE_MS)` for `SuccessResult`, else `Transition.Factory.NONE.create(target, result)`.
  - `rememberPosterRequest` (`:556`): annotate `@OptIn(ExperimentalCoilApi::class)`; replace `.crossfade(true)` (`:564`) with `.useExistingImageAsPlaceholder(true).transitionFactory(PosterCrossfade)`. Rewrite the KDoc's second paragraph (`:552-553`): one still now; the request carries the old image as placeholder and always crossfades.
  - Imports: drop `coil3.request.crossfade` (`:48`); add `coil3.annotation.ExperimentalCoilApi`, `coil3.compose.useExistingImageAsPlaceholder`, `coil3.request.ImageResult`, `coil3.request.SuccessResult`, `coil3.request.transitionFactory`, `coil3.transition.CrossfadeTransition`, `coil3.transition.Transition`, `coil3.transition.TransitionTarget` (all in Coil 3.6.3, `gradle/libs.versions.toml` `coil`).
- Test: `VideoDetailsPosterStateTest.kt` (extend).

**Consumes:** Task 3's single still.
**Produces:** `PosterCrossfade`.

Fixtures (spec: Test design): `ImageRequest.Builder(mockk<Context>(relaxed = true)).data("u").build()`, `mockk<TransitionTarget>()`, `mockk<coil3.Image>()`; `SuccessResult(image, request, DataSource.MEMORY_CACHE)`, `SuccessResult(image, request, DataSource.NETWORK)`, `ErrorResult(null, request, Throwable())`. If `build()` fails off-device, use `mockk<SuccessResult> { every { dataSource } returns ... }` and `mockk<ErrorResult>()` instead (the factory reads neither request nor image); if Coil classes still cannot load, drop the three tests, record it under Deviations, and AC-4 warm rests on M4.

| Test | Priority | Assert |
|---|---|---|
| `posterCrossfade_returnsCrossfade_whenResultFromMemoryCache` | P1 | `assertTrue(PosterCrossfade.create(target, memoryResult) is CrossfadeTransition)` and `durationMillis == 200` |
| `posterCrossfade_returnsCrossfade_whenResultFromNetwork` | P1 | `is CrossfadeTransition` |
| `posterCrossfade_returnsNone_whenResultIsError` | P1 | `assertFalse(... is CrossfadeTransition)` |

- [x] Write the three tests; implement the factory and the builder change
- [x] Run the fast gate and `VideoDetailsPosterStateTest`; commit, e.g. `Crossfade the detail poster on every card change`
- [x] Run the full gate once; confirm `git diff 76a8bc2a -- app/src/test/kotlin/com/kino/puber/ui/feature` is empty (AC-10)

**Done when:** `VideoDetailsPosterStateTest` passes 11 tests (8 if the fallback dropped them), `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` is green, and the AC-10 diff is empty.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

- Task 1: `5204db80` Hold the previous poster while the next card's details load
- Task 2: `b855a053` Show the poster skeleton only until its first image
- Task 3: `24201c0f` Keep one poster still over the trailer and fade it by alpha
- Task 4: `625824b9` Crossfade the detail poster on every card change

**Red run** (P0 `red` tests on the unchanged code):

- red: `heldPosterUrls_returnsSamePreviousInstance_whenLoadingWithEmptyUrls` - AssertionFailedError at `VideoDetailsPosterStateTest.kt:14`
- guard (green on base, stub `= current`): `heldPosterUrls_returnsCurrent_whenNewCardArrivesDuringLoading`, `heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters`, `heldPosterUrls_returnsCurrent_whenNothingHeldYet`
- red: `skeletonVisible_hidden_whenOptInAndImageShownBefore` - assertion failed on the stub `= isLoading`
- guard: `skeletonVisible_shown_whenOptInButNoImageYet`, `skeletonVisible_staysVisibleWhileLoading_whenOptInOff`
- red: `posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero`, `posterStillAlpha_isOpaque_whenTrailerNotRenderedYet`, `posterFadeTarget_isZero_whenTrailerRendered` - failed on stubs `= fade` / `= 1F`
- Task 4 `posterCrossfade_*` are P1 and were not in a red run; the three pass with `mockk<SuccessResult>` / `mockk<ErrorResult>` (the `build()` path was not needed).

**Deviations from the plan:**

None. The `posterCrossfade_*` fixtures use the plan's mockk fallback for results.

**Files touched outside the plan:**

None.

**For the reviewers:**

- `VideoDetailsPoster`: one `PosterStill` after the player, `graphicsLayer` alpha read from `fade.value` (recomposes per frame, R4 accepted).
- `rememberHeldUrls` writes the holder in `SideEffect`, no snapshot write in composition.
- `check.full` green; `git diff 76a8bc2a -- app/src/test/kotlin/com/kino/puber/ui/feature` is empty (AC-10).
- Device checks M1-M7 not run (qa).

**Blocked:**

None.

## Readiness

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


### Fix round 1

Rows 1-6 of `triage-1.md`, one commit. Row 7 is deferred.

| Row | Change | Test |
|-----|--------|------|
| 1 | `heldPosterUrls` takes `holdExpired`; `rememberHeldUrls` expires the hold after `POSTER_HOLD_MS` (3 s, private) of `Loading` with no URLs, then the panel shows the skeleton. No VM change. | `heldPosterUrls_returnsCurrent_whenHoldExpired` |
| 2 | `posterRequest(context, url)` extracted and `internal`. | `posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent`, `posterRequest_isNull_whenUrlMissingOrEmpty` |
| 3 | - | `posterFadeTarget_isOne_whenTrailerNotRendered`, `posterFadeTarget_isOne_whenNoTrailer` |
| 4 | Error test asserts the class of `Transition.Factory.NONE`'s transition. | `posterCrossfade_returnsNone_whenResultIsError` |
| 5 | KDoc moved above `heldPosterUrls`. | - |
| 6 | Opt-in is `internal const POSTER_SKELETON_ONLY_UNTIL_FIRST_IMAGE`, used by `PosterStill`. | `posterStill_staysOptedIntoSkeletonOnlyUntilFirstImage` |

Red run (the new tests against the old behaviour: hold ignoring `holdExpired`, request using `crossfade(true)`):

- red: `heldPosterUrls_returnsCurrent_whenHoldExpired` - AssertionFailedError at `VideoDetailsPosterStateTest.kt:64`
- red: `posterRequest_usesCrossfadeAndExistingPlaceholder_whenUrlPresent` - AssertionFailedError at `VideoDetailsPosterStateTest.kt:81`
- green on base (guard): the `posterFadeTarget_isOne_*` pair, the `posterCrossfade_returnsNone_*` change, `posterRequest_isNull_*`, `posterStill_*` - they cover existing behaviour.

Deviations: none. Coil builds an `ImageRequest` off-device with a relaxed `mockk<Context>`, so M4 needs no device for the request wiring. Detekt `ComplexCondition` forced the hold decision into two statements. A first full run showed one failure in `HomeVMIncrementalPublishTest` (unrelated, not reproduced on rerun; see run `unit-suite-flaky`).

Device ACs 1-3, 7 stay manual (M1-M7) for gate 2.
