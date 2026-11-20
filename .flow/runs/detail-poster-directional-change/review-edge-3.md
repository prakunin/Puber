LENS: edge  ROUND: 3  BASE: 01d1d305

### F1 [minor] If the new poster is already cached, the rest is cancelled before it starts, so the outgoing layer keeps its partial seed alpha and offset for the whole motion
- Where: /home/user1/orca/workspaces/Puber/detail-poster-directional-change/app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:923-924 (`resting.cancel()` as soon as `ready`), :940-943 (only the offset is driven after that, and with `sign == 0` it is held at its current value)
- Problem: The fix covers a predecessor that is still waiting for its picture. It does not cover a new record that settles right away. When `ready` is already true, or flips within a frame, `snapshotFlow { ready }.first { it }` returns at once. `resting.cancel()` then runs before `restOutgoing` has moved anything. `outgoingAlpha` stays at the seed value, which is below 1 if the predecessor was in its 150 ms fade-in. Nothing in the main motion animates `outgoingAlpha`, so it keeps that value until `finally`. The predecessor's layer, which hid this, ends `250 - t0` ms after B starts. From then until B's motion ends, B's see-through outgoing layer is the only thing covering the edge strip that B's still leaves bare while it is shifted. With `sign == 0`, the outgoing layer also stays shifted at the seed offset, because the target is `outgoingOffset.value`.
- Failure scenario: The user presses Right, then Left within about 100 ms. The poster that comes back is a memory-cache hit. A (the Right card) is ready and mid-motion at t0 ≈ 100 ms, with stillAlpha ≈ 0.85 and offset ≈ 11 px. B is created and settles in the same frame, so `resting` is cancelled. A ends about 150 ms later. B's still is at about +7 px for roughly the next 100 ms. That strip is covered only by B's outgoing layer at alpha 0.85, so the panel shows through faintly. The effect is short and small. Round 2 F1 was a long, large seam; this is a short, faint one on the warm-cache path. `run_bringsASeededOutgoingLayerToRest_whileWaitingForThePicture` covers only the case that never settles, so no test fails here.
- Fix: Do not cancel the alpha rest when `ready` flips. Let `outgoingAlpha.animateTo(1F, …)` keep running alongside the main motion, for example by launching it in `run`'s outer scope or by also launching the alpha rest inside the motion's `coroutineScope`. Only the offset needs to be handed over to the main motion. For `sign == 0`, use target `0F` instead of the current value. Add a test that settles the record before `run` and checks that `outgoingAlpha` reaches 1 before the motion ends.

### Checks
- **Triage-2 item 1 (review-edge-2 F1) is fixed when the new picture is still loading.** B's layer goes from the seed alpha to 1 over `150·(1−a)` ms and from the seed offset to 0 over `250·|off|/48` ms. This finishes before the 3 s hold. The new test fails on 4d9725b9, where the alpha was constant.
- **The implementer's residual risk (the rest outlasting the predecessor) is small.** I worked it through the FastOutSlowIn and LinearOutSlowIn curves:
  - Early interrupt, t0 ≈ 20 to 60 ms: the rest takes about 208 to 245 ms against 190 to 230 ms of predecessor motion left. When the predecessor ends, B's layer is less than 1 px from 0.
  - Interrupt at t0 ≥ 100 ms: the rest is shorter than the predecessor's remaining motion.
  - Alpha: the rest fade ends before the predecessor's fade-in would have.
  - Result: at most a sub-pixel seam for about 15 ms. No finding.
- **No regressions:**
  - Never drawn over a rendered trailer: `OutgoingPoster` still returns on `!visible` (:956), and the trailer gate (:637) is unchanged.
  - No skeleton flash: the failed-last-URL branch and `shownImage` handling are unchanged.
  - `outgoingImage` cleared: `finally` (:946-948) still clears it on settle, timeout and cancel.
  - The `resting` child is cancelled with the timeout's `coroutineScope`, so it does not leak.
  - A seed of alpha 1 and offset 0 gives zero-length tweens, which is harmless.
- **The `withFrameClock` change in the hold-expiry test is required, not a weakening.** `restOutgoing` now animates, so it needs a frame clock.

### Unproven
- VideoItemGridDetails.kt:1039 A chain of several non-ready records (C replaces B, which replaces A, all within the rest window) restarts the rest curve each time from zero velocity. That makes a visible stutter only if a device frame capture shows it.
