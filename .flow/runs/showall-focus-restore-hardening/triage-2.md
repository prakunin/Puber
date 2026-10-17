# Review triage - round 2

Run: showall-focus-restore-hardening   Base: 35bfbbec   Reviewed head: cc66ec96 (increment d6b51a2e..cc66ec96)

Sources: review-edge-2.md (no findings), review-tests-2.md, review-codex-2.md (ran: review, adversarial; clean). qa on affected items (qa-report.md "Review round 1 fixes", evidence 3b0aeb04): GridFocusMemoryTest 23/23, ShowAllScreenContentFocusTest 5/5 on device, D-M1, D-M2 and the rail open/close regression check PASS. Open observation (rail close lands one row above) compared with base (qa-report.md "Base comparison: rail close focus"): base 35bfbbec shifts the same way 3/3, so it is pre-existing.

Triage-1 rows #1-#3 confirmed fixed by edge, tests, codex and qa (cc66ec96).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | minor | `ShowAllScreenContent.kt:166`, `ShowAllScreenContentFocusTest.kt:152-172` | No test fails if the new `contentFocusActive` effect key is reverted; an instrumented sibling of the inactive-entry test could pin it | Read the JVM case (calls `reconcile()` directly) and the itest; holds; behaviour checked by edge-2 reading and device rail checks | defer | Review cap reached; folded into showall-entry-restore-foreign-landing (same code path, its Log carries the item) |
| 2 | qa observation | minor | Show all rail close | Closing the rail lands focus one row above the card that opened it | Base comparison: same on base 3/3; not caused by this run | defer | New run showall-rail-close-focus-shift |

## Next

- Review cap reached; no fixes this round. Judge with #1-#2 deferred and listed for the owner.
