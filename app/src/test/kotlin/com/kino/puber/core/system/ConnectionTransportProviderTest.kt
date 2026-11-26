package com.kino.puber.core.system

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ConnectionTransportProviderTest {

    private val connectivityManager = mockk<ConnectivityManager>()
    private val network = mockk<Network>()
    private val capabilities = mockk<NetworkCapabilities>()

    @Test
    fun transport_isEthernet_whenEthernetAndWifiBothPresent() {
        givenCapabilities(ethernet = true, wifi = true, cellular = false)

        assertEquals(ConnectionTransport.Ethernet, provider().current())
    }

    @Test
    fun transport_isWifi_whenOnlyWifi() {
        givenCapabilities(ethernet = false, wifi = true, cellular = false)

        assertEquals(ConnectionTransport.Wifi, provider().current())
    }

    @Test
    fun transport_isWifi_whenWifiAndCellularBothPresent() {
        givenCapabilities(ethernet = false, wifi = true, cellular = true)

        assertEquals(ConnectionTransport.Wifi, provider().current())
    }

    @Test
    fun transport_isCellular_whenOnlyCellular() {
        givenCapabilities(ethernet = false, wifi = false, cellular = true)

        assertEquals(ConnectionTransport.Cellular, provider().current())
    }

    @Test
    fun transport_isOther_whenNoMappedTransport() {
        givenCapabilities(ethernet = false, wifi = false, cellular = false)

        assertEquals(ConnectionTransport.Other, provider().current())
    }

    @Test
    fun transport_isNone_whenActiveNetworkNull() {
        every { connectivityManager.activeNetwork } returns null

        assertEquals(ConnectionTransport.None, provider().current())
    }

    @Test
    fun transport_isNone_whenNoCapabilities() {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns null

        assertEquals(ConnectionTransport.None, provider().current())
    }

    private fun provider() = ConnectionTransportProvider(connectivityManager)

    private fun givenCapabilities(
        ethernet: Boolean,
        wifi: Boolean,
        cellular: Boolean,
    ) {
        every { connectivityManager.activeNetwork } returns network
        every { connectivityManager.getNetworkCapabilities(network) } returns capabilities
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } returns ethernet
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } returns wifi
        every { capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } returns cellular
    }
}