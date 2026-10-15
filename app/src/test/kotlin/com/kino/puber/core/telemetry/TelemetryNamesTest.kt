package com.kino.puber.core.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

internal class TelemetryNamesTest {
    private val names = TelemetryNames()

    @Test
    fun suffixRuleTabUnwrapAndCache() {
        assertEquals("Home", names.owner(HomeScreen::class.java))
        assertEquals("Home", names.owner(HomeVM::class.java))
        assertEquals("Screen", names.owner(ScreenScreen::class.java))
        assertEquals("Content", names.plain(SampleState.Content::class.java))
        assertEquals("", names.owner(null))

        val tab = tabOf(HomeScreen())
        assertEquals("Home", names.owner(telemetryClassOf(tab)))
        assertSame(HomeScreen::class.java, telemetryClassOf(HomeScreen()))
        assertEquals(null, telemetryClassOf(null))

        assertSame(names.owner(HomeScreen::class.java), names.owner(HomeScreen::class.java))
        assertSame(names.plain(SampleState.Content::class.java), names.plain(SampleState.Content::class.java))
    }

    @Test
    fun simpleNameIsDerivedFromTheBinaryName() {
        assertEquals("ItemSelected", TelemetryNames.simpleNameOf("a.b.CommonAction\$ItemSelected"))
        assertEquals("Resumed", TelemetryNames.simpleNameOf("com.x.MainAction\$Resumed"))
        assertEquals("LoadingTab", TelemetryNames.simpleNameOf("com.x.LoadingTab"))
        assertEquals("pm2", TelemetryNames.simpleNameOf("pm2"))
    }

    @Test
    fun plainNameIsTheBinaryNameTailEvenWhereSimpleNameDiffers() {
        val anonymous = object : Any() {}.javaClass
        // `simpleName` of an anonymous class is empty; the log must not depend on that attribute.
        assertEquals("", anonymous.simpleName)
        assertEquals(anonymous.name.substringAfterLast('$'), names.plain(anonymous))
        assertFalse(names.plain(anonymous).isEmpty())
    }

    @Test
    fun parameterizedScreenNameNeverContainsKeyOrIds() {
        val screen = ParameterizedScreen("secret-id-4242")
        val name = names.owner(telemetryClassOf(screen))
        assertEquals("Parameterized", name)
        assertFalse(name.contains("4242"))
    }
}
