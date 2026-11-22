# Gate - cache-decoded-memory-tier - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** PASS
**Head:** 6adf8e1b   **Base:** e9c383d0

## Done as asked

`git diff e9c383d0...6adf8e1b -- app/src/main` matches every row of spec Scope and Design Decisions: one `CachedFeed` per record type plus lazy per-key payload feeds (`ContentCacheRepository.kt` `newFeed`, `payloadFeed`); weigher on carried `encodedLength` (`CachedFeed.kt` `Stamped`, `Usable`); `content:v2:` prefix, v1 rows left to the sweeper (no Room change); `flowOn`/`withContext(decodeDispatcher)` on the public flows and one-shot writes; `KinoPubApiClient` `apiCall`, `thirdPartyApiCall`, `handleApiResponse` decode on an injected dispatcher (plan Task 6); `IS NOT` no-op predicate on all eight written columns in `WatchStateDao.upsertFromServer`; `clearAllPending` after `awaitStartupSettled`, wired in `data/di/modules.kt`. Nothing from Out of scope (incremental snapshot, prefs/StrictMode/largeHeap items, cap change) is in the diff. Removed helpers (`encode`, `decode`, `decodeOrDiscard`, `discardUndecodablePayload`) leave no callers.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | CCR `aListServedFromTheMemoryTierReturnsTheSameDecodedCards` (:328), `detailsOnlyInRoomReadTheItemRowExactlyOnce` (:372), `aListOnlyInRoomReadsEachRowExactlyOnce`, `aPayloadInTheMemoryTierIsNotDecodedAgain` | check.full at 6adf8e1b (judge run): 2283 tests, 0 failures | PASS |
| AC-2 | CCR `flowsDecodeAndLoadOnTheInjectedDispatcher` (:551), `getPayloadAndOneShotWritesRunOnTheInjectedDispatcher` (:585) | same run | PASS |
| AC-3 | CCR `storedRecordsArePlainJsonUnderTheV2Namespace` (:412) | same run | PASS |
| AC-4 | CCR `v1RowsAreIgnoredAndRefetchedIntoV2` (:437); manual check 1 | same run; evidence file `## AC-4 / Manual check 1` (install-preserve over e9c383d0 build, Home + 3 Details, crash buffer empty) | PASS |
| AC-5 | `CachedFeedMemoryWeightTest.aValuePastTheWeightCapEvictsTheOldestFromMemory`, CCR `itemTierHoldsSixteenMegabytesOfEncodedJson` | same run | PASS |
| AC-6 | CCR `clearDropsEvery{Item,List,Page,HistoryPage,Payload}FromMemory` (:518, :528 sampled) | same run | PASS |
| AC-7 | DAO `aServerRowEqualToTheStoredOneIsNotRewritten` (:84), `aServerRowDifferingInAnyWrittenColumnLands` (:115, 12 variants read) | evidence file `## Round 2`: red on base DAO (expected 1 was 2), 10/10 green on head | PASS |
| AC-8 | WSR `clearAllPending_waitsForStartupToSettle` (:300), `markLocally_completesOnlyAfterTheDeferredCleanup` (:330); `WatchStateRepositoryModuleTest` (:27) | check.full run; manual check 2 below | PASS |
| AC-9 | Named suites; `CachedFeedTest` diff vs base empty; no removed `assert` line in test diff (grep) | check.full green at 6adf8e1b | PASS |
| AC-10 | Device traces + KDD tests | evidence file `## AC-10`: Main decode 0 (cold 0/0, opens 0 x3) vs baseline 31-66 exact and 72-240 episodes; heap recorded, no >25 % growth on comparable columns | PASS |
| Manual check 2 | Mark watched within 5 s of cold start | not run: mutates the account | NOT CHECKED |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Memory cap 16Mi chars per feed and per payload key, not one byte budget | review-blind-1 F1 | deferred | new run `.flow/runs/typed-feeds-memory-budget/state.md` (source cache-decoded-memory-tier) |
| AC-7 red row never ran red on base | review-tests-1 F1 | fixed | evidence 6adf8e1b, Round 2 section |
| No AC-1 Details Room-only exactly-once test | review-tests-1 F2 | fixed | c93c20c6, `ContentCacheRepositoryTest.kt:372` |
| `thirdPartyApiCall`/`handleApiResponse` decode untested | review-tests-1 F3 | fixed | c93c20c6, `KinoPubApiClientDecodeDispatcherTest.kt:96` and the `getDeviceCode` case |
| `flowOn` buffer lets old-session details reach `recordFromServer` after `clear()` | review-codex-1 (rejected) | rejected, accepted | the window exists at base: `recordFromServer` suspends on `currentGeneration()` (`WatchStateRepository.kt:256`); no caller shown |
| Probe Ktor client not closed in two tests | review-tests-2 F1 (nit) | accepted | test-only, no result depends on it |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Main-thread decode 0 after vs 1.8-10.2 s per sequence before (evidence AC-10). Decoded graphs weighed by JSON length, cap per feed: total tier can exceed base; deferred to `typed-feeds-memory-budget`. Token refresh (`KinoPubApiClient.kt:207`) and `ApiFailures.kt:66,73` decodes stay in caller context, outside plan Task 6 (readiness-2 note) |
| Reliability | Undecodable rows still dropped by `CachedFeed.decodeUsable`; cancellation rethrown in `handleApiResponse`; `clearAllPending` failure still completes `inheritedFlagsCleared` in `finally`. `markLocally` waits up to the 5 s settle fallback (`StartupSettled.kt:28`) on a fresh activity, spec-accepted risk |
| Maintainability | AGENTS.md conventions kept (Koin DSL, JUnit 5); replaced helpers removed with no callers left; `payloadFeeds` grows one small feed per payload key, covered by the deferred run |
| Security / privacy | No secrets or account data in diff or evidence; evidence audit passed (qa-report round 1); screenshots not stored |

## Manual checks for the owner

1. Manual check 2 (AC-8 runtime ordering), needs the owner's authorization to change the account's watch state: on emulator-5554 with the worktree dev APK installed by `tools/android-apk-install-preserve`, force-stop, cold start, open a series Details within 5 s, toggle an episode watched, BACK to Home. Expect the watched mark on the card at once or after at most ~5 s, and still there after the next WatchState sync (about 2 s after settle). Fail: mark reverted, or the toggle does nothing. Toggle it back afterwards.

## Must fix

none

## Concerns for the owner

none
