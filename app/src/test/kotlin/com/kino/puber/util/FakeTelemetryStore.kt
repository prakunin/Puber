package com.kino.puber.util

import com.kino.puber.core.telemetry.StoredFile
import com.kino.puber.core.telemetry.TelemetryStore
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** In-memory store. Records every call with the calling thread's name. */
internal class FakeTelemetryStore(private val zipDir: File? = null) : TelemetryStore {
    val files: MutableMap<String, MutableList<String>> = linkedMapOf()
    val sizes: MutableMap<String, Long> = linkedMapOf()
    val calls: MutableList<Pair<String, String>> = java.util.Collections.synchronizedList(mutableListOf())
    val zips: MutableList<Pair<String, List<String>>> = mutableListOf()

    @Volatile var available = true
    @Volatile var throwOnAppend = false
    @Volatile var throwOnZip = false
    @Volatile var zipFailure: Throwable? = null
    @Volatile var failNextAppends = 0
    @Volatile var onFlush: (() -> Unit)? = null
    @Volatile var blockAppend: CountDownLatch? = null
    @Volatile var currentName: String? = null
        private set

    val closedNames: List<String> get() = files.keys.filter { it != currentName }

    private fun record(call: String) {
        calls += call to Thread.currentThread().name
    }

    fun lines(name: String): List<String> = files[name].orEmpty()

    fun seed(name: String, sizeBytes: Long) {
        files[name] = mutableListOf()
        sizes[name] = sizeBytes
    }

    override fun isAvailable(): Boolean {
        record("isAvailable")
        return available
    }

    override fun list(): List<StoredFile> {
        record("list")
        return files.keys.sorted().map { StoredFile(it, sizes[it] ?: 0L) }
    }

    override fun append(name: String, line: String) {
        record("append")
        blockAppend?.await(BLOCK_TIMEOUT_S, TimeUnit.SECONDS)
        if (throwOnAppend || failNextAppends > 0) {
            if (failNextAppends > 0) failNextAppends--
            throw IOException("append failed")
        }
        currentName = name
        files.getOrPut(name) { mutableListOf() }.add(line)
        sizes[name] = (sizes[name] ?: 0L) + line.toByteArray().size
    }

    override fun flush() {
        record("flush")
        onFlush?.invoke()
    }

    override fun closeCurrent() {
        record("closeCurrent")
        currentName = null
    }

    override fun delete(name: String) {
        record("delete")
        if (name == currentName) currentName = null
        files.remove(name)
        sizes.remove(name)
    }

    override fun zip(sources: List<String>, archiveName: String): File {
        record("zip")
        if (throwOnZip) throw IOException("zip failed")
        zipFailure?.let { throw it }
        zips += archiveName to sources
        files[archiveName] = mutableListOf()
        sizes[archiveName] = ZIP_SIZE
        return File(zipDir ?: File("."), archiveName)
    }

    private companion object {
        const val BLOCK_TIMEOUT_S = 10L
        const val ZIP_SIZE = 100L
    }
}
