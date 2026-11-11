# Review triage - round 1

Run: collection-detail-inplace-refresh   Base: 0e4449e2   Reviewed head: a021e92c

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (ran: review, adversarial; 2 confirmed). ux-designer not run: no layout change.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, edge F1 | major | `FocusTargetState.kt:81-83,127,241-248` | `requestTargetId` lets a pending request override the target; in a non-target row the pending entry is never cleared (effect keyed without `isTargetRow`), so the requester stays on a stale card on Home/SectionRow/VideoGrid rows | Read the diff: `requestTargetId = pendingFocus.itemId ?: targetItemId` is returned as the state's target; the clearing effect runs only `if (isTargetRow && ...)` and is keyed on `pendingFocus.value, contentFocusActive` | fix | architect redesigns Task 3 (addendum 2), implementer fix |
| 2 | codex major, edge unproven | major | `FocusTargetState.kt:102,208` | `resolvedItemId` captured at composition from the old `focusedItemId` undoes `ReconcileInitialItemFocusEffect` when `initialFocusedItemId` and `items` change together (EpisodesPanel / Details episode auto-advance) | Read the diff: base recomputed `resolveFocusedItemId(..., focusedItemId.value)` inside the effect, after the initial-focus effect wrote it; the new code writes the composition-time value | fix | same architect addendum; add a guard test changing both values together |
| 3 | blind F2 | minor | `FocusTargetState.kt` | shared change with no test on another caller for the item-removed case | qa round 2 ran 5 regression classes green, none covers item removal + retry on a non-CollectionDetail screen | fix (with #1/#2) | architect decides between scoping the change to opt-in callers and a guard test on a Home row |
| 4 | codex minor | minor | `CollectionDetailVM.kt:75` | `savedSinceLastPublish` expires per batch: a settled save of A stays in the set while B is pending, masking a Details change to A | Read `loadItems()`: clear only if no mutation is active at all | fix (cheap) | track the ids of active saves; keep only those at load start; unit test A settled, B pending, server change to A |
| 5 | tests F1 | minor | `CollectionDetailVM.kt:79`; `CollectionDetailVMTest` | No test fails if `awaitPendingMutations()` is removed | Followed the reviewer's trace through `publishItems` patch: the patch masks the missing await in all AC-6 tests | fix (cheap) | strengthen `toggleDuringRefresh_keepsSavedValueWhenSaveSettlesAfterFetch` as suggested; test-design "Proves" updated |
| 6 | tests F2 | minor | `firstLoad_publishesLoadingBeforeContent` | test cannot see the Loading guard (StateFlow dedups the initial Loading) | initialViewState is Loading (VM:32) | fix (doc) | test-designer restates the row's claim |
| 7 | tests F3 | minor | AC-2 player clause, Manual 3 | not run: playback writes watch progress to the account | AGENTS.md: playback/account mutations need the owner's explicit authorization; the standing instruction does not cover it | reject (as a code finding) | carried to the gate as NOT CHECKED; owner decides |

## Rejected codex claims

- none

## Next

- Architect addendum 2 (rows 1-3), then implementer fix (rows 1-5), test-designer (rows 5-6), check.fast, qa on AC-7 + regression classes, incremental review (edge, codex, tests). Unproven lines: 5.
