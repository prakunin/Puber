package com.kino.puber.domain.model

/** The chosen subtitle as stored per item. A null value means "not saved / don't care". */
data class SubtitlePreference(
    val language: String?,
    val url: String?,
    val isForced: Boolean?,
    val descriptiveLabel: String?,
) {
    companion object {
        val NONE = SubtitlePreference(null, null, null, null)
    }
}
