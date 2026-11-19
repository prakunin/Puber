### F1 [nit] Two KDoc blocks are stacked above `PosterCrossfade`, and the first one describes `heldPosterUrls`
- Where: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:650-659`
- Problem: each declaration should carry only its own KDoc. Here the block at :651 ("While the next card's details load...") sits directly above the block at :656, and both sit above `PosterCrossfade` (:659). The helper the first block describes, `heldPosterUrls`, is at :668. Nothing changes on screen. qa noted the same thing.
- Failure scenario: none for the viewer. A reader, or Dokka, attaches the wrong text to `PosterCrossfade`.
- Fix: move the block at :651-655 down to sit directly above `heldPosterUrls` at :668.

**Code against the design delta (all match):**
- **Timings:** `POSTER_FADE_OUT_MS = 300` uses `tween(..., FastOutSlowInEasing)`, and it only runs while a trailer has rendered. In every other case the fade is `snap()`. `posterStillAlpha` also returns 1 in the same frame the trailer goes, so the poster returns instantly. `POSTER_CROSSFADE_MS = 200` comes through a custom `Transition.Factory` that also crossfades memory-cache hits (the warm-change fix from the delta). It uses `useExistingImageAsPlaceholder(true)`.
- **Layer order:** player, then the single still, then the horizontal scrim or the 48 dp gradient, then the bottom fade (`:473-556`). The scrims stay above the still and the player.
- **Fade covers the 0.32 `surfaceVariant` background:** the `graphicsLayer { alpha }` is the outermost modifier given to `PosterStill`. `SkeletonAsyncImage` chains `.background(surfaceVariant 0.32)` onto it (`SkeletonAsyncImage.kt:34-35`), so the tint and both skeleton layers fade with the image. Nothing greys the trailer.
- **Shared constants:** the diff does not touch `MediaScrim`, the width fractions, `ExpandedMediaLayout`, `ContentScale.FillWidth` or the alignment rules, the trailer fit, or `Placeholder.kt`. `skeletonOnlyUntilFirstImage` defaults to `false`, and only `PosterStill` turns it on.

**Device (reused qa's screenshots in the session scratchpad; I took no new lease):**
- **Card-change bursts:** I re-scanned all 85 frames in `.../scratchpad/burst/` over the media area (x 960-1920, y 0-540). No frame has more than 1% of pixels within 4 of `#625B71` (the skeleton grey), and no frame is mostly black.
- **Catalogue panel at rest** (`w0.png`): the poster runs down to y ≈ 540 px (270 dp), with the bottom fade over its last ~100 px (50 dp). This matches `MediaScrim.Catalogue`.
- **Details hero** (`fav0.png`): the media starts at x ≈ 662 px (331 dp) and ends in the bottom fade around y 772-836 px (418 dp hero). The still is fitted to width and centred, which leaves 64 px bands (`#171619`, the 0.32 tint over black) above and below the picture. That band is the open item already recorded in `docs/design/content/README.md` ("fitted to width and centred"). It existed before this run, so it is not counted as a deviation here.
- **Not measured:**
  - the 300 ms fade;
  - the instant return at the trailer's end;
  - the letterbox after the fade (no poster pixels and no tint around the video);
  - how smooth the 200 ms crossfade looks.

  The headless emulator never showed a SurfaceView layer, so these stay with the owner's manual check that qa's report lists under MANUAL.

VISUAL: match (static frames, scrims and card-change bursts). The trailer fade and letterbox are not measured, because the headless emulator renders no video layer.
