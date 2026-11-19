LENS: tests  ROUND: 2  BASE: bd2cf869

`VideoDetailsPosterStateTest` passes at 9ede97aa: 17 tests, 0 failures, 0 skipped. That is the 11 earlier tests plus 6 new ones; I ran only this class. Status of the round-1 findings:

- **F2 is closed.** `posterRequest` builds off-device with a relaxed `mockk<Context>`. The test asserts `transitionFactory === PosterCrossfade` and the `useExistingImageAsPlaceholder` extra. A switch back to `crossfade(true)` or losing the placeholder would make it fail. The Implementation record has a red run for it.
- **F3 is closed.** Both 1F branches of `posterFadeTarget` are now pinned.
- **F4 is closed.** The test now asserts the class of `Transition.Factory.NONE`'s transition.
- **F1 is only partly closed.** The triage accepted that, and the device ACs go to gate 2 as NOT CHECKED. Details in F1 below.

### F1 [minor] The new opt-in test cannot catch the regression it was added for
- Where: `app/src/test/kotlin/com/kino/puber/core/ui/uikit/component/details/VideoDetailsPosterStateTest.kt` (`posterStill_staysOptedIntoSkeletonOnlyUntilFirstImage`); `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:612`; `app/src/main/java/com/kino/puber/core/ui/uikit/component/SkeletonAsyncImage.kt:27,49`
- Problem: The test only asserts `POSTER_SKELETON_ONLY_UNTIL_FIRST_IMAGE == true`, so it fails only if someone flips the constant. Round-1 F1's scenario was dropping the argument from `PosterStill`. `SkeletonAsyncImage`'s parameter defaults to `false`, so if the line is deleted, the constant just goes unused and the test stays green. Two other parts of root causes #2 and #3 are still unguarded:
  - `onSuccess` setting `hasShownImage = true` (`SkeletonAsyncImage.kt:49`);
  - the single `PosterStill` keeping its slot when the trailer starts or stops.
- Failure scenario: Someone deletes `skeletonOnlyUntilFirstImage = POSTER_SKELETON_ONLY_UNTIL_FIRST_IMAGE` from `PosterStill`. The skeleton flash on a card change or trailer change comes back, and all 17 tests stay green.
- Fix: Count this test as documentation, not as a guard. Keep AC-1, AC-2, AC-3 and AC-7 as NOT CHECKED manual checks at gate 2, as triage row 6 already says. No code change is needed unless a Compose UI test seam is wanted.

### F2 [minor] The 3 s hold expiry in `rememberHeldUrls` (triage row 1) is only tested through its pure helper
- Where: `app/src/main/java/com/kino/puber/core/ui/uikit/component/details/VideoItemGridDetails.kt:694-707`
- Problem: `heldPosterUrls_returnsCurrent_whenHoldExpired` covers the `holdExpired` flag in the pure function. Nothing tests the code that sets it: the `LaunchedEffect(holding)` with `delay(POSTER_HOLD_MS)`, and the `remember(holding)` reset.
- Failure scenario: The `LaunchedEffect` is removed, or keyed so it never fires. A details request that throws then leaves the old poster next to the new card's skeleton for good (triage row 1). All tests stay green.
- Fix: Accept this as a known gap, since the row is minor and needs a Compose runtime to test. Alternatively, move the expiry into a small testable holder driven by a `TestScope`.

### Unproven
- `VideoItemGridDetails.kt:697` A `remember(holding)` reset racing a pending `holdExpired = true` write. This would become real if a recomposition were shown where `holding` flips false and back to true within one frame while the old effect's write lands.
