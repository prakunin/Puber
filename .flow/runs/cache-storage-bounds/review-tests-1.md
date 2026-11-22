LENS: tests  ROUND: 1  BASE: a22c03bc

Suite state: I ran `check.full` (`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) in the worktree at dfad8b7c and it passed (BUILD SUCCESSFUL, exit 0). Most tasks were already up to date from an earlier run on the same sources. The result files show `CachedPayloadSweeperTest` 8/8, `ContentCacheRepositoryTest` 20/20 and `PersistentPayloadStoreTest` 9/9, with no failures and no skipped tests. I did not run the instrumented tests myself. The committed device evidence records `CachedPayloadDaoTest` 10/10 and `PuberDatabaseMigrationTest` 2/2, run through `make DEVICE=emulator-5554 itest`. The only assertion this diff removes is the planned AC-8 rewrite, and every AC has either a test or a documented device check.

### F1 [minor] The sweeper's cutoff boundary is not pinned, so a +1 ms error in the cutoff passes
- Where: app/src/test/kotlin/com/kino/puber/data/cache/CachedPayloadSweeperTest.kt:57-66 (`sweepRemovesRowsPastHardCeilingAndKeepsNewerOnesIdentical`)
- Problem: The test design says this test fails if the cutoff is not exactly `clock() - HardCeiling`. It only checks rows at `now - ceiling - 1` and `now - ceiling + 1`. The read path keeps a row while `clock() - updatedAt <= HardCeiling` (`data/cache/CachedFeed.kt:560`), so a row at exactly `now - ceiling` must survive. No test checks that row.
- Failure scenario: `CachedPayloadSweeper.sweepOnce` changes to `olderThan = clock() - HardCeiling.inWholeMilliseconds + 1`. The row at exactly `now - ceiling` is deleted, although the read path would still serve it. Both current assertions still pass. The DAO test only covers `<` at the SQL level, not the cutoff the sweeper computes.
- Fix: Add a row at `now - ceiling` to this test and assert it survives with the same payload and `updatedAt`.

### F2 [minor] AC-1 (main player only reads the cache) has no automated test, and the device check ran 7 min instead of the 30 min the AC states
- Where: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:627-666 (`createDataSourceFactory(writeToCache)`); docs/superpowers/evidence/2026-11-21-cache-storage-bounds-device.md, section AC-1
- Problem: Only the manual check M1 covers the `writeToCache` branch. No test builds `PlaybackController`, so nothing would fail if `setCacheWriteDataSinkFactory(null)` were dropped or the two call sites were swapped. The M1 evidence also records a 7 min session against the AC's 30 min, and says "30 min not played".
- Failure scenario: A later edit makes `prepare` call `createDataSourceFactory(writeToCache = true)`. `check.full` and every unit and instrumented test stay green. The main player writes to `media_cache` again, and only a manual device rerun would notice.
- Fix: Either extract the choice of write sink into a small pure function and test both branches, or have the owner accept the 7 min M1 run as AC-1 evidence in writing (spec or gate note). Without that, AC-1 as written is not shown.

### F3 [nit] The test design claims `eachOnStopRunsOneMoreSweep` catches a missing `drop(1)`, but no test can
- Where: app/src/main/java/com/kino/puber/data/cache/CachedPayloadSweeper.kt:51 (`isForeground.drop(1).filter { !it }`); test design row "AC-6 ... eachOnStopRunsOneMoreSweep ... fails if `drop(1)` or the filter is missing"
- Problem: `AppForegroundState` starts as `MutableStateFlow(true)`, and `filter { !it }` already discards that initial `true`. Removing `drop(1)` changes nothing a test can observe, so the claimed safeguard is not real.
- Fix: Correct the claim in the test design, or drop `drop(1)` as redundant.

### F4 [nit] The red-run section of the Implementation record does not cover the instrumented P0 tests, and one commit hash is stale
- Where: docs/superpowers/plans/2026-11-21-cache-storage-bounds.md, "Implementation record"
- Problem: The P0 `red` DAO tests and `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow` are listed as "not run here". They cannot compile on base, because `deleteOlderThan`, `deleteBeyondNewest`, `create(name)` and schema v4 are all new, so they are red by construction. The record just does not say so. Separately, the Coil commit is recorded as `bbf13622`, while the branch has `ae127475`.
- Fix: Add one line stating that these tests are compile-red on a22c03bc, and update the commit hash.

### Unproven
- app/src/test/kotlin/com/kino/puber/data/cache/CachedPayloadSweeperTest.kt:156 `sweepRunsOffTheMainThread` builds `defaultScope()` itself rather than going through the DI `single`. A future change to the DI scope would escape it. It would become real if `data/di/modules.kt` stopped using `CachedPayloadSweeper.defaultScope()`.
