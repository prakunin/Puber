LENS: tests  ROUND: 2  BASE: 4db1b061 (incremental b25678c6..9ac3dba7)

AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests '*CachedFeedTest*': BUILD SUCCESSFUL; CachedFeedTest 67 tests, 0 failures, 0 errors, 0 skipped; both new tests ran and passed.

- Round 1 F1 fixed: aRefusedBulkWriteWritesTheFallbackOnlyOverKeysItStillOwns fails if the owned filter is removed (onWriteAll hook runs putAll("k" to "newer") once). Recorded green on base as guard (T2), correct.
- T1 tested: aRefusedBulkWriteDoesNotDeleteAKeyTakenOverDuringAnEarlierDelete; red on unchanged code recorded (AssertionFailedError at CachedFeedTest.kt:181).
- The new "fallback persisted" log lines (T3) have no test; they change no data and print no payload. Not a finding.
- Bulk log lookups cannot throw (fallbacks.getValue only for accepted keys).
- Round 1 F2 (AC-7 device check, logcat positive control) still open; outside this diff.

No findings.

### Unproven
- CachedFeed.kt persistLoaded, the branch where the fallback is also refused: only the put path is tested. Matters only if a load can produce an item whose card-only copy is still over the row limit.
