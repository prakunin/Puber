# Gate - showall-rail-close-focus-shift - round 1

**Decision:** CONCERNS
**Head:** 134c5032   **Base:** 6b087697

## Done as asked

The diff matches brief.md Plan 1-4 with fix shape A. `ShowAllScreenContent.kt:171-177` latches `pendingRequestId` (the same pattern as `FocusTargetState.kt:282-289`). `GridFocusMemory.onHandoff` (`:234-241`) checks `contentFocusActive`, `inGrid`, the null memory and whether the id was already answered, then uses `resolveRestoreId` and the existing `requestRestoreFocus`. `pendingEntry` and the shared drawer and handoff contract are unchanged. Outside the scope rules: no `focusRestorer()` on the grid, no change to History or CollectionDetail, no change to first entry with a null memory.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `showAllRailCloseKeepsFocusOnTheLowerRowCard`; D1 | qa-report.md R1; evidence file D1, 3/3, base 3/3 one row up | PASS |
| AC-2 | `showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling`; D2 | qa-report.md R2 5/5; evidence D2 3/3 each | PASS |
| AC-3 re-pick | D3b | evidence D3b 3/3 | PASS |
| AC-3 Back | D3a | Back with the rail open pops Show all, same as base (evidence D3a). The AC cannot be reached on this screen | NOT CHECKED |
| AC-4 | `onHandoff_withoutARememberedCardMakesNoRequest`; `showAllRailCloseWithoutMemoryBehavesAsBase` (guard); D4 | qa-report.md R1, R2 | PASS |
| AC-5 | T-U3, T-U4, T-U5 unit; `showAllHandoffResponseDoesNotStealFocusFromADialog`; D5a-c | qa-report.md R1, R2; `showAllRecompositionAfterSettleDoesNotRefocus` is a smoke check only (triage-2 #1) | PASS |
| AC-6 | `onHandoff_rememberedCardDroppedWhileTheRailWasOpenRequestsTheNeighbour` | unit only by design (test-design UNTESTABLE) | PASS |
| AC-7 | 8 new `GridFocusMemoryTest` cases | check.full re-run by the judge at 134c5032: BUILD SUCCESSFUL | PASS |
| AC-8 | `ShowAllScreenContentFocusTest` 5/5; D6; diff has no History files | qa-report.md R2; `git diff --name-only` | PASS |
| AC-9 | `ShowAllRailCloseFocusTest` | red on base `expected:<Card 16> but was:<Card 13>`, green on HEAD (evidence file) | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-----------|
| Handoff is marked answered before the restore succeeds | review-blind-1 F1 (minor) | rejected, accepted by the judge | Content is inactive only while the drawer is Open (`MainScreenComponent.kt:138`), and every close of an open rail starts a new id (`ModalNavigationDrawer.kt:129-131`) |
| Missing T-I1b scrolled case, T-I2, T-I3, T-I4 | review-tests-1 F2, F3 | fixed | e9595021 |
| AC-3 Back half cannot be reached | review-tests-1 F1 | rejected as a code finding, carried as NOT CHECKED | Accepted: evidence D3a. Rewording the AC is for the owner |
| T-I4 cannot fail when the handoff answer fires again | review-tests-2 F1 (major) | fixed (test relabelled as a smoke check; the latch is covered by T-I3 and T-U4) | 134c5032 |
| T-I2 not labelled as a guard | review-tests-2 F2 | fixed | 134c5032 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One `remember` state and one `LaunchedEffect` keyed on the latched id. The effect runs only on a new id or an active flip; no work on hot paths |
| Reliability | A null memory is a no-op, the content-inactive and `inGrid` guards hold, and each id is answered once. A missed request falls back to the blind landing, as on base (brief Risks) |
| Maintainability | Reuses the existing latch pattern and `requestRestoreFocus`; no duplicated helper and no dead code |
| Security / privacy | No secrets. The evidence file holds only public catalogue titles and passed `mobile-evidence-audit.sh` (qa-report.md R1) |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

1. AC-3 "Back closes the rail" is NOT CHECKED. On Show all, Back with the rail open pops the screen, the same as on base, so the check cannot be done here. It does not depend on the device. Reword AC-3 to cover re-pick only, or accept it as is.
2. Tab re-pick (AC-3) has device evidence only. Optional T-I6 was not written, so a regression on `MainScreenComponent.kt:272` would not make a test fail.
