# QA report

## Round 1

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` -> pass (BUILD SUCCESSFUL; unit tests and detekt were UP-TO-DATE from an earlier identical run, lint executed; `TrailerSurfaceRedrawTest` result file: 5 tests, 0 failures, 0 skipped; `VideoDetailsPosterStateTest` and `SkeletonAsyncImageVisibilityTest` results present)
DEVICE: emulator-5554 (leased, released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | D1: catalogue trailers on three card changes each have audio started, a SurfaceView layer, growing frames, and a screencap with video and no poster |
| AC-2 | PASS | D2: the reopened catalogue's trailer has layer, frames and video (evidence file) |
| AC-3 | NOT CHECKED | T1, owner's TV |
| AC-4 | PASS for the details hero, NOT CHECKED for favourites | Hero: series and movie details both show video (D3). Favourites never plays a trailer: `FavoriteScreenContent.kt` passes no `trailerUrl`, so the spec's favourites premise does not hold. TV part is T2 |
| AC-5 | PASS on the emulator, TV NOT CHECKED | D4 M1, M2, M3 pass; M5 NOT CHECKED (no fitted trailer found); T3 is the owner's |
| AC-6 | PASS | `TrailerSurfaceRedrawTest`, 5 tests green; all five rows of the test design are present |
| AC-7 | PASS | check.full above |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/trailer-video-hidden-regression/docs/superpowers/evidence/2026-11-19-trailer-video-hidden-regression-device.md (audit passed; committed)
DEFECTS: none
UNAVAILABLE: none
MANUAL:
- T1: catalogue on the TV, at least three trailers after card changes; expect video with the poster fading out.
- T2: details hero on the TV (favourites plays no trailer, skip it).
- T3: watch trailer start, trailer end and fast card changes. At the trailer end the emulator recording shows one frame with the video layer skewed before the poster returns; the base was not recorded, so check on the TV whether it is visible.
- M5: a fitted (letterboxed) trailer was not found on the emulator; check one on the TV if any exists.

Notes: the D0 install replaced the diagnostic build, with data kept (`compatible_replace`, same signer, on-device sha256 equals the built APK). One `input keyevent HOME` was sent just after the lease release; nothing else ran without the lease. Fade timing came from `screenrecord`, because `screencap` takes about 1.4 s.
