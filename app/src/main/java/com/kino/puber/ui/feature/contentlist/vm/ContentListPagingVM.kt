package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.paginator.PagingVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

/**
 * Paging over one content-list section for screens that show it: the section row on the content
 * list and the "show all" grid behind it.
 *
 * Both page the same endpoint through the same interactor, and both have to cope with the same
 * thing: hiding watched titles is a local filter, so a server page can arrive full and come out
 * empty. Walking past those pages, and turning the items the walk collects into UI state, is the
 * whole of what this class adds to [PagingVM] — which state holds them belongs to the subclass.
 */
internal abstract class ContentListPagingVM<VS>(
    paginator: Paginator.Store<Item>,
    private val config: SectionConfig,
    protected val interactor: ContentListInteractor,
    private val mapper: VideoItemUIMapper,
    router: AppRouter,
    errorHandler: ErrorHandler,
    pagingCoroutineContext: CoroutineContext = Dispatchers.Default,
) : PagingVM<Item, VS>(paginator, router, errorHandler, pagingCoroutineContext) {

    private var currentPage = 0
    private var emptyPageChain = 0
    private var resumeRounds = 0
    private var publishedAnyItems = false
    private var cachedInput: List<Item>? = null
    private var cachedOutput: List<VideoItemUIState> = emptyList()

    /** How this list names itself in the log — "Section", "Show all". */
    protected abstract val logName: String

    final override fun onLoadFirstPage() {
        currentPage = 0
        emptyPageChain = 0
        resumeRounds = 0
        publishedAnyItems = false
        pagingLaunch(errorHandlerGeneral) { loadPage(page = 1, isFirstPage = true) }
    }

    final override fun onLoadNextPage(key: Item?) {
        pagingLaunch(errorHandlerPaging) { loadPage(page = currentPage + 1, isFirstPage = false) }
    }

    /**
     * Hiding watched titles can empty a whole server page, and a heavily watched section can empty
     * several in a row. The paginator is told to keep walking in that case rather than reading the
     * blank page as the end of the list — but only so far, or one load could walk the catalogue in
     * a single burst. What is left over is picked up by [resumeWalkAfterPause].
     */
    private suspend fun loadPage(page: Int, isFirstPage: Boolean) {
        val response = interactor.loadPage(config, page)
        currentPage = response.pagination.current
        val serverHasMore = currentPage < response.pagination.total
        if (response.items.isEmpty()) {
            emptyPageChain += 1
        } else {
            // A page that yielded something ends the fruitless stretch, and gives back the budget
            // for the next one: what is bounded is a run of blank pages, not the list.
            emptyPageChain = 0
            resumeRounds = 0
        }
        val keepWalking = serverHasMore && emptyPageChain in 1..MAX_EMPTY_PAGE_CHAIN
        val budgetIsSpent = response.items.isEmpty() && serverHasMore && !keepWalking
        isFullDataNext = !serverHasMore
        if (response.items.isNotEmpty()) publishedAnyItems = true
        // A walk that gave up without ever finding anything belongs on the empty state, not on a
        // content row holding nothing.
        if (isFirstPage || (!publishedAnyItems && !keepWalking)) {
            replace(response.items, hasMorePages = keepWalking)
        } else {
            setNextPage(response.items, hasMorePages = keepWalking)
        }
        if (budgetIsSpent) resumeWalkAfterPause()
    }

    /**
     * Picks the walk up again where the page budget ran out.
     *
     * That budget bounds one burst of requests, not the list. A run of watched titles longer than
     * the budget would otherwise leave the screen with nothing and no way past it: an empty section
     * row is hidden, so there is no retry to press and no way into "show all" either, while the
     * "show all" grid offers a retry that starts over from page one and walks into the same wall.
     *
     * The pause is what keeps this from being the same burst by another name — it hands the screen
     * back its dispatcher between rounds and gives the cancellation a place to land. A restart or a
     * closed screen drops the walk with the rest of the paging work.
     *
     * [MAX_RESUME_ROUNDS] is what keeps the rounds themselves from adding up to an unbounded one.
     * Every list on the screen walks at once, so without a ceiling a heavily watched account could
     * have several of them reading their way through the catalogue in parallel, at a page every few
     * hundred milliseconds, against an account-wide request budget the user also needs for opening
     * a title. A list that spent the ceiling without finding anything is left reading as empty,
     * which for a section watched from end to end is the honest answer anyway.
     */
    private fun resumeWalkAfterPause() {
        if (resumeRounds >= MAX_RESUME_ROUNDS) {
            log("$logName ${config.id}: nothing found in $MAX_RESUME_ROUNDS rounds, giving up")
            return
        }
        resumeRounds += 1
        val resumeFrom = currentPage + 1
        log(
            "$logName ${config.id}: nothing to show in $emptyPageChain pages, " +
                "resuming from page $resumeFrom (round $resumeRounds)"
        )
        pagingLaunch(errorHandlerPaging) {
            delay(WALK_RESUME_PAUSE)
            emptyPageChain = 0
            loadPage(page = resumeFrom, isFirstPage = false)
        }
    }

    /**
     * The paginator hands the same list instance back for several of its states in a row — data,
     * then loading-next over that data, then a page error over it. Mapping is keyed on the identity
     * of that instance so those states reuse one set of UI items instead of allocating a fresh,
     * equal-but-unequal set that Compose would have to redraw.
     */
    protected fun mapItems(items: List<Item>): List<VideoItemUIState> {
        if (items === cachedInput) return cachedOutput
        return mapper.mapShortItemList(items).also {
            cachedInput = items
            cachedOutput = it
        }
    }

    /**
     * Whether the item is among the rows this list is currently showing.
     *
     * Read from the mapping cache rather than the paginator, because that is exactly the list the
     * screen last drew.
     */
    protected fun isShowingItem(itemId: Int): Boolean =
        cachedInput?.any { it.id == itemId } == true

    /**
     * Redraws the rows already loaded against whatever the index and display settings say now,
     * without asking the server for them again.
     *
     * For a change that alters how a card looks but not which cards belong — a watched mark landing
     * while watched titles are still shown — this is the whole of the work. Re-paging would spend a
     * request per open list to arrive at the same items.
     *
     * The identity cache in [mapItems] has to be dropped first: the paginator hands back the very
     * same list instance, which is exactly what that cache is keyed on. Assigning [Paginator.Store.render]
     * re-invokes it with the state the store is already holding.
     */
    protected fun remapLoadedItems() {
        cachedInput = null
        paginator.render = ::dispatchListState
    }

    private companion object {
        /**
         * How many blank pages in a row one load will walk past. Each of them already cost the
         * interactor several server pages, so this is a ceiling on a single load, not on the list.
         */
        const val MAX_EMPTY_PAGE_CHAIN = 3

        /**
         * How many times a walk that has found nothing will spend that budget again before it
         * stops. A round costs [MAX_EMPTY_PAGE_CHAIN] + 1 loads, and the interactor reads up to
         * five server pages per load it answers blank, so this puts the ceiling for one list at
         * about eighty server pages per fruitless stretch rather than the whole catalogue.
         */
        const val MAX_RESUME_ROUNDS = 3

        /** How long the walk waits before spending the next round of that budget. */
        val WALK_RESUME_PAUSE = 500.milliseconds
    }
}
