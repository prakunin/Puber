package com.kino.puber.data.api.network

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.ktor.client.plugins.api.createClientPlugin
import java.io.IOException

fun createConnectivityPlugin(connectivityManager: ConnectivityManager) =
    createClientPlugin("ConnectivityPlugin") {
        onRequest { _, _ ->
            if (!connectivityManager.isNetworkAvailable()) {
                throw NoConnectivityException()
            }
        }
    }

class NoConnectivityException : IOException("No network connectivity")

/**
 * Whether this failure means the request never reached the host it was addressed to.
 *
 * [IOException] is the test, because the transport giving out — DNS, connect, socket and request
 * timeouts — all arrive as one, while everything that proves the server *did* answer stays out: a
 * non-2xx response carries a body the client then fails to parse, which surfaces as a serialization
 * error. That split keeps a server which is up but unhappy from being read as a dead one.
 *
 * [NoConnectivityException] is the exception to it. The plugin raises it before the request leaves
 * the device, so it is evidence about the device and says nothing whatever about the host. Counted
 * as unreachability it would be actively harmful: with the TV offline every request would retire
 * the domain's verdict, and each load would then walk the whole mirror list — probes that cannot
 * succeed either — and report "no working domain" in place of the plain network error.
 */
fun Throwable.meansHostUnreachable(): Boolean = this is IOException && this !is NoConnectivityException

private fun ConnectivityManager.isNetworkAvailable(): Boolean {
    val network = activeNetwork ?: return false
    val capabilities = getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
