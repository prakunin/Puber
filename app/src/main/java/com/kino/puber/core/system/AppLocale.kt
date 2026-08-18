package com.kino.puber.core.system

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import com.kino.puber.core.model.AppLanguage
import java.util.Locale

/**
 * Stores the chosen interface language and puts it on a [Context].
 *
 * Sits outside the DI graph on purpose: `attachBaseContext` runs before Koin is started, so the
 * application and the activity have to read the choice straight from preferences. Everything else
 * goes through [com.kino.puber.data.preferences.AppLanguageRepository], which writes here.
 */
object AppLocale {

    const val PREFS_NAME = "language_preferences"
    const val KEY_LANGUAGE = "app_language"

    fun stored(context: Context): AppLanguage {
        val name = preferences(context).getString(KEY_LANGUAGE, null)
        return AppLanguage.fromName(name)
    }

    fun store(context: Context, language: AppLanguage) {
        preferences(context).edit().putString(KEY_LANGUAGE, language.name).apply()
    }

    /** Wraps [base] in the stored language, or hands it back untouched when the device one wins. */
    fun wrap(base: Context): Context = wrap(base, stored(base))

    fun wrap(base: Context, language: AppLanguage): Context {
        val locale = Locale.forLanguageTag(language.tag ?: return base)
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(locale)
        // A one-entry list rather than the chosen locale in front of the device's: the resource
        // loader walks the list in order, so anything left behind would answer for whichever
        // string the chosen language happens to be missing, and the screen would come out mixed.
        configuration.setLocales(LocaleList(locale))
        return base.createConfigurationContext(configuration)
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
