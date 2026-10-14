package com.kino.puber.core.system

import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

interface ResourceProvider {
    /** The locale strings resolve in, which follows the in-app language rather than the device's. */
    val locale: Locale
    fun getString(resId: Int): String
    fun getString(resId: Int, vararg arg: Any): String
    fun getColor(colorRes: Int): Int
    fun getStringArray(resId: Int): Array<String>
    fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String
    fun getImageVector(resId: Int): ImageVector
}