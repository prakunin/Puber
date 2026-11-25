# Triage round 1: catalogue-filters

Base b7a7d99e, reviewed head 027ed8d3. Lenses: blind (clean), edge, tests, state-path, ux-designer (match), codex (review + adversarial), antigravity (clean).

| # | Source | Severity | Finding | Verified | Mark | Note |
|---|--------|----------|---------|----------|------|------|
| 1 | codex | major | FollowPreference drops anime in the walking path even when Show Anime is on; with hide-watched on, the Anime genre gives an empty grid (and anime vanishes from Movies/Series walks) | yes: isVisible treats FollowPreference as Exclude (ContentListInteractor.kt:182-189) | fix | pass showAnime into isVisible; FollowPreference = no filter when on; unit test both preferences on + Anime |
| 2 | state-path F1 | major | Rail opened from «Фильтры» returns focus to a row-1 card on close | yes, traced onHandoff (ShowAllScreenContent.kt:395-399) | fix | record header ownership; onHandoff no-op while the header held focus; instrumented case Фильтры -> Left -> close rail -> Фильтры |
| 3 | edge F1, codex minor, tests F3 | minor | Old-query page can be published after changeQuery (check and publish not atomic) | yes (ContentListPagingVM.kt:206-208, :293-295) | fix | make check+send atomic with the generation bump (lock or generation-tagged actions); correct the spec rows of the two cancellation-carried tests |
| 4 | state-path F2 | minor | «Сброс» with a cached base page can skip the Loading frame, focus stays on «Фильтры» | plausible (Restart + cached Replace in one frame) | fix | key the grid body on a query generation from the VM; test Content -> Content without Loading |
| 5 | state-path F3, edge unproven | minor | refresh during a new query restores the old depth; a failed read leaves a spinner | yes: changeQuery keeps currentPage | fix | reset currentPage / emptyPageChain / resumeRounds in changeQuery; VM test |
| 6 | tests F1 | major | panel-trap test cannot fail without ContentFocusGate | yes (record) | fix | provide LocalScreenKey in the test host and show it red without the gate; else add a Manual 3 step |
| 7 | tests F2 | major | four P0 red tests green on stub/base | yes (record) | fix | test-designer marks them P0 guard with the reason (or a red run against the named mutation) |
| 8 | tests F4 | minor | Manual 1 tile route, Manual 6 Сброс from filtered-empty on Movies/Series not run | yes | fix | qa round 2 |
| 9 | ux F1 | minor | Panel glass shows poster lettering through rows | measured | accept | the stand lists the material as an open owner choice (AC-13); recommended: owner judges on the box; follow-up only if the owner picks a plain surface |
| 10 | edge unproven | - | Anime filter kept after show-anime turned off walks ~80 empty pages | not confirmed reachable | reject | Settings is a separate tab; on return the panel list hides Аниме; covered partly by #1 |
| 11 | blind/edge unproven | - | items null cached as empty genre list | API check shows items present for every type | reject | no evidence |
| 12 | blind unproven | - | config and cacheable written separately | result dropped by generation; #3 hardens it | reject | covered by #3 |
| 13 | codex rejected / qa | - | 4K «Сброс» lands in the cached-payload error | pre-existing | defer | cached-payload-row-too-big |
