LENS: edge  ROUND: 1  BASE: 3ec85287

No findings.

### Unproven
- `TrailerPreviewPlayer.kt:136` `videoSurfaceView` is null, so no listener is attached and the bug comes back silently. This only happens if PlayerView is inflated with `surface_type="none"` or `texture_view`. Neither appears anywhere in `app/src/main`, and only `PlayerView(ctx)` with default attributes is created here. It would become real if someone set a surface type attribute or a custom layout.
- `TrailerPreviewPlayer.kt:157` the runnable is posted while the view is detached. If the AndroidView is detached between layout and the posted message, `post` queues the runnable in the view's run queue, and on a detached view `invalidate()` does nothing. Nothing fails and nothing leaks past one message. It would only matter if the same PlayerView were reattached later, which needs AndroidView reuse (`onReset`), and this code does not pass `onReset`.
- `TrailerPreviewPlayer.kt:154` a fitted trailer (`scaleTrailerToFit`, RESIZE_MODE_FIT) resizes the SurfaceView when the video size arrives. The predicate catches the resize, and AspectRatioFrameLayout's own `requestLayout` schedules a traversal anyway. qa marked the fitted case NOT CHECKED (AC-5 M5) because it found no fitted trailer. Proving it would take a device run on a screen that passes `scaleTrailerToFit = true`.
- `TrailerPreviewPlayer.kt:102` the url changes in place from one non-null value to another. The PlayerView and its SurfaceView are kept at the same size, so the listener does not fire. The existing surface carries over and `trailerRendered` is reset through `remember(trailerUrl)`. It would only matter if evidence showed the surface being destroyed across `stop()`/`clearMediaItems()` while the size stays the same.

Notes on the boundaries in the brief:
- **View reuse and release:** without `onReset` the AndroidView is never reused, and `onRelease` removes the listener. The listener is a single stateless object that works only on its `v` parameter, so sharing it across instances is safe.
- **PlayerView swapping its surface:** PlayerView creates `videoSurfaceView` once, in its constructor, and never swaps it.
- **Repeated size changes:** each change posts one `invalidate()`, which does not change the size again, so it cannot loop.
- **Details hero vs catalogue:** both go through the single call site, `VideoItemGridDetails.kt:483`. qa found that favourites passes no `trailerUrl`, so AC-4's favourites premise does not hold.
