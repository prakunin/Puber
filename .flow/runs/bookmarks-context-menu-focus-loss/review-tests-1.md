LENS: tests  ROUND: 1  BASE: 8b116d22

I did not re-run `check.full` or the instrumented tests. I relied on qa round 2 (green at d938ea1e) and on the red-on-base results recorded there.

### F1 [major] AC-5 has no test and no manual check, and commit d938ea1e changed the path it covers
- Where: `/mnt/data/projects/PetProjets/Puber/.flow/runs/bookmarks-context-menu-focus-loss/qa-report.md` (AC-5 "NOT CHECKED" in both rounds). Related code: the `TvDialogFocusRestorer` added to `app/src/main/java/com/kino/puber/ui/feature/favorites/content/FavoriteScreenContent.kt` in d938ea1e.
- Problem: AC-5 says that after "Remove" the menu closes, a card is focused, and the neighbour is focused once the removal lands. The test design marks it "device, manual check 2". QA skipped it because re-adding to Watching would mutate the account, and no instrumented substitute exists. d938ea1e then added a restorer to Favourites that saves the focused child on open and restores it on close. On "Remove" it may restore focus to the card that is being removed, or fall back to the Column. The two Favourites tests in this diff only cover a Back close and a list change while the menu is open. Neither covers close-by-action followed by a list change.
- Failure scenario: In Favourites the user long-presses a card and chooses Remove. The restorer re-focuses the departing card, then the list update arrives. Focus could end on no card or on the first row, and no test or manual run would fail.
- Fix: Add an instrumented Favourites test for AC-5. Click the menu's remove action, swap `grid` to drop the card, and assert the neighbour is focused. This needs no account mutation, and the existing tests already show the pattern. Alternatively record an explicit owner waiver for AC-5 in the run notes.

### F2 [minor] Favourites has no test that the outer `LocalContentFocusActive=false` still gates focus (AC-6)
- Where: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/favorites/content/FavoriteScreenContentFocusTest.kt`
- Problem: AC-6 says "either tab", but only Bookmarks has a test (`bookmarksDoesNotTakeFocusWhileOuterContentFocusIsInactive`). The test design lists only the Bookmarks test, so this is a gap against the AC text rather than against the plan.
- Failure scenario: Someone changes the Favourites provider to `provides (contextMenuItem == null)` and drops the outer value. While the rail is open, Favourites cards steal focus from it. No test fails, and QA only checked this on the device.
- Fix: Add a Favourites test with `LocalContentFocusActive provides false`. It should assert that the content exists and that no card is focused.

### Unproven
- `FavoriteScreenContentFocusTest.kt:96` The second-row walk assumes the initial focus lands on Card 1 and that DOWN then RIGHT reaches Card 12. A change to the initial-focus rule would break test setup, not the behaviour under test. No evidence of this yet.
