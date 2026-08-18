package com.kino.puber.core.system

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.core.content.ContextCompat
import com.kino.puber.core.model.AppLanguage

class AndroidResourceProvider(private val appContext: Context) : ResourceProvider {

    @Volatile
    private var localized: Pair<AppLanguage, Context>? = null

    /**
     * The application was wrapped once, in `attachBaseContext`, so on its own it would keep
     * answering in whichever language the process started in. Re-wrapping when the choice changes
     * is what lets a string resolved outside a composition — an error message, a snackbar written
     * by a view model — come out in the language the screen is already showing.
     */
    private val context: Context
        get() {
            val language = AppLocale.current.value
            localized?.takeIf { it.first == language }?.let { return it.second }
            return AppLocale.wrap(appContext, language).also { localized = language to it }
        }

    override fun getString(resId: Int): String = context.getString(resId)
    override fun getString(resId: Int, vararg arg: Any): String = context.getString(resId, *arg)
    override fun getColor(colorRes: Int): Int = ContextCompat.getColor(context, colorRes)
    override fun getStringArray(resId: Int): Array<String> = context.resources.getStringArray(resId)
    override fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String {
        return context.resources.getQuantityString(resId, quantity, *args)
    }

    override fun getImageVector(resId: Int): ImageVector {
        val localizedContext = context
        return ImageVector.vectorResource(
            theme = localizedContext.theme,
            res = localizedContext.resources,
            resId = resId,
        )
    }
}
