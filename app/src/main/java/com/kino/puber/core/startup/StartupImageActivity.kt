package com.kino.puber.core.startup

import coil3.EventListener
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Size

/**
 * Tells the [gate] which image requests of the loader are in flight, from the moment a request's
 * size is resolved until it succeeds, fails or is cancelled. Requests that never get a size are
 * ignored, so a view that is never measured cannot hold the startup splash.
 */
internal class StartupImageActivity(
    private val gate: RevealGate,
) : EventListener() {
    override fun resolveSizeEnd(request: ImageRequest, size: Size) = gate.onImageStarted(request)

    override fun onSuccess(request: ImageRequest, result: SuccessResult) = gate.onImageFinished(request)

    override fun onError(request: ImageRequest, result: ErrorResult) = gate.onImageFinished(request)

    override fun onCancel(request: ImageRequest) = gate.onImageFinished(request)
}
