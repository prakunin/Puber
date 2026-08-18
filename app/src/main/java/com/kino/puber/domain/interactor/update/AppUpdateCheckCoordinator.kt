package com.kino.puber.domain.interactor.update

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

internal class AppUpdateCheckCoordinator {

    private val manualCheckChannel = Channel<Unit>(capacity = Channel.CONFLATED)

    val manualCheckRequests: Flow<Unit> = manualCheckChannel.receiveAsFlow()

    fun requestManualCheck() {
        manualCheckChannel.trySend(Unit)
    }
}
