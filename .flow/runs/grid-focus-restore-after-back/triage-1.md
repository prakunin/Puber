# Review triage - round 1

Run: grid-focus-restore-after-back   Base: e9184b79   Reviewed head: cfd0a1f3

Sources: review-blind-1.md, review-edge-1.md (no findings), review-tests-1.md, review-codex-1.md (ran: review, adversarial; clean). ux-designer not run: no layout change.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1 | major | `CollectionDetailVM.kt:69-70,98-101`; `CollectionDetailScreenContent.kt:52-65` | Back with a content change reloads through `Loading`, the Content branch is disposed, focus lands on the first card and the grid scrolls to the top | Read `onReturnedContentChanges` -> `loadItems()` -> `updateViewState(Loading)`; same as design R2 / Design Q2, out of scope by owner decision | defer | existing idea run `collection-detail-inplace-refresh` (Log note added); no AC covers it |
| 2 | blind F2 | minor | `CollectionDetailScreenContent.kt:59-65` | First card focused on open; the target is re-requested after the menu closes | First-load focus is AC-3 (approved); re-request after close is D16 / R10, Manual 7 PASS 6/6. The navigating-action variant has no evidence (edge marks it unproven too) | reject | intended by AC-3 and D16; navigating case unproven |
| 3 | tests F1 | minor | evidence `...-device.md:69`; AC-8 | `HistoryScreenContentTest` 20/30 marked PASS without a base run | qa-report Round 1/2: no base run of the class recorded | fix (cheap, evidence) | qa-device runs the class on e9184b79 and HEAD and records both |
| 4 | tests F2 | minor | `CollectionDetailScreenContentFocusTest.kt` AC-5 test | Menu close refocusing the card (D16) has no automated check | Read the test: it stops at the open-menu assertion | fix (cheap) | implementer extends the AC-5 test: BACK closes the menu, the neighbour card is focused |

## Rejected codex claims

- none (codex clean)

## Next

- Fix round 1: implementer extends the AC-5 test (tests only); qa-device runs CollectionDetailScreenContentFocusTest on HEAD and HistoryScreenContentTest on base and HEAD; incremental review: lens tests, edge and codex on `git diff cfd0a1f3`. Unproven lines: 7 (blind 2, tests 3, edge 3, minus 1 overlap).
