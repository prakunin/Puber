package com.kino.puber.domain.di

import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * The prefetch surface resolves the prefetcher from the screen's Koin scope, which only works
 * because a scope falls back to the root for definitions it does not own itself. Get that wrong and
 * every eligible screen throws on composition, so it is pinned here rather than on a device.
 */
class DetailsPrefetcherWiringTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun aScreenScopeResolvesTheGlobalPrefetcher() {
        val koin = startKoin {
            modules(
                interactorModule,
                module { single { mockk<ItemDetailsRepository>(relaxed = true) } },
            )
        }.koin
        val screenScope = koin.createScope("screen-scope-id", named("HomeScreen"))

        val resolved = screenScope.get<DetailsPrefetcher>()

        // One prefetcher for the whole app: focus survives moves between tabs, and so must the
        // recently-warmed set that keeps a return to a card from re-fetching it.
        assertSame(koin.get<DetailsPrefetcher>(), resolved)
    }
}
