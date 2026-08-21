package com.kino.puber.domain.interactor.diagnostics

import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.EndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.api.network.diagnostics.BoundedDownloader
import com.kino.puber.data.api.network.diagnostics.DiagnosticsApi
import com.kino.puber.data.api.network.diagnostics.HostResolver
import com.kino.puber.data.api.network.diagnostics.ThroughputSample
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

internal class NetworkDiagnosticsInteractorTest {

    private var reachableDomains = setOf("service-kp.test")
    private var resolvedAddresses = 2
    private var cataloguePageArrives = true
    private var progressiveUrl: String? = "https://cdn.test/a.mp4"
    private val downloadedUrls = mutableListOf<String>()
    private val now = 1_000L

    private val reachability = EndpointReachability(clock = { now })

    private val interactor = NetworkDiagnosticsInteractor(
        probe = EndpointProbe { endpoint -> endpoint.domain in reachableDomains },
        resolver = HostResolver { resolvedAddresses },
        api = object : DiagnosticsApi {
            override suspend fun loadCataloguePage(): Boolean = cataloguePageArrives
            override suspend fun findProgressiveMediaUrl(): String? = progressiveUrl
        },
        downloader = BoundedDownloader { url, maxBytes ->
            downloadedUrls += url
            ThroughputSample(bytes = maxBytes, elapsedMillis = 1_000)
        },
        reachability = reachability,
        clock = { now },
    )

    @BeforeEach
    fun setUp() {
        mockkObject(KinoPubConfig)
        every { KinoPubConfig.CURRENT_API_DOMAIN } returns "service-kp.test"
        every { KinoPubConfig.CURRENT_API_HOST } returns "api.service-kp.test"
        every { KinoPubConfig.CURRENT_ENDPOINT } returns endpointFor("service-kp.test")
        every { KinoPubConfig.BUILT_IN_ENDPOINTS } returns listOf(
            endpointFor("service-kp.test"),
            endpointFor("api.alador.test"),
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(KinoPubConfig)
    }

    @Test
    fun run_settlesEveryStep_whenEverythingWorks() = runTest {
        val last = interactor.run().toList().last()

        assertTrue(last.finished)
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.ApiReachability))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.NameResolution))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.ApiResponsiveness))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.MediaThroughput))
    }

    /** The sweep has no question to ask while the mirror in use is answering. */
    @Test
    fun run_skipsTheMirrorSweep_whenTheCurrentMirrorAnswers() = runTest {
        val last = interactor.run().toList().last()

        assertEquals(
            StepState.Skipped(SkipReason.CurrentMirrorAnswers),
            last.state(DiagnosticStep.MirrorSweep),
        )
    }

    @Test
    fun run_findsAWorkingMirror_whenTheCurrentOneIsDown() = runTest {
        reachableDomains = setOf("api.alador.test")

        val last = interactor.run().toList().last()

        assertEquals("api.alador.test", last.workingMirrorDomain)
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.MirrorSweep))
    }

    @Test
    fun run_skipsTheMediaStep_whenNoProgressiveUrlIsOnOffer() = runTest {
        progressiveUrl = null

        val last = interactor.run().toList().last()

        assertEquals(
            StepState.Skipped(SkipReason.NoMediaLink),
            last.state(DiagnosticStep.MediaThroughput),
        )
        assertTrue(downloadedUrls.isEmpty())
    }

    /** A failing step is news about that step, not a reason to stop asking the other questions. */
    @Test
    fun run_keepsGoing_whenOneStepFails() = runTest {
        cataloguePageArrives = false

        val last = interactor.run().toList().last()

        assertInstanceOf(StepState.Failure::class.java, last.state(DiagnosticStep.ApiResponsiveness))
        assertInstanceOf(StepState.Success::class.java, last.state(DiagnosticStep.MediaThroughput))
    }

    @Test
    fun run_reportsAResolutionFailure_whenNoAddressComesBack() = runTest {
        resolvedAddresses = 0

        val last = interactor.run().toList().last()

        assertEquals(
            StepState.Failure(FailureReason.ResolutionFailed),
            last.state(DiagnosticStep.NameResolution),
        )
    }

    /**
     * The run refreshes the verdict the rest of the app reads — that is the point of reusing the
     * probe rather than writing a second one.
     */
    @Test
    fun run_marksTheDomainReachable_whenTheProbeAnswers() = runTest {
        interactor.run().toList()

        assertTrue(reachability.answeredWithin("service-kp.test", 15.minutes))
    }

    /** A failure is the client's news to report, not a diagnostic's; a bad run must retire nothing. */
    @Test
    fun run_leavesTheVerdictAlone_whenTheProbeFails() = runTest {
        reachability.markReachable("service-kp.test")
        reachableDomains = emptySet()

        interactor.run().toList()

        assertTrue(reachability.answeredWithin("service-kp.test", 15.minutes))
    }

    @Test
    fun run_emitsRunningBeforeSettling_forEveryStep() = runTest {
        val emissions = interactor.run().toList()

        assertTrue(
            emissions.any { it.state(DiagnosticStep.MediaThroughput) == StepState.Running },
            "the media step must be visible while it is running",
        )
    }

    /**
     * Cancelling is the whole cancellation story: the flow is cold, so abandoning collection stops
     * it where it stands. Nothing downstream of the abandoned point may run — and because the run
     * writes nothing, stopping there leaves nothing behind to undo.
     */
    @Test
    fun run_stopsWhereItStands_whenCollectionIsAbandoned() = runTest {
        val partial = interactor.run().take(2).toList()

        assertEquals(2, partial.size)
        assertFalse(partial.any { it.finished })
        assertTrue(downloadedUrls.isEmpty())
    }

    /**
     * The sweep asks an [EndpointProbe] the run does not control, and that interface gives no
     * no-throw guarantee. The other four steps already survive a throw; the sweep must too, or one
     * misbehaving probe call ends a run that every other failure mode leaves standing.
     */
    @Test
    fun run_survivesAThrowingProbe_duringTheMirrorSweep() = runTest {
        reachableDomains = emptySet()
        val interactorWithThrowingProbe = NetworkDiagnosticsInteractor(
            probe = EndpointProbe { throw IllegalStateException("probe blew up") },
            resolver = HostResolver { resolvedAddresses },
            api = object : DiagnosticsApi {
                override suspend fun loadCataloguePage(): Boolean = cataloguePageArrives
                override suspend fun findProgressiveMediaUrl(): String? = progressiveUrl
            },
            downloader = BoundedDownloader { url, maxBytes ->
                downloadedUrls += url
                ThroughputSample(bytes = maxBytes, elapsedMillis = 1_000)
            },
            reachability = reachability,
            clock = { now },
        )

        val last = interactorWithThrowingProbe.run().toList().last()

        assertTrue(last.finished)
        assertEquals(
            StepState.Failure(FailureReason.RequestFailed),
            last.state(DiagnosticStep.MirrorSweep),
        )
    }

    private fun endpointFor(domain: String) = ApiEndpointPreset(
        domain = domain,
        apiHost = domain,
        mainBaseUrl = "https://$domain/v1/",
        oauthBaseUrl = "https://$domain/oauth2/",
        extraBaseUrl = "https://$domain/",
    )
}
