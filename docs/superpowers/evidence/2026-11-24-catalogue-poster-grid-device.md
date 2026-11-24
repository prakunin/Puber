# catalogue-poster-grid - device evidence, round 1

Date: 2026-11-24
Build: worktree `catalogue-poster-grid` at b3e01e31, `app-dev-debug.apk`, versionName 1.12.6-dev, sha256 ed5524c71b7f7345b7bc56824770d4661c2a321875536d921a8484ea37b5ee20 (the installed base.apk on the device has the same hash, checked after install and after the instrumented run).
Device: owner's TV box 192.168.1.110:5555 (lease held and released), package com.kino.puber.stage only. Install: tools/android-apk-install-preserve, outcome `installed`, classification `compatible_replace`, no destructive action.
Not done: `make auth-save` failed before the install ("run-as: package not debuggable", the app then installed on the box is not debuggable, so there was nothing to save). The session survived the install: Home and the catalogue loaded with the account's content, no pairing code.
Screenshots: 2026-11-24-catalogue-poster-grid-screens/ (catalogue content only, no account screens, no tokens).

## Instrumented tests

`make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<three classes>` run in the worktree: BUILD SUCCESSFUL, 17 tests, 0 failures, 0 errors, 0 skipped (VideoItemPosterTest 7, ShowAllScreenContentFocusTest 5, ShowAllRailCloseFocusTest 5).

## Manual checks

| # | AC | Steps | Expected | Observed | Result |
|---|----|-------|----------|----------|--------|
| M1 | AC-7 | Concerts tab, "All" row to its end, Show all, RIGHT into the grid; same in Movies; walked 6 and 7 screens down | six columns of poster cards, HD / 4K badge top start, pills at the poster foot, titles under it, long original ellipsized with the year whole | six columns in both sections; HD grey and 4K green badges top start; KinoPub (thumb), Kinopoisk (reel) and IMDb pills at the foot, one to three pills, three wrap into two rows; "Live: In The Still Of The Ni... - 2004", "Wenn der Vater mit dem S... - 1955", "Symphony No 9 from the ... - 2026": original cut, year whole; year alone for a title without original ("2026", "1980", "1970", "1969"); two-line Russian titles; eye icon top end on a watched card. No CC or AC3 badge anywhere. Owner judges the look against the stand. | PASS (owner judges) |
| M2 | AC-9 | RIGHT into the grid, DOWN steps, screenshots after each | one tile framed and lifted, the previous loses it, first row not clipped | one framed, lifted tile in every screenshot, the previous tile back to the plain border; the first-row frame is whole (grid-focused-first) | PASS |
| M3 | AC-9 | focused card, `input keyevent --longpress 23`; `input keyevent 82` | context menu opens both ways, details do not open | VideoItemContextMenuDialog (title, watch, bookmark, close) opens on long-press and on the menu key; the details screen did not open. After the dialog closes (Back or the Close button) the rail expands and holds focus, a second Back closes it and the card is framed again (see Observations). | PASS for opening; observation on close |
| M4 | AC-10 | card in row 2, column 3 (Chvrches), CENTER, details, BACK, 2.5 s | same card focused | the same card ("Chvrches") framed after Back, no key pressed | PASS |
| M5 | AC-7 | titles with a year only, long original, one rating, long Russian title, 4K, watched | see M1 | see M1; 4K badge in the green of the stand; eye top end. Not seen: a serial with new episodes counter (no such item in the walked screens). | PASS except the counter, NOT CHECKED |
| M6 | AC-12 | after the install: Home rows, a catalogue row, Favourites ("I watch") | as before | Home hero and rows, catalogue rows (wide cards, year + KP / IMDb / PUB row, title below), Favourites (vertical cards with the episode counter, ratings "8.2 / 7.6 / 8.0" as before) show the old cards and the old rating text ("10.0" in the hero). No screenshots from before the install exist, so this is a check against the known look, not a pair. Episode schedule and the player episode panel were not opened. | PASS (partial), see AC table |

## Observations

1. On the first entry into Show all the side rail is expanded with focus on the rail item; RIGHT then focuses the first card. The same observation is in docs/superpowers/evidence/2026-10-17-tab-stack-lost-on-root-push-device.md (item 1) and the 2026-10-17 round reports, so it predates this run. The diff does not touch the focus memory or the rail handoff.
2. After the context menu closes in Show all, focus sits on the rail (expanded), not on the card. Not attributed: the dialog and the focus memory are unchanged, but no base build was run (a main-checkout or base APK may not be installed by qa), so a regression from the card swap cannot be excluded. ShowAllRailCloseFocusTest.showAllHandoffResponseDoesNotStealFocusFromADialog passes.
3. Kinopoisk and IMDb pill text is not rounded: "5.978", "8.011" (Concerts and Movies grids, "Vse zakonchitsya", "Korchak"). The KinoPub pill shows "10", "8.3" per AC-5.
4. Rating pills sit on the poster art; on bright or text-heavy posters the pill text is hard to read (Tomorrowland, Rammstein, Bel Suono). Visual call for the ux-designer.
5. The first Back from the expanded rail inside the Show all grid sometimes leaves the app to the Android launcher (once, after the dialog check). Not investigated: the rail and Back handling are unchanged.

# Round 2

Date: 2026-11-24
Build: worktree `catalogue-poster-grid` at 3759abcf, `app-dev-debug.apk`, versionName 1.12.6-dev, sha256 3a2be8c73cddb572c0c4950d00484dd0aaa8ae83011199a1c036255ef65521ec (the installed base.apk on the device has the same hash, checked after install).
Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Install: tools/android-apk-install-preserve, outcome `installed`, classification `compatible_replace`, no destructive action. The session survived the install (catalogue content loaded, no pairing code).
Screenshots: 2026-11-24-catalogue-poster-grid-screens/ files starting with `r2-` (catalogue and Episode schedule / player panel screens, no account data, no tokens).

## Instrumented tests

`make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<class>`, 48 tests, 0 failures:

| Class | Tests | Result |
|---|---|---|
| VideoItemPosterTest | 7 | pass (includes the reworked ellipsis test) |
| ShowAllScreenContentFocusTest | 6 | pass (includes showAllMenuOpenAndBackReturnsFocusToTheOpeningCard) |
| ShowAllRailCloseFocusTest | 6 | pass |
| VideoGridInitialFocusTest | 10 | pass |
| FavoriteScreenContentFocusTest | 4 | pass |
| HomeFocusTraversalTest | 4 | pass |
| SectionRowFocusTraversalTest | 7 | pass |
| EpisodeScheduleScreenContentTest | 4 | pass |

Run notes: the first combined run of all eight classes (one `TESTS=` list) stopped with "Instrumentation run failed due to Process crashed" while ShowAllScreenContentFocusTest was running (test 4 of 6 reported "failed with status -1"); VideoItemPosterTest, VideoGridInitialFocusTest and FavoriteScreenContentFocusTest had passed before it. Re-runs of a single class then crashed at start (the report was "testRunFailed") until a zombie com.kino.puber.stage process and a stale stage.test process on the box were cleared with `am force-stop` of both stage packages (logd on the box was at 90 % cpu). After that every class ran alone and passed (table above). No source change in between; the crash looks like a device state problem; it did not recur in the later single-class runs.

## Manual checks

| # | AC | Steps | Expected | Observed | Result |
|---|----|-------|----------|----------|--------|
| R2-1 | AC-5 | Movies, "New" row end, Show all; screenshots of the grid | KinoPub, Kinopoisk and IMDb pills with at most one decimal | pills "7.3", "6.9", "8.3", "6.5", "10", "5", "3.9", "6.6", "9.3", "7", "6.8", "5.4", "5.6", "7.1", "5.8", "6.3"; no value with three decimals in any screenshot (r2-grid-focused-first, r2-grid-focused-row2-col3) | PASS |
| R2-2 | AC-7 | same screens, bright posters ("El deshielo", "Son Yemek", "Steps", "Snowbound") | pill text readable on the 78 % scrim | pills dark and readable on the white and bright posters; the pill over the Korean title text ("Stajer") is still cluttered by the poster lettering behind it. Owner and ux-designer judge. | PASS (owner judges) |
| R2-3 | AC-9 | row 2, column 3 card ("Stajer"); `input keyevent --longpress 23`; BACK | menu opens, BACK returns focus to that card, rail stays collapsed | menu "Stajer / ..." opened (watch, bookmark, close); after BACK the same card is framed and lifted, rail collapsed (r2-menu-open, r2-menu-back-focus) | PASS |
| R2-4 | AC-9 | same card; `input keyevent 82`; BACK | same | same card framed, rail collapsed (r2-menu-key-open, r2-menu-key-back-focus) | PASS |
| R2-5 | AC-9 | same card; `input keyevent 82`; DOWN, DOWN, CENTER on the Close button | focus returns to the card | same card framed, rail collapsed (r2-menu-close-button-focus) | PASS |
| R2-6 | AC-12 | serial "Gnomes": details, Episode schedule button (calendar icon) | screen opens as before | opens; the box build has no TMDB token (worktree build has no secrets), so the screen shows "Schedule unavailable. TMDB access is not configured" and the schedule cards cannot be seen on the device (r2-ac12-episode-schedule). Cards are covered by EpisodeScheduleScreenContentTest 4/4. | PASS (partial), cards NOT CHECKED on device |
| R2-7 | AC-12 | "Gnomes": Play, resume prompt Continue, pause, Episodes button of the player | episode panel as before | wide episode cards with a number, focus frame on episode 1, no badges, pills or split titles (r2-ac12-player-episodes-panel). Playback ran about 20 s from the saved position; left with BACK. | PASS |
| R2-8 | AC-12 | "Gnomes" details screen, episode row | as before | wide episode cards with the episode number, progress bar on episode 1, no badges or pills (r2-ac12-serial-details-episodes); the Series tab row itself looked as before (wide cards, year and rating row), no screenshot kept | PASS |

# Round 3

Date: 2026-11-24
Build: worktree `catalogue-poster-grid` at head 3aabfd58 (clean tree; production code last changed in 210540e6), `app-dev-debug.apk`, versionName 1.12.6-dev, sha256 19c81f1b72d118202246764383cd68ed35fc8db554e60ad710fd8e86093715e8 (the installed base.apk on the device has the same hash).
Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Install: tools/android-apk-install-preserve, outcome `installed`, classification `compatible_replace`, no destructive action; the session survived (catalogue content loaded, no pairing code). `make auth-save` was run before the install (snapshot stays in gitignored .auth/).
Screenshots: 2026-11-24-catalogue-poster-grid-screens/ files starting with `r3-` (concert catalogue only, no account data).

## Instrumented tests (concern 1 of the gate: head code on the box)

`make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>`, result XML read after each run:

| Run | Tests | Failures | Result |
|---|---|---|---|
| ShowAllScreenContentFocusTest alone | 6 | 0 | pass (includes showAllLoadMoreFiresAgainWhenAPageLandsInsideTheThreshold and showAllMenuOpenAndBackReturnsFocusToTheOpeningCard) |
| ShowAllRailCloseFocusTest alone | 6 | 0 | pass |
| VideoItemPosterTest alone | 7 | 0 | pass |
| all three in one `TESTS=` list | 6 + 6 + 7 | 0 | pass, exit 0 |

Combined-run crash (gate concern 2): did not recur. Before the combined run `adb logcat -b crash -c` was run; after it `adb logcat -d -b crash` returned no entries. Round 2's crash therefore remains unattributed; it was seen only after a zombie stage process and logd load on the box, and the same three classes ran together cleanly here. (The first attempt of this round used wrong class names and stopped at the Gradle filter; no device effect.)

## Manual checks

| # | AC | Steps | Expected | Observed | Result |
|---|----|-------|----------|----------|--------|
| R3-1 | M7 (paging) | Concerts, row "All", Show all, RIGHT into the grid; held DOWN in bursts of 6 presses (one row each) with a screenshot hash after each burst, 60 bursts = 360 presses | each page appends, grid keeps growing, no stall | every one of the 60 bursts changed the screen: 361 rows reached (at least 2166 items) with no stall and no stuck spinner; the list was still growing when the walk stopped (screenshots r3-showall-first, -mid10, -mid25, -bottom) | PASS |
| R3-2 | M7 (end of list) | Concerts, row "4K", Show all, held DOWN in bursts of 6 until the screen stopped changing | the grid grows to the end of the section, the last row is partial, no spinner | the screen changed for 15 bursts, then 3 bursts in a row were identical; last row holds one card (Taylor Swift), no spinner, no further load. One UP from the last card returns exactly the screen of burst 14, so the grid has 86 rows: 85 full rows plus 1 card = 511 items (r3-4k-first, r3-4k-mid8, r3-4k-bottom) | PASS |
| R3-3 | M7 (extra page) | same walk, look for a page fetched after the last one | note whether one extra page is fetched | not observable on this list: the last page was partial, so the end was reached without an empty page. The one-extra-page prefetch (triage-3 #1) needs a section whose total is an exact multiple of the page size; not available here. Stays accepted from code reading, not measured. | NOT CHECKED |
| R3-4 | AC-9 | 4K Show all, row 2 card 3 ("Bedouin / at Petra"), `input keyevent --longpress 23`, then BACK | menu opens for that card; after BACK the same card is framed, rail collapsed | menu titled "Bedouin / at Petra" opened (watch, bookmark, close); after BACK the Bedouin card is framed and lifted, rail collapsed, details did not open (r3-menu-open, r3-menu-back-focus) | PASS |

Not changed since round 2 and not repeated here: AC-1, AC-5 and AC-7 look checks, AC-12 guard classes, the Episode schedule cards (no TMDB token in a worktree build).

Observation: the first entry into Show all focused the expanded rail again (RIGHT then reached the first card). Predates the run (round 1 observation 1).
