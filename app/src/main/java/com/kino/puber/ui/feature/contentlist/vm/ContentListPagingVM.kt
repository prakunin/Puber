package com.kino.puber.ui.feature.contentlist.vm

import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.logger.log
import com.kino.puber.core.paginator.Paginator
import com.kino.puber.core.paginator.PagingVM
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.PaginatedResponse
import com.kino.puber.data.cache.Cached
import com.kino.puber.domain.interactor.contentlist.ContentListInteractor
import com.kino.puber.ui.feature.contentlist.model.SectionConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
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

    /**
     * How many times a caller has demanded that the next first-page load reach the server, against
     * how many of those demands a load has carried out.
     *
     * A single flag cannot express this. [resetPaging] restarts through the paginator, so the load a
     * demand belongs to only starts after a round trip through the store's dispatcher — and a second
     * signal arriving inside that window restarts again and cancels the first load. A flag consumed
     * when the load starts would leave with that cancelled load, and the load that survives would
     * serve the stored page for the rest of its TTL after an event that was supposed to guarantee a
     * request. Counting instead, the demand stands until a load has run to completion with it.
     */
    private val forcedReadsDemanded = AtomicInteger(0)
    private val forcedReadsServed = AtomicInteger(0)

    /**
     * Which first-page publication the walk currently belongs to.
     *
     * Both publications of one load — the stored page and the fresh one behind it — arrive in the same
     * collection, with no paginator restart in between, so nothing cancels the walk the first of them
     * started. Its outstanding request can therefore land after the second has replaced the list, and
     * would otherwise append to it, drag [currentPage] back to where the old walk had got to and
     * spend the new walk's budget. Each publication takes the next number and a walk step only
     * publishes under the number it was started with.
     */
    private val walkGeneration = AtomicInteger(0)

    /** How this list names itself in the log — "Section", "Show all". */
    protected abstract val logName: String

    /**
     * Restarts paging and guarantees that the first page is asked of the server rather than only of
     * the cache.
     *
     * This is what every signal that knows the server's answer has changed goes through — a retry, a
     * return from details or the player with a content change, a display-setting flip. The stored
     * page is still drawn first; what the demand adds is the request behind it.
     */
    fun refreshFirstPage() {
        forcedReadsDemanded.incrementAndGet()
        resetPaging()
    }

    final override fun onLoadFirstPage() {
        val demanded = forcedReadsDemanded.get()
        val force = demanded > forcedReadsServed.get()
        pagingLaunch(errorHandlerGeneral) {
            interactor.observeFirstPage(config, force = force).collect { cached ->
                when (cached) {
                    is Cached.Value -> publish(cached.value, isFirstPage = true)
                    // Nobody asked for this refresh and there is already content on screen, so a
                    // failure is not the user's problem: the stored page stands.
                    is Cached.RefreshFailed -> log(
                        cached.error,
                        "$logName ${config.id}: background refresh failed",
                    )
                }
            }
            // Only a load that got this far has made the request the demand asked for. One cancelled
            // by the next restart, or one that failed, leaves the demand standing for its successor.
            // The highest wins, so a slower load settling after a newer one cannot revive a demand
            // that newer one has already served.
            if (force) {
                forcedReadsServed.accumulateAndGet(demanded) { served, carried -> maxOf(served, carried) }
            }
        }
    }

    final override fun onLoadNextPage(key: Item?) {
        walkStep(page = currentPage + 1)
    }

    /**
     * One step of the walk, tied to the publication that started it. A step whose generation has
     * moved on belongs to a list that is no longer on screen, so its page is dropped rather than
     * published.
     */
    private fun walkStep(page: Int) {
        pagingLaunch(errorHandlerPaging) { publishWalkStep(page, walkGeneration.get()) }
    }

    private suspend fun publishWalkStep(page: Int, generation: Int) {
        val response = interactor.loadPage(config, page)
        if (generation != walkGeneration.get()) return
        publish(response, isFirstPage = false)
    }

    /**
     * Hiding watched titles can empty a whole server page, and a heavily watched section can empty
     * several in a row. The paginator is told to keep walking in that case rather than reading the
     * blank page as the end of the list — but only so far, or one load could walk the catalogue in
     * a single burst. What is left over is picked up by [resumeWalkAfterPause].
     *
     * @param isFirstPage a page-one publication — of which there are now two per load, the stored
     * one and the fresh one. Each starts its own walk, so the counters reset here rather than once
     * per load: counted together, two pages emptied by filtering would read as four.
     */
    private fun publish(response: PaginatedResponse<Item>, isFirstPage: Boolean) {
        if (isFirstPage) {
            // Ahead of everything else, so a walk this publication starts belongs to it and the one
            // the previous publication started is left behind.
            walkGeneration.incrementAndGet()
            emptyPageChain = 0
            resumeRounds = 0
            publishedAnyItems = false
        }
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
        val generation = walkGeneration.get()
        pagingLaunch(errorHandlerPaging) {
            delay(WALK_RESUME_PAUSE)
            // A first page published during the pause starts a walk of its own, and this round is
            // part of the one it replaced — including the counter it would otherwise reset.
            if (generation != walkGeneration.get()) return@pagingLaunch
            emptyPageChain = 0
            publishWalkStep(page = resumeFrom, generation = generation)
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
