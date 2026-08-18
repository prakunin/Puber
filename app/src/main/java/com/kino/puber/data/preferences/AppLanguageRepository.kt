package com.kino.puber.data.preferences

import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.system.AppLocale
import android.content.Context

/**
 * The interface language the user has chosen.
 *
 * Only stores the choice. Applying it is [AppLocale]'s job, and it happens once, in
 * `attachBaseContext`, before anything can read a string — so a language written here governs
 * from the next start of the app rather than the moment it is written. Everything the process has
 * already resolved, in a composition or through
 * [com.kino.puber.core.system.ResourceProvider], stays in one language until then.
 */
class AppLanguageRepository(private val context: Context) {

    fun getLanguage(): AppLanguage = AppLocale.stored(context)

    fun setLanguage(language: AppLanguage) = AppLocale.store(context, language)
}
