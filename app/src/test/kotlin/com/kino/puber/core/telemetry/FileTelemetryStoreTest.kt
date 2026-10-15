package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

internal class FileTelemetryStoreTest {
    @TempDir
    lateinit var root: File

    private fun store() = FileTelemetryStore { root }

    @Test
    fun appendFlushCloseRoundtrip() {
        val store = store()
        store.append("b.jsonl", "one\n")
        store.append("b.jsonl", "two\n")
        store.flush()
        assertEquals("one\ntwo\n", File(root, "telemetry/b.jsonl").readText())
        store.closeCurrent()
        store.append("b.jsonl", "three\n")
        store.closeCurrent()
        assertEquals("one\ntwo\nthree\n", File(root, "telemetry/b.jsonl").readText())
    }

    @Test
    fun listIsSortedAndIncludesLogsArchivesAndOrphanedTmpOnly() {
        val store = store()
        store.append("b.jsonl", "x\n")
        store.append("a.jsonl", "yy\n")
        store.closeCurrent()
        File(root, "telemetry/c.zip").writeText("z")
        File(root, "telemetry/d.tmp").writeText("t")
        File(root, "telemetry/e.txt").writeText("e")
        assertEquals(listOf("a.jsonl", "b.jsonl", "c.zip", "d.tmp"), store.list().map { it.name })
        assertEquals(3L, store.list().first().sizeBytes)
    }

    @Test
    fun availabilityAndListOnMissingDirectoryCreateNothing() {
        val store = store()
        assertTrue(store.isAvailable())
        assertTrue(store.list().isEmpty())
        assertFalse(File(root, "telemetry").exists())
        assertFalse(FileTelemetryStore { null }.isAvailable())
    }

    @Test
    fun zipContainsSourcesAndLeavesNoTmp() {
        val store = store()
        store.append("a.jsonl", "x\n")
        store.closeCurrent()
        val archive = store.zip(listOf("a.jsonl"), "out.zip")
        ZipFile(archive).use { assertEquals(listOf("a.jsonl"), it.entries().toList().map { e -> e.name }) }
        assertFalse(File(root, "telemetry/out.zip.tmp").exists())
    }

    @Test
    fun zipFailureLeavesNoZipAndNoTmp() {
        val store = store()
        store.append("a.jsonl", "x\n")
        store.closeCurrent()
        assertThrows(IOException::class.java) { store.zip(listOf("missing.jsonl"), "out.zip") }
        assertFalse(File(root, "telemetry/out.zip").exists())
        assertFalse(File(root, "telemetry/out.zip.tmp").exists())
    }

    @Test
    fun deleteRemovesTheOpenFileToo() {
        val store = store()
        store.append("a.jsonl", "x\n")
        store.delete("a.jsonl")
        assertTrue(store.list().isEmpty())
    }
}
