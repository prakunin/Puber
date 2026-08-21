package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.core.logger.log
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.EndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.api.network.diagnostics.BoundedDownloader
import com.kino.puber.data.api.network.diagnostics.DiagnosticsApi
import com.kino.puber.data.api.network.diagnostics.HostResolver
import com.kino.puber.data.api.network.diagnostics.MediaProbeTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the five measurements in order and reports the whole run after every transition.
 *
 * Writes nothing. Not a preference, not a domain override, not a cache invalidation — which is what
 * makes "a diagnostic failure must not alter normal networking configuration" a property of the
 * shape rather than a thing to remember. Abandoning collection is therefore a complete cancellation
 * story: the flow is cold, and there is no half-finished write for anyone to undo.
 *
 * [Dispatchers.IO] is scoped to the individual blocking calls ([probe] and [resolver] are plain,
 * synchronous interfaces) rather than applied to the flow with `flowOn`. `flowOn` would hand this
 * flow's emissions to the collector through a channel running on a second coroutine, which lets the
 * producer race ahead of a collector that abandons collection — exactly the case the cancellation
 * story above depends on staying impossible.
 */
internal class NetworkDiagnosticsInteractor(
    private val probe: EndpointProbe,
    private val resolver: HostResolver,
    private val api: DiagnosticsApi,
    private val downloader: BoundedDownloader,
    private val reachability: EndpointReachability,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    fun run(): Flow<NetworkDiagnosticsRun> = flow {
        var current = NetworkDiagnosticsRun(apiDomain = KinoPubConfig.CURRENT_API_DOMAIN)
        emit(current)

        current = runStep(current, DiagnosticStep.ApiReachability) { measureApiReachability() }
        val apiReachable = current.state(DiagnosticStep.ApiReachability) is StepState.Success

        current = runStep(current, DiagnosticStep.NameResolution) { resolveApiHost() }
        current = runStep(current, DiagnosticStep.ApiResponsiveness) {
            if (apiReachable) measureApiResponsiveness() else StepState.Skipped(SkipReason.NoNetwork)
        }
        current = runStep(current, DiagnosticStep.MediaThroughput) { measureMediaThroughput() }
        current = sweepMirrors(current, apiReachable)

        current = current.copy(finished = true)
        emit(current)
    }

    private suspend fun FlowCollector<NetworkDiagnosticsRun>.runStep(
        current: NetworkDiagnosticsRun,
        step: DiagnosticStep,
        measure: suspend () -> StepState,
    ): NetworkDiagnosticsRun {
        val running = current.with(step, StepState.Running)
        emit(running)

        val settled = running.with(step, guarded(step, measure))
        emit(settled)
        return settled
    }

    /**
     * Runs [measure] and contains anything it throws to [step] alone.
     *
     * Every step goes through this, including the mirror sweep: none of the collaborators a step
     * calls promise not to throw, and a step that throws must not be able to end a run the other
     * four steps still have answers worth having from.
     */
    private suspend fun guarded(step: DiagnosticStep, measure: suspend () -> StepState): StepState {
        return try {
            measure()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            // One step giving out is news about that step. The others still have answers worth
            // having, and the exception's text is never fit to show a user.
            log(error, "Network diagnostics step $step failed")
            StepState.Failure(FailureReason.RequestFailed)
        }
    }

    private suspend fun FlowCollector<NetworkDiagnosticsRun>.sweepMirrors(
        current: NetworkDiagnosticsRun,
        apiReachable: Boolean,
    ): NetworkDiagnosticsRun {
        if (apiReachable) {
            val skipped = current.with(
                DiagnosticStep.MirrorSweep,
                StepState.Skipped(SkipReason.CurrentMirrorAnswers),
            )
            emit(skipped)
            return skipped
        }

        emit(current.with(DiagnosticStep.MirrorSweep, StepState.Running))

        var working: String? = null
        val state = guarded(DiagnosticStep.MirrorSweep) {
            working = findWorkingMirror()
            if (working == null) StepState.Failure(FailureReason.Unreachable) else StepState.Success()
        }

        val settled = current
            .with(DiagnosticStep.MirrorSweep, state)
            .copy(workingMirrorDomain = working)
        emit(settled)
        return settled
    }

    private suspend fun measureApiReachability(): StepState {
        val endpoint = KinoPubConfig.CURRENT_ENDPOINT
        val startedAt = clock()
        val reachable = withContext(Dispatchers.IO) { probe.isReachable(endpoint) }
        val elapsed = clock() - startedAt

        if (!reachable) return StepState.Failure(FailureReason.Unreachable)

        // Marking reachable, never unreachable: retiring a verdict is the client's job, because it
        // is the only thing that sees every request. A probe that failed once must not take down a
        // domain the app is talking to successfully.
        reachability.markReachable(endpoint.domain)
        return StepState.Success(latencyMillis = elapsed)
    }

    private suspend fun resolveApiHost(): StepState {
        val startedAt = clock()
        val addresses = resolveWithinDeadline(KinoPubConfig.CURRENT_API_HOST)
        val elapsed = clock() - startedAt

        return if (addresses != null && addresses > 0) {
            StepState.Success(latencyMillis = elapsed)
        } else {
            StepState.Failure(FailureReason.ResolutionFailed)
        }
    }

    /**
     * How many addresses came back, or null when [RESOLUTION_TIMEOUT] passed first.
     *
     * [HostResolver.resolve] is a blocking JVM call, so `withTimeout` wrapped round it would give
     * no ceiling at all: the timeout cancels the coroutine, and the enclosing `withContext` then
     * waits for the blocking body to return anyway. What is bounded here is instead the *wait*.
     * The lookup runs on a job of its own, deliberately not a child of this scope, so the deadline
     * can fire while the abandoned lookup finishes on its own thread and has its answer dropped.
     *
     * The alternative — a `callTimeout` on the DNS-over-HTTPS client — would bound this and every
     * other request the app makes, playback included. That is a change with its own testing, not a
     * detail of a diagnostics step.
     */
    private suspend fun resolveWithinDeadline(host: String): Int? {
        val lookup = CoroutineScope(Dispatchers.IO).async { resolver.resolve(host) }
        return try {
            withTimeoutOrNull(RESOLUTION_TIMEOUT) { lookup.await() }
        } finally {
            lookup.cancel()
        }
    }

    private suspend fun measureApiResponsiveness(): StepState {
        val startedAt = clock()
        // The client's own request timeout is two minutes, which is reasonable for a screen loading
        // content and absurd for a step somebody is watching a spinner for.
        val arrived = withTimeoutOrNull(RESPONSIVENESS_TIMEOUT) { api.loadCataloguePage() }
        val elapsed = clock() - startedAt

        return if (arrived == true) {
            StepState.Success(latencyMillis = elapsed)
        } else {
            StepState.Failure(FailureReason.RequestFailed)
        }
    }

    private suspend fun measureMediaThroughput(): StepState {
        // Two sequential authenticated calls, and only the download after them was ever capped.
        // Both are genuinely suspending, so a timeout does bound them here. A lookup that runs out
        // of time is a failure rather than a skip: the catalogue not answering is news about the
        // network, not about what the account is offered.
        val target = withTimeoutOrNull(MEDIA_LOOKUP_TIMEOUT) { api.findMediaProbeTarget() }

        return when (target) {
            null -> StepState.Failure(FailureReason.RequestFailed)
            is MediaProbeTarget.Progressive -> downloadPrefix(target.url)
            // The one skip the user can act on: no progressive URL anywhere in the item's files is
            // what an account set to an HLS streaming type looks like from here.
            MediaProbeTarget.NoProgressiveStream -> StepState.Skipped(SkipReason.NoProgressiveStream)
            MediaProbeTarget.Unavailable -> StepState.Skipped(SkipReason.NoMediaLink)
        }
    }

    private suspend fun downloadPrefix(url: String): StepState {
        val sample = downloader.measure(url, MEDIA_PROBE_MAX_BYTES)

        return if (sample.bytes > 0) {
            StepState.Success(sample = sample)
        } else {
            StepState.Failure(FailureReason.RequestFailed)
        }
    }

    private suspend fun findWorkingMirror(): String? {
        val current = KinoPubConfig.CURRENT_API_DOMAIN
        return withContext(Dispatchers.IO) {
            KinoPubConfig.BUILT_IN_ENDPOINTS
                .filterNot { it.domain == current }
                .firstOrNull(probe::isReachable)
                ?.domain
        }
    }

    private companion object {
        val RESOLUTION_TIMEOUT: Duration = 5.seconds
        val RESPONSIVENESS_TIMEOUT: Duration = 8.seconds

        /** Two sequential catalogue calls, and still short enough to watch a spinner through. */
        val MEDIA_LOOKUP_TIMEOUT: Duration = 12.seconds
        const val MEDIA_PROBE_MAX_BYTES = 4L * 1024L * 1024L
    }
}
