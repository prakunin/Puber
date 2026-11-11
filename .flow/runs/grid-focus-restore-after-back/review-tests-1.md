LENS: tests  ROUND: 1  BASE: e9184b79

### F1 [minor] AC-8 says `HistoryScreenContentTest` is green, but it is 20/30 and was never run on base
- Where: /home/user1/orca/workspaces/Puber/grid-focus-restore-after-back/docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-device.md:69
- Problem: AC-8 names `HistoryScreenContentTest` among the suites that must be green. The verify rounds recorded 10 failures. They were put down to hard-coded Russian strings on an en-US device. No base run of the class is recorded, so the claim that the failures already existed before this change is not backed by evidence. The diff touches no History code, so a regression is unlikely. Still, the AC is marked PASS on a red suite.
- Failure scenario: a History focus regression in those 10 cases would look the same as the "locale" failures and would be accepted the same way.
- Fix: run the class on e9184b79 on the same device and record the same 10 failures in the evidence. Or run it under a ru locale, or reword AC-8 to "same result as base".

### F2 [minor] No automated test for the new behaviour where closing the menu refocuses the card (D16, R10)
- Where: /home/user1/orca/workspaces/Puber/grid-focus-restore-after-back/app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt:54-57
- Problem: the D15 override (`contentFocusActive && contextMenuItem == null`) makes closing the menu re-run the entry request and the parked request. That is new user-visible behaviour: the target card takes focus one frame after close, and on base nothing was requested. Only one Manual 7 observation covers it. `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` checks only the open-menu half.
- Failure scenario: a later change to the override or to the helper leaves focus nowhere, or on the wrong card, after BACK closes the menu (with or without an item change). No test fails.
- Fix: extend the AC-5 test, or add one. Press BACK to close the menu and assert that the neighbour card (after the removal) or the opener card is focused.

Other checks:
- Suite state: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` gives BUILD SUCCESSFUL on the current worktree HEAD (cfd0a1f3).
- Red runs: every P0 `red` row has a red run on unchanged code in the Implementation record and in `qa-report.md` (Red checkpoint and round 2 base column):
  - `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid`
  - `collectionDetailFocusReturnsToTheFocusedCardAfterReentry`
  - `collectionDetailFocusesTheFirstCardOnFirstLoad`
- Guard rows: they are marked `guard` and were green on base.
  - The AC-5 menu test was red at 5ea2899e and green after D15, so it does pin D15.
  - The P1 `...WithContentInactiveNeverFocusesTheGrid` case would go red if the `contentFocusActive()` part of the gate at `ShowAllScreenContent.kt:226` were dropped: frame 3 turns content inactive, `requestRestoreFocus` returns false, and the gate is the only thing that stops `grid`.
  - The Collections guard would fail without the grid `focusRestorer` fallback: a default enter after the restore lands on `Collection 1`.
- Weakened tests: the one changed assertion (`GridFocusMemoryTest.kt:248`) is the owner-approved AC-7 amendment. The commit that drops the host-entry test (f7d887a7) removes only that test.
- Untested ACs: none without a test or a documented device check. AC-1 and AC-6 rest on device checks (Manual 2 and 6). AC-4 was dropped.

### Unproven
- `CollectionDetailScreenContent.kt:54` `outer` content-focus-inactive branch (rail open over CollectionDetail): no test - CollectionDetail is a root screen outside the drawer (spec root cause #6), so it needs a caller that renders it under an inactive `LocalContentFocusActive`.
- `CollectionDetailScreenContent.kt:58-64` an entry from an ancestor before the first restore frame (R1): no instrumented test since the host-entry test was dropped. The owner accepted this and deferred it to `collection-detail-early-entry-test`. It needs a device sequence where this ordering loses the card.
- `CollectionDetailScreenContent.kt:110` a menu action that leaves the screen being overtaken by the restore one frame after close (R10): no test - it needs a menu action that navigates without disposing the `Content` composition.
