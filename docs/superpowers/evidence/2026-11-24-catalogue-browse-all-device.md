# catalogue-browse-all: device evidence

Build: head 04fa78aa, worktree dev debug APK (app-dev-debug.apk, sha256 c83e63d0f21e539db9c30b9785d7094c5ad4815191112cd12d42d90330669a2f), installed with tools/android-apk-install-preserve (outcome installed, compatible_replace). Device: TV box 192.168.1.110:5555, package com.kino.puber.stage, lease held for the run. Screenshots: 2026-11-24-catalogue-browse-all-screens/.

## Static gate and instrumented classes

- check.full (testDevDebugUnitTest, detektAll, lintDevDebug): BUILD SUCCESSFUL.
- make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<class>, one class per call, all BUILD SUCCESSFUL: SectionRowFocusTraversalTest, ShowAllScreenContentFocusTest, ShowAllRailCloseFocusTest (last one 6 tests, 0 failures; the first two report success through gradle only, their result XML was overwritten by the next run).

## Manual checks

| ID | Steps | Expected | Observed | Result |
|----|-------|----------|----------|--------|
| M-1 | Cold start, rail to each tab, RIGHT into content (Movies, Series, 4K, Concerts, Doc movies, Doc series, TV shows) | Label above the first shelf, focus on the first card, entry unfocused | Labels "Все фильмы", "Все сериалы", "Всё в 4К", "Все концерты", "Все док. фильмы", "Все док. сериалы", "Все ТВ-шоу" above the first shelf heading; first card framed, entry quiet (M1c-*, M1d-*, W0-4k). First shelf cards not clipped. Cartoons and Anime not opened (see notes). | PASS (Cartoons, Anime not checked) |
| M-2 | Movies, RIGHT to card 2, UP, DOWN, UP, LEFT, RIGHT | UP: entry filled; DOWN: card 2; LEFT: rail; RIGHT: entry | UP focuses the entry (filled), DOWN returns to card 2 (M2r-3), LEFT opens the rail (M2r-5), RIGHT returns focus to the entry (M2r-6). Trailer: the panel kept the static poster 3 s after UP onto the entry; no trailer playing. | PASS |
| M-3 | Entry focused, CENTER, BACK (cold and warm, with and without RIGHT into the grid) | Tab shown, entry focused | Four runs (V1, V2, D, P series): after BACK card 1 (or card 2 when it was the last focused) is framed and the entry is unfocused (V1-c, V2-c). CENTER on it opens details of that film. | FAIL |
| M-4 | Movies entry, CENTER, read header, DOWN 30 rows | Header "Все фильмы", first card focused, header not focusable, pages append | Header "Все фильмы" in one line (M4-a), RIGHT focuses card 1 (M4-b), 30 rows down new cards keep appending, no focus on the header (M4-c-row12, row30). On the first entry the rail is expanded and holds focus until RIGHT (V1-b), same as before the change. | PASS |
| M-5 | Doc series: "Новинки" Show all; "Все" shelf Show all; 4K "Все" shelf | "Док. Сериалы · Новинки"; "Все док. сериалы" same as the entry; "Всё в 4К" | M5-1 header "Док. Сериалы · Новинки"; M5-3 header "Все док. сериалы", equal to the entry label. 4K: the grid header "Всё в 4К" is drawn above the error state (see notes). Focus returning to the last card in the grid was not checked. | PASS (title), 4K grid in error |
| M-6 | Screenshots for the owner | owner judges | Entry unfocused (M1c-movies) and focused (V1-a): 24 dp pill above the first shelf heading, filled when focused, not cut at its top; grid header M4-a. | owner |

## Notes

- 4K: the "Все" shelf of the 4K tab shows "Не удалось выполнить запрос. Попробуйте ещё раз." with a retry button, and the browse grid opened from the entry shows the same error (E1-lastshelf, W0-4k). Same section config for both. The shelf existed before this run; not compared with the base build.
- Playback was not started, no account data written.

## Round 2

Builds: manual M-3, M-2, M-5 first on head 6c35732d (APK sha256 dcf6e745185724aa86bcdb870907d3cba635275ac5896976ee32fe268cb03d8d, outcome installed), then M-3 and rail again on head c0664ac0 (APK sha256 4e806de630262da3bfd69e389bccdcf2d62723e2d395981648e9932ad9297d36, outcome installed), because three commits (04ba8f1a, a9828e0f, c0664ac0) landed in the worktree while the session was interrupted. Device: TV box 192.168.1.110:5555, package com.kino.puber.stage, lease held and released. Screenshots: 2026-11-24-catalogue-browse-all-screens/R2-*.png (6c35732d) and R3-*.png (c0664ac0). Playback not started, no account data written. One film details screen was opened by mistake (CENTER on a card) and closed with BACK.

### Static gate

- check.full (testDevDebugUnitTest, detektAll, lintDevDebug) at 6c35732d: BUILD SUCCESSFUL. Not re-run at c0664ac0 by this round.

### Instrumented classes (make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>, one class per call; result XML in 2026-11-24-catalogue-browse-all-itest-r2/)

| Class | Head | Result |
|-------|------|--------|
| ShowAllScreenContentFocusTest | c0664ac0 | BUILD SUCCESSFUL |
| ShowAllRailCloseFocusTest | c0664ac0 | BUILD SUCCESSFUL, XML saved |
| SectionRowFocusTraversalTest | c0664ac0 | FAIL 1 of 16, three runs out of three: launchFocusSkipsTheEntryWhenSectionZeroIsEmpty (SectionRowFocusTraversalTest.kt:513), assertIsFocused on row-1-item-0 got Focused = false. XML: SectionRowFocusTraversalTest.xml, -rerun0.xml, -rerun1.xml |

The XML of ShowAllScreenContentFocusTest was overwritten by the next class run (the shared output path holds one result file).

Label: the red SectionRowFocusTraversalTest XML files in itest-r2/ (SectionRowFocusTraversalTest.xml, -rerun0.xml, -rerun1.xml) are the result at head c0664ac0, not at the later head; round 3 below re-runs the class at 109b7b0b and it is green.

### Manual checks

| ID | Steps | Observed | Result |
|----|-------|----------|--------|
| M-3 (AC-7) | Movies cold: UP to entry, CENTER, BACK | Entry framed (filled), no shelf card framed (R2-12, R3-04) | PASS |
| M-3 | Warm: CENTER, RIGHT into grid, BACK | Entry focused (R2-14, R3-05) | PASS |
| M-3 | DOWN, RIGHT (card 2), UP, CENTER, RIGHT, BACK, then DOWN | Entry focused (R2-16, R3-06); DOWN returns to card 2 (R2-17, R3-07) | PASS |
| M-2 rail | From the entry LEFT opens the rail, RIGHT returns to the entry; LEFT then BACK also returns to the entry | R2-18, R2-19, R2-20, R3-08 | PASS |
| M-1 step 3 | Cartoons and Anime | Neither tab is in the rail on this build (rail: Главная, Поиск, Я смотрю, История, Фильмы, Сериалы, 4K, Концерты, Док. Фильмы, Док. Сериалы, ТВ Шоу, Настройки), so the tabs could not be opened. No-entry is covered by TTC browseEntryFor_isNullForTabsWithoutAnEntry only | NOT CHECKED on device |
| M-5 step 3 | Doc. series «Все» shelf, Show all tile, grid opens on card 1 (R2-29). DOWN, RIGHT to card 6 of row 2, CENTER (details), BACK | Grid shows with focus on the card that was opened (R2-31) | PASS |

Notes: a freshly opened Show all grid (first open or reopened through the tile) starts on card 1 and the expanded rail holds focus until RIGHT, as in round 1. The 4K «Все» error is deferred (cached-payload-row-too-big) and was not re-opened.

## Round 3

Head 109b7b0b (tree clean). Build: worktree dev debug APK (app-dev-debug.apk, sha256 409e38b6efe5b797f6efe05153dd1968f5030bfffc4d2e98520eb88cae1d165f, install-preserve outcome installed, compatible_replace; the on-device base.apk sha256 matches). Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only, lease held and released. Screenshots: 2026-11-24-catalogue-browse-all-screens/R4-*.png. Playback not started, no account data written; one film details screen was opened twice on purpose (M-3 step 4) and closed with BACK. Screenshots that showed Home content were deleted.

### Static gate

- check.full (testDevDebugUnitTest, detektAll, lintDevDebug) at 109b7b0b: BUILD SUCCESSFUL (most tasks up to date from the preceding build of the same sources).

### Instrumented classes (make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>, one class per call, result XML in itest-r3/)

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| SectionRowFocusTraversalTest | 17 | 0 | 0 |
| ShowAllTitleResolveTest | 3 | 0 | 0 |
| ShowAllScreenContentFocusTest | 8 | 0 | 0 |
| ShowAllRailCloseFocusTest | 6 | 0 | 0 |

### Manual checks

| ID | Steps | Observed | Result |
|----|-------|----------|--------|
| M-1 | App cold start, rail (LEFT, DOWN, CENTER) to Movies, Series, Concerts and Doc. series, none opened before in this app run | First card of the first shelf framed on each tab, entry pill (label "Все фильмы", "Все сериалы", "Все концерты", "Все док. сериалы") above the shelf heading, not focused (R4-03-movies-opened, R4-04-series-cold, R4-05-concerts-cold, R4-40-docseries-entry). Cartoons and Anime are not in the rail | PASS (Cartoons, Anime not reachable) |
| M-2 | Movies, RIGHT (card 2), UP, DOWN, UP, wait 3.5 s, LEFT, RIGHT | UP: entry filled (R4-08); DOWN: card 2 framed (R4-09); UP again, 3.5 s: panel keeps the static poster, no trailer playing (R4-10); LEFT: rail expanded (R4-11); RIGHT: entry focused again (R4-12) | PASS |
| M-3 steps 1-3 | Entry, CENTER, BACK, CENTER, BACK (before any card was opened) | Grid opens (R4-13, R4-15); each BACK lands on the tab with the entry focused (R4-14, R4-16). Cold start on Movies and on Doc. series also pops with one BACK (R4-32, R4-42) | PASS |
| M-3 step 4 | DOWN, CENTER on a card, BACK | Details open (R4-18), BACK returns to the tab with that card framed (R4-19) | PASS |
| M-3 step 5 | After step 4: UP, CENTER (grid opens, R4-20 / R4-35 / R4-45), BACK | The grid stays on screen with card 1 framed and the rail collapsed (R4-21, R4-36, R4-46); the entry is not focused. A second BACK pops to the tab with the entry focused (R4-21c, R4-47). Reproduced on Movies (twice, plus three further CENTER/BACK cycles that all needed two BACKs, R4-22-repeat1..3) and on Doc. series (R4-46) | FAIL |
| M-2 rail round trip | Entry, LEFT, RIGHT | R4-11, R4-12 | PASS |

Notes: before the first card is opened from a tab the grid pops with a single BACK (R4-14, R4-16, R4-32, R4-42 all one BACK). After a details round trip from a shelf card the first BACK from the browse grid no longer leaves it, until the app is restarted (not checked past the restart). I did not compare with the shelf Show all tile path (the Movies and Doc. series shelves are too long to reach the tile quickly), so it is not known whether this is specific to the entry.

## Round 4

Head b23f7a51 (tree clean). Build: worktree dev debug APK (app-dev-debug.apk, sha256 37bf0cc53a45f741bf45429f2a72eac055e7ef624c37234b4c3eaed91044de03, install-preserve outcome installed, compatible_replace; the on-device base.apk sha256 matches). Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only, lease held and released. Screenshots: 2026-11-24-catalogue-browse-all-screens/R5-*.png. Playback not started, no account data written. Details screens of films and one doc series were opened on purpose (M-3 step 4 and the defect re-check) and closed with BACK. Home screenshots with account rows were not kept. One film details screen was opened by mistake at the start (CENTER on Home before the rail was open) and closed.

### Static gate

- check.full (testDevDebugUnitTest, detektAll, lintDevDebug) at b23f7a51: BUILD SUCCESSFUL (most tasks up to date from the preceding build of the same sources).

### Instrumented classes (make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>, one class per call, result XML in 2026-11-24-catalogue-browse-all-itest-r4/)

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| SectionRowFocusTraversalTest | 18 | 0 | 0 |
| ShowAllScreenContentFocusTest | 8 | 0 | 0 |
| ShowAllRailCloseFocusTest | 6 | 0 | 0 |
| ShowAllTitleResolveTest | 3 | 0 | 0 |

### Manual checks

| ID | Steps | Observed | Result |
|----|-------|----------|--------|
| M-1 | App cold start, rail (LEFT, DOWN, CENTER) to Movies, then Series and Concerts, then Doc. series, none opened before in this app run | First card of the first shelf framed on each tab, entry pill (label "Все фильмы", "Все сериалы", "Все концерты", "Все док. сериалы") above the shelf heading, not focused (R5-01-movies-cold, R5-22-series-cold, R5-23-concerts-cold, R5-15-docseries-cold; Doc. series seen in a run where Movies had been opened). Cartoons and Anime are not in the rail | PASS (Cartoons, Anime not reachable) |
| M-2 | Movies, RIGHT (card 2), UP, DOWN, UP, wait 3.5 s, LEFT, RIGHT | UP: entry filled (R5-02); DOWN: card 2 framed (R5-03); UP, 3.5 s: static poster, no trailer (R5-04); LEFT: rail expanded (R5-05); RIGHT: entry focused again (R5-06) | PASS |
| M-3 steps 1-3 | Entry, CENTER, BACK, CENTER, BACK | Grid opens "Все фильмы" (R5-07); each BACK lands on the tab with the entry focused (R5-08, R5-09) | PASS |
| M-3 step 4 | DOWN, CENTER on a card, BACK | Details open (R5-10), BACK returns to the tab with that card framed (R5-11) | PASS |
| M-3 step 5 (known, deferred to showall-first-entry-rail-focus) | After step 4: UP, CENTER (grid, R5-12), BACK | First BACK leaves the grid on screen, card 1 framed, toast "Нажми «Назад» ещё раз, чтобы выйти" (R5-13); the second BACK pops to the tab with the entry focused (R5-14). Same as round 3 | UNCHANGED (defect) |
| Defect re-check via the shelf tile | Doc. series, «Все» shelf, RIGHT to the "Показать все" tile (R5 shots t-series not kept), CENTER, BACK, before any details trip in that tab | Grid opens (R5-16); the first BACK pops to the shelf, no toast (R5-17); the second BACK opens the rail (R5-18) | PASS (one BACK) |
| Defect re-check via the shelf tile after a details trip | Same tab: CENTER on a shelf card (details, R5 n1 not kept), BACK (R5-19), RIGHT to the tile, CENTER (grid, R5-20), BACK | The grid stays on screen with card 1 framed and the exit toast shown (R5-21): the same two-BACK behaviour as via the entry | REPRODUCED via the tile |

Notes: the two-BACK behaviour appears after a card was opened from a shelf and closed with BACK, for the entry and the shelf tile alike, so it does not depend on the entry; it is the pre-existing rail BackHandler ordering already traced and deferred. Before any details trip the grid pops with one BACK via the entry (Movies, R5-08, R5-09) and via the tile (R5-17).

## Round 5

Head f4d4b22e (tree clean at start). Build: worktree dev debug APK (sha256 c5b20197635e1f3a9b73922a5d63bf419ccba4d9076df5841060cda1158dca8e, install-preserve outcome installed, compatible_replace; on-device base.apk sha256 matches). Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only, lease held and released. Screenshots: 2026-11-24-catalogue-browse-all-screens/R6-*.png. Playback not started, no account data written; Home screenshots with account rows were not kept. Change under test: SectionVM publishes the paginator's replayed initial Empty as Loading. SectionVM is used only by ContentListScreen (catalogue tabs); Home does not use it.

### Static gate

- check.full (testDevDebugUnitTest, detektAll, lintDevDebug) at f4d4b22e: BUILD SUCCESSFUL (most tasks up to date).

### Instrumented classes (one class per make call, result XML in 2026-11-24-catalogue-browse-all-itest-r5/)

| Class | Tests | Failures | Errors |
|-------|-------|----------|--------|
| SectionRowFocusTraversalTest (already at head) | 19 | 0 | 0 |
| ShowAllScreenContentFocusTest | 8 | 0 | 0 |
| ShowAllRailCloseFocusTest | 6 | 0 | 0 |

### Manual checks

| ID | Steps | Observed | Result |
|----|-------|----------|--------|
| Home | Cold start | Home rows (watching, bookmarks) and hero load as before. Not kept as a screenshot (account rows) | PASS |
| M-1 Movies | Cold, rail LEFT, DOWN x4, CENTER | Hero and card shimmer, then cards with first card focused and trailer panel; entry pill "Все фильмы" not focused (R6-10, R6-11) | PASS |
| M-1 Series | Rail, one DOWN, CENTER | Shimmer with first card already framed (R6-20-a), then content, entry "Все сериалы" unfocused (R6-20-c) | PASS |
| M-1 4K | Rail, one DOWN, CENTER | Shimmer cards (R6-21-a), then cards with first card focused, entry "Всё в 4K" unfocused (R6-21-c). The "Все" shelf error was not examined, deferred | PASS |
| M-1 Concerts | Rail, one DOWN, CENTER | Shimmer, then cards, first card focused, entry unfocused (R6-22-a, R6-22-c) | PASS |
| M-2 | Movies, RIGHT, UP, DOWN, UP, 3.5 s, LEFT, RIGHT | UP: entry filled (R6-31); DOWN: card 2 framed (R6-32); UP, 3.5 s: static poster, no trailer (R6-33); LEFT: rail (R6-34); RIGHT: entry focused (R6-35) | PASS |
| M-3 steps 1-3 | Entry, CENTER, BACK, CENTER, BACK | Grid opens (R6-38); each BACK lands on the tab with the entry focused (R6-37, R6-39) | PASS |
| M-3 step 4 | DOWN, CENTER on a card, BACK | Details open (R6-40); BACK returns to the tab with the card framed (R6-41) | PASS |
| Hide-watched tab | - | Not exercised: no tab with the setting on was at hand and toggling it would change app settings | NOT CHECKED |

Notes: no flash of an empty or error state was seen between shimmer and cards on any tab. Screenshot timing is about 0.7 s granularity, so a frame shorter than that could be missed.
