package com.kino.puber.profile

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.w3c.dom.Element

class BaselineNetworkSecurityConfigTest {

    private val config: File = listOf(
        File("src/instrumentation/res/xml/network_security_config.xml"),
        File("app/src/instrumentation/res/xml/network_security_config.xml"),
    ).first { it.isFile }

    @Test
    fun baseConfig_trustsOnlyTheMockRootAndRefusesCleartext() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(config)

        val baseConfigs = document.getElementsByTagName("base-config")
        val certificates = document.getElementsByTagName("certificates")

        assertEquals(1, baseConfigs.length)
        assertEquals("false", (baseConfigs.item(0) as Element).getAttribute("cleartextTrafficPermitted"))
        assertEquals(1, certificates.length)
        assertEquals("@raw/baseline_mock_ca", (certificates.item(0) as Element).getAttribute("src"))
        assertEquals(0, document.getElementsByTagName("domain-config").length)
    }
}
