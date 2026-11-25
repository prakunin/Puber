# Gate - catalogue-filters-persist - round 1

**Decision:** PASS
**Head:** 91a90d71   **Base:** ef5987ec

## Done as asked

`git diff ef5987ec...91a90d71` matches spec Design: new `CatalogueFilterRepository` (prefs `catalogue_filters`, key `filter_<config.id>`, nullable-field DTO, `ignoreUnknownKeys`, enum by `name` with fallback), `singleOf` in `data/di/modules.kt`, `initialCacheable` on `ContentListPagingVM`, private primary + public secondary `ShowAllVM` constructor with `restoreFilter` (filterable gate, anime skip), write on a changed close, clear on reset, `canFocus` on header «Сброс», one sentence in `docs/architecture/paging-and-filters.md`. One addition beyond Design: `ShowAllEmpty` requests focus on its «Сброс» (`ShowAllScreenContent.kt:186-202`), the exit for a restored filter that pages empty; it came from review finding triage-1 #1. Nothing from parts 2 and 4 of the catalogue-filters split.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached`, `restoredFilter_headerShowsSummaryAndResetBeforeTheFirstPage`, `coldOpenWithRestoredFilter_focusesTheFirstCardNotReset`; M1 | qa round 2 red/green of the itest; qa round 3 M1 on the 3e5248d0 APK; unit XMLs 2617/0/0 | PASS |
| AC-2 | `filterApplied_reopenedGridStartsWithIt`; M2 | qa round 3 | PASS |
| AC-3 | `filterOnMovies_isNotInheritedBySerialOr4k`; M3 | qa round 3 | PASS |
| AC-4 | `restoredFilter_firstPageReadsOnlyTheFilteredQueryUncached` (exact call list, `cacheable = false`) | unit only, by nature | PASS |
| AC-5 | `filterReset_removesTheStoredFilterAndTheReopenedGridReadsCached`, repository `write_inactiveFilterRemovesTheEntry`, `clear_removesOnlyThatGrid`; M2 | qa round 3, `catalogue_filters.xml` empty map after reset | PASS |
| AC-6 | `restoredFilter_panelDraftEqualsIt`, `restoredFilter_closedUnchanged_writesNothingAndDoesNotRepage` | qa round 1 unit and device panel ticks | PASS |
| AC-7 | repository `read_malformedJson_*`, `read_unknownKeys_*`, `read_unknownSortField_*`, `read_unknownDirection_*`, `read_genreWithoutTitle_*` | unit | PASS |
| AC-8 | `restoredAnime_withShowAnimeOff_*`, `_withShowAnimeOn_*`, `_onANonFollowPreferenceGrid_*`, `_closedUnchanged_keepsTheStoredGenre`; M4 | qa round 3 M4, prefs kept genreId 25 | PASS |
| AC-9 | `shelfGrid_neverReadsOrWritesTheStore` | unit | PASS |

Sampled: all 25 named tests resolve to `fun` declarations in `app/src/test` and `app/src/androidTest`; qa round 3 mutations a-d red, classes green on the box.

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Empty-state «Сброс» never takes focus on a restored filter that pages empty | triage-1 #1 | fixed | b1f0cd09; mutation a red |
| KDoc overstated that a skipped «Аниме» entry stays | triage-1 #2 | fixed | 87c7be8c |
| Reset from a stored filter, non-FollowPreference anime branch untested; guard assertion unmarked | triage-1 #3-5 | fixed | 87c7be8c, 18d72ea4 |
| Prefs read on the main thread at VM construction | triage-1 #6 | rejected, accepted | same pattern as `NavigationPreferencesRepository.kt:23`; design risk R6 |
| Shelf test does not detect a read | triage-1 #7 | rejected, accepted | test fails if the `filterable` gate is removed (cacheable base call) |
| Empty-state request re-fires on the outgoing tab during a tab pick | triage-2 #1 | fixed | b0cee092, d6ec6134; mutation c red |
| Tab-pick guard also blocked the arriving tab | triage-3 #1 | fixed | d6ec6134; mutation d red |
| Tests address header «Сброс»; arriving test started no handoff (blocker, major) | triage-4 #1-2 | fixed | 3e5248d0; qa round 3 red under c and d |
| Outgoing Empty first composed during the pick window briefly grabs «Сброс» | triage-4 #4 (minor) | accepted | transient, tab disposed on swap; same rule as `ContentFocusHandoffEffect` |
| detekt ComplexCondition `ShowAllScreenContent.kt:198` | qa round 3 defect | fixed | 91a90d71; condition identical by reading |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One small prefs file read once per grid construction; no hot-path allocation; no extra first-page load (AC-4). Baseline profile not regenerated (accepted design risk) |
| Reliability | Undecodable entry reads as no filter; stale genre falls to the filtered-empty state, now with focus on its «Сброс»; `apply()` write persists across force-stop (M1) |
| Maintainability | Follows the preferences-repo pattern and Koin `singleOf`; Koin factory updated (`ShowAllScreen.kt:54`); no dead code; architecture doc updated |
| Security / privacy | Stores only genre id, title and sort names; `tools/mobile-evidence-audit.sh` on the evidence dir: passed; screenshots with account lists not committed |

check.full re-run at 91a90d71 (`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`): BUILD SUCCESSFUL, tasks up to date with the head's inputs; unit XMLs 2617 tests, 0 failures, 0 errors. Device runs were on 3e5248d0; 91a90d71 changes only the split of one condition.

## Manual checks for the owner

N/A: every device check was run on the box by qa-device.

## Must fix

none

## Concerns for the owner

none
