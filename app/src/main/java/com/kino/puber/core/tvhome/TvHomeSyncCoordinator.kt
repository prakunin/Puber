package com.kino.puber.core.tvhome

import com.kino.puber.core.logger.log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.seconds

internal class TvHomeSyncCoordinator(
    private val source: ContinueWatchingSource,
    private val publisher: TvHomePublisher,
    private val scope: CoroutineScope,
) {
    private val reconciliationMutex = Mutex()
    private var requestedRefresh: Job? = null

    fun requestRefresh(immediate: Boolean = false) {
        requestedRefresh?.cancel()
        requestedRefresh = scope.launch {
            if (!immediate) delay(REFRESH_DEBOUNCE)
            refreshNow()
        }
    }

    suspend fun refreshNow() = reconciliationMutex.withLock {
        val programs = source.load().getOrElse { error ->
            log(error, "Failed to load TV home programs")
            return@withLock
        }
        try {
            publisher.reconcile(programs)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log(error, "Failed to publish TV home programs")
        }
    }

    suspend fun clearAccountPrograms() = reconciliationMutex.withLock {
        requestedRefresh?.cancel()
        try {
            publisher.clearAccountPrograms()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log(error, "Failed to clear TV home programs")
        }
    }

    private companion object {
        val REFRESH_DEBOUNCE = 2.seconds
    }
}
