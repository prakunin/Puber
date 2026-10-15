package com.kino.puber.core.telemetry

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal data class StoredFile(val name: String, val sizeBytes: Long)

/** The directory of log files. IO errors propagate as [IOException]; the writer handles them. */
internal interface TelemetryStore {
    /** False when the external directory is null. Never creates the directory. */
    fun isAvailable(): Boolean

    /** `*.jsonl`, `*.zip` and orphaned `*.tmp`, sorted by name; empty when the directory is absent. */
    fun list(): List<StoredFile>

    /** UTF-8. Creates the directory and the file on demand and keeps one buffered writer. */
    fun append(name: String, line: String)

    fun flush()

    /** Closes the buffered writer; the next [append] reopens. */
    fun closeCurrent()

    fun delete(name: String)

    /** Writes `$archiveName.tmp`, then renames it. Throws [IOException]. */
    fun zip(sources: List<String>, archiveName: String): File
}

internal class FileTelemetryStore(private val baseDir: () -> File?) : TelemetryStore {
    private var currentName: String? = null
    private var current: BufferedWriter? = null

    private fun dir(): File? = baseDir()?.let { File(it, DIR_NAME) }

    override fun isAvailable(): Boolean = baseDir() != null

    override fun list(): List<StoredFile> {
        val files = dir()?.listFiles().orEmpty()
        return files
            .filter { it.isFile && (it.name.endsWith(JSONL) || it.name.endsWith(ZIP) || it.name.endsWith(TMP)) }
            .sortedBy { it.name }
            .map { StoredFile(it.name, it.length()) }
    }

    override fun append(name: String, line: String) {
        val writer = if (name == currentName) current else null
        val target = writer ?: open(name)
        target.write(line)
    }

    private fun open(name: String): BufferedWriter {
        closeCurrent()
        val directory = ensureDir()
        val writer = BufferedWriter(
            OutputStreamWriter(FileOutputStream(File(directory, name), true), Charsets.UTF_8),
        )
        current = writer
        currentName = name
        return writer
    }

    override fun flush() {
        current?.flush()
    }

    override fun closeCurrent() {
        val writer = current
        current = null
        currentName = null
        writer?.close()
    }

    override fun delete(name: String) {
        if (name == currentName) closeCurrent()
        val directory = dir() ?: return
        File(directory, name).delete()
    }

    override fun zip(sources: List<String>, archiveName: String): File {
        val directory = ensureDir()
        val tmp = File(directory, "$archiveName$TMP")
        val target = File(directory, archiveName)
        try {
            ZipOutputStream(FileOutputStream(tmp)).use { out ->
                for (name in sources) {
                    out.putNextEntry(ZipEntry(name))
                    File(directory, name).inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
            }
            if (!tmp.renameTo(target)) throw IOException("Cannot rename archive")
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
        return target
    }

    private fun ensureDir(): File {
        val directory = dir() ?: throw IOException("External files directory is unavailable")
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) {
            throw IOException("Cannot create telemetry directory")
        }
        return directory
    }

    private companion object {
        const val DIR_NAME = "telemetry"
        const val JSONL = ".jsonl"
        const val ZIP = ".zip"
        const val TMP = ".tmp"
    }
}
