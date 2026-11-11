# Device evidence: collection-detail-inplace-refresh

Round 1. Spec `docs/superpowers/specs/2026-11-11-collection-detail-inplace-refresh.md`, test design Manual 1-4 and the instrumented rows.

## Build and device

| Item | Value |
|---|---|
| Worktree HEAD | cad9f616 (base 0e4449e2); `git status` clean after the base-VM run |
| Base-VM APK | HEAD plus `CollectionDetailVM.kt` from 0e4449e2, sha256 `311de9214308cfabfe87d7906bff515fd66b49f3573c7f63b69e361eb4fcb9c3`; on-device `base.apk` same sha256 |
| HEAD APK | sha256 `67795c0fe28dead052de04a6de649f25ebad29c1ddabcf2d941ef8038a0baa33`; on-device `base.apk` same sha256 |
| Install | `tools/android-apk-install-preserve`, outcome `installed` (`compatible_replace`) both times |
| Device | `emulator-5554` (AVD `puber_tv_36`, Android 16, en-US) |
| Method | D-pad keyevents, focused-node bounds `[l,t][r,b]` from `uiautomator dump`, screenshots at 0.4 s and 2.6 s after BACK. A spinner frame is ~11 KB, a grid frame >150 KB |
| Origin | Home > Collections row > collection "Первая мировая война" (more than 12 items, 3 columns). The Collections tab was not enabled, so Settings was not touched |
| Account changes | Card "Lawrence of Arabia" only, ends where it started: watched toggled 4x on base + 18x on HEAD (even, last toggle off: "Not started"); bookmark toggled 6x (first add, last remove: "Add to bookmarks" in the card menu) |
| Lease | emulator-5554, 7200 s, released |

## Instrumented (`make DEVICE=emulator-5554 itest TESTS=...CollectionDetailScreenContentFocusTest`, 3 runs)

| Test | Result |
|---|---|
| `collectionDetailFocusStaysOnTheFocusedCardWhenContentIsReplaced` | PASS, 3 of 3 |
| `collectionDetailFocusMovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent` | FAIL, 3 of 3: `ComposeTimeoutException` 5000 ms at test line 124 (`waitUntil { focusedWith(neighbour) }`) |
| other 3 tests in the class | PASS |

## Manual

| Manual | Build | Steps | Observed | Result |
|---|---|---|---|---|
| 1 on base VM (AC-2 "red") | base VM | DOWN x3, RIGHT, OK, Details: RIGHT x4, OK (watched toggle), BACK; 4 returns (on, off, on, off) | full-screen spinner at 0.4 s, 4 of 4; then grid, same card `[662,415][1259,751]`, 4 of 4 | spinner reproduced |
| 1 (AC-1, AC-2) | HEAD | same, 18 returns | no spinner frame, 18 of 18; grid in place at 0.4 s; same card `[662,415][1259,751]` after, 18 of 18; the card eye icon appears on watched and disappears on unwatched, 17 of 18 (see anomaly) | PASS with one anomaly |
| 2 (AC-6, heart) | HEAD | Details: RIGHT x3, OK (bookmark), BACK, long OK on the card; 6 returns | no spinner, same card, 6 of 6; card menu shows "Remove from bookmarks" after add and "Add to bookmarks" after remove, 6 of 6 | PASS |
| 3 | n/a | playback writes watch progress to the account and cannot be reverted by a toggle | not run | NOT CHECKED |
| 4 (AC-3) | HEAD | from Home, OK on the collection card, 3 times | spinner frame at 0.25 s, grid after; first card `[32,144][630,480]` focused, 3 of 3 | PASS |

Anomaly (HEAD, watched toggle, 4th of 6 returns in the first series): Details showed the toggle to "unwatched", but the grid card still showed the eye 2.6 s after BACK. The next return was consistent. 12 further returns, with a re-check 4 s later, matched on all 12. Cause not established; the card heart is not affected (Manual 2 never showed a stale value).

The grid card shows no bookmark marker, only watched; the heart value was read from the long-press menu.

Screenshots stay in the session scratchpad (account titles visible).


## Round 2 (HEAD d1dea378, shared helper `FocusTargetState.kt` changed)

| Item | Value |
|---|---|
| APK | sha256 `68f18c534ba624b3875806b29aa25951fd5dbf94f9b72a933d0aab9a233f8ea7`; on-device `base.apk` same sha256; install outcome `installed` (`compatible_replace`) |
| Device | `emulator-5554` (AVD `puber_tv_36`). It was not running at the start of the round; qa started it headless (`-no-window`, cold boot, no wipe). App and pairing were intact |
| Lease | emulator-5554, 7200 s, released |
| Deviation | A "new version" dialog covered Home and ignored D-pad focus; it was dismissed with one tap on "Not now" |
| Origin | Home > Collections row > collection "Факеры" (3 cards, 3 columns); card 2 "Meet the Fockers". The collection differs from round 1 |
| Account changes | Card 2 only, ends where it started: watched toggled 4x (last: off, "Not started"), bookmark toggled 4x (last: "Add to bookmarks") |

### Instrumented (`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=<class>`)

| Class | HEAD d1dea378 |
|---|---|
| `collections.detail.component.CollectionDetailScreenContentFocusTest` | 5 of 5 pass, 3 runs of 3 (incl. `...MovesToTheNeighbourWhenTheFocusedCardLeavesReplacedContent`) |
| `home.component.HomeFocusTraversalTest` | 4 of 4 pass |
| `home.component.HomeCollectionBackFocusTest` | 2 of 2 pass |
| `contentlist.content.SectionRowFocusTraversalTest` | 6 of 6 pass |
| `core.ui.uikit.component.moviesList.VideoGridInitialFocusTest` | 7 of 7 pass |
| `showall.content.ShowAllScreenContentFocusTest` (extra, VideoGrid) | 5 of 5 pass |
| `collections.component.CollectionsScreenContentFocusTest` (extra) | 1 of 1 pass |
| `home.component.HomeSectionRemovalFocusTest` | 3 of 4 pass; `removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch` fails 3 of 3 (`Focused = 'true'` assertion on the node at `l=32,t=164,r=565,b=464` found `false`) |

Regression check for `HomeSectionRemovalFocusTest`: with `FocusTargetState.kt` restored from cad9f616 (`git show cad9f616:<path> > <path>`), the same test fails 2 of 2 with the same assertion. `git diff 0e4449e2 cad9f616 --stat -- app/src/main` lists only `CollectionDetailVM.kt`, so cad9f616 carries the base helper: the failure predates the run. File restored with `git checkout -- <path>`, `git diff` empty. No Bookmarks focus test exists in `app/src/androidTest`.

### Manual (HEAD d1dea378)

| Manual | Steps | Observed | Result |
|---|---|---|---|
| 1 (watched) | Details: RIGHT x4 to the eye button, OK, BACK; 4 returns (on, off, on, off) | screenshot at 0.4 s is the grid (about 930 KB, not a spinner frame of about 11 KB), 4 of 4; card `[662,144][1259,480]` focused after, 4 of 4; the eye badge present after returns 1 and 3, absent after 2 and 4, matching Details each time (no anomaly) | PASS |
| 2 (heart) | Details: bookmark button, OK, BACK, long OK on the card; 4 returns | no spinner, same card, 4 of 4; menu reads "Remove from bookmarks", "Add to bookmarks", "Remove from bookmarks", "Add to bookmarks" | PASS |
| Home row smoke | Home "Continue watching" card 2: OK (Details), BACK; 3 times | focus `[753,644][1286,944]` before and after, 3 of 3 | PASS |
| Home "My bookmarks" row | card 1: OK, BACK | focus `[152,367][685,667]` before and after, 1 of 1 | PASS |
| Bookmarks screen | n/a | the Bookmarks tab is not in the side menu (the Settings toggle was not touched); the "Watching" tab stayed on a spinner | NOT CHECKED |


## Review fix round 1 (HEAD 48ff9085, `FocusTargetState.kt` opt-in flag, active-save ids)

| Item | Value |
|---|---|
| APK | sha256 `6eb4914d74b708dd4bb7f23d38e7e51fd7dd2f4b9decaec5157147eacf9a82f9`; on-device `base.apk` same sha256; install outcome `installed` (`compatible_replace`) |
| Device | `emulator-5554` (AVD `puber_tv_36`), lease released |
| Deviation | The "new version" dialog covered Home again; dismissed with one tap on "Not now" |
| Origin | Home > Collections row > "Факеры" (3 cards); card 2 "Meet the Fockers" |
| Account changes | Card 2 only, ends where it started: watched toggled 4x (last: off), bookmark toggled 4x (last: "Add to bookmarks") |

### Instrumented (`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=<class>`)

| Run | Result |
|---|---|
| `VideoGridInitialFocusTest` with `FocusTargetState.kt` and `CollectionDetailScreenContent.kt` restored from a021e92c | 7 of 9 pass; red: `changedInitialFocusedItemIdWithChangedItemsRefocusesTheRetainedRow`, `removedCardInAnUntargetedRowDoesNotPinFocusAfterContentFocusReturns` (selector 'Item 3' and 'Item 13' not focused) |
| same class on HEAD | 9 of 9 pass |
| `CollectionDetailScreenContentFocusTest` | 5 of 5 pass, 3 runs |
| `HomeFocusTraversalTest` | 4 of 4 |
| `HomeCollectionBackFocusTest` | 2 of 2 |
| `SectionRowFocusTraversalTest` | 6 of 6 |
| `ShowAllScreenContentFocusTest` | 5 of 5 |
| `CollectionsScreenContentFocusTest` | 1 of 1 |
| `HomeSectionRemovalFocusTest` | 3 of 4; `removingFocusedFirstCardFocusesTheRightNeighborAndNotSearch` fails (as in round 2) |

The a021e92c helper alone does not compile: `CollectionDetailScreenContent.kt` passes `holdPendingFocusTarget`, so it was restored from a021e92c too. Both files restored with `git checkout -- app/src/main`; `git status` empty.

### Manual (HEAD 48ff9085)

| Manual | Steps | Observed | Result |
|---|---|---|---|
| 1 (watched) | Details: RIGHT x4 to the eye button, OK, BACK; 4 returns (on, off, on, off) | frame at 0.4 s is the grid (927-930 KB, not a ~11 KB spinner frame), 4 of 4; card `[662,144][1259,480]` focused after, 4 of 4; eye badge on after returns 1 and 3, off after 2 and 4 (pixel at the badge 82,79,76 vs 158,151,145), matching the toggle each time | PASS |
| 2 (heart) | Details: RIGHT x3 to the bookmark button, OK, BACK, long OK on the card; 4 returns | grid frame at 0.4 s, 4 of 4; same card, 4 of 4; focus back on the card after closing the menu, 4 of 4; menu: "Remove from bookmarks", "Add to bookmarks", "Remove from bookmarks", "Add to bookmarks" | PASS |
| Home row smoke | Home "Continue watching" card 2: OK (Details), BACK; 3 times | focus `[717,644][1250,944]` before and after, 3 of 3 | PASS |
| Home "My bookmarks" | card 1: OK, BACK | focus `[152,780][685,1080]` before and after | PASS |
| Home Collections opener | BACK from the collection | focus on the opener `[152,658][685,958]` | PASS |
| Stale watched badge (round 1 observation) | within Manual 1 | not seen in 4 of 4 | not reproduced |
