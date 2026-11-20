LENS: edge  ROUND: 4  BASE: 01d1d305

No findings.

### Checks
- **Triage-3 item 1 (review-edge-3 F1) is fixed.**
  - `restOutgoingAlpha()` is now launched in `run`'s outer `coroutineScope` (VideoItemGridDetails.kt:922). Only `restOutgoingOffset` is cancelled when `ready` flips (:925-926). On a warm-cache settle, the seeded alpha therefore still reaches 1, in `150·(1−a)` ms. That is always shorter than the 250 ms motion, so the bare strip stays covered for the whole motion.
  - When `ready` is already true at `run`, the `resting` launch is cancelled before it starts, and the motion's `outgoingOffset.animateTo` takes over from the seed offset. That is correct.
  - With `sign == 0`, the target is now `-0 * shiftPx = 0F` (:943). The outgoing layer comes back to 0 under the unshifted still instead of staying at the seed offset.
- **Triage-3 item 2 is fixed.** `seedFrom` (:956-960) is a pure extraction. Its three branches match the old `when` exactly.
- **The alpha rest cannot outlive the record or leak.** It runs in the outer `coroutineScope`, so:
  - `run` returns only after it completes. Its maximum is 150 ms, under both the motion (250 ms) and the hold (3 s), so `outgoingImage` is not cleared later than before.
  - Cancelling `record.job` (a non-ready record replaced in `LaunchedEffect(record)`, :1045-1048) or leaving composition (`rememberCoroutineScope`) cancels it with the record.
  - No extra `Job` is held anywhere.
- **The hold path is unchanged in effect.** On the timeout branch the alpha rest finished long ago. The still snaps in, and `finally` clears `outgoingImage` (:946-948).
- **No regressions:**
  - Never drawn over a rendered trailer: the `OutgoingPoster` gate `!visible` (:966) and the call at :637 are unchanged.
  - No skeleton flash: the `shownImage` handling and the null/empty `heldUrls` branch in `rememberPosterTransition` are unchanged.
  - `outgoingImage` is still cleared on settle, timeout and cancel through `finally`.
  - The layer order (`layers`) and the rule for cancelling non-ready records are untouched.
- **The removed assertion at PosterTransitionTest.kt:57 is not a weakening.** It echoed the default alpha of 1, which triage item 3 asked to drop. The new tests cover settle before `run`, settle during the rest, `sign == 0` offset to 0, and the three `seedFrom` branches.

### Unproven
- VideoItemGridDetails.kt:1031 A chain of several non-ready records in a row restarts the alpha and offset rests from zero velocity each time (carried over from round 3). This would only be real if a device frame capture showed a stutter.
