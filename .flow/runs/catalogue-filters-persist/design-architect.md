# Design (architect): catalogue-filters-persist

Base ef5987ec. Paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/` or `docs/`. ACs: `brief.md#Acceptance criteria`. Owner answers Q1-Q3 of the brief are taken as decided.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Where filters are stored | New `data/preferences/CatalogueFilterRepository(context)`: SharedPreferences `catalogue_filters`, key `filter_<config.id>`, value JSON; `singleOf(::CatalogueFilterRepository)` beside `NavigationPreferencesRepository` (`data/di/modules.kt:109`) | Same shape as the other preference repos and their `FakeSharedPreferences` test double (`app/src/test/kotlin/com/kino/puber/util/FakeSharedPreferences.kt:15`); per config id (Q1); a separate file keeps `NavigationPreferencesRepository` (246 lines) out of it |
| Repository API | `fun read(configId: String): ContentFilter` (default when absent or undecodable); `fun write(configId: String, filter: ContentFilter)` removes the key when `!filter.isActive`; `fun clear(configId: String)` | One write path; an inactive filter is never stored, so «Сброс» and "chose the defaults by hand" leave the same file (AC-5) |
| Stored shape (AC-7) | Private `@Serializable data class StoredFilter(val genreId: Int? = null, val genreTitle: String? = null, val sort: String? = null, val direction: String? = null)` in the repository file; decoded with `Json { ignoreUnknownKeys = true }` inside `runCatching`; `sort` / `direction` matched by `name` against `SortField` / `SortDirection` (`domain/model/ContentFilter.kt:20-33`) with `UPDATED` / `DESC` fallback, as `getStartupTab` does (`NavigationPreferencesRepository.kt:64-67`); genre kept only when both id and title are present | Enum names as strings, not `@Serializable` enums, so a value part 4 removes decodes to the default instead of failing the whole entry; parts 2 and 4 add nullable fields with defaults. `ContentFilter` stays unannotated |
| Which grids read and write | Only when `filterable` (`ui/feature/showall/vm/ShowAllVM.kt:61`); shelves never touch the repository | AC-9 |
| How the restored filter reaches the first load (AC-1, AC-4) | `ContentListPagingVM` gains a last constructor param `initialCacheable: Boolean = true` backing `cacheable` (`ui/feature/contentlist/vm/ContentListPagingVM.kt:50-51`). `ShowAllVM` passes `initialConfig = restored.applyTo(config)` and `initialCacheable = !restored.isActive`; `onLoadFirstPage` (`:167-174`) already captures both | The first load runs once on the filtered query with `observeFirstPage(cacheable = false)` (`domain/interactor/contentlist/ContentListInteractor.kt:60-70`), which neither reads nor writes `cached_payload`. No `changeQuery` at start, so no double load and no unfiltered cached page drawn first. `SectionVM` keeps the default |
| Where restoration is computed | `ShowAllVM` gets a private primary constructor taking `restored: ContentFilter` and a public secondary constructor with today's parameters plus `filterRepository: CatalogueFilterRepository`, delegating with `restored = restoreFilter(config, filterRepository, interactor)` (companion function) | The super-constructor call needs the value before any `ShowAllVM` property exists; the Koin factory (`ui/feature/showall/ShowAllScreen.kt:44-54`) and the test factory (`app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt:741-755`) keep named arguments and add one |
| Stored «Аниме» with show-anime off (AC-8, Q3) | `restoreFilter` drops the genre when it is `ANIME_GENRE_ID` (`data/api/models/ContentGenres.kt:4`), `config.animeFilterMode == FollowPreference` and `!interactor.showAnimeEnabled` (`ContentListInteractor.kt:52-53`); sort and direction stay. The stored entry is not rewritten at open | With the genre applied, `fetchFilteredPage` drops every item and walks with no page budget when hide-watched is off (`ContentListInteractor.kt:144-178`). Not rewriting keeps the genre for when show-anime is back on |
| Initial header state (AC-1) | `mutableFilterState` (`ShowAllVM.kt:62-64`) starts as `ShowAllFilterState(available = true).withApplied(restored)` and `applied` (`:69`) starts as `restored` | Summary and «Сброс» are in the first composed state, before any page; `openFilterPanel` (`:144-145`) then seeds the draft with the restored filter (AC-6) |
| Write points | `closeFilterPanel` (`:154-160`): inside `if (changed)`, `filterRepository.write(base.id, applied)` before `changeQuery`. `resetFilter` (`:162-167`): `filterRepository.clear(base.id)` after the `isActive` guard | Unchanged close writes nothing (AC-6); reset removes the entry (AC-5). A changed close replaces the whole entry, so a skipped stored «Аниме» is dropped only when the user applies another filter |
| Same-session reopen (AC-2, AC-3) | Nothing beyond the above: every `ShowAllVM` reads the repository at construction; entry and «Все» tile share `config.id` (`ui/feature/contentlist/vm/ContentListVM.kt:146`) | One rule for reopen and restart |
| Logout | Not cleared (Q2); `ui/feature/root/component/App.kt:78-110` unchanged | Per-device setting, like the other preference repos |
| «Сброс» on a cold open (AC-1 focus) | The header «Сброс» gets the same `canFocus` gate as «Фильтры» (`ui/feature/showall/content/ShowAllHeader.kt:93-99` gains `this.canFocus = canFocus`) | Today the gate covers only «Фильтры» (`ShowAllHeader.kt:76-77`, latch `ShowAllScreenContent.kt:84-87`) because «Сброс» never existed on a cold open; with a restored filter it is the only focusable during `Loading` and would take first focus instead of card 1 |

## Changes by layer

**API / repository.**

| File | Change |
|---|---|
| `data/preferences/CatalogueFilterRepository.kt` (new) | Repository, `StoredFilter` DTO, mapping to and from `ContentFilter` (genre, sort, direction) |
| `data/di/modules.kt:109` | `singleOf(::CatalogueFilterRepository)` |

**ViewModel state and actions.**

| File | Change |
|---|---|
| `ui/feature/contentlist/vm/ContentListPagingVM.kt:31-39`, `:50-51` | `initialCacheable: Boolean = true` param; `cacheable = initialCacheable` |
| `ui/feature/showall/vm/ShowAllVM.kt:40-58` | Private primary + public secondary constructor; super gets the restored query and `initialCacheable` |
| `ShowAllVM.kt:62-69` | Initial `filterState` and `applied` from `restored` |
| `ShowAllVM.kt:154-167` | `write` on a changed close, `clear` on reset |
| `ShowAllVM.kt` companion (new) | `restoreFilter(config, repository, interactor)`: default when not filterable; anime skip |

**Screen and DI module.** `ui/feature/showall/ShowAllScreen.kt:44-54`: `filterRepository = get()`; key `:37` unchanged.

**Compose content.** `ui/feature/showall/content/ShowAllHeader.kt:93-99`: `canFocus` on «Сброс».

**Resources.** N/A: no new strings.

**Docs.** `docs/architecture/paging-and-filters.md:128`: one sentence that a browse grid's applied filter is stored per config id in `CatalogueFilterRepository` and restored at VM construction with `initialCacheable = false`.

**Tests.**

| File | Change |
|---|---|
| `app/src/test/kotlin/com/kino/puber/data/preferences/CatalogueFilterRepositoryTest.kt` (new) | Round trip; inactive write removes; `clear`; malformed JSON, unknown keys, unknown enum names (AC-5, AC-7) on `FakeSharedPreferences` with a mocked `Context`, as `NavigationPreferencesRepositoryTest.kt:473-490` |
| `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt:741-755` | Factory gains `filterRepository` (mockk, `read` returns `ContentFilter()` by default); new cases for AC-1 to AC-6, AC-8, AC-9 asserting the `observeFirstPage(config, force, cacheable)` arguments and repository calls |

## Alternatives rejected

- `changeQuery` from `onStart` on a restored filter: two first-page loads, the first of them on the cached unfiltered page.
- Keys inside `NavigationPreferencesRepository`: mixes a per-grid query into app navigation settings (research Option B).
- Room table: a migration per new filter field (research Option C).
- `@Serializable` on `ContentFilter` or its enums: an enum removed by part 4 fails the whole decode; `IntRange` / float ranges need custom serializers.
- Filter in `ShowAllScreen` args or key: splits Voyager state, DI scope and focus memory per filter, and does not survive a reopen.
- Restoration computed in the Koin factory: puts VM logic in DI and outside `ShowAllVMTest`.

## Risks

| Area | Risk | Lives in |
|---|---|---|
| Focus | Cold open with a restored filter: «Сброс» focusable before the grid lands takes first focus; device check of AC-1 | `ShowAllHeader.kt:84-99`, `ShowAllScreenContent.kt:84-87` |
| Correctness | A restored first page read through the cache path would show unfiltered cards under a filtered header, or store a filtered page as the unfiltered one (catalogue-filters AC-7) | `ContentListPagingVM.kt:50-51`, `:170-174`; `ContentListInteractor.kt:67-70` |
| Correctness | A stored genre the API no longer returns, or «Аниме» on a grid whose mode is not `FollowPreference`, pages an empty grid; the filtered-empty state with «Сброс» is the exit | `ShowAllScreenContent.kt:181-196` |
| Data shape | Parts 2 and 4 change fields or enum values; a strict decode would drop every stored filter | `CatalogueFilterRepository.kt` |
| Lifecycle | Show-anime turned off while a grid with «Аниме» applied stays open: not re-evaluated until the grid is reopened (same as today) | `ShowAllVM.kt` `restoreFilter` |
| Main-thread work | First `read` blocks on loading `catalogue_filters.xml` during VM construction on the main thread; one small file, once per process | `CatalogueFilterRepository.kt`; `ShowAllScreen.kt:44-54` |
| Data shape | Stored genre title is shown in the summary until the panel reloads genres; stale after a server rename | `ShowAllVM.kt:169-174` |
| Rollout | `allowBackup="true"` (`app/src/main/AndroidManifest.xml:23`) carries stored filters to a restored install | `catalogue_filters` prefs |
| Baseline profile | New repository class and the restored-filter start path are not in the generated profile until regeneration | `app/src/main/generated/baselineProfiles/baseline-prof.txt` |

## Open questions

N/A: brief Q1-Q3 answered by the owner; no new question.
