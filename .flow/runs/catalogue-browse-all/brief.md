# Brief: catalogue-browse-all

Kind: feature   Size: M   Tracks: ui   Device verification: yes (TV UI, focus, D-pad, navigation, screen wiring)

## Request

Owner, 2026-11-24: «сейчас нет возможности просмотреть все к примеру Сериалы и нет фильтрации». This run covers the first half: a direct entry per content type that pages the whole type in the catalogue-poster-grid grid. Filtering is part 3, run `catalogue-filters`, which builds on this run's grid.

## Findings

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

## Risks

- **TV focus.** A non-page item above the shelves breaks the one-section-per-page rule (`ContentListScreenContent.kt:150-153`), shifts `sectionListItemIndex` and meets `focusRestorer()` on the `LazyColumn`; Show all entry, rail and Back have a long bug history (`research.md: Related work`). Guarded by AC-2, AC-7, AC-8, AC-10.
- **Hide-watched.** A whole-type grid with hide-watched on walks up to 5 server pages per step (`ContentListInteractor.kt:202`) and can show short steps on a heavily watched account; behaviour unchanged, seen on device under AC-5.
- **Shared key.** Entry and «Все» tile share `ShowAllScreen_all_<type>`, so they share Voyager state and focus memory by design (AC-4).
- **Part 3 seam.** `ContentListPagingVM.config` is immutable; `catalogue-filters` must add a mutable query or reopen the screen per filter. Not changed here.
- **`ContentFilter` removal** by `dead-code-sweep` would take part 3's model (Q3).

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Where the browse entry sits | One focusable button above the first shelf of each enabled catalogue tab, 4K included; the «Все» shelf stays and opens the same grid (research Option A, Q2, Q3) | yes |
| Q2 | Grid header content | Title only on every Show all grid (AC-6); no item count, because `total_items` ignores client-side anime and hide-watched filtering; part 3 adds filter/sort controls to this header | yes |
| Q3 | `dead-code-sweep` vs `ContentFilter` | Keep `ContentFilter`; the conductor drops it from `dead-code-sweep`'s scope while `catalogue-filters` is open | no |

Assumed from `research.md: Open questions` Q4: default order `updated-`; sort choice is part 3.
