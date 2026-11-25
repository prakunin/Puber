# Test design: catalogue-browse-all

Base e4d75215. Inputs: `brief.md`, `design-architect.md`, `design-ux.md`. Levels and fakes: `.flow/roles/test-designer.md`.
Unit: `./tools/agentw testDevDebugUnitTest` in the worktree. Instrumented: `make DEVICE=192.168.1.110:5555 itest TESTS=<fqcn>` only. Device: TV box `192.168.1.110:5555`, package `com.kino.puber.stage`.

Test files (all exist; new tests are added to them):

| Short name | Path |
|---|---|
| TTC | `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/model/TabTypeConfigTest.kt` |
| CLVM | `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/ContentListVMTest.kt` |
| SAVM | `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt` |
| CLIT | `app/src/test/kotlin/com/kino/puber/domain/interactor/contentlist/ContentListInteractorTest.kt` |
| SRFT | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/contentlist/content/SectionRowFocusTraversalTest.kt` |
| SACF | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt` |
| SARC | `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllRailCloseFocusTest.kt` |

## Risk profile

| ID | Risk (source) | P | I | Score | Covered by |
|---|---|---|---|---|---|
| R1 | Back from the browse grid lands on the shelf's remembered card, not the entry: `browseEntryFocused` and `isTargetRow` diverge (architect Risks) | 2 | 3 | 6 | M-3. The owner flag lives in `ContentListScreenContent`, which resolves `SectionVM`s through DI; only the device composes it whole |
| R2 | Tab-open focus lands on the entry on a cold tab; the panel shows nothing (architect Risks, Q1) | 2 | 3 | 6 | `browseEntryIsNotFocusableWhileItsSectionIsLoading`, M-1 |
| R3 | Grid header breaks first focus or the AC-8 focus returns (architect Risks) | 2 | 3 | 6 | `showAllHeaderShowsTheTitleAndFirstFocusStaysOnTheFirstCard`, SACF and SARC re-run |
| R4 | Entry grows page 0 or takes its own list item: clipped row, shifted `sectionListItemIndex` (brief Risks, ux budget) | 2 | 2 | 4 | `browseEntryHeaderKeepsSectionZeroFlushWithTheViewportTop`, SRFT re-run |
| R5 | Rail handoff with the entry as owner falls to the blind fallback (architect Risks) | 2 | 2 | 4 | M-2 steps 5-6 |
| R6 | Entry opens a config other than the tab's «Все», or a second key (architect: last `all_` section) | 1 | 3 | 3 | TTC and CLVM rows of AC-3, AC-4 |
| R7 | Anime leaks into the Movies or Series browse grid (AC-9) | 1 | 2 | 2 | `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab` + existing CLIT filter test |
| R8 | Trailer keeps playing while the entry is focused (architect Decisions) | 2 | 1 | 2 | M-2 step 4 |
| R9 | Hide-watched gives short paging steps on the whole-type grid (brief Risks) | 2 | 1 | 2 | Accepted: behaviour unchanged; observed in M-4 |
| R10 | `ShowAllScreen` parcel gains `title`; an old back stack does not restore after update | 1 | 1 | 1 | Accepted: no migration by design |
| R11 | Section 0 `Empty` takes the entry with it (ux D1) | 1 | 2 | 2 | `browseEntryStaysWhenSectionZeroIsEmpty` (P2) |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | unit | P0 red | TTC `browseEntryFor_labelsEachCatalogueTabWithItsOwnString` | Asserts the exact `R.string` per tab for the seven tabs and that the seven are distinct; fails if a tab gets another tab's label or none |
| AC-1 | unit | P0 red | TTC `browseEntryFor_isNullForTabsWithoutAnEntry` | Cartoons, Anime and every non-catalogue `TabType` return `null`; fails if the entry leaks into hero tabs |
| AC-1 | device | P0 | M-1 | Entry with the right label visible above the first shelf on each of the seven tabs |
| AC-2 | instrumented | P1 red | SRFT `browseEntryUpFromFirstShelfCardThenDownReturnsToThatCard` | Scene as `SettleScene` with `SectionListItem(header = { BrowseAllEntry(...) })` on section 0: focus card `row-0-item-1`, Up focuses the entry, Down focuses `row-0-item-1` again; fails if Up skips the entry or Down lands on column 0 |
| AC-2 | instrumented | P1 red | SRFT `browseEntryIsNotFocusableWhileItsSectionIsLoading` | Section 0 `SectionState.Loading`: the entry node is not focusable; fails if the `canFocus` gate is removed (R2) |
| AC-2 | device | P0 | M-1, M-2 | Tab opens on the first card; Up/Down round trip; Left from the entry opens the rail; Right from the rail returns to the entry |
| AC-3 | unit | P0 red | CLVM `browseAll_navigatesToShowAllWithTheTabsAllConfigAndTitle` | VM built with `tabType = Movies`; `ContentListAction.BrowseAll` captures the `ShowAllScreen` passed to `navigateForResult`: `config == TabTypeConfig.sectionsFor(Movies).last()`, `config.type == "movie"`, `sort == "updated-"`, `animeFilterMode == FollowPreference`, `title.titleRes` = the Movies label; fails if the VM opens another section or drops the title |
| AC-3 | unit | P0 red | TTC `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab` | For each of the seven tabs `browseEntryFor(tab).config == sectionsFor(tab).last()`, id starts with `all_`, `sort == "updated-"`, `For4k` has `quality == "4k"`; fails if the lookup picks a non-«Все» section or `TabTypeConfig` reorders sections |
| AC-3 | unit | P1 red | CLVM `browseAll_onATabWithoutAnEntry_doesNotNavigate` | `tabType = Cartoons`: no `navigateForResult` call; fails if a `null` entry falls back to some config |
| AC-3 | unit | P1 red | CLVM `browseAll_returnedChanges_invalidateFirstPageCacheAndRequestSectionRefresh` | The listener captured from `BrowseAll` with a `Watched` change calls `invalidateFirstPageCache` then `requestRefresh`; fails if the entry navigates without the result path |
| AC-3 | unit | P2 red | CLVM `browseAll_stopsAPublishedTrailerPreview` | `previewTrailerUrl` is `null` after `BrowseAll` |
| AC-4 | unit | P0 red | CLVM `browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle` | `BrowseAll` and `ShowAll(sectionsFor(Movies).last())` produce screens with equal `key` (`ShowAllScreen_all_movie`), equal `config` and equal `title`; fails if the shelf tile builds its title differently or the entry gets its own id |
| AC-5 | unit | P1 guard | SAVM `aWalkThatSpentItsBudgetResumesWhereItStopped`, `firstPage_publishesInteractorItemsWithoutAdditionalFiltering` | Paging of an `all_` config unchanged |
| AC-5 | device | P0 | M-4 | Pages append past page 1 on the Movies browse grid |
| AC-6 | unit | P0 red | TTC `showAllTitle_namesTheAllGridWithTheEntryLabel` | `showAllTitle(tab, browseEntryFor(tab).config)` equals `ShowAllTitle(labelRes)` with `prefixRes == null` for the seven tabs; fails if the browse grid shows «Фильмы · Все» |
| AC-6 | unit | P0 red | TTC `showAllTitle_prefixesAShelfTitleWithTheTabTitle` | Movies «Новинки» config gives `titleRes == content_section_fresh`, `prefixRes == TabType.Movies.title`; fails if the tab prefix is dropped |
| AC-6 | instrumented | P0 red | SACF `showAllHeaderShowsTheTitleAndFirstFocusStaysOnTheFirstCard` | `ShowAllScreenContent(title = "Фильмы · Новинки", Content(items))`: the title text is displayed, its node is not focusable, `Card 1` is focused; fails if the header takes first focus or is not drawn |
| AC-6 | instrumented | P2 red | SACF `showAllHeaderIsShownInLoadingAndErrorStates` | Header text present for `Loading` and `Error` |
| AC-6 | device | P0 | M-4 step 2, M-5 | «Все фильмы» on the browse grid; «Фильмы · Новинки» on a shelf grid; first card focused |
| AC-7 | device | P0 | M-3 | Back from the browse grid focuses the entry, twice in a row and after a card round trip (R1) |
| AC-8 | instrumented | P0 guard | SACF `showAllFocusReturnsToTheFocusedCardAfterReentry`, `showAllFocusReturnsToACardBelowTheFirstViewportAfterReentry`; SARC `showAllRailCloseKeepsFocusOnTheLowerRowCard`, `showAllMenuOpenAndBackReturnsFocusToTheOpeningCard`, `showAllRailCloseKeepsFocusOnTheTopRowCardAndAfterScrolling` | Run with a title passed; fail if the header shifts the grid's focus memory or the rail-close restore |
| AC-9 | unit | P0 red | TTC `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab` (Movies, Series assert `FollowPreference`) | Fails if the entry config stops following the anime preference |
| AC-9 | unit | P1 guard | CLIT `followPreference_whenAnimeIsHidden_excludesAnimeAndRetainsMissingGenres` | The interactor drops anime for `FollowPreference` with the preference off |
| AC-10 | instrumented | P0 guard | SRFT whole class | Fails if the header slot changes `SectionListItem` or `sectionListItemIndex` for existing callers |
| AC-10 | instrumented | P1 red | SRFT `browseEntryHeaderKeepsSectionZeroFlushWithTheViewportTop` | With the entry in section 0's header and a hero absent, section 0 and section 1 settle flush with the 205 dp viewport top after Down, and section 0's card bottom stays inside the viewport; fails if the entry adds height to page 0 or becomes its own list item (R4) |
| AC-10 | unit | P0 guard | TTC whole class | Existing section configs unchanged |
| AC-10 | instrumented | P2 red | SRFT `browseEntryStaysWhenSectionZeroIsEmpty` | Section 0 `Empty`: the entry is still displayed and focusable (R11) |
| AC-11 | manual | P0 | M-6 | Owner judges the look |

## Manual checks

Device `192.168.1.110:5555`, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held. Keys: `adb -s 192.168.1.110:5555 shell input keyevent <KEYCODE>`. Screenshot each "Expected" into the evidence folder.

| ID | Steps | Expected | Must not happen |
|---|---|---|---|
| M-1 | 1. Open Movies from the rail (DPAD_RIGHT into content). 2. Repeat for Series, Concerts, Doc. movies, Doc. series, TV shows, 4K. 3. Open Cartoons and Anime | 1-2: label per AC-1 above the first shelf heading; focus on the first card of the first shelf, panel shows that film. 3: no entry | Focus on the entry at tab open; first shelf cards clipped at the bottom; entry on Cartoons/Anime |
| M-2 | 1. Movies, focus on card 2 of the first shelf (DPAD_RIGHT). 2. DPAD_UP. 3. DPAD_DOWN. 4. DPAD_UP, wait 3 s with a trailer-capable film in the panel. 5. DPAD_LEFT. 6. DPAD_RIGHT | 2: entry focused. 3: card 2 focused. 4: no trailer starts or one playing stops. 5: rail opens. 6: entry focused again | Up skipping to nothing; Down landing on card 1; focus lost after the rail closes |
| M-3 | 1. Focus the entry, DPAD_CENTER. 2. BACK. 3. Repeat 1-2. 4. DPAD_DOWN, DPAD_CENTER on a card (details), BACK. 5. DPAD_UP, DPAD_CENTER, BACK | 2, 3, 5: tab shown, entry focused. 4: the card focused | Focus on a shelf card after Back from the grid; focus lost; crash |
| M-4 | 1. Movies entry, DPAD_CENTER. 2. Read the header. 3. DPAD_DOWN until past 3 grid pages | 2: «Все фильмы», first card focused, header not focusable. 3: new cards append, no duplicate, focus keeps moving | Header focused; pages stop after page 1; anime items with «show anime» off |
| M-5 | 1. Movies, «Новинки» shelf, Show all tile. 2. BACK. 3. «Все» shelf Show all tile. 4. On 4K, its «Все» tile | 1: header «Фильмы · Новинки». 3: same grid and header as M-4 («Все фильмы»), focus on the card last focused there. 4: «Всё в 4К» | Different header for the «Все» tile and the entry |
| M-6 | Screenshots of M-1 (Movies, unfocused and focused entry) and M-4 step 2 with the updated `docs/design/catalogue/` and `docs/design/showall/` stands | Owner accepts sizes and the filled or quiet entry (ux D2) | Focused entry cut at its top edge; header pushing row 2 off screen |

## Perf track

N/A: track off.

## UNTESTABLE

AC-11: owner's visual judgement (M-6 collects the evidence).
