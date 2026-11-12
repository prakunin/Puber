# Review triage - round 2

Run: next-episode-end-stall   Base: 4db9c0d4   Reviewed head: a7027fa1 (incremental 3bf1209d..a7027fa1)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1, codex #2 | minor (regression from fix round 1) | `PlayerVM.kt:1422` | A panel opened inside the 1 ms deferral makes `advanceAfterDismissedEnd` return without arming `advanceWhenPanelCloses`, so closing the panel leaves the viewer on the ended frame: the reported stall, by a narrow timing | read `PlayerVM.kt:1416-1424` (plain return on `activePanel != None`), `onPanelClosed` consumes only `advanceWhenPanelCloses`, `checkEarlyNextEpisode` gated on `!countdownDismissed` | fix (review cap reached; owner option "one more round", recommended because both items are regressions from the last fix round; standing instruction) | in the re-check, arm `advanceWhenPanelCloses` (and mark the controls ended) when a panel is open; keep the plain return for a running countdown |
| 2 | codex #1 | minor (regression from fix round 1) | `PlayerVM.kt:1420-1423` | The deferred block has no `closing` check and is not cancelled by `exitPlayer()`, so Back inside the window can release the player and publish Loading on a leaving screen | read `exitPlayer()` (cancels the countdown jobs, sets `closing`); the deferred job is untracked; `switchEpisode`/`playNextEpisode` do not check `closing` | fix (same round) | add `closing` to the re-check (or track and cancel the job in `exitPlayer()`) |

## Rejected codex claims

- none.

## Next

- Fix round 2: implementer (#1, #2) with unit tests for both re-check branches; then lens edge + codex on the incremental diff and lens tests (tests change). Device re-check not repeated: verify cap (2) reached, the deferral path itself passed M2/M4 on the device at a7027fa1, and the change only adds guard conditions inside the 1 ms window that the device cannot time; unit tests carry it. Unproven lines: 3 (seek/restart inside the window; Back during the transition is covered by #2).
