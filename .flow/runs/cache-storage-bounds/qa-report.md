Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; unit results 228 suites, 0 failures, tasks up to date from the 16:01 run on the same sources)
DEVICE: emulator-5554 (AVD puber_tv_36), leased and released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | 0 new media_cache files and flat du over 7 min of main playback (base: 127 MB); 30 min not played |
| AC-2 | PASS | 7 new files in warm-up; 7 onCachedBytesRead lines at episode 8 start equal those file sizes |
| AC-3 | PASS | 526360 KB before, 261300 KB after first start, 254672 KB after warm-up; log cacheSize 259464846 B |
| AC-4 | PASS | CachedPayloadDaoTest (10/10 on device), CachedPayloadSweeperTest 8/8, PersistentPayloadStoreTest; on device 2900 -> 457 rows, 0 older than 7 days |
| AC-5 | PASS (cap on test only) | deleteBeyondNewest* DAO tests, sweepCapsRowsToTheNewest, listWhoseItemRowsWereSweptRefetchesThem; the real table never exceeded the cap, so the cap step was not seen on device |
| AC-6 | PASS | CachedPayloadSweeperTest: sweepRunsOnceAfterTheStartupDelay, eachOnStopRunsOneMoreSweep, sweepRunsOffTheMainThread, startTwiceRunsOneCollector; device: sweep ran after delay |
| AC-7 | PASS | PuberDatabaseMigrationTest 2/2; device: v3 -> v4 with install-preserve, 6 seeded legacy rows gone, 2900 content rows kept, watch_state/watch_state_sync identical |
| AC-8 | PASS | ContentCacheRepositoryTest.firstUseLeavesLegacyRowsToTheMigration (20/20) |
| AC-9 | PASS | debug log: Coil memory cache maxSize=50331648 |
| AC-10 | PASS | CachedFeedTest 42, ContentCacheRepositoryTest 20, PlayerVMNextEpisodePrefetchTest 3, CachedPayloadDaoTest 10 green; check.full green |
| AC-11 | PASS (recorded) | numbers table in the evidence; PSS after Home scroll 301 MB vs 276 MB base, single samples, not a verdict |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-storage-bounds/docs/superpowers/evidence/2026-11-21-cache-storage-bounds-device.md (committed in wt; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: owner judges M6 (poster reloads on Home scroll with the 48 MB cap; none seen in screenshots) and the PSS difference. Notes: the account mutation was episode 7/8 playback (resume position, watch_state 885 -> 886). The DB had six legacy fixture rows added by me via run-as (backup in scratchpad) because the device had none. The row cap (2000) was not reached by real data (457 rows after the age step).

## Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> FAIL
- testDevDebugUnitTest: pass (231 result files, 0 failures; CachedPayloadSweeperTest 11, MediaCacheTrimTest 3 green).
- :app:detektDevDebug: FAIL, 1 issue: style CollapsibleIfStatements at ui/feature/player/vm/PlaybackController.kt:643 (`if (!writeToCache) { if (BuildConfig.DEBUG) {`).
- :app:lintDevDebug: FAIL, 17 UnsafeOptInUsageError errors, all in the new files data/cache/MediaCacheTrim.kt (lines 10-29) and ui/feature/player/vm/CacheWriteSink.kt (lines 8-9): media3 Cache/CacheSpan/DataSink/CacheDataSink need @OptIn(UnstableApi::class).
DEVICE: emulator-5554 (leased after waiting about 25 min behind another agent, released)
ACS (round 2):
| AC | Status | Evidence |
|----|--------|----------|
| AC-3 | PASS (trim mechanism; shipped cap not seen over-cap) | Cache left at 256 MB cap by round 1, so over-cap could not be recreated legitimately. Temporary local build with cap 128 MiB (not committed, tree restored, git status clean) over the 261300 KB cache: no playback, no warm-up, 261300 KB -> 130348 KB within 70 s of cold start. Shipped build: cache under cap, trim no-op, du unchanged |
| AC-1 | PASS (round 1) | Not re-run; round 2 change only extracts cacheWriteSink (CacheWriteSinkTest) |
| AC-2 | PASS (round 1) | Not re-run, same reason |
| AC-6 | PASS | unit tests (sweeper, trim once per process); device: trim ran at the first sweep about 60 s after start, 0 FATAL |
| AC-9 | PASS | debug log Coil memory cache maxSize=50331648 |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-storage-bounds/docs/superpowers/evidence/2026-11-21-cache-storage-bounds-device.md (round 2 section, committed: b28b9369 Record the round 2 device evidence of the media cache trim; mobile-evidence-audit passed)
DEFECTS:
1. check.full red (lint): missing @OptIn(UnstableApi::class) in MediaCacheTrim.kt and CacheWriteSink.kt (17 errors). Repro: cd wt && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:lintDevDebug.
2. check.full red (detekt): CollapsibleIfStatements at PlaybackController.kt:643. Repro: ./tools/agentw :app:detektDevDebug.
UNAVAILABLE: none
MANUAL: none. Notes: another agent's build of the same package was installed during my wait (not debuggable, flags without DEBUGGABLE); I did not touch it beyond replacing it with the worktree APK. The device now carries the shipped worktree APK and a 127 MB media cache.
