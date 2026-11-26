# Triage round 4: baseline-generation-hardening

Reviewed: 91a9b7ba..017e975e. Sources: review-edge-4.md, review-tests-4.md (clean), review-codex-4.md, review-antigravity-4.md.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | New discriminator treats a missing or empty baseline-prof.txt as stale and deletes the backups (regression from fix round 3) | antigravity, codex, edge (unproven) | minor | fix | One line: out of date only when the file exists, is non-empty and has no non-H method rule; otherwise restore. Add the missing-file test |
| 2 | Signal between the re-mark and the trap re-arm; double faults in restore | edge, codex unproven | - | reject | One statement wide / double fault |

Deferred: none. Accepted: none.
