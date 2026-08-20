package com.kino.puber.ui.feature.player.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PlayerInfoPanelTest {

    @Test
    fun cleanInfoParts_joinsOnlyNonBlankValues() {
        assertEquals("AAC · 5.1", cleanInfoParts(" AAC ", "", " 5.1 "))
    }

    @Test
    fun cleanInfoParts_returnsNullWhenEveryValueIsBlank() {
        assertNull(cleanInfoParts(null, "", "   "))
    }
}
