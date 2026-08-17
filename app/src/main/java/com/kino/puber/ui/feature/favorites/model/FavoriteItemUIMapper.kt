package com.kino.puber.ui.feature.favorites.model

import com.kino.puber.core.ui.model.VideoItemTypeMapper
import com.kino.puber.core.ui.model.VideoItemUIMapper
import com.kino.puber.core.ui.uikit.component.details.VideoDetailsUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridItemUIState
import com.kino.puber.core.ui.uikit.component.moviesList.VideoGridUIState
import com.kino.puber.data.api.models.Item
import com.kino.puber.data.api.models.isSeriesLike

internal class FavoriteItemUIMapper(
    val videoItemUIMapper: VideoItemUIMapper,
    val typeMapper: VideoItemTypeMapper,
) {

    fun mapToState(items: List<Item>, selectedItem: Item?): FavoriteViewState.Content {
        return FavoriteViewState.Content(
            gridState = mapList(items),
            selectedItem = mapSelectedItem(items, selectedItem),
        )
    }

    /**
     * What the side panel shows: the item whose details were fetched when it belongs on this grid,
     * and the first row otherwise — drawn from the list entry, which is everything the panel has
     * before the details land, and all it ever gets for a row that is not series-like.
     */
    fun mapSelectedItem(items: List<Item>, selectedItem: Item?): VideoDetailsUIState {
        val effectiveSelected = if (selectedItem?.type?.isSeriesLike() == true) {
            selectedItem
        } else {
            items.firstOrNull { it.type.isSeriesLike() }
        }
        return effectiveSelected?.let(::mapDetailedItem) ?: VideoDetailsUIState.Loading
    }


    fun mapList(items: List<Item>): VideoGridUIState {
        val seriesOnly = items.filter { it.type.isSeriesLike() }
        return VideoGridUIState(
            list = buildList {
                val groupedItems = seriesOnly.groupBy { it.type }
                groupedItems.forEach { (type, items) ->
                    if (groupedItems.size > 1) {
                        add(VideoGridItemUIState.Title(typeMapper.map(type)))
                    }
                    add(
                        VideoGridItemUIState.Items(
                            items = videoItemUIMapper.mapShortItemList(items).mapSaved(),
                            rowKey = "favorites_${type.name}",
                        )
                    )
                }
            },
        )
    }

    fun mapDetailedItem(item: Item): VideoDetailsUIState {
        return videoItemUIMapper.mapDetailedItem(item)
    }

    private fun List<com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState>.mapSaved() =
        map { item -> item.copy(isSaved = true) }

}
