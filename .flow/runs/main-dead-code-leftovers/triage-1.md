# Triage round 1: main-dead-code-leftovers

Reviewed head: c8799377 (base 55bf932d). Sources: review-blind-1.md, review-tests-1.md, review-codex-1.md (ran), review-antigravity-1.md (ran, clean), review-state-path-1.md (clean). qa round 1: qa-report.md.
Each finding was checked against the code by the conductor.

| # | Finding | Sources | Severity | Verdict | Reason / action |
|---|---------|---------|----------|---------|-----------------|
| 1 | After PagingColumn is gone, PagingListState and PagingListPageError have no users outside their own file | blind (unproven) | minor | fix | Confirmed by the conductor: `grep -rnw "PagingListState\|PagingListPageError" app baselineprofile` (excluding generated profiles) hits only PagingListState.kt itself. Delete the file; cheap and inside the task's goal |
| 2 | Evidence cold-launch row says PASS but shows only a black frame at 8 s before HOME | codex | minor | fix | Evidence wording; qa marks the row inconclusive or adds a pre-HOME screenshot |
| 3 | remember(vm) key is a runtime no-op covered only by the smoke | tests (unproven) | - | reject | Intended: the change aligns Main with the other screens; no behaviour to test |

Deferred: none. Accepted: none.
