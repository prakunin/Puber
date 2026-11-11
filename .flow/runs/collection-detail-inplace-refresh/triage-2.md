# Review triage - round 2

Run: collection-detail-inplace-refresh   Base: 0e4449e2   Reviewed head: 48ff9085 (incremental a021e92c..48ff9085)

Sources: review-edge-2.md (no findings), review-tests-2.md, review-codex-2.md (ran: review, adversarial; clean). qa (review fix round 1): check.full pass; both new VideoGrid guards red on the a021e92c helper and green on HEAD (9/9); CollectionDetailScreenContentFocusTest 5/5 x3; helper regression classes green; Manual 1-2 PASS 4/4; HomeSectionRemovalFocusTest one test red as before (pre-existing, run home-section-removal-focus-test-red).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | minor | `FocusTargetState.kt` `requestPending`; test-design R9 | The retry gate for default callers has no test; test-design claims the untargeted-row guard covers it | Reviewer's trace: the requester already sits on the focused card, so the first request succeeds and retry vs single request is indistinguishable | accept | round-2 minor; the claim is a doc overstatement, listed for the owner at handover (the retry gate for default callers is untested) |
| 2 | tests F2 | nit | plan Implementation record, Task 4 | red-run line cites `:200`, the assertion is at `:212` | reviewer read the file at 48ff9085 | accept | round-2 nit; corrected at squash if cheap |

## Rejected codex claims

- none (codex clean; its note about uncommitted reverts was qa's red check, restored, worktree clean)

## Next

- No fixes; go to judge. Unproven lines: 0.
