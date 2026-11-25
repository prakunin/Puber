# Triage round 1: cached-payload-row-too-big

Reviewed: 8e005395..a4b27373. Lenses: blind, edge, tests, codex (ran: review + adversarial), antigravity (ran), state-path. Each finding checked against the code at a4b27373 by the conductor.

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| T1 | blind F1, edge F1 | minor | Every miss in `read`/`readAll` runs `deleteOversized`, so a read that finds nothing opens a write transaction and can throw a write error (PersistentPayloadStore.kt:62-79) | fix | Confirmed: the delete runs for every key not returned, including never-stored keys. Cheap: select only the over-limit keys (`SELECT key ... length(CAST(payload AS BLOB)) > :maxBytes`), delete only those, and never let the cleanup fail the read |
| T2 | blind F2, codex P2 (rejected there), state-path F1 (part) | minor | `refuse` allocates a copy of the oversized payload with `encodeToByteArray()` just to log its size (PersistentPayloadStore.kt:115-117) | fix | Confirmed; cheap. Log the char length or the bounded count instead |
| T3 | tests F1 | minor | R2 tests use Cyrillic (2 bytes in both UTF-8 and UTF-16), so they cannot detect a UTF-16 database | fix | Confirmed by encoding arithmetic. Cheap: an ASCII payload of exactly MAX_ROW_BYTES written and read back through the store on a real database |
| T4 | tests F2 | minor | The `length * 3 <= stopAfter` fast path in `utf8Size` is not pinned | fix | Confirmed: no test payload reaches the boundary through the fast path. Cheap unit test with "中".repeat(MAX_ROW_BYTES / 3) stored and +1 refused |
| T5 | state-path F1 | minor | A refused write deletes the key's row unconditionally, so a concurrent card write for the same key can be lost for the session | defer | Real race (needs an oversized details load and a card merge of the same item at the same moment); fixing it means moving the delete under CachedFeed's epoch ownership, which is the same change as T6. New run `cached-item-card-only-fallback` |
| T6 | state-path F2 | minor | While an oversized details record sits in memory, every card merge for that item is refused too, so its card is never persisted; after restart lists naming it refetch, offline the card is missing | defer | Real; matches Q2's planned follow-up (persist a trimmed copy). Scope is ContentCacheRepository/CachedFeed, beyond this run's store-level fix. New run `cached-item-card-only-fallback`; the spec's risk row is corrected now (architect) |
| T7 | codex P2/high | — | Refusal after an accepted upsert can delete a newer row of a concurrent batch | reject | Same race as T5, deferred there |
| T8 | blind unproven | — | Timber logs the raw cache key | reject | Keys are `content:v2:item:<id>` / query keys; Timber is planted in debug builds only (PuberApp.kt:105) |
| T9 | edge unproven | — | Lone surrogate counted as 3 bytes may disagree with SQLite near the limit | reject | Unproven; needs an unpaired surrogate within a few bytes of 1 MiB |
| T10 | edge unproven | — | UTF-16 database | covered by T3 | |

Fix round: T1-T4 to the implementer. Then check.fast; incremental review by edge, tests, codex, antigravity, state-path; qa-device on AC-1, AC-2 and the new T3 test on the box.
