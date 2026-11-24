LENS: tests  ROUND: 1  BASE: 8e005395

Suite state: check.full at a4b27373 green; 256 unit result files, 0 failures, 0 skipped. PersistentPayloadStoreTest 19/19, CachedFeedTest 56/56 (mostly UP-TO-DATE from the qa run; a4b27373 only adds an evidence doc on top of af7d459a).

Coverage:
- AC-1 to AC-5: every one has a test that would fail if the behaviour broke.
- Red runs: every P0 red test has a recorded red run (unit in the plan's Implementation record; instrumented AC-1/AC-2 in the qa-report: red at fd6e8233 with SQLiteBlobTooBigException, green at af7d459a).
- Guards: the AC-5 pair is marked guard; writeStartedBeforeAClearDoesNotSurviveIt is an existing guard.
- AC-6: device observation in docs/superpowers/evidence/2026-11-24-cached-payload-row-too-big-device.md.
- Removed or weakened assertions: none; old dao.read(key) calls were rewritten to a helper with Int.MAX_VALUE.

### F1 [minor] The R2 "UTF-16 database" test uses Cyrillic, which cannot tell UTF-8 from UTF-16
- Where: app/src/androidTest/kotlin/com/kino/puber/data/db/PersistentPayloadStoreDbTest.kt:72 (aMultiByteRowJustUnderTheLimitRoundTrips) and :81 (aStoredCyrillicRowOverTheLimitInBytesIsTreatedAsAbsent)
- Problem: The spec's Risks table and test-design row R2 say this test "fails if the db measures in UTF-16". U+0436 takes 2 bytes in UTF-8 and 2 bytes in UTF-16, so length(CAST(payload AS BLOB)) gives the same number either way. Both R2 tests would stay green on a UTF-16 database. The mismatch the spec describes, an ASCII row between 0.5 and 1 MiB that is accepted on write and then deleted on every read, is not tested at all.
- Failure scenario: puber.db is UTF-16. A 700 kB ASCII JSON payload passes utf8Size on write, fails the SQL predicate on read, and is deleted every time. Both R2 tests still pass.
- Fix: Add, or switch one of the cases to, a payload that measures differently in the two encodings: ASCII "a".repeat(MAX_ROW_BYTES) through store.write and assert store.read returns it, or a 3-byte CJK payload just under the limit.

### F2 [minor] The length * 3 <= stopAfter fast path in utf8Size is not pinned by any test
- Where: app/src/main/java/com/kino/puber/data/repository/PersistentPayloadStore.kt:160
- Problem: The fast path is never hit with a payload that needs the 3x factor. If the factor were weakened to * 2, every test would stay green.
- Failure scenario: mutation length * 2 <= stopAfter; "中".repeat(MAX_ROW_BYTES / 3 + 1) (about 1.5 MiB UTF-8) is accepted and upserted; the suite stays green.
- Fix: a case where store.write of "中".repeat(MAX_ROW_BYTES / 3 + 1) is refused and "中".repeat(MAX_ROW_BYTES / 3) is stored.

### Unproven
- PersistentPayloadStore.kt:118 R12, that the refused-write log carries no payload: qa-report NOT CHECKED; the format string holds only key and byte counts.
