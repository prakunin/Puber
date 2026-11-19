# Diagnosis: trailer-video-hidden-regression

Done by a diagnostic agent on emulator-5554 (API 36) on 2026-11-19, with temporary `Log.d("PosterDiag")` instrumentation. The instrumentation was never committed, and the worktree is clean. Raw logcat stays outside git, because `mobile-evidence-audit.sh` rejects unscoped logcat and the trailer URLs are signed.

## Root cause (high confidence, reproduced)

On every trailer after the first one, the trailer's `SurfaceView` never gets a surface. ExoPlayer reaches READY and plays audio (`dumpsys audio`: `state:started USAGE_MEDIA`). It has no surface to draw video into, so `onRenderedFirstFrame` never fires. `trailerRendered` stays false, and the still stays at alpha 1 for the whole trailer. This is the owner's TV symptom.

Mechanism:
- `TrailerPreviewPlayer`'s `AndroidView` is inserted after a card change, while the window is otherwise idle.
- The window's one pre-draw pass runs while the `SurfaceView` is still 0x0. The view gets its 1350x759 size later, in Compose's layout inside the draw phase.
- Nothing redraws the window after that, so `SurfaceView.updateSurface()` never runs again. No surface is created, and SurfaceFlinger has no SurfaceView layer.

Why 1.12.0 worked: `VideoItemGridDetails.kt` composed a fresh over-the-player poster copy, inside `AnimatedVisibility` with its own Coil load, every time a trailer started. That copy's first frames drove a second pre-draw about 18 ms later, and the surface was created then. 1.12.1 has a single still that does not change at trailer start, so nothing redraws.

Why the first trailer worked on 1.12.1: the first navigation (details, focus, poster loads) was still producing frames when the player was laid out.

## Evidence

| Build | Trailer | Surface | First frame | Still alpha |
|---|---|---|---|---|
| 1.12.1 | 1 | 1350x759 at once | fired | 1.0 → 0.0 over about 300 ms; the screenshot shows the video |
| 1.12.1 | 2, 3 (after a card change) | never created | never | 1.0 throughout; the screenshot shows only the poster |
| 1.12.0 poster code, same player | 1, 2, 3 | created, about 18 ms after the late layout on 2 and 3 | fired | removed |
| 1.12.1 plus a temporary `invalidate()` posted on the SurfaceView after the late layout | 2 | created within 30 ms | fired | reaches 0 |

## Ruled out

- The alpha, fade or `graphicsLayer` logic: the layer block re-runs and reaches 0 whenever a first frame arrives.
- An alpha-0 layer drawn over the video hiding it. This was the conductor's first hypothesis.
- `trailerRendered` being reset, and the player being re-created.
- A codec or media error: no `onPlayerError` fired.

## Consequence for the options in research.md

- Option A (stop drawing the still at rest) does not address this cause.
- Any fix must give the `SurfaceView` one more traversal after its size is set, or stop depending on that traversal (a TextureView, option B). The `invalidate()` experiment proves that the first route is enough on the emulator.
