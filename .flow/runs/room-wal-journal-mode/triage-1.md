# Review triage - round 1

Run: room-wal-journal-mode   Base: 8c3117ea   Reviewed head: 457bb5dd

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial), review-antigravity-1.md (skipped: quota reached, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1, blind F1, codex (rejected) | minor | `PuberDatabaseMigrationTest.kt:137-183`, plan Implementation record | Guards cannot fail on a non-low-RAM device; the plan's "green on base" line was never recorded | Evidence: `ro.config.low_ram` empty, header already `2 2` before install; Implementation record says "not run here" | fix (cheap, record only) | Implementation record states that the guards were not run on base and cannot tell base from head on any available device; `PuberDatabase.kt:42-43` is guarded by review. A config-reading test is not added: the spec's Test design accepts this risk (score 3), and `isLowRamDevice` cannot be injected |
| 2 | blind F2 | minor | `PuberDatabase.kt:42-43` | WAL brings `synchronous = NORMAL` on low-RAM devices; the last commits can roll back after a power cut, and the comment does not say so | WAL + NORMAL durability is SQLite's documented behaviour; `is_local_pending` rows are local-only | fix (cheap, comment) | Comment names the trade-off; no PRAGMA change (non-low-RAM devices already run WAL + NORMAL today) |

## Rejected codex claims

- none (codex verdict clean; its one rejected item is merged into #1)

## Next

- Fix round: implementer fixes #1-#2 (comment + record); then check.fast, review edge + codex on the incremental diff; no runtime behaviour change, so no qa re-run; unproven lines: 3
