# Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL)
DEVICE: emulator-5554 (puber_tv_36); stale lock reclaimed by the script itself

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | git diff 4db9c0d4 of catalog and wrapper: every To value matches except media3 (unchanged 1.11.0, per gate-1 delta); detekt-compose-rules untouched |
| AC-2 | PASS | `--warning-mode all` base vs new: base 0 deprecations; new 1 (`Configuration.setVisible`, no build script named) |
| AC-3 | PASS | check.full green; no diff in config/detekt or lint xml; no added @Suppress |
| AC-4 | PASS | AuthProbe 2 tests 0 failures; Failure and HistorySerialization tests present; no diff in data/api tests; grep markAsRefreshTokenRequest empty |
| AC-5 | PASS | assembleProdRelease OK, 6.9 MB APK, signer equals dev debug cert; launches on emulator |
| AC-6 | PASS | clean ksp: devDebug and prodRelease list exactly the 5 `_Impl`; app/schemas clean |
| AC-7 | PASS | device |
| AC-8 | PASS | device |
| AC-9 | PASS | device |
| AC-10 | NOT CHECKED | Round 1: Room opens, no Room/SQLite log lines, rows survive restart; saved progress never appears because `watching/marktime` save fails ("Playback progress save failed"), identical on base |

EVIDENCE: docs/superpowers/evidence/2026-11-12-dependency-update-stable-device.md (worktree, commit c62b3ae1)

# Round 2 (AC-10 only)

CHECK: not re-run; no runtime code changed since round 1 (docs, .gitattributes, gradlew.bat)
DEVICE: emulator-5554 (puber_tv_36), lease released

| AC | Status | Evidence |
|----|--------|----------|
| AC-10 | NOT CHECKED | itest `WatchStateDaoTest` 8/8 pass, `CachedPayloadDaoTest` 6/6 pass (upsert, sync via `@Transaction` upsertAllFromServer, prune, delete, deleteByPrefix, clear). `withWriteTransaction` (`DatabaseTransaction.kt:22`) is not called by either suite, so the AC's named path is only partly covered. Round-1 device findings unchanged |

TESTS ADDED: none
EVIDENCE: docs/superpowers/evidence/2026-11-12-dependency-update-stable-device.md (worktree, section "Round 2")
DEFECTS: none
MANUAL: owner accepts AC-10 as partial, or asks for a test running `DatabaseTransaction` over a real Room database
