# Browse a whole content type as a titled poster grid - plan

**Spec:** `docs/superpowers/specs/2026-11-24-catalogue-browse-all.md`
**Goal:** A «Все …» entry above the first shelf of each catalogue tab opens the tab's «Все» Show all grid, and every Show all grid carries a one-line title.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `cd /home/dev/orca/workspaces/Puber/catalogue-browse-all && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `cd /home/dev/orca/workspaces/Puber/catalogue-browse-all && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Never `./gradlew` in the worktree.
- Touched unit tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'`. A task that touches `app/src/androidTest` also runs `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugAndroidTestKotlin`.
- Instrumented tests run only as `make DEVICE=192.168.1.110:5555 GRADLE=./tools/agentw itest TESTS=<fqcn>` (`Makefile:23` `GRADLE ?= ./gradlew`), by qa-device with the device lease; the implementer only compiles them. Manual checks M-1 to M-6 of the spec's Test design are qa-device's.
- One task = green fast gate + green tests of the touched code + one commit, message one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- P0/P1/P2 tests marked `red` in the spec's Traceability are written first and seen failing (a compile failure on a missing symbol counts); record it under Red run.
- Visual numbers are the starting values in `docs/design/catalogue/README.md:200-222` and `docs/design/showall/README.md:96-118`; the stands are already updated (98ef4665), do not edit them.
- Out of scope: filter or sort controls, a changeable query (`ContentListPagingVM.config` stays `private val`), CC/AC3 badges, `ContentFilter.kt`, baseline profile regeneration.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.

Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. Test short names (TTC, CLVM, SRFT, SACF, SARC, SAVM, CLIT) are the spec's Test design table.

---

## Tasks

### Task 1: Entry labels, the browse config lookup and the grid title model

**Covers:** AC-1 (label), AC-3 (config), AC-6 (title mapping), AC-9, AC-10 (TTC)
**Files:**
- Modify: `app/src/main/res/values/main_screen_strings.xml` - after `content_section_all` (`:35`) add the strings below
- Modify: `app/src/main/res/values-en/strings.xml` - after `content_section_all` (`:180`) add the English twins (the locale carries every string; lint `MissingTranslation` otherwise)
- Create: `ui/feature/contentlist/model/BrowseEntry.kt`
- Create: `ui/feature/showall/model/ShowAllTitle.kt`
- Modify: `ui/feature/contentlist/model/TabTypeConfig.kt` - two functions in `object TabTypeConfig` (`:9`); relies on `sectionsFor` (`:25-42`), the `all_$type` / `all_4k` sections (`:77-83`, `:120-125`, `:185-190`), `TabType.title` (`domain/model/TabType.kt:16`)
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/model/TabTypeConfigTest.kt`

| Name | ru (`values`) | en (`values-en`) |
|---|---|---|
| `browse_all_movies` | Все фильмы | All movies |
| `browse_all_series` | Все сериалы | All series |
| `browse_all_concerts` | Все концерты | All concerts |
| `browse_all_docmovies` | Все док. фильмы | All documentary movies |
| `browse_all_docseries` | Все док. сериалы | All documentary series |
| `browse_all_tvshows` | Все ТВ-шоу | All TV shows |
| `browse_all_4k` | Всё в 4К | All in 4K |
| `show_all_title_shelf` | `%1$s · %2$s` | `%1$s · %2$s` |

**Consumes:** `SectionConfig` (`ui/feature/contentlist/model/SectionConfig.kt:9-21`, `@param:StringRes` style at `:12`).
**Produces:**
```kotlin
@Immutable internal data class BrowseEntry(val config: SectionConfig, @param:StringRes val labelRes: Int)

@Parcelize internal data class ShowAllTitle(
    @param:StringRes val titleRes: Int?,
    val title: String = "",
    @param:StringRes val prefixRes: Int? = null,
) : Parcelable

// in TabTypeConfig
fun browseEntryFor(tabType: TabType): BrowseEntry?          // label by tab; config = sectionsFor(tabType).lastOrNull { it.id.startsWith("all_") }
fun showAllTitle(tabType: TabType, config: SectionConfig): ShowAllTitle
    // config.id == browseEntryFor(tabType)?.config?.id -> ShowAllTitle(titleRes = entry.labelRes)
    // otherwise -> ShowAllTitle(config.titleRes, config.title, prefixRes = tabType.title)
```
Labels: Movies, Series, Concerts, DocMovies, DocSeries, TvShows, For4k map to the table rows in order; every other `TabType` (Cartoons and Anime included) returns `null`.

- [x] Write the failing TTC tests: `browseEntryFor_labelsEachCatalogueTabWithItsOwnString`, `browseEntryFor_isNullForTabsWithoutAnEntry`, `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab` (also asserts `FollowPreference` for Movies and Series: AC-9), `showAllTitle_namesTheAllGridWithTheEntryLabel`, `showAllTitle_prefixesAShelfTitleWithTheTabTitle`; assertions as in the spec's Traceability rows
- [x] Add the strings, the two model files and the two functions
- [x] Run the fast gate and `--tests 'com.kino.puber.ui.feature.contentlist.model.TabTypeConfigTest'` (whole class green: AC-10); commit

**Done when:** `TabTypeConfigTest` passes with the five new tests and the existing ones unchanged.

---

### Task 2: BrowseAll action, the tab-aware ContentListVM and the titled ShowAllScreen

**Covers:** AC-3, AC-4
**Files:**
- Modify: `ui/feature/contentlist/model/ContentListAction.kt:5-16` - add `data object BrowseAll : ContentListAction`
- Modify: `ui/feature/contentlist/vm/ContentListVM.kt` - constructor (`:26-34`) gains `private val tabType: TabType` (no default); `handleAction` (`:48-59`) routes `BrowseAll` to `TabTypeConfig.browseEntryFor(tabType)?.let { openShowAll(it.config) }`; `openShowAll` (`:138-145`) builds `ShowAllScreen(config, TabTypeConfig.showAllTitle(tabType, config))`, so the shelf tile (`ShowAll`, `:53`) and the entry share one title path
- Modify: `ui/feature/contentlist/ContentListScreen.kt:48-57` - pass `tabType = tabType` (the screen's `tabType`, `:33`)
- Modify: `ui/feature/showall/ShowAllScreen.kt:28-30` - `internal val config: SectionConfig, internal val title: ShowAllTitle`; key (`:33`) unchanged; `Content()` (`:55-63`) unchanged in this task
- Test: `app/src/test/kotlin/com/kino/puber/ui/feature/contentlist/vm/ContentListVMTest.kt`

**Consumes:** Task 1 `browseEntryFor`, `showAllTitle`, `ShowAllTitle`; `router.navigateForResult` capture pattern (CLVM `:205-239`, `:241-256`); trailer pattern `openingShowAll_stopsAPublishedTrailerPreview` (`:480-492`).
**Produces:** `ContentListAction.BrowseAll`; `ShowAllScreen(config: SectionConfig, title: ShowAllTitle)` with readable `config` / `title`.

- [x] Give the three `createVM` helpers (CLVM `:584-615`) a `tabType: TabType = TabType.Movies` parameter passed to the VM
- [x] Write the failing CLVM tests: `browseAll_navigatesToShowAllWithTheTabsAllConfigAndTitle` (P0), `browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle` (P0), `browseAll_onATabWithoutAnEntry_doesNotNavigate` (P1, `TabType.Cartoons`), `browseAll_returnedChanges_invalidateFirstPageCacheAndRequestSectionRefresh` (P1), `browseAll_stopsAPublishedTrailerPreview` (P2); capture the `ShowAllScreen` from `navigateForResult` with a `slot<PuberScreen>()`
- [x] Implement the action, constructor parameter, DI argument and screen fields
- [x] Run the fast gate and `--tests 'com.kino.puber.ui.feature.contentlist.vm.ContentListVMTest'`; commit

**Done when:** `ContentListVMTest` passes; the captured screens of `BrowseAll` and `ShowAll(sectionsFor(Movies).last())` have key `ShowAllScreen_all_movie` and equal `config` and `title`.

---

### Task 3: Show all header above the grid

**Covers:** AC-6, AC-8
**Files:**
- Create: `ui/feature/showall/content/ShowAllHeader.kt` - `@Composable internal fun ShowAllHeader(title: String, modifier: Modifier = Modifier)`: one `androidx.tv.material3.Text`, `SectionTitleStyle` (`core/ui/uikit/theme/Type.kt:50`), `maxLines = 1`, `TextOverflow.Ellipsis`, padding top 12 dp, start/end 16 dp, not focusable; and `@Composable internal fun ShowAllTitle.resolve(): String` = base (`titleRes` string or `title`), wrapped in `stringResource(R.string.show_all_title_shelf, stringResource(prefixRes), base)` when `prefixRes != null`
- Modify: `ui/feature/showall/content/ShowAllScreenContent.kt` - `ShowAllScreenContent` (`:55-81`) takes `title: String` as first parameter and becomes `Column(fillMaxSize) { ShowAllHeader(title); Box(Modifier.fillMaxWidth().weight(1f)) { when (state) … } }`; the grid's `contentPadding` (`:132`) becomes `PaddingValues(start = 16, top = 8, end = 16, bottom = 16)` through a new `GridTopPadding = 8.dp` beside `GridContentPadding` (`:48`, value unchanged). Nothing else in `ShowAllContentBody` / `GridFocusMemory` (`:83-273`) changes
- Modify: `ui/feature/showall/ShowAllScreen.kt:55-63` - `ShowAllScreenContent(title = title.resolve(), state = state, onAction = onAction)`
- Modify: SACF call sites `:42`, `:68`, `:104`, `:132`, `:157`, `:183` and SARC `:286` - pass `title = "Фильмы · Новинки"`
- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/showall/content/ShowAllScreenContentFocusTest.kt`

**Consumes:** Task 1 `ShowAllTitle`, `show_all_title_shelf`; Task 2 `ShowAllScreen.title`.
**Produces:** `ShowAllScreenContent(title: String, state: ShowAllViewState, onAction: (UIAction) -> Unit)`; `ShowAllHeader` as the place catalogue-filters adds controls.

- [x] Write the failing SACF tests: `showAllHeaderShowsTheTitleAndFirstFocusStaysOnTheFirstCard` (P0: title text displayed, its node not focused after `awaitAnyFocus()`, `Card 1` focused) and `showAllHeaderIsShownInLoadingAndErrorStates` (P2)
- [x] Implement the header, the layout change and the title resolution; update the seven call sites
- [x] Run the fast gate and `:app:compileDevDebugAndroidTestKotlin`; commit

**Done when:** both compiles are green; qa-device's `itest` of `com.kino.puber.ui.feature.showall.content.ShowAllScreenContentFocusTest` and `com.kino.puber.ui.feature.showall.content.ShowAllRailCloseFocusTest` passes (AC-8 guards and the two new tests).

---

### Task 4: Browse entry in the first section's band

**Covers:** AC-1, AC-2, AC-7, AC-10
**Files:**
- Create: `ui/feature/contentlist/content/BrowseAllEntry.kt` - signature below. A tv-material3 `Button` (same component as the row's «Показать все», `ui/feature/contentlist/content/SectionRow.kt:195-203`) with `Modifier.padding(top = 4.dp).height(24.dp)`, `contentPadding = PaddingValues(horizontal = 12.dp)`, label `MaterialTheme.typography.labelLarge.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)`, one line; width wraps the label. Focus: `focusProperties { this.canFocus = canFocus }`; `onFocusChanged` keeps a `hasFocus` ref and calls `onFocused()` on gaining focus; own `FocusRequester`; it latches `LocalContentFocusHandoff.current?.pendingRequestId` as `ShowAllScreenContent.kt:193-199` does, and a `LaunchedEffect(isOwner, LocalContentFocusActive.current, latchedHandoffId)` requests itself, when `isOwner && contentFocusActive && canFocus && !hasFocus`, over up to 3 frames (`withFrameNanos` then `requestFocus()`, as `FocusTargetState.kt:319-324`), then calls `LocalRootAnchorFocusRestored.current()` (`core/ui/navigation/component/FlowComponent.kt:356`) - the contract of `RestoreItemFocusOnTabReentryEffect` (`FocusTargetState.kt:298-317`)
- Modify: `ui/feature/contentlist/content/ContentListScreenContent.kt`:
  - `SectionListItem` (`:454-482`) gains `header: (@Composable () -> Unit)? = null` before `row`. With a header, both branches draw `Box(Modifier.fillMaxWidth().height(SectionTitleTopPadding).padding(start = SectionTitleSidePadding)) { header() }` first and the heading drops its own top padding; the page height stays 30 + 20 + 2 + 150. Without one, unchanged (keeps SRFT `:460` compiling)
  - `ContentListScreenContent` (`:60-64`) gains `browseEntry: BrowseEntry?`; `var browseEntryFocused by rememberSaveable { mutableStateOf(false) }` beside `focusedSectionIndex` (`:71`); `onSectionFocused` (`:103`) also sets `browseEntryFocused = false`; a remembered `onBrowseEntryFocused` sets it `true` and dispatches `ContentListAction.TrailerPreviewStopped`
  - `ContentListLayout` (`:130-140`) and `sectionItems` (`:318-330`) thread `browseEntry`, `browseEntryFocused`, `onBrowseEntryFocused`; the entry's `onFocused` also calls `onListItemFocused(sectionListItemIndex(0, isHeroItemPresent))` (`:145`, `:437-438`); `isTargetRow = !browseEntryFocused && index == focusedSectionIndex` (`:338`)
  - `sectionItem` (`:365-379`) gains `header` and passes it to `SectionListItem` (`:419-423`); only index 0 gets `BrowseAllEntry(label = stringResource(browseEntry.labelRes), isOwner = browseEntryFocused, canFocus = sectionStates[0] !is SectionState.Loading, onFocused = …, onClick = { onAction(ContentListAction.BrowseAll) })`, lambdas `remember`ed; no header when `browseEntry == null`
- Modify: `ui/feature/contentlist/ContentListScreen.kt:86-95` - pass `browseEntry = TabTypeConfig.browseEntryFor(tabType)`
- Test: `app/src/androidTest/kotlin/com/kino/puber/ui/feature/contentlist/content/SectionRowFocusTraversalTest.kt`

**Consumes:** Task 1 `BrowseEntry`, `browseEntryFor`; Task 2 `ContentListAction.BrowseAll`; `LocalContentFocusActive` (`core/ui/uikit/component/modifier/FocusOnLaunchRequester.kt:32`), `LocalContentFocusHandoff` (`core/ui/uikit/component/drawer/ContentFocusHandoff.kt:14`).
**Produces:**
```kotlin
@Composable
internal fun BrowseAllEntry(
    label: String,
    isOwner: Boolean,      // browseEntryFocused: takes focus back on return and on a rail handoff
    canFocus: Boolean,     // false while section 0 is Loading, so a cold tab opens on the first card
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
)
```

- [x] Extend `SettleScene` (SRFT `:424-485`) with `entryLabel: String? = null`: when set, section 0's `SectionListItem` gets `header = { BrowseAllEntry(entryLabel, isOwner = false, canFocus = rows[0].state !is SectionState.Loading, onFocused = { onListItemFocused(rowIndex) }, onClick = {}, Modifier.testTag(SETTLE_ENTRY_TAG)) }`
- [x] Write the failing SRFT tests: `browseEntryUpFromFirstShelfCardThenDownReturnsToThatCard` (P1, `row-0-item-1` via `itemTitle` `:670`), `browseEntryIsNotFocusableWhileItsSectionIsLoading` (P1: `RequestFocus` on the entry tag, then `assertIsNotFocused`), `browseEntryHeaderKeepsSectionZeroFlushWithTheViewportTop` (P1, `isHeroItemPresent = false`, `assertSettleFlush` `:496` for sections 0 and 1, card bottom inside the 205 dp viewport), `browseEntryStaysWhenSectionZeroIsEmpty` (P2: displayed and takes focus)
- [x] Implement `BrowseAllEntry`, the header slot and the wiring
- [x] Run the fast gate and `:app:compileDevDebugAndroidTestKotlin`; commit

**Done when:** both compiles are green; qa-device's `itest` of `com.kino.puber.ui.feature.contentlist.content.SectionRowFocusTraversalTest` passes (existing tests unchanged: AC-10); M-1, M-2 and M-3 on the TV box match the spec's Manual checks (AC-1, AC-2, AC-7).

---

### Task 5: Full gate

**Covers:** AC-5, AC-9 (guards), AC-10, AC-11 (evidence handed to qa-device)
**Files:** none planned; fixes from detekt or lint stay in the files above.

**Consumes:** Tasks 1-4.
**Produces:** a green full gate.

- [x] Run the full gate; SAVM `aWalkThatSpentItsBudgetResumesWhereItStopped` (`ShowAllVMTest.kt:234`), `firstPage_publishesInteractorItemsWithoutAdditionalFiltering` (`:177`) and CLIT `followPreference_whenAnimeIsHidden_excludesAnimeAndRetainsMissingGenres` (`ContentListInteractorTest.kt:262`) pass unchanged
- [x] Commit fixes only if any were needed

**Done when:** the full gate exits 0; qa-device then runs M-4 to M-6 (AC-5, AC-6, AC-11).

---

## Implementation record

<!-- owner: implementer -->

**Commits:** see the git log of the branch (one per task, then a detekt fix of `BrowseAllEntry` found by the full gate, then this record).

**Red run** (P0 `red` tests on the unchanged code; symbols stubbed with default results):

- red: TTC browseEntryFor_labelsEachCatalogueTabWithItsOwnString - AssertionFailedError, stub returned null (TabTypeConfigTest.kt:244)
- red: TTC browseEntryFor_returnsTheAllSectionOfEachCatalogueTab - AssertionFailedError, stub returned null (:263)
- red: TTC showAllTitle_namesTheAllGridWithTheEntryLabel - NullPointerException, no entry from the stub (:279)
- red: TTC showAllTitle_prefixesAShelfTitleWithTheTabTitle - AssertionFailedError, stub title had no `titleRes` (:294)
- green on stub: TTC browseEntryFor_isNullForTabsWithoutAnEntry - the stub already returns null; the symbol did not exist on base (a compile failure there), so it guards the leak, not the lookup
- red: CLVM browseAll_navigatesToShowAllWithTheTabsAllConfigAndTitle - no navigateForResult call without the BrowseAll route (AssertionError, :593)
- red: CLVM browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle - AssertionError, one navigation instead of two (:612)
- red: CLVM browseAll_returnedChanges_invalidateFirstPageCacheAndRequestSectionRefresh (P1) - no listener captured (:638)
- red: CLVM browseAll_stopsAPublishedTrailerPreview (P2) - preview URL still published (:661)
- green on stub: CLVM browseAll_onATabWithoutAnEntry_doesNotNavigate (P1) - no handler yet means no navigation; it guards the null fallback
- red: SACF showAllHeaderShowsTheTitleAndFirstFocusStaysOnTheFirstCard - on the TV box: "component with text 'Фильмы · Новинки' is not displayed" (title parameter ignored)
- red: SACF showAllHeaderIsShownInLoadingAndErrorStates (P2) - same failure line, on the box
- red: SRFT browseEntryUpFromFirstShelfCardThenDownReturnsToThatCard (P1) - on the box with an empty `BrowseAllEntry` stub: no node with tag settle_entry
- red: SRFT browseEntryIsNotFocusableWhileItsSectionIsLoading (P1) - same, no node with the entry tag
- red: SRFT browseEntryStaysWhenSectionZeroIsEmpty (P2) - settle_entry is not displayed
- green on stub: SRFT browseEntryHeaderKeepsSectionZeroFlushWithTheViewportTop (P1) - an empty stub in the header band keeps the page height; it guards the layout budget against the real button (passes with it)

**Deviations from the plan:**

- Red runs for SACF and SRFT were run on the TV box (lease held, released) as the brief asked; the plan said compile only.
- The Up/Down SRFT test reaches card 1 with Right from card 0, not a direct RequestFocus: the row redirects the first focus of a composition to its fallback card, so a direct request on card 1 never holds focus. Its first red run failed on that, so it was re-run with the stub after the fix and fails on the missing entry node.
- `SACF` asserts the header is unfocused with `assert(!isFocused())`; a non-focusable node carries no Focused property, so `assertIsNotFocused` fails on it.
- Test call sites pass the title through a file-level `TestTitle` constant instead of the literal.

**Files touched outside the plan:** none.

**For the reviewers:**

- `BrowseAllEntry` focus effect (`isOwner`, handoff latch, `LocalRootAnchorFocusRestored` call) is only exercised on the device (M-2, M-3); the SRFT scene passes `isOwner = false`.
- `ContentListScreenContent`: `browseEntryFocused` is saveable, cleared by `onSectionFocused`; lambdas into the lazy item are `remember`ed.
- `check.full` green; the detekt "compiler errors" line is the existing analysis noise (87 before this run's changes).
- Instrumented classes run on the box: ShowAllScreenContentFocusTest, ShowAllRailCloseFocusTest, SectionRowFocusTraversalTest, all green. M-1 to M-6 are qa-device's.

**Blocked:** none.

## Readiness

Verdict: **READY**

Spec `docs/superpowers/specs/2026-11-24-catalogue-browse-all.md` (Test design identical to `.flow/runs/catalogue-browse-all/test-design.md` apart from heading levels), plan `docs/superpowers/plans/2026-11-24-catalogue-browse-all.md` at 437b7b95, code at base e4d75215.

## AC coverage

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 | 1, 4 | TTC label and null rows (P0 red), M-1 |
| AC-2 | 4 | SRFT Up/Down and Loading gate (P1 red), M-1, M-2 |
| AC-3 | 1, 2 | CLVM `browseAll_navigatesToShowAllWithTheTabsAllConfigAndTitle`, TTC `browseEntryFor_returnsTheAllSectionOfEachCatalogueTab` (P0 red) |
| AC-4 | 2 | CLVM `browseAllAndAllShelfShowAll_openTheSameScreenKeyAndTitle` (P0 red) |
| AC-5 | 5 | SAVM guards (P1), M-4 |
| AC-6 | 1, 3 | TTC title rows, SACF header row (P0 red), M-4, M-5 |
| AC-7 | 4 | M-3 only (device) |
| AC-8 | 3 | SACF and SARC guards (P0 guard), qa-device `itest` |
| AC-9 | 1, 5 | TTC (P0 red), CLIT guard (P1) |
| AC-10 | 1, 4, 5 | SRFT and TTC whole class (P0 guard), SRFT flush row (P1 red) |
| AC-11 | 5 (evidence) | M-6 |

## Citation sample (all resolve at e4d75215)

| Citation | Found |
|---|---|
| `ContentListScreenContent.kt:71`, `:103`, `:338` | `focusedSectionIndex` saveable; `onSectionFocused = { focusedSectionIndex = it }`; `isTargetRow = index == focusedSectionIndex` |
| `ContentListScreenContent.kt:455`, `:437-438` | `LazyItemScope.SectionListItem(...)`; `sectionListItemIndex` |
| `ShowAllScreenContent.kt:48`, `:132`, `:193-199` | `GridContentPadding`; grid `contentPadding`; handoff latch |
| `FocusTargetState.kt:298-317`, `:319-324` | `RestoreItemFocusOnTabReentryEffect`; `requestAfterAnchorRestore` |
| `FlowComponent.kt:356`, `:447` | `LocalRootAnchorFocusRestored`; `PreserveLazyListAnchorOnRootReturn` |
| `ContentListVM.kt:138-145`, `ShowAllScreen.kt:28-33` | `openShowAll` (sole `ShowAllScreen(` caller); constructor and key |
| `TabTypeConfig.kt:77-83`, `:120-125`, `:185-190` | `all_$type` / `all_4k` sections, `sort = "updated-"` |
| `Makefile:23`, `values-en/strings.xml:180`, `main_screen_strings.xml:35` | `GRADLE ?= ./gradlew`; `content_section_all` twice |
| SRFT `:424`, `:460`, `:496`, `:670`; SAVM `:177`, `:234`; CLIT `:262`; CLVM `:481`, `:584-615` | named functions and helpers exist |

## Build and order

Tasks 1 to 5 follow their `Consumes` lines. Every signature change updates all its callers in the same task, checked by `git grep`: `ShowAllScreen(` and `ContentListVM(` in Task 2, `ShowAllScreenContent(` (seven androidTest sites) in Task 3, `ContentListScreenContent(` and `SectionListItem(` (default `header`) in Task 4.

## Scope

Plan stays inside Out of scope: `ContentListPagingVM.config` stays `private val`, `ContentFilter.kt` and baseline profile untouched. Brief Q1-Q3 and architect Q1-Q2 answered (spec Decisions).

## Notes for the gate (not blocking)

| # | Note |
|---|---|
| 1 | AC-7 (R1, score 6) has no automated test; M-3 is its only evidence. Without a device record AC-7 is `NOT VERIFIED` |
| 2 | Device rows marked P0 carry no `red`/`guard` mark; they are manual by nature and are judged on the evidence file |
| 3 | `baseline-prof.txt:33785-33786` names the old `ContentListScreenContent` and `SectionListItem` signatures; stale until regeneration (architect Risks) |

### Verify round 1 fixes

| Defect | Result |
|---|---|
| 1 AC-7, Back from the grid lands on a shelf card | Fixed, commits "Keep the browse entry focused when its section finishes loading after a return from the grid" and "Ask the browse entry for focus before the first frame so the rail cannot take it on a return". Two causes, found with temporary logging on the box. (a) A return recomposes the tab with the entry as owner while section 0 is still Loading; the focusable shimmer takes focus, so `SectionRowContent.hasFocusRef` is true when the cards arrive and the row's own initial-focus `LaunchedEffect` (`SectionRow.kt`, `ContentSectionCards`) requests card 1, whose `onSectionFocused` clears `browseEntryFocused`. `initialFocusEnabled = !browseEntryFocused` now gates it. (b) With (a) gated the entry's own request waited one frame first; frames were slow, Android gave focus to the side rail, which opened it and closed the content focus gate. The entry now asks at once and retries per frame. |
| 2 4K "Все" shelf and grid in error | Not fixed, pre-existing, outside this run. The section config is the one on base (`TabTypeConfig.for4kSections`, unchanged by the diff) and the same request shape as the working «Популярные» shelf. On the box the retry fails in 0.25 s without a request: `SQLiteBlobTooBigException: Row too big to fit into CursorWindow` from `SELECT * FROM cached_payload WHERE key IN (...)` (`data/db/CachedPayloadDao.kt:13-14`, `PersistentPayloadStore.kt:60-64`, `CachedFeed.kt:360`), a stored row over the 2 MB window, and the section goes to Error. The fix is a cache-layer decision (cap payload size on write, or read in pieces) and needs an owner call. |

**Red run (fix round):** red: SRFT browseEntryOwnerKeepsFocusWhenItsSectionFinishesLoading - on the box with the `initialFocusEnabled` parameter stubbed (accepted, ignored): "Failed to assert the following: (Focused = 'true')" on the entry. Green after the gate: SRFT 12 of 12. The slow-frame cause (b) has no itest: it needs the rail to win a focus race, which the test scene does not have; device M-3 covers it.

**Device M-3 after the fixes** (TV box, worktree APK via install-preserve, lease released): cold start, Movies, UP to the entry, CENTER, RIGHT into the grid, BACK: entry focused, rail closed. Same without RIGHT: entry focused.

### Review round 1 fixes (triage rows 2, 3, 4, 5, 7)

| Row | Fix | Commit |
|---|---|---|
| 3 | `BrowseAllEntry` calls `anchorFocusRestored()` only when the focus request landed | 04ba8f1a |
| 5 | `ShowAllTitleResolveTest` (shelf with prefix, whole type, plain title with prefix), expected strings built from resources so the device locale does not matter | a9828e0f |
| 2, 4, 7 | `BrowseEntryFocus` (owner, launch settling, `canFocus`) and `BrowseEntryHeader` in `BrowseEntryFocus.kt`, used by the screen and by both SRFT scenes; a row taking non-Loading focus clears ownership | c0664ac0, 96c7a9c3 and the detekt follow-up |

The entry becomes focusable one `FOCUS_ON_LAUNCH_DELAY_MILLIS` after the first row focus, not at it: the first version (settled at the row focus) failed the Empty launch test, because the screen's launch request then moved focus from the card to the entry (log: row focus, then entry focus 250 ms later). Owner on return from the grid stays focusable at once, so AC-7 (0a65717f, c484b503) is untouched; the OwnerEntryScene test still passes and uses `initialFocusEnabled = !focus.isOwner`. M-2 and M-3 on the real screen are qa round 2's.

**Red run (round 1, TV box, class SectionRowFocusTraversalTest with the gating reverted to the old rule):**

- red: SRFT launchFocusSkipsTheEntryWhenSectionZeroIsEmpty - Focused = 'true' not met for row-1-item-0 (first run), later "NOT Focused" on the entry
- red: SRFT launchFocusSkipsTheEntryWhenSectionZeroIsError - NOT (Focused = 'true') failed on settle_entry
- red: SRFT focusEnteringACardClearsTheEntryOwnership - "entry still owns focus"
- red: SRFT focusEnteringARetryRowClearsTheEntryOwnership - "entry still owns focus"
- green on base: SACF/SRFT existing tests and ShowAllTitleResolveTest (new coverage of existing behaviour, 3 cases, guard)
- `browseEntryStaysWhenSectionZeroIsEmpty` became `browseEntryIsReachableWithUpWhenSectionZeroIsEmpty` (focus a card of section 1, Up reaches the entry): the old body focused the entry directly, which the new rule forbids before launch settles.

**Checks:** SectionRowFocusTraversalTest 16/16, ShowAllTitleResolveTest 3/3, ShowAllScreenContentFocusTest and ShowAllRailCloseFocusTest 14/14 on the box (lease released). The last commit moved one call without a device re-run. `testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:compileDevDebugAndroidTestKotlin` BUILD SUCCESSFUL.

### Review round 2 fix (triage row 1)

`rememberBrowseEntryFocus(sectionStates)` settles the launch at once when every section is Empty (none Loading, none with a Retry button or cards), so the entry becomes focusable and launch focus may land on it. The round-1 rule is unchanged otherwise: Empty section 0 with a populated later section still sends launch focus to that section's first card (`launchFocusSkipsTheEntryWhenSectionZeroIsEmpty` and the Error variant stay green).

**Red run (TV box, the settle call stubbed off):** red: SRFT browseEntryIsFocusableWhenEverySectionIsEmpty - "Failed to assert the following: (Focused = 'true')" on settle_entry after a RequestFocus.

**Checks:** SectionRowFocusTraversalTest whole class 17/17 on the box at head, XML in `docs/superpowers/evidence/2026-11-24-catalogue-browse-all-itest-r3/SectionRowFocusTraversalTest.xml` (lease released).

### Round 3 qa defect (AC-7 second Back) and review round 3 (triage-3 rows 1, 2)

**qa round 3 defect: not fixed, pre-existing, outside this run's files.** Traced with temporary logging on the box (reverted, tree clean of it).

- Cause: the grid always opens with the side rail expanded and holding focus (`drawer state value=Open` right after the `ShowAllVM` registers). Back then goes to whichever `BackHandler` the `OnBackPressedDispatcher` ranks last among the enabled ones, and its order is registration order. After a details round trip the Main screen is composed afresh, so `RailOpenBackHandler` (`ui/feature/main/component/MainScreenComponent.kt:199`) registers after `TabBackHandler` (`core/ui/navigation/component/FlowComponent.kt:752`) and wins: the first Back only closes the rail (`RailOpenBackHandler fired value=Open`, no `AppRouter.dispatchBackPressed`), the second pops. Before a details trip the tab flow registers its handler later than the rail's, so `TabBackHandler` wins and one Back pops with the rail still open.
- Predates the entry: the shelf «Показать все» tile of Movies (title «Фильмы · Новинки») after the same details trip gave the same log (`RailOpenBackHandler fired value=Open`, one Back only closes the rail). None of `MainScreenComponent.kt`, `FlowComponent.kt`, `AppRouter.kt` and the rail-open-on-grid-open behaviour is in this run's diff (`git diff e4d75215 --stat`); `ShowAllScreenContent.kt` changed only by the header and a top padding. Not compared on a base build, by the conductor's limit; the code paths above are unchanged.
- Fix would be in the Back-handler ordering or in making the grid take focus from the rail on open: an app-wide navigation decision, left for an owner call (suggested `/task:new`).

**Triage-3 rows 1 and 2 (fixed).** `rememberBrowseEntryFocus` settles the all-Empty launch only after that state has lasted `FOCUS_ON_LAUNCH_DELAY_MILLIS` (the keyed effect cancels when the state changes, so the paginator's replayed initial Empty settles nothing); then `settleLaunchWhenEveryEmpty` makes the entry owner when the list holds no focus (`BrowseEntryFocus.onListFocusChanged`, fed by an `onFocusChanged` on the list in `ContentListScreenContent` and in the test scene), so the entry takes focus without a key press. The rail case stays safe: the entry's own request needs `LocalContentFocusActive`.

**Red run (TV box, production reverted, `onListFocusChanged` stubbed empty):**

- red: SRFT aOneFrameAllEmptyThenContentKeepsLaunchFocusOffTheEntry - "Failed to assert the following: (Focused = 'true')" on card 0,0 (the entry took launch focus)
- red: SRFT browseEntryTakesFocusWhenEverySectionTurnsEmptyAfterLoading - "(Focused = 'true')" failed on settle_entry (focus dropped after Loading to Empty)
- `browseEntryIsFocusableWhenEverySectionIsEmpty` became the second test (starts Loading, switches to Empty, waits for the entry to be focused, presses Down); its old RequestFocus body is replaced.

**Checks:** SectionRowFocusTraversalTest 18/18 on the box at the fix; `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL. Lease released. While finding the repro route one Home card was opened by a mistaken CENTER (details, then a player screen for about 5 s on the first run, force-stopped); no playback was started on purpose and no account data written.

### Review round 4 (triage-4 rows 1, 2)

**Row 1 (fixed, 538f5a48).** `SectionVM.dispatchListState` publishes the store's replayed initial `Paginator.State.Empty` as `SectionState.Loading` until any other paginator state has been dispatched (private flag), so an all-Empty composed state always means a finished load; the delay in `rememberBrowseEntryFocus` stays. Other consumers: `SectionState.Empty` is not a `StartupContentState`, so startup telemetry is unaffected (the comment in `SectionState.kt` now says what Empty means); `HomeVM` and the grid read the composed states, and `HomeVMTest`, `SectionVMTest`, `SectionVMRefreshDepthTest` stay green.

**Red run (unit, SectionVM change reverted):** red: SectionVMTest theStoresReplayedInitialEmptyIsPublishedAsLoadingUntilTheFirstLoadStarts - AssertionFailedError at SectionVMTest.kt:230 (state was Empty, expected Loading). Guard: SectionVMTest anEmptyFirstPageAfterTheLoadStartedIsPublishedAsEmpty (a real Empty after Loading stays Empty), green on base by design.

**Row 2 (fixed).** `browseEntryDoesNotTakeFocusFromTheHeroWhenEverySectionIsEmpty`: hero holds focus (requested while the compose clock is held, so it lands before the all-Empty delay), all rows Empty, the clock then runs 5 launch delays; the hero must keep focus and the entry must not take it. `SettleScene` got `heroFillsViewport` (default true) so the entry is composed next to a short hero; a full-viewport hero leaves the entry uncomposed and the test passes against any stub.

**Red run (TV box, `if (!listHasFocus) isOwner = true` stubbed to `isOwner = true`):** red: SRFT browseEntryDoesNotTakeFocusFromTheHeroWhenEverySectionIsEmpty - "Expected exactly '1' node but could not find any node that satisfies: (Focused = 'true') && hasAnyAncestorThat(TestTag = 'settle_hero')" (the entry took focus). Two earlier drafts (launch-focus based, full-height hero) were green or failed on the real code and were rewritten.

**Checks:** SectionRowFocusTraversalTest whole class 19/19 on the box at the fix, XML in `docs/superpowers/evidence/2026-11-24-catalogue-browse-all-itest-r5/SectionRowFocusTraversalTest.xml` (lease released).
