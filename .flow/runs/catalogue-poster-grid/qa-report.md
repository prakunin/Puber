Round 3

CHECK: not re-run this round; no production or test source changed since round 2's check.full (BUILD SUCCESSFUL at f41bd66f, as the gate re-ran it). Head 3aabfd58 builds (assembleDevDebug up to date, clean tree).
DEVICE: 192.168.1.110:5555 (owner's TV box; worktree dev APK 1.12.6-dev built at head 3aabfd58, install-preserve outcome installed / compatible_replace, on-device sha256 19c81f1b...15e8 equals the built APK; package com.kino.puber.stage only; auth-save done before install). Lease token 358306-1795507853 acquired and released (rc 0). No emulator.
ACS (round 3 delta; the rest as round 2):
| AC | Status | Evidence |
|----|--------|----------|
| AC-9 | PASS | R3-4: long-press on a row-2 card ("Bedouin / at Petra") in the 4K concerts Show all, then Back: same card framed, rail collapsed (r3-menu-open, r3-menu-back-focus). ShowAllRailCloseFocusTest 6/6 and VideoItemPosterTest 7/7 on head |
| AC-10 | PASS | ShowAllScreenContentFocusTest 6/6 on head 3aabfd58 (includes showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold); M7 on device: R3-1, R3-2 |
| AC-11 | PASS | ShowAllRailCloseFocusTest 6/6 on head, alone and in the combined run |
| AC-1 | NOT CHECKED | owner's stand approval, unchanged |
| AC-2 .. AC-8, AC-12 | as round 2 | no source change; AC-12 Episode schedule cards stay NOT CHECKED on device (no TMDB token) |
M7 (load more on the head APK): PASS. Concerts "All": 360 DOWN presses in 60 bursts, every burst moved, 361 rows (at least 2166 items), no stall, no stuck spinner. Concerts "4K": grid grows to the end, 86 rows = 511 items, last row one card, 3 identical bursts at the end, no spinner, no further load. One-extra-page prefetch: not observable (last page partial), NOT CHECKED, stays accepted from code reading.
Combined itest crash (gate concern 2): ShowAllScreenContentFocusTest, ShowAllRailCloseFocusTest and VideoItemPosterTest each green alone (6, 6, 7) and together in one TESTS list (6 + 6 + 7, exit 0). `logcat -b crash` was cleared before the combined run and was empty after it. The round 2 crash did not recur; still unattributed (round 2 had a zombie stage process and busy logd on the box).
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-poster-grid/docs/superpowers/evidence/2026-11-24-catalogue-poster-grid-device.md (Round 3 section; new r3-*.png in .../2026-11-24-catalogue-poster-grid-screens/; audit passed, 111 files; committed 116594ff)
DEFECTS: none
UNAVAILABLE: Episode schedule cards on device (no TMDB token in a worktree build), unchanged. Not measurable here: the extra page after a section whose total is an exact multiple of the page size.
MANUAL: owner: AC-1 stand approval, AC-7 look (r2-grid-focused-first.png, r2-grid-focused-row2-col3.png), Episode schedule cards after merge on a main build with the TMDB token. Observation, predates the run: first entry into Show all focuses the expanded rail (candidate for /task:new).

---

Round 2

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at head 3759abcf; result XML read: VideoItemUIMapperTest 56 tests / 0 failures, VideoItemPosterUrlsTest 3 / 0)
DEVICE: 192.168.1.110:5555 (owner's TV box; worktree dev APK 1.12.6-dev built at 3759abcf, install-preserve outcome installed / compatible_replace, on-device sha256 3a2be8c7...21ec equals the built APK; package com.kino.puber.stage only). Lease note under UNAVAILABLE.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | NOT CHECKED | stand approval is the owner's (unchanged from round 1) |
| AC-2 | PASS | unit tests as round 1 (VideoItemUIMapperTest green); HD / 4K badges top start on device |
| AC-3 | PASS | mapper tests (yearEmpty_whenYearIsZero and the rest) green; VideoItemPosterTest 7/7 on the box including the reworked ellipsis test (asserts the year Text does not overflow). The red-with-weight run is in the round 1 fix record, not re-done by qa. Device: year alone and long original with a whole year, as round 1 |
| AC-4 | PASS | mapper tests incl. usesOriginalAsLocalTitle_whenLocalPartEmpty green |
| AC-5 | PASS | mapShortItem_pillRatingsRoundKpAndImdbToOneDecimal, KpAndImdbDropTrailingZero, KpAndImdbUseDotDecimal_underRussianLocale green; buildRatings_keepsRawKpAndImdbText green. Device: every pill in the Movies grid has at most one decimal ("7.3", "6.9", "8.3", "6.5", "3.9", "6.6", "9.3", "6.8", "5.4", "5.6", "7.1", "5.8", "6.3", PUB "10"); no "5.978" type value in any screenshot. Hero still "10.0" (AC-12 unchanged) |
| AC-6 | PASS | unchanged from round 1; PosterChipScrim is the one named value, no colour literal in VideoItemPoster.kt |
| AC-7 | PASS (owner judges) | PosterChipScrim = 0.78 in Color.kt; screenshots r2-grid-focused-first and r2-grid-focused-row2-col3: pills dark and readable over the white "El deshielo", "Son Yemek", "Steps", "Snowbound" posters. The pill over the lettering of "Stazher" (Korean title text) is still busy. For the ux visual re-check |
| AC-8 | PASS | VideoItemPosterUrlsTest 3/3, unchanged |
| AC-9 | PASS | M3 re-run on the box: long-press 23 and menu key 82 on a row-2 card that is not the first ("Stazher"); Back after each, and the Close button: the same card is framed and lifted, rail stays collapsed (r2-menu-* screenshots). Details did not open. VideoItemPosterTest 7/7. Round 1 defect 1 is fixed |
| AC-10 | PASS | ShowAllScreenContentFocusTest 6/6 (incl. showAllMenuOpenAndBackReturnsFocusToTheOpeningCard, showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold), run alone on the box |
| AC-11 | PASS | ShowAllRailCloseFocusTest 6/6 run alone |
| AC-12 | PASS (Episode schedule cards NOT CHECKED on device) | guard classes on the box, each alone, 0 failures: VideoGridInitialFocusTest 10, FavoriteScreenContentFocusTest 4, HomeFocusTraversalTest 4, SectionRowFocusTraversalTest 7, EpisodeScheduleScreenContentTest 4, VideoItemPosterTest 7. Device: serial details episode row and the player episode panel (wide cards with number, no badge or pill) look as before; Episode schedule screen opens but the worktree build has no TMDB token, so it shows "Schedule unavailable, TMDB access is not configured" and no cards (cards covered by EpisodeScheduleScreenContentTest 4/4 only). Playback ran about 20 s from the saved position to open the panel (resume prompt, Continue, then paused); left with Back |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-poster-grid/docs/superpowers/evidence/2026-11-24-catalogue-poster-grid-device.md (Round 2 section; new screens r2-*.png in .../2026-11-24-catalogue-poster-grid-screens/; audit passed, 102 files; committed 6ba7d266). For the ux re-check: r2-grid-focused-first.png (focused card, first row), r2-grid-focused-row2-col3.png (focused card over lettering, bright posters with pills), r2-menu-open.png
DEFECTS: none confirmed. Observations:
1. A combined itest run of all eight guard classes (one TESTS list) died with "Process crashed" during ShowAllScreenContentFocusTest (test 4 of 6, showAllFocusMovesToTheNeighbourWhenTheFocusedCardLeftTheListBeforeReentry, status -1); later single-class runs crashed at start until a zombie stage process and a stale stage.test process on the box were cleared with am force-stop of both stage packages (logd on the box was at 90 % cpu). After that every class passed alone. No crash text was read (logcat on the box hung). Not attributed; the box needs a look if it recurs.
2. The worktree has uncommitted edits that are not mine, made while I ran: ShowAllScreenContent.kt (LaunchedEffect key without state.items.size, modified 08:01:38) and ShowAllScreenContentFocusTest.kt (the load-more test, modified 07:45:57), plus an app APK rebuilt at 08:01:44. My install and the production code I tested are head 3759abcf (the production file changed after my install); the ShowAll test runs from 07:50 may include the edited test file. Results above hold for 3759abcf plus possibly that test edit. I did not touch them and committed only the evidence.
3. Pills over busy lettering (Stazher) remain hard to read; ux call.
4. Predates the run: first entry into Show all focuses the expanded rail (round 1 observation 4); not re-checked.
UNAVAILABLE: Episode schedule cards on device (no TMDB token in a worktree build). Lease: acquired token 322852-1795505898 at 07:38, my release call at 08:00:55 printed nothing and a lock owned by a different token (pid 340177, dead, created 08:00:56, cwd this worktree) now stands on 192.168.1.110:5555; a release with my token answers resource_lock_token_mismatch. I did not touch it. The holder (conductor?) should confirm or clear it. No emulator used.
MANUAL: owner and ux-designer look at r2-grid-focused-first.png and r2-grid-focused-row2-col3.png (pill legibility, focus frame); owner decides whether the busy-lettering pill case needs more. Whoever is editing ShowAllScreenContent.kt must commit and have qa re-run ShowAllScreenContentFocusTest on the box.
