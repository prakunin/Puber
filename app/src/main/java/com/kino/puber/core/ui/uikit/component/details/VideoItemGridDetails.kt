package com.kino.puber.core.ui.uikit.component.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.model.Lorem
import com.kino.puber.core.ui.uikit.theme.PuberTheme


/**
 * The share of the panel width the description occupies. It is [DescriptionWeight] of the whole in
 * either layout, so the text wraps the same way whether the picture is beside it or behind it.
 */
private const val DescriptionWeight = 3F
private const val PosterWeight = 5F
private const val DescriptionWidthFraction = DescriptionWeight / (DescriptionWeight + PosterWeight)

/** How far the picture reaches into the description in the overlapping layout. */
private const val MediaOverlapOfDescription = 1F / 3F

/**
 * The picture is as wide as it can be while still stopping a third of the way into the
 * description. Running it the whole width instead would scale a 16:9 frame to a panel nearer 3.5:1
 * and throw away half its height; this keeps most of the frame.
 */
private const val MediaWidthFraction =
    1F - DescriptionWidthFraction * (1F - MediaOverlapOfDescription)

/** Where the description's edge falls across the picture, measured from the picture's own left. */
private const val DescriptionEdgeInMedia =
    (DescriptionWidthFraction - (1F - MediaWidthFraction)) / MediaWidthFraction

/** How much of the picture the scrim still holds back under the last column of text. */
private const val ScrimAlphaAtTextEdge = 0.80F

/** How far across the picture that scrim has faded out completely. */
private const val ScrimEndFraction = 0.45F

/**
 * @param fullBleedMedia lets the still and the trailer reach across most of the panel and under
 * the right edge of the description, the way Prime does it, instead of standing the two side by
 * side. Off by default: only the catalogue and details panels play trailers, and the screens that
 * show a static poster read better with the picture in its own column.
 */
@Composable
fun VideoItemGridDetails(
    modifier: Modifier,
    state: VideoDetailsUIState,
    descriptionMaxLines: Int = Int.MAX_VALUE,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
    fullBleedMedia: Boolean = false,
) {
    if (fullBleedMedia) {
        Box(modifier = modifier) {
            // The same placement the details hero uses, from one definition: where the picture sits
            // and how wide it is must not be able to drift between the two screens that show it.
            VideoDetailsMedia(
                modifier = Modifier.fillMaxSize(),
                state = state,
                trailerUrl = trailerUrl,
                onTrailerFinished = onTrailerFinished,
            )
            // Drawn after the poster on purpose. The trailer is a `SurfaceView`, which clears
            // everything the window painted before it; only what comes later survives on top of a
            // playing video.
            VideoDetailsDescription(
                modifier = Modifier.fillMaxWidth(DescriptionWidthFraction),
                state = state,
                descriptionMaxLines = descriptionMaxLines,
            )
        }
    } else {
        Row(modifier = modifier) {
            VideoDetailsDescription(
                modifier = Modifier.weight(DescriptionWeight),
                state = state,
                descriptionMaxLines = descriptionMaxLines,
            )
            VideoDetailsPoster(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(PosterWeight),
                imageUrl = state.imageUrl,
                imageFallbackUrls = state.imageFallbackUrls,
                trailerUrl = trailerUrl,
                onTrailerFinished = onTrailerFinished,
            )
        }
    }
}

/**
 * The still and trailer alone, full-bleed, without the description panel — for a screen like the
 * details hero that draws its own text over the picture instead of beside it.
 */
@Composable
fun VideoDetailsMedia(
    modifier: Modifier,
    state: VideoDetailsUIState,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
) {
    Box(modifier = modifier) {
        VideoDetailsPoster(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(MediaWidthFraction)
                .align(Alignment.CenterEnd),
            imageUrl = state.imageUrl,
            imageFallbackUrls = state.imageFallbackUrls,
            trailerUrl = trailerUrl,
            onTrailerFinished = onTrailerFinished,
            fullBleed = true,
        )
    }
}

@Composable
fun VideoDetailsDescription(
    modifier: Modifier,
    state: VideoDetailsUIState,
    descriptionMaxLines: Int = Int.MAX_VALUE,
) {
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
                .padding(top = 4.dp),
        ) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .placeholder(visible = state.isLoading),
                text = state.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                state.ratings.forEach { rating ->
                    Rating(rating)
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(visible = state.isLoading),
                text = "${state.year}, ${state.genres} ${state.country}",
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                modifier = Modifier
                    .placeholder(visible = state.isLoading),
                text = state.duration,
                style = MaterialTheme.typography.labelSmall,
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (state.isLoading) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.description.split("\n").forEach { line ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .placeholder(
                                    visible = true,
                                    shape = RoundedCornerShape(2.dp)
                                )
                                .height(8.dp)
                        )
                    }
                }
            } else {
                Text(
                    modifier = Modifier
                        .fillMaxWidth(),
                    text = state.description,
                    style = MaterialTheme.typography.bodySmall,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = descriptionMaxLines,
                )
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoDetailsPoster(
    modifier: Modifier,
    imageUrl: String,
    imageFallbackUrls: List<String>,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
    fullBleed: Boolean = false,
) {
    Box(
        // The panel is far wider than 16:9, so a still scaled to its width stands taller than the
        // panel does. Across the whole width that overflow would otherwise land on the carousel
        // below.
        modifier = modifier.clipToBounds(),
    ) {
        val imageUrls = remember(imageUrl, imageFallbackUrls) {
            (listOf(imageUrl) + imageFallbackUrls)
                .filter { it.isNotBlank() }
                .distinct()
        }
        var urlIndex by remember(imageUrls) { mutableIntStateOf(0) }
        val currentUrl = imageUrls.getOrNull(urlIndex)

        var trailerRendered by remember(trailerUrl) { mutableStateOf(false) }

        PosterStill(
            imageUrl = currentUrl,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            onError = {
                if (urlIndex < imageUrls.lastIndex) {
                    urlIndex++
                }
            },
        )

        if (trailerUrl != null) {
            TrailerPreviewPlayer(
                url = trailerUrl,
                onFinished = onTrailerFinished,
                onFirstFrameRendered = { trailerRendered = true },
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
            )

            // The player is a `SurfaceView`: it punches a transparent hole through everything the
            // window drew before it, and media3 fills that hole with a black shutter until the
            // first frame arrives. So the still above is not what is on screen while the trailer
            // buffers — this second copy, drawn *after* the player, is. The 48 dp gradients below
            // stay visible over a playing trailer for the same reason.
            AnimatedVisibility(
                visible = !trailerRendered,
                enter = EnterTransition.None,
                exit = fadeOut(),
            ) {
                PosterStill(
                    imageUrl = currentUrl,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(),
                )
            }
        }

        if (fullBleed) {
            // The description's last third lies on top of the picture, so the scrim is what keeps
            // it readable: solid where the two meet, then a ramp well past the text so the picture
            // emerges gradually instead of starting at a seam.
            val surface = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            0.00F to surface,
                            DescriptionEdgeInMedia to surface.copy(alpha = ScrimAlphaAtTextEdge),
                            ScrimEndFraction to surface.copy(alpha = 0.0F),
                        )
                    )
            )
        } else {
            val gradientWidth = 48.dp
            Box(
                modifier = Modifier
                    .width(gradientWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.0F),
                            ),
                            endX = with(LocalDensity.current) { gradientWidth.toPx() },
                        )
                    )
            )
        }

        val gradientHeight = 48.dp
        Box(
            modifier = Modifier
                .height(gradientHeight)
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.0F),
                            MaterialTheme.colorScheme.surface,
                        ),
                        endY = with(LocalDensity.current) { gradientHeight.toPx() },
                    )
                )
        )
    }
}

@Composable
private fun PosterStill(
    imageUrl: String?,
    modifier: Modifier,
    onError: () -> Unit = {},
) {
    AsyncImage(
        modifier = modifier.placeholder(visible = imageUrl.isNullOrEmpty()),
        model = ImageRequest.Builder(LocalContext.current)
            .data(imageUrl)
            .crossfade(true)
            .build(),
        onError = { onError() },
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
@Preview(device = TV_1080p)
private fun VideoItemGridLoadingPreview() = PuberTheme {
    Column {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3F),
            state = VideoDetailsUIState.Loading,
        )

        Box(Modifier.weight(2F))
    }
}

@Composable
@Preview(device = TV_1080p, showBackground = true)
private fun VideoItemGridPreview() = PuberTheme {
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        VideoItemGridDetails(
            modifier = Modifier
                .fillMaxWidth()
                .weight(3F),
            state = VideoDetailsUIState(
                id = 0,
                title = "Movie Title \n Some Long Title",
                description = Lorem.words(100, newLineEachWordCount = 10),
                imageUrl = "",
                trailerUrl = "",
                ratings = listOf(
                    RatingUIState.IMDB("9.9"),
                    RatingUIState.KP("9.9"),
                    RatingUIState.PUB("9.9"),
                ),
                year = "1991",
                genres = "horror, thriller, action",
                duration = "Длительность: 2:00",
                country = "KZ",
            ),
        )

        Box(Modifier.weight(2F))
    }
}
