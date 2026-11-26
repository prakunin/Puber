# Gate - catalogue-filters-country-year-status - round 1

**Decision:** CONCERNS
**Head:** 7c3fd1b6   **Base:** 90e69aff

## Done as asked

The diff (`git -C /home/dev/orca/workspaces/Puber/catalogue-filters-country-year-status diff 90e69aff...HEAD`) adds the «Страна», «Год» and «Статус» doors and pages, unwraps `countries`, sends `country`/`year`/`finished` through `SectionConfig` -> `fetchPage` (named args) -> `getItems`, extends the store and the header summary, and documents the change (`docs/architecture/paging-and-filters.md`, `docs/design/showall/`). Out of scope holds: no «Выходящие» (`StatusChosen(false)` maps to `null`, `ShowAllVM.kt` StatusChosen branch), no free year range, no «Популярные», no ratings or quality. `fixedWidth = null` keeps the old `widthIn(min, max).width(IntrinsicSize.Max)` chain in the same order (`TvSidePanel.kt`).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | box API probe | `docs/superpowers/evidence/2026-11-25-catalogue-filters-country-year-status-api-check.md` | PASS |
| AC-2 | `getCountries_unwrapsTheItemsOfAStatusItemsBody` | check.full green, 2644 tests, 0 failures (re-run here, UP-TO-DATE at HEAD) | PASS |
| AC-3 (unit) | `getItems_sendsCountryYearAndFinishedAsTheServerReadsThem`, `filteredBrowseConfig_sendsCountryYearAndFinishedWithTheBaseType`, `countryChosen_panelClosed_readsTheCountryQueryUncachedAndSummarisesIt`, itest `filterSummary_namesGenreCountryYearStatusThenSort` | check.full; itest 28/28 on the box (device evidence, Round 2) | PASS |
| AC-3 (device) | M1: returned items list X | not run: box account 403 `user_banned` | NOT CHECKED |
| AC-4 | `applyTo_yearRange_sendsTheExactYearOrAnInclusiveRange`, `yearChosen_panelClosed_sendsThePresetAndSummarisesIt`, `YearOptionsTest` | check.full; itest `yearLabel_*` | PASS |
| AC-5 (unit) | `statusChosen_onSeriesGrids_sendsFinished`, `statusDoor_isUnavailableOnMovieDocumovieConcertAnd4k`, `statusChosen_onAMovieGrid_isIgnored`, itest `statusDoor_absentWhenStatusUnavailable` | check.full; itest | PASS |
| AC-5 (device) | M1 root, M3, M6 | 4 doors on the movies grid seen (Round 2); series 5 doors and 4K not reachable | NOT CHECKED |
| AC-6 | `filterPanelClosed_withEveryFilterSet_readsOneQueryCarryingAllOfThem`, `firstPageCache_isSeparatedByCountryYearAndFinished`, `firstPageCache_withNoBrowseFilter_keepsTheKeyShapeOfBeforeTheFilters` | check.full | PASS |
| AC-7 (unit) | `write_thenRead_returnsCountryYearAndFinished`, `read_entryOfThePreviousVersion_hasNoCountryYearOrStatus`, `restoredCountryYearAndStatus_headerShowsSummaryBeforeTheFirstPage` | check.full | PASS |
| AC-7 (device) | M4: force-stop, relaunch, summary on first frame | not run (403) | NOT CHECKED |
| AC-8 | `filterReset_clearsCountryYearAndStatusRemovesTheEntryAndReadsCached` | check.full | PASS |
| AC-9 | `countryLoadFailure_showsFailedAndKeepsGenresUsable`, `countriesRetry_loadsAgainAfterAFailure`, itest `countriesFailed_showsTheErrorRowAndFocusableRetry` | check.full; itest; on the box the 403 showed the error row and «Повторить» | PASS |
| AC-10 | `ShowAllFilterFocusTest` incl. `rootDoors_downReachesCountryYearAndStatus`, `newPagesBack_returnToTheDoorTheyCameFrom`, `countryPage_*`, `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`, `yearPage_aStoredYearOutsideThePresets_isTheCheckedRowAndTakesFocus` | 28/28 on 192.168.1.110:5555 at e286d189 (device evidence, Round 2); M1/M2 on the real 102-country list not run | PASS |
| AC-11 | M3 screenshots, owner judges against the stand | not run (403) | NOT CHECKED |

Sampled: all 26 named P0/fix tests exist in `app/src/test` and `app/src/androidTest`; bodies of the cache-key, status, store, restore, reset and country/year focus tests assert what the rows claim.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| M1-M6 and AC-11 not run on the device | review-tests-1 F1 (major), triage-1 #1, triage-2 #4 | open: blocked on the box session | no commit possible; carried as NOT CHECKED, listed below |
| cacheKey always grew three parts, missing every pre-upgrade cached page | review-codex-1 (minor), triage-1 #2 | fixed | e286d189 `browseFilterKeyParts`; `firstPageCache_withNoBrowseFilter_keepsTheKeyShapeOfBeforeTheFilters` |
| stored year off the presets: no checked row on «Год» | review-ux-1 F1 (minor), triage-1 #3 | fixed | e286d189 `ShowAllFilterPanel.kt` YearPage; itest ran on the box |
| «Любой статус» on a series grid untested | review-tests-1 F2 (minor), triage-1 #4 | fixed | e286d189 `statusChosen_anyStatusOnASeriesGrid_clearsFinished` |
| last-country assertion could not fail | review-tests-1 F3 (nit), triage-1 #5 | fixed | e286d189 `countryPage_downReachesTheLastCountry` |
| off-list year itest compiled only | review-tests-2 F1 (minor), triage-2 #1 | fixed | qa round 2: 28/28 on the box |
| off-list year test does not assert selected/click | review-tests-2 F2 (nit), triage-2 #2 | accepted | row is `selected = true` unconditionally; VM path unit-tested |
| stand README lacks the off-list «Год» row | review-ux-2 F1 (nit), triage-2 #3 | accepted | for the owner's AC-11 look |
| raw 403 body in evidence | codex-1 (rejected) | rejected, accepted | spot-checked: evidence quotes only `{"status":403,"error":"user_banned"}`; `tools/mobile-evidence-audit.sh` passed here |
| focus loss when the off-list year row is removed | codex-2 adversarial (rejected) | rejected, accepted | spot-checked: the `if` row is its own group before the `forEach` in a plain `Column`; preset rows keep their slots. Device transition still part of AC-11 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Country page is a `LazyColumn` keyed by id outside the outer `verticalScroll`; countries payload-cached 30 min and kept in the VM; `yearOptions` and `statusAvailable` computed once per VM. No concern |
| Reliability | Load failure and retry (`countriesJob` guard), cancellation through the VM scope, restart via the store, old entries decode with defaults. Unproven (blind, edge): a 200 `countries` body without `items` caches an empty list for 30 min with no Retry; AC-1 evidence shows `items` present |
| Maintainability | Mirrors `GenreInteractor`/`FilterGenres`; `getItems` called by name; Koin `singleOf`; strings in `main_screen_strings.xml` and `values-en`; docs updated. No dead code |
| Security / privacy | No secrets or account data in code or docs; evidence audit passed (`evidence_audit_status=passed`, 362 files) |
| TV | Focus paths covered by the box itest; real-list D-pad (M1, M2) and visuals (AC-11) pending |

## Manual checks for the owner

All need a working KinoPub session on `192.168.1.110:5555` (now 403 `user_banned`). Restore or re-pair the session first; the worktree APK at e286d189 is installed. Steps are the spec's Manual checks table (`docs/superpowers/specs/2026-11-25-catalogue-filters-country-year-status.md`, Test design / Manual checks).

| Item | Steps | Expected |
|------|-------|----------|
| M1 (AC-3, AC-5, AC-10) | «Фильмы» -> «Все» -> «Фильтры» -> «Страна» -> «США» -> Back, Back | 4 doors; 102 countries; header «США»; cards are US titles; focus back on «Страна», then «Фильтры» |
| M2 (AC-10) | reopen «Страна»; Down to the last country, Down once more | opens on «США»; last row whole and focused; extra Down stays |
| M3 (AC-5, AC-11) | «Сериалы» -> «Все»: «Драма», «США», «2010-е», «Завершённые», «по году ↓»; close | 5 doors; one-line summary in that order; finished 2010-2019 series |
| M4 (AC-7, AC-8) | force-stop, relaunch, open «Сериалы» -> «Все»; then «Сброс» | summary on the first frame, focus on the first card; reset clears all three and reloads |
| M5 (R10) | player settings, About, Episodes panels on this build and on a master build | widths identical |
| M6 (AC-5) | «4K» -> «Фильтры» | «Страна», «Год», no «Статус» |
| AC-11 | screenshots of root (4 and 5 doors), «Страна», «Год» (incl. an off-list year), «Статус» | match `docs/design/showall/README.md` starting values; owner judges |

## Must fix

none

## Concerns for the owner

1. AC-3, AC-5, AC-7 device halves, M5 and AC-11 are NOT CHECKED: the box account answers 403 `user_banned`. No code defect is known; triage row "M1-M6 + AC-11" stays open until the manual checks above run.
2. The ban appeared after the AC-1 probe and qa runs; its cause is unknown (qa-report Round 1 MANUAL).
