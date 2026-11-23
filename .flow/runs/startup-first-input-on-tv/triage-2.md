# Review triage - round 2

Run: startup-first-input-on-tv   Base: c506d0bd   Reviewed head: 9d454407 (incremental 6eb30d7c..9d454407); qa round 2 evidence 80ca6fb6

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial). Lens blind not re-run (incremental round).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | triage-1 row 1 (tests F1) | major | qa-report.md Round 2, verify-2-device.md | AC-6 anchors B/C | qa re-measured with a 4-6 ms on-device injector; B landed +1220..+2063 ms and C +1800..+2712 ms after the revealed mark; reported "NOT MET as specified", not PASS; A n=9 lines | fix | resolved: the false PASS is gone; B/C carried to gate 2 as NOT MET as specified. An in-app reveal-relative trigger is recorded in startup-reveal-gate-on-slow-tv |

No new findings in round 2 (edge, tests, codex all clean).

## Rejected codex claims

- none

## Next

- No fixes; go to judge. Unproven lines: 3 (edge 1, tests 2).
