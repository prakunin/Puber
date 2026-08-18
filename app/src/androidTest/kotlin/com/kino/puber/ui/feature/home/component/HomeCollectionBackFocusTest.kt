package com.kino.puber.ui.feature.home.component

import android.app.Activity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.DpRect
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.core.ui.navigation.AppLauncher
import com.kino.puber.core.ui.navigation.AppRouter
import com.kino.puber.core.ui.navigation.PuberScreen
import com.kino.puber.core.ui.navigation.PuberTab
import com.kino.puber.core.ui.navigation.RootPuberScreen
import com.kino.puber.core.ui.navigation.Screens
import com.kino.puber.core.ui.navigation.TabRouter
import com.kino.puber.core.ui.navigation.component.FlowComponent
import com.kino.puber.core.ui.navigation.component.LazyAnchor
import com.kino.puber.core.ui.navigation.component.PuberCurrentTab
import com.kino.puber.core.ui.navigation.component.TabAppRouterHolder
import com.kino.puber.core.ui.navigation.component.TabComponent
import com.kino.puber.core.ui.uikit.component.HeroItemState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import com.kino.puber.ui.ScreensImpl
import com.kino.puber.ui.feature.home.model.HomeSectionState
import com.kino.puber.ui.feature.home.model.HomeSectionType
import com.kino.puber.ui.feature.home.model.HomeViewState
import com.kino.puber.ui.feature.main.model.TabType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.module

private const val COLLECTION_TITLE = "collection-1"
private const val COLLECTION_SCREEN_TAG = "collection_destination"
private const val COLLECTION_BACK_TAG = "collection_back"
private const val BOUNDS_TOLERANCE = 1f
private const val MAX_FOCUSED_NODE_DIAGNOSTICS = 5
private const val COLLECTIONS_ROW_INDEX = 5

/**
 * A collection opens from Home the way a details screen does, but it is pushed inside the tab flow
 * instead of the root one. Returning from it has to land on the very card it was opened from, with
 * the rows still where the user left them — otherwise the collections row, which sits last on Home,
 * has to be scrolled down to all over again.
 */
internal class HomeCollectionBackFocusTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun collectionBackRestoresFocusedCollectionCardAndViewport() {
        collectionBackRestoresFocusedCollectionCardAndViewport(withHero = false)
    }

    /**
     * The same journey on the Home the user actually sees: a hero carousel above the rows, which is
     * the initial focus target and therefore the one thing that must not swallow a restore.
     */
    @Test
    fun collectionBackUnderHeroRestoresFocusedCollectionCardAndViewport() {
        collectionBackRestoresFocusedCollectionCardAndViewport(withHero = true)
    }

    private fun collectionBackRestoresFocusedCollectionCardAndViewport(withHero: Boolean) {
        HomeCollectionProbeHost.showHero = withHero
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val tabRouter = TabRouter(coroutineScope)
        val tabAppRouterHolder = TabAppRouterHolder(ScreensImpl)
        HomeCollectionProbeHost.bind(tabRouter, tabAppRouterHolder)

        try {
            composeRule.setContent {
                PuberTheme {
                    FlowComponent(
                        scopeName = "HomeCollectionBackFocusTest",
                        screen = HomeCollectionProbeHostScreen,
                        moduleFactory = { scopeId, _ ->
                            module {
                                scope(named(scopeId)) {
                                    scoped<AppLauncher> { HomeCollectionNoOpAppLauncher }
                                    scoped<Screens> { ScreensImpl }
                                }
                            }
                        },
                    )
                }
            }
            composeRule.mainClock.advanceTimeBy(100)
            run {
                tabRouter.openTab(
                    PuberTab(
                        screen = HomeCollectionProbeHomeScreen,
                        tag = TabType.Home,
                    ),
                )
            }
            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule
                    .onAllNodes(hasText("row-0-item-0"))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeRule.mainClock.advanceTimeBy(200)

            composeRule
                .onNodeWithText("row-0-item-0")
                .performSemanticsAction(SemanticsActions.RequestFocus)
                .assertIsFocused()
            repeat(COLLECTIONS_ROW_INDEX) { row ->
                composeRule
                    .onNodeWithText("row-$row-item-0")
                    .performDirection(Key.DirectionDown)
                composeRule.mainClock.advanceTimeBy(300)
            }
            composeRule
                .onNodeWithText("collection-0")
                .performDirection(Key.DirectionRight)
            composeRule.mainClock.advanceTimeBy(1_000)

            val focusedCard = composeRule.onNodeWithText(COLLECTION_TITLE)
            focusedCard.assertIsFocused()
            val boundsBefore = focusedCard.getUnclippedBoundsInRoot()
            val anchorBefore = HomeCollectionProbeHost.anchor()

            focusedCard.performDirection(Key.DirectionCenter)
            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule
                    .onAllNodes(hasTestTag(COLLECTION_SCREEN_TAG))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            composeRule.onNodeWithTag(COLLECTION_BACK_TAG).performDirection(Key.DirectionCenter)
            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule
                    .onAllNodes(hasTestTag(COLLECTION_SCREEN_TAG))
                    .fetchSemanticsNodes()
                    .isEmpty()
            }
            composeRule.waitUntil(timeoutMillis = 1_500) {
                composeRule
                    .onAllNodes(hasText(COLLECTION_TITLE))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            try {
                composeRule.waitUntil(timeoutMillis = 2_000) {
                    composeRule
                        .onAllNodes(isFocused() and hasText(COLLECTION_TITLE))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
            } catch (error: ComposeTimeoutException) {
                throw AssertionError(
                    "Expected $COLLECTION_TITLE focused after returning from the collection; " +
                        "focused nodes=" + focusedNodeSummary(),
                    error,
                )
            }
            composeRule.mainClock.advanceTimeBy(500)
            val anchorAfter = HomeCollectionProbeHost.anchor()
            assertEquals("lazy anchor", anchorBefore, anchorAfter)
            val boundsAfter = composeRule
                .onNode(isFocused() and hasText(COLLECTION_TITLE))
                .getUnclippedBoundsInRoot()
            assertRectEquals(boundsBefore, boundsAfter)
        } finally {
            tabAppRouterHolder.dispose()
            coroutineScope.cancel()
            HomeCollectionProbeHost.clear()
        }
    }

    private fun assertRectEquals(before: DpRect, after: DpRect) {
        assertTrue(
            "focused card moved: before=$before after=$after",
            listOf(
                before.left.value - after.left.value,
                before.top.value - after.top.value,
                before.right.value - after.right.value,
                before.bottom.value - after.bottom.value,
            ).all { kotlin.math.abs(it) <= BOUNDS_TOLERANCE },
        )
    }

    private fun focusedNodeSummary(): List<String> {
        return composeRule
            .onAllNodes(isFocused(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .take(MAX_FOCUSED_NODE_DIAGNOSTICS)
            .map { node ->
                val text = node.config
                    .getOrNull(SemanticsProperties.Text)
                    ?.joinToString(separator = "|") { it.text }
                val tag = node.config.getOrNull(SemanticsProperties.TestTag)
                "text=$text, tag=$tag, bounds=${node.boundsInRoot}"
            }
    }

    private fun SemanticsNodeInteraction.performDirection(key: Key) {
        performKeyInput {
            keyDown(key)
            keyUp(key)
        }
    }
}

private object HomeCollectionProbeHost {
    private var tabRouter: TabRouter? = null
    private var tabAppRouterHolder: TabAppRouterHolder? = null
    private var lazyListState: LazyListState? = null
    var showHero: Boolean = false

    fun bind(tabRouter: TabRouter, tabAppRouterHolder: TabAppRouterHolder) {
        this.tabRouter = tabRouter
        this.tabAppRouterHolder = tabAppRouterHolder
    }

    fun clear() {
        tabRouter = null
        tabAppRouterHolder = null
        lazyListState = null
        showHero = false
    }

    fun requireTabRouter(): TabRouter = requireNotNull(tabRouter)

    fun requireTabAppRouterHolder(): TabAppRouterHolder = requireNotNull(tabAppRouterHolder)

    fun recordLazyListState(state: LazyListState) {
        lazyListState = state
    }

    fun anchor(): LazyAnchor {
        val state = requireNotNull(lazyListState)
        return LazyAnchor(
            index = state.firstVisibleItemIndex,
            offset = state.firstVisibleItemScrollOffset,
        )
    }
}

@Parcelize
private data object HomeCollectionProbeHostScreen : PuberScreen {
    @Composable
    override fun Content() {
        TabComponent(
            tabRouter = HomeCollectionProbeHost.requireTabRouter(),
            tabAppRouterHolder = HomeCollectionProbeHost.requireTabAppRouterHolder(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRestorer()
                    .focusGroup()
            ) {
                PuberCurrentTab()
            }
        }
    }
}

@Parcelize
private data object HomeCollectionProbeHomeScreen : PuberScreen {
    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        val listState = rememberLazyListState()
        SideEffect {
            HomeCollectionProbeHost.recordLazyListState(listState)
        }
        HomeScreenContent(
            state = HomeViewState.Content(
                heroItems = probeHeroItems(HomeCollectionProbeHost.showHero),
                sections = probeSections(),
            ),
            onAction = {},
            onHeroClick = {},
            onCollectionClick = { id, _ -> router.navigateTo(HomeCollectionProbeCollectionScreen(id)) },
            lazyListState = listState,
        )
    }
}

private fun probeHeroItems(showHero: Boolean): List<HeroItemState> {
    if (!showHero) return emptyList()
    return (0..1).map { index ->
        HeroItemState(
            id = 200 + index,
            title = "hero-$index",
            wideImageUrl = "",
            fallbackImageUrl = "",
            year = "2026",
            genres = "",
        )
    }
}

private fun probeSections(): List<HomeSectionState> {
    val itemRows = listOf(
        HomeSectionType.ContinueWatching,
        HomeSectionType.WatchLater,
        HomeSectionType.Bookmarks,
        HomeSectionType.Fresh,
        HomeSectionType.PopularMovies,
    ).mapIndexed { row, type ->
        HomeSectionState(
            title = "Row $row",
            type = type,
            items = (0..2).map { column ->
                VideoItemUIState(
                    id = row * 10 + column,
                    title = "row-$row-item-$column",
                    imageUrl = "",
                    bigImageUrl = "",
                    showTitle = true,
                )
            },
        )
    }
    val collections = HomeSectionState(
        title = "Collections",
        type = HomeSectionType.Collections,
        items = (0..2).map { column ->
            VideoItemUIState(
                id = 100 + column,
                title = "collection-$column",
                imageUrl = "",
                bigImageUrl = "",
                showTitle = true,
            )
        },
    )
    return itemRows + collections
}

@Parcelize
private data class HomeCollectionProbeCollectionScreen(
    private val collectionId: Int,
) : RootPuberScreen {

    @IgnoredOnParcel
    override val key: String = "HomeCollectionProbeCollectionScreen_$collectionId"

    @Composable
    override fun Content() {
        val router = requireNotNull(LocalPuberKoinScope.current).get<AppRouter>()
        Box(
            Modifier
                .fillMaxSize()
                .testTag(COLLECTION_SCREEN_TAG)
        ) {
            Button(
                onClick = { router.back() },
                modifier = Modifier.testTag(COLLECTION_BACK_TAG),
            ) {
                Text("back")
            }
        }
    }
}

private object HomeCollectionNoOpAppLauncher : AppLauncher {
    override fun restart() = Unit

    override fun finish() = Unit

    override fun bind(activity: Activity) = Unit

    override fun unbind() = Unit
}
