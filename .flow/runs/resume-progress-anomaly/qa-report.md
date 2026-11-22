# QA report

## Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks up to date from the implementer's green run on the same HEAD 37c5c7af)
DEVICE: 192.168.1.110:5555 (owner's TV box), com.kino.puber.stage, clean HEAD APK sha256 250f9ea9..., verified on device before and after; left installed. Lease released.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | KinoPubApiClientFailureTest, red on base per implementation record, green in check.full |
| AC-2 | PASS | same suite, empty and extra-field bodies |
| AC-3 | PASS | 500 test (sent once) and ApiRetryPolicyTest green |
| AC-4 | PASS | PlayerVMPlaybackFlowTest green in check.full |
| AC-5 | FAIL (partial) | Movie and episode: 0 failure lines, marktime 200 on every save (5, 2). The Details progress line still read "Not started" / "Next - episode N" after exit, even on a fresh Details open |
| AC-6 | PASS | Movie dialog "Continue from 28:23?", episode "Continue from 10:27?" |
| AC-7 | FAIL (partial) | From Home: 0 failure lines, 3 saves OK, resume dialog moved 7:46 to 11:49; Details progress line not showing progress |
| AC-8 | PASS | check.full green |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/resume-progress-anomaly/docs/superpowers/evidence/2026-11-22-resume-progress-anomaly-verify-device.md
DEFECTS:
1. Details progress line does not reflect the saved position (movie and series, box). Repro: play a movie past 5 min for a save, BACK; Details (and a fresh Details open) says "Not started - 1 h 55 min" while Play offers "Continue from 28:23?". Series shows "Next - season 1, episode N" with no time left. Both read the same item; resume uses videos[].watching.time (PlayerInteractor.kt:101/125), the line uses item.watching first for movies (DetailsScreenUIMapper.kt buildMovieResumeLine) and needs time>0 and duration>0. Cause not observed (no bodies captured). It also fails for movies, so it is not episode-only; per the brief it needs a decision: new run, or in scope of AC-5/AC-7. The decode fix itself works on the box.
UNAVAILABLE: none
MANUAL: none


## Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks up to date on HEAD c185d417; DetailsScreenUIMapperTest 25 tests, 0 failures, includes both Delta 2 tests)
DEVICE: 192.168.1.110:5555 (owner's TV box), com.kino.puber.stage, clean HEAD APK sha256 32e29a83..., verified on device after install and at the end; left installed. Lease released. auth-save taken first.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | KinoPubApiClientFailureTest green in check.full |
| AC-2 | PASS | same suite |
| AC-3 | PASS | 500 test and ApiRetryPolicyTest green |
| AC-4 | PASS | PlayerVMPlaybackFlowTest green |
| AC-5 | PASS | Movie: 0 failure lines, 4 marktime 200, Details "Stopped - 1 h 54 min left" (was "Not started"). Episode: 0 failures, 3 x 200, Details "Stopped at season 1, episode 1 - 26 min left", moved with the saved position (20 to 26 min). Episode bar on screen: not checked (white frame on the box), covered by the green mapper unit test |
| AC-6 | PASS | Movie dialog "Continue from 8:58?"; episode "Continue from 4:38?" |
| AC-7 | PASS | From Home: 0 failures, 3 x 200, Details "Stopped at season 1, episode 2 - 11 min left", dialog moved 4:36 to 12:48 |
| AC-8 | PASS | check.full green |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/resume-progress-anomaly/docs/superpowers/evidence/2026-11-22-resume-progress-anomaly-verify-device.md (Round 2 section), commit f2e0f25f
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
Notes: in the episode step the dialog was answered with Restart (position afterwards 4:38), not Continue; the Details line followed the position both ways. Odd Taxi in Home step is the same title as round 1 but its position differed (different episode), so it is not a fresh title.
