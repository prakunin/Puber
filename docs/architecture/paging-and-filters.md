# Paging And Filters

## PagingVM

`core/paginator/PagingVM.kt`:

```kotlin
abstract class PagingVM<T, VS>(
    protected val paginator: Paginator.Store<T>,
    router: AppRouter,
    override val errorHandler: ErrorHandler,
    pagingCoroutineContext: CoroutineContext = Dispatchers.Default,
) : PuberVM<VS>(router)
```

`errorHandler` is required here, unlike in `PuberVM`. Paging work runs on its
own `SupervisorJob` scope, separate from `viewModelScope`, so a failed page does
not tear down the screen.

Call `init(key)` from `onStart()`. It wires `paginator.render` to
`dispatchListState`, subscribes to the side-effect stream, hooks the optional
`newItemsFlow()` / `updateItemsFlow()` / `deleteItemsFlow()` overrides, and
kicks off `resetPaging(key)`.

Implement:

- `onLoadFirstPage()` - load page one, finish with
  `replace(list, key = null, hasMorePages = ...)`.
- `onLoadNextPage(key: T?)` - finish with `setNextPage(list, hasMorePages = ...)`.
- `dispatchListState(state: Paginator.State)` - map paginator state to view state.
- optional `onLoadFirstPageWithKey(key)` when paging is keyed.

Available to the subclass: `refresh()`, `resetPaging(key)`,
`refreshPagingKeepingContent()`, `notifyLoadNextPage()`,
`notifyLoadingPrev(forced)`, `setPrevPage(list)`, `updateItem(item)`,
`setPageError(error)`, `setGeneralError(error)`, and
`pagingLaunch(context, start) { }`.

`resetPaging(key)` and `refreshPagingKeepingContent()` are a pair and differ in
what the user sees. `resetPaging` clears the list first, which is right when the
list's identity changed - a new filter, a new query. `refreshPagingKeepingContent`
reloads the same list and keeps drawing it meanwhile, because on a remote an
emptied list has no card left to hold the focus.

Run paging coroutines through `pagingLaunch`, passing the matching handler:
`errorHandlerGeneral` for the first page (a failure replaces the screen) and
`errorHandlerPaging` for later pages (a failure only marks the page). Both are
lazy properties on `PagingVM`. `ContentListPagingVM.kt` shows the pattern.

## Paginator states

`Paginator.State` (`core/paginator/Paginator.kt`) has ten variants: `Empty`,
`Loading`, `Refreshing<T>`, `LoadingPrev<T>`, `LoadingNext<T>`,
`PageErrorNext<T>`, `PageErrorPrev<T>`, `Data<T>`, `ErrorEmpty`, and
`Error<T>`. A `when` over a sealed class must cover all of them, so copying a
shortened example will not compile.

`ShowAllVM.dispatchListState` is the reference implementation: `Loading` and
`Empty` become their own states, `ErrorEmpty` becomes an error screen, and every
variant that carries data - `Data`, `LoadingNext`, `Error`, `PageErrorNext`,
`Refreshing` - keeps rendering the items, with `LoadingNext` additionally
setting a "loading more" flag. That asymmetry is the point: once the user has
items on screen, a failed page must not blank it.

`Paginator.Store<T>` is created in the screen's `buildModule` together with the
comparator it dedupes on. Register it as a scoped dependency when the VM's other
constructor parameters are all in the graph:

```kotlin
scoped { Paginator.Store<History>(comparator = HistoryRowComparator) }
viewModelOf(::HistoryVM)
```

Use an explicit `viewModel { }` block when the VM also needs a value the graph
does not hold, such as the screen's config (`ShowAllScreen.kt`):

```kotlin
viewModel {
    ShowAllVM(
        paginator = Paginator.Store { old, new -> old.id == new.id },
        config = config,
        interactor = get(),
        router = get(),
        errorHandler = get(),
    )
}
```

## Search is not paged

`SearchVM` (`ui/feature/search/vm/SearchVM.kt`) is a plain `PuberVM`, not a
`PagingVM`. It keeps the query and a single `searchJob`, and every route into a
search - typing, retry, re-entering the screen - cancels that job first, so
there is only ever one in flight. Queries shorter than the minimum reset the
screen to `Idle`; typing debounces inside the job with `delay()`, which is also
what cancellation relies on. Do not run the request in a second `launch`: it
would escape `searchJob`, and a stale answer could land after a newer one.

## Filters

There is no chip-state framework. Catalogue filtering is a plain data class,
`domain/model/ContentFilter.kt`:

```kotlin
data class ContentFilter(
    val genre: Genre? = null,
    val country: Country? = null,
    val sort: SortField = SortField.UPDATED,
    val sortDirection: SortDirection = SortDirection.DESC,
    val yearRange: IntRange? = null,
    val kinopoiskRating: ClosedFloatingPointRange<Float>? = null,
    val imdbRating: ClosedFloatingPointRange<Float>? = null,
    val quality: String? = null,
    val finished: Boolean? = null,
)
```

`SortField` and `SortDirection` carry their own `apiValue`, so the VM never
hardcodes an API sort string.

What a catalogue tab shows is configured, not coded: `SectionConfig`
(`ui/feature/contentlist/model/SectionConfig.kt`) describes one shelf - type,
shortcut, sort, quality, genre, required genre id, and an `AnimeFilterMode` of
`None`, `FollowPreference`, `Exclude`, or `Only`. `TabTypeConfig.sectionsFor(tabType)`
returns the sections for a tab, and `ContentListScreen` builds its scope from
that list. Adding a shelf usually means adding a `SectionConfig`, not a screen.

After any filter change, restart paging with `resetPaging()`.
