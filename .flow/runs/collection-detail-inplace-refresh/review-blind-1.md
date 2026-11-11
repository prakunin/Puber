LENS: blind  ROUND: 1  BASE: 0e4449e2

### F1 [major] A pending focus request that never ran now takes over the focus target in rows that are not targeted (affects Home, Bookmarks, SectionRow and VideoGrid)
- Where: app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt:81-83, 127 (with 207-215 and 241-247)
- Problem: `requestTargetId` prefers `pendingFocus.value.itemId` over `targetItemId` whenever that id is still in `items`. Three things combine:
  - `ReconcilePublishedItemsEffect` sets `pendingFocus` for every row whose remembered card leaves, whether or not the row is targeted.
  - Only the request effect clears `pendingFocus`, and only when `isTargetRow && canRequestFocus`.
  - That effect is keyed on `pendingFocus.value` and `contentFocusActive`, not on `isTargetRow`. So the row becoming targeted later does not re-run it, and the pending entry stays.

  Before this diff a stuck pending entry did nothing to the card target. Now it pins `ReconciledItemFocusState.targetItemId` to a stale card. Three things follow that target:
  - which card holds the `focusRequester` (the `focusRestorer` fallback);
  - where the first focus after composition is sent back to (`onItemFocused`);
  - whether `onRootFocusRestored` fires.

  The component is shared by Home, Bookmarks, SectionRow and VideoGrid. The diff only meant to change CollectionDetail.
- Failure scenario:
  1. On Home the user opens a card from the Recommendations row and finishes it.
  2. On return the item leaves Continue Watching, which is not the targeted row. That row's pending entry becomes neighbour Y and is never cleared.
  3. The user moves into Continue Watching and presses Right to card Z. `focusedItemId` becomes Z, but `requestTargetId` stays Y.
  4. The user scrolls the row out of the lazy column and back. The first focus is sent to Y instead of Z, and `onRootFocusRestored` fires only for Y.
- Fix: Do not let `pendingFocus` override `targetItemId` in the returned state. If the gap between publish and request needs covering, use the pending id only while `isTargetRow` is true. Also clear `pendingFocus` (or re-key the request effect on `isTargetRow`) so it cannot outlive the request it was created for.

### F2 [minor] Shared focus code changed without a stated reason, and CollectionDetail tests do not cover the other screens
- Where: app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt:203-208, 245, 303-311
- Problem: The diff changes `ReconcilePublishedItemsEffect` in two ways:
  - It now uses the `resolvedItemId` captured at composition time instead of recomputing it inside the effect.
  - The pending request now retries `requestFocus()` for up to 3 frames (`requestUntilFocused`) instead of one attempt (`requestAfterComposition`).

  Both changes apply to every screen that uses `rememberReconciledItemFocus`. The new tests cover only `CollectionDetailScreenContent`.
- Failure scenario: The diff contains no test on Home or Bookmarks for either change. If the retry or the captured id misbehaves on a screen without `requestAfterFrame`, nothing goes red.
- Fix: Add a guard test for the item-removed case on one Home or Bookmarks row. Otherwise, scope the retry to callers that opt in, as `requestAfterFrame` already does.

### Unproven
- FocusTargetState.kt:303-311: the retry loop can move focus back after the user has already moved it, if the target card attaches in frame 2 or 3 after the user pressed D-pad in frame 1. This becomes real if a device trace shows focus jumping back after a quick press right after a list refresh.
- CollectionDetailVM.kt:76-78: cancelling a fetch shows an error message if `interactor.getCollectionItems` wraps the cancellation in an exception that is not a `CancellationException`. This becomes real if the HTTP client is shown to rethrow cancellation as an IO exception.
