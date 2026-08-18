package com.kino.puber.core.system

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.kino.puber.core.model.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Stores the chosen interface language, publishes it, and puts it on a [Context].
 *
 * Sits outside the DI graph on purpose: `attachBaseContext` runs before Koin is started, so the
 * application and the activity have to read the choice straight from preferences. Everything else
 * goes through [com.kino.puber.data.preferences.AppLanguageRepository], which writes here.
 */
object AppLocale {

    const val PREFS_NAME = "language_preferences"
    const val KEY_LANGUAGE = "app_language"

    private val languageState = MutableStateFlow(AppLanguage.System)

    /**
     * The language the interface is drawn in right now, which is what makes the choice take effect
     * on the spot: the composition re-wraps its resources off this, and so does
     * [AndroidResourceProvider] for the strings resolved outside one.
     */
    val current: StateFlow<AppLanguage> = languageState.asStateFlow()

    fun stored(context: Context): AppLanguage {
        val name = preferences(context).getString(KEY_LANGUAGE, null)
        return AppLanguage.fromName(name)
    }

    fun store(context: Context, language: AppLanguage) {
        preferences(context).edit().putString(KEY_LANGUAGE, language.name).apply()
        languageState.value = language
    }

    /** Wraps [base] in the stored language, and makes that language the one [current] reports. */
    fun wrap(base: Context): Context = wrap(base, stored(base).also { languageState.value = it })

    fun wrap(base: Context, language: AppLanguage): Context {
        val locales = localesFor(language) ?: return base
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocales(locales)
        return base.createConfigurationContext(configuration)
    }

    /**
     * A one-entry list for a chosen language rather than that language in front of the device's:
     * the resource loader walks the list in order, so anything left behind would answer for
     * whichever string the chosen language happens to be missing, and the screen would come out
     * mixed.
     *
     * [AppLanguage.System] resolves to the device's own list, read from the system resources
     * rather than from [base] — [base] may already be wrapped in a language the user has just
     * moved away from, and the device list is the only thing that survives that. Null means there
     * is nothing to say, so the caller hands the context back untouched.
     */
    private fun localesFor(language: AppLanguage): LocaleList? {
        val tag = language.tag
            ?: return Resources.getSystem().configuration.locales.takeUnless(LocaleList::isEmpty)
        return LocaleList(Locale.forLanguageTag(tag))
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
