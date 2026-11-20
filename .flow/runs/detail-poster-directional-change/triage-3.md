# Review triage - round 3

Run: detail-poster-directional-change   Base: 01d1d305   Reviewed head: b27800f6 (incremental from 4d9725b9)

Sources: review-edge-3.md, review-tests-3.md, review-codex-3.md (ran: review, adversarial; clean), qa-report.md (round 3: no defects).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, tests F1 | minor (treated as the unfinished part of triage-2 #1) | `VideoItemGridDetails.kt:920-926, :940-943` | On a warm-cache settle the alpha rest is cancelled before it moves, so the seeded outgoing layer stays partly transparent (and with sign 0 stays shifted) for the rest of the motion; same ghosted strip triage-2 #1 meant to remove, short and faint | two lenses agree; read: cancel on ready, motion drives offset only | fix | the fix of triage-2 #1 was incomplete; caps lifted by the owner; cheap: keep the alpha rest running alongside the motion, sign 0 targets 0F; add a test that settles before `run` and asserts alpha reaches 1 before the motion ends |
| 2 | tests F2 | minor | `VideoItemGridDetails.kt:1022-1027` | Seeding `when` (which predecessor values are taken) untested | read | fix | cheap with #1: extract `seedFrom(previous)` and test null / ready mid-motion / waiting |
| 3 | tests F3 | nit | `PosterTransitionTest.kt:57` | Assertion echoes the default | read | fix | drop it or move it into the #1 test |

## Rejected codex claims

- "device evidence predates the fix" - not a defect; qa round 3 re-ran the double-press cases at b27800f6.

## Next

- Round 4: implementer fixes #1-#3; then check.fast, lens edge + codex + tests on the incremental diff, qa on AC-1/2/3 warm double press. Unproven lines: 2.
