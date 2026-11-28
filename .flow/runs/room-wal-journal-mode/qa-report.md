Round 1

CHECK: check.full -> pass (re-run after the test edit)
DEVICE: 192.168.1.110:5555 (lease released; session saved with auth-save before install; installed base.apk sha256 matches the built APK)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | productionBuilderOpensTheFileInWalWithNormalSync and productionBuilderSwitchesARollbackJournalFileToWal pass on the box (see TESTS ADDED) |
| AC-2 | PASS | PuberDatabaseMigrationTest (6), CachedPayloadDaoTest (11), PersistentPayloadStoreDbTest (5), WatchStateDaoTest (11): 33 run, 0 failures |
| AC-3 | PASS | Launch over existing data: no pairing screen, no crash; badge 12 and the progress bar unchanged; card opened and BACK restored focus |
| AC-4 | PASS | puber.db header bytes 18-19 = 2 2 (WAL); ro.config.low_ram empty (not low RAM). Header was already 2 2 before the install |
| AC-5 | PASS | check.full green |
TESTS ADDED: none new. Fixed the setup of the existing productionBuilderSwitchesARollbackJournalFileToWal in app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt: it failed with SQLITE_BUSY on first run because PRAGMA journal_mode = TRUNCATE ran on the MigrationTestHelper connection while Room's pool held WAL connections. It now rewrites the file on a fresh AndroidSQLiteDriver connection and asserts header 1 1 before the production builder opens it. Test defect, not production. Commit 457bb5dd.
EVIDENCE: /home/dev/orca/workspaces/Puber/room-wal-journal-mode/docs/superpowers/evidence/2026-11-28-room-wal-journal-mode-device.md (audit passed, committed)
DEFECTS: none in production. Note: AC-1 cannot fail on this box before the change (AUTOMATIC already gives WAL), as the spec accepts; the box cannot show the low-RAM path.
UNAVAILABLE: none
MANUAL: none
