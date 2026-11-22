Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL, 46 tasks; tests re-run with `:app:testDevDebugUnitTest --rerun`: 2279 tests, 0 failures, 0 errors, 0 skipped; detekt and lint green)
DEVICE: emulator-5554 (puber_tv_36), lease held and released. Needed: diff touches startup wiring, DI, persistence, threading and a Room query. APK sha256 9dc153a2...58d67 equal on device after install-preserve (outcome installed, compatible_replace, signer unchanged) and after make itest.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | ContentCacheRepositoryTest: aListServedFromTheMemoryTierReturnsTheSameDecodedCards, detailsServedFromTheMemoryTierAreNotDecodedAgain, aListOnlyInRoomReadsEachRowExactlyOnce, aPayloadInTheMemoryTierIsNotDecodedAgain, aStaleListRefetchedAfterARoomReadStillRevalidates, all green in the re-run |
| AC-2 | PASS | flowsDecodeAndLoadOnTheInjectedDispatcher, getPayloadAndOneShotWritesRunOnTheInjectedDispatcher green; device trace shows 0 decode on Main |
| AC-3 | PASS | storedRecordsArePlainJsonUnderTheV2Namespace green |
| AC-4 | PASS | v1RowsAreIgnoredAndRefetchedIntoV2 green; device: install-preserve upgrade over the base 1.12.3-dev build, Home and three Details render, still logged in, crash buffer empty, no SerializationException |
| AC-5 | PASS | CachedFeedMemoryWeightTest.aValuePastTheWeightCapEvictsTheOldestFromMemory, ContentCacheRepositoryTest.itemTierHoldsSixteenMegabytesOfEncodedJson green |
| AC-6 | PASS | five clearDropsEvery{Item,List,Page,HistoryPage,Payload}FromMemory tests green |
| AC-7 | PASS | make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest: 10 tests, 0 failures, incl. aServerRowEqualToTheStoredOneIsNotRewritten and aServerRowDifferingInAnyWrittenColumnLands. Red-on-base was not run on the device |
| AC-8 | PASS | WatchStateRepositoryTest clearAllPending_waitsForStartupToSettle, markLocally_completesOnlyAfterTheDeferredCleanup, markLocally_isNotStrippedByTheCleanupQueuedAtStartup; WatchStateRepositoryModuleTest green. Manual check 2 (mark watched) NOT CHECKED: it mutates the account, no authorization |
| AC-9 | PASS | CachedFeedTest (42) unchanged (empty diff against base); ContentCacheRepositoryTest (36), WatchStateRepositoryTest (33), HomeInteractorCacheTest (6), DetailsVMCachedLoadTest (7), ItemDetailsRepositoryTest (18), HomeVMIncrementalPublishTest (21) all green, one run each, no flake; ContentCacheRepositoryTest diff vs base deletes only a comment; check.full green |
| AC-10 | PASS | Main-thread decode zero. Cold start (full trace): 66 and 64 decodeFromString, 0 on Main (baseline 165/66 and 101/31). Whole sequence, sampling x3: Main decode episodes 0 in the cold and the opens windows (baseline 72-95 and 197-240 episodes, 1.8-2.7 s and 9.3-10.2 s), ktor converter on Main 0 (baseline 10-15 episodes). KinoPubApiClientDecodeDispatcherTest.responseBodyIsDecodedOnTheInjectedDispatcher green. Heap (3 runs, no profiler): Java Heap PSS summary 32684-48104 KB vs baseline 40648-58728; Dalvik Private Dirty 22476-37896 vs ~26.2 MB; no growth over 25 % visible (the baseline's "Dalvik Heap Alloc" column does not map to this device's columns, comparison is coarse) |
TESTS ADDED: none (the implementer's tests cover every testable AC; no gaps found)
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-decoded-memory-tier/docs/superpowers/evidence/2026-11-22-cache-decoded-memory-tier-device.md (audit passed, committed in the worktree: "Record the device evidence of the decoded memory tier verification")
DEFECTS: none. Note for review: token refresh (KinoPubApiClient.kt:207) and error-body decodes (ApiFailures.kt:66,73) were not exercised (no 401 or error response in the sequence), so their decode context stays unobserved; they are by design outside Task 6. One full-trace run (c1) ended with a native SIGSEGV in art::Trace::StopTracing (ART's own profiler stop, app uptime 17 s); the app was not otherwise crashing, c2 and all later runs were clean.
UNAVAILABLE: none
MANUAL: (1) Manual check 2: on the upgraded app mark an episode watched in Details, BACK to Home within 5 s of a cold start, confirm the mark shows and survives the next sync (needs the owner's authorization for the account change). (2) Optional: a 401 token refresh with a trace to confirm KinoPubApiClient.kt:207 does not decode on Main.

---

Round 2 (affected ACs only; head c93c20c6, tests-only change since round 1)

CHECK: :app:testDevDebugUnitTest --tests ContentCacheRepositoryTest --tests KinoPubApiClientDecodeDispatcherTest --rerun -> pass (37 and 4 tests, 0 failures, 0 errors, 0 skipped). Full check.full not re-run: production code identical to the round 1 verified state, round 1 check.full was green.
DEVICE: emulator-5554, lease held and released. Device ends on the head dev debug APK (on-device sha256 9dc153a2...58d67 equals the worktree APK, install-preserve: installed, compatible_replace, signer unchanged).
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-7 | PASS | Base WatchStateDao (e9c383d0) temporarily in the worktree, itest WatchStateDaoTest: 10 tests, 1 failure. aServerRowEqualToTheStoredOneIsNotRewritten RED on base (expected:<1> but was:<2>). aServerRowDifferingInAnyWrittenColumnLands is GREEN on base: the base writes every row unconditionally, so a differing row lands there too; it is an over-skip guard and cannot fail on base. Head DAO restored via git checkout: 10 tests, 0 failures. |
| AC-1, AC-2 (new tests) | PASS | ContentCacheRepositoryTest 37 and KinoPubApiClientDecodeDispatcherTest 4 green |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/cache-decoded-memory-tier/docs/superpowers/evidence/2026-11-22-cache-decoded-memory-tier-device.md (Round 2 section, audit run, committed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: unchanged from round 1
NOTE: triage-1 #2 expected both AC-7 tests red on base; only the first can be. The second stays green on base by design.
