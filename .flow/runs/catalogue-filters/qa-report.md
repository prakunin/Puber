Round 2 (head 426b5e99, evidence commit see git log)

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; unit/detekt up to date from identical head, lint ran)
DEVICE: 192.168.1.110:5555 (TV box, com.kino.puber.stage, lease released; install-preserve outcome installed, sha256 7c14d605... matches on-device base.apk)
ACS: Manual 1, 2, 3, 4, 5, 6 as briefed; see evidence file.
Defect 1: BACK with the expanded rail open pops Show all (also from a card). RIGHT closes the rail onto «Фильтры».
Not checked: Movies filtered-empty, player leg of Manual 5, show-anime-off.
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters/docs/superpowers/evidence/2026-11-24-catalogue-filters-device.md

---
Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; test, detekt and lint tasks were UP-TO-DATE from an identical earlier run on this head, lintDevDebug re-ran)
DEVICE: 192.168.1.110:5555 (TV box, com.kino.puber.stage, lease released)
ACS: (AC ids from the test design)
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | ShowAllFilterFocusTest 13/13, ShowAllScreenContentFocusTest 9/9 on the box; Manual 1: header + Фильтры on Movies and Series; the expanded rail holds focus on open until RIGHT, then card 1 is framed (same as base per browse-all evidence). Show all tile route NOT CHECKED |
| AC-2 | PASS (unit not re-run by me) | instrumented unavailable-filter test green; button seen on Movies, Series, 4K browse grids |
| AC-3 | PASS | ShowAllRailCloseFocusTest 7/7; Manual 2 col 1 and col 6: Up to Фильтры, Down back to same card, Left opens rail |
| AC-4 | PASS | Manual 3: panel opens on Сортировка, Left/Right stay inside, Back with no change closes on Фильтры with the same grid, sub-page Back returns to its door |
| AC-5 | PASS | Manual 3: year ascending + Драма gives 1903, 1906, 1909, 1911... first, card 1 framed; Series by rating descending; 4K Драма loads |
| AC-6 | PASS (loading frame not observed) | Summary «Драма · по году ↑» + Сброс; reset restores default order, button and summary gone, card 1 framed. Reset list came from cache too fast to capture the Loading frame |
| AC-7 | NOT CHECKED on device | unit-only; filtered reads worked on the box (uncached path), cache entry not inspectable |
| AC-8 | NOT CHECKED on device | unit-only (race); no flash of an old list seen in any change |
| AC-9 | NOT CHECKED | show-anime is on for this account (Аниме listed); setting not changed |
| AC-10 | PASS | genre lists load on Movies and 4K (real {status, items} body); failure/retry page not reachable |
| AC-11 | PASS for details leg; player leg NOT CHECKED | filter summary and card 3 of row 2 survived details + BACK (frames deleted for watch marks); ShowAllScreenContentFocusTest active-filter test green. Playback avoided |
| AC-12 | PASS (restore blocked by note) | 4K + Водевиль: «Ничего не найдено. Активен фильтр: Водевиль», Сброс reached by Down from Фильтры. Сброс then runs the unfiltered 4K read, which is in error (note 1) |
| AC-13 | owner | screenshots: header (03c), active filter (17), panel pages (07, 09, 13), empty state (31) in docs/superpowers/evidence/2026-11-24-catalogue-filters-shots/ |
Player side panel after the chrome lift: NOT CHECKED (opens only from the player; playback avoided).
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters/docs/superpowers/evidence/2026-11-24-catalogue-filters-device.md (audit passed; committed 027ed8d3); XML in .../2026-11-24-catalogue-filters-itest-r1/
DEFECTS: none caused by this change. Observation 1 (not this diff): the unfiltered "Всё в 4K" grid shows «Не удалось выполнить запрос» (log: SQLiteBlobTooBigException from Room reading the stored first page, the deferred cached-payload-row-too-big issue named in the browse-all evidence). Repro: Movies... 4K tab, UP to «Всё в 4K», CENTER. After a filtered empty state, «Сброс» on 4K lands in this error, so reset-to-grid cannot be shown on 4K. Filtered 4K reads (uncached) work.
UNAVAILABLE: none
MANUAL: owner to judge the panel and header look against docs/design/showall (Manual 7); check the player leg of Manual 5 (play a card, BACK, same filter and card), the player side panel look, and the show-anime-off genre list by hand.
