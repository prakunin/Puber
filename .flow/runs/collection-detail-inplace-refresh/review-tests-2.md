LENS: tests  ROUND: 2  BASE: a021e92c

### F1 [minor] No test covers the retry gate, but test-design says one does
- Where: `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt` at 48ff9085 (`retryPendingRequest = holdPendingFocusTarget`, `requestPending`); `/mnt/data/projects/PetProjets/Puber/.flow/runs/collection-detail-inplace-refresh/test-design.md` R9 and the `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` row.
- Problem: test-design says `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` "fails if the requester gate (`requestTargetId`) or the retry gate is missing". The retry path only runs from the pending effect when `isTargetRow` is true. When `active` comes back, the effect runs in the `bottom` row. At HEAD, the requester there is already attached to `Item 13`, the card that is still focused. Its first `requestFocus()` therefore succeeds, so `requestUntilFocused` and `requestAfterComposition` produce the same result. The other guard, `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow`, never reaches the pending effect with a failing first request either. The Home and SectionRow regression classes were green on cad9f616, where the retry was unconditional, so they do not detect it either.
- Failure scenario: a later edit passes `retryPendingRequest = true`, or drops the gate inside `requestPending`. Every caller then retries for up to 3 frames again, which is the R11 D-pad override that triage-1 row 3 removed from callers other than CollectionDetail. Every listed test stays green.
- Fix: the cheap fix is to remove "or the retry gate" from that row and from R9, and record the retry gate for default callers as untested or accepted. A real guard needs a default-caller scenario where the first `requestFocus()` fails and a key press lands inside the retry window. That costs more and is optional.

### F2 [nit] The Task 4 red-run record cites the wrong line
- Where: `docs/superpowers/plans/2026-11-11-collection-detail-inplace-refresh.md`, Implementation record, Task 4.
- Problem: the record says the strengthened test "fails at the `[42]` ids assertion (`CollectionDetailVMTest.kt:200`)". At 48ff9085, line 200 is the `fun` declaration. The assertion is at `:212`.
- Failure scenario: anyone checking the red evidence lands on the wrong line.
- Fix: change `:200` to `:212`.

Checked and sound:
- **Strengthened test (triage row 5).** If `awaitPendingMutations()` is removed, `[42, 43]` is published while the save is still pending, and the assertion at `:212` fails.
- **`settledToggleWhileAnotherSaveIsPending_showsServerSavedValue` (row 4).** It fails on the A1 rule, because 42 stays patched to true. It also fails if the `removeAll { !isActive }` pruning is missing. If load start clears the set unconditionally, card 43 shows false and it fails too. If `saveJobItemIds[job] = item.id` is missing, `toggleBeforeFailedRefresh_keepsSavedValueOnNextRefresh` fails instead.
- **`changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow` (row 2).** It reaches the effect-resolve gate: `items` changes, so `LaunchedEffect(items)` runs again.
- **`removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` (row 1).** It reaches the `requestTargetId` gate. The `bottom` row becomes the target after Down, and turning `active` back on runs the pending effect with the stale id 12.
- **Row 6 doc fix.** It is in place.

No build or test was run, as the brief asked. Two things are still open:
- The red runs of the two instrumented guards on the a021e92c helper are not in `qa-report.md` yet (Task 4 handoff row). Both guards are P1 `guard`, so this is not a P0 rule finding.
- The worktree currently has uncommitted changes to `FocusTargetState.kt` and `CollectionDetailScreenContent.kt`, which look like the a021e92c checkout from that handoff row. I reviewed the committed 48ff9085 through `git show`.
