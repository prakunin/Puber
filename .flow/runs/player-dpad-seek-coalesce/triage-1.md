# Review triage - round 1

Run: player-dpad-seek-coalesce   Base: 8c3117ea   Reviewed head: 50809306

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial), review-state-path-1.md, review-antigravity-1.md (skipped: quota reached, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | codex; blind, edge (unproven) | minor | `PlayerVM.kt:1426-1455` (`onPlaybackEnded`) | A pending target is ignored when playback ends: a forward seek lands at the end, the viewer presses Left inside the 250 ms window, ENDED fires and a film exits before the trailing seek runs. On base every press seeked, so the rewind worked | Read `onPlaybackEnded`: it publishes `playbackController.currentPosition` and calls `exitPlayer()` (which drops the target) with no look at `pendingSeekTarget` | fix (cheap) | When a target is pending, commit it and skip the end handling (the seek moves playback away from the end); add a unit test for a film ending with a pending backward target |
| 2 | tests F1 | minor | `PlayerVM.kt:479`, `:532` | The two failover commit sites (`switchToNextStreamCandidate`, `refreshStreamLinksOnce`) have no test | Only `aQualitySwitchCommitsThePendingTargetFirst` covers a commit site | fix (cheap) | Add a test that raises a playback error mid-hold and asserts the pending target is sought before `switchStream`; the refresh path too if the fixture can drive it |
| 3 | tests F2 | minor | qa-report AC-7 row | Manual 3 (prompt on a series intro) never ran on the box | qa round 1 used a film | fix | qa round 2 on a series episode with an intro (running) |
| 4 | codex (rejected by its reviewer) | - | evidence AC-8 | Device bursts were eight discrete keyevents, not a 5 s hold | Evidence file states the method | reject | The test design defines bursts as the device stand-in for a held key; per-window counts are covered by unit tests (unproven) |

## Rejected codex claims

- Next-episode prompt can start during a seek burst via `onPlaybackEnded` - not a regression: a clamped seek ends playback the same way on base.
- AC-8 should use a real 5 s hold - evidence gap, not a defect; covered by unit tests.

## Next

- Fix round: implementer fixes #1 #2 (production change in `onPlaybackEnded` + tests); qa round 2 covers #3 and then re-checks the affected ACs on the box (end of a film with a pending target is not reachable at human pace, so unit only); review edge + codex + tests + state-path on the incremental diff; unproven lines: 7
