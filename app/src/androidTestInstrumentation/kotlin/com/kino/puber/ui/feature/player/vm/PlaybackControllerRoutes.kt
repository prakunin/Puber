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

/** A master whose only subtitle rendition is [subtitleName] ([language], DEFAULT=NO, manifest-only) at [uri]. */
internal fun subtitleVariantMaster(
    videoPlaylist: String,
    subtitleName: String,
    language: String,
    uri: String,
): String =
    """
    #EXTM3U
    #EXT-X-VERSION:3
    #EXT-X-INDEPENDENT-SEGMENTS
    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="English",LANGUAGE="en",DEFAULT=YES,AUTOSELECT=YES,URI="audio_english.m3u8"
    #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",NAME="Español",LANGUAGE="es",DEFAULT=NO,AUTOSELECT=YES,URI="audio_spanish.m3u8"
    #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="$subtitleName",LANGUAGE="$language",DEFAULT=NO,AUTOSELECT=NO,URI="$uri"
    #EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360,CODECS="avc1.42c01e,mp4a.40.2",AUDIO="audio",SUBTITLES="subs"
    $videoPlaylist
    """.trimIndent()

/** The manifest-only subtitle routes of [subtitleVariantMaster]: its playlist at `/media/hls/sub_<language>.m3u8` and its VTT. */
internal fun PlaybackControllerHarness.manifestSubtitleRoutes(language: String, cueText: String): List<HermeticRoute> = listOf(
    server.route(
        id = "sub-$language-playlist",
        path = "/media/hls/sub_$language.m3u8",
        response = hlsText(
            """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXT-X-MEDIA-SEQUENCE:0
            #EXT-X-PLAYLIST-TYPE:VOD
            #EXTINF:10.0,
            sub_$language.vtt
            #EXT-X-ENDLIST
            """.trimIndent(),
        ),
    ),
    server.route(
        id = "sub-$language-vtt",
        path = "/media/hls/sub_$language.vtt",
        response = HermeticTestServer.text(
            body = "WEBVTT\n\n00:00:00.000 --> 00:00:09.000\n$cueText\n",
            contentType = "text/vtt",
        ),
    ),
)

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
