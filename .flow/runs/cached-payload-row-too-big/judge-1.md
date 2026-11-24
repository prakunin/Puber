# Gate - cached-payload-row-too-big - round 1

**Decision:** PASS
**Head:** 6f3ba67c   **Base:** 8e005395

## Done as asked

`git diff 8e005395...HEAD -- app` touches production code only in `data/db/CachedPayloadDao.kt` and `data/repository/PersistentPayloadStore.kt`, as the brief's approach (research option A) chose: writes over `MAX_ROW_BYTES = 1 shl 20` UTF-8 bytes are refused and the key's row deleted; `read`/`readAll` filter with `length(CAST(payload AS BLOB)) <= :maxBytes` and delete only keys `oversizedKeys` reports. No schema change, `PersistentPayloadStore` interface, `CachedFeed` and `ContentCacheRepository` untouched; nothing from spec Out of scope (trim/split of the details record, schema or driver) is in the diff.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `PersistentPayloadStoreDbTest.readAllReturnsTheSmallRowsAndDeletesARowTooBigForTheCursorWindow` | red at fd6e8233 (`SQLiteBlobTooBigException`), green on the box at 69c61413 (5/5); evidence file, Round 2 | PASS |
| AC-2 | `PersistentPayloadStoreDbTest.readReturnsNullAndDeletesARowTooBigForTheCursorWindow` | same runs | PASS |
| AC-3 | `PersistentPayloadStoreTest.writeOfAnOversizedPayloadDeletesTheKeysOlderRow`, `writeAllStoresTheSmallPayloadAndDropsTheOversizedKey` | red recorded in plan Implementation record; green in check.full at 69c61413 (2555 tests, 0 failures) | PASS |
| AC-4 | `writeRefusesACyrillicPayloadUnderTheLimitInCharsButOverItInBytes`, `writeStoresAPayloadOfExactlyTheLimitAndRefusesOneByteMore` [a, ж, 中, 😀] | same | PASS |
| AC-5 | `CachedFeedTest.aValueTheStoreRefusesIsStillServedFromMemoryInTheSameSession` (+ load variant) | guard, green in check.full at 69c61413 | PASS |
| AC-6 | box 192.168.1.110:5555: before state, then fixed APK, shelf and grid, cold start | before: 120 exception lines, 2439355 B row vs 2097152 B window; after at 69c61413 (sha256 matches device): cards on both screens, 0 exceptions in pids 8770, 9242 | PASS |

Sampled: all AC tests exist in the diff with the asserted shape; the evidence file records commit, sha256 and per-pid counts.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| T1 delete on every miss | blind F1, edge F1 | fixed | 23dfb968 (`oversizedKeys` select, cleanup failure logged) |
| T2 `encodeToByteArray` in refusal log | blind F2, codex, state-path F1 | fixed | 23dfb968 (logs `payload.length`) |
| T3 R2 tests cannot detect UTF-16 | tests-1 F1 | fixed | 69c61413 `anAsciiPayloadOfExactlyTheLimitRoundTripsThroughTheStore`, green on box |
| T4 `length * 3` fast path unpinned | tests-1 F2 | fixed | 69c61413 `writeStoresAThreeByteCharPayloadOfExactlyTheLimitAndRefusesOneCharMore` |
| T5 refused write deletes a concurrent card row | state-path-1 F1 | deferred | run `cached-item-card-only-fallback` (stage idea) |
| T6 card never persisted while big details record is in memory | state-path-1 F2 | deferred | same run; spec risk rows corrected |
| T7 codex race | codex-1 | rejected, accepted | same race as T5, carried by the deferred run |
| T8 Timber logs raw key | blind unproven | rejected, accepted | Timber planted only under `BuildConfig.DEBUG` (`PuberApp.kt:104-105`), checked |
| T9 lone surrogate near limit | edge unproven | rejected, accepted | unproven, needs an unpaired surrogate within bytes of 1 MiB |
| U1 device evidence predates cleanup change [major] | codex-2 | fixed | qa round 2 at 69c61413, evidence 6f3ba67c |
| U2 cleanup test never throws from `deleteOversized` | tests-2 F1, codex-2 | accepted minor | single try covers both calls (`PersistentPayloadStore.kt` `deleteOversizedAmong`) |
| U3 `CancellationException` rethrow untested | tests-2 F2 | accepted minor | rethrow present in diff |
| U4 three-byte "exact" is 1 byte under 2^20 | codex-2, edge-2 | accepted minor | exact limit pinned for ASCII on real db and for ж/😀 in unit test |
| U5 cleanup log repeats on full disk | edge-2 unproven | rejected, accepted | debug-only log, no evidence of cleanup failure |
| U6 `IN` list over 999 params | edge-2 unproven | rejected, accepted | `readAll` binds the same or a longer list first (diff, `readAll`), checked |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | `utf8Size` does not allocate; refusal no longer encodes. Each read miss adds one key-only `SELECT` (no payload in the cursor). `length(CAST ...)` cost accepted as R11. |
| Reliability | Cleanup failure cannot fail a read; cancellation rethrown; generation check kept for kept rows in `writeAll`. Open: T5/T6 (deferred run). |
| Maintainability | Two files, project conventions kept, no dead code. Nit: the `utf8Size` KDoc sits above `ONE_BYTE_BELOW`, so it documents the constant, not the function. |
| Security / privacy | Refusal log holds key, char count and limit only, never the payload (diff, `refuse`), debug builds only; evidence file holds no tokens or raw logcat, audit passed per qa-report. |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

none
