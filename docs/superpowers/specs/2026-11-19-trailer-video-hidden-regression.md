# Trailer preview shows video again after a card change

Status: approved
Date: 2026-11-19
Slug: trailer-video-hidden-regression
Kind: bug
Size: M
Tracks: none

## Problem / Why

### Request
On 1.12.1 the detail panel's trailer preview plays audio but shows no video on the owner's TV; 1.12.0 showed it (state.md: Log). Fix ships as release 1.12.2.

### Root cause
**Confirmed** (reproduced on emulator-5554, diagnosis.md: Root cause, Evidence).

| # | Step | Where |
|---|------|-------|
| 1 | After a card change the `AndroidView` with `PlayerView` (default `SurfaceView`) is inserted while the window is otherwise idle. | `TrailerPreviewPlayer.kt:125-139`, composed at `VideoItemGridDetails.kt:482-493` |
| 2 | The window's only pre-draw runs while the `SurfaceView` is 0x0; it gets its size later, in Compose layout inside the draw pass. Nothing schedules another traversal, so `SurfaceView.updateSurface()` never creates the surface. | diagnosis.md: Mechanism |
| 3 | ExoPlayer plays audio with no surface, `onRenderedFirstFrame` (`TrailerPreviewPlayer.kt:85-87`) never fires, `trailerRendered` (`VideoItemGridDetails.kt:469`) stays false, and the still stays at alpha 1 (`:506`). Matches the owner's report that the poster stays fully visible (state.md: Decisions). | |
| 4 | 1.12.0 worked by accident: its fresh over-copy of the still loaded on every trailer start and drove a second pre-draw about 18 ms later. 1.12.1's single still (from `detail-poster-trailer-flicker`) draws nothing new at trailer start. | diagnosis.md: Why 1.12.0 worked |

The first trailer after opening a screen works on 1.12.1 because the window is still producing frames then. Ruled out: the alpha-0 layer over the video, `trailerRendered` reset, player re-creation, codec errors (diagnosis.md: Ruled out).

### Fix direction
Research option A (stop drawing the still at rest) does not touch this cause (diagnosis.md: Consequence). Options that do:

| # | What | Trade-offs |
|---|------|------------|
| **T (recommended)** | In `TrailerPreviewPlayer`'s `factory` (`TrailerPreviewPlayer.kt:126-132`), add an `OnLayoutChangeListener` to `playerView.videoSurfaceView`; when its size changes to a non-zero size, `post { invalidate() }` on it, forcing the traversal that runs `updateSurface()`. The size predicate is a pure top-level function with a unit test. | One file, no dependency, keeps the SurfaceView (Q3 of the source run). The posted `invalidate()` fixed trailer 2 on the emulator within 30 ms (diagnosis.md: Evidence row 4). Independent of what the poster draws, so it no longer relies on a sibling's frames. |
| B | Render into a TextureView (`surface_type="texture_view"` layout or `media3-ui-compose`). | Avoids the hole-punch path entirely but costs power and frame timing, caps the video at UI resolution and reverses the source run's Q3 (research.md: Options). Fallback only if T fails on the TV. |

## Acceptance criteria

| ID | Given | When | Then | Checked by |
|----|-------|------|------|------------|
| AC-1 | Catalogue on the emulator, auto-trailer on, a trailer already played once | focus moves to another card with a trailer, three times in a row | each trailer's SurfaceView gets a surface (layer in `dumpsys SurfaceFlinger --list`), `onRenderedFirstFrame` fires and a screencap after the fade shows video, not the poster | qa on emulator |
| AC-2 | The first trailer after opening the catalogue | it starts | the video shows as on 1.12.1 | qa on emulator |
| AC-3 | The owner's TV, catalogue | trailers start after several card changes | the video is visible and the poster fades out over it | owner on the TV |
| AC-4 | The details hero and the favourites panel | a trailer starts after a card change or on screen open | AC-1 and AC-3 hold there | qa on emulator, owner on the TV |
| AC-5 | The fix in place | trailer start, end and card change | `detail-poster-trailer-flicker` brief.md AC-1 to AC-8 still hold: no skeleton or black flash, held poster during Loading, 300 ms fade after the first frame, instant poster return at trailer end, no poster around a fitted trailer | qa on emulator; owner on the TV (owner judges smoothness) |
| AC-6 | The size predicate | unit test with 0x0→WxH, WxH→same, WxH→W'xH', WxH→0x0 | it requests a traversal only for a change to a non-zero size | unit test |
| AC-7 | The change | `check.full` runs in the worktree | it passes | qa |

## Out of scope

In scope: `TrailerPreviewPlayer.kt` and a new unit test next to `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt`. All three screens share the player: catalogue (`ui/feature/contentlist/content/ContentListScreenContent.kt:166`), favourites (`ui/feature/favorites/content/FavoriteScreenContent.kt:99`), details hero (`ui/feature/details/component/DetailsScreenContent.kt:189`).

Out of scope: `VideoItemGridDetails.kt` poster logic, `SkeletonAsyncImage.kt`, option B, the 1.12.2 version bump (done by the release procedure, `docs/release.md`, after ship).

## Design

Fix T from `brief.md#fix-direction`. Paths are relative to `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/` unless they start with `app/`.

### Decisions

| Question | Decision | Why |
|---|---|---|
| Which view gets the listener | `playerView.videoSurfaceView`, read once in the `factory` (`TrailerPreviewPlayer.kt:126-132`), null-safe | media3 1.11.0 `PlayerView.getVideoSurfaceView()` returns the `private final View surfaceView` field (bytecode), annotated `@Nullable` and `@UnstableApi`; the composable is already `@UnstableApi` (`TrailerPreviewPlayer.kt:40`). The field is final, so the view the listener is added to is the one `onRelease` sees. |
| Listener shape | One stateless top-level `private val` `View.OnLayoutChangeListener` that uses only its `v` argument; unused lambda parameters named `_` | Captures nothing, so it can hold neither the player, the context nor the composition. `View.addOnLayoutChangeListener` ignores a listener already in the list, so re-adding is safe. |
| Listener lifecycle | Add in `factory`; remove in a new `onRelease = { it.videoSurfaceView?.removeOnLayoutChangeListener(...) }` on the `AndroidView` (`TrailerPreviewPlayer.kt:125`) | Compose UI 1.12 `AndroidView(factory, modifier, onReset, onRelease, update)` exists (`ui-android` 1.12.1 bytecode). `onReset` stays unset, so the view is never reused and `factory`/`onRelease` pair one to one. The `DisposableEffect` at `:73-95` keeps owning the player. |
| Size predicate seam | `internal fun surfaceNeedsRedraw(oldWidth: Int, oldHeight: Int, newWidth: Int, newHeight: Int): Boolean` = new size non-zero and different from the old size; top-level in `TrailerPreviewPlayer.kt`. The listener passes `right - left`, `bottom - top` and the `old*` equivalents | Pure, so AC-6 is a plain JUnit 5 test with no Android stubs. Position-only moves return false: SurfaceView repositions through its RenderNode position callback, not a new surface. |
| `invalidate()` or `requestLayout()` | `invalidate()` on the SurfaceView | `SurfaceView.updateSurface()` runs from the window's pre-draw listener; any traversal reaches it, and a draw-only traversal is enough (diagnosis.md: Evidence row 4). `requestLayout()` adds a measure/layout pass through `AndroidComposeView` for nothing. |
| `post` or synchronous / `postOnAnimation` | `v.post { v.invalidate() }` | The late size arrives inside the current traversal (Compose layout in the draw pass); posting moves the invalidate after that traversal, which the experiment proved. Whether a synchronous call would also schedule one is `unverified` and not needed. `post` is the proven variant. `postOnAnimation` could land the traversal one frame earlier, but it is unproven and the gain (at most one vsync, before the 300 ms fade) is invisible. |
| KDoc | Extend `TrailerPreviewPlayer.kt:36-38` with one sentence: the SurfaceView is redrawn once it gets a size, because a late layout gets no surface otherwise | The next reader must not remove the listener as dead code. |

### Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data change. |
| ViewModel state and actions | N/A: no state change. |
| Screen and DI module | N/A: the single call site `VideoItemGridDetails.kt:483-492` serves catalogue, favourites and details hero unchanged. |
| Compose content | `TrailerPreviewPlayer.kt`: add `surfaceNeedsRedraw`, the private listener val, `videoSurfaceView?.addOnLayoutChangeListener(...)` inside the `apply` at `:127-132`, `onRelease` on the `AndroidView` at `:125-139`, KDoc at `:36-38`. Imports: `android.view.View`. |
| Resources | N/A. |
| Tests | New `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt`, JUnit 5 like `VideoDetailsPosterStateTest.kt:18-20`: the four AC-6 cases. Names and count come from test-design.md. |

`VideoItemGridDetails.kt` and `SkeletonAsyncImage.kt` (the 1.12.1 poster code) stay unchanged.

### Alternatives rejected

| Alternative | Why not |
|---|---|
| Option A, stop drawing the still at rest | Does not touch the cause (diagnosis.md: Consequence). |
| Option B, TextureView now | Costs power, frame timing and resolution; held as fallback (state.md Q2). |
| `requestLayout()` instead of `invalidate()` | Extra measure/layout of the Compose tree for the same pre-draw. |
| Invalidate the root or the `PlayerView` instead of the SurfaceView | Wider redraw; the SurfaceView invalidate is the proven call. |
| A stateful listener class with a per-view `Runnable` and `removeCallbacks` on release | More code for no gain: a pending post on a detached view runs one `invalidate()` that is a no-op and frees the view on the next loop iteration. |
| `setEnableComposeSurfaceSyncWorkaround(true)` | Targets API 34 stretch/sync on resize, not a missing surface; unproven for this cause. |
| Re-introducing a frame source in `VideoItemGridDetails.kt` (1.12.0's accidental fix) | Couples the player to a sibling's drawing again; out of scope. |

### Risks

| Risk | Where | Note |
|---|---|---|
| Rollout: vendor HWUI on the owner's TV still skips surface creation | `TrailerPreviewPlayer.kt` factory | Emulator proof only (brief.md: Risks). Fallback B below; costs one build round. |
| Lifecycle: listener left on a released view | `AndroidView` `onRelease` | The listener is stateless and owned by the view, so a miss does not leak; `onRelease` removes it anyway. Review checks add/remove use the same val. |
| Correctness: the predicate fires on every size change, e.g. `scaleTrailerToFit` toggling | `surfaceNeedsRedraw` | One extra frame per change, which `updateSurface()` needs for a resize too. Never fires for 0x0, so no traversal loop: `invalidate()` does not change size, so no new layout callback. |
| Focus and D-pad | N/A to the change | No focusable view or modifier is added; `PlayerView` keeps `useController = false` (`:128`). |
| Recomposition | `AndroidView` parameters | The listener is a top-level val and `onRelease` a non-capturing lambda; no new state read in composition. |
| Main thread / performance | `v.post` | One message and one draw per player size change. |
| Baseline profile | `:baselineprofile` | The new code is small and on a path the profile already reaches; no regeneration needed for this patch. |

### Fallback B, if AC-3 fails on the TV

- New `app/src/main/res/layout/trailer_player_view.xml`: a single `androidx.media3.ui.PlayerView` with `app:surface_type="texture_view"` and `app:use_controller="false"` (PlayerView picks its surface only from XML).
- `factory` inflates it with `LayoutInflater.from(ctx)` instead of `PlayerView(ctx)`; the redraw listener and `surfaceNeedsRedraw` are removed with their test, since a TextureView composites in the UI layer.
- KDoc `:36-38` drops the SurfaceView wording. The still/fade logic in `VideoItemGridDetails.kt` stays as is: a TextureView is not a hole, so the still over it is ordinary drawing.

### Open questions

None.

## Test design

Base 3ec85287. ACs: `brief.md#Acceptance criteria`; fix T and risks: `design-architect.md`. Levels from `.flow/roles/test-designer.md`. No Compose UI harness exists, so the only unit seam is `surfaceNeedsRedraw` (`design-architect.md#Decisions`). Whether the listener sits on the real SurfaceView and the surface gets created can only be seen on a device.

New test file: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt`. It uses plain JUnit 5 like `VideoDetailsPosterStateTest.kt` and needs no dispatcher extension.

### Risk profile

| Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|
| The wrong APK is checked: emulator-5554 still has the diagnostic build | brief Risks | 3 | 3 | 9 | D0 |
| Vendor HWUI on the owner's TV still skips surface creation | design Risks: rollout | 2 | 3 | 6 | T1, T2; fallback B (state.md Q2) |
| The listener is attached to a view other than the SurfaceView, or `videoSurfaceView` is null, so no traversal reaches `updateSurface()` | design Decisions | 2 | 3 | 6 | D1 (the device is the level where this fails) |
| Headless screencap renders no video layer, so the evidence cannot show video directly | task note | 3 | 2 | 6 | D1 uses SurfaceFlinger layer + `--latency` frames + still absence; T1 shows video |
| Predicate fires for 0x0 or misses a resize (`scaleTrailerToFit` toggle) | design Risks: correctness | 2 | 2 | 4 | unit rows AC-6 |
| Fix regresses the source run's poster behaviour (flash, skeleton, fade) | AC-5 | 1 | 3 | 3 | P0 guard unit rows; D4; T3 |
| The first trailer after opening a screen regresses | AC-2 | 1 | 2 | 2 | D2 |
| An invalidate loop redraws every frame (power) | design Risks: correctness | 1 | 2 | 2 | Accepted: `invalidate()` changes no size, so it causes no layout callback; review item |
| Listener left on a released view | design Risks: lifecycle | 1 | 1 | 1 | Accepted: stateless listener; review checks that add and remove use the same val |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero` | Fails if the predicate misses the late first layout, which is the bug path (0x0 → 1350x759) |
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_false_whenSizeUnchanged` | Fails if the predicate drops the "different from old" check and redraws on every layout pass |
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_true_whenSizeChangesBetweenNonZeroSizes` | Fails if the predicate fires only for 0 → non-zero, so a resize (fit/zoom toggle) gets no traversal |
| AC-6 | unit | P0 red | `surfaceNeedsRedraw_false_whenSizeBecomesZero` | Fails if a collapse to 0x0 asks for a redraw that cannot create a surface |
| AC-6 | unit | P1 | `surfaceNeedsRedraw_false_whenOnlyOneDimensionIsZero` | Fails if "non-zero" is read as `width > 0 \|\| height > 0` (e.g. 1350x0) |
| AC-5 | unit | P0 guard | `VideoDetailsPosterStateTest` and `SkeletonAsyncImageVisibilityTest`, all tests unchanged | Fail if the fix touches the 1.12.1 poster seams. `git diff 3ec85287 -- app/src/test/kotlin/com/kino/puber/core/ui/uikit/component` shows only the new file |
| AC-7 | unit | P0 guard | `check.full` in the worktree | Fails on any compile, test, detekt or lint regression (e.g. unused lambda parameters not named `_`, a missing `@UnstableApi` opt-in) |
| AC-1 | device | P0 red | D1 | Fails if the SurfaceView of trailers 2-4 gets no layer or no frames. On base, diagnosis.md: Evidence row 2 shows this failure, and qa does not re-run base |
| AC-2 | device | P1 | D2 | First trailer after screen open loses its video or fade |
| AC-4 | device | P0 red | D3 | Fails if the details hero or favourites keeps the poster opaque over an audible trailer. On base this is the `diagnosis.md#Evidence` row 2 symptom on the shared player call (`VideoItemGridDetails.kt:483`) |
| AC-5 | device | P0 guard | D4 | Fails if the source run's poster behaviour breaks: skeleton or black frame before the first frame, no fade, poster not back at trailer end, poster pixels around a fitted trailer |
| AC-3 | manual (owner TV) | P0 red | T1 | Fails if video is still hidden on the TV's HWUI; on 1.12.1 the TV shows the `diagnosis.md#Evidence` row 2 symptom |
| AC-4 | manual (owner TV) | P0 red | T2 | Fails if the hero or favourites still hide video on the TV; on 1.12.1 they show the row 2 symptom |
| AC-5 | manual (owner TV) | P1 | T3 | Smoothness regression the emulator cannot show |

### Failing-first test

`surfaceNeedsRedraw_true_whenSizeGoesFromZeroToNonZero` is written first. On base the seam does not exist (`git show 3ec85287:app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt` has no `surfaceNeedsRedraw`; the factory at `:126-132` adds no listener), so the test does not compile on base. The bug itself shows only on device (D1).

### Device checks (qa, emulator)

Package `com.kino.puber.stage` (dev flavor). Auto-trailer on. Keys go through `adb -s <serial> shell input keyevent` (`DPAD_RIGHT`, `DPAD_LEFT`, `DPAD_DOWN`, `DPAD_CENTER`, `BACK`). qa reads the probes below and changes no committed code: no temporary logging is built into the APK under test. `onRenderedFirstFrame` is proven indirectly. It is the only setter of `trailerRendered` (`VideoItemGridDetails.kt:469`), and `trailerRendered` is the only thing that drops the still to alpha 0 (`:506`). So a still that is gone after the fade, plus frames on the SurfaceView layer, proves the first-frame callback fired.

Probes, run about 3 s after a trailer starts:

| Probe | Command | Pass |
|---|---|---|
| Audio | `dumpsys audio \| grep -A2 USAGE_MEDIA` | `state:started` (a trailer is running) |
| Surface layer | `dumpsys SurfaceFlinger --list \| grep SurfaceView \| grep com.kino.puber.stage` | One SurfaceView layer for the app's window |
| Frames | `dumpsys SurfaceFlinger --latency '<exact layer name from --list>'` | Non-zero timestamp rows, growing between two reads 1 s apart |
| Still | `screencap -p` pulled after the 300 ms fade | Media area shows no poster pixels (headless: black/empty hole) |

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| D0 | 1. Lease a device with `tools/emulator-resource-lock.sh`. 2. Install this worktree's dev APK with `tools/android-apk-install-preserve`. 3. Compare the on-device `base.apk` sha256 with the built APK. | Hashes match; the app keeps its login | Uninstall, data clear, the diagnostic build still installed |
| D1 | 1. Open the catalogue grid and let the first trailer start (dwell over 2 s). 2. `DPAD_RIGHT` to a card with a trailer, dwell, run all probes. 3. Repeat step 2 twice more. | All probes pass for each of the three trailers | Audio `started` with no SurfaceView layer, no latency rows, or the poster in the screencap |
| D2 | 1. `BACK` out to home and open the catalogue again. 2. Dwell on the first card with a trailer and run the probes. | All probes pass, as on 1.12.1 | No layer or the still opaque on the first trailer |
| D3 | 1. Open a details screen with a trailer and wait for the hero trailer. Run the probes. 2. `BACK`, open favourites, change cards three times with dwells as in D1 and run the probes each time. | All probes pass on the hero and on every favourites trailer | Opaque still over audible playback; focus loss on `BACK` |
| D4 | 1. Run the source run's M1, M3 and M5 (`.flow/runs/detail-poster-trailer-flicker/test-design.md#Manual checks (device)`) on this build. 2. Run its M2: rest on a card with a trailer for over 2 s and take a screencap burst from URL arrival (audio `started`) through the first frame and the fade, about every 50 ms. | Step 1: results as recorded for 1.12.1. Step 2: poster at full opacity until the first frame, then fading out over about 300 ms to the blank hole | Skeleton or empty frame between posters, poster not back at trailer end, poster pixels around a fitted trailer; in step 2 a skeleton, black or empty frame before the fade starts, or a hard cut instead of a fade |

Headless screencap draws no video layer, so D4 step 2 sees the still and its fade over a blank layer: it can catch a skeleton or black frame and a missing or abrupt fade, not a flash between the poster and real video pixels; T3 covers that.

Release the lease after D4. The evidence directory passes `tools/mobile-evidence-audit.sh` and holds no raw logcat or trailer URLs.

### Manual checks (owner, TV)

The conductor builds the APK and the owner installs and watches. Agents never touch the TV (state.md: Decisions).

| # | Steps | Expected | Must not happen |
|---|---|---|---|
| T1 | 1. Open the catalogue and let a trailer play. 2. Move to another card with a trailer, wait. Repeat at least three times. | Every trailer shows video; the poster fades out over it in about 300 ms | Audio with the poster still fully visible (the 1.12.1 symptom) |
| T2 | 1. Repeat T1 on the favourites panel. 2. Open a details screen with a trailer. | Same as T1 on both | Same as T1 |
| T3 | Watch trailer start, trailer end and fast card changes in the catalogue | No skeleton or black flash, poster back at once at trailer end, smooth crossfades | Any flash or hard cut absent on 1.12.1 |

If T1 or T2 fails, the run switches to fallback B (`design-architect.md#Fallback B`). The AC-6 unit rows are then removed with the seam, and D1-D4 and T1-T3 run again.

### perf track

N/A: perf track off.

### UNTESTABLE

None.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Replace option A with fix T? | yes | answered | Yes, T replaces A (owner, 2026-11-19) |
| Q2 | If T works on the emulator but not on the TV, switch to option B in this run? | no | answered | Yes (owner, 2026-11-19) |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-19 | Fix T replaces option A | owner |
| 2026-11-19 | Fallback B (TextureView) in this run if T fails on the owner's TV | owner |
| 2026-11-19 | Gate 1: spec approved as written (size M, tracks none, 7 ACs, design T, test design as above) | owner (standing instruction: recommended) |
| 2026-11-19 | AC-4's favourites part is N/A: `FavoriteScreenContent.kt` passes no `trailerUrl`, so favourites never plays a trailer, before or after this fix. AC-4 covers the details hero only, and the owner skips favourites in T2 (triage-1 row 2) | owner (standing instruction: recommended) |
