package com.kino.puber.ui.feature.showall.content

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.kino.puber.R
import com.kino.puber.ui.feature.showall.model.ShowAllTitle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

internal class ShowAllTitleResolveTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun shelfTitleIsPrefixedWithTheTabName() {
        val resolved = resolve(
            ShowAllTitle(
                titleRes = R.string.content_section_fresh,
                prefixRes = R.string.main_tabs_movies,
            ),
        )

        assertEquals(
            context.getString(
                R.string.show_all_title_shelf,
                context.getString(R.string.main_tabs_movies),
                context.getString(R.string.content_section_fresh),
            ),
            resolved,
        )
    }

    @Test
    fun wholeTypeTitleHasNoPrefix() {
        val resolved = resolve(ShowAllTitle(titleRes = R.string.browse_all_movies))

        assertEquals(context.getString(R.string.browse_all_movies), resolved)
    }

    @Test
    fun plainTitleIsUsedWhenThereIsNoResource() {
        val resolved = resolve(
            ShowAllTitle(titleRes = null, title = "Plain", prefixRes = R.string.main_tabs_movies),
        )

        assertEquals(
            context.getString(
                R.string.show_all_title_shelf,
                context.getString(R.string.main_tabs_movies),
                "Plain",
            ),
            resolved,
        )
    }

    private fun resolve(title: ShowAllTitle): String {
        lateinit var resolved: String
        composeRule.setContent { resolved = title.resolve() }
        composeRule.waitForIdle()
        return resolved
    }
}
