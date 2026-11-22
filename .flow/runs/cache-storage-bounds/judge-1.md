# Gate - cache-storage-bounds - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** 7c6868fd   **Base:** a22c03bc

## Done as asked

The diff (`git diff a22c03bc...7c6868fd`) covers every row of spec Scope: read-only main factory and writing warm-up factory (`PlaybackController.kt`, `CacheWriteSink.kt`), cap 256 MiB (`MediaCacheTrim.kt`), two-step sweep with `updated_at` index (`CachedPayloadDao.kt`, `CachedPayloadEntity.kt`, `PersistentPayloadStore.kt`, `CachedPayloadSweeper.kt`), Room v4 with `LegacyPayloadCleanup` and the runtime cleanup removed (`PuberDatabase.kt`, `ContentCacheRepository.kt`), Coil `maxSizeBytes` (`PuberApp.kt`). One addition beyond the spec design: a once-per-process media cache trim on the first sweep (`MediaCacheTrim.kt`, `modules.kt:108-116`), the triage-1 row 1 fix that makes the 256 MB cap reach users who never trigger a warm-up. Nothing from Out of scope is touched. No Co-Authored-By line in any commit; worktree clean.

## Traceability

Instrumented and device evidence: `docs/superpowers/evidence/2026-11-21-cache-storage-bounds-device.md` (in wt; `tools/mobile-evidence-audit.sh` re-run by the judge: passed). Named tests re-checked by grep in `app/src/`.

| AC | Test / manual check | Evidence | Status (PASS / FAIL / NOT CHECKED / NOT VERIFIED) |
|----|---------------------|----------|----------------------------------------------------|
| AC-1 | M1; `CacheWriteSinkTest` | 0 new files over 7 min of main playback; the AC asks for 30 min with a fast-forward and a seek back; evidence says "30 min not played" | NOT VERIFIED |
| AC-2 | M2 | 7 files written in warm-up; 7 `onCachedBytesRead` lines with the same sizes at episode 8 start | PASS |
| AC-3 | M3; `MediaCacheTrimTest` | 526360 KB -> 254672 KB after warm-up (round 1); trim mechanism shown with a 128 MiB test build (round 2) | PASS |
| AC-4 | `deleteOlderThanRemovesOnlyRowsBeforeTheCutoff`, `sweepRemovesRowsPastHardCeilingAndKeepsNewerOnesIdentical`, `aRowExactlyAtTheHardCeilingSurvives` | DAO 10/10 on device; unit green in judge's check.full; device 2900 -> 457 rows | PASS |
| AC-5 | `deleteBeyondNewestKeepsTheNewestRows`, `deleteBeyondNewestKeepsARowWrittenAfterTheAgeStep`, `sweepCapsRowsToTheNewest` | DAO 10/10 on device; unit green; cap not reached by real data | PASS |
| AC-6 | `sweepRunsOnceAfterTheStartupDelay`, `eachOnStopRunsOneMoreSweep`, `sweepRunsOffTheMainThread`, `aFailingSweepNeitherCrashesNorStopsLaterSweeps` | unit green; device sweep at about 60 s, 0 FATAL | PASS |
| AC-7 | `migrate3To4RemovesLegacyRowsAndKeepsEveryOtherRow`, `productionBuilderOpensAV3DatabaseWithoutDroppingWatchState`, M4 | 2/2 on device; v3 -> v4 upgrade: 6 legacy rows gone, watch_state sha256 identical | PASS |
| AC-8 | `firstUseLeavesLegacyRowsToTheMigration` | unit green; all nine `ensureLegacyRowsRemoved` calls removed in the diff | PASS |
| AC-9 | M5 | debug log `maxSize=50331648` (rounds 1 and 2) | PASS |
| AC-10 | existing suites; check.full | `CachedFeedTest`, `PlayerVMNextEpisodePrefetchTest` untouched; `ContentCacheRepositoryTest`, `CachedPayloadDaoTest` lose only the AC-8 test; check.full green on 7c6868fd (judge run) | PASS |
| AC-11 | perf table | numbers recorded in evidence "AC-11 numbers"; PSS single samples, see Concerns | PASS |

## Findings

| Finding | Source | Disposition | Proof (commit, new run, accepted reason) |
|---------|--------|-------------|------------------------------------------|
| 256 MB cap never applies without a warm-up | blind-1 F1, edge-1 F1 (minor) | fixed | 9c5c5db9 (`MediaCacheTrim.kt`, first sweep) |
| Dead field `dataSourceFactory` | edge-1 F2 (minor) | fixed | 9c5c5db9, verified in diff |
| Cutoff boundary not pinned | tests-1 F1 (minor) | fixed | 9c5c5db9, `aRowExactlyAtTheHardCeilingSurvives` |
| AC-1 branch untested; 7 min instead of 30 | tests-1 F2 (minor), codex-1, codex-3 | fixed (test) / accepted (duration) | `CacheWriteSinkTest`; duration carried to Concerns |
| `drop(1)` claim overstated | tests-1 F3 (nit) | fixed | comment at `CachedPayloadSweeper.kt:47-48` |
| Plan record red-run line and hash | tests-1 F4 (nit) | fixed | a8fdd8d1 |
| `trimMediaCache` and its wiring untested | tests-2 F1 (major) | fixed | 7c6868fd (`MediaCacheTrimTest` with mocked `Cache`) |
| lint UnsafeOptInUsageError | qa round 2 defect 1 (blocker) | fixed | 7c6868fd; lint green in judge's check.full |
| detekt CollapsibleIfStatements | qa round 2 defect 2 (blocker) | fixed | 7c6868fd; detekt green in judge's check.full |
| `CacheWriteSinkTest` checks the helper, not the call sites | tests-2 F2 (minor) | accepted | real, small; AC-1/AC-2 have device evidence |
| `theMediaCacheIsTrimmedOnceAcrossSweeps` collapses a StateFlow toggle | tests-2 F3 (minor) | accepted | real (`CachedPayloadSweeperTest.kt:88-89` has no `runCurrent()`); trim-once still asserted |
| PSS +25 MB after Home scroll | codex-1 (rejected) | rejection accepted as a code finding | no code path besides the Coil cap; kept as a perf concern below |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Sweep and trim on `Dispatchers.IO` 60 s after start; first trim opens `SimpleCache` in every session (edge-2 unproven). PSS after Home scroll 301027 KB vs 276232 KB base, single samples; the perf-track threshold (median of 3 <= baseline) was not measured |
| Reliability | Sweep and trim failures caught and logged (`runCatchingCancellable`), tested. Migration only adds an index and deletes by literal prefix; `fallbackToDestructiveMigration(dropAllTables = true)` also fires on downgrade, so any v3 build installed over this one wipes `watch_state` |
| Maintainability | Conventions kept; no dead code left (legacy cleanup, `LEGACY_PREFIXES`, field removed); `MEDIA_CACHE_SIZE_BYTES` has one definition |
| Security / privacy | No secrets or account data in code or logs; evidence audit passed; account mutation (two episodes' resume position) disclosed in evidence |

## Manual checks for the owner

1. M6 (R7, Coil 48 MB): on the emulator, cold start, scroll Home rows (`KEYCODE_DPAD_DOWN` x10, `KEYCODE_DPAD_RIGHT` x20 per row, then back up); confirm posters already seen do not flash placeholders. NOT CHECKED by the owner; qa saw no churn in screenshots.

## Must fix

none

## Concerns for the owner

1. AC-1 is NOT VERIFIED as written: 7 min of main playback, not 30 with a fast-forward and a seek back. The null write sink makes a later write structurally unlikely, but a 30-min M1 run could have been done here. Accept the 7-min run, or have qa-device run M1 in full.
2. PSS after the Home scroll rose 25 MB over a single base sample; the spec's PSS threshold (median of 3) was not measured.
3. Shared emulator: with DB v4 installed, a later install of any v3 build (another worktree based on a22c03bc) triggers the destructive fallback and drops `watch_state` on that device. qa round 2 also replaced another agent's build of the package on emulator-5554 with install-preserve.
4. The shipped 256 MiB trim of an over-cap cache was not seen on device (only the 128 MiB test build); `MediaCacheTrimTest` covers the logic.
