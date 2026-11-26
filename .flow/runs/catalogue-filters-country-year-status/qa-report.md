Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; 2642 unit tests, 0 failures/errors/skipped in the result XMLs; test task was UP-TO-DATE, i.e. an identical earlier run passed)
DEVICE: 192.168.1.110:5555 (installed worktree APK sha256 e0f75d9c...0584, verified on device). Session rejected server side (HTTP 403 user_banned), so real-data checks were not possible.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | api-check evidence file (done before spec) |
| AC-2 | PASS | KinoPubApiClientCountriesTest in the green unit run |
| AC-3 | PASS (unit, itest); device NOT CHECKED | ShowAllVMTest, ContentListInteractorBrowseFilterTest, KinoPubApiClientItemsFilterTest; itest filterSummary_namesGenreCountryYearStatusThenSort. M1 (returned items list X) not run |
| AC-4 | PASS | ContentFilterQueryTest, YearOptionsTest, ShowAllVMTest; itest yearLabel_* |
| AC-5 | PASS (unit, itest); device NOT CHECKED | ShowAllVMTest statusDoor_*; itest statusDoor_absentWhenStatusUnavailable. M3/M6 not run |
| AC-6 | PASS | ShowAllVMTest one-query test, interactor cache-key test, ContentFilterQueryTest |
| AC-7 | PASS (unit); device restart NOT CHECKED | CatalogueFilterRepositoryTest, ShowAllVMTest restored test. M4 not run |
| AC-8 | PASS (unit) | ShowAllVMTest filterReset test. M4 reset part not run |
| AC-9 | PASS | ShowAllVMTest countries failure/retry, CountryInteractorTest, itest countriesFailed_* |
| AC-10 | PASS | ShowAllFilterFocusTest 27/27 on the box (rootDoors, newPagesBack, countryPage far-down/last/landing, yearPage, cold open). M1/M2 on the real 102-country list not run |
| AC-11 | NOT CHECKED | No filter panel reachable on the box (account banned); no screenshots of root page (4/5 doors) or the three new pages. Owner judges; needs a working session |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-device.md (audit passed, uncommitted)
DEFECTS: none found in code. Environment: every API call from the box returns {"status":403,"error":"user_banned"}; the Home screen showed cached rows only.
UNAVAILABLE: device manual checks M1-M6 and AC-11 screenshots: KinoPub account/session of the box is rejected (user_banned). I stopped per the session caveat; nothing uninstalled, cleared or re-paired. Lease released. Emulator not used (box-only rule).
MANUAL: 1) Owner: check why the KinoPub account is banned (possibly rate limiting from the AC-1 probe or app traffic) and restore access, or re-pair. 2) Then re-run QA (APK already installed, sha verified) for M1-M6 and take AC-11 screenshots (root with 4 and 5 doors, Страна, Год, Статус) against docs/design/showall/. 3) M5: compare player settings, About and Episodes panel widths with a master build screenshot.

---

Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL)
DEVICE: 192.168.1.110:5555 (worktree APK at e286d189, sha256 4dfcda42...56b4, on-device sha256 identical; install-preserve outcome installed). Session still banned: every API call returns 403 user_banned; Home and the grid show cached content.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1, AC-2, AC-6, AC-8 | PASS | unchanged from round 1 |
| AC-3, AC-5, AC-7 | PASS (unit, itest); device NOT CHECKED | M1 list, M3, M4, M6 need a working session |
| AC-4 | PASS | unit tests; itest incl. yearLabel_* |
| AC-9 | PASS | unit + itest countriesFailed_*; on device the 403 showed "Не удалось загрузить страны" + Повторить; retry shows the spinner (second request got no response for 30+ s, server side) |
| AC-10 | PASS | ShowAllFilterFocusTest 28/28 on the box incl. the new off-list year test (triage-2 row 1 closed); M1/M2 on the real 102-country list NOT CHECKED |
| AC-11 | NOT CHECKED | root with 4 doors seen on the movies grid (no Статус); 5 doors, Страна, Год, Статус pages and the off-list-year transition unreachable without a session |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-device.md (Round 2 section appended, audit passed, uncommitted)
DEFECTS: none. Environment: account still user_banned.
UNAVAILABLE: M1 list, M2, M3, M4, M5, M6 and AC-11 screenshots (session banned). Nothing re-paired, uninstalled or cleared. Lease released.
MANUAL: owner restores the KinoPub session on the box, then re-run M1-M6 and AC-11 (APK at e286d189 is installed); M5 needs a master build screenshot pair.

---

Round 3

CHECK: not re-run (code unchanged since e286d189; round 2 was pass)
DEVICE: 192.168.1.110:5555 (worktree APK e286d189, on-device sha256 4dfcda42...56b4 identical to the build). Session still rejected: 403 user_banned on every API call; the app shows "Не удалось выполнить запрос" with Повторить.
ACS: unchanged from round 2; M1-M6 and AC-11 NOT CHECKED (no session).
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-device.md (Round 3 section appended, uncommitted)
DEFECTS: none. Environment: account still user_banned.
UNAVAILABLE: device manual checks M1-M6 and AC-11 screenshots, session banned. Nothing re-paired, uninstalled or cleared; auth-save not taken (nothing worth saving). Lease released.
MANUAL: owner restores KinoPub access for the account on the box, then re-run M1-M6 and AC-11.

---

Round 4

CHECK: not re-run (code unchanged since e286d189; round 2 pass)
DEVICE: 192.168.1.110:5555 (worktree APK e286d189, sha256 4dfcda42...56b4, on-device identical). New account works: no 401/403. Human-paced key presses only, one cold start, no itest, no scripted requests. Lease released.
ACS (device halves):
| AC | Status | Evidence |
|----|--------|----------|
| AC-3 | PASS | Сериалы + Великобритания + 2010-е + Завершённые: summary in order, page 1 all 2011-2019 (12 cards); country and finished are server side, not visible per card; Буркина-Фасо variant shows the filtered-empty state with the filter named and Сброс |
| AC-4 | PASS | Год page lists Любой год, 2026, 2025, 2020-е ... 1980-е, до 1980; 2010-е checked and shown on the door. Off-list year row NOT CHECKED |
| AC-5 | PASS | movies 4 doors, series 5 doors, 4K 4 doors (no Статус); Статус page has Любой статус and Завершённые only |
| AC-7 | PASS | force-stop and relaunch: header carries the filter on the first frame, focus on the first card |
| AC-8 | PASS | Сброс clears the filter and reloads the plain grid |
| AC-10 | PASS (unit and itest earlier; real list scroll seen) | real country list scrolls in the panel; end of the 102 rows not walked |
| AC-11 | captured, owner judges | root 4 and 5 doors, Страна, Год, Статус, summary, empty state screenshots |
M5: Настройки/Общие identical layout on master and this build; player settings and Episodes panels NOT CHECKED (need playback).
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status/docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-device.md (Round 4 appended, audit passed, uncommitted)
DEFECTS: none
UNAVAILABLE: none beyond the NOT CHECKED items (M5 player panels, last-row Down on the 102 list, off-list year, per-title country and finished verification)
MANUAL: owner looks at the screenshots in /tmp/claude-1000/-home-dev-Puber/5257593d-23a9-490e-b468-6f40de2c5fe8/scratchpad (w-7-root4, w-13-root5, w-8-country-top, w-9-country-far, w-14-year, w-18-status-page, w-23-filtered-grid, w-25-filtered-grid, w-37-4k-root) against docs/design/showall; checks player settings and Episodes panel widths if wanted.
