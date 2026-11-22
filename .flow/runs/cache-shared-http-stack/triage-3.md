# Review triage - round 3 (integration after rebase)

Run: cache-shared-http-stack   Base: 7bcbfd83   Reviewed head: 04bb948c

Sources: review-edge-3.md (no findings), review-codex-3.md (attempt 1 UNAVAILABLE: usage limit; attempt 2 ran review, adversarial: clean). qa round 3 (qa-report.md): check.full green (2236 tests), M1, M2, splash smoke and domain switch PASS; evidence d376f671.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | qa round 3 note | open | startup reveal | Reveal at 4.0-4.3 s on the merged head against 2.0-2.7 s medians in the startup-splash-preload evidence; host load ~14. The reveal waits on images in flight, and image hosts now resolve through DoH (two queries) instead of system DNS, so a real regression is plausible | Not separable from host load without an A/B on the same host | measure: A/B of master 7bcbfd83 vs head, interleaved cold starts, same sitting | closed: A/B round 3b (d84d47c2) - head revealed median 3922 ms vs master 4640 ms, head range inside master range, both under host load; no regression |

## Rejected codex claims

- none

## Next

- A/B done, no regression: go to judge (round 2, integration). Unproven lines: 2 (edge).
