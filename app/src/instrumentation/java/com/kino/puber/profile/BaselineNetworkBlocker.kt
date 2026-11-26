package com.kino.puber.profile

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * Refuses every connection except to the allowed origins (the mock by IP and its API and DNS test
 * hosts): any other URI gets a dead proxy and is written to a file journal that survives a process
 * restart.
 */
internal object BaselineNetworkBlocker {

    @Synchronized
    fun install(journalDir: File, allowedOrigins: Set<String>) {
        val journal = BaselineNetworkJournal(journalDir)
        ProxySelector.setDefault(selector(allowedOrigins, journal))
    }

    internal fun selector(allowedOrigins: Set<String>, journal: BaselineNetworkJournal): ProxySelector {
        val allowed = allowedOrigins.mapTo(HashSet()) { it.removeSuffix("/").lowercase() }
        return object : ProxySelector() {
            override fun select(uri: URI): List<Proxy> =
                if (origin(uri) in allowed) {
                    listOf(Proxy.NO_PROXY)
                } else {
                    journal.record(uri)
                    listOf(DENY_PROXY)
                }

            override fun connectFailed(uri: URI, sa: SocketAddress?, ioe: IOException) {
                if (origin(uri) !in allowed) journal.record(uri)
            }
        }
    }

    private const val DENY_PROXY_PORT = 9
    private val DENY_PROXY = Proxy(
        Proxy.Type.HTTP,
        InetSocketAddress.createUnresolved("127.0.0.1", DENY_PROXY_PORT),
    )
}

private const val HTTP_PORT = 80
private const val HTTPS_PORT = 443

private fun defaultPort(scheme: String): Int = when (scheme) {
    "http" -> HTTP_PORT
    "https" -> HTTPS_PORT
    else -> -1
}

private fun origin(uri: URI): String {
    val scheme = uri.scheme.orEmpty().lowercase()
    val host = uri.host.orEmpty().lowercase()
    val port = uri.port.takeIf { it >= 0 } ?: defaultPort(scheme)
    return "$scheme://$host:$port"
}

internal class BaselineNetworkJournal(private val directory: File) {
    private val journalFile = File(directory, JOURNAL_FILE)
    private val lockFile = File(directory, LOCK_FILE)

    init {
        check(directory.isDirectory || directory.mkdirs()) {
            "Failed to create baseline network journal directory"
        }
    }

    fun record(uri: URI) = withFileLock {
        val violation = violation(uri)
        val current = readEntries()
        if (violation !in current) {
            writeEntries((current + violation).takeLast(MAX_ENTRIES))
        }
    }

    fun snapshot(): List<String> = withFileLock(::readEntries)

    fun clear() = withFileLock {
        writeEntries(emptyList())
    }

    private fun readEntries(): List<String> {
        if (!journalFile.isFile) return emptyList()
        return journalFile.readLines(StandardCharsets.UTF_8)
            .filter(String::isNotBlank)
            .takeLast(MAX_ENTRIES)
    }

    private fun writeEntries(entries: List<String>) {
        val payload = entries.joinToString(ENTRY_SEPARATOR).toByteArray(StandardCharsets.UTF_8)
        RandomAccessFile(journalFile, "rw").use { file ->
            file.setLength(0)
            file.write(payload)
            file.fd.sync()
        }
    }

    private fun violation(uri: URI): String {
        val path = uri.rawPath.orEmpty()
            .ifBlank { "/" }
            .replace('\n', '_')
            .replace('\r', '_')
        return "${origin(uri)}$path".take(MAX_ENTRY_LENGTH)
    }

    private inline fun <T> withFileLock(block: () -> T): T =
        synchronized(PROCESS_LOCK) {
            RandomAccessFile(lockFile, "rw").use { lockAccess ->
                lockAccess.channel.lock().use { block() }
            }
        }

    private companion object {
        const val JOURNAL_FILE = "violations"
        const val LOCK_FILE = "journal.lock"
        const val ENTRY_SEPARATOR = "\n"
        const val MAX_ENTRIES = 64
        const val MAX_ENTRY_LENGTH = 512

        val PROCESS_LOCK = Any()
    }
}
