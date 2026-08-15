package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.Eye
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.SkeletonAsyncImage
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.theme.PuberTheme

@Immutable
data class VideoItemUIState(
    val id: Int,
    val title: String,
    val imageUrl: String,
    val bigImageUrl: String,
    val wideImageUrl: String = "",
    val imageFallbackUrls: List<String> = emptyList(),
    val showTitle: Boolean = false,
    val unwatchedCount: Int? = null,
    val ratings: List<RatingUIState> = emptyList(),
    val progressPercent: Float? = null,
    val isWatched: Boolean = false,
    val showWatchedIndicator: Boolean = true,
    val isSeriesLike: Boolean = false,
    val isSaved: Boolean = false,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val isSeasonWatched: Boolean? = null,
    val year: String = "",
)

internal const val WATCHED_INDICATOR_TEST_TAG = "watched_indicator"
internal const val WATCH_PROGRESS_TEST_TAG = "watch_progress"

@Composable
fun VideoItem(
    modifier: Modifier = Modifier,
    state: VideoItemUIState,
    onClick: () -> Unit,
    onContextMenu: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .then(
                if (onContextMenu != null) {
                    Modifier.onTvContextMenuKey(onOpen = onContextMenu)
                } else {
                    Modifier
                }
            )
            .size(
                PuberTheme.Defaults.VideoItemWidth,
                PuberTheme.Defaults.VideoItemHeight,
            ),
        scale = CardDefaults.scale(pressedScale = 1f, focusedScale = 1f),
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            CardPoster(state = state, modifier = Modifier.fillMaxSize())
            TopEndBadge(
                state = state,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            CardCaption(
                state = state,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            WatchProgressBar(
                progressPercent = state.progressPercent,
                isWatched = state.isWatched,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/**
 * The poster, falling forward through [VideoItemUIState.imageFallbackUrls] as each candidate fails.
 *
 * The index is keyed on the item as well as the list, so a recycled card starts from the first
 * candidate again rather than inheriting the position the previous item's failures left behind.
 */
@Composable
private fun CardPoster(
    state: VideoItemUIState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val urls = remember(state.imageUrl, state.imageFallbackUrls) {
        (listOf(state.imageUrl) + state.imageFallbackUrls)
            .filter { it.isNotBlank() }
            .distinct()
    }
    var urlIndex by remember(state.id, urls) { mutableIntStateOf(0) }
    val currentUrl = urls.getOrNull(urlIndex)
    val imageRequest = remember(currentUrl) {
        currentUrl?.let { imageUrl ->
            ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build()
        }
    }
    SkeletonAsyncImage(
        modifier = modifier,
        model = imageRequest,
        onError = {
            if (urlIndex < urls.lastIndex) {
                urlIndex++
            }
        },
        contentDescription = null,
        contentScale = ContentScale.Crop,
    )
}

/**
 * Year, ratings and title over a scrim at the foot of the card. Absent entirely when the item has
 * none of them, so a bare poster is not darkened for nothing.
 */
@Composable
private fun CardCaption(
    state: VideoItemUIState,
    modifier: Modifier = Modifier,
) {
    val hasRatings = state.ratings.isNotEmpty()
    val hasYear = state.year.isNotBlank()
    val hasTitle = state.showTitle && state.title.isNotEmpty()
    if (!hasRatings && !hasYear && !hasTitle) return

    val scrimColor = MaterialTheme.colorScheme.scrim
    val gradientBrush = remember(scrimColor) {
        Brush.verticalGradient(
            colors = listOf(
                scrimColor.copy(alpha = 0f),
                scrimColor.copy(alpha = 0.85f),
            ),
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(gradientBrush)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        if (hasRatings || hasYear) {
            CardRatingsRow(state = state, hasYear = hasYear)
        }

        if (hasTitle) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = state.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (hasRatings || hasYear) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The year rides alongside the IMDB rating when there is one, and stands on its own line when there
 * is not — the card is too narrow to give it a line of its own while a rating sits beside it.
 */
@Composable
private fun CardRatingsRow(
    state: VideoItemUIState,
    hasYear: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (hasYear && state.ratings.none { it is RatingUIState.IMDB }) {
            CardYear(state.year)
        }
        state.ratings.forEach { rating ->
            if (hasYear && rating is RatingUIState.IMDB) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CardYear(state.year)
                    Rating(state = rating)
                }
            } else {
                Rating(state = rating)
            }
        }
    }
}

@Composable
internal fun CardYear(year: String) {
    Text(
        text = year,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * The eye badge and the unwatched-episode counter share the top-end corner, so only one may win.
 */
@Composable
private fun TopEndBadge(
    state: VideoItemUIState,
    modifier: Modifier = Modifier,
) {
    if (state.isWatched && state.showWatchedIndicator) {
        WatchedIndicatorBadge(visible = true, modifier = modifier)
        return
    }

    val count = state.unwatchedCount ?: return
    if (count <= 0) return

    Box(
        modifier = modifier
            .padding(6.dp)
            .background(
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
internal fun WatchProgressBar(
    progressPercent: Float?,
    isWatched: Boolean,
    modifier: Modifier = Modifier,
) {
    // A fully watched item is marked by the eye badge instead, so the two never stack.
    if (progressPercent == null || isWatched) return

    LinearProgressIndicator(
        progress = { progressPercent.coerceIn(0f, 1f) },
        modifier = modifier
            .testTag(WATCH_PROGRESS_TEST_TAG)
            .fillMaxWidth()
            .height(3.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    )
}

@Composable
internal fun WatchedIndicatorBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    Box(
        modifier = modifier
            .testTag(WATCHED_INDICATOR_TEST_TAG)
            .padding(6.dp)
            .background(
                MaterialTheme.colorScheme.scrim.copy(alpha = 0.48F),
                RoundedCornerShape(6.dp),
            )
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = PhosphorIcons.Fill.Eye,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9F),
        )
    }
}

// region Previews

private val allRatings = listOf(
    RatingUIState.KP("8.2"),
    RatingUIState.IMDB("7.5"),
    RatingUIState.PUB("9.1"),
)

private val twoRatings = listOf(
    RatingUIState.KP("8.9"),
    RatingUIState.IMDB("9.0"),
)

private val singleRating = listOf(
    RatingUIState.KP("7.3"),
)

private fun previewState(
    title: String = "Рик и Морти / Rick and Morty",
    showTitle: Boolean = false,
    unwatchedCount: Int? = null,
    ratings: List<RatingUIState> = emptyList(),
    progressPercent: Float? = null,
    year: String = "2024",
) = VideoItemUIState(
    id = 1,
    title = title,
    imageUrl = "",
    bigImageUrl = "",
    showTitle = showTitle,
    unwatchedCount = unwatchedCount,
    ratings = ratings,
    progressPercent = progressPercent,
    year = year,
)

@Preview(name = "Ratings only (3)")
@Composable
private fun PreviewRatingsOnly() = PuberTheme {
    VideoItem(state = previewState(ratings = allRatings), onClick = {})
}

@Preview(name = "Ratings only (2)")
@Composable
private fun PreviewTwoRatings() = PuberTheme {
    VideoItem(state = previewState(ratings = twoRatings), onClick = {})
}

@Preview(name = "Single rating (KP)")
@Composable
private fun PreviewSingleRating() = PuberTheme {
    VideoItem(state = previewState(ratings = singleRating), onClick = {})
}

@Preview(name = "Ratings + Title")
@Composable
private fun PreviewRatingsWithTitle() = PuberTheme {
    VideoItem(
        state = previewState(
            ratings = allRatings,
            showTitle = true,
        ),
        onClick = {},
    )
}

@Preview(name = "Ratings + Title + Badge")
@Composable
private fun PreviewRatingsTitleBadge() = PuberTheme {
    VideoItem(
        state = previewState(
            ratings = twoRatings,
            showTitle = true,
            unwatchedCount = 5,
        ),
        onClick = {},
    )
}

@Preview(name = "Title only (no ratings)")
@Composable
private fun PreviewTitleOnly() = PuberTheme {
    VideoItem(
        state = previewState(showTitle = true),
        onClick = {},
    )
}

@Preview(name = "Long title + Ratings")
@Composable
private fun PreviewLongTitleWithRatings() = PuberTheme {
    VideoItem(
        state = previewState(
            title = "Невероятные приключения невероятного героя в невероятном мире / The Incredible Adventures",
            showTitle = true,
            ratings = allRatings,
        ),
        onClick = {},
    )
}

@Preview(name = "No ratings, no title (plain card)")
@Composable
private fun PreviewPlainCard() = PuberTheme {
    VideoItem(state = previewState(), onClick = {})
}

@Preview(name = "Badge only (no ratings)")
@Composable
private fun PreviewBadgeOnly() = PuberTheme {
    VideoItem(
        state = previewState(unwatchedCount = 12),
        onClick = {},
    )
}

@Preview(name = "Partially watched (progress bar)")
@Composable
private fun PreviewPartiallyWatched() = PuberTheme {
    VideoItem(
        state = previewState(
            showTitle = true,
            ratings = twoRatings,
            progressPercent = 0.4f,
        ),
        onClick = {},
    )
}

@Preview(name = "Fully watched (eye badge)")
@Composable
private fun PreviewFullyWatched() = PuberTheme {
    VideoItem(
        state = previewState(showTitle = true).copy(isWatched = true),
        onClick = {},
    )
}

// endregion
