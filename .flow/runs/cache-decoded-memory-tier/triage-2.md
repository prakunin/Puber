# Review triage - round 2

Run: cache-decoded-memory-tier   Base: e9c383d0   Reviewed head: c93c20c6 (incremental 803329d9..c93c20c6)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial). Lens blind not re-run (incremental round). qa round 2 in qa-report.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests (edge unproven 1) | nit | `KinoPubApiClientDecodeDispatcherTest.kt:175` (`probeHttpClient()`) | Two new tests never close the Ktor/OkHttp client they build | Read the test: client built per test, no `use`/`close`; no result depends on it | accept | accepted: test-only resource left to JVM teardown, two clients per suite run; listed for the owner |

## Rejected codex claims

- none (codex clean)

## Next

- No blockers or majors; triage-1 fixes confirmed (c93c20c6 tests, qa round 2 red-on-base for AC-7). Go to judge. Unproven lines: 1 (edge).
