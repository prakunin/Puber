Round 5

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass at f4d4b22e (BUILD SUCCESSFUL, mostly up to date)
DEVICE: 192.168.1.110:5555 (own dev APK via install-preserve, outcome installed, on-device sha256 matches; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (device) | M-1 cold on Movies, Series, 4K, Concerts: shimmer then cards, first card focused, entry unfocused, no empty or error flash seen (R6-10..R6-22). 4K "Все" shelf error not examined (deferred) |
| AC-2 | PASS | M-2 on Movies (R6-31..R6-35); SectionRowFocusTraversalTest 19/19 |
| AC-6, AC-8 | PASS (itest) | ShowAllScreenContentFocusTest 8/8, ShowAllRailCloseFocusTest 6/6 |
| AC-7 | PASS steps 1-4 (device); step 5 known defect, not re-run | M-3 steps 1-4 (R6-37..R6-41). Step 5 is deferred to showall-first-entry-rail-focus, unchanged |
| AC-10 | PASS (itest) | SectionRowFocusTraversalTest 19/19 |
| SectionVM change | PASS | SectionVM is used only by ContentListScreen (catalogue tabs); Home rows load as before; check.full green incl. SectionVMTest |
| Hide-watched tab | NOT CHECKED | setting not exercised; toggling would change app settings |
| AC-3..AC-5, AC-9 | unchanged | not re-run beyond the unit suite |
| AC-11 | NOT CHECKED | owner judgement |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-browse-all/docs/superpowers/evidence/2026-11-24-catalogue-browse-all-device.md (section Round 5; XML in 2026-11-24-catalogue-browse-all-itest-r5/; screenshots R6-*); audit passed; committed
DEFECTS: none new. Known and deferred: M-3 step 5 (two BACKs after a details round trip), 4K "Все" error
UNAVAILABLE: none (Cartoons and Anime not reachable from the rail, as before)
MANUAL: owner to judge AC-11 / M-6 from screenshots; optionally check a tab with hide-watched on

---
Round 4

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass at b23f7a51 (BUILD SUCCESSFUL, mostly up to date)
DEVICE: 192.168.1.110:5555 (own dev APK via install-preserve, outcome installed, on-device sha256 matches; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (device) | M-1 cold on Movies, Series, Concerts (and Doc. series): first card focused, entry unfocused, labels shown. R5-01, R5-22, R5-23, R5-15. Cartoons and Anime not reachable from the rail (unit test covers no-entry) |
| AC-2 | PASS | M-2 on Movies: UP to filled entry, DOWN to card 2, no trailer after 3.5 s, LEFT rail, RIGHT back to entry (R5-02..06); SectionRowFocusTraversalTest 18/18 |
| AC-6 | PASS | ShowAllTitleResolveTest 3/3, ShowAllScreenContentFocusTest 8/8; header "Все фильмы" and "Все док. сериалы" seen |
| AC-7 | FAIL (step 5 only, unchanged) | M-3 steps 1-4 pass (BACK from the grid lands on the entry twice; card opened from the shelf is framed after BACK; R5-07..11). Step 5 unchanged from round 3: after a details round trip, UP, CENTER, BACK leaves the grid (toast "press Back again"), second BACK reaches the entry (R5-12..14) |
| AC-8 | PASS (itest) | ShowAllScreenContentFocusTest 8/8, ShowAllRailCloseFocusTest 6/6 |
| AC-10 | PASS (itest) | SectionRowFocusTraversalTest 18/18 |
| AC-3..AC-5, AC-9 | unchanged | not re-run beyond the unit suite in check.full |
| AC-11 | NOT CHECKED | owner judgement |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-browse-all/docs/superpowers/evidence/2026-11-24-catalogue-browse-all-device.md (section Round 4; XML in 2026-11-24-catalogue-browse-all-itest-r4/; screenshots R5-*); audit passed; committed d4ec57cd
DEFECTS:
1. (known, deferred to showall-first-entry-rail-focus, not re-opened) M-3 step 5: unchanged on b23f7a51. It ALSO happens via the shelf "Показать все" tile, so it is independent of the entry: Doc. series, «Все» shelf, a shelf card opened (details) and closed with BACK, RIGHT to the tile, CENTER (grid), BACK -> grid stays with card 1 framed and the toast "Нажми «Назад» ещё раз, чтобы выйти" (R5-19..21). Before any details trip one BACK pops, via the entry and via the tile (R5-08, R5-09, R5-16, R5-17).
UNAVAILABLE: Cartoons and Anime tabs cannot be reached from the rail on this build.
MANUAL: owner to judge AC-11 / M-6 from screenshots (R5-01, R5-02, R5-07).

---
Round 3

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass at 109b7b0b (BUILD SUCCESSFUL, mostly up to date)
DEVICE: 192.168.1.110:5555 (own dev APK via install-preserve, outcome installed, on-device sha256 matches; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-7 | FAIL | M-3 steps 1-4 pass (BACK from the grid lands on the entry, twice; a card opened from the shelf is framed after BACK). Step 5 fails: after a details round trip, UP, CENTER, BACK leaves the browse grid on screen (card 1 framed, rail collapsed); a second BACK reaches the entry. Movies and Doc. series. R4-20, R4-21, R4-35, R4-36, R4-45..47 |
| AC-1 | PASS (device) | M-1 on Movies, Series, Concerts and Doc. series, all cold in this app run: labels shown, first card focused, entry unfocused. Cartoons and Anime not reachable from the rail (unit test covers no-entry) |
| AC-2 | PASS | M-2: UP to filled entry, DOWN to card 2, no trailer after 3.5 s, LEFT rail, RIGHT back to the entry; SectionRowFocusTraversalTest 17/17 at head |
| AC-6 | PASS | ShowAllTitleResolveTest 3/3, ShowAllScreenContentFocusTest 8/8; headers "Все фильмы" and "Все док. сериалы" seen |
| AC-8 | PASS (itest) | ShowAllScreenContentFocusTest 8/8, ShowAllRailCloseFocusTest 6/6 at head |
| AC-10 | PASS (itest) | SectionRowFocusTraversalTest 17/17 (the round-2 red test is green) |
| AC-3..AC-5, AC-9 | unchanged | not re-run beyond the unit suite in check.full |
| AC-11 | NOT CHECKED | owner judgement |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-browse-all/docs/superpowers/evidence/2026-11-24-catalogue-browse-all-device.md (section Round 3; XML in 2026-11-24-catalogue-browse-all-itest-r3/; screenshots R4-*); audit passed; committed 4b8febf2
DEFECTS:
1. AC-7 / M-3 step 5: after a card of the shelf was opened (details) and closed with BACK, the first BACK from the browse grid does not leave the grid. Repro on 109b7b0b: cold start, Movies tab (LEFT, DOWN x4, CENTER), UP to "Все фильмы", CENTER, BACK (pops, entry focused), DOWN, CENTER on a card (details), BACK, UP, CENTER (grid), BACK -> grid still shown, card 1 framed, rail collapsed; BACK again -> tab with the entry focused. Repeated 3 further CENTER/BACK cycles on Movies: all needed two BACKs. Same on Doc. series. Before any details trip one BACK pops (cold Movies, cold Doc. series). Not compared with the shelf Show all tile path, so it is unknown whether it predates the entry. No unit or instrumented test covers it (SACF/SARC pass).
UNAVAILABLE: Cartoons and Anime tabs cannot be reached from the rail on this build.
MANUAL: owner to judge AC-11 / M-6 from screenshots (R4-03, R4-08, R4-13).

---
Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass at 6c35732d (BUILD SUCCESSFUL). Not re-run at c0664ac0.
NOTE: three commits (04ba8f1a, a9828e0f, c0664ac0) landed in the worktree while the session was interrupted, so the head is now c0664ac0, not 6c35732d. Itests ran on c0664ac0. Manual checks ran on 6c35732d, and M-3 and the rail were repeated on c0664ac0.
DEVICE: 192.168.1.110:5555 (own dev APK via install-preserve, outcome installed both times; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-7 | PASS (device) | M-3 on 6c35732d and c0664ac0: Back lands on the entry cold, warm, with and without RIGHT into the grid, and with card 2 as last focus (DOWN then returns to card 2). Rail LEFT/RIGHT/BACK from the entry returns to the entry. Screens R2-12..R2-20, R3-04..R3-08 |
| AC-2 | PASS (device), itest FAIL | M-2: UP to the entry, DOWN to card 2, rail round trip OK. SectionRowFocusTraversalTest fails 1 of 16 on c0664ac0 (see defect 1) |
| AC-1 (Cartoons/Anime) | NOT CHECKED on device | The rail has no Cartoons or Anime tab, so they cannot be opened. Unit test browseEntryFor_isNullForTabsWithoutAnEntry covers it |
| AC-8 | PASS | ShowAllScreenContentFocusTest and ShowAllRailCloseFocusTest green on c0664ac0. M-5 step 3 on Doc. series: card opened from the grid is focused again after BACK (R2-31) |
| AC-1, AC-3..AC-6, AC-9, AC-10 | unchanged from round 1 | not re-run beyond the above; AC-10 depends on SRFT (see defect 1) |
| AC-11 | NOT CHECKED | owner judgement |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-browse-all/docs/superpowers/evidence/2026-11-24-catalogue-browse-all-device.md (section Round 2; result XML in 2026-11-24-catalogue-browse-all-itest-r2/; screenshots R2-* and R3-*); audit passed; committed a8e4cbbc
DEFECTS:
1. SectionRowFocusTraversalTest.launchFocusSkipsTheEntryWhenSectionZeroIsEmpty fails on c0664ac0 (SectionRowFocusTraversalTest.kt:513, assertIsFocused on row-1-item-0, Focused = false), three runs out of three. Repro: make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.contentlist.content.SectionRowFocusTraversalTest. The other 15 tests pass. On the device no matching symptom: the cold tab opens on the first card. I did not check whether this test passed on 6c35732d.
2. 4K "Все" error: deferred, not re-opened.
UNAVAILABLE: Cartoons and Anime tabs cannot be reached from the rail on this build.
MANUAL: owner to judge AC-11 / M-6 from screenshots; confirm where Cartoons and Anime can be opened, if anywhere.

---
Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass
DEVICE: 192.168.1.110:5555 (worktree dev APK installed with install-preserve, outcome installed; lease released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | M-1 on 7 tabs, labels seen; unit tests green. Cartoons/Anime not opened on device |
| AC-2 | PASS | M-2 (Up, Down to card 2, Left rail, Right back to entry); SRFT itest green |
| AC-3, AC-4 | PASS (unit) | CLVM/TTC green; Show all tile of "Все" shelf shows same title as entry (M-5) |
| AC-5 | PASS | M-4: 30 rows of paging on Movies grid |
| AC-6 | PASS | headers "Все фильмы", "Док. Сериалы · Новинки", "Все док. сериалы"; SACF green |
| AC-7 | FAIL | M-3: Back from the browse grid lands on the shelf card, not the entry |
| AC-8 | PASS | SACF, SARC itest green |
| AC-9, AC-10 | PASS | unit and SRFT green |
| AC-11 | NOT CHECKED | owner judgement, screenshots collected |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-browse-all/docs/superpowers/evidence/2026-11-24-catalogue-browse-all-device.md
DEFECTS:
1. AC-7 / R1: Back from the browse grid does not refocus the entry. Repro: cold start, Movies, UP to "Все фильмы" (filled), CENTER, optionally RIGHT into the grid, BACK -> card 1 of "Новинки" is framed, entry unfocused; CENTER then opens that film. Same on a warm tab and with card 2 as the last focus (V1-c, V2-c screenshots). Reproduced 4 of 4 times.
2. 4K: both the "Все" shelf and the entry grid show "Не удалось выполнить запрос" (possibly pre-existing, not compared with base).
UNAVAILABLE: none
MANUAL: owner to judge AC-11 / M-6 from the screenshots; check 4K "Все" error against a master build; Cartoons/Anime absence of entry.
