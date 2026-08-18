package com.kino.puber.core.tvhome

internal interface TvHomePublisher {
    suspend fun reconcile(programs: List<PublishedProgram>)
    suspend fun clearAccountPrograms()
}

internal object NoOpTvHomePublisher : TvHomePublisher {
    override suspend fun reconcile(programs: List<PublishedProgram>) = Unit
    override suspend fun clearAccountPrograms() = Unit
}
