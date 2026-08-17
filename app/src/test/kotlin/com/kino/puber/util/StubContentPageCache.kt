package com.kino.puber.util

import com.kino.puber.data.cache.ContentPageCache
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow

/**
 * A payload cache that never has a history page to hand over and never revalidates one, for tests
 * that are about the depth walk rather than about what is stored.
 *
 * `emptyFlow()` is a deviation, not a simulation: the real `CachedFeed` always emits a value, emits
 * `RefreshFailed`, or throws — it never simply completes. It is still the right stub for the walk
 * fixtures, several of which switch behaviour on the call count of `getHistoryData(1)`, and a cache
 * running its own page-one loader would shift every one of those counts for reasons that have
 * nothing to do with what they assert. The shapes this hides are covered where they belong, on the
 * cache path itself, by `HistoryVMStoredFirstPageTest` — including the failing read, which is the
 * one this stub would otherwise let past.
 */
internal fun stubContentPageCache(): ContentPageCache = mockk {
    every { historyFirstPage(any(), any()) } returns emptyFlow()
}
