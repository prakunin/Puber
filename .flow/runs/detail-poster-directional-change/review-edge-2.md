LENS: edge  ROUND: 2  BASE: 01d1d305

### F1 [major] A card change that interrupts a running motion inherits a see-through outgoing layer, so the bare edge strip and a dimmed panel come back
- Where: /home/user1/orca/workspaces/Puber/detail-poster-directional-change/app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:1001-1006 (handover values), :887 (`outgoingAlpha` fixed per record), :1018-1030 together with :925-927 (the interrupted layer ends on its own clock)
- Problem: Triage item 1 is fixed only for a change that starts from rest. When the previous record A is `ready` and still moving, the new record B starts its outgoing layer from A's still: `previous.stillOffset.value to previous.stillAlpha.value`. `outgoingAlpha` is now a constant, so B's outgoing layer keeps that partial alpha (below 1 during A's 150 ms `POSTER_FADE_IN_MS` fade-in) for B's whole life, up to `POSTER_HOLD_MS`. It also starts at A's partial offset (up to 48 px toward A's side). While A's layer runs it sits underneath and hides both problems. A's job clears its `outgoingImage` when A's own 250 ms motion ends, not when B's ends. From that point:
  - B's outgoing layer is the only thing under B's still, which is at alpha 0 until B's poster loads, so the panel shows through it.
  - B's outgoing layer is shifted, so a strip of up to 48 dp is bare on A's side.
- Failure scenario: In the catalogue (full-bleed), the user presses Right twice quickly or holds the key. A's poster loads and A starts its 250 ms motion. About 50 ms later B's details arrive, which changes `heldUrls` and creates B with outgoing alpha of about 0.6 and offset of about +30 px. A finishes at 250 ms. B's poster is still on the network, so for up to 3 s the panel shows A's poster at about 60 % opacity with a bare strip of about 30 px at the left edge. That is the same seam item 1 was meant to remove, and it contradicts the README row "stays 1 under the incoming still". No test covers the handover, because `PosterTransitionTest` only builds records with `outgoingAlpha = 1F` and `outgoingOffsetFrom = 0F`.
- Fix: Keep a ready predecessor's layer drawn until the record that replaced it ends its motion, not only its own. For example, remove interrupted records from `active` and clear their `outgoingImage` in the newer record's `finally`. Alternatively, make B's outgoing alpha an `Animatable` that goes from `previous.stillAlpha` to 1 over the rest of the fade-in. Add a unit test that builds a record from a ready, mid-motion predecessor and checks that nothing see-through or shifted is left alone after the predecessor ends.

Checks on the triage items from round 1:
- **Item 2 is fixed:** when the last URL fails, `onError` calls `settle()` and sets `shownImage = null`.
- **Item 4 is fixed for interrupted ready records**, apart from F1.
- **Item 5 is fixed:** `outgoingImage` is nullable and cleared in `finally`, which also runs on cancel.
- **Trailer:** every layer, including the finishing ones, is still hidden by `visible = !(hasTrailer && trailerRendered)`. I found no path that draws an outgoing layer over a rendered trailer.
- **No skeleton flash:** after a failed last URL, the next change has no record and uses Coil's crossfade. `SkeletonAsyncImage` keeps `hasShownImage`, so no skeleton is drawn.

### Unproven
- VideoItemGridDetails.kt:1018 If a not-ready predecessor settles after composition but before `LaunchedEffect(record)` runs, it is not cancelled. It then draws image X twice at nearly the same offsets, both opaque. This only matters if the two layers' offsets visibly diverge, which needs a frame capture of that window.
