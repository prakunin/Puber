package com.kino.puber.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val packageName: String
        get() = targetPackage()

    @Before
    fun resetMock() {
        if (lateCheckDue) BaselineProfileTestControl.checkLate(packageName, backend)
        lateCheckDue = true
        backend.resetTest()
        BaselineProfileTestControl.resetJournal(packageName)
    }

    /**
     * One profile collection: before each iteration the target is killed and its caches dropped, so
     * every journey requests what it shows again; after it, the mock and the target's egress
     * journal must show a clean run that hit every required route.
     */
    private fun collectJourney(
        scenario: BaselineScenario,
        maxIterations: Int = DEFAULT_MAX_ITERATIONS,
        includeInStartupProfile: Boolean = false,
        journey: MacrobenchmarkScope.() -> Unit,
    ) = baselineProfileRule.collect(
        packageName = packageName,
        maxIterations = maxIterations,
        includeInStartupProfile = includeInStartupProfile,
        filterPredicate = { "com/kino/puber/profile/" !in it },
    ) {
        killProcess()
        BaselineProfileTestControl.clearTarget(packageName)
        backend.startIteration(scenario)
        journey()
        BaselineProfileTestControl.checkIteration(packageName, backend)
    }

    @Test
    fun startupProfile() = collectJourney(
        BaselineScenario.Startup,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        // Outlast the splash reveal cap (REVEAL_CAP_MS in core/startup/RevealGate.kt) so the late frames are profiled.
        Thread.sleep(REVEAL_CAP_WAIT_MS)
        device.waitForIdle()
    }

    @Test
    fun browseAndDetailsProfile() = collectJourney(BaselineScenario.BrowseAndDetails) {
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(2000)

        // Side rail: focus starts in Home content
        // DOWN → move into the first row below the hero
        device.pressDPadDown()
        device.waitForIdle()

        // Scroll through vertical sections
        repeat(8) {
            device.pressDPadDown()
            device.waitForIdle()
        }

        // Scroll RIGHT through a horizontal LazyRow
        repeat(5) {
            device.pressDPadRight()
            device.waitForIdle()
        }

        // Scroll back UP towards first card
        repeat(8) {
            device.pressDPadUp()
            device.waitForIdle()
        }

        // Rest on the card past the prefetcher's neighbour dwell (DetailsPrefetcher, 750 ms) so the
        // neighbours' details are warmed on every iteration, as for a user who pauses before OK.
        Thread.sleep(NEIGHBOUR_DWELL_WAIT_MS)

        // SELECT → DetailsScreen
        device.pressDPadCenter()
        device.waitForIdle()
        Thread.sleep(2000)

        // BACK → main screen
        device.pressBack()
        device.waitForIdle()
    }

    @Test
    fun railNavigationProfile() = collectJourney(BaselineScenario.Rail, maxIterations = RAIL_MAX_ITERATIONS) {
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(2000)

        // Side rail: LEFT from content opens the ModalNavigationDrawer
        device.pressDPadLeft()
        device.waitForIdle()
        // LEFT lands on the rail entry level with the focused content, which depends on the layout:
        // go to the top so the loop below always starts from Home.
        repeat(RAIL_TOTAL_ENTRIES) { device.pressDPadUp() }
        device.waitForIdle()

        // Every rail entry: DOWN to the next one, OK to open it, back to the rail. Search moves focus
        // into its text field, where LEFT no longer reaches the rail, so it is left with BACK. After
        // each entry the rail must have reopened (its titles are visible); a LEFT that did not reach
        // the rail fails here instead of profiling fewer tabs. This does not tell which entry is
        // selected: see the KDoc of railShows.
        RAIL_TITLE_RES.forEachIndexed { index, titleRes ->
            device.pressDPadDown()
            device.pressDPadCenter()
            device.waitForIdle()
            Thread.sleep(1000)
            if (index == SEARCH_INDEX) device.pressBack() else device.pressDPadLeft()
            device.waitForIdle()
            if (index == SEARCH_INDEX && !railShows(titleRes)) {
                // The first BACK only closed the keyboard.
                device.pressBack()
                device.waitForIdle()
            }
            check(railShows(titleRes)) {
                "The rail did not reopen after entry ${index + 1} of $RAIL_ENTRIES; the journey lost its place"
            }
        }

        // Back to Home, RIGHT into its content, scroll
        repeat(RAIL_ENTRIES) {
            device.pressDPadUp()
        }
        device.pressDPadCenter()
        device.waitForIdle()
        Thread.sleep(1000)
        device.pressDPadRight()
        device.waitForIdle()

        repeat(5) {
            device.pressDPadDown()
            device.waitForIdle()
        }
    }

    /** Opens the player on the first Home item; the watch position it saves goes to the mock. */
    @Test
    fun playbackProfile() = collectJourney(BaselineScenario.Playback) {
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(2000)

        // DOWN into Home content, OK → DetailsScreen
        device.pressDPadDown()
        device.waitForIdle()
        device.pressDPadCenter()
        device.waitForIdle()
        Thread.sleep(2000)

        // OK on the initially focused Play action → PlayerScreen
        device.pressDPadCenter()
        check(device.wait(Until.hasObject(By.res(PLAYER_RES_ID)), PLAYER_WAIT_MS)) {
            "The player screen (resource id $PLAYER_RES_ID) did not appear after OK on Play"
        }
        Thread.sleep(5000)

        // BACK → Details, BACK → main screen
        repeat(2) {
            device.pressBack()
            device.waitForIdle()
        }
    }

    /**
     * True once the rail ("main_rail" test tag, MainScreenComponent.kt) is open and lists [titleRes].
     * The collapsed rail exposes no item text, and the scope to the rail keeps a title elsewhere on
     * screen from counting, such as the "4K" section header on the Series screen (TabTypeConfig.kt).
     * The check does not tell which entry is selected or focused: a probe on the emulator showed LEFT
     * can land on another entry (Settings), so a selected-entry check (`By.checked(true)`, the
     * rail item View carries the selected state) fails the journey at entry 5 until
     * rail-left-focus-nearest-entry lands.
     */
    private fun MacrobenchmarkScope.railShows(titleRes: String): Boolean {
        val resources = InstrumentationRegistry.getInstrumentation().context.packageManager
            .getResourcesForApplication(packageName)
        val id = resources.getIdentifier(titleRes, "string", packageName)
        check(id != 0) { "string resource $titleRes not found in $packageName" }
        return device.wait(Until.hasObject(By.res("main_rail").hasDescendant(By.text(resources.getString(id)))), RAIL_WAIT_MS)
    }

    companion object {
        private lateinit var backend: BaselineMockBackend
        private var lateCheckDue = false

        private fun targetPackage(): String = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not found in instrumentation args")

        @JvmStatic
        @BeforeClass
        fun startBackend() {
            backend = BaselineProfileTestControl.createBackend(InstrumentationRegistry.getArguments())
            backend.start()
        }

        @JvmStatic
        @AfterClass
        fun stopBackend() {
            try {
                BaselineProfileTestControl.checkLate(targetPackage(), backend)
            } finally {
                backend.close()
            }
        }

        private const val DEFAULT_MAX_ITERATIONS = 15
        private const val REVEAL_CAP_WAIT_MS = 3_000L
        private const val NEIGHBOUR_DWELL_WAIT_MS = 1_500L
        // Rail order after Home: Search, Favourites, History, Movies, Series, 4K (TabType, enabled tabs).
        private val RAIL_TITLE_RES = listOf(
            "main_tabs_search",
            "main_tabs_favorites",
            "main_tabs_history",
            "main_tabs_movies",
            "main_tabs_series",
            "main_tabs_f4k",
        )
        // The journey takes about 30 s per iteration; the default 15 ran past 7 minutes on the emulator.
        private const val RAIL_MAX_ITERATIONS = 5
        private val RAIL_ENTRIES = RAIL_TITLE_RES.size
        // Every entry of the rail, enabled tabs and Settings included; more UPs than entries is harmless.
        private const val RAIL_TOTAL_ENTRIES = 12
        private const val SEARCH_INDEX = 0
        private const val RAIL_WAIT_MS = 5_000L
        private const val PLAYER_RES_ID = "player"
        private const val PLAYER_WAIT_MS = 15_000L
    }
}
