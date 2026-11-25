# Device evidence: catalogue-filters-persist

Date: 2026-11-25. Device: owner TV box 192.168.1.110:5555 (Android 10, KM6), package com.kino.puber.stage only.
Build: worktree dev debug APK, version 1.12.7-dev, sha256 2604c950006be3f180ea16142761c68047d01a67b3f7a2f85ac55ec5b043d699, installed with tools/android-apk-install-preserve (outcome installed, compatible_replace, app data kept). Branch head 14278faa. Session was logged in before install; auth-save taken first.

Screenshots were taken for review but are not committed (the Home screen shows the account's watch list).

## Instrumented

make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest: 15 tests, 0 failures, including coldOpenWithRestoredFilter_focusesTheFirstCardNotReset.

Red on base: NOT demonstrated. With ShowAllHeader.kt reverted to base (no canFocus gate on Reset) and everything else on the branch, coldOpenWithRestoredFilter_focusesTheFirstCardNotReset still passed (1 test, 0 failures). The test does not fail without the gate it is meant to guard. The worktree file was restored afterwards (git status clean).

## Manual checks

| Check | Steps | Observed | Result |
|---|---|---|---|
| M1 AC-1 | Movies, "Все фильмы", Filters: sort "по году", order "по возрастанию", genre "Драма", Back to apply (header: Сброс, summary "Драма · по году ↑", cards from 1903 upward). am force-stop; launch; Movies; "Все фильмы"; screenshots in a tight loop | First captured frame already shows the header with Сброс and "Драма · по году ↑" over a spinner; then the same filtered grid ("Герои Сарагосы" 1903 first), focus ring on card 1, neither Фильтры nor Сброс focused. No crash, no unfiltered flash, not empty | PASS |
| AC-6 on device | Opened Filters on the restored filter | Rows show "Сортировка: по году ↑", "Жанр: Драма"; sort sub-page has "по году" and "по возрастанию" ticked. Closing the panel unchanged showed the same grid with no loading spinner | PASS |
| M2 AC-2 | Back to the Movies landing, "Все фильмы" again | Opens filtered, focus on card 1 | PASS |
| M2 AC-5 | Focus Сброс, press it; Back; "Все фильмы" again; read catalogue_filters.xml | After reset: unfiltered grid ("Меня зовут Карло" first), no summary, no Сброс, focus on card 1. Reopened: unfiltered. File: empty map, no filter_all_movie | PASS |
| M3 AC-3 | With Movies filtered (before reset), open "Все сериалы" and "Всё в 4K" | Both unfiltered (no Сброс, no summary), default order, focus on card 1, no crash | PASS |
| M4 AC-8 | Apply "Аниме" on Movies; Settings > Content > turn "Аниме в общих списках" off; reopen Movies grid | Grid shows cards, not empty; header has no summary and no Сброс (genre skipped, sort was default). catalogue_filters.xml still held genreId 25 (Аниме), sort UPDATED, direction DESC. After switching the setting back on, the grid reopened with "Аниме" applied | PASS |

Cleanup: show-anime setting restored to on; Movies filter reset (file is an empty map); app force-stopped; lease released.

Incident, no effect on the checks: after the first relaunch a wrong rail step opened an item and the player; I backed out at the "Продолжить просмотр с 8:58?" prompt without choosing either button.

Note on the rail: after Left from a grid the rail always started on «История», not on the current tab. Not checked against base; the diff does not touch the rail.

## Round 2

Scope: the cold-open-with-restored-filter row (Defect 1 of round 1). Fix commit 121feab7 changes androidTest only; production code is unchanged since round 1. Device: owner's TV box 192.168.1.110:5555, package com.kino.puber.stage, lease held and released.

- Red run: ShowAllHeader.kt temporarily restored from ef5987ec (no Reset gate), `make DEVICE=192.168.1.110:5555 itest TESTS=com.kino.puber.ui.feature.showall.content.ShowAllFilterFocusTest#coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`. Result: FAILED, `java.lang.AssertionError: Failed to assert the following: (Focused = 'false') Text = '[Сброс]'`. The file was then restored; `git status` in the worktree is clean.
- Green run: the whole class ShowAllFilterFocusTest on the branch, same device. Result: BUILD SUCCESSFUL, 15 tests, 0 failures, 0 errors, 0 skipped (including coldOpenWithRestoredFilter_focusesTheFirstCardNotReset, 2.0 s).
- Stage app after the runs: installed 1.12.7-dev, launched, home screen with account content shown (still logged in). No uninstall, data clear or downgrade.

| AC / row | Status | Evidence |
|----|--------|----------|
| Cold open with a restored filter focuses the first card, not Reset | PASS | Fails on the pre-fix header (Reset focused), passes on the branch |
| Other ShowAllFilterFocusTest rows | PASS | 15 of 15 green |

DEFECTS: none (Defect 1 closed).

## Round 3

Device: owner TV box 192.168.1.110:5555 (Android 10, KM6), package com.kino.puber.stage only. Build: worktree dev debug APK at head 3e5248d0, 1.12.7-dev, sha256 42fe94880d65e449c572044ad65d56fb36cb2234576f83b519eb4d09247ef994, installed with tools/android-apk-install-preserve (outcome installed, compatible_replace, app data kept). Stage was logged in before install (Home showed the account's lists); auth-save taken first (.auth, gitignored). Lease taken and released.

### Instrumented red and green

| Mutation (one at a time, file restored after each) | Test | Result |
|---|---|---|
| a. ShowAllEmpty LaunchedEffect request removed | ShowAllFilterFocusTest#coldOpenWithRestoredFilter_thatPagesEmpty_focusesTheEmptyStateReset | RED: ComposeTimeoutException "Condition still not satisfied after 5000 ms" |
| b. "&& !headerHoldsFocus.value" removed | ShowAllFilterFocusTest#panelClosedThenFilteredEmpty_keepsFocusOnFiltersNotReset | RED: AssertionError "Failed to assert the following: (Focused = 'true')" |
| c. "&& !outgoingDuringTabPick" removed | ShowAllRailCloseFocusTest#tabPickHandoff_outgoingEmptyResetDoesNotTakeFocus | RED: AssertionError "Failed to assert the following: (Focused = 'false')" |
| d. condition replaced by the round-2 one (handoff?.expectsNewContent != true, no bornDuringRequest) | ShowAllRailCloseFocusTest#tabPickHandoff_arrivingEmptyResetTakesFocus | RED: ComposeTimeoutException "Condition still not satisfied after 12000 ms" |

Green on the branch (head 3e5248d0), same device: ShowAllFilterFocusTest 17 tests, 0 failures, 0 errors, 0 skipped; ShowAllRailCloseFocusTest 10 tests, 0 failures, 0 errors, 0 skipped. `git status` clean after the mutations.

### Manual checks on the final APK

| Check | Steps | Observed | Result |
|---|---|---|---|
| M1 AC-1 | Movies, "Все фильмы", Filters: sort "по году", order "по возрастанию", genre "Драма", Back to apply. am force-stop, launch, Movies, "Все фильмы", screenshots right after Select | First frames show header with «Сброс» and "Драма · по году ↑" over a spinner, then the filtered grid ("Герои Сарагосы" 1903 first), focus ring on card 1, not on «Фильтры» or «Сброс». No crash, no unfiltered flash | PASS |
| M2 AC-2 | Back to the Movies landing, "Все фильмы" again | Opens filtered, focus on card 1 | PASS |
| M3 AC-3 | With Movies filtered: open "Все сериалы" and "Всё в 4K" | Both unfiltered (no «Сброс», no summary), focus on card 1 | PASS |
| M2 AC-5 | Movies grid, focus header «Сброс», press Select; Back; "Все фильмы" again; read shared_prefs/catalogue_filters.xml via run-as (genre ids and sort only) | After reset: unfiltered grid, no summary, focus on card 1; reopened: unfiltered; file is an empty map | PASS |
| M4 AC-8 | Apply "Аниме" on Movies. Settings > Контент > "Аниме в общих списках" off. Reopen Movies grid | Grid shows cards, not empty; no summary, no «Сброс». catalogue_filters.xml still held genreId 25, UPDATED, DESC. After switching the setting on, the grid reopened with "Аниме" applied. Then reset: file is an empty map | PASS |
| Empty filter cold open | 4K grid, Filters > Жанр "Водевиль" (an ordinary-UI filter that pages empty: "Ничего не найдено. Активен фильтр: Водевиль" with «Сброс»). am force-stop, launch, "Всё в 4K" | Empty state shown with filter summary; focus ring on the empty-state «Сброс», not on «Фильтры». (After closing the panel instead, focus stayed on «Фильтры».) | PASS |
| Tab pick from the empty state | Same state: Left to the rail, pick "Сериалы" | Arriving Сериалы landing took focus on its first card, rail closed, no focus stuck on the outgoing «Сброс» | PASS |

Cleanup: 4K and Movies filters reset (catalogue_filters.xml is an empty map), show-anime setting on, app force-stopped, lease released. Screenshots were taken for review and not committed (they show the account's watch list).

Notes: after a cold launch the rail's first-focused item varies (История, Я смотрю, Док. Фильмы and Главная were seen), so blind DPAD paths mis-navigate; one mis-step opened a series page and trailer preview, backed out without choosing playback.
