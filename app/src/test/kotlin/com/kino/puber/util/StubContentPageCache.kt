package com.kino.puber.util

import com.kino.puber.data.cache.ContentPageCache
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow

/**
 * A payload cache that never has a history page to hand over and never revalidates one, for tests
 * that are about the depth walk rather than about what is stored.
 */
internal fun stubContentPageCache(): ContentPageCache = mockk {
    every { historyFirstPage(any(), any()) } returns emptyFlow()
}
