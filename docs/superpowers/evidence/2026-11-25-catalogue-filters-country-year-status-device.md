# Device evidence: catalogue-filters-country-year-status (QA round 1)

Date: 2026-11-25
Build: worktree dev debug APK, sha256 e0f75d9c9b95d9489e36e36425826bdf676f33e73d67b689dbb4bea32b650584 (host file and on-device base.apk identical, checked after install and again after itest). Installed with tools/android-apk-install-preserve: outcome installed, compatible_replace, no destructive action.
Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage only. Lease held and released. auth-save taken before install.

## Instrumented: ShowAllFilterFocusTest (AC-3 summary, AC-4 labels, AC-5 door, AC-9 retry row, AC-10)

make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest -> BUILD SUCCESSFUL, 27 tests, 0 failures, 0 errors, 0 skipped. New cases seen passing: statusDoor_absentWhenStatusUnavailable, countryPage_downReachesTheLastCountry, yearPage_downReachesBefore1980, rootDoors_downReachesCountryYearAndStatus, yearLabel_namesSingleYearDecadeBefore1980AndAnOffListRange, countriesFailed_showsTheErrorRowAndFocusableRetry, filterSummary_namesGenreCountryYearStatusThenSort, countryPage_opensFocusedOnTheSelectedCountryFarDown, countryPage_listLandingAfterLoading_movesFocusToTheSelectedCountry, newPagesBack_returnToTheDoorTheyCameFrom, coldOpenWithRestoredFilter_focusesTheFirstCardNotReset. Result: PASS.

## Manual M1-M6 (real catalogue): NOT RUN

Steps taken: launched the app (main screen with cached Home rows appeared, so the stored session decrypted), opened the "Фильмы" tab. Expected: the tab loads. Observed: the tab stayed on skeletons, then showed "Сессия истекла. Войдите в аккаунт ещё раз." with "Повторить". The app log showed the server answering every request with HTTP 403 body {"status":403,"error":"user_banned"} (HomeVM Unauthorized). One retry did not change it. The account is rejected server side, so no catalogue grid, filter panel on real data, country list, or filtered request can be observed. Stopped per the session caveat; nothing uninstalled or cleared.

M1 NOT RUN, M2 NOT RUN, M3 NOT RUN, M4 NOT RUN, M5 NOT RUN, M6 NOT RUN. No AC-11 screenshots taken (no filter panel reachable).

## Round 2 (head e286d189)

Build: worktree dev debug APK at e286d189, sha256 4dfcda42e31c059dc2691699ed6c79cd66e73430eb324f651ce0da8a325656b4 (on-device base.apk sha256 identical), install-preserve outcome installed / compatible_replace, destructive_action false. Device: 192.168.1.110:5555 (box), lease held and released.

| Check | Observed | Result |
|---|---|---|
| check.full (testDevDebugUnitTest, detektAll, lintDevDebug) | BUILD SUCCESSFUL | PASS |
| make itest ShowAllFilterFocusTest | 28 tests, 0 failures, 0 errors, 0 skipped (the off-list year focus test ran) | PASS |
| Session check | Home and the Movies grid render, but every API call still answers 403 user_banned (body {"status":403,"error":"user_banned"} on /v1/countries and the bookmarks refresh); screens show cached content | still banned |
| M1 root page | Movies, All grid, Filters: four doors (Сортировка, Жанр, Страна, Год), no Статус. Screenshot taken, not stored in the repo | PASS (root part only) |
| M1 / AC-9 on device | Opening Страна: first load showed "Не удалось загрузить страны" with Повторить (403); Повторить showed the spinner and the second request got no response for 30+ s. Failure state and retry render as designed; no list to check | observation |
| M1 list, M2, M3, M4, M6 | need a working session (country list, year and status results, restart summary) | NOT CHECKED |
| M5 | player panels need a master screenshot pair and playback | NOT CHECKED |
| AC-11 | root with 4 doors seen; 5 doors, Страна list, Год, Статус pages, off-list year transition not reachable | NOT CHECKED |

## Round 3 (head e286d189 code, docs 7c3fd1b6)

Device: 192.168.1.110:5555 (box), lease held and released. On-device base.apk sha256 4dfcda42e31c059dc2691699ed6c79cd66e73430eb324f651ce0da8a325656b4, identical to the worktree build, so no reinstall was needed.

| Check | Observed | Result |
|---|---|---|
| Session check | Force-stop and cold launch; the app showed the domain check, then "Не удалось выполнить запрос. Попробуйте ещё раз." with Повторить and Сменить зеркало KinoPub. The log showed the HTTP response {"status":403,"error":"user_banned"} (HomeVM, PopularMovies, Unauthorized). One Повторить press gave the same screen | still banned |
| auth-save, M1-M6, AC-11 screenshots | Not run: no grid or filter panel loads, and saving a banned session adds nothing | NOT RUN |

Stopped per the session caveat. Nothing uninstalled, cleared or re-paired; no master build installed.

## Round 4 (head e286d189, new account paired by the owner)

Device: 192.168.1.110:5555 (box), lease held and released. Human-paced remote key presses only (1-1.5 s apart), no scripted requests, no itest, one cold start on the worktree build. No 401/403 seen. Worktree APK sha256 4dfcda42e31c059dc2691699ed6c79cd66e73430eb324f651ce0da8a325656b4, on-device base.apk identical, install-preserve outcome installed / compatible_replace, destructive_action false. Master build 5cd3e37e was on the box beforehand (session fresh). Screenshots are kept outside the repo (scratchpad), no account data on them.

| Check | Steps | Expected | Observed | Result |
|---|---|---|---|---|
| M1 root, movies (AC-5, AC-11) | Фильмы, Все фильмы, Фильтры | 4 doors, no Статус | Сортировка, Жанр, Страна, Год; summary values «по обновлению ↓», «Все жанры», «Все страны», «Любой год» | PASS |
| M1/M2 country page, real list (AC-10, AC-11) | open Страна on Фильмы, then on Сериалы; Down 25 and 12 times | list from the server, alphabetical, scrolls inside the panel | Все страны (checked) first, then Австралия, Австрия, Азербайджан ... Бразилия, Великобритания; the list scrolls and the focused row stays visible. End of the 102 rows and the last-row Down NOT run (covered by the box itest, 28/28 earlier) | PASS (scroll), end-of-list NOT CHECKED |
| M3 root, series (AC-5, AC-11) | Сериалы, Все сериалы, Фильтры | 5 doors | Сортировка, Жанр, Страна, Год, Статус («Любой статус») | PASS |
| Год page (AC-4, AC-11) | open Год | Любой год, 2026, 2025, 2020-е ... 1980-е, до 1980; checked row opens focused; choosing keeps the page open; Back returns to the door with the value | exactly that list; 2010-е checked and the door read «2010-е» after Back. Off-list year transition NOT CHECKED | PASS |
| Статус page (AC-5) | open Статус | Любой статус, Завершённые (no «Выходящие», dropped per Q2) | exactly those two rows | PASS |
| M3 combination (AC-3, AC-6) | Буркина-Фасо (picked by mistake) + 2010-е + Завершённые, close | filtered-empty state keeps the filter | summary «Буркина-Фасо · 2010-е · Завершённые», text «Ничего не найдено. Активен фильтр: ...» with Сброс | PASS |
| M3 combination, items | change country to Великобритания, close | page 1 matches | summary «Великобритания · 2010-е · Завершённые»; page 1 cards: Блуи 2019, Хинтерланд 2013, Игра престолов 2011, Шекспир и Хэтэуэй 2018, Долина муми-троллей 2019, Слово на букву А 2016, Невиновный 2018, Во плоти 2013, Город и Город 2018, Мальчик в синей футболке 2017, По дороге 2015, Супермаркет 2011: all inside 2010-2019. Country and the finished flag are server side and not visible on the cards (Блуи may carry several countries); not checked per title | PASS (year), country and status by summary only |
| M4 restart (AC-7) | force-stop, cold launch, Сериалы, Все сериалы | summary on the first frame, focus on the first card | header «Великобритания · 2010-е · Завершённые», same first 12 cards, focus on the first card (Блуи) | PASS |
| M4 Сброс (AC-8) | Фильтры row, Сброс | all three clear, grid reloads | Сброс and the summary disappear, unfiltered grid (Во всеоружии: Сила артефактов first) | PASS |
| M6 4K (AC-5) | 4K, Всё в 4K, Фильтры | Страна and Год, no Статус | Сортировка, Жанр, Страна, Год | PASS |
| M5 (R10) | Настройки, Общие on master and on this build | same layout | the Настройки Общие screen (with «О приложении и устройстве») is pixel-for-pixel the same layout on both builds, only the version line differs (1.12.8-dev vs 1.12.7-dev). Player settings and Episodes panels need playback: NOT CHECKED | PARTIAL, player panels NOT CHECKED |
| AC-11 screenshots | taken for root (4 and 5 doors), Страна, Год, Статус, summary, filtered-empty | owner judges | files below | captured |

Notes: on every cold start the app offered the 1.12.8 update dialog; it was dismissed with «Не сейчас». The dev APK is 1.12.7-dev, master reference 1.12.8-dev.

Screenshots (scratchpad, outside the repo): w-7-root4, w-8-country-top, w-9-country-far, w-13-root5, w-14-year, w-16-year-2010, w-18-status-page, w-23-filtered-grid (empty state), w-25-filtered-grid, w-30-restored, w-31-reset, w-37-4k-root, m5-4-master-settings, w-33-settings (all .png in /tmp/claude-1000/-home-dev-Puber/5257593d-23a9-490e-b468-6f40de2c5fe8/scratchpad).
