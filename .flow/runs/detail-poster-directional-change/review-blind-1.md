LENS: blind  ROUND: 1  BASE: 01d1d305

### F1 [major] A failed poster load leaves the previous card's poster on screen for 3 s
- Where: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:619-628` (onSuccess/onError of `PosterStill`), `:737-765` (`PosterTransition.run`)
- Problem: Only `onSuccess` sets `record.ready`. When the last URL of the new card fails, `onError` does nothing to the record. `SkeletonAsyncImage` then hides the still (`isFailed` makes its alpha 0), but `OutgoingPoster` stays fully visible at alpha 1 until the `POSTER_HOLD_MS` timeout in `run`. Before this diff, a failed still left the panel blank right away.
- Failure scenario: the panel shows film A. Focus moves to film B, whose poster URLs all fail (offline, 404, or a single bad URL; the fallback chain in `onError` exists because this happens). For about 3 s the panel shows B's title and plot over A's poster, then the poster disappears.
- Fix: in `onError`, when `urlIndex == heldUrls.lastIndex`, end the hold. Either set `record?.ready = true` so the outgoing layer fades out, or add a `failed` flag that `run` treats as settled.

### F2 [minor] A card change that interrupts a running transition makes the older outgoing layer vanish in one frame
- Where: `VideoItemGridDetails.kt:826-832` (`rememberPosterTransition`, branch `previous.ready`)
- Problem: Say the previous record is ready and still animating. The new record takes over only the previous still: its image, offset and alpha become the new outgoing layer. The previous record's own outgoing layer stops being drawn at once, because `transition.record` now points at the new record, even if that layer was still at, for example, alpha 0.5 and part-way through its shift.
- Failure scenario: in Favourites (cached details, no debounce) the user holds D-pad right. Cards change faster than the 250 ms motion, and on each change a half-faded poster disappears in one frame instead of fading out.
- Fix: keep the interrupted record's outgoing layer drawing until its own animation ends. One way is to hold a short list of active records and draw each `OutgoingPoster` while `outgoingShown` is true. Another is to compose the two layers into the new outgoing start values.

### Unproven
- `PanelDirection.kt:188-192` A race during fast navigation. Details for card B are published after focus has already moved to C but before the VM job is cancelled. `markShown(B)` then sets `shown` to null and C's change gets no direction. This is real only if `updateViewState` for B can land after `onCardFocused(C)` and before `focusedItemJob.cancel()`.
- `VideoItemGridDetails.kt:820-835` `DetailsScreenContent` also calls `VideoDetailsMedia`, now always with `direction = None`. If its `imageUrls`/`imageFallbackUrls` change after the first image is shown, a record replaces Coil's crossfade with a dimming double fade. This is real only if the details screen republishes a changed URL list while an image is on screen.
- `VideoItemGridDetails.kt:820, 592-595` Suppose the held URL list changes while the first URL stays the same and a record already exists, so `crossfade` is false both before and after. The `ImageRequest` is then the same object, `onSuccess` never fires again, and the still stays at alpha 0 behind the outgoing copy until the 3 s timeout. This is real only if two consecutive cards, or a same-card refresh, share their first poster URL.
- `VideoItemGridDetails.kt:619-622` The previous request's success could be delivered after the composition that installed the new record. It would then mark the new record ready with the old image. This is real only if Coil 3 delivers a stale success after `onState` has been rebound and before the request restarts.
