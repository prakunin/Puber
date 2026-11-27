package com.kino.puber.ui.feature.player.vm

import com.kino.puber.playertestfixtures.FixtureId
import com.kino.puber.playertestfixtures.PlayerTestFixtures
import com.kino.puber.playertestfixtures.server.HermeticRoute
import com.kino.puber.playertestfixtures.server.HermeticTestServer
import com.kino.puber.playertestfixtures.server.QueryMatchMode
import com.kino.puber.playertestfixtures.server.ResponsePlan

private const val HLS_CONTENT_TYPE = PlaybackControllerHarness.HLS_CONTENT_TYPE

internal fun PlaybackControllerHarness.fixtureText(fixture: FixtureId): String =
    PlayerTestFixtures.readBytes(fixture, testContext).toString(Charsets.UTF_8)

internal fun singleVariantMaster(videoPlaylist: String): String =
    """
    #EXTM3U
    #EXT-X-VERSION:3
    #EXT-X-INDEPENDENT-SEGMENTS
    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio_english.m3u8"
    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Español",LANGUAGE="es",DEFAULT=NO,AUTOSELECT=YES,URI="audio_spanish.m3u8"
    #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="audio"
    $videoPlaylist
    """.trimIndent()

private fun PlaybackControllerHarness.hlsText(body: String): ResponsePlan =
    HermeticTestServer.text(body = body, contentType = HLS_CONTENT_TYPE)

/**
 * The route set of the track, switch and fault scenarios: the media routes of the harness plus the
 * low/high quality masters, two progressive aliases and the subtitle. [extraRoutes] come first, so
 * they win over a route of the same path.
 */
internal fun PlaybackControllerHarness.commonHlsRoutes(
    qualityLowMasterBody: String = singleVariantMaster("video_360.m3u8"),
    lowPlaylist: ResponsePlan = hlsText(fixtureText(FixtureId.HlsVideo360Playlist)),
    extraRoutes: List<HermeticRoute> = emptyList(),
): List<HermeticRoute> {
    val progressive = PlayerTestFixtures.readBytes(FixtureId.ProgressiveMp4, testContext)
    val own = listOf(
        server.route(
            id = "progressive-a",
            path = "/media/progressive-a.mp4",
            response = ResponsePlan.Range(body = progressive, contentType = "video/mp4"),
        ),
        server.route(
            id = "progressive-b",
            path = "/media/progressive-b.mp4",
            response = ResponsePlan.Range(body = progressive, contentType = "video/mp4"),
        ),
        server.route(
            id = "quality-low",
            path = "/media/hls/quality-low.m3u8",
            response = hlsText(qualityLowMasterBody),
        ),
        server.route(
            id = "quality-high",
            path = "/media/hls/quality-high.m3u8",
            response = hlsText(singleVariantMaster("video_720.m3u8")),
        ),
        server.route(
            id = "subtitle",
            path = "/media/subtitle.vtt",
            queryMode = QueryMatchMode.Contains,
            response = HermeticTestServer.text(body = fixtureText(FixtureId.SubtitleWebVtt), contentType = "text/vtt"),
        ),
    )
    // The harness routes for the same paths come after, and the video_360 playlist is replaced.
    val base = mediaRoutes().filterNot { it.id == FixtureId.HlsVideo360Playlist.path }
    val video360 = server.route(
        id = FixtureId.HlsVideo360Playlist.path,
        path = "/media/hls/video_360.m3u8",
        response = lowPlaylist,
    )
    return extraRoutes + own + video360 + base
}
