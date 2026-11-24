LENS: state-path  ROUND: 2  BASE: 8e005395

No findings.

Reviewed the incremental diff a4b27373..69c61413. It touches only the store layer: PersistentPayloadStore.kt, CachedPayloadDao.kt, tests and docs. It changes no ViewModel, UIAction, composable or focus path.

- Fix T1, read cleanup (deleteOversizedAmong): oversizedKeys, then deleteOversized(oversized). Between the select and the delete, a concurrent writer can upsert a small row for the same key; the delete still carries length(CAST(payload AS BLOB)) > :maxBytes, so it leaves that row alone. In the opposite order, the select misses a row and the delete is skipped; that row can only be an oversized one written meanwhile, and write/writeAll refuse oversized payloads before any upsert, so nothing is lost. The read's result is computed before the cleanup runs. A cleanup failure is logged and dropped. CancellationException is rethrown. The key-only select puts no payload into the cursor, so it cannot hit the cursor-window limit.
- Fix T2, refusal log: only removes the payload encoding. dao.delete(key) is unchanged, so the T5/T6 race stays as it was (deferred, not reported again).
- Spec risk rows now describe the deferred T5/T6 behaviour correctly.
