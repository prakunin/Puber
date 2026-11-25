# Triage round 2: cached-item-card-only-fallback

Reviewed incrementally: b25678c6..9ac3dba7 (fix 9ac3dba7). Lenses: edge (no findings), tests (no findings), codex (ran), antigravity (ran), state-path (no findings). qa round 2 at 9ac3dba7, evidence 2b79669c.

| ID | Source | Severity | Finding | Decision | Reason |
|----|--------|----------|---------|----------|--------|
| U1 | codex [major], antigravity [major] | major | The T3 "fallback persisted" log reuses the prefix "Refused cached payload", which the spec (design table) and AC-7 reserve for "nothing persisted"; AC-7's logcat condition then fails on a correct card-only row, and the prefix no longer separates recovery from a failed persist | fix | Confirmed in code (CachedFeed.kt persistLoaded / persistRefused) and observed by qa round 2 (line logged for id 9365). This came from my T3 instruction. Fix: a distinct message for the fallback path (no "Refused cached payload" prefix), keep "Refused cached payload ... nothing persisted" unchanged; plan T3 entry updated |
| U2 | edge unproven | — | Bulk "fallback persisted" log not re-checked against the epoch after writeAll | reject | Log only, data unaffected (edge itself); unproven |
| U3 | tests unproven | — | persistLoaded fallback-also-refused branch untested on the load path | reject | Unproven: needs a card-only copy over 1 MiB; none seen (card-only rows 3-4 KB on the box) |

Fix round: U1 to the implementer; then incremental codex, antigravity, edge; qa round 3 on AC-7 (logcat: the new fallback line present, no "Refused cached payload" line for the opened key).
