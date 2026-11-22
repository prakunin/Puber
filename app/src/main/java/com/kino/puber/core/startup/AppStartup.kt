package com.kino.puber.core.startup

import com.kino.puber.core.logger.log
import com.kino.puber.data.cache.Cached
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.domain.interactor.api.ApiDomainAutoResolveResult
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.home.HomeInteractor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/**
 * Starts the cold start's background work at process start: applies the saved API domain, then warms
 * the Home feeds so the screen finds them in flight or ready. Interactors are providers so resolving
 * this class on the main thread builds none of them.
 */
internal class AppStartup(
    private val domainInteractor: () -> ApiDomainInteractor,
    private val preferences: ICryptoPreferenceRepository,
    private val homeInteractor: () -> HomeInteractor,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val started = AtomicBoolean(false)
    private val domainInitialized = CompletableDeferred<Unit>()

    /** Idempotent. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch(ioDispatcher) {
            try {
                domainInteractor().initialize()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                log(error, "Failed to apply the saved API domain")
            } finally {
                domainInitialized.complete(Unit)
            }
            warmUp()
        }
    }

    /** Resumes once the saved domain is applied, or applying it failed. */
    suspend fun awaitDomainInitialized() = domainInitialized.await()

    /**
     * Starts the two feeds Home shows first. It keeps no rows: a screen that opens meanwhile joins the
     * request in flight, or reads what it stored, through the shared content cache.
     */
    private suspend fun warmUp() {
        val token = withContext(ioDispatcher) { preferences.getAccessToken() }
        if (token.isNullOrEmpty()) return
        if (domainInteractor().autoResolveWorkingDomain() !is ApiDomainAutoResolveResult.Success) return
        val home = homeInteractor()
        coroutineScope {
            launch { drain(home.observeHotItems()) }
            launch { drain(home.observeWatchingItems()) }
        }
    }

    private suspend fun drain(feed: Flow<Cached<List<*>>>) {
        try {
            feed.collect { }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            log(error, "Home warm-up feed failed")
        }
    }
}
