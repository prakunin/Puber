# Brief: catalogue-filters-persist

Kind: feature   Size: M   Tracks: none   Device verification: yes (ViewModel state, persistence, first focus on a restored grid)

## Request

Part 3 of the `catalogue-filters` split (`../catalogue-filters/brief.md#Split`): keep the filter applied on a browse grid across closing the grid and restarting the app. Supersedes catalogue-filters Q2 (lifetime = screen instance) and R19 (`docs/superpowers/specs/2026-11-24-catalogue-filters.md:319`, `:416`).

## Findings

Code map, related runs and options: `research.md: Current state in code`, `Related work`, `Options` (option A recommended). Checked at ef5987ec; the facts this brief relies on:

| Where | Fact |
|---|---|
| `app/src/main/java/com/kino/puber/ui/feature/showall/vm/ShowAllVM.kt:60-69` | `applied = ContentFilter()` is the only holder of the filter; `filterable` gates it |
| `ShowAllVM.kt:154-167` | The two write points: `closeFilterPanel` (changed draft) and `resetFilter` |
| `app/src/main/java/com/kino/puber/ui/feature/contentlist/vm/ContentListPagingVM.kt:46-51` | `config = initialConfig`, `cacheable = true` fixed: a VM cannot start on an uncached filtered query without a `changeQuery` |
| `app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt:143-190` | On a `FollowPreference` grid with show-anime off every anime item is dropped client-side, so a restored «Аниме» genre (id 25) pages an empty grid |
| `app/src/main/java/com/kino/puber/domain/model/ContentFilter.kt:6-33` | Not `@Serializable`; `SortField.KINOPOISK` / `IMDB` may be removed by part 4 |
| `app/src/main/java/com/kino/puber/ui/feature/showall/ShowAllScreen.kt:36`, `:42-55` | Key `ShowAllScreen_${config.id}`; `buildModule` is where a new singleton is injected |
| `app/src/main/java/com/kino/puber/data/preferences/NavigationPreferencesRepository.kt:64-67`, `data/di/modules.kt:109` | Pattern: SharedPreferences repo, enum by `name` with fallback, `singleOf` |

## Size

M: one goal, but it touches persistence and ViewModel state/start-up of paging (risk areas in `.flow/roles/analyst.md`). About 5 production files plus tests and `docs/architecture/paging-and-filters.md:99-128`.

## Acceptance criteria

| ID | Given | When | Then |
|---|---|---|---|
| AC-1 | genre «Драма» and sort «по году ↑» applied on `all_movie` | the app process is killed and restarted, and the Movies browse grid is opened | the first request carries `sort=year` and `genre=<drama id>` with the base `type`; the header shows the summary and «Сброс» from the first frame; first focus lands on card 1 (VM unit test for the request and state; device for frame and focus) |
| AC-2 | a filter applied on a grid | the grid is closed and reopened in the same session (entry or «Все» tile) | it opens with that filter (VM unit test) |
| AC-3 | a filter applied on `all_movie` | `all_serial` and `all_4k` are opened | each opens with its own stored filter or unfiltered; none inherits Movies' (VM unit test) |
| AC-4 | a restored filter | the first page loads | exactly one first-page load runs, on the filtered query, and it neither reads nor writes the persistent first-page cache (VM unit test with a fake interactor) |
| AC-5 | a restored filter | «Сброс» is pressed, then the grid is reopened | the stored entry is removed; the reopened grid is unfiltered and uses the first-page cache (VM and repository unit tests) |
| AC-6 | the panel is opened on a restored filter | — | the draft equals the restored filter and its genre and sort rows show as chosen; closing unchanged writes nothing and does not re-page (VM unit test) |
| AC-7 | a stored value that is malformed JSON, has unknown keys, or names an unknown sort field or direction | the grid opens | no crash; unknown keys are ignored, an unknown enum falls back to its default, malformed JSON yields no filter (repository unit test) |
| AC-8 | «Аниме» stored on `all_movie` / `all_serial` and show-anime turned off | the grid opens | the genre is not applied (sort and direction still are); the grid is not empty because of it (VM unit test) |
| AC-9 | a shelf grid (`fresh_*`, `hot_*`, `popular_*`, `4k_<type>`) | opened and used | the store is neither read nor written (VM unit test) |

## Risks

- Starting paging on the filtered query needs a cacheable flag at construction (`ContentListPagingVM.kt:46-51`); a `changeQuery` from `onStart` instead would double the first load and can read the unfiltered cached page (breaks catalogue-filters AC-7).
- The stored genre title is shown in the summary before genres reload; a genre the API no longer returns pages an empty grid, which falls back to the existing filtered-empty state with «Сброс» (catalogue-filters AC-12).
- Stored shape must grow with parts 2 and 4 (country, year, finished; possible removal of rating sorts): a DTO with defaults, decoded with `ignoreUnknownKeys`, separate from `ContentFilter`.

## Open questions

| ID | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Key the store per browse config id or per `TabType` | Per config id (entry and «Все» tile share it; cartoon/anime grids work later unchanged) | no |
| Q2 | Clear stored filters on logout | No: per device, like the other catalogue settings (`app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:78-110` clears no preference repo) | no |
| Q3 | Stored «Аниме» while show-anime is off: drop it from the store or only skip it | Skip it on restore, keep it stored, so turning show-anime back on restores it (AC-8) | no |
