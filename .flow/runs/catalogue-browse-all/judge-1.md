# Gate - catalogue-browse-all - round 1

**Decision:** CONCERNS
**Head:** c2de93ff   **Base:** e4d75215

## Done as asked

The diff adds `TabTypeConfig.browseEntryFor` / `showAllTitle`, `ContentListAction.BrowseAll`, the entry as section 0's header (`BrowseAllEntry.kt`, `BrowseEntryFocus.kt`), `ShowAllTitle` passed to `ShowAllScreen` and drawn by `ShowAllHeader`, RU and EN strings, and both design stands. The only caller of `ShowAllScreen(` is `ContentListVM.kt:146`, so every grid gets a title. No filter, sort or query change; `ContentListPagingVM.config` untouched. One change outside the architect's file list: `SectionVM.dispatchListState` publishes the paginator's replayed initial Empty as `Loading` (538f5a48, triage-4 #1); `SectionVM` is used only by `ContentListScreen`, and refresh paths go through `refreshPagingKeepingContent` (`PagingVM.kt:150`), not through Loading. c2de93ff touches only evidence, so check.full at f4d4b22e (qa round 5) covers the code head; not re-run.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | TTC `browseEntryFor_labelsEachCatalogueTabWithItsOwnString`, `browseEntryFor_isNullForTabsWithoutAnEntry`; M-1 | qa r1 seven tabs (M1c-*, M1d-*, W0-4k); r5 cold Movies, Series, 4K, Concerts (R6-10..R6-22). Cartoons/Anime unreachable from the rail, unit test only | PASS |
| AC-2 | SRFT `browseEntryUpFromFirstShelfCardThenDownReturnsToThatCard`, `browseEntryIsNotFocusableWhileItsSectionIsLoading`; M-2 | itest-r5 SRFT 19/0 failures; R6-31..R6-35 | PASS |
| AC-3 | CLVM `browseAll_navigatesToShowAllWithTheTabsAllConfigAndTitle`; TTC `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab` | check.full green at f4d4b22e (qa r5) | PASS |
| AC-4 | CLVM `browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle`; M-5 | check.full; qa r1 M-5, R5-16 | PASS |
| AC-5 | SAVM paging guards; M-4 | qa r1 M-4 (M4-c-row12, M4-c-row30); ShowAll paging code unchanged since | PASS |
| AC-6 | TTC `showAllTitle_*`; SACF `showAllHeaderShowsTheTitleAndFirstFocusStaysOnTheFirstCard`; `ShowAllTitleResolveTest` | itest-r5 SACF 8/0; itest-r4 ShowAllTitleResolveTest 3/0; headers seen r1, r4 | PASS |
| AC-7 | M-3 | Steps 1-4 PASS (R6-37..R6-41). Step 5 (after a details trip the first Back from the grid stays on the grid) fails, reproduced via the shelf tile too (R5-19..R5-21); deferred to `showall-first-entry-rail-focus` | PASS with deferred defect (Concerns 1) |
| AC-8 | SACF, SARC | itest-r5 SACF 8/0, SARC 6/0 | PASS |
| AC-9 | TTC `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab`; CLIT `followPreference_whenAnimeIsHidden_excludesAnimeAndRetainsMissingGenres` | check.full | PASS |
| AC-10 | SRFT whole class incl. `browseEntryHeaderKeepsSectionZeroFlushWithTheViewportTop`; TTC | itest-r5 SRFT 19/0; check.full | PASS |
| AC-11 | M-6 | Screenshots collected; owner judgement | NOT CHECKED (manual, section below) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| AC-7 fix not confirmed on device | edge-1 F1, tests-1 F1 (major) | fixed | qa r2 M-3 on c0664ac0, evidence a8e4cbbc |
| All-Empty tab: entry visible, never focusable | codex-2 (major) | fixed | 109b7b0b |
| No evidence at head; saved SRFT XML red | tests-2 F1 (major) | fixed | itest-r3/r4/r5 XML, all green |
| Launch focus / ownership on Empty, Error, Retry | edge-1 F2, state-path-1 F1, blind-1 F2 | fixed | c0664ac0, 96c7a9c3 |
| Anchor restore when entry never focused | blind-1 F1 | fixed | 04ba8f1a |
| SRFT copies production gating | tests-1 F2, blind-1 F3 | fixed | `BrowseEntryHeader` shared, c0664ac0 |
| `ShowAllTitle.resolve` untested | tests-1 F3 | fixed | a9828e0f |
| Wrong doc-series shot; pre-fix after-Back frames | ux-1 F1, F2 | fixed | qa r2 recapture |
| Settle on replayed Empty | edge-3 F1, state-path-4 F1 | fixed | e96a6c91, 538f5a48 (SectionVMTest) |
| Loading to all-Empty drops focus | codex-3, tests-3 F1 | fixed | e96a6c91 |
| Hero + all-Empty untested; no r4 XML | tests-4 F1, F2 | fixed | f4d4b22e, itest-r4 |
| Tests drive the scene's ownership-clear call | tests-2 F2 | accepted minor | triage-2 #3; M-3 on device guards it |
| Stale `rememberBrowseEntryFocus` KDoc on replayed Empty | state-path-5 | accepted nit | `BrowseEntryFocus.kt` KDoc still names the replay; real |
| SRFT timing not pinned to test clock | codex-4 P3 | accepted nit (not in triage-4) | preference; tests green over five rounds |
| Section 0 back to Loading while entry focused | edge-1 unproven | rejected, accepted | spot-checked: `refreshFirstPage` uses `refreshPagingKeepingContent` (`ContentListPagingVM.kt:104-108`, `PagingVM.kt:150`), no Loading |
| Replace(empty) without Loading stays Loading | codex-5 note | rejected, accepted | spot-checked: `storeLeftItsInitialState` set by any non-Empty state; reset passes Loading first |
| Show all opens with rail focus; second Back after details | codex-1, codex-4 adversarial | deferred | run `showall-first-entry-rail-focus`, log 2026-11-24 |
| 4K «Все» request error | qa r1 defect 2 | deferred | run `cached-payload-row-too-big` (source catalogue-browse-all) |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | `isOwner` toggles recompose section items, as `focusedSectionIndex` already does; entry lambdas remembered; focus retry bounded to 3 frames. `BrowseAllEntry`/`ShowAllHeader` not in the baseline profile until regeneration (architect risk) |
| Reliability | Empty, Error, Loading and all-Empty paths covered by SRFT; ownership survives process death via `BrowseEntryFocus.Saver`; `ShowAllScreen` parcel gains `title`, old back stacks do not restore across update (accepted R10). Hide-watched tab not exercised on device (qa r5) |
| Maintainability | Follows AGENTS.md conventions (pure content, VM-owned config, strings in resources incl. values-en); gating in one shared place; stale KDoc nit above |
| Security / privacy | `tools/mobile-evidence-audit.sh docs/superpowers/evidence com.kino.puber.stage` re-run here: passed, 282 files; no secrets in the diff |
| TV | Focus, D-pad, rail and Back covered by M-1..M-3 and SRFT/SACF/SARC; Back defect in Concerns 1 |

## Manual checks for the owner

1. AC-11 / M-6: on 192.168.1.110:5555 with this branch's stage APK, open Movies; compare the entry «Все фильмы» unfocused (first card focused) and focused (D-pad Up) with `docs/design/catalogue/`; press OK and compare the header «Все фильмы» with `docs/design/showall/`. Screenshots: `docs/superpowers/evidence/2026-11-24-catalogue-browse-all-screens/R6-11-movies-b.png`, `R6-31-m2-entry.png`, `R6-38-m3-grid2.png`. Accept or dial the stand sliders.

## Must fix

none

## Concerns for the owner

1. AC-7 M-3 step 5: after a details round trip, the first Back from any Show all grid (entry or shelf tile) does not leave the grid; a second Back does. Deferred to `showall-first-entry-rail-focus`; qa reproduced it via the shelf tile on this branch, not on a base build.
2. 4K «Все» shelf and «Всё в 4К» grid show «Не удалось выполнить запрос»; the entry opens a broken grid on 4K until `cached-payload-row-too-big` lands.
3. Codex did not review the round-5 diff b23f7a51..f4d4b22e (usage limit; owner: do not wait); triage-5 asks for a re-run after reset.
4. During verify fix round 1 the implementer opened a film on the box for a few seconds (state.md log); a small watch position may have been written to the account.
