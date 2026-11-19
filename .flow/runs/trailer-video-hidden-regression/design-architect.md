# Design (architect): trailer-video-hidden-regression

Fix T from `brief.md#fix-direction`. Paths are relative to `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/` unless they start with `app/`.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Which view gets the listener | `playerView.videoSurfaceView`, read once in the `factory` (`TrailerPreviewPlayer.kt:126-132`), null-safe | media3 1.11.0 `PlayerView.getVideoSurfaceView()` returns the `private final View surfaceView` field (bytecode), annotated `@Nullable` and `@UnstableApi`; the composable is already `@UnstableApi` (`TrailerPreviewPlayer.kt:40`). The field is final, so the view the listener is added to is the one `onRelease` sees. |
| Listener shape | One stateless top-level `private val` `View.OnLayoutChangeListener` that uses only its `v` argument; unused lambda parameters named `_` | Captures nothing, so it can hold neither the player, the context nor the composition. `View.addOnLayoutChangeListener` ignores a listener already in the list, so re-adding is safe. |
| Listener lifecycle | Add in `factory`; remove in a new `onRelease = { it.videoSurfaceView?.removeOnLayoutChangeListener(...) }` on the `AndroidView` (`TrailerPreviewPlayer.kt:125`) | Compose UI 1.12 `AndroidView(factory, modifier, onReset, onRelease, update)` exists (`ui-android` 1.12.1 bytecode). `onReset` stays unset, so the view is never reused and `factory`/`onRelease` pair one to one. The `DisposableEffect` at `:73-95` keeps owning the player. |
| Size predicate seam | `internal fun surfaceNeedsRedraw(oldWidth: Int, oldHeight: Int, newWidth: Int, newHeight: Int): Boolean` = new size non-zero and different from the old size; top-level in `TrailerPreviewPlayer.kt`. The listener passes `right - left`, `bottom - top` and the `old*` equivalents | Pure, so AC-6 is a plain JUnit 5 test with no Android stubs. Position-only moves return false: SurfaceView repositions through its RenderNode position callback, not a new surface. |
| `invalidate()` or `requestLayout()` | `invalidate()` on the SurfaceView | `SurfaceView.updateSurface()` runs from the window's pre-draw listener; any traversal reaches it, and a draw-only traversal is enough (diagnosis.md: Evidence row 4). `requestLayout()` adds a measure/layout pass through `AndroidComposeView` for nothing. |
| `post` or synchronous / `postOnAnimation` | `v.post { v.invalidate() }` | The late size arrives inside the current traversal (Compose layout in the draw pass); posting moves the invalidate after that traversal, which the experiment proved. Whether a synchronous call would also schedule one is `unverified` and not needed. `post` is the proven variant. `postOnAnimation` could land the traversal one frame earlier, but it is unproven and the gain (at most one vsync, before the 300 ms fade) is invisible. |
| KDoc | Extend `TrailerPreviewPlayer.kt:36-38` with one sentence: the SurfaceView is redrawn once it gets a size, because a late layout gets no surface otherwise | The next reader must not remove the listener as dead code. |

## Changes by layer

| Layer | Change |
|---|---|
| API / repository | N/A: no data change. |
| ViewModel state and actions | N/A: no state change. |
| Screen and DI module | N/A: the single call site `VideoItemGridDetails.kt:483-492` serves catalogue, favourites and details hero unchanged. |
| Compose content | `TrailerPreviewPlayer.kt`: add `surfaceNeedsRedraw`, the private listener val, `videoSurfaceView?.addOnLayoutChangeListener(...)` inside the `apply` at `:127-132`, `onRelease` on the `AndroidView` at `:125-139`, KDoc at `:36-38`. Imports: `android.view.View`. |
| Resources | N/A. |
| Tests | New `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt`, JUnit 5 like `VideoDetailsPosterStateTest.kt:18-20`: the four AC-6 cases. Names and count come from test-design.md. |

`VideoItemGridDetails.kt` and `SkeletonAsyncImage.kt` (the 1.12.1 poster code) stay unchanged.

## Alternatives rejected

| Alternative | Why not |
|---|---|
| Option A, stop drawing the still at rest | Does not touch the cause (diagnosis.md: Consequence). |
| Option B, TextureView now | Costs power, frame timing and resolution; held as fallback (state.md Q2). |
| `requestLayout()` instead of `invalidate()` | Extra measure/layout of the Compose tree for the same pre-draw. |
| Invalidate the root or the `PlayerView` instead of the SurfaceView | Wider redraw; the SurfaceView invalidate is the proven call. |
| A stateful listener class with a per-view `Runnable` and `removeCallbacks` on release | More code for no gain: a pending post on a detached view runs one `invalidate()` that is a no-op and frees the view on the next loop iteration. |
| `setEnableComposeSurfaceSyncWorkaround(true)` | Targets API 34 stretch/sync on resize, not a missing surface; unproven for this cause. |
| Re-introducing a frame source in `VideoItemGridDetails.kt` (1.12.0's accidental fix) | Couples the player to a sibling's drawing again; out of scope. |

## Risks

| Risk | Where | Note |
|---|---|---|
| Rollout: vendor HWUI on the owner's TV still skips surface creation | `TrailerPreviewPlayer.kt` factory | Emulator proof only (brief.md: Risks). Fallback B below; costs one build round. |
| Lifecycle: listener left on a released view | `AndroidView` `onRelease` | The listener is stateless and owned by the view, so a miss does not leak; `onRelease` removes it anyway. Review checks add/remove use the same val. |
| Correctness: the predicate fires on every size change, e.g. `scaleTrailerToFit` toggling | `surfaceNeedsRedraw` | One extra frame per change, which `updateSurface()` needs for a resize too. Never fires for 0x0, so no traversal loop: `invalidate()` does not change size, so no new layout callback. |
| Focus and D-pad | N/A to the change | No focusable view or modifier is added; `PlayerView` keeps `useController = false` (`:128`). |
| Recomposition | `AndroidView` parameters | The listener is a top-level val and `onRelease` a non-capturing lambda; no new state read in composition. |
| Main thread / performance | `v.post` | One message and one draw per player size change. |
| Baseline profile | `:baselineprofile` | The new code is small and on a path the profile already reaches; no regeneration needed for this patch. |

## Fallback B, if AC-3 fails on the TV

- New `app/src/main/res/layout/trailer_player_view.xml`: a single `androidx.media3.ui.PlayerView` with `app:surface_type="texture_view"` and `app:use_controller="false"` (PlayerView picks its surface only from XML).
- `factory` inflates it with `LayoutInflater.from(ctx)` instead of `PlayerView(ctx)`; the redraw listener and `surfaceNeedsRedraw` are removed with their test, since a TextureView composites in the UI layer.
- KDoc `:36-38` drops the SurfaceView wording. The still/fade logic in `VideoItemGridDetails.kt` stays as is: a TextureView is not a hole, so the still over it is ordinary drawing.

## Open questions

None.
