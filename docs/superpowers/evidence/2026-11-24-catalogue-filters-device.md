# catalogue-filters: device evidence, round 2

Head 426b5e99 (tree clean at install). Build: worktree dev debug APK, sha256 7c14d6056866e51454372cfd84fcfc662f02b66265824e7dbb8f935ee1dd2849, install-preserve outcome `installed` (compatible_replace, version 1.12.6-dev); the on-device base.apk sha256 matches. Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only, lease held and released, auth-save taken first (.auth/ stays untracked). No setting changed, no account data written. Playback: one film was started by CENTER on the details screen's Watch button while navigating (about 3 s of a film, no progress written by me; BACK left it); this was not intended and the frames of it are not kept. Screenshots: 2026-11-24-catalogue-filters-shots/r2-*.png (frames that showed watch state, progress or the player were deleted).

Check: `./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL (up to date from the identical head, lint ran).

## Instrumented (already run at head, XML in 2026-11-24-catalogue-filters-itest-r2/, not re-run)

| Class | Tests | Failures | Errors |
|---|---|---|---|
| ShowAllFilterFocusTest | 14 | 0 | 0 |
| ShowAllRailCloseFocusTest | 8 | 0 | 0 |
| ShowAllScreenContentFocusTest | 9 | 0 | 0 |

## Manual checks (round 2)

| # | Steps | Expected | Observed | Status |
|---|---|---|---|---|
| 1 | Movies tab; «Все фильмы» entry CENTER; BACK; row «Все», RIGHT to the «Показать все» tile (13 tile focused, frame deleted after reading), CENTER | Title + «Фильтры» in the header, first focus on card 1 | Entry: grid opens with the rail expanded and holding focus (02-entry-open), RIGHT puts the frame on card 1 and shows «Все фильмы» + «Фильтры» (03-entry-after-right). Tile: same two states (13-tile-open, 14-tile-after-right). The expanded rail holding focus until RIGHT is the browse-all base behaviour (round 1 note). | PASS (entry and tile; first focus is card 1 after the rail hand-off, as on base) |
| 2 | Card col 1: UP, DOWN; col 6: UP, DOWN; «Фильтры» LEFT, then close the rail with RIGHT and with BACK | UP lands on «Фильтры», DOWN returns to the same row-1 card; LEFT opens the rail; closing puts focus on «Фильтры» | 20-up-col1 («Фильтры» highlighted), 21-down-col1 (card 1 framed), 22-up-col6, 23-down-col6 (card 6 framed), 24-rail-open (LEFT), 25-rail-closed-by-right («Фильтры» highlighted again). BACK with the rail open does not close it: it pops «Все фильмы» to the Movies tab, with focus on the «Все фильмы» entry (checked twice, once from «Фильтры» and once from a card, same result; frame 26-rail-closed-by-back). | PASS for RIGHT. BACK: FAIL against the expectation; same pop from a card, so it is not caused by the filter header (see defect 1) |
| 3 | «Фильтры» CENTER; BACK (no change); reopen, «Сортировка» -> «по году», «по возрастанию», BACK, «Жанр» -> «Драма», BACK, BACK; watch focus while the list lands | Open: focus on «Сортировка»; first BACK closes on «Фильтры», grid not reloaded; after the change: «Фильтры» focused while the list loads, then card 1 | 30-panel-open, 31-back-nochange (same grid, «Фильтры» highlighted), 32-sort-page, 33-sort-chosen («по году», «по возрастанию» ticked), 34-main-after-sort (door «Сортировка» «по году ↑»), 35-genre-page, 36-drama-chosen, 37-main-drama («Жанр» «Драма», panel still open). Second BACK: 38-after-close-0 and 39-after-close-1 show the spinner with «Фильтры» «Сброс» «Драма · по году ↑» and «Фильтры» focused (no card, no rail focus, no old list); 40-after-close-settled: first cards 1903, 1906, 1909, 1911, 1912, 1914 (oldest dramas), card 1 framed. The change is applied when the panel closes, so a list cannot land under an open panel by remote; the gate step is the Loading frame above. | PASS |
| 4 | With the filter of step 3: UP, RIGHT to «Сброс», CENTER; same on Series | After reset: default order, «Сброс» and summary gone, «Фильтры» focused while loading, then card 1 | Movies: 41-reset-focused («Сброс» highlighted), 42-reset-0 (spinner, header «Фильтры» only, «Фильтры» focused), 43-reset-1 and 44-reset-settled (default order, Антарктика first, card 1 framed). Series: Драма + по рейтингу ↓ applied (frames deleted, watch marks); reset -> spinner with «Фильтры» focused -> default list with card 1 framed (frames deleted). | PASS |
| 5 | Series, «Драма» active, card 3 of row 2 (Шанхай) CENTER (details), BACK | Same filter and summary, same card framed, no reload to unfiltered | 70-details-before (header «Драма», card 3 of row 2 framed), 72-after-details-back (identical frame: summary «Драма», same card framed, list unchanged). Player leg not run (no playback by design). | PASS for details; player leg NOT CHECKED |
| 6 | Series: genre «Водевиль» until empty; DOWN from «Фильтры»; CENTER | Filtered-empty text with «Сброс»; DOWN reaches it; «Сброс» restores | 66-series-vaudeville («Ничего не найдено. Активен фильтр: Водевиль» with «Сброс»), 67-series-empty-down («Сброс» highlighted after DOWN); CENTER: spinner, then the full Series grid with card 1 framed (frame deleted). Movies has no empty genre: «Водевиль» gives one title (80-movies-vaudeville), «Боевик» a full grid; the Movies empty case is covered on the same code path by Series and by 4K in round 1. | PASS (Series); Movies empty NOT REACHED |
| 7 | Screenshots for the owner | owner compares to docs/design/showall | 02, 03, 30-40, 41-44, 66, 67 in the shots folder (round 1 frames also kept) | owner |

Other: the genre list loads on Series too (61-series-genre-page deleted, seen; «Аниме» offered because show-anime is on); sort by rating descending on Series gave the 9.x ratings first; Movies filter «Боевик» gave an action grid and the header summary «Боевик» (no sort part when only the genre differs).

## Defects and notes (round 2)

1. BACK with the expanded rail open on a Show all grid pops the screen instead of closing the rail. Reproduction: Movies tab, «Все фильмы» entry CENTER, RIGHT (card 1 or «Фильтры» focused), LEFT (rail expands), BACK: the Movies tab is shown, focus on the «Все фильмы» entry. It reproduces from a card as well as from «Фильтры», so it is the behaviour the deferred run showall-rail-back-exits-app describes, not something the filter header adds. I did not run base to compare.

# Round 1 (kept for history)

Head f1ee2102 (tree clean at install). Build: worktree dev debug APK, sha256 8acef6aede9babce9e5dd838ad4f14f3351c10c78ecdd66ecd6dd64dd353d06f, install-preserve outcome `installed` (compatible_replace); the on-device base.apk sha256 matches. Device: TV box 192.168.1.110:5555, package com.kino.puber.stage only, lease held and released, auth-save taken first (.auth/ stays untracked). Playback not started, no account data written, no setting changed. Screenshots: 2026-11-24-catalogue-filters-shots/ (frames that showed watch state or progress were deleted, so some steps below are text only). A film details screen was opened on purpose (Manual 5) and left with BACK.

## Instrumented (one class per make call, XML in 2026-11-24-catalogue-filters-itest-r1/)

| Class | Tests | Failures | Errors |
|---|---|---|---|
| ShowAllFilterFocusTest | 13 | 0 | 0 |
| ShowAllScreenContentFocusTest | 9 | 0 | 0 |
| ShowAllRailCloseFocusTest | 7 | 0 | 0 |

## Manual checks

| # | Steps | Expected | Observed | Status |
|---|---|---|---|---|
| 1 | Movies tab, «Все фильмы» entry, CENTER; Back; 4K entry «Всё в 4K»; Series entry | Title + «Фильтры» in the header, first card focused | Movies and Series: header «Все фильмы» / «Все сериалы» + «Фильтры» (03c-after-right, 04b), the expanded rail holds focus on open until RIGHT, then card 1 is framed; the same behaviour is in the browse-all round 2-4 evidence on base («starts on card 1 and the expanded rail holds focus until RIGHT»). 4K: header + «Фильтры» present, list in error (see defect note 1). «Все» shelf tile not opened (long shelf) | PASS (tile route NOT CHECKED) |
| 2 | Card col 1: UP, DOWN; col 6: UP, DOWN; from «Фильтры» LEFT | UP lands on «Фильтры», DOWN returns to the same row-1 card, LEFT opens the rail | 05-up-from-col1, 05b-down, 06-up-from-col6, 06b-down-col6 (card 6 framed again), 06c-left-from-filters (rail expanded) | PASS |
| 3 | «Фильтры» CENTER, LEFT, RIGHT; BACK; reopen: «Сортировка» -> по году, по возрастанию, BACK; «Жанр» -> Драма, BACK, BACK | Focus on «Сортировка», Left/Right stay in the panel; first Back closes on «Фильтры», grid unchanged; after the change the grid reloads, oldest first, dramas, focus on card 1 | 07-panel-main, 07b (unchanged after LEFT/RIGHT), 08-panel-closed-nochange (same grid, «Фильтры» focused), 09 .. 15 (door focus kept after each sub-page Back; summary row «по году ↑»), 17-applied-list: first cards 1903, 1906, 1909, 1911, 1912, 1914 (oldest dramas), card 1 framed | PASS |
| 4 | Read header; UP, RIGHT to «Сброс», CENTER | Summary «Драма · по году ↑»; after reset default order, «Сброс» and summary gone, focus on «Фильтры» while loading, then card 1 | 17: «Фильтры» «Сброс» «Драма · по году ↑»; 18-reset-focused; 20-after-reset-settled: default order (Антарктика first), no «Сброс», no summary, card 1 framed. The reset list came from cache in under 150 ms, so the intermediate «focus on Фильтры while Loading» frame could not be captured (19-after-reset-immediate already shows the landed list) | PASS (loading frame not observed) |
| 5 | Filter «по году ↓» active, card 3 row 2, CENTER (details), BACK | Same filter and summary, card 3 row 2 focused, no reload | After BACK: header summary «по году ↓» still shown, card 3 of row 2 framed (frames deleted: they showed watch progress). Player leg (play a card) not run: playback avoided | PASS for details; player leg NOT CHECKED |
| 6 | Reach a filtered-empty grid | Text with «Сброс»; DOWN from «Фильтры» reaches it; «Сброс» restores | 4K, genre «Водевиль»: «Ничего не найдено. Активен фильтр: Водевиль», in-body «Сброс» (31, 32-empty-down shows it focused after DOWN); CENTER ran the reset and the grid went to the unfiltered 4K read, which is in error (defect note 1). Hide-watched was not switched (settings untouched) | PASS (restore step blocked by note 1) |
| 7 | Screenshots for the owner | owner compares to docs/design/showall | Header with no filter (03c), active filter (17), both panel pages (07, 09, 13), filtered-empty (31) | owner |

Other checks: sort and genre change the order on Movies (year ascending, then year descending: 2026 titles first) and on Series (rating: 9.5, 9.3, 8.6 ... descending, Series frames deleted for watch marks); genre on 4K («Драма» filtered list loaded, 29-4k-drama); genre list loads on Movies and 4K (the `{status, items}` decode works). «Аниме» is offered in the genre list because show-anime is on for this account; the show-anime-off case was NOT CHECKED (setting not changed). Player side panel look after the chrome lift: NOT CHECKED (the panel opens only from the player).

## Notes

1. The 4K unfiltered grid ("Всё в 4K") ends in «Не удалось выполнить запрос». The log carries SQLiteBlobTooBigException from Room while reading the stored first page; it is the deferred cached-payload-row-too-big problem recorded in the browse-all evidence (same screen, base code path, `cacheable = true` unchanged). A filtered query reads uncached and works (29, 31).
2. The panel is a compact right-edge card, vertically centred, as TvSidePanel is written; its top, right edge and corner radii are flush with the screen edge.
