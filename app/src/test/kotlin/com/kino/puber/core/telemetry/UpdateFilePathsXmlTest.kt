package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

internal class UpdateFilePathsXmlTest {
    private fun source(relative: String): File =
        listOf(File("src/main/$relative"), File("app/src/main/$relative")).first { it.isFile }

    @Test
    fun pathsCoverTelemetryDirOnlyAndKeepUpdates() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(source("res/xml/update_file_paths.xml"))
        val children = document.documentElement.childNodes
        val entries = (0 until children.length)
            .map { children.item(it) }
            .filter { it.nodeType == Node.ELEMENT_NODE }
            .map { (it as Element).tagName to it.getAttribute("path") }

        // Exactly these two; no root-path, no external-path.
        assertEquals(
            listOf("external-files-path" to "Download/updates/", "external-files-path" to "telemetry/"),
            entries,
        )
    }
}
