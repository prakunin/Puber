package com.kino.puber.domain.model

/** How much the player is asked to buffer ahead. Stored per device, so the data layer owns it. */
enum class BufferPreset {
    AUTO,
    SMALL,
    MEDIUM,
    LARGE,
    MAX,
}
