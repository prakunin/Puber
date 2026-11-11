# Test design: grid-focus-restore-after-back

Test designer, 2026-11-11, base e9184b79, updated after the device baseline (`qa-report.md#baseline`) after verify round 1 (`qa-report.md#round-1` D1, D2; `design-architect.md#addendum-after-verify-round-1` D14-D16) and after verify round 2 (`qa-report.md#round-2` D3; host-entry test dropped, state.md Decisions, verify cap). ACs: `brief.md#acceptance-criteria` with AC-7 amended (state.md Decisions, Design Q1) and the AC delta accepted (state.md Decisions, Design delta Q4; wording in `design-architect.md#ac-delta-proposal-for-the-analyst-owned-ac-section`). Risks: `design-architect.md#risks` and `#risks-delta`. Levels: `.flow/roles/test-designer.md`. Test names and steps: plan Tasks 1-4.

## Harnesses

| File (new unless noted) | Pattern reused |
|---|---|
| `app/src/test/kotlin/com/kino/puber/ui/feature/showall/content/GridFocusMemoryTest.kt` (existing) | `Harness`, `FakeFrameClock`; AC-7 case replaces `:241-249` |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/component/CollectionsScreenContentFocusTest.kt` | `ShowAllScreenContentFocusTest`: `StateRestorationTester.emulateSavedInstanceStateRestore()` (D9 seam); host box modelled on `MainScreenComponent.kt:296-302`, entered with `host.requestFocus()` after the restore |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContentFocusTest.kt` | Same harness; context menu by long OK as `TvContextMenuLongPressTest` |

Reentry tests start with `onNodeWithText("<first title>").requestFocus()`, then DOWN and RIGHT to a lower visible row. CollectionDetail "no landing" requests nothing after the restore (the baseline symptom: no focus).

## Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Parked reconcile request takes focus from the open context menu when `items` change (D2, seen in round 1) | 3 | 2 | 6 | `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`, Manual 7 |
| R1 an entry from an ancestor reaches the CollectionDetail grid before the first restore attempt | 2 | 3 | 6 | Covered by Manual 3 (device restores 6/6) and `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` only; the early-entry instrumented test is dropped (below) and deferred to idea run `collection-detail-early-entry-test`. Residual accepted by the owner (state.md Decisions, verify cap) |
| R1 residual: a direct request on a non-target card in the first frame is recorded as the position (`FocusTargetState.kt:156-161`) | 1 | 3 | 3 | Accepted: no producer in CollectionDetail or Bookmarks (D1); out of scope with `FocusTargetState.kt` |
| R2 CollectionDetail reloads through `Loading` after a Details change | 2 | 2 | 4 | Accepted: out of scope (state.md Design Q2); Manual 3 uses Details without changes |
| R8 Collections AC-1 rests on an unidentified host entry plus the grid restorer fallback | 1 | 3 | 3 | `collectionsHostEntryAfterReentryFocusesTheFocusedCard` (below the host), Manual 2 (whole chain); host call deferred (state.md Design delta Q3) |
| R9 an ancestor `requestFocus()` may not run the nested grid's `onEnter` in the test seam | 2 | 1 | 2 | Accepted: a red guard on base is a seam finding; Manual 2 governs AC-1 |
| R5 live fallback C takes focus the user moved away | 1 | 2 | 2 | Existing `reconcile_focusLeavingTheGridToTheRailThenReloadMakesNoRequest`; `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingWithContentInactiveNeverFocusesTheGrid` |
| Bookmarks and History regress (baseline: not affected) | 1 | 2 | 2 | Manual 5; `HistoryScreenContentTest` |
| R10 menu close refocuses the target card one frame later (D16); a menu action that leaves the screen must not be overtaken | 1 | 2 | 2 | Manual 7 observation; the request is cancelled when the composition is disposed |
| R7 same-file overlap with the two Show all runs | 2 | 1 | 2 | Accepted: sequencing, not a test |
| R6 recomposition per card focus in CollectionDetail | 1 | 1 | 1 | Accepted: same cost as Bookmarks today |
| R11 one `Content` recomposition per menu open and close (`LocalContentFocusActive` is static) | 1 | 1 | 1 | Accepted |

R3, R4 and the brief's `onLoadMore` risk are withdrawn with D10 (no Collections production change).

## Traceability

| AC | Level | Priority | Mark | Test | Proves |
|---|---|---|---|---|---|
| AC-1, AC-9 | instrumented | P0 | guard | `collectionsHostEntryAfterReentryFocusesTheFocusedCard` | Fails if the saveable index, its fallback requester or the grid `focusRestorer` (`CollectionsScreenContent.kt:58,59,69,75-88`) is removed or detached from the saved card |
| AC-2, AC-9 | instrumented | P0 | red | `collectionDetailFocusReturnsToTheFocusedCardAfterReentry` | Fails if CollectionDetail has no saveable focus memory or no requester on the target card (base: no focus) |
| AC-3 | instrumented | P0 | red | `collectionDetailFocusesTheFirstCardOnFirstLoad` | Fails if `rememberReconciledItemFocus` is not given the items or the requester sits on another card |
| AC-3 | instrumented | P0 | guard | `HomeCollectionBackFocusTest` (existing, both cases) | Fails if the CollectionDetail change breaks Home's opener restore |
| AC-5 | instrumented | P0 | guard | `collectionDetailItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Fails if the open menu does not park the card focus requests (`LocalContentFocusActive` provided as `outer && contextMenuItem == null`, D15); red at HEAD 5ea2899e |
| AC-7 | unit | P0 | red | `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid` | Expects `restore:6:false` x3 then `grid`; fails if the `pendingEntry` gate at `ShowAllScreenContent.kt:226` stays |
| AC-7 | unit | P0 | guard | Every other `GridFocusMemoryTest` case (existing) | Fails if D7 also fires the grid for a pending-free, non-removed state |
| AC-7 | unit | P1 | guard | `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingWithContentInactiveNeverFocusesTheGrid` | Fails if the `contentFocusActive()` part of the gate is dropped (R5) |
| AC-8 | instrumented | P0 | guard | `ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest`, `HistoryScreenContentTest` (existing) | Fails on any regression in Show all or History focus |
| AC-8 | manual | P0 | guard | Manual 1 (done) and 5 | Bookmarks and History behave as on base |
| AC-1, AC-6 | manual | P0 | guard | Manual 2 and 6 | Collections opener restore and rail close behave as on base |

AC-4: N/A, dropped (state.md Decisions, Design delta Q4).

### Dropped

| Test | Reason |
|---|---|
| `collectionDetailHostEntryBeforeTheFirstRestoreAttemptFocusesTheFocusedCard` | Dropped at the verify cap (qa-report.md Round 2 D3): red on base and on HEAD with the same timeout while the device restores 6/6 (Manual 3), so the seam does not reproduce the device and cannot tell R1 from a seam fault. R1 instrumented coverage deferred to idea run `collection-detail-early-entry-test` |
| `collectionDetailFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` | After verify round 1: a direct request on `Card 1` before the entry frame is a producer the device lacks; the failed redirect records `Card 1` as the position, so it stays red for a seam reason (D1, D14). The host-entry test that replaced it is dropped too (above) |
| `collectionsFocusReturnsToTheFocusedCardAfterReentry` | Replaced by the Collections guard: Collections restores on base (baseline, D10, D11) |
| `collectionsFocusMovesFromADefaultLandingToTheFocusedCardAfterReentry` | A direct request on the first card skips the grid's `onEnter`; red on base for a reason the device never shows (D11) |
| `restoreEntry_*` (7 unit cases) | No `CollectionsFocusMemory` class (D10) |
| `resolveCollectionRestoreId_*` (4 unit cases), `collectionsFocusReturnsToTheSameCollectionAfterPagesAppendBeforeReentry`, `collectionsFocusMovesToTheCardAtTheSameIndexWhenTheCollectionLeftBeforeReentry` | AC-4 dropped (D12) |
| `collectionsPendingRestoreDoesNotTakeFocusFromTheRail` | AC-5 Collections rail clause dropped |
| `collectionsRailCloseKeepsFocusOnTheLowerRowCard` | No Collections code change; AC-6 stays Manual 6 |

## Failing-first

`collectionDetailFocusReturnsToTheFocusedCardAfterReentry`, written at the plan's red checkpoint on unchanged code and run by qa-device through `make GRADLE=./tools/agentw DEVICE=<serial> itest TESTS=com.kino.puber.ui.feature.collections.detail.component.CollectionDetailScreenContentFocusTest`. Base trace: `git show e9184b79:app/src/main/java/com/kino/puber/ui/feature/collections/detail/component/CollectionDetailScreenContent.kt` lines 63-78: the grid has no `focusRestorer`, no requester and no saveable focus, so after the restore nothing is focused and `waitUntil` times out (device baseline: no focus after Back, 3/3 from both origins). The first red in plan order is `reconcile_liveGridFocusedCardRemovedAllAttemptsFailingFocusesTheGrid` (Task 1, unit, base gate at `ShowAllScreenContent.kt:226`).

Every `red` row runs on base first and must fail there; every `guard` row must pass there. A red `collectionsHostEntryAfterReentryFocusesTheFocusedCard` on base is a seam finding (R9) for the conductor, not a test to adjust.

## Manual checks

Emulator under a lease, `adb -s <serial> shell input keyevent`: DOWN 20, RIGHT 22, LEFT 21, OK 23, BACK 4. Settle 1 s between keys. Evidence per step: a screenshot showing the focused card. Each sequence 3 times. "LEFT (rail)" starts from a column-1 card (from column 2 LEFT only moves one column). Bookmarks and Collections tabs are enabled in Settings > Navigation for the run and disabled after.

| # | Build | Steps | Expected | Must not happen |
|---|---|---|---|---|
| 1 | base e9184b79 | Done in the baseline (`docs/superpowers/evidence/2026-11-11-grid-focus-restore-after-back-baseline.md`): Bookmarks and History, card, OK, BACK; column-1 card, LEFT (rail), RIGHT | Same card focused on both screens | N/A |
| 2 | worktree APK | Collections tab: DOWN x3 (scrolls), RIGHT, OK (CollectionDetail), BACK | Same collection card focused, viewport unchanged, as on base (AC-1 guard) | First visible card focused; no focus |
| 3 | worktree APK | Open CollectionDetail from Home, DOWN x2, RIGHT, OK (Details, no watch/bookmark change), BACK. Repeat opening from Collections | Same card focused, both origins (AC-2); base had no focus | First card focused; no focus; focus inside the title text |
| 4 | worktree APK | Fresh CollectionDetail (from Home and from Collections) | First card focused on load; BACK returns focus to the Home or Collections opener (AC-3) | Focus stays on the rail or Home first row |
| 5 | worktree APK | Repeat 1 on Bookmarks and History; Show all: card, OK, BACK, and LEFT (rail)/RIGHT | Same as Manual 1; Show all keeps the opener (AC-8) | Any difference from base |
| 6 | worktree APK | Collections: column-1 card in a lower row, LEFT (rail), wait 2 s, RIGHT | Same card focused, as on base (AC-6) | Row shift; no focus |
| 7 | worktree APK | CollectionDetail after Manual 3's BACK: long OK on a card (context menu), wait 2 s, BACK | Menu keeps focus (AC-5); after BACK closes it, the opener card is focused (observation, D16, R10, not an AC) | Focus jumps behind the dialog; after close, no focus or another card focused |

## UNTESTABLE

None.
