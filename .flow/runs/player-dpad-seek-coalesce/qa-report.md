# Round 1

CHECK: check.full -> pass
DEVICE: 192.168.1.110:5555 (owner's TV box; lease released; install-preserve outcome installed, on-device base.apk sha256 matches built APK)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PlayerVMSeekCoalesceTest everyPressInAHoldShowsTheCumulativeTarget, aHoldNearTheEndClampsTheTargetToTheDuration (15/15 green); device: ladder sums exact (+310 s over 8 presses) |
| AC-2 | PASS | aHeldKeySeeksOncePerWindowAndEndsOnTheFinalTarget, aLongHoldKeepsAtLeastOneWindowBetweenSeeks; device: playback landed on indicator target |
| AC-3 | PASS | aSingleBackwardPressSeeksOnceToZero; device: single Right/Left = +10/-10 s |
| AC-4 | PASS | startOver/resume/skipSegment DropsThePendingSeek (unit only) |
| AC-5 | PASS | backgroundSaves, backPressedSaves, aQualitySwitchCommits (unit); device: Back,Back 140 ms after burst, resume offer 1:00:14 = burst end |
| AC-6 | PASS | thePositionTickKeepsThePendingTarget (unit) |
| AC-7 | PASS (unit) / device NOT CHECKED | PlayerVMSeekBurstTest 7/7, PlayerVMSkipCountdownFitTest, SeekHandlerTest 4/4 green; Manual 3 (series intro) not run: only a film was opened |
| AC-8 | PASS | Right/Left/FF bursts, bar focused and not, on one HLS film: thumb and indicator follow, final position = indicator target, no failover, no prompt, no crash; see evidence |
TESTS ADDED: none (every traceability row already has a test)
EVIDENCE: /home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce/docs/superpowers/evidence/2026-11-28-player-dpad-seek-coalesce-device.md
DEFECTS: none
UNAVAILABLE: none
MANUAL: Manual 3 from the test design: on a series episode with an intro segment, play from 10 s before the lead-in, hold Right into the intro; expect no prompt during the hold and a prompt about 1.5 s after the last press that counts down normally.

## Round 2 (Manual 3 only)

CHECK: not re-run this round (no code change; round 1 result stands)
DEVICE: 192.168.1.110:5555 (installed APK sha256 f73a1e44... equals the worktree build; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-7 on device (Manual 3) | NOT CHECKED | The only series on Home (S1E1) has no intro segment: no skip prompt from 0:00 to about 8:00 at 70 s steps. Unit coverage from round 1 stands (`PlayerVMSeekBurstTest`) |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce/docs/superpowers/evidence/2026-11-28-player-dpad-seek-coalesce-device.md (section "Round 2")
DEFECTS: none
UNAVAILABLE: a series episode with an intro segment, reachable at human pace
MANUAL: pick an episode known to show "Skip intro", play from about 10 s before it, send `adb -s 192.168.1.110:5555 shell input keyevent 22 22 22 22 22 22 22 22`; expect no prompt during the hold and a prompt about 1.5 s after the last press that counts down. Note: S1E1 of the title above now resumes from about 8 min, not 51:03.

## Round 3 (commit 4da50fd1: pending seek target committed when playback ends)

CHECK: check.full -> pass
DEVICE: 192.168.1.110:5555 (owner's TV box; installed with install-preserve, outcome installed; on-device base.apk sha256 a0af0bd0... equals the built APK; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| Burst near the end, no key after (case A) | PASS | Indicator 1:44:16 (-1:58), no key: credits played on, then exit to detail screen marked watched |
| Overshoot burst about 54 s before the end (case B, clamp to duration) | PASS | Detail screen within 2 s, marked watched, no stall on the last frame, same after 60 s |
| AC-3 single Right/Left press | PASS | 0:27 -> 0:37 -> 0:27 (clean pair); an earlier read of +21 s was a stale thumb reading before the pause settled |
| AC-1, AC-2, AC-4..AC-8 | unchanged | Round 1 results stand; unit suites re-run green inside check.full |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/player-dpad-seek-coalesce/docs/superpowers/evidence/2026-11-28-player-dpad-seek-coalesce-device.md (section "Round 3")
DEFECTS: none
UNAVAILABLE: none
MANUAL: Manual 3 (series intro segment) from round 1 remains open.
Side effect on the owner's data: Quasimodo is now marked watched; the last exit was at about 0:27 via Back.
