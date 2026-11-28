# Review triage - round 2

Run: player-dpad-seek-coalesce   Base: 50809306 (incremental)   Reviewed head: 4da50fd1

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), review-state-path-2.md, review-antigravity-2.md (skipped: quota reached, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex | minor | `PlayerVM.kt:1430` | The flush in `onPlaybackEnded` does not restart the throttle window, so a press 50 ms later can produce one extra seek inside 250 ms | Read the code: `commitPendingSeek()` leaves `seekThrottleJob` and its deadline as they are | accept | One extra exact seek on a rare path (a held key reaching the end of the media); final position correct. Listed for the owner |
| 2 | tests F1 | minor | evidence Round 2 | Manual 3 still not checked on the box | qa rounds 2 and 3: no series episode with an intro segment reachable from Home at human pace | accept | AC-7 has unit coverage (`PlayerVMSeekBurstTest` incl. `aHeldKeyKeepsThePromptDownAndTheTrailingSeekStartsNoCountdown`); Manual 3 goes to the owner's manual checks |
| - | edge, tests, codex (unproven) | - | `PlayerVM.kt:1430` | A hold clamped to the duration relies on a second STATE_ENDED | qa round 3 on the box: the overshoot burst ended normally (detail screen within 2 s, marked watched) | - | Settled by device evidence |

## Rejected codex claims

- End handling skipped when the target is clamped to the duration - settled on the box (round 3 PASS).
- Complete Manual 3 - process note; carried as a manual check.

## Next

- No fixes, go to judge; unproven lines: 1 (settled)
