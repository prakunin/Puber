# Review triage - round 3

Run: startup-reveal-gate-on-slow-tv   Base: 9158cf82   Reviewed: 0800302d..cdb85fa1

Sources: review-edge-3.md. Codex skipped (usage limit, owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | minor | evidence :133 | AC-10 row claims one row per key on 15 starts | Read | fix (consistency with the triage-2 split, text only) | open |
| 2 | edge F2 | nit | evidence :130 | focus clause reads as 1 of 25 covered; it is 0 of 25 counted | Read | fix (text only) | open |
| 3 | edge F3 | nit | evidence :129 | HTML entities in the AC-6 row | Read | fix (text only) | open |

All triage-2 items hold. No blockers or majors. The AC-7 focus clause (0 of 25 counted starts checked) goes to the owner at gate 2.

## Next

- qa-device text fix (no device), then the final judge.
