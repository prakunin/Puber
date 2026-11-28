LENS: blind  ROUND: 1  BASE: 8c3117ea
### F1 [minor] Both new WAL tests pass on the old code on any device that is not low-RAM, so on the usual test devices they cannot catch the regression
- Where: app/src/androidTest/kotlin/com/kino/puber/data/db/PuberDatabaseMigrationTest.kt:137 and :160
- Problem: On base, Room uses `JournalMode.AUTOMATIC`. That mode already gives WAL with synchronous NORMAL whenever `ActivityManager.isLowRamDevice()` is false. The only behaviour this diff changes is on low-RAM devices. Neither test forces that case, and neither checks that the device is low-RAM. So both tests only show the change when the instrumentation device happens to be low-RAM.
- Failure scenario: Run `make DEVICE=<emulator> itest` with the `.setJournalMode(WRITE_AHEAD_LOGGING)` line removed, on an emulator or box that is not low-RAM. `productionBuilderOpensTheFileInWalWithNormalSync` and `productionBuilderSwitchesARollbackJournalFileToWal` still pass. Losing the line goes unnoticed.
- Fix: Make the test independent of the device's RAM class. One way is a test that reads the `DatabaseConfiguration.journalMode` the builder produces, or the resolved mode, and expects `WRITE_AHEAD_LOGGING`. The other is to run the tests on a low-RAM profile, or log or assume `isLowRamDevice()` so the run records which path it actually tested.

### F2 [minor] On low-RAM devices, locally pending watch progress is less durable after a power cut, and nothing says so
- Where: app/src/main/java/com/kino/puber/data/db/PuberDatabase.kt:42-43
- Problem: Forcing WAL also makes Room use `synchronous = NORMAL` instead of FULL. Under WAL with NORMAL, the last committed transactions can be rolled back after a power loss. The comment presents the change as only an override of the TRUNCATE + FULL choice. It says nothing about the durability cost to `watch_state` rows with `is_local_pending = 1`. Those rows are local writes the server does not have yet, so "rebuildable from the server" does not cover them.
- Failure scenario: A TV box is low-RAM, which is common, and loses power by being unplugged at the wall. If that happens right after playback progress is saved as a pending local row, the row is rolled back on the next start. Before this change it would have survived under FULL.
- Fix: Accept the trade-off and write it into the comment. Or, if pending rows must survive a power cut, issue `PRAGMA synchronous = FULL` for the writer connection, for example in an `onOpen` callback.

### Unproven
- PuberDatabaseMigrationTest.kt:183 `stream.skip(18)` may skip fewer bytes than asked. That would only matter on a non-regular file stream, and none is used here.
