Round 1

CHECK: check.full from .flow/config.yaml in the worktree -> pass (exit 0; BUILD SUCCESSFUL, profile tooling tests all PASS; most Gradle tasks were UP-TO-DATE from the implementer's run at the same HEAD 006b2a9d, test result XMLs checked: PlayerInteractorTest 64/0 failures, VideoCapParametersTest 3, PlayerAudioTracksTest 2, PlayerVMTrackSelectionTest 46, all 0 failures)
DEVICE: 192.168.1.110:5555 (dev APK sha256 121882f5..., verified on device) for M1-M6; instrumented tests on emulator host.docker.internal:5555 (owner decision)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | PlayerInteractorTest (manual order hls4/hls/http with cap, Auto unchanged, surround, no-width, no-height, out-of-range rows), 64 tests green |
| AC-2 | PASS | VideoCapParametersTest (3, JVM) green; instrumented hlsVideoCap_switchStreamReplacesCap green, re-run at HEAD on the emulator (11/11). Device: Auto after a manual pick returned to 1920x800 |
| AC-3 | PASS | instrumented hlsVideoCap_belowEveryVariant_stillSelectsVideo green (re-run at HEAD, 11/11) |
| AC-4 | PASS | Box: Audio row "01. Дубляж (RUS)" and 4 named tracks after Auto -> 720p and after 720p -> 480p (master baseline: "Track 1", one row). Shots 3,4,6,7 |
| AC-5 | PASS | Stream row: Auto 1920x800, 720p 1280x534, 480p 720x300 (distinct, at most the pick; 720p equals the master 720p size, so no R4 exclusion) |
| AC-6 | PASS with caveat | Subtitles list with 11 rows on 720p, pick "Русский · вариант 1", cues rendered, row kept over 480p/Auto/720p. The UI does not show whether a row comes from the hls4 manifest or the API, so manifest origin is not proven |
| AC-7 | PASS | No native-size corner draw or frozen frame after any switch (shots 3-8) |
| AC-8 | PASS | PlayerAudioTracksTest (2) green |
| AC-9 | PASS (unit) | PlayerVMTrackSelectionTest green. Device M6: after Auto -> UKR -> 720p -> leave -> restart, row "02. Дубляж (UKR)". The fallback-row path did not occur on hls4, so the guard is covered by unit only |
| AC-10 | PASS | check.full exit 0 (see CHECK) |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/manual-quality-hls4/docs/superpowers/evidence/2026-11-27-manual-quality-hls4-device.md (shots in docs/superpowers/evidence/manual-quality-hls4-shots/; audit passed, 413 files). Not committed, per brief.
DEFECTS: none
UNAVAILABLE: none
MANUAL: (1) M5 dubbing audibility: the UKR row stayed checked after 720p, but I could not listen; confirm by ear that Ukrainian dubbing plays. (2) R6, whether the hls4 URL changes between 720p and 480p, was not observable without logs and is not recorded.

Device state: box left with the master dev APK (sha256 4354d24d..., logged in, home screen launches); stored audio RUS / subtitles Off restored; .auth/com.kino.puber.stage.tar saved before the install. Box lease and emulator lease released, emulator stopped. Playback of the item created watch progress on the account (unavoidable, as in the baseline).


---

Round 2

CHECK: check.full from .flow/config.yaml in the worktree at 07aba956 -> pass (exit 0; BUILD SUCCESSFUL, profile tooling tests all PASS)
DEVICE: 192.168.1.110:5555 (worktree dev APK sha256 d5dd1d5e..., verified on device); instrumented tests on emulator host.docker.internal:5555 (owner decision), evidence itest-fix1-1..3.xml
ACS (affected only):
| AC | Status | Evidence |
|----|--------|----------|
| AC-2 | PASS | VideoCapParametersTest (incl. new cap_clearsTheDisplayViewportConstraint) in check.full; instrumented hlsVideoCap_switchStreamReplacesCap now also asserts the viewport is cleared under a cap and back to the default on Auto, itest-fix1-3.xml 12 tests, 0 failures (run at 13:21:30, after the last source edit at 13:20:14). Box: Auto after 720p returned to 1920x800 |
| AC-3 | PASS | hlsVideoCap_belowEveryVariant_stillSelectsVideo green in itest-fix1-3.xml, now also asserts the 320x180 cap was applied |
| AC-5 | PASS | Box, same movie: Auto 1920x800 -> 1080p (highest offered) 1920x800 -> 720p 1280x534 -> Auto 1920x800. The 1080p Stream row equals the Auto size, not lower, not audio-only; 720p differs and is below it. No corner draw, picture filled the frame |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/manual-quality-hls4/docs/superpowers/evidence/2026-11-27-manual-quality-hls4-device.md (Round 2 section; shots r2-1..r2-5 in manual-quality-hls4-shots/; audit passed, 418 files). Not committed, per brief.
DEFECTS: none
UNAVAILABLE: none
MANUAL: (1) The 1080p pick cannot be told apart from Auto by the Stream row (both 1920x800 on this item); the Quality list does not show the pick's nominal API size. (2) Carried from round 1: dubbing audibility and the hls4 URL change between switches were not observable.

Device state: box left with the master dev APK (sha256 4354d24d..., on-device match, home screen loads logged in); .auth/com.kino.puber.stage.tar saved before the install; no audio/subtitle picks made. The 'Квазимодо' watch progress moved to about 15:30 on the account (playback). Lease released.
