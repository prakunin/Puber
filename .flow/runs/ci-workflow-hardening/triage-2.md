# Triage round 2: ci-workflow-hardening

Reviewed: 7a836616..cfa72862 (incremental). Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran, clean), review-antigravity-2.md (ran, clean); qa round 2 (qa-report.md): all ACs PASS, check.full green.
Round 2 rule: fix blockers and majors only. None found.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | No static test requires the verifier checkout at github.sha or the `signer-verifier/` script path; reverting fix #1 keeps tests green | edge F1, tests F1 | minor | accept | Real gap in the guard for triage-1 #1, workflow itself is correct (checked release.yml:106-118). Listed for the owner; about 10 lines in test_ci_workflows.py if wanted |
| 2 | Durable AC-8 check no longer asserts the 5 required secrets in "Validate release secrets" | tests F2 | minor | accept | Workflow unchanged there (AC-8 PASS by qa); test coverage gap only. Listed for the owner |
| 3 | Wider digest regex also matches a Source Stamp signer line | antigravity, edge unproven, codex rejected | minor | accept | Deliberate fail-closed trade-off of triage-1 #6; AGP assembleProdRelease adds no stamp |
| 4 | `total > 0` no longer catches a dropped PR checkout | tests unproven | - | reject | Hypothetical; no evidence |
| 5 | Release may publish APKs the check never saw | codex rejected | - | reject | Same as triage-1 #10 |

Deferred: none.
