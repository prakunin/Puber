Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; tasks were UP-TO-DATE from the implementer's identical inputs; result XMLs: 2615 tests, 0 failures, 0 skipped; CatalogueFilterRepositoryTest 8, ShowAllVMTest 40)
DEVICE: 192.168.1.110:5555 (owner box, com.kino.puber.stage, Android 10)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Unit restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached and restoredFilter_headerShowsSummaryAndResetBeforeTheFirstPage green; M1 on box after force-stop: first frame already shows Сброс and "Драма · по году ↑", filtered cards (1903 first), focus on card 1. Instrumented coldOpenWithRestoredFilter test green but see defect 1 |
| AC-2 | PASS | Unit filterApplied_reopenedGridStartsWithIt; M2 on box, reopened via "Все фильмы" filtered |
| AC-3 | PASS | Unit filterOnMovies_isNotInheritedBySerialOr4k; M3 on box: Сериалы and 4K unfiltered, focus on card 1 |
| AC-4 | PASS | Unit restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached (exactly one uncached filtered call). Device cannot show cache use; unit-only |
| AC-5 | PASS | Unit filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached, repository write_inactive/clear tests; M2 on box: after Сброс the reopened grid is unfiltered and catalogue_filters.xml is an empty map |
| AC-6 | PASS | Unit restoredFilter_panelDraftEqualsIt, restoredFilter_closedUnchanged_writesNothingAndDoesNotRepage; box: panel shows Сортировка "по году ↑" and Жанр "Драма", ticks in sub-page, unchanged close did not repage |
| AC-7 | PASS | Repository tests: malformed JSON, unknown keys, unknown sort, unknown direction, genre without title (device not needed) |
| AC-8 | PASS | Unit restoredAnime_* (3 tests); M4 on box: with show-anime off grid is non-empty, no summary; prefs still hold genreId 25; with show-anime on again, Аниме re-applied |
| AC-9 | PASS | Unit shelfGrid_neverReadsOrWritesTheStore. Not driven on device |
TESTS ADDED: none (the branch already carries all rows of the test design)
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters-persist/docs/superpowers/evidence/2026-11-25-catalogue-filters-persist-device.md (audit passed; committed in worktree; screenshots not committed, Home shows watch list)
DEFECTS:
1. Test-quality, not a product bug: ShowAllFilterFocusTest.coldOpenWithRestoredFilter_focusesTheFirstCardNotReset is not red without the fix. Repro: in the worktree restore ShowAllHeader.kt from ef5987ec (git show ef5987ec:app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllHeader.kt), run make DEVICE=192.168.1.110:5555 itest TESTS=...ShowAllFilterFocusTest#coldOpenWithRestoredFilter_focusesTheFirstCardNotReset: 1 test, 0 failures. So the canFocus gate on «Сброс» (ShowAllHeader.kt:95) has no instrumented guard; the test design says it "fails without the gate". On device M1 showed no focus stolen, but it was run with the gate present, so the gate's necessity is unproven. File restored; worktree clean.
UNAVAILABLE: none
MANUAL: none. Note: lease released, box app force-stopped, show-anime restored to on, Movies filter cleared. The installed APK is the branch build 1.12.7-dev. During M1 a mis-navigation briefly opened a title and its "continue watching" prompt; I backed out without choosing, no playback.

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

Final APK at 3e5248d0. Device: owner's TV box 192.168.1.110:5555, com.kino.puber.stage, lease held and released.

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug --rerun-tasks -> FAIL on detekt. `:app:detektDevDebug FAILED: Analysis failed with 1 issues`: complexity ComplexCondition, ShowAllScreenContent.kt:198:13, "This condition is too complex (4). The defined maximum number of allowed conditions is set to '3'" (`if (filter.isActive && contentFocusActive && !outgoingDuringTabPick && !headerHoldsFocus.value)`). Re-run with `--continue` semantics (testDevDebugUnitTest and :app:lintDevDebug separately, --rerun-tasks): both pass, 2617 unit tests, 0 failures, 0 skipped; lint BUILD SUCCESSFUL. Nothing ran from cache.
DEVICE: 192.168.1.110:5555
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Unit tests (round 1) + M1 on final APK: first frame already has «Сброс» and summary, filtered grid, focus on card 1. Empty-filter cold open focuses the empty-state «Сброс» (4K + Водевиль). Instrumented red/green: coldOpenWithRestoredFilter_* and _thatPagesEmpty_* |
| AC-2 | PASS | Unit filterApplied_reopenedGridStartsWithIt; M2 on final APK |
| AC-3 | PASS | Unit test; M3 on final APK (Сериалы, 4K unfiltered) |
| AC-4 | PASS | Unit restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached (unit-only; device cannot show cache use) |
| AC-5 | PASS | Unit tests; M2 reset on final APK, catalogue_filters.xml empty map afterwards |
| AC-6 | PASS | Unit tests (round 1) and device panel ticks (round 1); ShowAllFilterFocusTest 17/17 green |
| AC-7 | PASS | Repository tests (round 1, unchanged) |
| AC-8 | PASS | Unit restoredAnime_*; M4 on final APK |
| AC-9 | PASS | Unit shelfGrid_neverReadsOrWritesTheStore (round 1, not driven on device) |
| Focus: empty-state reset on cold open | PASS | Red with the request removed (mutation a), green on branch; device confirmed |
| Focus: panel closed then empty keeps «Фильтры» | PASS | Red with "&& !headerHoldsFocus.value" removed (mutation b), green on branch; device: focus stayed on «Фильтры» after the panel closed |
| Focus: outgoing empty reset during tab pick | PASS | Red with "!outgoingDuringTabPick" removed (mutation c), green on branch |
| Focus: arriving empty reset during tab pick | PASS | Red with the round-2 condition (mutation d), green on branch; device: tab pick from the filtered-empty 4K grid landed on the arriving Сериалы landing's first card |

Red/green lines (box 192.168.1.110:5555, make DEVICE=192.168.1.110:5555 itest TESTS=<Class#method>):
| Mutation (one at a time, file restored after each) | Test | Result |
|---|---|---|
| a. ShowAllEmpty LaunchedEffect request removed | ShowAllFilterFocusTest#coldOpenWithRestoredFilter_thatPagesEmpty_focusesTheEmptyStateReset | RED: ComposeTimeoutException "Condition still not satisfied after 5000 ms" |
| b. "&& !headerHoldsFocus.value" removed | ShowAllFilterFocusTest#panelClosedThenFilteredEmpty_keepsFocusOnFiltersNotReset | RED: AssertionError "Failed to assert the following: (Focused = 'true')" |
| c. "&& !outgoingDuringTabPick" removed | ShowAllRailCloseFocusTest#tabPickHandoff_outgoingEmptyResetDoesNotTakeFocus | RED: AssertionError "Failed to assert the following: (Focused = 'false')" |
| d. condition replaced by the round-2 one (handoff?.expectsNewContent != true, no bornDuringRequest) | ShowAllRailCloseFocusTest#tabPickHandoff_arrivingEmptyResetTakesFocus | RED: ComposeTimeoutException "Condition still not satisfied after 12000 ms" |

Green on the branch (head 3e5248d0), same device: ShowAllFilterFocusTest 17 tests, 0 failures, 0 errors, 0 skipped; ShowAllRailCloseFocusTest 10 tests, 0 failures, 0 errors, 0 skipped. `git status` clean after the mutations.

TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/catalogue-filters-persist/docs/superpowers/evidence/2026-11-25-catalogue-filters-persist-device.md (audit result below)
DEFECTS:
1. Gate red: detekt ComplexCondition at ShowAllScreenContent.kt:198:13 (4 conditions, limit 3) in the round-3 production change. Reproduce: cd /home/dev/orca/workspaces/Puber/catalogue-filters-persist && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:detektDevDebug. The condition needs to be split (for example an extracted local such as a "mayRequestResetFocus" boolean); production code is not mine to change, so the check stays red.
UNAVAILABLE: none
MANUAL: none. The box: lease released, stage app force-stopped, show-anime on, Movies and 4K filters reset (empty map).
