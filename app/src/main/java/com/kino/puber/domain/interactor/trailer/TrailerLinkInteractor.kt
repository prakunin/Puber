package com.kino.puber.domain.interactor.trailer

import com.kino.puber.core.coroutine.runCatchingCancellable
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.playableUrl

/**
 * Turns an item's trailer into something a player can open.
 *
 * The item payload carries a signed CDN link for a few titles and, for most, only a storage path
 * such as `/trailers/d/02/....mp4`. A path is not playable -- the file sits behind a signed link
 * that has to be asked for -- so anything without one goes to `items/trailer` for it.
 */
internal class TrailerLinkInteractor(
    private val api: KinoPubApiClient,
) {

    /** The URL to play, or null when the item has no trailer or the request for one fails. */
    suspend fun resolve(item: Item): String? {
        val trailer = item.trailer ?: return null
        return trailer.playableUrl() ?: requestSignedLink(item.id)
    }

    private suspend fun requestSignedLink(itemId: Int): String? =
        runCatchingCancellable { api.getTrailerLinks(itemId).getOrThrow() }
            .getOrNull()
            ?.trailer
            .orEmpty()
            .firstNotNullOfOrNull { it.playableUrl() }
}
