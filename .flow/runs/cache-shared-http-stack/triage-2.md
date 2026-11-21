# Review triage - round 2

Run: cache-shared-http-stack   Base: 88fa7303   Reviewed head: e3a8cdb3 (incremental 780aa9ca..e3a8cdb3)

Sources: review-edge-2.md (no findings), review-tests-2.md (1 minor), review-codex-2.md (ran: review, adversarial; clean). qa round 2 (qa-report.md): check.full green at e3a8cdb3; AC-1, AC-2, AC-4, AC-7 PASS on device; evidence 63dc038e.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | minor | `PuberApp.kt:135` | The Coil fetcher lambda in PuberApp is not under test; the Koin single it reads is | Read: one line, get<HttpClient>(ImageHttpClientQualifier) | accept | accepted: round 2 minor; device evidence round 2 shows image lookups going through DoH on the shared stack (image.dns counted under DoH); listed for the owner |

## Rejected codex claims

- none

## Next

- No fixes, go to judge. Unproven lines: 2 (edge 1, tests 1).
