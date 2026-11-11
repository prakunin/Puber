# Device evidence: bookmarks-context-menu-focus-loss

Round 1. Device: emulator-5554 (AVD puber_tv_36, API 36), leased. Package com.kino.puber.stage, dev debug, installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, no destructive action). Instrumented runs: `make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=<fqcn>`.

HEAD build: worktree fd8f221e (production sources identical at 25b00f7e), APK sha256 597886e7...fd0a, equal on device. Base-behaviour build for comparison: HEAD with `FavoriteScreenContent.kt` from 8b116d22, sha256 fe024ed0...cdb13, equal on device; HEAD APK reinstalled afterwards. Source diff empty after each restore.

## Instrumented

| Class | Base (both screen files from 8b116d22) | HEAD |
|---|---|---|
| BookmarksScreenContentFocusTest (4) | 1 failed: `bookmarksItemChangeWhileContextMenuIsOpenKeepsFocusInTheMenu`, `assertIsFocused` on "Watch movie" action, Focused = false (BookmarksScreenContentFocusTest.kt:49) | 4 passed |
| FavoriteScreenContentFocusTest (2) | 2 failed at `openMenuOnCard` (test setup: `requestFocus` on a second-row card never holds). After the test fix (25b00f7e, D-pad walk): `favoriteItemChangeWhile...` fails at `assertIsFocused` on the first action; `favoriteMenuOpenAndBack...` fails, focused card after Back is "Card 1" | `favoriteItemChangeWhile...` passes; `favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard` fails: after Back the focused card is "Card 1", not "Card 12" |
| CollectionDetailScreenContentFocusTest (5) | not run | 5 passed |
| VideoGridInitialFocusTest (9) | not run | 9 passed |

The first Favourites run at fd8f221e (before 25b00f7e) failed both tests at `openMenuOnCard` on HEAD too, so those results prove nothing about the fix; the numbers above are from 25b00f7e.

## Smoke, Favourites ("Watching"), single visible row

Steps use the remote only: DPAD keys, long press on DPAD_CENTER, BACK. No screenshot is stored.

| Check | Steps | Expected | Observed HEAD | Observed base-behaviour build |
|---|---|---|---|---|
| 1 (AC-4) | Focus the 3rd card, long press Select, Back | Menu closes, same card focused | Menu closes, no card shows focus, side rail expands with "Watching" highlighted. DPAD_RIGHT from the rail refocuses the same 3rd card | Same |
| 2 (AC-5) Remove | not run | | NOT CHECKED: re-adding the title to Watching would be an account mutation beyond the lease | |
| 3 (AC-6) rail | Open rail (see 1), DPAD_RIGHT | Focus returns to the card | Returns to the 3rd card | not run |

A "New version available" dialog appears at launch; dismissed with Back, no preference changed.

## Round 2 (HEAD d938ea1e)

Device: emulator-5554 (puber_tv_36), leased. Own APK sha256 44f304ad...3dbf33 installed with `tools/android-apk-install-preserve` (outcome `installed`, `compatible_replace`, `destructive_action` false); on-device sha256 equal. Runs: `make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=<fqcn>`.

### Instrumented

| Class | Result at d938ea1e |
|---|---|
| FavoriteScreenContentFocusTest (2), three runs | 2 of 2 passed in each run, incl. `favoriteMenuOpenAndBackReturnsFocusToTheOpeningCard` (second-row "Card 12") |
| BookmarksScreenContentFocusTest (4) | 4 passed |
| CollectionDetailScreenContentFocusTest (5) | 5 passed |
| VideoGridInitialFocusTest (9) | 9 passed |

### Smoke, Favourites ("Watching"), single visible row (DPAD_DOWN does nothing, so no second row on this account)

Remote only: DPAD keys, long press on DPAD_CENTER, BACK. No screenshot is stored.

| Repeat | Card | Steps | Expected | Observed |
|---|---|---|---|---|
| 1 | 2nd | Long press Select, Back | Menu closes, same card focused | Menu with three actions shown; after Back the 2nd card shows the focus ring, rail collapsed |
| 2 | 4th | same | same | After Back the 4th card focused |
| 3 | 6th | same | same | After Back the 6th card focused |
| Rail | 1st | DPAD_LEFT from the first card, then DPAD_RIGHT | Rail expands, then focus returns to the card | Rail expanded with "Watching" highlighted; DPAD_RIGHT refocused the first card |

DPAD_LEFT between cards moved to the neighbouring card, not the rail, as before. No menu action was activated; no preference or account change.
