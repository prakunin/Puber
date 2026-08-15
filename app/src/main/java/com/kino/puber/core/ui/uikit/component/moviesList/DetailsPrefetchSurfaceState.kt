package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import com.kino.puber.core.di.LocalPuberKoinScope
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher

/**
 * One list surface's half of the prefetch: which cards exist, and which of them focus is on.
 *
 * The prefetcher is told about a card only if its row registered itself, so a row whose cards do
 * not open the details screen — Home's Collections, the player's episode grid — simply never
 * appears here and nothing is fetched for it.
 */
internal class DetailsPrefetchSurfaceState(
    val id: DetailsPrefetcher.SurfaceId,
    val neighbourhood: FocusNeighbourhood,
    private val prefetcher: DetailsPrefetcher,
) {
    fun onItemFocused(rowKey: String, itemId: Int) {
        val neighbours = neighbourhood.neighboursOf(rowKey, itemId) ?: return
        prefetcher.onFocused(id, itemId, neighbours)
    }
}

/** Null wherever there is no opted-in surface, which makes every call site below a no-op. */
internal val LocalDetailsPrefetchSurface = compositionLocalOf<DetailsPrefetchSurfaceState?> { null }

/**
 * Opens a prefetch surface over [content] and closes it when the surface leaves composition —
 * including on a move to the details screen or the player, where nothing on this list is worth
 * fetching any more.
 *
 * Resolved from the screen's Koin scope rather than injected, so a composable rendered outside DI —
 * a preview, a UI test — draws exactly as before with prefetching absent.
 */
@Composable
internal fun DetailsPrefetchSurface(enabled: Boolean = true, content: @Composable () -> Unit) {
    val scope = LocalPuberKoinScope.current
    val prefetcher = remember(scope) { scope?.get<DetailsPrefetcher>() }
    if (!enabled || prefetcher == null) {
        content()
        return
    }
    val surface = remember(prefetcher) {
        DetailsPrefetchSurfaceState(
            id = DetailsPrefetcher.SurfaceId(),
            neighbourhood = FocusNeighbourhood(),
            prefetcher = prefetcher,
        )
    }
    DisposableEffect(surface) {
        onDispose { prefetcher.onSurfaceGone(surface.id) }
    }
    CompositionLocalProvider(LocalDetailsPrefetchSurface provides surface, content = content)
}

/**
 * Declares a row of cards that open the details screen, at its absolute position in the surface.
 *
 * [rowOrder] must count every row the surface has, registered or not: it is what tells a row one
 * D-pad press below from one two presses below. Rows that do not participate leave a gap, and a gap
 * yields no candidate.
 */
@Composable
internal fun DetailsPrefetchRow(
    rowOrder: Int,
    rowKey: String,
    items: List<VideoItemUIState>,
    enabled: Boolean = true,
) {
    val surface = LocalDetailsPrefetchSurface.current
    if (surface == null || !enabled) return
    val itemIds = remember(items) { items.map(VideoItemUIState::id) }
    DisposableEffect(surface, rowOrder, rowKey, itemIds) {
        surface.neighbourhood.register(rowOrder = rowOrder, rowKey = rowKey, itemIds = itemIds)
        onDispose { surface.neighbourhood.unregister(rowKey) }
    }
}
