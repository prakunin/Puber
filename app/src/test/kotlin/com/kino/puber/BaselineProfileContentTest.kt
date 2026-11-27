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
    fun profilesHaveNoInstrumentationOrGeneratorClasses() {
        listOf("baseline" to baseline, "startup" to startup).forEach { (name, lines) ->
            listOf("com/kino/puber/profile/", "com/kino/puber/baselineprofile/")
                .forEach { assertTrue(!lines.hasRuleFor(it), "$name profile holds $it") }
        }
    }

    @Test
    fun baselineProfileCoversImageTlsAndHlsPaths() {
        listOf(
            "coil3/network/",
            "coil3/decode/",
            "androidx/media3/exoplayer/hls/",
            "okhttp3/internal/tls/",
        ).forEach { assertTrue(baseline.hasRuleFor(it), "no baseline rule for $it") }
    }

    @Test
    fun baselineProfileCoversBookmarkFolderLookup() {
        assertTrue(
            baseline.any { "KinoPubApiClient;->getItemBookmarkFolders" in it },
            "no rule for getItemBookmarkFolders",
        )
        assertTrue(
            baseline.hasRuleFor("com/kino/puber/data/api/models/BookmarkFoldersResponse"),
            "no rule for BookmarkFoldersResponse",
        )
    }

    @Test
    fun baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons() {
        listOf(
            "okhttp3/internal/http1/Http1ExchangeCodec",
            "okhttp3/internal/http2/Http2Connection", // guard: already in the profile at 08f50b81 (DoH, update check)
            "kotlinx/serialization/internal/BooleanSerializer",
            "kotlinx/serialization/internal/FloatSerializer",
            "kotlinx/serialization/internal/DoubleSerializer",
            "com/adamglin/phosphoricons/fill/EyeKt",
            "com/adamglin/phosphoricons/fill/BookmarksimpleKt",
        ).forEach { assertTrue(baseline.hasRuleFor(it), "no baseline rule for $it") }
    }

    /**
     * The prod cold start the generation must keep warm: token decrypt, DNS-over-HTTPS, the endpoint
     * probe, the update check and the TV home publish. Each substring is a method (`;->`) or class a
     * run against the paired app records; the instrumentation app reaches them through the mock.
     */
    @Test
    fun profilesCoverProdColdStartPaths() {
        val guards = listOf(
            "com/kino/puber/data/repository/CryptoPreferenceRepository;->decrypt",
            "com/kino/puber/data/repository/CryptoPreferenceRepository;->secretKey",
            "com/kino/puber/data/api/network/DnsOverHttpsFactory;->create",
            "okhttp3/dnsoverhttps/DnsOverHttps;->",
            "okhttp3/dnsoverhttps/BootstrapDns;->lookup",
            "com/kino/puber/domain/interactor/api/ApiDomainInteractor;->walkForWorkingDomain",
            "com/kino/puber/data/api/network/HttpEndpointProbe;->isReachable",
            "com/kino/puber/domain/interactor/update/AppUpdateInteractor;->checkForUpdate",
            "com/kino/puber/data/repository/AppUpdateRepository;->getAvailableUpdate",
            "androidx/tvprovider/media/tv/PreviewProgram",
            "com/kino/puber/core/tvhome/AndroidTvHomePublisher;->publish",
        )
        listOf("baseline" to baseline, "startup" to startup).forEach { (name, lines) ->
            guards.forEach { assertTrue(lines.hasRuleFor(it), "$name profile has no rule for $it") }
        }
    }

    @Test
    fun startupProfileHasNoDetailsOrPlayerRules() {
        listOf(
            "com/kino/puber/ui/feature/details/",
            "com/kino/puber/ui/feature/player/",
        ).forEach { assertTrue(!startup.hasRuleFor(it), "startup profile holds $it") }
    }
}
