# Review triage - round 2

Run: detail-poster-trailer-flicker   Base: 76a8bc2a   Reviewed head: 9ede97aa (incremental from bd2cf869)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review), qa-report.md Round 2.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | minor | `VideoDetailsPosterStateTest.kt` (`posterStill_staysOptedIntoSkeletonOnlyUntilFirstImage`), `VideoItemGridDetails.kt:612` | The opt-in test only pins the constant's value; deleting the argument from `PosterStill` stays green | Read the test and `PosterStill` | accept | accepted: guarding the wiring needs a Compose UI harness (Q4: none in this run); the trailer ACs go to gate 2 as NOT CHECKED manual checks |
| 2 | tests F2 | minor | `VideoItemGridDetails.kt:694-707` | The `LaunchedEffect` that expires the 3 s hold is untested; only the pure helper is | Read `rememberHeldUrls` | accept | accepted: needs a Compose runtime; listed as a manual step (hung details request releases the old poster after about 3 s) |

## Rejected codex claims

- None (codex: no findings).

## Next

- No fixes; go to judge. Unproven lines: 2 (tests 1, edge 1: a sustained scroll that never lets details land drops to the skeleton after 3 s, no worse than base).
