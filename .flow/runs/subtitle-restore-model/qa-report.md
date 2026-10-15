# Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (AGENT_GRADLE_SERIAL=1). Tests re-run with `--rerun`: 1632 tests, 0 failed, 0 skipped; no flaky suite failed. detekt and lint green.
DEVICE: emulator-5554 (leased, released). Worktree APK sha256 dda60c6f...c666f2, install-preserve outcome installed / compatible_replace, on-device base.apk sha256 matches.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | T-1a, T-1b, T-1c, T-1d, T-1e all present and green; device: English (manifest-only) picked on Outlander, same row on re-entry |
| AC-2 | PASS | T-2a, T-2b, T-2c, T-1e variant green (unit only, no device title) |
| AC-3 | PASS | T-3a, T-3b, T-3c green; device: explicit Off stays Off on re-entry |
| AC-4 | PASS | existing resolver tests unchanged and green, T-4a, T-4b, T-4c green; device: old Russian pref restored after install |
| AC-5 | PASS | T-5a, T-5b green (unit only) |
| AC-6 | PASS | T-6a..T-6d green, T-6e green (unit only) |
| AC-7 | PASS | T-7a, T-7b green |
| AC-8 | PASS | T-8a, T-8b, T-8c green, rebuildFastDns/BufferPreset tests green |
| AC-9 | PASS | T-9 green |
| AC-10 | PASS | T-10a, T-10b, T-10c green |
| AC-11 persistence and clause (b) | PASS | M1, M2, 4b, 4c and a stricter exit-from-720p re-entry: see evidence file |
| AC-11 clause (a) with a manifest-only row | NOT CHECKED | every manual quality of Outlander (1080p/720p/480p) plays url.hls without the row, so no stream other than Auto offers a manifest-only row. Closest observation on Psyren (Russian stays on after Auto -> 720p, cues render) is not a manifest-only row and is not counted |
| AC-12 | PASS | check.full green, see CHECK |
| AC-16 | PASS | T-16a, T-16b, T-16c green (unit only) |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/subtitle-restore-model/docs/superpowers/evidence/2026-10-15-subtitle-restore-model-device.md (commit a99cdff9; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none required. Optional: AC-11 clause (a) for a manifest-only row needs a title where a manual quality still plays a manifest with subtitle rows; none is known.

# Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (AGENT_GRADLE_SERIAL=1, HEAD 630e2074; test task was up to date from the same sources). 1635 tests, 0 failed, 0 skipped; HomeVMIncrementalPublishTest / HistoryVMDeletionSchedulingTest did not fail. detekt and lint green.
DEVICE: emulator-5554 (leased, released). Worktree APK sha256 d36991cc...17decd, install-preserve outcome installed / compatible_replace / destructive_action false, on-device base.apk sha256 matches. No crash-buffer entries.
ACS (affected after fix round 1; the others keep their round 1 status):
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | device: English picked, exit, re-enter on Outlander S1E5 -> English; round 1 unit tests rerun green (1635 total) |
| AC-3 | PASS | device: explicit Off, exit, re-enter -> Off |
| AC-11 persistence / clause (b) | PASS | device: 720p -> Off shown (Audio Track 1), Auto -> English back (Audio 01), exit/re-enter -> English |
| Preference saved only after an explicit pick | PASS | device: audio 01 -> 02 without touching subtitles, exit, re-enter -> Audio 02, Subtitles English (stored preference not overwritten) |
| Label chooses among same-language rows, never filters | NOT CHECKED on device | Outlander has one row per language; covered by unit tests only |
| AC-11 clause (a) with a manifest-only row | NOT CHECKED | unchanged from round 1: no known title |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/subtitle-restore-model/docs/superpowers/evidence/2026-10-15-subtitle-restore-model-device.md (Round 2 section, commit 8606de32; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none required. Notes: (a) the first S1E5 open of this round showed Subtitles Off before any pick, although round 1 ended with Russian restored; not reproduced or explained, recorded in the evidence. (b) Account state at the end: Outlander subtitle Russian, audio 01, quality Auto. Watch progress of Outlander S1 moved because the player auto-advanced to S1E6/S1E10 during navigation (S1E5 resume point and Continue-watching row changed).
