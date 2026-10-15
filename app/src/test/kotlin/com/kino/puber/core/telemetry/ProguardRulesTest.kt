package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Text only: a fast tripwire. The proof of the names is a minified build on a device. */
internal class ProguardRulesTest {
    private val rules = listOf(File("proguard-rules.pro"), File("app/proguard-rules.pro"))
        .first { it.isFile }
        .readLines()
        .map { it.trim() }

    @Test
    fun keepNamesRulesPresentAndLogStripRuleIsNarrowed() {
        listOf(
            "-keepnames class * extends com.kino.puber.core.ui.PuberVM",
            "-keepnames class * implements com.kino.puber.core.ui.uikit.model.UIAction",
            "-keepnames class com.kino.puber.ui.**State",
            "-keepnames class com.kino.puber.ui.**State$*",
            "-keepnames class * implements cafe.adriel.voyager.navigator.tab.Tab",
            "public static void log(java.lang.Object, java.lang.String, java.lang.String);",
        ).forEach { assertTrue(it in rules, "missing rule: $it") }
        assertFalse("public static void log(...);" in rules)
    }
}
