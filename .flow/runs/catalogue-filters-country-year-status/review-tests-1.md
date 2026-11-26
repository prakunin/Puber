LENS: tests  ROUND: 1  BASE: 90e69aff

### F1 [major] AC-11 has no evidence, and the device halves of AC-3, AC-5 and AC-7 were never run
- Where: /home/dev/Puber/.flow/runs/catalogue-filters-country-year-status/qa-report.md (ACS table, UNAVAILABLE); spec «Manual checks» M1-M6
- Problem: AC-11 (owner judges the doors and pages against the stand) has no automated test and no screenshots. It was marked NOT CHECKED because every API call from the box returns `user_banned`. The device parts of the spec were not run either: AC-3 "returned items list X" (M1), AC-5 "device for the door" (M1/M6), AC-7 cold restart (M4), the real 102-country list (M1/M2), and the panel-width comparison (M5). The qa report says so honestly. Still, nothing done in this round shows that the server honours the new params from inside the app, or that the new pages match the stand.
- Failure scenario: suppose the 320 dp country page clips titles, or the stand sizes differ. Gate 2 sees no screenshot, so nothing fails. Likewise, if the real response has a field shape the unit fixtures don't cover, no test or check catches it.
- Fix: once the session is restored, re-run qa-device for M1-M6 and take the AC-11 screenshots: root with 4 and with 5 doors, «Страна», «Год», «Статус». Until then, keep AC-11 and the device rows open at gate 2.

### F2 [minor] Choosing «Любой статус» on a series grid is not tested
- Where: app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt (`StatusChosen` branch, `if (action.finishedOnly) true else null`); app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllFilterPanel.kt:279 sends `StatusChosen(false)`
- Problem: every VM test that sends `StatusChosen(false)` runs on `allMovie`, where the action is ignored. On a series grid it is only ever sent with `true`. The focus-test fake does the false-to-null mapping itself, so it says nothing about the VM. If someone changes the branch to `copy(finished = action.finishedOnly)`, no test fails.
- Failure scenario: on `all_serial` the user picks «Завершённые», applies it, then picks «Любой статус» and closes. With the mutated branch the draft gets `finished = false`. The request then carries `finished=0`, which the server ignores. The filter still counts as active, so the read is uncached and the header shows «Сброс» with an empty status part. The suite stays green.
- Fix: add one VM test on `allSerial`. Apply `finished = true`, then send `FilterPanelOpened`, `StatusChosen(false)` and `FilterPanelClosed`. Assert that the last call has `config.finished == null` and `cacheable == true`, and that `filterState.finishedOnly == false`.

### F3 [nit] One assertion in the last-country focus test can never fail on its own
- Where: app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllFilterFocusTest.kt, `countryPage_downReachesTheLastCountry`, last line
- Problem: `onNode(isFocused() and hasText(last)).assertIsFocused()` looks up a node that is already focused and then checks that it is focused. The only thing it really checks is that such a node exists.
- Fix: use `awaitFocusOnRow(countryTitle(CountryCount), …)` after the extra Down, or `onNodeWithText(countryTitle(CountryCount)).assertIsFocused()`.

Suite state: check.full not re-run (green in qa round 1, 2642 tests). ShowAllFilterFocusTest 27/27 on the box. Traceability, red runs, removed assertions, cache key, movie-grid status and fixedWidth null path checked and adequate.

### Unproven
- `ShowAllVMTest.kt` `restoredCountryYearAndStatus_headerShowsSummaryBeforeTheFirstPage`: the `headerOnly` VM is never cancelled (same as the existing restoredFilter test). Matters only if the suite shows flakiness from a leaked `Dispatchers.Default` job.
