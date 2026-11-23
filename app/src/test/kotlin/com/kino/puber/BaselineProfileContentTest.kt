package com.kino.puber

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Text only: what the checked-in profiles hold. Regenerate them with tools/generate-baseline-profile.sh. */
internal class BaselineProfileContentTest {
    private val baseline = profileLines("baseline-prof.txt")
    private val startup = profileLines("startup-prof.txt")

    private fun profileLines(name: String): List<String> {
        val dir = "src/main/generated/baselineProfiles"
        return listOf(File("$dir/$name"), File("app/$dir/$name"))
            .first { it.isFile }
            .readLines()
    }

    private fun List<String>.hasRuleFor(classPrefix: String) = any { "L$classPrefix" in it }

    @Test
    fun baselineProfileCoversDetailsPlayerRailTabsAndStartupClasses() {
        listOf(
            "com/kino/puber/ui/feature/details/",
            "com/kino/puber/ui/feature/player/",
            "com/kino/puber/core/telemetry/TelemetryRuntime",
            "com/kino/puber/core/startup/RevealGate",
            "com/kino/puber/ui/feature/player/vm/PlayerVM",
            "com/kino/puber/ui/feature/details/component/DetailsScreen",
            "com/kino/puber/ui/feature/favorites/",
            "com/kino/puber/ui/feature/history/",
            "com/kino/puber/ui/feature/contentlist/",
        ).forEach { assertTrue(baseline.hasRuleFor(it), "no baseline rule for $it") }
    }

    @Test
    fun baselineProfileHasNoRemovedTopTabClasses() {
        listOf(
            "com/kino/puber/ui/feature/main/toptabs/",
            "com/kino/puber/ui/feature/main/model/TabType",
            "com/kino/puber/core/model/NavigationMode",
            "com/kino/puber/domain/interactor/auth/model/AuthState",
        ).forEach { assertTrue(!baseline.hasRuleFor(it), "stale baseline rule for $it") }
    }

    @Test
    fun baselineProfileMethodRulesAreHot() {
        val notHot = baseline.filter { "->" in it && !it.startsWith("H") }
        assertTrue(notHot.isEmpty(), "${notHot.size} method rules without H, first: ${notHot.firstOrNull()}")
    }

    @Test
    fun startupProfileHasNoDetailsOrPlayerRules() {
        listOf(
            "com/kino/puber/ui/feature/details/",
            "com/kino/puber/ui/feature/player/",
        ).forEach { assertTrue(!startup.hasRuleFor(it), "startup profile holds $it") }
    }
}
