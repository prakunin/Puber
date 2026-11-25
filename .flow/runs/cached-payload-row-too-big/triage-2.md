# Triage round 2: cached-payload-row-too-big

Reviewed incrementally: a4b27373..69c61413 (fix commits 23dfb968, 69c61413). Lenses: edge, tests, codex (ran: review + adversarial), antigravity (ran, clean), state-path (no findings). qa-device round 2 ran on the box at 69c61413 (evidence commit 6f3ba67c).

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| U1 | codex [major] | major | Device evidence predates the read-cleanup change | resolved | qa-device round 2 at 69c61413: PersistentPayloadStoreDbTest 5/5 (incl. the T3 ASCII exact-limit test), CachedPayloadDaoTest 11/11, AC-6 PASS after two cold starts; evidence 6f3ba67c |
| U2 | tests F1, codex minor 2 | minor | `aFailingCleanupStillReturnsTheReadResult` throws only from `oversizedKeys`, never from `deleteOversized` | accept | Confirmed. Code wraps both calls in one try (PersistentPayloadStore.kt:88-97); a test gap only, round 2 fixes blockers/majors only |
| U3 | tests F2 | minor | The `CancellationException` rethrow in the cleanup has no test | accept | Confirmed; test gap only |
| U4 | codex minor 1, edge unproven 3 | minor | The three-byte test's "exact" payload is one byte under 2^20, not exactly at it | accept | Confirmed (2^20 is not divisible by 3); exactly-at-limit is pinned for ASCII on a real database and for 2- and 4-byte chars in the unit test |
| U5 | edge unproven 1 | — | Cleanup-failure log names no keys and would repeat on every miss on a full disk | reject | Unproven: no evidence the cleanup throws on the box |
| U6 | edge unproven 2 | — | `oversizedKeys` IN-list may pass the 999 host-parameter limit | reject | Not new: `readAll` binds the same list first |

No item left to fix. Stage -> judge.
