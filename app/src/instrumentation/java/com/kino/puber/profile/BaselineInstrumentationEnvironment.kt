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

    /** The HTTP/2 mock server: DNS-over-HTTPS and the update check. */
    fun mockH2Origin(): String = "https://$MOCK_HOST:${BuildConfig.BASELINE_MOCK_H2_PORT}"

    /** The HTTPS loopback server of the player device tests (`:player-test-fixtures`). */
    fun playerFixtureOrigin(): String = "https://$MOCK_HOST:${BuildConfig.PLAYER_FIXTURE_PORT}"

    fun journalDirectory(context: Context): File = File(context.noBackupFilesDir, JOURNAL_DIRECTORY)

    /**
     * The five origins the app may reach: the HTTP/1.1 server (the mock by IP and the API test host),
     * the HTTP/2 server (the mock by IP and the DNS-over-HTTPS test host) and the player fixture server.
     */
    fun allowedOrigins(): Set<String> = setOf(
        mockOrigin(),
        "https://$API_HOST:${BuildConfig.BASELINE_MOCK_PORT}",
        mockH2Origin(),
        "https://$DNS_HOST:${BuildConfig.BASELINE_MOCK_H2_PORT}",
        playerFixtureOrigin(),
    )

    fun configure(context: Context) = configure(journalDirectory(context))

    /**
     * Pins the API to the `.test` host on the HTTP/1.1 port (resolved over DNS-over-HTTPS served by the
     * mock on the HTTP/2 port, bootstrapped from 127.0.0.1) and installs the blocker. Every name resolves to the loopback mock.
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
            DohResolver("https://$DNS_HOST:${BuildConfig.BASELINE_MOCK_H2_PORT}/dns-query", listOf(MOCK_HOST)),
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
