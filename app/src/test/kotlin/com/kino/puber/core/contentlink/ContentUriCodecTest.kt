package com.kino.puber.core.contentlink

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ContentUriCodecTest {

    private val codec = ContentUriCodec()

    @Test
    fun internalUri_roundTripsAllTargets() {
        val targets = listOf(
            ContentTarget.Details(itemId = 42),
            ContentTarget.EpisodeDetails(itemId = 42, seasonNumber = 2, episodeNumber = 7),
            ContentTarget.Playback(itemId = 42, videoNumber = 3),
            ContentTarget.Playback(itemId = 42, seasonNumber = 2, episodeNumber = 7),
        )

        targets.forEach { target ->
            assertEquals(target, codec.parse(codec.internalUri(target)))
        }
    }

    @Test
    fun parse_supportsCanonicalKinoPubDetailsAndEpisodeUrls() {
        assertEquals(
            ContentTarget.Details(itemId = 148),
            codec.parse("https://kino.pub/item/view/148/Back-to-the-Future?utm_source=share"),
        )
        assertEquals(
            ContentTarget.EpisodeDetails(itemId = 96451, seasonNumber = 1, episodeNumber = 3),
            codec.parse("https://kino.pub/item/view/96451/s1e3"),
        )
    }

    @Test
    fun publicUrl_stripsPlaybackActionAndTitleMetadata() {
        assertEquals(
            "https://kino.pub/item/view/42",
            codec.publicUrl(ContentTarget.Playback(itemId = 42, videoNumber = 2)),
        )
        assertEquals(
            "https://kino.pub/item/view/42/s2e7",
            codec.publicUrl(
                ContentTarget.Playback(itemId = 42, seasonNumber = 2, episodeNumber = 7),
            ),
        )
    }

    @Test
    fun parse_rejectsUnsupportedOrSensitiveWebLinks() {
        val links = listOf(
            "http://kino.pub/item/view/42",
            "https://mirror.example/item/view/42",
            "https://kino.pub/item/view/42/slug/extra",
            "https://kino.pub/item/view/42?action=play",
            "https://kino.pub/item/view/42?access_token=secret",
            "https://user@kino.pub/item/view/42",
            "https://kino.pub:443/item/view/42",
        )

        links.forEach { link -> assertNull(codec.parse(link), link) }
    }

    @Test
    fun parse_rejectsMalformedInternalRoutes() {
        val links = listOf(
            null,
            "",
            "puber://content/items/0",
            "puber://content/items/-1",
            "puber://content/items/2147483648",
            "puber://content/items/42/",
            "puber://content/items/42/seasons/1",
            "puber://content/items/42/seasons/1/episodes/0",
            "puber://content/items/42?action=unknown",
            "puber://content/items/42?video=2",
            "puber://content/items/42?action=play&action=play",
            "puber://content/items/42?action=play&token=secret",
            "puber://content/items/%34%32",
        )

        links.forEach { link -> assertNull(codec.parse(link), link) }
    }
}
