package com.kino.puber.core.startup

import kotlinx.coroutines.CoroutineScope

/** A latch that has never been fed: startup is still in progress. */
internal fun unsettledStartup(scope: CoroutineScope): StartupSettled = StartupSettled(scope)

/** A latch that is already settled, as after a restored start. */
internal fun settledStartup(scope: CoroutineScope): StartupSettled =
    StartupSettled(scope).also { it.onActivityCreated(restored = true) }
