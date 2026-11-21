package com.kino.puber.core.startup

import coil3.EventListener
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Reports whether any image request of the loader is in flight, from the moment its size is
 * resolved until it succeeds, fails or is cancelled. Requests that never get a size are ignored,
 * so a view that is never measured cannot hold the startup splash.
 */
internal class StartupImageActivity(
    private val onBusyChanged: (Boolean) -> Unit,
) : EventListener() {
    private val lock = Any()
    private val inFlight: MutableSet<ImageRequest> =
        Collections.newSetFromMap(IdentityHashMap())

    override fun resolveSizeEnd(request: ImageRequest, size: Size) = change { add(request) }

    override fun onSuccess(request: ImageRequest, result: SuccessResult) = change { remove(request) }

    override fun onError(request: ImageRequest, result: ErrorResult) = change { remove(request) }

    override fun onCancel(request: ImageRequest) = change { remove(request) }

    /** Forgets every request without reporting; for tests. */
    fun reset() = synchronized(lock) { inFlight.clear() }

    // Reports inside the lock so two threads cannot deliver the edges out of order.
    private inline fun change(edit: MutableSet<ImageRequest>.() -> Unit) = synchronized(lock) {
        val wasBusy = inFlight.isNotEmpty()
        inFlight.edit()
        val busy = inFlight.isNotEmpty()
        if (busy != wasBusy) onBusyChanged(busy)
    }
}
