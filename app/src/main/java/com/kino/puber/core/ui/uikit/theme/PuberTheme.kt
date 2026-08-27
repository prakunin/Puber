package com.kino.puber.core.ui.uikit.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.kino.puber.core.ui.uikit.component.LocalTvContextMenuLongSelectState
import com.kino.puber.core.ui.uikit.component.TvContextMenuLongSelectState

data object PuberTheme {
    data object Defaults {
        val VideoItemWidth = 120.dp
        val VideoItemHeight = 180.dp
        val HorizontalVideoItemHeight = 150.dp

        /** Card height for catalogue rows, kept separate so their layout can evolve independently. */
        val CatalogueRowItemHeight = 150.dp
        const val HorizontalVideoItemAspectRatio = 16f / 9f
        const val DetailsWeight = 1F
        const val ContentWeight = 1F

        /**
         * The catalogue tab leaves enough room below the detail panel for one 150 dp card, its
         * section title and spacing. The panel still owns most of the screen and carries the 16∶9
         * media frame behind the rows.
         *
         * Deliberately not [DetailsWeight] / [ContentWeight], which the favourites screen also
         * uses — there the lower half is a grid of full-height posters and would be crushed.
         * Stated as percentages so the two layout requirements remain explicit.
         */
        const val CatalogueDetailsWeight = 62F
        const val CatalogueContentWeight = 38F
    }
}

@Composable
fun PuberTheme(
    content: @Composable () -> Unit,
) {
    val contextMenuLongSelectState = remember { TvContextMenuLongSelectState() }
    val colorSchemeTv = darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        error = Error60,
        errorContainer = Error60,
        background = OledBackground,
        surface = OledBackground,
    )

    val colorScheme = androidx.compose.material3.darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80,
        error = Error60,
        errorContainer = Error60,
        background = OledBackground,
        surface = OledBackground,
    )
    CompositionLocalProvider(
        LocalTvContextMenuLongSelectState provides contextMenuLongSelectState,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = colorScheme,
            content = {
                MaterialTheme(
                    colorScheme = colorSchemeTv,
                    typography = Typography,
                    content = content
                )
            }
        )
    }
}
