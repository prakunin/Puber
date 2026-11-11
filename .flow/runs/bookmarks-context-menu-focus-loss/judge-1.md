# Gate - bookmarks-context-menu-focus-loss - round 1

**Decision:** CONCERNS
**Head:** f918aedb   **Base:** 8b116d22

## Done as asked

Yes. `git diff -w 8b116d22...HEAD -- app/src/main` touches only the two screens named in brief.md Plan 1-2: `LocalContentFocusActive provides (outer && contextMenuItem == null)` in `BookmarksScreenContent.kt:74-77` and `FavoriteScreenContent.kt:89-93`. Beyond the plan, Favourites gained a `TvDialogFocusRestorer` on `mainContentFocus` (`FavoriteScreenContent.kt:72-88`, d938ea1e) to meet AC-4. The shared helper, Home and ContentList are unchanged, as Out of scope requires. The tests are in the two new androidTest classes. The device evidence is committed (36a9fa50).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` | Red on base at `BookmarksScreenContentFocusTest.kt:49` and green at HEAD (evidence file, Instrumented; qa-report Review fix round 1) | PASS |
| AC-2 | `bookmarksBackAfterItemChangeFocusesTheNeighbourCard` (guard) | Green 2/2 at fac7ce19 | PASS |
| AC-3 | `favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`, `favoriteBackAfterItemChangeFocusesTheNeighbourCard` | Green 3/3 at HEAD. The base red in Round 1 was a clean assertion failure (evidence file); the later base run failed by cascade | PASS |
| AC-4 | `bookmarksMenuOpenAndBackReturnsFocusToTheOpeningCard`, `favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard`; Favourites device smoke | Both green at HEAD; the Favourites test is red on base. Device Round 2: Back refocused the 2nd, 4th and 6th card | PASS |
| AC-5 Favourites | `favoriteRemoveFromMenuFocusesTheNeighbourCard`; device check NOT CHECKED | Green 3/3 at HEAD, red on base. The test drops the card synchronously (triage-2 #1). Device: account mutation, see Manual checks | PASS (instrumented); device NOT CHECKED |
| AC-5 Bookmarks | none at HEAD (`bookmarksRemoveFromMenuFocusesTheNeighbourCard` removed in f918aedb) | Red 2/2 at HEAD. The base run ended in an "empty failure, device went offline" (qa-report Review fix round 1), so the base result is unknown | NOT VERIFIED |
| AC-6 | `bookmarksDoesNotTakeFocusWhileOuterContentFocusIsInactive`; Favourites rail device smoke | Bookmarks test green. Device Round 2 Rail row: the rail opens and DPAD_RIGHT refocuses the card. The Favourites provider keeps the outer value (`FavoriteScreenContent.kt:91`). The Favourites launch-focus gap is identical on base (deferred, see Findings) | PASS |
| AC-7 | `CollectionDetailScreenContentFocusTest`, `VideoGridInitialFocusTest`, check.full | Instrumented 5/5 and 9/9 at d938ea1e; nothing in production changed after that except comments and imports. My check.full at f918aedb: detekt, lint and `compileDevDebugAndroidTestKotlin` green. Unit tests: 1-2 of 2058 failed in each of 3 runs, always in `HistoryVMDeletionSchedulingTest` or `HomeVMIncrementalPublishTest`. Those are the flakes recorded in run `unit-suite-flaky`; the diff touches neither them nor their subjects. The same classes pass on base in the main checkout (`--rerun`) | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| AC-5 untested; Favourites restorer fallback goes to the Column | review-tests-1 F1, review-blind-1 F1 (triage-1 #1) | fixed | fac7ce19 added the remove tests; the Favourites one is green and red on base |
| No Favourites test for the outer-inactive gate | review-tests-1 F2 (triage-1 #2) | test added, then deferred | red on base and HEAD; run `favourites-launch-focus-ignores-rail` |
| Favourites test checks two behaviours | review-blind-1 F3 (triage-1 #3) | fixed | fac7ce19 (`favoriteBackAfterItemChangeFocusesTheNeighbourCard`) |
| Dialog sits inside the provider | review-blind-1 F2 (triage-1 #4) | fixed (comment) | fac7ce19, `BookmarksScreenContent.kt:162`, `FavoriteScreenContent.kt:117` |
| Import order | review-blind-1 F4 (triage-1 #5) | fixed | fac7ce19 |
| Remove test drops the card synchronously | review-tests-2 F1 (triage-2 #1) | accepted minor | noted on the test-design AC-5 row; real removal is async after the API call (`BookmarksVM.kt:154-162`) |
| Bookmarks: neighbour not focused after Remove | qa defect 1 (triage-2 #2) | deferred | run `bookmarks-remove-from-menu-focus`; its title says "red on base", which the base run did not establish |
| Favourites takes launch focus while content focus is inactive | qa defect 2 (triage-2 #3) | deferred | run `favourites-launch-focus-ignores-rail`; red 3/3 on HEAD and on base with the same assertion |

Codex review rounds 1-2: clean. Nothing was rejected.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | `LocalContentFocusActive` is static, so each menu open and close recomposes the screen subtree once (brief.md Risks, accepted). No work added to hot paths |
| Reliability | `runCatching` around the focus requests, as in Bookmarks. Fallback when the saved child is gone: `mainContentFocus.requestFocus()` on the Column (`FavoriteScreenContent.kt:83`), covered only by the synchronous remove test |
| Maintainability | The Favourites restorer copies the Bookmarks one (`BookmarksScreenContent.kt:85-97`), a two-place pattern with no shared helper. No dead code. Conventions kept |
| Security / privacy | No secrets. `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber` returned `passed`. Evidence holds no account data or screenshots |
| TV | Focus, D-pad and Back are covered by the instrumented tests and the Favourites device smoke. The Bookmarks tab was not checked on a device (owner decision Q2) |

## Manual checks for the owner

1. AC-5, Favourites, on a device (mutates the account):
   1. On the leased emulator, open Watching and focus a middle card.
   2. Long-press Select and choose Remove.
   3. Expect: the menu closes, a card is focused, and once the item leaves, the next card is focused, not the first card, the details pane or the rail.
   4. Re-add the title to Watching afterwards.

## Must fix

None.

## Concerns for the owner

1. AC-5 on Bookmarks is NOT VERIFIED. The only automated check was red 2/2 at HEAD and was removed in f918aedb. Its base run never completed, so this branch may have caused the failure; "pre-existing" is not established. Before ship, or as the first step of `bookmarks-remove-from-menu-focus`, run the test from fac7ce19 once on base production files with a stable emulator.
2. AC-5 on Favourites rests on a test that drops the card in the same frame as the close, while the app drops it after the API call. The device check above is open.
3. Each of my check.full runs at f918aedb was red on the known unit-test flakes (`unit-suite-flaky`), outside this diff. Here `HistoryVMDeletionSchedulingTest.deleteSelectionDuringAutomaticPagination_isQueuedOnceAndRetainsSemanticFocus` failed 3 of 3 times in the worktree under load average 20+. It passed on base in the main checkout.
4. Both pre-existing gaps leave this branch unfixed and go to idea runs: the Favourites launch focus ignores the rail, and the Bookmarks Remove neighbour focus.
