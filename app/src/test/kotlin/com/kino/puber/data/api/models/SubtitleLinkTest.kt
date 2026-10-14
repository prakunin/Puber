package com.kino.puber.data.api.models

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SubtitleLinkTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    @Test
    fun forced_isNullWhenAbsent() {
        assertNull(json.decodeFromString<SubtitleLink>("""{"lang":"rus","url":"u"}""").forced)
    }

    @Test
    fun forced_readsTrue() {
        assertEquals(true, json.decodeFromString<SubtitleLink>("""{"lang":"rus","url":"u","forced":true}""").forced)
    }

    @Test
    fun forced_readsFalse() {
        assertEquals(false, json.decodeFromString<SubtitleLink>("""{"lang":"rus","url":"u","forced":false}""").forced)
    }

    @Test
    fun forced_nullWithUnknownKey_decodesWithoutError() {
        val link = json.decodeFromString<SubtitleLink>("""{"lang":"rus","url":"u","forced":null,"unknown":1}""")

        assertNull(link.forced)
    }
}
