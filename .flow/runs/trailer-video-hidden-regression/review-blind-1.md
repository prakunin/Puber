LENS: blind  ROUND: 1  BASE: 3ec85287

### F1 [minor] The fix wiring has no test, and the tests only cover a helper the listener calls
- Where: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/TrailerPreviewPlayer.kt:136-159`, `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/TrailerSurfaceRedrawTest.kt`
- Problem: The tests call only `surfaceNeedsRedraw`, a four-argument size comparison. Nothing checks that the listener is attached in `factory`, that `post { invalidate() }` runs, or that `onRelease` removes it. Removing the `addOnLayoutChangeListener` line, which is the whole fix, would leave every test green.
- Failure scenario: A later refactor drops or misorders the registration. For example, `videoSurfaceView` could be null when `factory` runs. The trailer goes back to a black rectangle and the suite still passes.
- Fix: Add a Robolectric or instrumented check that a layout change from 0x0 to non-zero size on the surface view triggers an invalidate. Otherwise record a manual device check as the evidence for this fix.

### Unproven
- `TrailerPreviewPlayer.kt:136` `videoSurfaceView?.` does nothing if the surface type is "none" or the view is null. A null view skips the fix silently. Evidence needed: a `PlayerView` configuration that uses a non-surface type.
- `TrailerPreviewPlayer.kt:154` `v.post { v.invalidate() }` assumes invalidating a `SurfaceView` makes the window create its surface. That was not verified from the diff alone. Evidence needed: an on-device run that shows the first frame renders.
