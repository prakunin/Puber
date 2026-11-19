# Brief: trailer-video-hidden-regression

Base 3ec85287 (v1.12.1). Analyst, 2026-11-19. Builds on `research.md` and `diagnosis.md`. The plugin has no brief template, so this brief uses the analyst job layout. Paths are relative to `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/`.

kind: bug. size: M. tracks: none. Device verification: **yes** (playback surface, runtime-only ACs; final proof is the owner's TV).

## Request
On 1.12.1 the detail panel's trailer preview plays audio but shows no video on the owner's TV; 1.12.0 showed it (state.md: Log). Fix ships as release 1.12.2.

## Root cause
**Confirmed** (reproduced on emulator-5554, diagnosis.md: Root cause, Evidence).

| # | Step | Where |
|---|------|-------|
| 1 | After a card change the `AndroidView` with `PlayerView` (default `SurfaceView`) is inserted while the window is otherwise idle. | `TrailerPreviewPlayer.kt:125-139`, composed at `VideoItemGridDetails.kt:482-493` |
| 2 | The window's only pre-draw runs while the `SurfaceView` is 0x0; it gets its size later, in Compose layout inside the draw pass. Nothing schedules another traversal, so `SurfaceView.updateSurface()` never creates the surface. | diagnosis.md: Mechanism |
| 3 | ExoPlayer plays audio with no surface, `onRenderedFirstFrame` (`TrailerPreviewPlayer.kt:85-87`) never fires, `trailerRendered` (`VideoItemGridDetails.kt:469`) stays false, and the still stays at alpha 1 (`:506`). Matches the owner's report that the poster stays fully visible (state.md: Decisions). | |
| 4 | 1.12.0 worked by accident: its fresh over-copy of the still loaded on every trailer start and drove a second pre-draw about 18 ms later. 1.12.1's single still (from `detail-poster-trailer-flicker`) draws nothing new at trailer start. | diagnosis.md: Why 1.12.0 worked |

The first trailer after opening a screen works on 1.12.1 because the window is still producing frames then. Ruled out: the alpha-0 layer over the video, `trailerRendered` reset, player re-creation, codec errors (diagnosis.md: Ruled out).

## Fix direction
Research option A (stop drawing the still at rest) does not touch this cause (diagnosis.md: Consequence). Options that do:

| # | What | Trade-offs |
|---|------|------------|
| **T (recommended)** | In `TrailerPreviewPlayer`'s `factory` (`TrailerPreviewPlayer.kt:126-132`), add an `OnLayoutChangeListener` to `playerView.videoSurfaceView`; when its size changes to a non-zero size, `post { invalidate() }` on it, forcing the traversal that runs `updateSurface()`. The size predicate is a pure top-level function with a unit test. | One file, no dependency, keeps the SurfaceView (Q3 of the source run). The posted `invalidate()` fixed trailer 2 on the emulator within 30 ms (diagnosis.md: Evidence row 4). Independent of what the poster draws, so it no longer relies on a sibling's frames. |
| B | Render into a TextureView (`surface_type="texture_view"` layout or `media3-ui-compose`). | Avoids the hole-punch path entirely but costs power and frame timing, caps the video at UI resolution and reverses the source run's Q3 (research.md: Options). Fallback only if T fails on the TV. |

## Scope
In scope: `TrailerPreviewPlayer.kt` and a new unit test next to `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt`. All three screens share the player: catalogue (`ui/feature/contentlist/content/ContentListScreenContent.kt:166`), favourites (`ui/feature/favorites/content/FavoriteScreenContent.kt:99`), details hero (`ui/feature/details/component/DetailsScreenContent.kt:189`).

Out of scope: `VideoItemGridDetails.kt` poster logic, `SkeletonAsyncImage.kt`, option B, the 1.12.2 version bump (done by the release procedure, `docs/release.md`, after ship).

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

## Risks
- **Proof on the TV only.** The emulator shows surface creation and first frame, but vendor HWUI on the owner's TV may differ; if AC-3 fails there, option B is the fallback and costs one more build round.
- **Playback risk area** (`.flow/roles/analyst.md`), hence M despite one file: the listener runs inside media3's `PlayerView`; `videoSurfaceView` may be null for `surface_type="none"` and must be null-safe.
- **Emulator state.** emulator-5554 holds a diagnostic build (1.12.0 poster code plus logging, state.md: Log); qa must install this worktree's APK with `tools/android-apk-install-preserve` before checking AC-1.

## Open questions
| ID | Question | Recommended answer | Blocks |
|----|----------|--------------------|--------|
| Q1 | The owner chose option A before the diagnosis. Replace it with fix T (forced traversal of the SurfaceView) and drop A from this run? | Yes. A does not address the cause, and the alpha-0 resting still is already a no-op (diagnosis.md: Ruled out). | yes |
| Q2 | If T shows video on the emulator but not on the TV, switch to option B (TextureView) in this run? | Yes, within this run before 1.12.2 is cut, since the release exists to fix this symptom. | no |
