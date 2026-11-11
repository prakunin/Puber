## Round 1

First attempt found no emulator; this re-run used emulator-5554 (puber_tv_36), leased and released.

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` at fd8f221e -> pass (first attempt; not repeated). After qa commit 25b00f7e (androidTest file only, one import removed) not re-run.
DEVICE: emulator-5554 (puber_tv_36), own APK sha256 597886e7...fd0a matches on device.

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Red on base: `bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu` fails (action not focused, BookmarksScreenContentFocusTest.kt:49); green at HEAD |
| AC-2 | PASS | `bookmarksBackAfterItemChangeFocusesTheNeighbourCard` green at HEAD (guard; green on base too) |
| AC-3 | PASS | Favourites `favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`: red on base (first action not focused), green at HEAD, incl. neighbour focused after Back. Needed test fix 25b00f7e |
| AC-4 | FAIL (Favourites); PASS (Bookmarks) | Bookmarks test green. Favourites: `favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard` red on base and at HEAD, "Card 1" focused after Back instead of "Card 12". Device, Favourites: after Back no card is focused and the side rail expands; same on a build with the base `FavoriteScreenContent.kt`. Pre-existing, not fixed, not a regression |
| AC-5 | NOT CHECKED | Re-adding to Watching would mutate the account beyond what is safe to restore; not run |
| AC-6 | PASS | `bookmarksDoesNotTakeFocusWhileOuterContentFocusIsInactive` green at HEAD (guard); device: rail open then DPAD_RIGHT refocuses the card |
| AC-7 | PASS | `CollectionDetailScreenContentFocusTest` 5/5, `VideoGridInitialFocusTest` 9/9 at HEAD; check.full green |

TESTS ADDED: none new. Fixed `FavoriteScreenContentFocusTest.openMenuOnCard` (commit 25b00f7e): direct `requestFocus` on a second-row card never holds, so it walks there with DPAD_DOWN and DPAD_RIGHT. Before this both Favourites tests failed at setup on HEAD.
EVIDENCE: /home/user1/orca/workspaces/Puber/bookmarks-context-menu-focus-loss/docs/superpowers/evidence/2026-11-11-bookmarks-context-menu-focus-loss-device.md (audit passed; not committed)
DEFECTS:
1. Favourites: closing the context menu with Back does not return focus to the opening card (AC-4). Instrumented: menu on "Card 12" (second row), Back, "Card 1" holds focus. Device: single row, Back, no card focused, rail expands. Identical on base. Cause per test-design risk: no `TvDialogFocusRestorer`; `isTargetRow` follows `focusedRowKey`. Check: `FavoriteScreenContentFocusTest.favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard`.
UNAVAILABLE: none
MANUAL: none

## Round 2

Fix d938ea1e (`TvDialogFocusRestorer` on the Favourites `mainContentFocus` Column). emulator-5554 (puber_tv_36), leased and released; own APK sha256 44f304ad...3dbf33 matches on device (install-preserve `installed`, no destructive action).

CHECK: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` at d938ea1e -> pass (all tasks up to date except lint).
DEVICE: emulator-5554

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Unchanged from Round 1: `bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`; Bookmarks class 4/4 at d938ea1e |
| AC-2 | PASS | Unchanged: `bookmarksBackAfterItemChangeFocusesTheNeighbourCard` |
| AC-3 | PASS | Unchanged: Favourites `favoriteItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`; green in 3 of 3 runs at d938ea1e |
| AC-4 | PASS | `favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard` (second-row "Card 12") green in 3 of 3 runs, was red in Round 1; Bookmarks green. Device, Favourites: Back after the menu on the 2nd, 4th and 6th card refocuses that card. Device has a single row, so the second-row case is instrumented only |
| AC-5 | NOT CHECKED | Unchanged: re-adding to Watching would mutate the account |
| AC-6 | PASS | `bookmarksDoesNotTakeFocusWhileOuterContentFocusIsInactive` green; device: DPAD_LEFT from the first card expands the rail, DPAD_RIGHT refocuses the card |
| AC-7 | PASS | `CollectionDetailScreenContentFocusTest` 5/5, `VideoGridInitialFocusTest` 9/9 at d938ea1e; check.full green |

TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/bookmarks-context-menu-focus-loss/docs/superpowers/evidence/2026-11-11-bookmarks-context-menu-focus-loss-device.md (Round 2 section appended; audit passed; not committed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none

## Review fix round 1

HEAD fac7ce19, emulator-5554 (puber_tv_36), leased and released. Only `make DEVICE=emulator-5554 itest TESTS=<FQCN>`. The emulator went offline during the last (base, Bookmarks) run and was gone from `adb devices` afterwards; not restarted.

CHECK: not re-run; production diff since Round 2 is comments and import order only (fac7ce19 touched tests and comments).
DEVICE: emulator-5554 (lost during the final run)

HEAD results per test:

| Test | Favourites (3 runs) / Bookmarks (2 runs) at HEAD | Base 8b116d22 (1 run) |
|---|---|---|
| ItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu | green / green | red (Bookmarks: action not focused; Favourites: "Activity never DESTROYED", cascade from the previous hang) |
| BackAfterItemChangeFocusesTheNeighbourCard | green / green | Favourites green (guard); Bookmarks not run |
| RemoveFromMenuFocusesTheNeighbourCard | green 3/3 / RED 2/2 | Favourites red (`ComposeNotIdleException`); Bookmarks red (empty failure, device went offline) |
| MenuOpenAndBackReturnsFocusToTheOpeningCard | green / green | Favourites red (focus timeout); Bookmarks not run |
| DoesNotTakeFocusWhileOuterContentFocusIsInactive | RED 3/3 / green | Favourites red (same assertion as HEAD); Bookmarks not run |

Base diff restored: `git diff HEAD` empty after the check.

| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Bookmarks `...ItemChange...` green 2/2 at HEAD, red on base |
| AC-2 | PASS | Bookmarks `...BackAfterItemChange...` green 2/2; Favourites green 3/3 and on base (guard) |
| AC-3 | PASS | Favourites `...ItemChange...` green 3/3 at HEAD, red on base (cascade failure, see table) |
| AC-4 | PASS | `...MenuOpenAndBack...` green on both at HEAD; Favourites red on base |
| AC-5 | FAIL (Bookmarks); PASS (Favourites) | Favourites `favoriteRemoveFromMenuFocusesTheNeighbourCard` green 3/3 at HEAD, red on base. Bookmarks `bookmarksRemoveFromMenuFocusesTheNeighbourCard` red 2/2 at HEAD: `waitUntil` for "Neighbour" focus times out (BookmarksScreenContentFocusTest.kt:87) |
| AC-6 | FAIL (Favourites); PASS (Bookmarks) | Bookmarks gate green at HEAD. Favourites `favoriteDoesNotTakeFocusWhileOuterContentFocusIsInactive` red 3/3 at HEAD and on base: a card takes focus while `contentFocusActive = false` (FavoriteScreenContentFocusTest.kt:100) |
| AC-7 | NOT CHECKED | Not re-run this round; last result in Round 2 |

TESTS ADDED: none by QA (the new tests are the fac7ce19 rows above).
EVIDENCE: n/a for this round (test-only, no device observation beyond the tests); Round 2 device file unchanged.
DEFECTS:
1. Bookmarks, Remove from the menu: after choosing Remove the neighbour card never gets focus (2 of 2 runs). Check: `BookmarksScreenContentFocusTest.bookmarksRemoveFromMenuFocusesTheNeighbourCard`. Where focus landed was not probed. Base also red, so either a pre-existing production gap or a test setup problem (the Favourites twin passes with the same shape).
2. Favourites: a card takes focus although the outer content focus is inactive (3 of 3 runs, identical on base). Check: `FavoriteScreenContentFocusTest.favoriteDoesNotTakeFocusWhileOuterContentFocusIsInactive`. The Bookmarks gate passes, so the Favourites gate was not ported or the test's expectation is wrong for that screen.
UNAVAILABLE: base run of the Bookmarks class incomplete (2 of 5 tests; emulator offline). Bookmarks base rows for BackAfterItemChange, MenuOpenAndBack and the gate are not recorded; emulator-5554 needs a restart before a repeat.
MANUAL: none
