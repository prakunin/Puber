# Gate - grid-focus-restore-after-back - round 1

**Decision:** CONCERNS
**Head:** 5b020264   **Base:** e9184b79

## Done as asked

The diff changes two production files. `CollectionDetailScreenContent.kt` adopts `rememberReconciledItemFocus` with a target requester, a grid `focusRestorer` and `onFocusChanged` (spec D1), and holds `LocalContentFocusActive` false while the context menu is open (D15). `ShowAllScreenContent.kt:226` gains `|| focusedCardLeft` (D7). Collections gets a test only (D10, D11). Bookmarks, History, Search, `FlowComponent` and the shared helpers are untouched, which matches Out of scope. Production is unchanged since 3e1530f0 (`git diff --stat 3e1530f0..HEAD -- app/src/main` is empty), so the round 2 device evidence applies to HEAD.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `collectionsHostEntryAfterReentryFocusesTheFocusedCard`; Manual 2 | device.md Round 1 (3/3); Collections code unchanged | PASS |
| AC-2 | `collectionDetailFocusReturnsToTheFocusedCardAfterReentry`; Manual 3 from both origins | device.md Round 2: base FAIL, HEAD PASS; manual 6/6 at 3e1530f0 | PASS |
| AC-3 | `collectionDetailFocusesTheFirstCardOnFirstLoad`, `HomeCollectionBackFocusTest`; Manual 4 | device.md Round 2: base FAIL, HEAD PASS; 2/2; manual 6/6 | PASS |
| AC-4 | Dropped (design D12) | N/A | N/A |
| AC-5 | `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` (BACK-close extension in 4efeed73); Manual 7 | device.md Review fix round 1: 3/3 twice on HEAD; manual 6/6 | PASS |
| AC-6 | Manual 6 | device.md Round 1 (3/3); Collections code unchanged | PASS |
| AC-7 | `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid`, `...WithContentInactiveNeverFocusesTheGrid` | check.full re-run by the judge on 5b020264: BUILD SUCCESSFUL; red on base per the Implementation record | PASS |
| AC-8 (Bookmarks, History, Show all automated) | Manual 5; `ShowAllScreenContentFocusTest` 5/5, `ShowAllRailCloseFocusTest` 5/5; `HistoryScreenContentTest` | device.md: manual 3/3 each; History 20/30 on both base and HEAD with the same 10 failures | PASS (as on base; the suite named "green" is not green on base either) |
| AC-8 (Show all manual) | Manual 5, Show all | device.md Round 1: no Show all card reached on Home | NOT CHECKED |
| AC-9a | The two CollectionDetail red tests | Red on base, green on HEAD (device.md Round 2). The host-entry test was dropped (owner decision, state.md 2026-11-11) | PASS |
| AC-9b | `collectionsHostEntryAfterReentryFocusesTheFocusedCard` | Green on base (qa-report Red checkpoint) and on HEAD | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Reload through `Loading` after a Details change drops focus and scroll | blind F1 (major) | Deferred | Idea run `collection-detail-inplace-refresh`, Log line from review round 1 |
| First card is auto-focused; the target is re-requested after the menu closes | blind F2 (minor) | Rejected | Accepted: first-load focus is AC-3; refocus on close is D16 (design-architect.md:168), Manual 7 6/6 |
| `HistoryScreenContentTest` 20/30 marked PASS without a base run | tests-1 F1 (minor) | Fixed | 5b020264: the same 10 failures on base and HEAD |
| No automated check that closing the menu refocuses a card | tests-1 F2 (minor) | Fixed | 4efeed73; 3/3 twice on HEAD |
| No test for closing the menu when the list did not change | tests-2 F1 (minor) | Accepted | Manual 7 covers it 6/6; too small for a run |
| D1/D3 host-entry test red on base and HEAD | qa Round 1, Round 2 | Dropped and deferred | f7d887a7; idea run `collection-detail-early-entry-test` |
| Bookmarks has the same exposure to the menu focus loss (D2) | design addendum, Open question 5 | Deferred | Idea run `bookmarks-context-menu-focus-loss` |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Reading `targetItemId` in composition recomposes the grid on each card focus, as Bookmarks already does (design R6). Nothing else is added to the main thread. |
| Reliability | Empty list: no requester is attached. Error to Content: fresh first-card focus. Process death: `rememberSaveable` id. All checked by the edge lens. A navigating menu action that triggers a refocus one frame after close is unproven (edge, R10). |
| Maintainability | Reuses the existing helper with the Bookmarks wiring. No new helper, no dead code. Tests follow the existing `StateRestorationTester` pattern. |
| Security / privacy | No secrets or account data in the diff. `mobile-evidence-audit.sh` passed on the evidence directory (45 files). Screenshots were kept out of Git. |

## Manual checks for the owner

1. Show all after Back (AC-8). Use emulator-5554 with the worktree APK, installed with install-preserve. On Home, open a row's "Show all". Move DOWN twice and RIGHT once to a card in a lower row, press OK, then BACK. Expect the same card to be focused. Then go to a column-1 card, press LEFT (the rail opens), then RIGHT. Expect the same card to be focused.

## Must fix

none

## Concerns for the owner

- Accepted risk R1: a host entry that arrives before the first restore frame has no instrumented coverage. The dropped test was red on base and on HEAD, while the device restored the card 6/6. Tracked by `collection-detail-early-entry-test`.
- AC-8 lists `HistoryScreenContentTest` as "green", but 10 of 30 fail on base and on HEAD alike (hard-coded Russian strings on an en-US device). The AC is met only as "behaves as on base".
- CollectionDetail still loses focus and scroll when Details reports a change (blind F1). This stays open until `collection-detail-inplace-refresh` lands.
