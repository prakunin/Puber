# Review triage - round 3

Run: catalogue-filters-persist   Base: ef5987ec   Reviewed head: b0cee092 (incremental 18d72ea4..b0cee092)

Sources: review-edge-3.md (no findings), review-tests-3.md, review-codex-3.md (ran: review, adversarial), review-antigravity-3.md (ran, clean), review-state-path-3.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | state-path F1, codex | minor | `ShowAllScreenContent.kt:190-196` | The TabPick guard also suppresses «Сброс» on an arriving ShowAll; a return to a tab whose top is the filtered-empty grid lands on «Фильтры» | Read the effect: drawer-wide flag, no `bornDuringRequest` check unlike `ContentFocusHandoff.kt:108,115`; state-path trace holds | fix | regression of fix round 2; cheap: skip only on outgoing content |
| 2 | tests F1 | minor | `ShowAllScreenContent.kt:191-196` | No test covers the TabPick guard; the plan's "no host" reason is wrong (`ShowAllRailCloseFocusTest.RailWithShowAll`) | Read: host exists with real DrawerState and ContentFocusHandoff; `beginHandoff` is public | fix | outgoing-tab case in `ShowAllRailCloseFocusTest`; an arriving-tab case if the host can compose content during a request |

## Rejected codex claims

- Verify the tab-pick handoff on device - process point; qa-device runs the focus classes on the box after this round.

## Next

- Fix round 3 (items 1, 2) -> check.fast -> incremental review round 4 (edge, tests, codex, antigravity, state-path) -> qa-device: red/green of the focus tests, check.full; unproven lines: 3
