package com.kino.puber.core.system

import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class ConnectionTransport {
    Ethernet,
    Wifi,
    Cellular,
    Other,
    None,
}

class ConnectionTransportProvider(
    private val connectivityManager: ConnectivityManager,
) {
    fun current(): ConnectionTransport {
        val network = connectivityManager.activeNetwork ?: return ConnectionTransport.None
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectionTransport.None

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                ConnectionTransport.Ethernet

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                ConnectionTransport.Wifi

            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                ConnectionTransport.Cellular

            else -> ConnectionTransport.Other
        }
    }
}
