# Show all focus restore hardening - device evidence

Slug: showall-focus-restore-hardening. Round 1.
Worktree head: 4aa7bb0e (base 35bfbbec).
Device: emulator-5554 (AVD puber_tv_36, Android 16, atv64 x86_64), leased for the run, released at the end, left running.
Build: this worktree's own `app-dev-debug.apk` (`com.kino.puber.stage`, 1.10.0-dev, sha256 f079528be046c79db2e2883f322cce42924aa3421a7842bc9fd1b9e579dbd0b4), installed with `tools/android-apk-install-preserve` (outcome `installed`, classification `compatible_replace`, destructive_action false). The on-device base.apk sha256 equals the built APK.
Input: D-pad key events only, no taps. No playback was started and no bookmark or watched-state toggle was used. No screenshots are stored in the repository.

## Static gate

`./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` with `AGENT_GRADLE_SERIAL=1`: BUILD SUCCESSFUL. Tasks were UP-TO-DATE from the implementer's identical run at the same head (inputs unchanged), only `lintDevDebug` re-executed. The stored result `GridFocusMemoryTest` shows tests=21 failures=0 errors=0.

## D-M4 / AC-8: instrumented class

`make GRADLE=./tools/agentw DEVICE=emulator-5554 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest`

- Run 1 (committed head): Finished 5 tests, 0 failed. BUILD SUCCESSFUL.
- M-AC7 (test file only, `repeat(DeepRows)` -> `repeat(2)` at line 72 of the deep-card test): `showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry` FAILED with `java.lang.AssertionError: focus moved below the first viewport`. The other four cases passed (3/5 completed with 1 failure at the point the run aborted, Gradle reported 1 failure in total). Reverted with `git checkout`, `git diff --exit-code` empty.
- M-AC2 (`ShowAllScreenContent.kt` line 230 resolver `else -> items[index.coerceIn(0, items.lastIndex)].id` -> `else -> null`): `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry` FAILED with `androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 5000 ms` (nothing focused, as the plan predicts). Only that case failed. Reverted, `git diff --exit-code` empty.
- Run after both reverts (clean tree): Finished 5 tests, 0 failed. BUILD SUCCESSFUL.

Neither mutation was committed; the tree was clean before the APK was built and installed.

## D-M1: Back from Details restores the same card (AC-9)

Movies tab, "New releases" row scrolled to its end, "Show all" (DPAD_CENTER) opened the grid. Rail expanded on first entry (known, out of scope: showall-first-entry-rail-focus). DPAD_RIGHT into the grid.

| Run | Steps | Expected | Observed | Result |
|-----|-------|----------|----------|--------|
| 1 | DOWN x4, RIGHT x1 (card "Hopeu", row 4 below the first viewport; row 0 off screen), CENTER (Details shown, 4 s), BACK, 3 s | same card focused, no key needed | "Hopeu" focused (screenshot compared to the pre-open one: same ring, same scroll) | PASS |
| 1b | DOWN once | focus moves to the card below | focus moved to the card directly below ("Matchbox") | PASS |
| 2 | DOWN x6, RIGHT (deeper card "Pizza Movies", right column), CENTER, BACK, 3 s | same card | "Pizza Movies" focused after Back | PASS |
| 3 | grid scrolled ~12 rows, left column card "Onslaught", CENTER, BACK, 3 s | same card | "Onslaught" focused | PASS |
| 4 | a further deep card ("Dix Pour Cent", reached through a longer scroll), Details, BACK | same card | focused | PASS |

## D-M2: rail not stolen (AC-9)

| Steps | Expected | Observed | Result |
|-------|----------|----------|--------|
| left column card, CENTER (Details), BACK, 0.6 s later LEFT, observe 3 s and 6 s with no input | rail opens and keeps focus | rail expanded with "Movies" focused at 0.3 s and still at ~6 s; the grid did not take focus back | PASS |
| same, but LEFT sent 0.15 s after BACK | rail opens, or the key is ignored; never a steal | the key was absorbed during the navigation transition (rail did not open, "Onslaught" stayed focused, nothing jumped). Not a focus steal; recorded as an observation about key timing, not a defect of this change | observation |
| from the open rail select "Series" (tab switch), then back to "Movies" | Show all grid shown (tab stack), focused card present, rail not re-opened | grid shown, "Onslaught" focused, rail collapsed | PASS |

## D-M3: opener-removed path (AC-9, best effort)

Trigger found without playback: the local preference "Hide watched content" (Settings, Content). Original value: OFF (also: Show watched indicators ON, Anime in general lists ON, Trailer on focus ON; only Hide watched was touched).

Steps (Show all stays on the Movies tab stack while Settings is visited, tab-local stack from the parent run):
1. Movies Show all grid scrolled to a left column card with a watched indicator ("Dyavol nosit Prada 2", about row 64; confirmed by the indicator on the focused card).
2. LEFT to the rail, Settings, Content, Hide watched content OFF -> ON.
3. Rail, select Movies. Wait 7 s with no input.

Expected: the grid is shown, the opener is gone, a card is focused, never "nothing focused", rail not re-opened.
Observed: the Show all grid was shown, the opener card no longer in the list, a card in the grid carried the focus ring without any key press (rail stayed collapsed). One DOWN afterwards moved the ring to a neighbouring card, so the grid was live.
Caveat: after the filter the grid re-paged while on screen, so which new-list card sits at the saved index could not be read from the screen with certainty. The check proves "a card is focused after the opener was removed"; it does not prove the exact clamped neighbour. The exact neighbour logic is covered by the JVM tests (U-AC1a/b/c, U-AC2a, resolver cases) and the instrumented `showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry` (red under M-AC2).
An earlier pass of the same trigger with a card that stayed visible (the opener was not watched) restored the focus to the opener itself: PASS.
The race inside the ~3-frame window cannot be forced on a device; this is not the AC-1 race, it is the entry fallback.

Restored: Hide watched content back to OFF, verified by the toggle state in a screenshot (same pixels as the initial OFF reading).

## Notes

- Two navigation slips with the rail (a single LEFT from the second column does not reach the rail) opened Details of a focused card and once pressed DPAD_DOWN x8 and CENTER inside Details. Details of a movie is shown; the Details screen with focus on "Watch movie" remained displayed, `topResumedActivity` stayed `MainActivity`, "Not started" was unchanged, so no playback was started. No state was changed by it.
- The Details screen starts its own trailer preview by itself; this is existing behaviour, not playback started by the check.
