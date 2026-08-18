package com.kino.puber.core.contentlink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal class ContentLaunchCoordinator(
    private val uriCodec: ContentUriCodec,
) {
    private val mutableChanges = MutableStateFlow(0L)
    val changes: StateFlow<Long> = mutableChanges.asStateFlow()

    private var pendingTarget: ContentTarget? = null
    private var navigationReady = false

    fun accept(uri: String?): Boolean {
        val target = uriCodec.parse(uri) ?: return false
        synchronized(this) {
            pendingTarget = target
        }
        mutableChanges.update { it + 1 }
        return true
    }

    @Synchronized
    fun consumeForAuthenticatedStart(): ContentTarget? {
        navigationReady = true
        return takePending()
    }

    @Synchronized
    fun waitForAuthentication() {
        navigationReady = false
    }

    @Synchronized
    fun consumeAfterAuthentication(): ContentTarget? {
        navigationReady = true
        return takePending()
    }

    @Synchronized
    fun consumeForWarmRouting(): ContentTarget? {
        if (!navigationReady) return null
        return takePending()
    }

    @Synchronized
    fun clearSession() {
        navigationReady = false
        pendingTarget = null
    }

    private fun takePending(): ContentTarget? = pendingTarget.also { pendingTarget = null }
}
