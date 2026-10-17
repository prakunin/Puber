CODEX: ran: review, adversarial (increment 59cdb9c3..b5919b85)

CONFIRMED:
- [minor] app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:198-202 - `restoreWhenFocusedCardLeft` only reconciles when `inGrid` is true, and `inGrid` starts false on re-entry. `restoreOnEntry` retries for `RESTORE_FOCUS_ATTEMPTS` frames against a `restoreId` fixed at composition. Scenario:
  1. The user opens a card, and the details screen returns a non-empty `ContentChangeSet`.
  2. `onReturnedContentChanges` calls `refreshFirstPage` and drops the opener from the list.
  3. The new list arrives within those first frames, before the entry request succeeds.
  4. The effect keeps requesting a card that no longer exists, then gives up after the last attempt.
  5. `inGrid` is still false, so the neighbour fallback never runs, and focus is lost.

  I read the VM, and `onReturnedContentChanges` does start an async refresh with the cache invalidated. The window is only about 3 frames, so the network response normally arrives after entry focus has succeeded and set `inGrid`. That makes it rare, not a blocker. A fix is to track pending entry restoration separately from `inGrid`, or to re-resolve `restoreId` on `items` changes until the entry restore succeeds. Neither new test covers removal after recreation and before the first restore request.

REJECTED:
- Plain `review` pass reported nothing; it said instrumented focus tests were not run. That is a note, not a defect. Nothing to reject.
- Adversarial recommendation to ship nothing until the pending-entry race is fixed ("Do not ship yet", medium) - I confirmed the defect but re-graded it from medium to minor, per the narrow window above. It also only costs focus on one grid after a content change.
- Codex ran the review statically and did not run the focus tests. I also did not run them (no device or lease), so the runtime behaviour of the new tests is unverified by this pass.

VERDICT: changes needed (one minor; the conductor may defer it if the owner accepts the 3-frame window)
