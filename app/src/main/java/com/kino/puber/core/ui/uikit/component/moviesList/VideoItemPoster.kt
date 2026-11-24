package com.kino.puber.core.ui.uikit.component.moviesList

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.fill.ThumbsUp
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.SkeletonAsyncImage
import com.kino.puber.core.ui.uikit.component.onTvContextMenuKey
import com.kino.puber.core.ui.uikit.theme.ImdbYellow
import com.kino.puber.core.ui.uikit.theme.KinoPubGreen
import com.kino.puber.core.ui.uikit.theme.KinopoiskOrange
import com.kino.puber.core.ui.uikit.theme.OnKinoPubGreen
import com.kino.puber.core.ui.uikit.theme.PosterChipScrim
import com.kino.puber.core.ui.uikit.theme.PuberTheme

private const val YEAR_SEPARATOR = " · "
private const val POSTER_ASPECT_RATIO = 2f / 3f
private const val MAX_PILLS_PER_ROW = 2

/**
 * Vertical 2:3 poster card of the Show all grid: quality badge, rating pills, local title and the
 * original title with the year. Numbers come from the approved stand in
 * `docs/design/showall/README.md` ("Starting numbers"), see [VideoItemPosterDefaults].
 *
 * The tile fills the width the caller gives it; the column count of the grid sets the size.
 */
@Composable
fun VideoItemPoster(
    state: VideoItemUIState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onContextMenu: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val fill = colors.surfaceVariant.copy(alpha = VideoItemPosterDefaults.TileFillAlpha)
    val shape = RoundedCornerShape(VideoItemPosterDefaults.TileCorner)
    Card(
        modifier = modifier
            .then(
                if (onContextMenu != null) {
                    Modifier.onTvContextMenuKey(onOpen = onContextMenu)
                } else {
                    Modifier
                },
            )
            .onSelectKeyClick(onClick),
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(containerColor = fill, focusedContainerColor = fill),
        border = CardDefaults.border(
            border = Border(
                border = BorderStroke(
                    VideoItemPosterDefaults.TileBorderWidth,
                    colors.border.copy(alpha = VideoItemPosterDefaults.TileBorderAlpha),
                ),
                shape = shape,
            ),
            focusedBorder = Border(
                border = BorderStroke(VideoItemPosterDefaults.FocusBorderWidth, colors.border),
                shape = shape,
            ),
        ),
        scale = CardDefaults.scale(
            scale = 1f,
            focusedScale = VideoItemPosterDefaults.FocusScale,
            pressedScale = VideoItemPosterDefaults.FocusScale,
        ),
        glow = CardDefaults.glow(),
    ) {
        Column(modifier = Modifier.padding(VideoItemPosterDefaults.TilePadding)) {
            PosterArt(state)
            Spacer(Modifier.height(VideoItemPosterDefaults.PosterToTitle))
            Text(
                text = state.localTitle.ifBlank { state.title },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = VideoItemPosterDefaults.TitleFontSize,
                    lineHeight = VideoItemPosterDefaults.TitleLineHeight,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.onSurface,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(VideoItemPosterDefaults.TitleToOriginal))
            OriginalTitleLine(state)
        }
    }
}

@Composable
private fun PosterArt(state: VideoItemUIState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(POSTER_ASPECT_RATIO)
            .clip(RoundedCornerShape(VideoItemPosterDefaults.PosterCorner)),
    ) {
        PosterImage(state, Modifier.matchParentSize())
        state.qualityBadge?.let { badge ->
            QualityBadge(
                badge = badge,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(VideoItemPosterDefaults.BadgeInset),
            )
        }
        TopEndBadge(state = state, modifier = Modifier.align(Alignment.TopEnd))
        RatingPills(
            ratings = state.pillRatings,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(
                    bottom = VideoItemPosterDefaults.PillInset,
                    start = VideoItemPosterDefaults.PillRowPaddingHorizontal,
                    end = VideoItemPosterDefaults.PillRowPaddingHorizontal,
                ),
        )
        WatchProgressBar(
            progressPercent = state.progressPercent,
            isWatched = state.isWatched,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** The poster, falling forward through [posterCardImageUrls] as each candidate fails. */
@Composable
private fun PosterImage(
    state: VideoItemUIState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val urls = remember(state.imageUrl, state.bigImageUrl, state.wideImageUrl, state.imageFallbackUrls) {
        posterCardImageUrls(state)
    }
    var urlIndex by remember(state.id, urls) { mutableIntStateOf(0) }
    val currentUrl = urls.getOrNull(urlIndex)
    val imageRequest = remember(currentUrl) {
        currentUrl?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
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

@Composable
private fun QualityBadge(
    badge: VideoQualityBadge,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val isUhd = badge == VideoQualityBadge.UHD
    val shape = RoundedCornerShape(VideoItemPosterDefaults.BadgeCorner)
    Box(
        modifier = modifier
            .height(VideoItemPosterDefaults.BadgeHeight)
            .background(if (isUhd) KinoPubGreen else PosterChipScrim, shape)
            .then(
                if (isUhd) {
                    Modifier
                } else {
                    Modifier.border(1.dp, colors.onSurface.copy(alpha = HD_STROKE_ALPHA), shape)
                },
            )
            .padding(horizontal = VideoItemPosterDefaults.BadgePaddingHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(if (isUhd) R.string.poster_card_badge_4k else R.string.poster_card_badge_hd),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = VideoItemPosterDefaults.BadgeFontSize,
                lineHeight = VideoItemPosterDefaults.BadgeLineHeight,
                fontWeight = FontWeight.Bold,
            ),
            color = if (isUhd) OnKinoPubGreen else colors.onSurface,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RatingPills(
    ratings: List<RatingUIState>,
    modifier: Modifier = Modifier,
) {
    if (ratings.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            VideoItemPosterDefaults.PillSpacing,
            Alignment.CenterHorizontally,
        ),
        verticalArrangement = Arrangement.spacedBy(VideoItemPosterDefaults.PillSpacing),
        maxItemsInEachRow = MAX_PILLS_PER_ROW,
    ) {
        ratings.forEach { RatingPill(it) }
    }
}

@Composable
private fun RatingPill(rating: RatingUIState) {
    Row(
        modifier = Modifier
            .height(VideoItemPosterDefaults.PillHeight)
            .background(PosterChipScrim, RoundedCornerShape(VideoItemPosterDefaults.PillHeight / 2))
            .padding(horizontal = VideoItemPosterDefaults.PillPaddingHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(VideoItemPosterDefaults.PillIconGap),
    ) {
        when (rating) {
            is RatingUIState.PUB -> PillVectorIcon(PhosphorIcons.Fill.ThumbsUp, KinoPubGreen)
            is RatingUIState.KP -> PillDrawableIcon(
                R.drawable.ic_kinopoisk,
                KinopoiskOrange,
                Modifier.size(VideoItemPosterDefaults.PillIconSize),
            )
            is RatingUIState.IMDB -> PillDrawableIcon(
                R.drawable.ic_imdb,
                ImdbYellow,
                Modifier.size(
                    width = VideoItemPosterDefaults.PillImdbIconWidth,
                    height = VideoItemPosterDefaults.PillIconSize,
                ),
            )
        }
        Text(
            text = rating.value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = VideoItemPosterDefaults.PillFontSize,
                lineHeight = VideoItemPosterDefaults.PillLineHeight,
                fontWeight = FontWeight.Medium,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun PillVectorIcon(icon: ImageVector, tint: Color) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(VideoItemPosterDefaults.PillIconSize),
        tint = tint,
    )
}

@Composable
private fun PillDrawableIcon(resId: Int, tint: Color, modifier: Modifier) {
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
private fun OriginalTitleLine(state: VideoItemUIState) {
    val line = originalLine(state.originalTitle, state.year)
    val style = MaterialTheme.typography.bodySmall.copy(
        fontSize = VideoItemPosterDefaults.OriginalFontSize,
        lineHeight = VideoItemPosterDefaults.OriginalLineHeight,
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (line == null) {
        // Keeps the height of the line, so cards with and without an original title align.
        Text(text = "", style = style, color = color, minLines = 1, maxLines = 1)
        return
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        if (line.original.isNotBlank()) {
            Text(
                text = line.original,
                modifier = Modifier.weight(1f, fill = false),
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (line.yearText.isNotEmpty()) {
            Text(
                text = line.yearText,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Candidate poster URLs for the card: the stand's medium tier, then big, then the remaining
 * fallbacks. The wide poster is never asked for, so a card never loads a landscape image.
 */
internal fun posterCardImageUrls(state: VideoItemUIState): List<String> {
    return (listOf(state.imageUrl, state.bigImageUrl) + state.imageFallbackUrls)
        .filter { it.isNotBlank() && it != state.wideImageUrl }
        .distinct()
}

internal data class OriginalLine(val original: String, val yearText: String)

/**
 * The original title and the year as two values, so a long original ellipsizes and the year stays
 * whole. Null when neither is known.
 */
internal fun originalLine(originalTitle: String, year: String): OriginalLine? {
    return when {
        originalTitle.isNotBlank() && year.isNotBlank() -> OriginalLine(originalTitle, YEAR_SEPARATOR + year)
        originalTitle.isNotBlank() -> OriginalLine(originalTitle, "")
        year.isNotBlank() -> OriginalLine("", year)
        else -> null
    }
}

private const val HD_STROKE_ALPHA = 0.28f

/** Numbers of the Show all poster card, from `docs/design/showall/README.md` "Starting numbers". */
internal object VideoItemPosterDefaults {
    val TilePadding = 5.dp
    val TileCorner = 10.dp
    val PosterCorner = 7.dp
    const val TileFillAlpha = 0.14f
    val TileBorderWidth = 1.dp
    const val TileBorderAlpha = 0.22f
    val FocusBorderWidth = 2.dp
    const val FocusScale = 1.05f

    val BadgeHeight = 11.dp
    val BadgeFontSize = 7.sp
    val BadgeLineHeight = 11.sp
    val BadgePaddingHorizontal = 4.dp
    val BadgeInset = 5.dp
    val BadgeCorner = 3.dp

    val PillHeight = 13.dp
    val PillFontSize = 8.sp
    val PillLineHeight = 13.sp
    val PillIconSize = 8.dp
    val PillImdbIconWidth = 10.dp
    val PillSpacing = 3.dp
    val PillInset = 5.dp
    val PillPaddingHorizontal = 4.55.dp
    val PillIconGap = 2.6.dp
    val PillRowPaddingHorizontal = 2.dp

    val TitleFontSize = 10.sp
    val TitleLineHeight = 12.sp
    val OriginalFontSize = 8.sp
    val OriginalLineHeight = 10.sp
    val PosterToTitle = 5.dp
    val TitleToOriginal = 1.dp
}

// region Previews

private fun previewState(
    quality: VideoQualityBadge? = null,
    originalTitle: String = "Original title",
    year: String = "2020",
) = VideoItemUIState(
    id = 1,
    title = "Русское название / $originalTitle",
    imageUrl = "",
    bigImageUrl = "",
    localTitle = "Русское название фильма",
    originalTitle = originalTitle,
    year = year,
    qualityBadge = quality,
    pillRatings = listOf(
        RatingUIState.PUB("8.3"),
        RatingUIState.KP("7.2"),
        RatingUIState.IMDB("7.1"),
    ),
)

@Composable
private fun PreviewCard(state: VideoItemUIState) {
    PuberTheme {
        VideoItemPoster(state = state, onClick = {}, modifier = Modifier.width(135.dp))
    }
}

@Preview(widthDp = 150, heightDp = 260)
@Composable
private fun VideoItemPoster4kPreview() = PreviewCard(previewState(VideoQualityBadge.UHD))

@Preview(widthDp = 150, heightDp = 260)
@Composable
private fun VideoItemPosterHdPreview() = PreviewCard(previewState(VideoQualityBadge.HD))

@Preview(widthDp = 150, heightDp = 260)
@Composable
private fun VideoItemPosterNoBadgePreview() = PreviewCard(previewState())

@Preview(widthDp = 150, heightDp = 260)
@Composable
private fun VideoItemPosterNoOriginalPreview() = PreviewCard(previewState(originalTitle = ""))

@Preview(widthDp = 150, heightDp = 260)
@Composable
private fun VideoItemPosterLongOriginalPreview() =
    PreviewCard(previewState(originalTitle = "A very long original title that does not fit the card"))

// endregion
