# Review triage - round 1

Run: detail-poster-trailer-flicker   Base: 76a8bc2a   Reviewed head: bd2cf869

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-ux-designer-1.md.

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, edge F1 | minor | `VideoItemGridDetails.kt:668-680`, `ContentListVM.kt:70-72`, `FavoriteVM.kt:108-117` | A details request that throws leaves `selectedItem = Loading`, so the held list never releases and the previous card's poster stays next to the new card's skeleton text | Read `ContentListVM.onItemFocused`: `Loading` is published, then `getItemDetails` runs with no catch; the hold condition is `isLoading && current.isEmpty()` with no bound | fix | Bound the hold inside the poster component (no VM change, per Q1): after a fixed hold window of `Loading` the held list is released and the panel falls back to the skeleton. Pure-function seam plus unit test. |
| 2 | tests F2 | major | `VideoItemGridDetails.kt:576-579`, `VideoDetailsPosterStateTest.kt:76-95` | No test checks that the poster request actually uses `PosterCrossfade` and `useExistingImageAsPlaceholder`; reverting to `crossfade(true)` stays green | Read the tests: they call `PosterCrossfade.create` directly; `rememberPosterRequest` is untested | fix | Extract `internal fun posterRequest(context, url)`, test `transitionFactory === PosterCrossfade` and the placeholder extra; if Coil cannot build off-device, record it and leave M4 warm with the owner |
| 3 | tests F3 | minor | `VideoDetailsPosterStateTest.kt:65-68` | `posterFadeTarget` 1F branch untested | Read the test file | fix | Add `posterFadeTarget_isOne_whenTrailerNotRendered`, `posterFadeTarget_isOne_whenNoTrailer` |
| 4 | tests F4 | minor | `VideoDetailsPosterStateTest.kt:90-95` | Error-branch test only asserts "not a crossfade" | Read the test | fix | Assert the class equals `Transition.Factory.NONE`'s transition |
| 5 | blind F2, ux-designer F1, qa note | nit | `VideoItemGridDetails.kt:650-659` | KDoc for `heldPosterUrls` stacked above `PosterCrossfade` | Read the diff | fix | Move the block above `heldPosterUrls` |
| 6 | tests F1 (major), codex rejected [high] | major | `VideoItemGridDetails.kt:492-504`, `SkeletonAsyncImage.kt:30,49` | Single-still identity and the opt-in wiring have no automated guard; trailer ACs never seen on a device | The headless emulator renders no video layer (qa-report); the spec classifies AC-1/2/3/7 as manual by nature | fix (partial) | Cheap seam: `PosterStill` opt-in exposed as an `internal const` with a test. The device gap is carried to gate 2 as NOT CHECKED manual checks for the owner, not rounded up. |
| 7 | codex CONFIRMED | minor | `SkeletonAsyncImage.kt:42-62` | After a poster has been shown, a failed primary URL hides the image (alpha 0, error painter null) and suppresses the skeleton, so the panel is empty until the fallback loads | Read `SkeletonAsyncImage`: `hasShownImage` keyed on `model != null` survives the URL change; `isFailed` hides the image | defer | Rare path (broken primary wide poster with a working fallback); the right behaviour (keep the old picture vs. skeleton) is a design choice. New idea run `detail-poster-fallback-empty-frame`. |

## Rejected codex claims

- [high] Trailer ACs untested on a device - not a code defect; carried to gate 2 as manual checks (row 6), as the codex-reviewer itself recommends.

## Next

- Fix round 1: rows 1-6 by the implementer in fix mode, then check.fast, lens edge + codex-reviewer on `git diff bd2cf869`, lens tests (tests changed), qa on AC-4/AC-6 if runtime code changed. Unproven lines: 6 (blind 2, edge 3, tests 1); edge's favourites "Loading forever" unproven line is also covered by row 1's bound.
