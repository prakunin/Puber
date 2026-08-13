package com.kino.puber.domain.interactor.api

import com.kino.puber.domain.interactor.api.ApiDomainInteractor.Companion.resolveStartupDomain
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ApiDomainStartupTest {

    @Test
    fun `saved domain wins over the build default`() {
        assertEquals(
            "saved.example.com",
            resolveStartupDomain(savedDomain = "saved.example.com", buildDomain = "build.example.com"),
        )
    }

    @Test
    fun `build default applies when nothing is saved`() {
        assertEquals(
            "build.example.com",
            resolveStartupDomain(savedDomain = null, buildDomain = "build.example.com"),
        )
    }

    @Test
    fun `build default applies when the saved domain is invalid`() {
        assertEquals(
            "build.example.com",
            resolveStartupDomain(savedDomain = "not a domain", buildDomain = "build.example.com"),
        )
    }

    @Test
    fun `empty build default leaves the stock domain in place`() {
        assertNull(resolveStartupDomain(savedDomain = null, buildDomain = ""))
    }

    @Test
    fun `invalid build default is ignored`() {
        assertNull(resolveStartupDomain(savedDomain = null, buildDomain = "http://"))
    }

    @Test
    fun `build default is normalized`() {
        assertEquals(
            "build.example.com",
            resolveStartupDomain(savedDomain = null, buildDomain = "https://Build.Example.com/v1/"),
        )
    }
}
