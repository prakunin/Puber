package com.kino.puber.data.repository

import android.content.Context
import androidx.core.content.edit

class TmdbIdRepository(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTmdbId(imdbId: String): Int? {
        val key = "$KEY_PREFIX$imdbId"
        return if (prefs.contains(key)) prefs.getInt(key, 0) else null
    }

    fun saveTmdbId(imdbId: String, tmdbId: Int) {
        prefs.edit { putInt("$KEY_PREFIX$imdbId", tmdbId) }
    }

    private companion object {
        const val PREFS_NAME = "tmdb_id_cache"
        const val KEY_PREFIX = "tmdb_"
    }
}
