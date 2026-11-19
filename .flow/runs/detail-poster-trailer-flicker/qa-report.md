# Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; VideoDetailsPosterStateTest 11/11, SkeletonAsyncImageVisibilityTest 3/3, 0 failures; the three VM test files are untouched in the diff)
DEVICE: emulator-5554 (headless, leased and released); worktree APK installed with install-preserve (outcome installed), on-device sha256 matches
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | NOT CHECKED | ExoPlayer starts but first frame never confirmed (no SurfaceView layer, static screencap/screenrecord); unit rows posterStillAlpha_isOpaque_whenTrailerNotRenderedYet and skeletonVisible_* pass |
| AC-2 | NOT CHECKED | fade not reachable on this emulator; unit rows posterFadeTarget_isZero_whenTrailerRendered, posterStillAlpha_followsFade pass |
| AC-3 | NOT CHECKED | same; unit row posterStillAlpha_isOpaque_whenTrailerGoneButFadeStillZero passes |
| AC-4 | PASS (screencap level) | 49 captures on catalogue and 12 on favourites during repeated RIGHT/LEFT/DOWN: no #625B71 frame, no empty panel; unit rows heldPosterUrls_*, posterCrossfade_* pass. Crossfade smoothness is not observable by screencap |
| AC-5 | NOT CHECKED | first-focus skeleton not captured; unit row skeletonVisible_shown_whenOptInButNoImageYet passes |
| AC-6 | NOT CHECKED on device | no broken URL; unit rows heldPosterUrls_returnsCurrent_whenLoadedCardHasNoPosters / ..._whenNewCardArrivesDuringLoading and posterCrossfade_returnsNone_whenResultIsError pass |
| AC-7 | NOT CHECKED | no rendered trailer; unit row posterFadeTarget_isZero_whenTrailerRendered passes |
| AC-8 | PASS for the non-trailer part (favourites burst clean, details hero renders, BACK returns to the grid); trailer part NOT CHECKED | evidence file |
| AC-9 | PASS | code review of diff: skeletonOnlyUntilFirstImage defaults false; skeletonVisible_staysVisibleWhileLoading_whenOptInOff passes; only PosterStill opts in |
| AC-10 | PASS | check.full green; git diff 76a8bc2a --stat on app/src/test/.../ui/feature is empty |
TESTS ADDED: none (the implementer's 14 tests cover the testable ACs)
EVIDENCE: /home/user1/orca/workspaces/Puber/detail-poster-trailer-flicker/docs/superpowers/evidence/2026-11-19-detail-poster-trailer-flicker-device.md (committed bd2cf869, audit passed)
DEFECTS: none found. Note (not a defect): VideoItemGridDetails.kt has two stacked KDoc blocks before PosterCrossfade; the first one ("While the next card's details load...") describes heldPosterUrls, which sits below.
UNAVAILABLE: video-layer observation on the headless emulator. adb screenrecord was tried twice: static poster frames only, and dumpsys SurfaceFlinger showed no SurfaceView layer while ExoPlayer was initialised, so it is inconclusive whether screenrecord captures the video layer or the emulator does not render video.
MANUAL: owner watches a real or windowed emulator: (1) rest on a catalogue card with a trailer: poster stays through buffering, fades in about 300 ms over the video, no skeleton/black frame (AC-1, AC-2); (2) letterboxed trailer after the fade: no poster pixels around the video and no tint (AC-7); (3) trailer end and RIGHT while playing: poster returns in the same frame (AC-3); (4) repeat on details hero and favourites (AC-8); (5) first focus after opening a grid shows the skeleton (AC-5); (6) a card whose first poster URL fails falls back and does not keep the old poster (AC-6).

# Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 0 failures, 0 errors in all unit test results; VideoDetailsPosterStateTest 17/17; no flaky rerun needed)
DEVICE: emulator-5554 (headless, leased and released); worktree APK a12341d2 installed with install-preserve (outcome installed), on-device sha256 matches
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 | PASS (screencap level) | 57 catalogue frames and 36 favourites frames during RIGHT/LEFT/DOWN/UP: max grey fraction 0.0013, no #625B71 frame, no empty panel; unit rows heldPosterUrls_* (incl. holdExpired), posterCrossfade_*, posterRequest wiring pass. Crossfade smoothness and the 3 s hold bound are not observable by screencap |
| AC-5 | NOT CHECKED | first-focus skeleton not isolated; unit row skeletonVisible_shown_whenOptInButNoImageYet and the opt-in constant test pass |
| AC-6 | NOT CHECKED on device | no broken URL; unit rows heldPosterUrls_* (hold released after expiry), posterCrossfade_returnsNone_whenResultIsError pass |
| AC-8 favourites | PASS (non-trailer part) | favourites burst clean (36 frames); trailer part stays NOT CHECKED |
| AC-10 | PASS | check.full green; app/src/test/.../ui/feature untouched since base |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/detail-poster-trailer-flicker/docs/superpowers/evidence/2026-11-19-detail-poster-trailer-flicker-device.md (Round 2 appended, committed a1da31c8, audit passed)
DEFECTS: none
UNAVAILABLE: video layer on the headless emulator (trailer ACs 1, 2, 3, 7 and the trailer part of 8 stay NOT CHECKED; not retried)
MANUAL: unchanged from round 1, plus: make a details request hang or fail on a card and confirm the old poster is released after about 3 s and the panel shows the skeleton.
