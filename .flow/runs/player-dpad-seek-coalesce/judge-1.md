# Gate - player-dpad-seek-coalesce - round 1

**Decision:** PASS
**Head:** b3a7c947   **Base:** 8c3117ea

## Done as asked

The diff (`PlayerVM.kt`, `SeekHandler.kt`, tests, spec, plan, evidence) does what spec Design says. It adds a leading-edge 250 ms throttle (`requestSeek`, `seekThrottleJob`, `pendingSeekTarget`). Commits run before all three `switchStream` sites. Drops run in resume, start-over, skip-segment, `resetMediaSession`, `exitPlayer` and `onCleared`. The tick and `saveCurrentPosition` read the pending target, and the ladder clock moved to `seekBurstJob`. No `SeekParameters`, scrubbing mode, Compose or DI change (Q1). One addition beyond the design: `onPlaybackEnded` commits a pending target and returns (triage-1 #1, commit 4da50fd1).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `everyPressInAHoldShowsTheCumulativeTarget`, `aHoldNearTheEndClampsTheTargetToTheDuration` | Test body read (asserts state, indicator, leading seek); check.full green at b3a7c947 | PASS |
| AC-2 | `aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget`, `aLongHoldKeepsAtLeastOneWindowBetweenSeeks` | Test body read; check.full green | PASS |
| AC-3 | `aSingleBackwardPressSeeksOnceToZero`; device single press | check.full green; evidence Round 3 case C | PASS |
| AC-4 | `startOverDropsThePendingSeek`, `resumeDropsThePendingSeek`, `skipSegmentDropsThePendingSeek` | Test bodies read (`assertNoSeekAfter`); check.full green | PASS |
| AC-5 | `backgroundSavesThePendingTarget`, `backPressedSavesThePendingTarget`, `aQualitySwitchCommitsThePendingTargetFirst`, both failover tests; Manual 4 | check.full green; evidence AC-8 table, last row (resume 1:00:14) | PASS |
| AC-6 | `thePositionTickKeepsThePendingTarget` | Test body read; check.full green | PASS |
| AC-7 | `PlayerVMSeekBurstTest` (7 cases incl. `aHeldKeyKeepsThePromptDownAndTheTrailingSeekStartsNoCountdown`), `PlayerVMSkipCountdownFitTest` | check.full green; device Manual 3 NOT CHECKED (no intro segment reachable, qa-report Round 2) | PASS (unit; device part in Manual checks) |
| AC-8 | Manual 1, 2 (8-press bursts, Right, Left, FF, bar focused) | evidence "AC-8" table; Round 3 end-of-film cases | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Pending target lost when playback ends | review-codex-1, triage-1 #1 | fixed | 4da50fd1; `aFilmEndingWithAPendingTargetSeeksThereInsteadOfExiting`; evidence Round 3 A/B |
| Failover commit sites untested | review-tests-1 F1, triage-1 #2 | fixed | 4da50fd1; `aStreamFailureCommitsThePendingTargetBeforeTheNextCandidate`, `aLinkRefreshCommitsThePendingTargetBeforeTheNewStream` |
| Manual 3 not run on device | review-tests-1 F2, review-tests-2 F1, triage-1 #3, triage-2 #2 | accepted minor | Two qa attempts; no episode with an intro reachable at human pace; unit coverage in place; carried to Manual checks |
| Flush in `onPlaybackEnded` does not restart the window (one extra seek) | review-codex-2, triage-2 #1 | accepted minor | Confirmed at `PlayerVM.kt:1430`: `commitPendingSeek` leaves `seekThrottleJob` running; final position still correct |
| AC-8 used bursts, not a 5 s hold | review-codex-1 (rejected) | rejected, accepted | Test design Manual checks defines the burst as the device stand-in (readiness-2 READY); window counts are unit-tested |
| Clamped hold needs a second STATE_ENDED | review-codex-2, edge-2, tests-2 (unproven) | rejected, accepted | Evidence Round 3 case B: detail screen within 2 s, marked watched |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Fewer exact seeks and one `updateContent` per press. The throttle loop allocates nothing per press. No measured number (brief Q3, accepted) |
| Reliability | The throttle job runs in the VM scope and is cancelled in `dropPendingSeek` on teardown, episode switch and retry. `selectBufferPreset` / `toggleFastDns` (`PlayerVM.kt` ~1021, 1033) still read the player position, but the trailing seek still lands (review-state-path-1). No process-death impact: the target is transient |
| Maintainability | Follows the AGENTS.md conventions. `SeekHandler` wall clock removed; its only remaining constant is used at `PlayerVM.kt:735`; no dead code or duplicated helper; commits carry no Co-Authored-By |
| Security / privacy | No secrets in the diff. `tools/mobile-evidence-audit.sh` on the evidence dir: `passed`. Screenshots kept outside the repo |
| TV | No Compose or focus code changed. Device: seek bar kept focus through bursts; no prompt flicker seen |

check.full re-run at b3a7c947 (qa-report predates the last commit by 3 s): exit 0.

## Manual checks for the owner

1. Manual 3 (AC-7 on device), NOT CHECKED. Pick an episode that shows "Skip intro". Play from about 10 s before the intro, then send `adb -s 192.168.1.110:5555 shell input keyevent 22 22 22 22 22 22 22 22` or hold Right on the remote. Expect no prompt during the hold, then a prompt about 1.5 s after the last press, counting down normally, without taking focus from the seek bar.

Side effect of qa on the stage app: Quasimodo is now marked watched; S1E1 of "Nebesnyi aidol" resumes at about 8 min.

## Must fix

none

## Concerns for the owner

none
