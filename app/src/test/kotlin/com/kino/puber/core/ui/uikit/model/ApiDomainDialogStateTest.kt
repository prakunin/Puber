package com.kino.puber.core.ui.uikit.model

import com.kino.puber.domain.interactor.api.ApiDomainState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ApiDomainDialogStateTest {

    @Test
    fun toDialogState_mapsDomainAndCustomDomain() {
        val dialog = ApiDomainState(domain = "api.example", customDomain = "custom.example").toDialogState()

        assertEquals(
            ApiDomainDialogState(
                currentDomain = "api.example",
                customDomain = "custom.example",
                isDetecting = false,
            ),
            dialog,
        )
        assertEquals("custom.example", dialog.initialInput)
    }

    @Test
    fun toDialogState_leavesInitialInputEmpty_whenNoCustomDomain() {
        val dialog = ApiDomainState(domain = "api.example", customDomain = null).toDialogState()

        assertNull(dialog.customDomain)
        assertEquals("", dialog.initialInput)
    }

    @Test
    fun toDialogState_isNeverDetecting() {
        assertFalse(ApiDomainState("api.example", "custom.example").toDialogState().isDetecting)
        assertFalse(ApiDomainState("api.example", null).toDialogState().isDetecting)
    }
}
