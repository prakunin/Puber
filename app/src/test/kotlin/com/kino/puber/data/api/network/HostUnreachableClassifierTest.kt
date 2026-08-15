package com.kino.puber.data.api.network

import kotlinx.serialization.SerializationException
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HostUnreachableClassifierTest {

    @Test
    fun theTransportGivingOutMeansTheHostWasNotReached() {
        assertTrue(UnknownHostException("dns").meansHostUnreachable())
        assertTrue(ConnectException("refused").meansHostUnreachable())
        assertTrue(SocketTimeoutException("timeout").meansHostUnreachable())
    }

    /**
     * A server that answered is a server that is up, however unhappy the answer. Its body is what
     * this client then fails to parse, and reading that as a dead mirror would move the app off a
     * domain that is working.
     */
    @Test
    fun aServerThatAnsweredIsNotUnreachable() {
        assertFalse(SerializationException("bad body").meansHostUnreachable())
        assertFalse(IllegalStateException("HTTP 503").meansHostUnreachable())
    }

    /**
     * The one that matters most. This is raised before the request leaves the device, so it is news
     * about the TV's own connection and none at all about the host. Counted as unreachability, every
     * request made while offline would retire the current domain's verdict — and each load would
     * then walk the whole mirror list on probes that cannot succeed either, reporting "no working
     * domain" where the honest answer is that there is no network.
     */
    @Test
    fun havingNoNetworkAtAllSaysNothingAboutTheHost() {
        assertFalse(NoConnectivityException().meansHostUnreachable())
    }
}
