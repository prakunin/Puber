# Research: catalogue-filters-persist

## Request

"Keep the catalogue filter per tab across restarts" — persist the chosen ContentFilter per catalogue tab (Movies, Series, etc.) so it survives app restarts. Part 3 of the L split of the run catalogue-filters (see /home/dev/Puber/.flow/runs/catalogue-filters/brief.md, section Split, and its research.md, design-architect.md, design-ux.md, judge-1.md). catalogue-filters is merged on master.

## Understanding

Today the filter chosen in the «Фильтры» panel of a browse grid lives only in `ShowAllVM` and dies with the screen: closing the grid, restarting the app or process death reopens it unfiltered (accepted as R19 in the catalogue-filters spec). This run stores the applied filter per browse grid (keyed by the grid's config id, which is what a "tab" means here: one browse grid per catalogue tab) and restores it when that grid is next opened, so the first page loads already filtered and the header shows the summary and «Сброс».

## Current state in code

All paths under `app/src/main/java/com/kino/puber/` unless shown otherwise. Base ef5987ec.

| Where | Fact |
|---|---|
| `domain/model/ContentFilter.kt:6-18` | Plain data class; `Genre`, `Country`, `IntRange`, `ClosedFloatingPointRange<Float>` fields; not `@Serializable`, not Parcelable. `isActive` = differs from default |
| `domain/model/ContentFilter.kt:20-33` | `SortField` (7 values incl. `KINOPOISK`, `IMDB` that part 4 may remove), `SortDirection` |
| `data/api/models/Models.kt:196-200` | `Genre(id, title)` is `@Serializable`; title comes from the API |
| `ui/feature/showall/vm/ShowAllVM.kt:60-69` | `base = config`; `filterable = TabTypeConfig.isFilterable(config)`; `applied = ContentFilter()` is a plain `var`: the only holder of the filter |
| `ShowAllVM.kt:154-167` | `closeFilterPanel` applies the draft and calls `changeQuery(applied.applyTo(base), cacheable = !applied.isActive)` only when changed; `resetFilter` restores `base` with `cacheable = true`. These are the two write points a store needs |
| `ShowAllVM.kt:169-174` | `withApplied` builds the header summary from `applied` (genre title, sort, direction) |
| `ShowAllVM.kt:176-180` | Anime genre hidden in the panel when `FollowPreference` and show-anime is off; evaluated only at panel open |
| `ShowAllVM.kt:81-82` | `onStart` → `init()` starts paging with the constructor config |
| `ui/feature/contentlist/vm/ContentListPagingVM.kt:46-51` | `config = initialConfig`; `cacheable = true` hard-coded: a VM cannot start on an uncached (filtered) query without a `changeQuery` |
| `ContentListPagingVM.kt:145-160` | `changeQuery` swaps config, bumps generations, `resetPaging` |
| `ContentListPagingVM.kt:167-175` | `onLoadFirstPage` captures `config` / `cacheable` / generation before reading |
| `ui/feature/showall/model/ContentFilterQuery.kt:6-14` | `applyTo(base)` maps sort + genre onto `SectionConfig` |
| `ui/feature/showall/ShowAllScreen.kt:36` | Key `ShowAllScreen_${config.id}`; filter is not part of the screen (Parcelable carries only `config`, `title`) |
| `ShowAllScreen.kt:42-55` | `buildModule` builds `ShowAllVM` with Koin `get()`: a new singleton repository is injected here |
| `ui/feature/contentlist/model/TabTypeConfig.kt:12-28` | `browseEntryFor` covers Movies, Series, Concerts, DocMovies, DocSeries, TvShows, For4k; `isFilterable` = config id of one of those entries (`all_<type>`, `all_4k`). `all_cartoon` / `all_anime` excluded |
| `ui/feature/contentlist/vm/ContentListVM.kt:146` | Only constructor call of `ShowAllScreen`; browse entry and the «Все» tile pass the same config, so they share one key |
| `data/preferences/NavigationPreferencesRepository.kt:21-31`, `:219-245` | Pattern to reuse: `SharedPreferences` (`navigation_preferences`), `prefs.edit {}`, enum stored by `name` with fallback on unknown (`:64-67`) |
| `data/repository/PlayerPreferencesRepository.kt:14-63` | Pattern for per-id keys (`prefix + itemId`) with `putOrRemove` |
| `data/di/modules.kt:105`, `:109` | Preference repositories registered `singleOf(::…)` |
| `app/src/test/kotlin/com/kino/puber/util/FakeSharedPreferences.kt:15` | Test double used by `NavigationPreferencesRepositoryTest` and `PlayerPreferencesRepositoryTest` |
| `app/src/test/kotlin/com/kino/puber/ui/feature/showall/vm/ShowAllVMTest.kt:747-760` | VM factory in tests: one new constructor arg to add |
| `ui/feature/root/component/App.kt:78-110` | Logout clears session caches (watch state, content cache, prefetcher); preference repositories are not cleared |
| `app/src/main/AndroidManifest.xml:23` | `allowBackup="true"`, no backup rules file in `res/xml`: shared prefs fall under default backup (`unverified` on the box) |
| Persistence in the app | SharedPreferences repositories, Room 3 (`data/db/PuberDatabase.kt`, `room = 3.0.3`), kotlinx.serialization `1.13.0`; no DataStore dependency in `gradle/libs.versions.toml` |

Process death: `ShowAllScreen` is Parcelable and `ShowAllVM` is rebuilt from `config` alone, so a restored grid comes back unfiltered (spec R19). Whether Voyager restores the stack after process death in this app (`MainActivity.kt:71-77` only reads `savedInstanceState != null`) is `unverified`; a cold start opens the startup tab, so the grid is reached again by navigation.

## Related work

| Item | Relation |
|---|---|
| `catalogue-filters` (done, 948783d1) | Parent; Q2 set the lifetime to the screen instance; R19 accepted the process-death loss; judge-1 "Filter is lost on process death (by design, item 3)" |
| `catalogue-filters-country-year-status` (idea) | Adds country, year range, finished to the applied filter: the stored shape must grow without breaking stored values |
| `catalogue-filters-ratings-quality` (idea) | May remove `SortField.KINOPOISK` / `IMDB` and rating fields: a stored unknown enum must decode to the default |
| `catalogue-filters-cartoon-anime-grids` (idea) | Would add `all_cartoon` / `all_anime` to `isFilterable`; keying by config id covers them with no change |
| `catalogue-browse-all` (done) | Owns the browse configs and the screen key |
| `docs/superpowers/specs/2026-11-24-catalogue-filters.md:56`, `:319`, `:416` | Split row 3, R19, Q2 |
| `docs/architecture/paging-and-filters.md:99-128` | Filters section; needs a line on persistence after this run |
| Commits | b4dd70d4 (applyTo, `changeQuery`), d52cf9be (panel, state) |

Duplicate: none. No other run persists a catalogue filter.

## External findings

Not used: the app's own SharedPreferences and kotlinx.serialization patterns cover the need; no library question is open.

## Options

| | Approach | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | New `data/preferences/CatalogueFilterRepository` (SharedPreferences `catalogue_filters`, one key per config id, value is JSON of a `@Serializable` DTO: genre id + title, sort and direction by name). `ShowAllVM` reads it at construction when `filterable`, starts paging on `applied.applyTo(base)` with `cacheable = false` (new `initialCacheable` arg on `ContentListPagingVM`), writes on a changed close, removes on «Сброс» | Matches existing preference repos and test double; decoupled from the domain model, so parts 2 and 4 extend the DTO; isolated file. Needs a tolerant decode (unknown keys and enum names → default) | M (persistence and ViewModel state are risk areas; about 5 files plus tests) |
| B | Same, but keys inside `NavigationPreferencesRepository` | One file less; mixes a per-grid query with app navigation settings in a class already 246 lines long | M |
| C | Room table `catalogue_filter(config_id, …)` | Typed columns; schema migration and DAO for a few small values; every new filter field becomes a migration | M, heavier |

## Risks

- **Initial query path.** `ContentListPagingVM` starts `cacheable = true`; a restored filter loaded through it would read or write the stored unfiltered first page under a filtered query (breaks catalogue-filters AC-7). Starting the VM on the filtered config with `cacheable = false` avoids an extra `changeQuery` and a double first load.
- **Stale stored values.** A genre id no longer returned by `genres?type=`, the anime genre while show-anime is off (`ShowAllVM.kt:176-180` only filters the panel), or a sort field removed by part 4: each must decode or apply to a safe state, not a silently empty grid. The filtered-empty state with «Сброс» (catalogue-filters AC-12) is the fallback.
- **Genre title language.** The summary uses the stored title; after an app language change it may show the old language until the panel reloads genres (`unverified` whether genre titles vary with the app language).
- **Focus on open.** With a filter restored, first focus must still land on card 1 (catalogue-filters AC-1) and the summary and «Сброс» must be visible from the first frame; needs device check (track ui).
- **Lifetime change.** Q2 of catalogue-filters ("cleared when the grid closes") is replaced: a filter now follows the user into every later visit of that grid.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Key: per browse config id (entry and «Все» tile share one filter) or per `TabType` | Per config id; same thing today, and it covers cartoon/anime grids later without change |
| Q2 | Does the stored filter also apply when the grid is reopened in the same session | Yes: one rule, the last applied filter of that grid, until «Сброс» |
| Q3 | Per device or per account (clear on logout) | Per device, like the other catalogue settings; not cleared on logout |
| Q4 | Which fields to store now | Those the panel sets today (genre, sort, direction) in a DTO with defaults; parts 2 and 4 add fields to it |

## Sources

- Code: files in the table above at ef5987ec; `gradle/libs.versions.toml:16`, `:24`, `:33`
- Runs: `.flow/runs/catalogue-filters/{brief,state,research,design-architect,judge-1}.md`; `catalogue-filters-country-year-status`, `catalogue-filters-ratings-quality`, `catalogue-filters-cartoon-anime-grids`, `catalogue-browse-all`, `persistent-content-cache` state files
- Docs: `docs/superpowers/specs/2026-11-24-catalogue-filters.md`, `docs/architecture/paging-and-filters.md`
- Commits: b4dd70d4, d52cf9be, 948783d1
