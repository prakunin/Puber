@file:Suppress("TooManyFunctions")

package com.kino.puber.core.paginator


import com.kino.puber.core.collections.EquallyFunction
import com.kino.puber.core.collections.replaceOrInsertAtStart
import com.kino.puber.core.error.ErrorEntity
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.coroutines.CoroutineContext


@Suppress("UNCHECKED_CAST")
object Paginator {

    sealed class State(
        val isLoadingEmpty: Boolean,
        val isRefreshing: Boolean,
        val isContentEmpty: Boolean,
        val isLoadingNext: Boolean,
        val errorGeneral: ErrorEntity?,
    ) {
        object Empty : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = true,
            isLoadingNext = false,
            errorGeneral = null,
        )

        object Loading : State(
            isLoadingEmpty = true,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class Refreshing<T>(val data: List<T>) : State(
            isLoadingEmpty = data.isEmpty(),
            isRefreshing = data.isNotEmpty(),
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class LoadingPrev<T>(val data: List<T>) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class LoadingNext<T>(val data: List<T>) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = true,
            errorGeneral = null,
        )

        data class PageErrorNext<T>(val data: List<T>, val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class PageErrorPrev<T>(val data: List<T>, val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class Data<T>(val data: List<T>, val key: T? = null) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = null,
        )

        data class ErrorEmpty(val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = error,
        )

        data class Error<T>(val data: List<T>, val error: ErrorEntity) : State(
            isLoadingEmpty = false,
            isRefreshing = false,
            isContentEmpty = false,
            isLoadingNext = false,
            errorGeneral = error,
        )

        override fun toString(): String = javaClass.simpleName
    }

    sealed class Action {
        object Refresh : Action()
        object Restart : Action()
        data class RestartWithKey(val key: Any) : Action()
        object LoadNext : Action()
        object LoadPrev : Action()
        data class Error(val error: ErrorEntity) : Action()
        data class PageError(val error: ErrorEntity) : Action()
        /**
         * @param hasMorePages whether the server still has pages after this one. A page can come
         * back empty because everything on it was filtered out locally, which is not the same as
         * the list being over.
         */
        data class Replace<T>(
            val items: List<T>,
            val key: T? = null,
            val hasMorePages: Boolean = false,
        ) : Action()

        data class NextPage<T>(val items: List<T>, val hasMorePages: Boolean = false) : Action()
        data class PrevPage<T>(val items: List<T>) : Action()
        data class ItemUpdated<T>(val item: T) : Action()
        data class ItemDeleted<T>(val item: T) : Action()
        data class ItemAdded<T>(val item: T) : Action()

        override fun toString(): String = javaClass.simpleName
    }

    sealed interface SideEffect {
        data class LoadNextPage<T>(val key: T?) : SideEffect
        data class LoadPrevPage<T>(val key: T?) : SideEffect
        data object LoadFirstPage : SideEffect
        data class LoadFirstPageWithKey(val key: Any) : SideEffect
    }

    private fun <T> reducer(
        action: Action,
        state: State,
        comparator: EquallyFunction<T>,
        sideEffectListener: (SideEffect) -> Unit,
    ): State {
        return when (action) {
            Action.Refresh -> executeRefreshAction<T>(sideEffectListener, state)

            is Action.Restart -> executeRestartAction(sideEffectListener)

            is Action.RestartWithKey -> executeRestartWithKeyAction(sideEffectListener, action.key)

            is Action.Replace<*> -> executeReplaceAction(action, sideEffectListener)

            is Action.LoadPrev -> executeLoadPrevAction<T>(state, sideEffectListener)

            is Action.LoadNext -> executeLoadNextAction<T>(state, sideEffectListener)

            is Action.PrevPage<*> -> executePrevPageAction(action, state, comparator)

            is Action.NextPage<*> -> executeNextPageAction(action, state, comparator, sideEffectListener)

            is Action.ItemUpdated<*> -> executeUpdateItem(action as Action.ItemUpdated<T>, state, comparator)

            is Action.ItemDeleted<*> -> executeDeleteItem(action as Action.ItemDeleted<T>, state, comparator)

            is Action.ItemAdded<*> -> executeAddedItem(action as Action.ItemAdded<T>, state, comparator)

            is Action.PageError -> executePageErrorAction<T>(state, action)

            is Action.Error -> executeErrorAction<T>(state, action)
        }
    }


    /**
     * Reloads page one over whatever the list is already holding.
     *
     * Every state that carries a list keeps drawing it, not only [State.Data]: a refresh landing
     * while a next page was in flight, or over a page error, used to fall through to
     * [State.Loading] and take the list off the screen for the length of the reload. On a remote
     * that empties the screen the user was looking at and drops the focus with it.
     */
    private fun <T> executeRefreshAction(
        sideEffectListener: (SideEffect) -> Unit,
        state: State
    ): State {
        sideEffectListener(SideEffect.LoadFirstPage)
        val data: List<T> = dataOf<T>(state).orEmpty()
        return if (data.isEmpty()) State.Loading else State.Refreshing(data)
    }

    private fun executeRestartAction(sideEffectListener: (SideEffect) -> Unit): State {
        sideEffectListener(SideEffect.LoadFirstPage)
        return State.Loading
    }

    private fun executeRestartWithKeyAction(
        sideEffectListener: (SideEffect) -> Unit,
        key: Any
    ): State {
        sideEffectListener(SideEffect.LoadFirstPageWithKey(key))
        return State.Loading
    }

    private fun executeReplaceAction(
        action: Action.Replace<*>,
        sideEffectListener: (SideEffect) -> Unit,
    ): State {
        if (action.items.isEmpty()) {
            // Local filtering can empty a page the server still counts, and there may be pages
            // behind it. Calling that the end leaves a section reading as having no content at all,
            // with nothing left that would ask for the rest.
            if (!action.hasMorePages) return State.Empty
            sideEffectListener(SideEffect.LoadNextPage(null))
            return State.Loading
        }
        return State.Data(action.items, action.key)
    }

    private fun <T> executeLoadPrevAction(
        state: State,
        sideEffectListener: (SideEffect) -> Unit
    ): State {
        return when (state) {
            is State.Data<*> -> {
                val key = state.data.firstOrNull()
                sideEffectListener(SideEffect.LoadPrevPage(key))
                State.LoadingPrev(state.data as List<T>)
            }

            is State.Refreshing<*> -> {
                val key = state.data.firstOrNull()
                sideEffectListener(SideEffect.LoadPrevPage(key))
                State.LoadingPrev(state.data as List<T>)
            }

            is State.LoadingPrev<*> -> State.LoadingPrev(state.data as List<T>)

            is State.LoadingNext<*> -> State.LoadingPrev(state.data as List<T>)

            is State.PageErrorNext<*> -> state
            is State.PageErrorPrev<*> -> state

            is State.Error<*> -> {
                val key = state.data.firstOrNull()
                sideEffectListener(SideEffect.LoadPrevPage(key))
                State.LoadingPrev(state.data as List<T>)
            }

            // Empty, ErrorEmpty and Loading carry no page to reach back from. Answering with
            // State.Loading, as this used to, put up the full-screen spinner and emitted no side
            // effect with it, so nothing was ever going to take it down again.
            else -> state
        }
    }

    private fun <T> executeLoadNextAction(
        state: State,
        sideEffectListener: (SideEffect) -> Unit
    ): State {
        return when (state) {
            is State.Data<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.Refreshing<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.LoadingPrev<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.LoadingNext<*> -> State.LoadingNext(state.data as List<T>)

            is State.PageErrorNext<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.PageErrorPrev<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            is State.Error<*> -> {
                val key = state.data.lastOrNull()
                sideEffectListener(SideEffect.LoadNextPage(key))
                State.LoadingNext(state.data as List<T>)
            }

            // Empty, ErrorEmpty and Loading have no page to follow. State.Loading here left the
            // screen on a spinner with no request behind it: the view models draw Error and
            // PageErrorPrev as content, so scrolling to the end of one hung the list for good.
            else -> state
        }
    }

    private fun <T> executePrevPageAction(
        action: Action.PrevPage<*>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val items = action.items as List<T>
        return when (state) {
            is State.Empty -> if (items.isEmpty()) {
                State.Empty
            } else {
                State.Data(items)
            }

            is State.Data<*> -> {
                // sometimes loading prev page faster than socket and messages are duplicated
                var list = state.data as List<T>
                items.asReversed().forEach { item ->
                    list = addItemOrUpdate(list, item, comparator)
                }
                State.Data(list.toList())
            }

            is State.LoadingPrev<*> -> {
                // sometimes loading prev page faster than socket and messages are duplicated
                var list = state.data as List<T>
                items.asReversed().forEach { item ->
                    list = addItemOrUpdate(list, item, comparator)
                }
                State.Data(list.toList())
            }

            else -> State.Data(items)
        }
    }

    private fun <T> executeNextPageAction(
        action: Action.NextPage<*>,
        state: State,
        comparator: EquallyFunction<T>,
        sideEffectListener: (SideEffect) -> Unit,
    ): State {
        val items = action.items as List<T>
        // Same as a replaced page: nothing survived the filter here, but the server has more.
        if (items.isEmpty() && action.hasMorePages) {
            sideEffectListener(SideEffect.LoadNextPage(null))
            return state
        }
        return when (state) {
            is State.Empty -> if (items.isEmpty()) {
                State.Empty
            } else {
                State.Data(items)
            }

            is State.Data<*> -> {
                val currentList = (state.data as List<T>).toMutableList()
                items.forEach { item ->
                    val index = currentList.indexOfFirst { comparator.isItemTheSame(item, it) }
                    if (index >= 0) {
                        currentList[index] = item
                    } else currentList.add(item)
                }
                State.Data(currentList)
            }

            is State.LoadingNext<*> -> {
                val currentList = (state.data as List<T>).toMutableList()
                items.forEach { item ->
                    val index = currentList.indexOfFirst { comparator.isItemTheSame(item, it) }
                    if (index >= 0) {
                        currentList[index] = item
                    } else currentList.add(item)
                }
                State.Data(currentList)
            }

            else -> State.Data(items)
        }
    }

    /**
     * The list a state is carrying, or null for the states that hold none.
     *
     * [State.Error] belongs here: it keeps the pages it had when the reload failed, and the view
     * models draw it as content rather than as an error screen.
     */
    private fun <T> dataOf(state: State): List<T>? = when (state) {
        is State.Data<*> -> state.data as List<T>
        is State.Refreshing<*> -> state.data as List<T>
        is State.LoadingNext<*> -> state.data as List<T>
        is State.LoadingPrev<*> -> state.data as List<T>
        is State.PageErrorNext<*> -> state.data as List<T>
        is State.PageErrorPrev<*> -> state.data as List<T>
        is State.Error<*> -> state.data as List<T>
        else -> null
    }

    /**
     * The same state around a different list, or null for the states that carry none.
     *
     * The three item mutations below used to spell every branch out for themselves, three times
     * over. That is how [State.Error] came to be missing from all of them: an item updated while
     * the list carried a general error was dropped, and an item added to one replaced the whole
     * list with itself.
     */
    private fun <T> State.withData(items: List<T>): State? = when (this) {
        is State.Data<*> -> State.Data(items, key as T?)
        is State.Refreshing<*> -> State.Refreshing(items)
        is State.LoadingNext<*> -> State.LoadingNext(items)
        is State.LoadingPrev<*> -> State.LoadingPrev(items)
        is State.PageErrorNext<*> -> State.PageErrorNext(items, error)
        is State.PageErrorPrev<*> -> State.PageErrorPrev(items, error)
        is State.Error<*> -> State.Error(items, error)
        else -> null
    }

    private fun <T> executeUpdateItem(
        action: Action.ItemUpdated<T>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val items = dataOf<T>(state) ?: return state
        val index = items.indexOfFirst { comparator.isItemTheSame(it, action.item) }
        if (index < 0) return state
        val newList = items.toMutableList()
        newList[index] = action.item
        return state.withData(Collections.unmodifiableList(newList)) ?: state
    }

    private fun <T> executeDeleteItem(
        action: Action.ItemDeleted<T>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val items = dataOf<T>(state) ?: return state
        val newList = items.toMutableList()
        val removed = newList.removeAll { comparator.isItemTheSame(it, action.item) }
        return when {
            !removed -> state
            // Only a settled list may empty the screen. A list with a page still in flight keeps
            // its state, because that page is about to arrive and needs somewhere to land.
            newList.isEmpty() -> if (state is State.Data<*>) State.Empty else state
            else -> state.withData(Collections.unmodifiableList(newList)) ?: state
        }
    }

    private fun <T> executeAddedItem(
        action: Action.ItemAdded<T>,
        state: State,
        comparator: EquallyFunction<T>,
    ): State {
        val items = dataOf<T>(state)
            ?: return State.Data(listOf(action.item))
        return state.withData(addItemOrUpdate(items, action.item, comparator)) ?: state
    }

    private fun <T> addItemOrUpdate(
        items: List<T>,
        item: T,
        comparator: EquallyFunction<T>
    ): List<T> {
        val newList = items.toMutableList()
        newList.replaceOrInsertAtStart(item, comparator)
        return Collections.unmodifiableList(newList)
    }

    private fun <T> executePageErrorAction(state: State, action: Action.PageError): State {
        return when (state) {
            is State.LoadingNext<*> -> State.PageErrorNext(state.data as List<T>, action.error)

            is State.LoadingPrev<*> -> State.PageErrorPrev(state.data as List<T>, action.error)

            is State.Refreshing<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            is State.Data<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            else -> State.ErrorEmpty(action.error)
        }
    }

    private fun <T> executeErrorAction(state: State, action: Action.Error): State {
        return when (state) {
            is State.Refreshing<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            is State.Data<*> -> {
                val items = state.data as List<T>
                if (items.isEmpty()) {
                    State.ErrorEmpty(action.error)
                } else {
                    State.Error(items, action.error)
                }
            }

            else -> State.ErrorEmpty(action.error)
        }
    }


    class Store<T>(
        coroutineContext: CoroutineContext = Dispatchers.Default.limitedParallelism(1),
        private val comparator: EquallyFunction<T>,
    ) :
        CoroutineScope by CoroutineScope(
            coroutineContext + CoroutineName("Paginator.State"),
        ) {
        private var state: State = State.Empty
        private val actions = Channel<Action>(Channel.UNLIMITED)

        var render: (State) -> Unit = {}
            set(value) {
                field = value
                value(state)
            }

        val sideEffects = MutableSharedFlow<SideEffect>()

        init {
            launch {
                for (action in actions) {
                    val newState = reducer(action, state, comparator) { sideEffect ->
                        launch { sideEffects.emit(sideEffect) }
                    }
                    if (state != newState) {
                        state = newState
                        render(newState)
                    }
                }
            }
        }

        fun proceed(action: Action) {
            actions.trySend(action)
        }

        fun close() {
            actions.close()
            cancel()
        }
    }
}

fun <T> Paginator.Store<T>.itemAdded(item: T) = proceed(Paginator.Action.ItemAdded(item))

fun <T> Paginator.Store<T>.itemUpdated(item: T) = proceed(Paginator.Action.ItemUpdated(item))

fun <T> Paginator.Store<T>.itemDeleted(item: T) = proceed(Paginator.Action.ItemDeleted(item))

fun <T> Paginator.Store<T>.refresh() = proceed(Paginator.Action.Refresh)

fun <T> Paginator.Store<T>.restart() = proceed(Paginator.Action.Restart)

fun <T> Paginator.Store<T>.restartWithKey(key: Any) = proceed(Paginator.Action.RestartWithKey(key))

fun <T> Paginator.Store<T>.loadNext() = proceed(Paginator.Action.LoadNext)

fun <T> Paginator.Store<T>.loadPrev() = proceed(Paginator.Action.LoadPrev)

fun <T> Paginator.Store<T>.replace(list: List<T>, key: T? = null, hasMorePages: Boolean = false) =
    proceed(Paginator.Action.Replace(list, key, hasMorePages))

fun <T> Paginator.Store<T>.nextPage(list: List<T>, hasMorePages: Boolean = false) =
    proceed(Paginator.Action.NextPage(list, hasMorePages))

fun <T> Paginator.Store<T>.prevPage(list: List<T>) = proceed(Paginator.Action.PrevPage(list))

fun Paginator.Store<*>.error(error: ErrorEntity) = proceed(Paginator.Action.Error(error))

fun Paginator.Store<*>.pageError(error: ErrorEntity) = proceed(Paginator.Action.PageError(error))
