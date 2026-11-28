I ran `check.full` in the worktree and it passed (exit 0; Gradle tasks were mostly up to date, and both tooling scripts passed every case). I did not re-run the instrumented suite. qa-report and the evidence file record 33/33 passing on the box after the test-setup fix in 457bb5dd.

The one survivor is a minor gap in AC-1, which the spec already accepts. Beyond it, AC-2 through AC-5 each have a test or a recorded device check, and the existing migration tests are unchanged (the diff only adds lines). Both new tests read the live mode on Room's writer connection. The rollback-file test checks that the file really is in rollback mode (header `1 1`) before opening it, wraps `close()` in `finally`, and its files are deleted in setUp and tearDown.

```
LENS: tests  ROUND: 1  BASE: 8c3117ea
### F1 [minor] The AC-1 guards cannot fail on any device the run has, and the green run on base the plan asked for was never recorded
- Where: app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt:137-183; docs/superpowers/plans/2026-11-28-room-wal-journal-mode.md (Implementation record, "Red run")
- Problem: The only production change is `.setJournalMode(WRITE_AHEAD_LOGGING)` at PuberDatabase.kt:42-43. On the box (`ro.config.low_ram` empty, header already `2 2` before the install, as the evidence file shows), AUTOMATIC already resolves to WAL with synchronous NORMAL. So `productionBuilderOpensTheFileInWalWithNormalSync` and `productionBuilderSwitchesARollbackJournalFileToWal` pass with or without that line, and no automated test can catch the line being removed. The spec and test-design accept this and mark both tests `guard`, so it is not a major finding. But Task 1's "Done when" asks to record that the tests pass on base, and the Implementation record says "not run here (qa-device runs them)". qa-device ran them only at head. So the claim that they are green on base was never actually checked.
- Failure scenario: A later change drops `setJournalMode` from `PuberDatabase.create` -> both guards stay green on the box and the emulator -> low-RAM devices go back to TRUNCATE and synchronous FULL with no test failing.
- Fix: In the Implementation record, replace the "n/a" with an honest line: the two guards were never run on base, and they cannot tell base from head on any available device, so `PuberDatabase.kt:42-43` is protected only by review. No test change is practical, since `isLowRamDevice` cannot be injected.
```

Files referenced:
- /home/dev/orca/workspaces/Puber/room-wal-journal-mode/app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt
- /home/dev/orca/workspaces/Puber/room-wal-journal-mode/app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt
- /home/dev/orca/workspaces/Puber/room-wal-journal-mode/docs/superpowers/plans/2026-11-28-room-wal-journal-mode.md
- /home/dev/orca/workspaces/Puber/room-wal-journal-mode/docs/superpowers/evidence/2026-11-28-room-wal-journal-mode-device.md
