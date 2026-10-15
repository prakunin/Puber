package com.kino.puber.core.telemetry

import android.os.Parcel
import android.os.Parcelable
import androidx.compose.runtime.Composable
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab

/** Parcelize-free doubles; none of them is ever written to a parcel. */
internal open class TestPuberScreen : PuberScreen {
    @Composable
    override fun Content() = Unit

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}

internal class HomeScreen : TestPuberScreen()

internal class ScreenScreen : TestPuberScreen()

internal class HomeVM

internal class DialogVM

internal class ParameterizedScreen(private val id: String) : TestPuberScreen() {
    override val key: String get() = "ParameterizedScreen:$id"
}

internal sealed interface SampleState {
    data object Content : SampleState
}

internal fun tabOf(screen: PuberScreen): PuberTab = PuberTab(screen, tag = TestTag)

internal object TestTag : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) = Unit
}
