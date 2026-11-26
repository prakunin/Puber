package com.kino.puber.profile

import android.content.Context
import com.kino.puber.BuildConfig
import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.DohResolver
import java.io.File

internal object BaselineInstrumentationEnvironment {

    const val MOCK_HOST = "127.0.0.1"
    const val API_HOST = "api.baseline.test"
    const val DNS_HOST = "dns.baseline.test"
    private const val JOURNAL_DIRECTORY = "baseline_network_journal"

    fun mockOrigin(): String = "https://$MOCK_HOST:${BuildConfig.BASELINE_MOCK_PORT}"

    fun journalDirectory(context: Context): File = File(context.noBackupFilesDir, JOURNAL_DIRECTORY)

    /** The three origins the app may reach: the mock by IP, and its API and DNS-over-HTTPS test hosts. */
    fun allowedOrigins(): Set<String> = setOf(
        mockOrigin(),
        "https://$API_HOST:${BuildConfig.BASELINE_MOCK_PORT}",
        "https://$DNS_HOST:${BuildConfig.BASELINE_MOCK_PORT}",
    )

    fun configure(context: Context) = configure(journalDirectory(context))

    /**
     * Pins the API to the `.test` host (resolved over DNS-over-HTTPS served by the mock, bootstrapped
     * from 127.0.0.1) and installs the blocker. Every name resolves to the loopback mock.
     */
    internal fun configure(journalDir: File) {
        val port = BuildConfig.BASELINE_MOCK_PORT
        val apiOrigin = "https://$API_HOST:$port"
        KinoPubConfig.setPinnedEndpoint(
            ApiEndpointPreset(
                domain = "$API_HOST:$port",
                apiHost = API_HOST,
                mainBaseUrl = "$apiOrigin/v1/",
                oauthBaseUrl = "$apiOrigin/oauth2/",
                extraBaseUrl = "$apiOrigin/",
            ),
            DohResolver("https://$DNS_HOST:$port/dns-query", listOf(MOCK_HOST)),
        )
        BaselineNetworkBlocker.install(journalDir, allowedOrigins())
    }

    /**
     * Drops everything a previous run could have cached from the mock: every database and the
     * internal and external cache directories, so each journey requests what it shows again.
     * The network journal is left alone.
     */
    fun clearCaches(context: Context) {
        context.databaseList().forEach { name ->
            check(context.deleteDatabase(name) || !context.getDatabasePath(name).exists()) {
                "Failed to delete instrumentation database: $name"
            }
        }
        listOfNotNull(context.cacheDir, context.externalCacheDir).forEach { directory ->
            directory.listFiles().orEmpty().forEach { entry ->
                check(entry.deleteRecursively()) {
                    "Failed to clear instrumentation cache: $entry"
                }
            }
        }
    }
}
