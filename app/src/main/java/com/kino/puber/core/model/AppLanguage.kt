package com.kino.puber.core.model

/**
 * The language the interface is drawn in.
 *
 * [System] is what the app did before the setting existed — whatever the device is set to. The
 * other two override it, which is the point on a TV box whose system language a user may not want
 * to change for one app.
 */
enum class AppLanguage(val tag: String?) {
    System(null),
    Russian("ru"),
    English("en"),
    ;

    companion object {

        /** Reads back a stored choice, falling back to [System] for anything unrecognised. */
        fun fromName(name: String?): AppLanguage = entries.find { it.name == name } ?: System
    }
}
