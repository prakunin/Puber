CODEX: ran: review (round 2, incremental `bd2cf869..9ede97aa`, model gpt-6-astra). The adversarial pass was not requested for this round.

CONFIRMED:
- None.

REJECTED:
- None. Codex reported no findings: "No actionable regressions were identified in the diff".

I read the incremental diff myself and found no blocker or major. These points are observations, not defects:
- `rememberHeldUrls` keeps `holdExpired` set across back-to-back `Loading` states, because `holding` never goes false between them. A second card focused while the first is still loading therefore gets no hold, and its poster falls to the skeleton at once. That is a safe direction and only shortens the hold. The 3 s bound still applies, and the "request that never answers" case from round-1 row 1 is covered.
- `LaunchedEffect(holding)` and `remember(holding)` are keyed together. Leaving `Loading` cancels the delay and resets the flag, so the timer cannot leak.
- `posterRequest` is a pure extraction of the old `remember` body, with the same builder calls in the same order. Behavior is unchanged.
- The new tests (`posterRequest_*`, `posterFadeTarget_*`, the `NONE` transition class check) cover round-1 rows 2 to 4. I did not run them. `posterRequest_*` builds an `ImageRequest` from `mockk<Context>(relaxed = true)`, so the first `check.full` run is the real test of whether that works off-device.
- `posterStill_staysOptedIntoSkeletonOnlyUntilFirstImage` only asserts a constant. It guards the flag's value, not the wiring, which matches the partial fix planned in row 6.

VERDICT: clean
