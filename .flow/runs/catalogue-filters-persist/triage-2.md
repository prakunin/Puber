# Review triage - round 2

Run: catalogue-filters-persist   Base: ef5987ec   Reviewed head: 18d72ea4 (incremental c205c62e..18d72ea4)

Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (ran: review, adversarial), review-antigravity-2.md (ran, clean), review-state-path-2.md (no findings). Blind not re-run (incremental round).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | edge F1 | minor | `ShowAllScreenContent.kt:190-195` | The empty-state «Сброс» request re-fires on the outgoing tab when content focus turns back on for a tab-pick handoff, and can settle the handoff on a node about to be disposed | Read the effect: keyed on contentFocusActive, no check of `LocalContentFocusHandoff.current?.expectsNewContent` (ContentFocusHandoff.kt:67,115 guards the same case for the handoff effect) | fix | regression introduced by fix round 1 (triage-1 #1); cheap; fixed by skipping while a handoff expects new content |
| 2 | tests F1 | minor | `ShowAllFilterFocusTest.kt:140-170` | New instrumented cases not yet run red/green | Plan record says compiled only | fix (verification) | qa-device red runs on the box after this round |
| 3 | tests F2 | nit | `ShowAllFilterFocusTest.kt:155` | `panelApplyThatPagesEmpty_...` closes with Back, never applies | Read the test | fix | renamed with the item 1 commit (cheap, same file) |

## Rejected codex claims

- Panel-apply test misses a fast empty result during the close - headerHoldsFocus latches before the panel can open; no failing sequence (state-path round 2 traced the same path clean).

## Next

- Fix round 2 (items 1, 3) -> check.fast -> qa-device on the box: red runs of the three focus tests, green class -> incremental review round 3 (edge, codex, antigravity, tests) on 18d72ea4..HEAD; unproven lines: 3
