package com.kino.puber.baselineprofile

import android.os.Build
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val packageName: String
        get() = InstrumentationRegistry.getArguments().getString("targetAppId")
            ?: error("targetAppId not found in instrumentation args")

    @Before
    fun requireInPlaceReset() {
        check(Build.VERSION.SDK_INT >= 34) {
            "Below API 34 without root the benchmark library uninstalls the target before each journey, " +
                "which drops the pairing; generate on an emulator with API 34 or newer."
        }
    }

    @Test
    fun startupProfile() = baselineProfileRule.collect(
        packageName = packageName,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        // Outlast the splash reveal cap (REVEAL_CAP_MS in core/startup/RevealGate.kt) so the late frames are profiled.
        Thread.sleep(REVEAL_CAP_WAIT_MS)
        device.waitForIdle()
        requirePairedApp()
    }

    @Test
    fun browseAndDetailsProfile() = baselineProfileRule.collect(
        packageName = packageName,
    ) {
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

        // SELECT → DetailsScreen
        device.pressDPadCenter()
        device.waitForIdle()
        Thread.sleep(2000)

        // BACK → main screen
        device.pressBack()
        device.waitForIdle()
        requirePairedApp()
    }

    @Test
    fun railNavigationProfile() = baselineProfileRule.collect(
        packageName = packageName,
        maxIterations = RAIL_MAX_ITERATIONS,
    ) {
        startActivityAndWait()
        device.waitForIdle()
        Thread.sleep(2000)

        // Side rail: LEFT from content opens the ModalNavigationDrawer
        device.pressDPadLeft()
        device.waitForIdle()

        // Every rail entry: DOWN to the next one, OK to open it, back to the rail. Search moves focus
        // into its text field, where LEFT no longer reaches the rail, so it is left with BACK. After
        // each entry the rail must show the next title; a journey that lost its place fails here
        // instead of profiling fewer tabs.
        RAIL_TITLE_RES.forEachIndexed { index, _ ->
            device.pressDPadDown()
            device.pressDPadCenter()
            device.waitForIdle()
            Thread.sleep(1000)
            if (index == SEARCH_INDEX) device.pressBack() else device.pressDPadLeft()
            device.waitForIdle()
            val expectedTitle = RAIL_TITLE_RES.getOrNull(index + 1) ?: RAIL_TITLE_RES[index]
            if (index == SEARCH_INDEX && !railShows(expectedTitle)) {
                // The first BACK only closed the keyboard.
                device.pressBack()
                device.waitForIdle()
            }
            check(railShows(expectedTitle)) {
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
        requirePairedApp()
    }

    /**
     * Opens the player on the first Home item. Leaving the player saves a watch position
     * (`PlayerVM.exitPlayer`), so every run of this journey writes to the paired KinoPub account
     * (docs/release.md, Refresh Baseline Profiles).
     */
    @Test
    fun playbackProfile() = baselineProfileRule.collect(
        packageName = packageName,
    ) {
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
        requirePairedApp()
    }

    /**
     * The generator never writes tokens. The app must already be paired on the device; the
     * benchmark library resets it in place on API 34 and above, which keeps app data.
     */
    private fun MacrobenchmarkScope.requirePairedApp() {
        val output = device.executeShellCommand("content query --uri content://$packageName.profile.auth/tokens")
        val paired = Regex("name=access_token, value_base64=\\S+").containsMatchIn(output) &&
            Regex("name=refresh_token, value_base64=\\S+").containsMatchIn(output)
        Log.i(LOG_TAG, "journey end: paired=$paired")
        check(paired) {
            "The app is not paired on this device; pair it once (docs/release.md, Refresh Baseline Profiles). " +
                "The generator never writes tokens."
        }
    }

    private fun MacrobenchmarkScope.railShows(titleRes: String): Boolean {
        val resources = InstrumentationRegistry.getInstrumentation().context.packageManager
            .getResourcesForApplication(packageName)
        val id = resources.getIdentifier(titleRes, "string", packageName)
        check(id != 0) { "string resource $titleRes not found in $packageName" }
        return device.wait(Until.hasObject(By.text(resources.getString(id))), RAIL_WAIT_MS)
    }

    private companion object {
        private const val LOG_TAG = "PuberGen"

        private const val REVEAL_CAP_WAIT_MS = 3_000L
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
        private const val SEARCH_INDEX = 0
        private const val RAIL_WAIT_MS = 5_000L
        private const val PLAYER_RES_ID = "player"
        private const val PLAYER_WAIT_MS = 15_000L
    }
}
