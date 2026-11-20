CODEX: ran: review (adversarial not requested for this round)
CONFIRMED:
- None.
REJECTED:
- Codex raised no findings. Its summary was "No actionable regressions", and it did not run tests or device checks.
- My own check of the 484e5adf changes in `/home/user1/orca/workspaces/Puber/detail-poster-directional-change/app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt` found nothing either:
  - **Alpha rest:** it now runs in the outer `coroutineScope`, independent of the offset rest and the hold timeout. If the hold times out, the scope still waits for the alpha rest, which takes at most `POSTER_FADE_IN_MS`. `finally` cleanup is delayed by that much and nothing breaks.
  - **`sign == 0`:** the target `-sign * shiftPx` is 0, which matches the new test `run_bringsTheOutgoingOffsetToZero_whenSettledWithoutDirection`.
  - **`seedFrom`:** moving it out of `rememberPosterTransition` kept its logic the same, and the new tests cover all three branches.
VERDICT: clean
