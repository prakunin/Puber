# ViewModel

Base class: `core/ui/PuberVM.kt`. Paginated screens use `PagingVM` on top of it
(see [paging-and-filters.md](paging-and-filters.md)).

```kotlin
internal class BookmarksVM(
    router: AppRouter,
    private val interactor: BookmarkInteractor,
    private val mapper: VideoItemUIMapper,
    override val errorHandler: ErrorHandler,
) : PuberVM<BookmarksViewState>(router) {

    override val initialViewState: BookmarksViewState = BookmarksViewState.Loading

    override fun onStart() { loadFolders() }

    override fun onAction(action: UIAction) {
        when (action) {
            CommonAction.RetryClicked -> loadFolders()
            is CommonAction.ItemSelected<*> -> onItemSelected(action)
            else -> super.onAction(action)
        }
    }
}
```

Shortened from `ui/feature/bookmarks/vm/BookmarksVM.kt`, which takes two more
collaborators. `errorHandler` is optional: a view model that maps its own
failures into state, such as `FavoriteVM`, leaves it null and never calls
`dispatchError`.

## What the base class gives you

| Member | Shape |
|---|---|
| `initialViewState` | `protected abstract val`, seeds the state flow |
| `updateViewState(viewState)` | replace the whole state |
| `updateViewState<T> { copy(...) }` | reified: applies only when the current state is `T` |
| `stateValue` | current state, `protected` |
| `collectViewState(initial)` | `State<ViewState>` for the composable |
| `onStart()` | runs once, guarded; see `ensureStarted()` |
| `launch { }` | `viewModelScope` plus the error handler wiring below |
| `showMessage(String)` / `showMessage(SnackbarMessage)` | transient message, `collectMessage()` on the UI side |
| `onAction(action)` | `open`; always end the `when` with `else -> super.onAction(action)` |
| `onBackPressed()` | routes back through `AppRouter` |

## Errors

`errorHandler` is `protected open val errorHandler: ErrorHandler? = null`. A VM
that wants error handling must override it, normally as a constructor
parameter - without the override it stays null and failures inside `launch { }`
go nowhere.

The real interface (`core/error/ErrorHandler.kt`) is:

```kotlin
interface ErrorHandler {
    fun proceed(action: ((ErrorEntity) -> Unit)? = null): (Throwable) -> Unit
    fun proceedInvoke(e: Throwable, action: ((ErrorEntity) -> Unit)? = null)
    fun map(e: Throwable): ErrorEntity
}
```

`ErrorEntity` is `data class ErrorEntity(val message: String, val code: String)`
- both fields are required, there is no single-argument constructor.

`launch { }` installs a handler that calls
`errorHandler?.proceedInvoke(it, ::dispatchError)`, so overriding
`dispatchError(error: ErrorEntity)` is where a screen decides what a failure
looks like. The rule the screens follow: if the user is already looking at
content, keep it and show a message; if the screen is still loading, switch to
an error state.

```kotlin
override fun dispatchError(error: ErrorEntity) {
    if (stateValue is MyViewState.Loading) {
        updateViewState(MyViewState.Error(error.message))
    } else {
        showMessage(error.message)
    }
}
```

Never swallow `CancellationException`: rethrow it before the generic `catch`, or
skip the manual try/catch and let `launch` handle the failure.

## State and actions

View state is a sealed class marked `@Immutable`, with the states the screen can
actually render. Actions are a sealed interface extending `UIAction`;
`CommonAction` (`core/ui/uikit/model/Actions.kt`) already covers the shared
ones - `RetryClicked`, `Refresh`, `ItemSelected<T>`, `ItemPlayed<T>`,
`ItemFocused<T>`, `ItemRemoved<T>`, `TextChanged`, `LoadMore`,
`ReloadNextPage`, `SnackBarDismissed`, and more. Reuse them before inventing a
screen-specific action.

Mapping API models to UI state belongs in a `*UIMapper` registered with
`scopedOf(...)`, using `ResourceProvider` for strings.

## Patterns worth copying

Parallel loads - one `launch`, `coroutineScope { async { } }` inside, so a
failure in either branch cancels the other and reaches `dispatchError`.

Observed data - collect a flow in `onStart()` and update state from it; the
interactors expose `Flow` for watchlist and history (`observeWatchlist`).

Forms - hold a private draft, and rebuild the content state on every change:

```kotlin
private var draft = params.entity?.let(::toDraft) ?: Draft()

private fun updateDraft(block: Draft.() -> Draft) {
    draft = draft.block()
    updateViewState(mapDraft(draft))
}
```

Debounced work - keep the `Job` and cancel it before starting the next one,
rather than debouncing inside the composable.
