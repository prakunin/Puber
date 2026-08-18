package com.kino.puber.data.preferences

import com.kino.puber.core.model.AppLanguage
import com.kino.puber.core.system.AppLocale
import android.content.Context

/**
 * The interface language the user has chosen.
 *
 * Only stores the choice. Applying it is [AppLocale]'s job: writing here publishes the language on
 * [AppLocale.current], which the composition and
 * [com.kino.puber.core.system.ResourceProvider] both follow, so the interface changes over without
 * a restart. What a screen has already resolved into its state — a rail title, a duration label —
 * keeps the old language until that state is built again.
 */
class AppLanguageRepository(private val context: Context) {

    fun getLanguage(): AppLanguage = AppLocale.stored(context)

    fun setLanguage(language: AppLanguage) = AppLocale.store(context, language)
}
