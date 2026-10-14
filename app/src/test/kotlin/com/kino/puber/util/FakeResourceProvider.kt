package com.kino.puber.util

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kino.puber.core.system.ResourceProvider
import java.util.Locale

class FakeResourceProvider : ResourceProvider {
    override var locale: Locale = Locale.ENGLISH
    override fun getString(resId: Int): String = "string_$resId"
    override fun getString(resId: Int, vararg arg: Any): String =
        "string_${resId}_${arg.joinToString("_")}"
    override fun getColor(colorRes: Int): Int = 0
    override fun getStringArray(resId: Int): Array<String> = emptyArray()
    override fun getQuantityString(resId: Int, quantity: Int, vararg args: Any): String =
        "quantity_${resId}_${quantity}"
    override fun getImageVector(resId: Int): ImageVector =
        ImageVector.Builder("test", 24.dp, 24.dp, 24f, 24f).build()
}
