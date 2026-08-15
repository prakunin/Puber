package com.kino.puber.data.api.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.minutes

class EndpointReachabilityTest {

    private val window = 15.minutes
    private var now = 1_000_000L
    private val reachability = EndpointReachability(clock = { now })

    @Test
    fun aDomainThatJustAnsweredIsTakenOnTrust() {
        reachability.markReachable("service-kp.test")

        assertTrue(reachability.answeredWithin("service-kp.test", window))
    }

    /** One domain answering says nothing about another. */
    @Test
    fun theVerdictDoesNotStandInForADifferentDomain() {
        reachability.markReachable("service-kp.test")

        assertFalse(reachability.answeredWithin("api.alador.test", window))
    }

    /**
     * The window is a tolerance for staleness, not a promise the domain is still up — so it has to
     * lapse on its own, or a domain blocked since the last probe would never be re-checked.
     */
    @Test
    fun theVerdictLapsesWithTheWindow() {
        reachability.markReachable("service-kp.test")

        now += (window + 1.minutes).inWholeMilliseconds

        assertFalse(reachability.answeredWithin("service-kp.test", window))
    }

    /**
     * The whole point of the second reporter: a mirror that dies just after a probe has to lose its
     * standing at once rather than keeping it for the rest of the window.
     */
    @Test
    fun aReportOfFailureRetiresTheVerdictAtOnce() {
        reachability.markReachable("service-kp.test")

        reachability.markUnreachable("service-kp.test")

        assertFalse(reachability.answeredWithin("service-kp.test", window))
    }

    /**
     * A failure can take a connect timeout to surface, by which point the app may have switched
     * elsewhere. News about the domain it left must not take down the verdict the new one earned.
     */
    @Test
    fun aReportAboutAnotherDomainLeavesTheVerdictStanding() {
        reachability.markReachable("api.alador.test")

        reachability.markUnreachable("service-kp.test")

        assertTrue(reachability.answeredWithin("api.alador.test", window))
    }

    /**
     * The same rule under the interleaving that actually threatens it. Probes and failures both run
     * on the request pool, so a failure that took a connect timeout to surface reports while a probe
     * is installing a verdict for the mirror the app has just moved to. Retiring by comparing a copy
     * read moments earlier passes that check and clears a verdict it never saw — losing the only
     * evidence that the new domain works, on the word of a request to the old one.
     *
     * Run repeatedly because the losing interleaving is a narrow window rather than a certainty. The
     * assertion never depends on the order: whichever way the two land, the domain the probe cleared
     * is the one left standing.
     */
    @Test
    fun aLateFailureOfTheOldDomainDoesNotEraseTheNewOnesVerdict() {
        repeat(CONTENDED_ATTEMPTS) {
            val contended = EndpointReachability(clock = { now })
            contended.markReachable("service-kp.test")
            val bothReady = CountDownLatch(1)

            val retireOldDomain = thread {
                bothReady.await()
                contended.markUnreachable("service-kp.test")
            }
            val probeNewDomain = thread {
                bothReady.await()
                contended.markReachable("api.alador.test")
            }
            bothReady.countDown()
            retireOldDomain.join()
            probeNewDomain.join()

            assertTrue(contended.answeredWithin("api.alador.test", window))
        }
    }

    private companion object {
        /** Enough passes to hit the interleaving, few enough to stay a fast unit test. */
        const val CONTENDED_ATTEMPTS = 2_000
    }
}
