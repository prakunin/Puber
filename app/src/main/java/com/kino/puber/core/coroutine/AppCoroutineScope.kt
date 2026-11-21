package com.kino.puber.core.coroutine

import com.kino.puber.core.logger.log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlin.coroutines.CoroutineContext

/**
 * Work that belongs to the process, not to a screen: it outlives every ViewModel and a failed child
 * does not cancel its siblings.
 */
internal class AppCoroutineScope(
    override val coroutineContext: CoroutineContext = SupervisorJob() + Dispatchers.Default +
        CoroutineExceptionHandler { _, e -> e.log(e, "Application scope task failed") },
) : CoroutineScope
