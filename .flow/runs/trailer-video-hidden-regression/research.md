# Research: trailer-video-hidden-regression

## 1. Request

Regression in release 1.12.1. On the owner's physical Android TV, the trailer preview in the detail panel (catalogue) plays audio but no video is visible. On 1.12.0 the video showed on the same TV. The change between them is run detail-poster-trailer-flicker (see .flow/runs/detail-poster-trailer-flicker/, `git diff 76a8bc2a 3ec85287 -- app/src/main/java/com/kino/puber/core/ui/uikit/component/`). VideoDetailsPoster in core/ui/uikit/component/details/VideoItemGridDetails.kt now keeps a single PosterStill (SkeletonAsyncImage) composed AFTER the SurfaceView-based TrailerPreviewPlayer (media3 PlayerView in AndroidView), faded with Modifier.graphicsLayer { alpha } to 0 after onRenderedFirstFrame. 1.12.0 removed the still from composition once the trailer rendered. Conductor hypothesis (unproven): a graphicsLayer with alpha < 1 (CompositingStrategy.Auto -> offscreen layer), or any content drawn over the SurfaceView hole, hides the video on some TV hardware/HWUI paths. Investigate: the exact rendering path (Compose graphicsLayer alpha 0 / offscreen layer vs SurfaceView hole punching, HWUI behaviour, known media3/Compose issues with SurfaceView under siblings), other plausible causes in the diff (e.g. onRenderedFirstFrame never firing so alpha stays 1, Coil crossfade painter, the 0.32 surfaceVariant background inside SkeletonAsyncImage, placeholder modifier), and fix options that keep the 1.12.1 gains (no skeleton flash at trailer start/end or card change): e.g. ModulateAlpha compositing during the fade, skipping draw entirely (drawWithContent no-op) once fully faded, leaving composition while preserving the image, or switching the player to TextureView / media3-ui-compose PlayerSurface(SURFACE_TYPE_TEXTURE_VIEW). Note: the headless emulator in this host renders no video layer, so device proof will come from the owner's TV only. Do not use any device. Also check whether DUPLICATE_OF any existing run in .flow/runs.

## 2. Understanding

The only runtime change between v1.12.0 (`76a8bc2a`) and v1.12.1 (`3ec85287`) is in `VideoItemGridDetails.kt` and `SkeletonAsyncImage.kt` (`git diff --stat`; versions in `gradle/libs.versions.toml` unchanged). With a trailer playing, 1.12.0 had **no** Compose content between the player and the scrims. 1.12.1 keeps a full-size still there for the trailer's whole lifetime: an outer `graphicsLayer` at alpha 0, an inner `Modifier.alpha(1)` layer and a Coil painter. On stock AOSP HWUI (API 29+), a RenderNode with alpha <= 0 is skipped, so the resting frames should match 1.12.0 pixel for pixel. The mechanism on the owner's TV is therefore not proven. It may be vendor compositing (the TV recorded in 2026-08 evidence is a "KM6"), a View-backed Compose layer on API < 29, or the still never fading. The fix that rests on proven ground is the one that brings back 1.12.0's resting shape (nothing over the hole once the trailer shows) and keeps the still's state, so trailer end and card change stay flash-free.

## 3. Current state in code

Paths are relative to `app/src/main/java/com/kino/puber/core/ui/uikit/component/`.

- `details/VideoItemGridDetails.kt:469` `trailerRendered` is `remember(trailerUrl)` and is set only by `onFirstFrameRendered` (`:486`).
- `details/VideoItemGridDetails.kt:471-480` `fade = animateFloatAsState(...)`, with `tween(300)` to 0 once rendered and `snap()` otherwise.
- `details/VideoItemGridDetails.kt:482-493` The player is composed first, only while `trailerUrl != null`.
- `details/VideoItemGridDetails.kt:501-513` A single `PosterStill`, always composed after the player, with `.graphicsLayer { alpha = posterStillAlpha(...) }` (`:506`). The default `CompositingStrategy.Auto` applies. At rest over a rendered trailer it is alpha 0, but its layer, child nodes and painter stay in the tree.
- `details/VideoItemGridDetails.kt:515-565` The scrims (full-bleed gradient or 48 dp edge, plus the bottom fade) are drawn over the hole in both versions, so they are not the trigger.
- `details/VideoItemGridDetails.kt:584-594` `posterRequest`: `useExistingImageAsPlaceholder(true)` plus `PosterCrossfade` (200 ms), defined at `:681-689`. The crossfade only runs on a URL change, not on trailer start.
- `details/VideoItemGridDetails.kt:711-719` `posterFadeTarget` / `posterStillAlpha`.
- `SkeletonAsyncImage.kt:33-35` The outer `Box` has a background of 0.32 `surfaceVariant`. It sits inside the alpha-0 layer, so it is invisible at rest.
- `SkeletonAsyncImage.kt:57-59` `AsyncImage` carries `Modifier.alpha(...)`, which is a second graphicsLayer. 1.12.0 had it too, but removed it with the still at first frame.
- `SkeletonAsyncImage.kt:63-67` The skeleton is drawn only while `skeletonVisible(...)`. `placeholder(false)` draws nothing (`modifier/Placeholder.kt:22-39`).
- `details/TrailerPreviewPlayer.kt:85-87` `onRenderedFirstFrame` → caller. This file is unchanged since 1.12.0.
- `details/TrailerPreviewPlayer.kt:125-139` `PlayerView` in `AndroidView` with the default `surface_type` (SurfaceView) and no `setEnableComposeSurfaceSyncWorkaround`.
- Callers that share the path: the catalogue `ui/feature/contentlist/content/ContentListScreenContent.kt:166` and the details hero `ui/feature/details/component/DetailsScreenContent.kt:189`.
- Tests: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` holds pure-function tests for `heldPosterUrls`, `posterFadeTarget`, `posterStillAlpha`, `posterRequest` and `PosterCrossfade`. `.../component/SkeletonAsyncImageVisibilityTest.kt` tests `skeletonVisible`. No Compose or UI test covers the poster or the player.

Tree over the SurfaceView hole, derived from the diff:

| Moment | 1.12.0 (`76a8bc2a`) | 1.12.1 (`3ec85287`) |
|---|---|---|
| Buffering | Over-copy of the still inside `AnimatedVisibility` (opaque), with its own `.alpha(1)` layer | The single still, outer layer alpha 1, inner `.alpha(1)` |
| Fade (about 300 ms) | `fadeOut()` animates a graphicsLayer alpha from 1 to 0: an offscreen alpha layer over the hole | Outer layer `tween` from 1 to 0: the same kind of layer |
| Trailer playing, at rest | Nothing: both stills have left composition | The still at outer alpha 0, plus the inner layer, painter and background |

The fade, the one moment where an offscreen alpha layer sits over the hole, already happened on 1.12.0, and 1.12.0 worked on that TV. The resting state is the only new thing.

## 4. Related work

| Ref | Relevance |
|---|---|
| Run `detail-poster-trailer-flicker` (done, `a7611630`, released `v1.12.1`) | The source of the regression. Q3 kept the SurfaceView. The design rejected "remove the still after the fade" because it re-creates the still on trailer end (`design-architect.md` alternatives table). `judge-1.md` §Manual checks item 1 (fade over the playing video) was never observed. |
| Run `focus-trailer-preview` (done), commit `34652167` | Introduced the still drawn after the SurfaceView, because the PlayerView shutter is black and the hole is transparent. |
| Commit `85bfd1d2` | Removed the under-still once rendered, so poster pixels do not frame a fitted trailer. That also kept 1.12.0's resting tree empty. |
| `docs/superpowers/evidence/2026-08-20-trailer-panel-and-controls-device.md:3` | The trailer was proven on the "television" `KM6`, 1920×1080 UI. `screencap` and `screenrecord` blank while video renders, and `dumpsys audio` tells which trailer player is running. Whether this is the owner's current TV is `unverified`. |
| Run `detail-poster-fallback-empty-frame` (idea) | Same component (`SkeletonAsyncImage` failed URL). Not a duplicate. |
| Run `detail-poster-directional-change` (idea) | Same component, later poster animation. It waits for this run. |
| Run `media3-hagc-removal` (idea) | Only relevant if option B adds `media3-ui-compose`. |

Possible duplicate: none.

## 5. External findings

| Finding | Source |
|---|---|
| HWUI `RenderNode::nothingToDraw()` returns true when `properties().getAlpha() <= 0`, and `RenderNodeDrawable::forceDraw` returns early on it. An alpha-0 node therefore draws nothing on stock Android 9+ (Skia pipeline). | https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderNode.h, https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-9.0.0_r1/libs/hwui/RenderNode.h, https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/pipeline/skia/RenderNodeDrawable.cpp (read 2026-11-19) |
| Compose `AndroidGraphicsContext.createGraphicsLayer`: API 29+ uses `GraphicsLayerV29` (RenderNode). Below 29 it tries `GraphicsLayerV23` and falls back to the View-based `GraphicsViewLayer` if that fails. Which path Compose BOM 2026.09.00 takes on the owner's TV is `unverified`. | https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidGraphicsContext.android.kt (androidx-main, 2026-11-19) |
| SurfaceView: "the transparent region that makes the surface visible is based on the layout positions in the view hierarchy". A sibling drawn on top through post-layout transforms "may not be properly composited". Overlays cost "a full alpha-blended composite". Before API 34, alpha between 0 and 1 *on the SurfaceView itself* was ignored. Whether a View-backed Compose layer at alpha 0 still cuts the transparent region is `unverified`. | https://developer.android.com/reference/android/view/SurfaceView |
| media3: prefer SurfaceView (power, frame timing, HDR, full display resolution on TVs that upscale the UI). `PlayerView` in `AndroidView` is "not guaranteed" compatible; `setEnableComposeSurfaceSyncWorkaround` exists for API 34 stretch issues. `media3-ui-compose` `PlayerSurface` / `ContentFrame` offer `SURFACE_TYPE_TEXTURE_VIEW`. `PlayerView` picks its surface only through the XML `surface_type` attribute. | https://developer.android.com/media/media3/ui/surface (current docs) |
| media3 release notes: `ContentFrame` added in 1.9.0, with a 1.11.0 fix for first-frame size on recompose and a 1.11.0 shutter-suppression fix in `PlayerView`. The latest stable is 1.11.1; the project uses 1.11.0. | https://raw.githubusercontent.com/androidx/media/release/RELEASENOTES.md |
| Audio-only/black-video reports on Amlogic S905X3/X4 and MediaTek TV SoCs exist, but none ties the symptom to a view or alpha overlay. KM6 = Amlogic S905X4 is `unverified`. | https://code.videolan.org/videolan/vlc-android/-/issues/1655, https://github.com/androidx/media/issues/2711 |
| Coil `placeholderMemoryCacheKey` exists as a request option (recipes). Its Coil 3.6.3 signature is `unverified`. | https://coil-kt.github.io/coil/recipes |

## 6. Options

| # | What | Trade-offs | Size |
|---|---|---|---|
| **A (recommended)** | Restore 1.12.0's resting shape and keep the still's state. Once `trailerRendered` holds and `fade` has reached 0, the still stays in composition (painter, `hasShownImage` and request survive) but is neither placed nor drawn: a `Modifier.layout` that skips `place`, and no outer `graphicsLayer` while hidden. During the 300 ms fade, use `graphicsLayer { alpha; compositingStrategy = ModulateAlpha }` so no offscreen buffer is allocated. The return on trailer end stays in the same frame, because the composition-time alpha 1 places it again. Add pure tests for the "hidden at rest" predicate. | Matches the shape proven on the TV. Keeps every 1.12.1 gain and adds no dependency. Whether Compose releases the layers of unplaced children is `unverified`. If the TV's problem is the fade itself (unlikely: 1.12.0 faded too) or the still never fading (Q1), this does not fix it. Proof comes only from the owner's TV. | S |
| A' | A variant of A: let the still leave composition after the fade, exactly as in 1.12.0. On trailer end, compose a fresh still seeded with the last image (`placeholderMemoryCacheKey` or a held `Image`) and the skeleton suppressed from the start. | Byte-for-byte 1.12.0 at rest. Risks a one-frame empty panel on trailer end if Coil does not resolve the memory hit synchronously (`unverified`), which is the flash that run fixed. Touches `SkeletonAsyncImage`'s contract. | S |
| B | Render the trailer into a TextureView: either `PlayerView` inflated from a layout with `app:surface_type="texture_view"`, or `media3-ui-compose` `ContentFrame(surfaceType = SURFACE_TYPE_TEXTURE_VIEW)`. The video then composites in the UI layer, so any still or alpha over it is ordinary drawing, and the video itself can fade in. | Removes the hole-punch class of problems on every vendor. Costs power and frame-timing accuracy, and caps the video at UI resolution (1080p UI on the KM6), which may be acceptable for a muted-size preview. Reverses Q3 of the source run. The compose path adds a dependency that `media3-hagc-removal` must then consider. | M |

## 7. Risks

- **No local proof.** The headless emulator renders no video layer, and `screencap`/`screenrecord` blank on the TV (2026-08-20 evidence). Every trailer AC is the owner's eye, and a wrong guess costs a full build-install-look round.
- **Cause not isolated.** On stock HWUI, alpha 0 is already a no-op. If Q1 shows the poster is still visible while audio plays, the cause is the fade never completing or `onRenderedFirstFrame` never firing, not compositing, and A does not apply.
- **Trailer-end flash.** Any fix that re-creates the still (A') can bring back the skeleton or empty frame that `detail-poster-trailer-flicker` AC-3 removed.
- **Fitted trailer.** The still must be fully gone (not partially transparent) over a letterboxed trailer (`85bfd1d2`).
- **Shared path.** The details hero (`DetailsScreenContent.kt:189`) goes through the same `VideoDetailsPoster`, so the owner's check must include it.

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | While the audio plays, what does the panel show: the poster at full opacity, black, or the bare panel background? | Ask the owner. If the poster is visible, the still never faded (cause is the fade or first frame, not compositing). If it is black or empty, compositing is the cause and A applies. |
| Q2 | Which TV and Android version is it (the `KM6` from the 2026-08-20 evidence)? Does the details screen's hero trailer show video on 1.12.1? | Ask the owner. The API level decides whether Compose layers are RenderNodes (29+) or may be Views (< 29). |
| Q3 | If Q1 cannot be answered before the build, which option ships first? | A. It restores the resting shape that worked on 1.12.0 and keeps the 1.12.1 gains. Fall back to B only if A fails on the TV. |

## 9. Sources

Files:

- `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImage.kt`
- `app/src/main/java/com/kino/puber/core/ui/uikit/component/modifier/Placeholder.kt`
- `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt`
- `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImageVisibilityTest.kt`
- `gradle/libs.versions.toml`
- `buildSrc/src/main/kotlin/Versions.kt`
- `docs/superpowers/evidence/2026-08-20-trailer-panel-and-controls-device.md`

Commits: `76a8bc2a` (v1.12.0), `3ec85287` (v1.12.1), `a7611630`, `c992db1f`, `34652167`, `85bfd1d2`.

Runs: `detail-poster-trailer-flicker` (state, research, design-architect, judge-1), `focus-trailer-preview`, `detail-poster-fallback-empty-frame`, `detail-poster-directional-change`, `media3-hagc-removal`; the full `.flow/runs/*/state.md` list was scanned for duplicates.

URLs:

- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/RenderNode.h
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-9.0.0_r1/libs/hwui/RenderNode.h
- https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/libs/hwui/pipeline/skia/RenderNodeDrawable.cpp
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-9.0.0_r1/libs/hwui/pipeline/skia/RenderNodeDrawable.cpp
- https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui-graphics/src/androidMain/kotlin/androidx/compose/ui/graphics/AndroidGraphicsContext.android.kt
- https://developer.android.com/reference/android/view/SurfaceView
- https://developer.android.com/media/media3/ui/surface
- https://raw.githubusercontent.com/androidx/media/release/RELEASENOTES.md
- https://github.com/androidx/media/issues/1237
- https://github.com/androidx/media/issues/2711
- https://code.videolan.org/videolan/vlc-android/-/issues/1655
- https://coil-kt.github.io/coil/recipes
