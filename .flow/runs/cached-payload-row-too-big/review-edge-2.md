LENS: edge  ROUND: 2  BASE: 8e005395

No findings.

### Unproven
- PersistentPayloadStore.kt:94 The cleanup-failure log (`Timber.w(e, "Could not delete oversized cached payloads")`) does not name the keys it was cleaning up. Also, if the database is full or read-only, the cleanup fails on every miss, so this line would repeat on every miss. Both matter only with evidence that `oversizedKeys` or `deleteOversized` actually throw on the box (`SQLiteFullException`, or a read-only open).
- CachedPayloadDao.kt:20 `oversizedKeys` binds the whole missing-key list in `IN (:keys)`, so a very long list could pass SQLite's host-parameter limit (999 on older SQLite builds). This is not new: `readAll` already binds a list at least as long on the line above, and it would fail first. It matters only with evidence that `CachedFeed.peekAll` (CachedFeed.kt:360) is called with more than 999 keys.
- PersistentPayloadStoreTest.kt:203 `writeStoresAThreeByteCharPayloadOfExactlyTheLimit...` writes 349,525 × 3 = 1,048,575 bytes, one byte under the limit, not exactly at it: 2^20 is not divisible by 3. So the test checks the `length * 3 <= stopAfter` fast-path boundary, but not a payload of exactly `MAX_ROW_BYTES`. Exactly 2^20 bytes is covered for ASCII by `anAsciiPayloadOfExactlyTheLimitRoundTripsThroughTheStore` on a real database, and the old unit test covers it for ж and 😀. The gap matters only if someone reads the test name as covering 3-byte text at exactly 2^20 bytes.
