package com.kino.puber.core.ui.navigation

import android.os.Parcel
import androidx.compose.runtime.Composable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PuberScreenKeyTest {
    private class PlainScreen : PuberScreen {
        @Composable
        override fun Content() = Unit

        override fun describeContents(): Int = 0

        override fun writeToParcel(dest: Parcel, flags: Int) = Unit
    }

    @Test
    fun keyIsStillSimpleNameForNonParameterizedScreens() {
        assertEquals("PlainScreen", PlainScreen().key)
    }
}
