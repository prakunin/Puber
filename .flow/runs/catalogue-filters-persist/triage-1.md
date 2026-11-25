# Review triage - round 1

Run: catalogue-filters-persist   Base: ef5987ec   Reviewed head: c205c62e

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial), review-antigravity-1.md (ran, clean), review-state-path-1.md. ux-designer not run: the diff changes no layout or visuals (a canFocus flag only).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | state-path F1 | minor | `ShowAllScreenContent.kt:176-205`, `ShowAllHeader.kt:95` | Cold open with a restored filter that pages empty leaves nothing focused; the empty-state «Сброс» (the designed exit) never requests focus | Read ShowAllEmpty: focusRequester(resetFocus) only, no requestFocus; header gated during Loading; new path reachable from a stale stored genre | fix | ShowAllEmpty requests focus on its «Сброс» on a cold open when nothing is focused; itest case added |
| 2 | blind F2, edge F1 | minor | `ShowAllVM.kt:364-380, 187-195` | A changed filter on a grid with a skipped «Аниме» overwrites the stored genre | design-architect.md: "The next changed filter the user applies replaces the stored entry, «Аниме» included" - behaviour is the agreed design; but the KDoc "The entry itself stays" overstates it | fix (comment only) | KDoc states the entry stays until the next changed filter; behaviour unchanged by design |
| 3 | tests F1 | minor | `ShowAllVMTest.kt` | AC-5 reset never tested on a VM built from a stored filter | Read the reset tests: all start from an empty store | fix | new test: stored dramaByYear, FilterReset -> FirstPageCall(allMovie, cacheable = true), entry gone |
| 4 | tests F2 | minor | `ShowAllVM.kt:375-377` | animeFilterMode == FollowPreference branch of the anime skip untested | All AC-8 tests use allMovie | fix | new test restoredAnime_onANonFollowPreferenceGrid_isApplied on all_4k |
| 5 | tests F3 | minor | `ShowAllFilterFocusTest.kt` | Only the Loading-phase assertion discriminates; unmarked | Read implementation record (fix 121feab7) | fix | comment marks the guard assertion |
| 6 | blind F1 | minor | `CatalogueFilterRepository.kt:24`, `ShowAllVM.kt:90` | First prefs read on the main thread at VM construction | Same pattern as NavigationPreferencesRepository / PlayerPreferencesRepository (getSharedPreferences in the constructor, synchronous reads from VMs); design risk 6 records it; getSharedPreferences starts the load on a background thread and the file holds a few short entries | reject | consistent with every preferences repo in the app and accepted as design R6; no measured stall |
| 7 | blind F3 | nit | `ShowAllVMTest.kt` shelfGrid_neverReadsOrWritesTheStore | A read is not detected | Test asserts no transaction and the cacheable unfiltered first call, which fails if the guard is removed | reject | the behaviour is guarded; the name traces to test-design.md |

## Rejected codex claims

- apply() may lose a write on a kill inside milliseconds - not from ordinary use; AC-1 device evidence covers kill after apply.
- «Сброс» unreachable while a slow restored query loads - the gate is the agreed design (spec), opens on any non-Loading state, Back leaves the screen; «Фильтры» had the same gate on base.

## Next

- Fix round 1 (items 1-5) -> check.fast and touched tests -> qa-device on the box for item 1 (new itest case, AC-1 focus) -> incremental review (edge, tests, codex, antigravity, state-path) on c205c62e..HEAD; unproven lines: 7
