LENS: tests  ROUND: 2  BASE: 8e005395 (incremental a4b27373..69c61413)

Suite state: ./tools/agentw testDevDebugUnitTest --tests '*PersistentPayloadStore*' at 69c61413 passed: PersistentPayloadStoreTest 22 tests, 0 failures, 0 skipped. No instrumented test has run on this diff yet (T3 test and AC-1/AC-2; the new oversizedKeys SQL is only exercised there).

Coverage of the fix round:
- T1: aMissOnANeverStoredKeyRunsNoDelete would fail on a4b27373. Oversized rows still deleted via the new path: readAllDropsAndDeletesOnlyTheRowsOverTheLimit (unit), AC-1/AC-2 (device).
- T3: ASCII payload of exactly MAX_ROW_BYTES on a real database would measure 2 MiB under UTF-16, so the test would catch it. Written correctly, not yet run.
- T4: writeStoresAThreeByteCharPayloadOfExactlyTheLimitAndRefusesOneCharMore sits on the boundary; "中".repeat(349526) = 1 048 578 bytes is refused; a * 2 factor would fail it. Green by design as a pin.
- No assertions removed or weakened.

### F1 [minor] The cleanup-failure test only makes the SELECT throw, not the DELETE that T1 was about
- Where: app/src/test/kotlin/com/kino/puber/data/repository/PersistentPayloadStoreTest.kt:194 (aFailingCleanupStillReturnsTheReadResult); PersistentPayloadStore.kt:88-97
- Problem: onOversizedKeys throws before deleteOversized is ever called. The deleteOversized failure case is never tested: the fake has no hook and the test plants no oversized row.
- Failure scenario: the try narrowed to wrap only dao.oversizedKeys(...), or deleteOversized moved outside it; a SQLiteFullException during the delete would make store.read throw again; all 22 tests still pass.
- Fix: an onDeleteOversized hook in FakeCachedPayloadDao; plant an oversized row, make the hook throw, assert store.read(big) returns null and store.readAll(listOf("small", "big")) returns only small.

### F2 [minor] The CancellationException rethrow in the cleanup has no test
- Where: PersistentPayloadStore.kt:93-94
- Problem: new branch; no test fails if it is deleted; without it catch (e: Exception) swallows the cancellation.
- Failure scenario: a scope cancelled while oversizedKeys or deleteOversized is suspended; with the rethrow removed, the cancellation is only logged and read returns normally. Suite stays green.
- Fix: make the hook throw CancellationException("x") and assertThrows<CancellationException> on store.read("never").

### Unproven
- PersistentPayloadStore.kt:61-97: the plan's fix-round record gives no priority and no red run for aMissOnANeverStoredKeyRunsNoDelete or aFailingCleanupStillReturnsTheReadResult. Only matters if they count as P0; the test design marks nothing new P0.
