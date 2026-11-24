# Browse a whole content type as a titled poster grid from every catalogue tab

Status: approved
Date: 2026-11-24
Slug: catalogue-browse-all
Kind: feature
Size: M
Tracks: ui

## Problem / Why

Owner, 2026-11-24: «сейчас нет возможности просмотреть все к примеру Сериалы и нет фильтрации». This run covers the first half: a direct entry per content type that pages the whole type in the catalogue-poster-grid grid. Filtering is part 3, run `catalogue-filters`, which builds on this run's grid.

Current state, API and options: `research.md: Current state in code`, `External findings`, `Options`. This brief takes Option A. Checked against the code at e4d75215:

| Where | Fact |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/model/TabTypeConfig.kt:25-41` | Tab to sections; the last section of each catalogue tab is «Все» (`all_<type>`, `all_4k`): `type` (or `quality = "4k"`), `sort = "updated-"`, tab's anime mode: already a whole-type query |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListVM.kt:141-148` | `openShowAll(config)` navigates for result to `ShowAllScreen(config)`; returned `ContentChangeSet` refreshes the tab |
| `app/src/main/java/com/kino/puber/ui/feature/showall/ShowAllScreen.kt:27-34` | Nav argument is a `SectionConfig`; key `ShowAllScreen_${config.id}` |
| `app/src/main/java/com/kino/puber/ui/feature/showall/model/ShowAllViewState.kt` | `Content(items, isLoadingMore)`: no title |
| `app/src/main/java/com/kino/puber/ui/feature/showall/content/ShowAllScreenContent.kt:47-146` | Grid only, no header; `mainContentFocus` requested on launch, focus memory per card |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt:150-153` | `itemsFillViewport = true`: one section per page, «Все» is the 5th page on Movies |
| `ContentListScreenContent.kt:196-224` | `LazyColumn` with `focusRestorer()` + `focusGroup()`: hero item, then section items |
| `ContentListScreenContent.kt:400-415` | Every section gets Show all; «Все» does not page in the row (`loadsMore = !isLastSection`) |
| `ContentListScreenContent.kt:434-435` | `sectionListItemIndex` offsets sections by the hero; shared with `SectionRowFocusTraversalTest` |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:33` | `config` is a private immutable `val` |
| `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt:113-123` | First-page cache key covers every `SectionConfig` field + `showAnime` + `hideWatched` |
| `app/src/main/java/com/kino/puber/data/api/models/Models.kt:26-43` | `PaginatedResponse.pagination.total_items` exists but is the server count, before client-side anime and hide-watched filtering |
| `app/src/main/java/com/kino/puber/domain/model/ContentFilter.kt:6-31` | Unused today; part 3's model. Run `dead-code-sweep` (idea) proposes removing it |
| `app/src/main/res/values/main_screen_strings.xml:6-14, 35` | Tab titles and «Все» |

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | each enabled catalogue tab (Movies, Series, Concerts, Doc. movies, Doc. series, TV shows, 4K) | the tab is opened on the TV box | a browse entry naming the type («Все фильмы», «Все сериалы», …, «Всё в 4К») sits above the first shelf (device observation; label per tab by unit test) |
| AC-2 | the tab has just opened and focus is on the first card of the first shelf (device check); then focus stays on a card of the first shelf | D-pad Up, then Down | Up lands on the browse entry; Down returns to the first shelf; Left from the entry opens the side rail as from a card (device) |
| AC-3 | the browse entry focused | OK pressed | `ShowAllScreen` opens with the tab's «Все» config: same `type` / `quality`, `sort = "updated-"`, same `animeFilterMode` (unit test in `ContentListVMTest`) |
| AC-4 | the «Все» shelf's Show all tile | pressed | the same screen as AC-3 opens: same screen key, same header (unit test) |
| AC-5 | the browse grid open | scrolled down past the first page | further pages load and append (device; covered by `ShowAllVMTest` paging) |
| AC-6 | any Show all grid | opened | a one-line header names what it shows: browse grid «Все фильмы» etc., shelf grid «Фильмы · Новинки»; first focus still lands on the first card, not on the header (device; title mapping by unit test) |
| AC-7 | browse grid opened from the entry | Back | the tab is shown with focus on the browse entry (device) |
| AC-8 | focus on a grid card in row 2+, column > 1; the side rail opened from the grid | open details then Back; close the rail | focus returns to the same card (`ShowAllScreenContentFocusTest`, `ShowAllRailCloseFocusTest` via `make DEVICE=<serial> itest`) |
| AC-9 | Movies tab with «show anime» off | browse grid opened | no anime items appear (unit test on the config's `AnimeFilterMode.FollowPreference` reaching the interactor) |
| AC-10 | the tab layout with the entry added | `SectionRowFocusTraversalTest` and `TabTypeConfigTest` run | both pass; `sectionListItemIndex` accounts for the entry if it is a list item |
| AC-11 | the entry and header | seen on the TV box | look matches the ux-designer delta for `docs/design/catalogue/` and `docs/design/showall/` (owner judges) |

## Out of scope

Filters and sort controls (catalogue-filters); a changeable query (catalogue-filters); CC / AC3 badges (catalogue-card-audio-subtitle-badges).

## Design

### Architect


Base e4d75215. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.
Sizes, colours and the entry's exact place in its band are the ux-designer's delta against
`docs/design/catalogue/` and `docs/design/showall/`.

#### Decisions

| Question | Decision | Why |
|---|---|---|
| Which config the entry opens | The tab's existing «Все» section, unchanged: `TabTypeConfig.browseEntryFor(tab)` returns it with the entry label; `null` for every tab without an entry | Same `id`, so the same key `ShowAllScreen_all_<type>` (`ui/feature/showall/ShowAllScreen.kt:33`) and the same first-page cache entry; AC-3, AC-4, AC-9 hold by construction |
| How «Все» is found | Last element of `sectionsFor(tab)` whose `id` starts with `all_` | «Все» being last is already relied on by `loadsMore = !isLastSection` (`ui/feature/contentlist/content/ContentListScreenContent.kt:414`); the prefix check makes a reorder fail the unit test instead of opening the wrong grid |
| Which tabs get an entry | Movies, Series, Concerts, DocMovies, DocSeries, TvShows, For4k (`domain/model/TabType.kt:22-23`, `:26-30`); Cartoons and Anime return `null` | Brief AC-1 scope; keeps the entry out of the hero tabs (`TabTypeConfig.kt:11-23`) |
| Where the entry lives in the list | Inside the first section's list item, as a header slot of `SectionListItem` (`ContentListScreenContent.kt:455-482`), drawn above the section title; also drawn when that section is `Empty` | No new list item: `sectionListItemIndex` (`:437-438`), the one-page-per-section rule (`:149-152`) and `SectionRowFocusTraversalTest` stay as they are (AC-10); visible on tab open; Up/Down stay inside one list item |
| Page height | The entry must fit inside page 0 without growing it: it takes the band of `SectionTitleTopPadding` (`ContentListScreenContent.kt:491`) and the title line | The list viewport is 38 % of the screen (`core/ui/uikit/theme/PuberTheme.kt:33-34`), about one heading plus one 150 dp row; a taller page 0 clips the row |
| Horizontal place | The entry is the start-most focusable on its line, left-aligned with the cards | Left from it then reaches the rail by the same 2D search as the first card (AC-2) |
| Who owns focus after Back | New `rememberSaveable` `browseEntryFocused` beside `focusedSectionIndex` (`ContentListScreenContent.kt:71`): set on entry focus, cleared in the `onSectionFocused` lambda (`:103`); rows get `isTargetRow = !browseEntryFocused && index == focusedSectionIndex` (`:338`) | A target row requests its remembered card on return (`core/ui/uikit/component/moviesList/FocusTargetState.kt:298-317`); without an owner flag it would win over the entry (AC-7) |
| How the entry takes focus back | `BrowseAllEntry` has its own `FocusRequester` and requests itself while it is the owner and `LocalContentFocusActive` is true, on return and on a rail handoff (`LocalContentFocusHandoff`), then calls `LocalRootAnchorFocusRestored` (`core/ui/navigation/component/FlowComponent.kt:356`) | Same contract as `RestoreItemFocusOnTabReentryEffect` (`FocusTargetState.kt:298-317`), so the anchor restore in `PreserveLazyListAnchorOnRootReturn` (`FlowComponent.kt:447`) completes |
| Tab-open focus | Unchanged: the first card. The entry is `focusProperties { canFocus = false }` while section 0 is `SectionState.Loading` | The launch request (`ContentListScreenContent.kt:65`) enters the `LazyColumn` `focusRestorer()` (`:199`); on a cold tab the entry would be the only focusable and would take ownership |
| Trailer on entry focus | The entry dispatches `ContentListAction.TrailerPreviewStopped` on focus | Focus stays inside the `LazyColumn`, so the leave-the-rows stop (`:193-198`) never fires; same reason as the hero (`:308-310`) |
| Scroll on entry focus | Entry focus calls `onListItemFocused(sectionListItemIndex(0, isHeroItemPresent))` | Keeps page 0 flush with the viewport top like a card focus (`:341`) |
| Action | New `ContentListAction.BrowseAll` (`data object`); `ContentListVM` resolves `browseEntryFor(tabType)` and calls `openShowAll` (`ui/feature/contentlist/vm/ContentListVM.kt:138-145`) | The VM, not the composable, picks the config, so AC-3 is a VM unit test |
| VM knows the tab | `ContentListVM` gains `tabType: TabType`, passed in `ContentListScreen.buildModule` (`ui/feature/contentlist/ContentListScreen.kt:48-57`) | Needed for the browse config and the grid title; no new Koin definition |
| Grid title model | New `@Parcelize data class ShowAllTitle(@StringRes titleRes: Int?, title: String = "", @StringRes prefixRes: Int? = null)` in `ui/feature/showall/model/`; `TabTypeConfig.showAllTitle(tab, config)`: browse config → `ShowAllTitle(browse label)`, any other → `ShowAllTitle(config.titleRes, config.title, prefixRes = tab.title)` | One function for entry and «Все» tile, so AC-4's "same header" holds; resource ids make AC-6's mapping a JVM test |
| How the title reaches the grid | `ShowAllScreen(config, title: ShowAllTitle)`, both `internal val`; key stays `ShowAllScreen_${config.id}`; `Content()` resolves the title to a `String` and passes it to `ShowAllScreenContent(title, state, onAction)` | Static per screen, so not VM state; `internal` lets `ContentListVMTest` read config and title off the navigated screen |
| Header placement | `ShowAllHeader(title)` is a fixed, non-focusable `Text` in a `Column` above the `Box` holding the state switch (`ui/feature/showall/content/ShowAllScreenContent.kt:60-80`); shown in every state | First focus stays on the grid (`:88`, `:130`) for AC-6; outside the grid, so `GridFocusMemory` indices (`:226-229`, `:276-280`) do not shift |
| Seam for catalogue-filters | Header is its own composable, the place part 3 adds controls; `ContentListPagingVM.config` stays `private val` (`ui/feature/contentlist/vm/ContentListPagingVM.kt:33`) | A mutable query has no caller in this run; see Open questions 2 |

#### Changes by layer

**API / repository.** N/A: the «Все» query already pages a whole type (`TabTypeConfig.kt:77-83`, `:120-125`, `:185-190`).

**Model and ViewModel state / actions.**

| File | Change |
|---|---|
| `ui/feature/contentlist/model/TabTypeConfig.kt:9` | Add `browseEntryFor(tabType): BrowseEntry?` and `showAllTitle(tabType, config): ShowAllTitle` |
| `ui/feature/contentlist/model/BrowseEntry.kt` (new) | `data class BrowseEntry(val config: SectionConfig, @StringRes val labelRes: Int)` |
| `ui/feature/showall/model/ShowAllTitle.kt` (new) | `ShowAllTitle` as in Decisions |
| `ui/feature/contentlist/model/ContentListAction.kt:5-16` | Add `data object BrowseAll` |
| `ui/feature/contentlist/vm/ContentListVM.kt:26-34` | Constructor `tabType: TabType`; `handleAction` (`:48-59`) routes `BrowseAll`; `openShowAll` (`:138-145`) builds `ShowAllScreen(config, TabTypeConfig.showAllTitle(tabType, config))` |

**Screen and DI module.**

| File | Change |
|---|---|
| `ui/feature/contentlist/ContentListScreen.kt:48-57` | Pass `tabType` to `ContentListVM`; `Content()` (`:86-95`) passes `browseEntry = TabTypeConfig.browseEntryFor(tabType)` |
| `ui/feature/showall/ShowAllScreen.kt:28-34` | `internal val config`, new `internal val title: ShowAllTitle`; key unchanged; `Content()` (`:55-63`) resolves and passes the title |

**Compose content.**

| File | Change |
|---|---|
| `ui/feature/contentlist/content/ContentListScreenContent.kt:60-64` | Param `browseEntry: BrowseEntry?`; `browseEntryFocused` saveable; threaded through `ContentListLayout` (`:130-140`) and `sectionItems` (`:318-330`) to section 0 only |
| `ContentListScreenContent.kt:455-482` | `SectionListItem` gains `header: (@Composable () -> Unit)? = null`, drawn first in both branches; default keeps `SectionRowFocusTraversalTest` (`app/src/androidTest/kotlin/com/kino/puber/ui/feature/contentlist/content/SectionRowFocusTraversalTest.kt:460`) compiling |
| `ui/feature/contentlist/content/BrowseAllEntry.kt` (new) | TV Material3 `Button` with the label; focus effects, `canFocus` gate, trailer stop and scroll call from Decisions |
| `ui/feature/showall/content/ShowAllScreenContent.kt:56-81` | `title: String` first param; `Column { ShowAllHeader(title); Box(weight 1) { when(state) … } }` |
| `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt` (`:42`, `:68`, `:104`, `:132`, `:157`, `:183`), `ShowAllRailCloseFocusTest.kt:286` | Pass a title; required for AC-8 to compile |

**Resources.** `app/src/main/res/values/main_screen_strings.xml` after `content_section_all` (`:35`): seven entry labels («Все фильмы», «Все сериалы», «Все концерты», «Все док. фильмы», «Все док. сериалы», «Все ТВ-шоу», «Всё в 4К»; final wording from the ux-designer) and `show_all_title_shelf` = `%1$s · %2$s`.

#### Alternatives rejected

- Entry as its own `LazyColumn` item: scrolled off when the first card takes focus, shifts `sectionListItemIndex`, breaks one page per section.
- Entry outside the `LazyColumn`, under the panel: costs height on every page; the row no longer fits the 38 % viewport.
- A distinct `browse_<type>` config: a second key and focus memory for the same grid, against AC-4.
- A new `BrowseScreen` (research Option C): duplicates the grid and its focus fixes.
- Header as a full-span grid item: shifts `GridFocusMemory` indices and puts it on the first-focus path.
- Title inside `ShowAllViewState`: every sealed variant would carry it; it never changes.
- Mutable query in `ContentListPagingVM` now: no caller until catalogue-filters.

#### Risks

| Area | Risk | Lives in |
|---|---|---|
| Focus | Ownership flag and row target diverge: Back from the grid lands on the row's card, or a card focus fails to clear the flag and a later return jumps to the entry. Guarded by AC-2, AC-7 on device | `ContentListScreenContent.kt:71`, `:103`, `:338`; `FocusTargetState.kt:298-317` |
| Focus | Tab-open focus lands on the entry when the launch request beats the row's request after section 0 leaves `Loading`; the panel then shows nothing until Down | `ContentListScreenContent.kt:65`, `:199`; `BrowseAllEntry.kt` gate |
| Focus | Rail handoff with the entry as owner is answered by nobody but the blind fallback | `core/ui/uikit/component/drawer/ContentFocusHandoff.kt:93-130`; `BrowseAllEntry.kt` |
| Layout | Entry taller than its band grows page 0 and clips row 0 under the panel | `ContentListScreenContent.kt:466-470`, `:491`; `PuberTheme.kt:33-34` |
| Focus | Grid header pushes the grid down; first-focus and rail-close paths re-measured by AC-8 | `ShowAllScreenContent.kt:88`, `:125-135` |
| Recomposition | `browseEntryFocused` is read at the top of `ContentListScreenContent`, so a toggle recomposes the layout as `focusedSectionIndex` does today; entry lambdas must be `remember`ed | `ContentListScreenContent.kt:71`, `:381-395` |
| Data shape | `ShowAllScreen` parcel gains `title`; a back stack saved by the previous build does not restore across an update, so no migration | `ShowAllScreen.kt:27-30` |
| Baseline profile | `BrowseAllEntry` and `ShowAllHeader` are not in the generated profile until the next regeneration | `app/src/main/generated/baselineProfiles/baseline-prof.txt` |

#### Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | Pin tab-open focus on the first card with an AC (today implicit; the entry can steal it) | Yes: add to AC-2's Given "tab opened: focus on the first card of the first shelf", device | no |
| 2 | How catalogue-filters changes the query | Part 3 turns `ContentListPagingVM.config` (`ContentListPagingVM.kt:33`) into a `var` set only through a ShowAllVM method that calls `resetPaging` (`:111-114`); the screen key stays the base config's id | no |


### UX


#### Governing stands

| Element | Stand | Covers it? |
|---|---|---|
| Browse entry above the first shelf (brief.md#AC-1, AC-2) | `docs/design/catalogue/` | No. The stand has no tab-level control. Stand update needed |
| One-line title over every Show all grid (brief.md#AC-6) | `docs/design/showall/` | No. The stand starts the grid at `padTop` 16 and has no header. Stand update needed |

Both screens have a stand, so `designing-a-screen` does not have to run before planning. The run must update both stands: README rows and an element in `index.src.html` with sliders for the numbers below, then `python3 docs/design/build.py catalogue showall`. The owner dials them at AC-11. Until then the numbers below are starting values, not agreed ones.

**The catalogue stand is stale.** Its "Agreed sizes" table (2026-08-26) no longer matches the code. 85bfd1d2 retuned the rows, and neither the README nor `index.src.html` was updated:

| | stand README | code at e4d75215 |
|---|---|---|
| panel / rows split | 67 % | 62 / 38 (`PuberTheme.kt:33-34`) |
| card height | 115 dp | 150 dp (`CatalogueRowItemHeight`) |
| section heading | 11 sp | 16 / 20 sp Medium (`Type.kt:50` `SectionTitleStyle`) |
| row padding / card gap | 0 / 10 dp | 16 / 16 dp (`SectionRow.kt` `RowHorizontalPadding`, `CardSpacing`) |

The stand update has to start from the code values first. Otherwise the entry gets dialled against a layout that no longer ships.

#### Numbers that apply (dp at 1080p)

| What | Value | Source |
|---|---|---|
| Closed rail | 60 | `MainScreenComponent.kt:304`, measured on the box (showall README) |
| Catalogue rows viewport | 540 x 0.38 = 205.2 | `CatalogueContentWeight` |
| Section page | 30 air + 20 heading + 2 + 150 card = 202 | `ContentListScreenContent.kt` `SectionTitleTopPadding` / `SectionTitleToRow`, catalogue README "поле над заголовком 30", "заголовок → ряд 2" |
| Section heading side padding | 16 | `SectionTitleSidePadding` |
| Show all grid padding sides / top | 16 / 16 | showall README (agreed 2026-11-24), `ShowAllScreenContent.kt:48` |
| Grid tile height, row gap | 237, 16 | showall README "What that gives" (2 + 1 title lines reserved) |
| Tile focus scale | 1.05 (overhang 237 x 0.025 = 6 dp vertical) | showall README |
| Button label | 11 sp | content README "кегль кнопки 11" (only agreed button type on a stand) |

#### Element A: browse entry (catalogue)

**Placement: inside the first section's page, in its 30 dp air band. It is not a list item of its own.** The band above the heading becomes `[4 top][24 button][2]` = 30. The heading and the cards of section 0 therefore sit at the same y as on every other page (title 30-50, cards 52-202). The page keeps its 202 dp, the one-section-per-page rule (`ContentListScreenContent.kt:149-152`) holds, and `sectionListItemIndex` keeps its value. A separate list item would cut about 27 dp off the shelf while the entry is focused (a 30 dp entry plus the 202 dp page in a 205.2 dp viewport), which is the half-row peek the page rule exists to stop.

| Slider (starting value) | Value |
|---|---|
| entry → panel (top air) | **4** dp |
| entry height | **24** dp |
| entry → section heading | **2** dp |
| label | **11 / 16** sp, Medium (`labelLarge` resized), 1 line |
| inner side padding | **12** dp |
| left edge | **16** dp from the content edge, the heading's `SectionTitleSidePadding` (76 dp = 152 px from the screen edge) |
| width | wraps the label; not stretched |
| colours, shape, focus scale | tv-material3 `Button` defaults, the same as the row's «Показать все» (`SectionRow.kt:187-205`) |

The budget check: focus scale 1.1 on 24 dp grows 1.2 dp at each edge, which fits inside 4 dp above and 2 dp below. The `LazyColumn` clips at its top, so a button of 30 dp (the content stand's height) would leave no air and get its focused top edge cut. 30 is not a safe slider maximum without taking air from the heading. Clamp the slider at 26.

Labels (unit-tested per brief.md#AC-1): «Все фильмы», «Все сериалы», «Все концерты», «Все док. фильмы», «Все док. сериалы», «Все ТВ-шоу», «Всё в 4К».

**Focus:**

| From | Key | To |
|---|---|---|
| tab opened | (none) | first card of the first shelf, as today. The entry is not the launch focus: a focused entry leaves the panel with no film |
| any card of the first shelf | Up | entry |
| entry | Down | the first shelf's remembered card (its `focusRestorer`), not the card under the entry |
| entry | Left | side rail |
| entry | Up / Right | stays (nothing focusable above; nothing to the right) |
| Show all grid opened from the entry | Back | entry (brief.md#AC-7) |

Focus visibility: the default `Button` focused state (light container with dark label, scale 1.1). Unfocused it stays a filled pill. Check on the box that an always-visible pill above the shelf does not outweigh the cards. If it does, the owner's fallback is a quiet unfocused container (transparent with an outline), and that becomes a stand switch, not a code guess.

While the entry is focused the panel keeps the last focused card. Whether the trailer preview should stop then is for the architect (`ContentListScreenContent.kt:189-198` only stops it when focus leaves the rows).

#### Element B: Show all header

**A pinned header above the grid, not a grid item.** It stays when the grid scrolls, and catalogue-filters adds its controls to it (brief.md#Q2). It is not focusable in this run, so first focus stays on the first card (AC-6).

| Slider (starting value) | Value |
|---|---|
| header top | **12** dp |
| title style | `SectionTitleStyle` 16 / 20 sp Medium, default content colour. The same heading as the catalogue shelves |
| title left edge | **16** dp (= `GridContentPadding`, the edge of column 1) |
| lines | 1, ellipsis |
| header → grid (grid `contentPadding` top) | **8** dp. It must stay at or above the 6 dp tile focus overhang, or the grid's clip cuts the focused tile in row 1 |
| grid sides / bottom | 16 / 16, unchanged |

The budget check: tiles in row 1 start at 12 + 20 + 8 = 40 dp. Row 2 ends at 40 + 237 + 16 + 237 = 530 dp, plus the 6 dp focus overhang = 536, which is at most 540. Two full rows stay on screen at rest, as today (row 2 ends at 506 today).

Text: browse grid and «Все» shelf grid show the entry label («Все фильмы», ..., «Всё в 4К»). Other shelves show `<tab title> · <section title>` («Фильмы · Новинки», «4К · Популярные»), with the same " · " separator as the poster card's original-title line.

#### Shared constants: parameterise, do not retune

| Constant | Where | Why |
|---|---|---|
| `SectionTitleTopPadding` 30 | `ContentListScreenContent.kt:174` | Every section uses it. Section 0 replaces it with a header slot whose parts (4 + 24 + 2) sum to it. The 30 stays the reference |
| `SectionListItem` | `ContentListScreenContent.kt:138` | Shared with `SectionRowFocusTraversalTest`. Add an optional slot above the heading; do not fork it |
| `SectionTitleStyle` | `Type.kt:50` | Also used by `EpisodesPanel.kt:223`. The grid header reuses it as is |
| `GridContentPadding` 16 | `ShowAllScreenContent.kt:48` | Split into sides/bottom 16 and top 8 under the header. Do not change the single value |
| `Button` look of «Показать все» | `SectionRow.kt:194` | The entry uses the same component with its own height and padding constants. The row button keeps its 40 dp default |

#### Open

| ID | Question | For |
|---|---|---|
| D1 | Section 0 `Empty` collapses to nothing (`SectionListItem`, `ContentListScreenContent.kt:145-148`), so the entry would vanish with it. Starting answer: the band moves to the first non-empty section | architect |
| D2 | Filled or quiet unfocused entry | owner on the box (AC-11) |
| D3 | Re-base the catalogue stand on the code numbers before adding the entry | this run's stand update |


Stands updated in this run: docs/design/catalogue (resynced to the code, entry button) and docs/design/showall (grid title); starting values in each README section "catalogue-browse-all starting values (2026-11-24)".

## Test design


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

#### Risk profile

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

#### Traceability

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

#### Manual checks

Device `192.168.1.110:5555`, worktree dev APK installed with `tools/android-apk-install-preserve`, lease held. Keys: `adb -s 192.168.1.110:5555 shell input keyevent <KEYCODE>`. Screenshot each "Expected" into the evidence folder.

| ID | Steps | Expected | Must not happen |
|---|---|---|---|
| M-1 | 1. Open Movies from the rail (DPAD_RIGHT into content). 2. Repeat for Series, Concerts, Doc. movies, Doc. series, TV shows, 4K. 3. Open Cartoons and Anime | 1-2: label per AC-1 above the first shelf heading; focus on the first card of the first shelf, panel shows that film. 3: no entry | Focus on the entry at tab open; first shelf cards clipped at the bottom; entry on Cartoons/Anime |
| M-2 | 1. Movies, focus on card 2 of the first shelf (DPAD_RIGHT). 2. DPAD_UP. 3. DPAD_DOWN. 4. DPAD_UP, wait 3 s with a trailer-capable film in the panel. 5. DPAD_LEFT. 6. DPAD_RIGHT | 2: entry focused. 3: card 2 focused. 4: no trailer starts or one playing stops. 5: rail opens. 6: entry focused again | Up skipping to nothing; Down landing on card 1; focus lost after the rail closes |
| M-3 | 1. Focus the entry, DPAD_CENTER. 2. BACK. 3. Repeat 1-2. 4. DPAD_DOWN, DPAD_CENTER on a card (details), BACK. 5. DPAD_UP, DPAD_CENTER, BACK | 2, 3, 5: tab shown, entry focused. 4: the card focused | Focus on a shelf card after Back from the grid; focus lost; crash |
| M-4 | 1. Movies entry, DPAD_CENTER. 2. Read the header. 3. DPAD_DOWN until past 3 grid pages | 2: «Все фильмы», first card focused, header not focusable. 3: new cards append, no duplicate, focus keeps moving | Header focused; pages stop after page 1; anime items with «show anime» off |
| M-5 | 1. Movies, «Новинки» shelf, Show all tile. 2. BACK. 3. «Все» shelf Show all tile. 4. On 4K, its «Все» tile | 1: header «Фильмы · Новинки». 3: same grid and header as M-4 («Все фильмы»), focus on the card last focused there. 4: «Всё в 4К» | Different header for the «Все» tile and the entry |
| M-6 | Screenshots of M-1 (Movies, unfocused and focused entry) and M-4 step 2 with the updated `docs/design/catalogue/` and `docs/design/showall/` stands | Owner accepts sizes and the filled or quiet entry (ux D2) | Focused entry cut at its top edge; header pushing row 2 off screen |

#### Perf track

N/A: track off.

#### UNTESTABLE

AC-11: owner's visual judgement (M-6 collects the evidence).


## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Where the browse entry sits | One focusable button above the first shelf of each enabled catalogue tab, 4K included; the «Все» shelf stays and opens the same grid (research Option A, Q2, Q3) | yes |
| Q2 | Grid header content | Title only on every Show all grid (AC-6); no item count, because `total_items` ignores client-side anime and hide-watched filtering; part 3 adds filter/sort controls to this header | yes |
| Q3 | `dead-code-sweep` vs `ContentFilter` | Keep `ContentFilter`; the conductor drops it from `dead-code-sweep`'s scope while `catalogue-filters` is open | no |

Assumed from `research.md: Open questions` Q4: default order `updated-`; sort choice is part 3.

Architect Q1 (AC-2 Given: the tab opens on the first card of the first shelf, device check) and Q2 (catalogue-filters makes ContentListPagingVM.config changeable through a ShowAllVM method that calls resetPaging; screen key stays the base config id): answered on recommendation.

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-24 | Brief Q1-Q3 and architect Q1-Q2 on recommendation; ContentFilter kept out of dead-code-sweep | owner (standing instruction: recommended) |
| 2026-11-24 | Stand starting values used as the port values; the owner judges the look on the box (AC-11) | owner (standing instruction: recommended) |
| 2026-11-24 | Gate 1: approved | owner (standing instruction: recommended) |
