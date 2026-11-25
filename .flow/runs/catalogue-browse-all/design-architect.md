# Design (architect): catalogue-browse-all

Base e4d75215. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`.
Sizes, colours and the entry's exact place in its band are the ux-designer's delta against
`docs/design/catalogue/` and `docs/design/showall/`.

## Decisions

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

## Changes by layer

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

## Alternatives rejected

- Entry as its own `LazyColumn` item: scrolled off when the first card takes focus, shifts `sectionListItemIndex`, breaks one page per section.
- Entry outside the `LazyColumn`, under the panel: costs height on every page; the row no longer fits the 38 % viewport.
- A distinct `browse_<type>` config: a second key and focus memory for the same grid, against AC-4.
- A new `BrowseScreen` (research Option C): duplicates the grid and its focus fixes.
- Header as a full-span grid item: shifts `GridFocusMemory` indices and puts it on the first-focus path.
- Title inside `ShowAllViewState`: every sealed variant would carry it; it never changes.
- Mutable query in `ContentListPagingVM` now: no caller until catalogue-filters.

## Risks

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

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | Pin tab-open focus on the first card with an AC (today implicit; the entry can steal it) | Yes: add to AC-2's Given "tab opened: focus on the first card of the first shelf", device | no |
| 2 | How catalogue-filters changes the query | Part 3 turns `ContentListPagingVM.config` (`ContentListPagingVM.kt:33`) into a `var` set only through a ShowAllVM method that calls `resetPaging` (`:111-114`); the screen key stays the base config's id | no |
