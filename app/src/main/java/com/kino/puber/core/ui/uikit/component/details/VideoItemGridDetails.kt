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
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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


/** Side-by-side layout only: the description column measured against the poster column. */
private const val DescriptionWeight = 3F
private const val PosterWeight = 5F

/**
 * Full-bleed layout: how much of the panel the text covers, and how much the picture does.
 *
 * These two used to be one derivation — the description was [DescriptionWeight] of the whole in
 * either layout, and the picture was whatever was left plus a third of the text's width, so that
 * the two overlapped by a fixed amount. The catalogue stand pulled them apart: the text there
 * wants 60 % of the width while the picture stays at 75 %, which is far more overlap than the
 * derivation could express. They are independent numbers now, and the scrim below is what makes
 * the overlap readable rather than the geometry.
 *
 * Only the catalogue takes this branch — favourites draws the two side by side, and the details
 * screen passes its own fraction to [VideoDetailsMedia].
 */
private const val FullBleedDescriptionWidthFraction = 0.60F
private const val FullBleedMediaWidthFraction = 0.75F

/** How much of the picture the scrim still holds back under the last column of text. */
private const val ScrimAlphaAtTextEdge = 0.90F

/** How far across the picture that scrim has faded out completely. */
private const val ScrimEndFraction = 0.45F

/**
 * How the picture is let go towards the right. The catalogue and the content screen share this
 * component but not a layout: there the text lies over the picture's last third, here the column
 * is narrower and holding back as much black under it buys nothing.
 */
@Immutable
data class MediaScrim(
    val edgeFraction: Float,
    val alphaAtEdge: Float,
    val endFraction: Float,
    /**
     * How tall the fade at the picture's bottom edge is. It lives here rather than as one constant
     * because the catalogue's frame overflows past the panel and that fade is what ends it, while
     * on the details screen the same gradient only has to meet the rail below.
     */
    val bottomFade: Dp,
) {
    companion object {
        val Catalogue = MediaScrim(
            edgeFraction = 0.10F,
            alphaAtEdge = ScrimAlphaAtTextEdge,
            endFraction = ScrimEndFraction,
            bottomFade = 50.dp,
        )

        val Details = MediaScrim(
            edgeFraction = 0.10F,
            alphaAtEdge = 0.50F,
            endFraction = 0.50F,
            bottomFade = 48.dp,
        )
    }
}

/**
 * Everything about the description column that the catalogue and the favourites screen disagree
 * on. Same shape as [MediaScrim] and for the same reason: one component draws this block on two
 * screens whose layouts have nothing in common, and retuning it in place would move both.
 *
 * The type comes through as sizes rather than whole `TextStyle`s so the theme still owns the
 * family, weight and tracking; only what the stand actually dialled is named here.
 */
@Immutable
data class DescriptionLayout(
    val horizontalPadding: Dp,
    val topPadding: Dp,
    val titleAlignment: Alignment.Horizontal,
    val titleTextAlign: TextAlign,
    val titleSize: TextUnit,
    val titleToRatings: Dp,
    val ratingsToFacts: Dp,
    val factsSize: TextUnit,
    val factsLineHeight: TextUnit,
    val betweenFactLines: Dp,
    val factsToPlot: Dp,
    val plotLineHeight: TextUnit,
) {
    companion object {
        /** What every screen but the catalogue still gets. */
        val Default = DescriptionLayout(
            horizontalPadding = 16.dp,
            topPadding = 4.dp,
            titleAlignment = Alignment.CenterHorizontally,
            titleTextAlign = TextAlign.Center,
            titleSize = TextUnit.Unspecified,
            titleToRatings = 8.dp,
            ratingsToFacts = 4.dp,
            factsSize = TextUnit.Unspecified,
            factsLineHeight = TextUnit.Unspecified,
            betweenFactLines = 4.dp,
            factsToPlot = 8.dp,
            plotLineHeight = TextUnit.Unspecified,
        )

        /**
         * The catalogue panel: the title left aligned on the same axis as everything under it,
         * and a bigger title over smaller facts, because here the column is 60 % of the width and
         * lies over a picture rather than standing beside one.
         */
        val Catalogue = Default.copy(
            horizontalPadding = 15.dp,
            topPadding = 5.dp,
            titleAlignment = Alignment.Start,
            titleTextAlign = TextAlign.Start,
            titleSize = 20.sp,
            titleToRatings = 4.dp,
            ratingsToFacts = 5.dp,
            factsSize = 10.sp,
            factsLineHeight = 15.sp,
            plotLineHeight = 15.sp,
        )
    }
}

/**
 * Overrides only what was actually named. `copy(fontSize = TextUnit.Unspecified)` does not leave
 * the style's own size alone — it clears it, and the text then falls back to Compose's 14 sp
 * default. Every screen that takes [DescriptionLayout.Default] names nothing, so it has to come
 * out of here byte-identical to the style it went in as.
 */
private fun TextStyle.override(
    size: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
): TextStyle = copy(
    fontSize = if (size.isSpecified) size else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight else this.lineHeight,
)

/** The catalogue artwork and trailers are supplied as landscape 16:9 media. */
private const val LandscapeMediaAspectRatio = 16F / 9F

/**
 * @param fullBleedMedia lets the still and the trailer reach across most of the panel and under
 * the right edge of the description, the way Prime does it, instead of standing the two side by
 * side. Off by default: only the catalogue and details panels play trailers, and the screens that
 * show a static poster read better with the picture in its own column.
 * @param expandMediaIntoContent gives the still and trailer one top-aligned 16:9 frame whose bottom
 * can overflow behind the catalogue rows instead of cropping either medium to the details panel.
 */
@Composable
fun VideoItemGridDetails(
    modifier: Modifier,
    state: VideoDetailsUIState,
    descriptionMaxLines: Int = Int.MAX_VALUE,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
    fullBleedMedia: Boolean = false,
    expandMediaIntoContent: Boolean = false,
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
                expandIntoContent = expandMediaIntoContent,
            )
            // Drawn after the poster on purpose. The trailer is a `SurfaceView`, which clears
            // everything the window painted before it; only what comes later survives on top of a
            // playing video.
            VideoDetailsDescription(
                modifier = Modifier.fillMaxWidth(FullBleedDescriptionWidthFraction),
                state = state,
                descriptionMaxLines = descriptionMaxLines,
                layout = DescriptionLayout.Catalogue,
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
    expandIntoContent: Boolean = false,
    widthFraction: Float = FullBleedMediaWidthFraction,
    scrim: MediaScrim = MediaScrim.Catalogue,
) {
    if (expandIntoContent) {
        ExpandedMediaLayout(modifier = modifier) {
            VideoDetailsPoster(
                modifier = Modifier,
                imageUrl = state.imageUrl,
                imageFallbackUrls = state.imageFallbackUrls,
                trailerUrl = trailerUrl,
                onTrailerFinished = onTrailerFinished,
                fullBleed = true,
                scrim = scrim,
                scaleTrailerToFit = true,
                alignMediaToTop = true,
            )
        }
        return
    }

    Box(modifier = modifier) {
        VideoDetailsPoster(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(widthFraction)
                .align(Alignment.CenterEnd),
            imageUrl = state.imageUrl,
            imageFallbackUrls = state.imageFallbackUrls,
            trailerUrl = trailerUrl,
            onTrailerFinished = onTrailerFinished,
            fullBleed = true,
            scrim = scrim,
        )
    }
}

/**
 * Reports the panel's original size to its parent while measuring the media itself at a full 16:9.
 * Compose does not clip overflowing children by default, so the extra height continues behind the
 * catalogue below; that later content is still drawn over it and keeps its normal focus geometry.
 */
@Composable
private fun ExpandedMediaLayout(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        modifier = modifier,
        content = content,
    ) { measurables, constraints ->
        val layoutWidth = constraints.maxWidth
        val layoutHeight = constraints.maxHeight
        val mediaWidth = (layoutWidth * FullBleedMediaWidthFraction).toInt()
        val mediaHeight = (mediaWidth / LandscapeMediaAspectRatio).toInt()
        val media = measurables.single().measure(
            Constraints.fixed(width = mediaWidth, height = mediaHeight),
        )

        layout(width = layoutWidth, height = layoutHeight) {
            media.placeRelative(x = layoutWidth - mediaWidth, y = 0)
        }
    }
}

@Composable
fun VideoDetailsDescription(
    modifier: Modifier,
    state: VideoDetailsUIState,
    descriptionMaxLines: Int = Int.MAX_VALUE,
    layout: DescriptionLayout = DescriptionLayout.Default,
) {
    val factsStyle = MaterialTheme.typography.labelSmall
        .override(size = layout.factsSize, lineHeight = layout.factsLineHeight)
    Box(
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = layout.horizontalPadding)
                .padding(top = layout.topPadding),
        ) {
            Text(
                modifier = Modifier
                    .align(layout.titleAlignment)
                    .placeholder(visible = state.isLoading),
                text = state.title,
                style = MaterialTheme.typography.titleMedium.override(size = layout.titleSize),
                textAlign = layout.titleTextAlign,
            )
            Spacer(modifier = Modifier.height(layout.titleToRatings))
            Row {
                state.ratings.forEach { rating ->
                    Rating(rating)
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            Spacer(modifier = Modifier.height(layout.ratingsToFacts))

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .placeholder(visible = state.isLoading),
                text = "${state.year}, ${state.genres} ${state.country}",
                style = factsStyle,
            )

            Spacer(modifier = Modifier.height(layout.betweenFactLines))

            Text(
                modifier = Modifier
                    .placeholder(visible = state.isLoading),
                text = state.duration,
                style = factsStyle,
            )

            Spacer(modifier = Modifier.height(layout.factsToPlot))

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
                    style = MaterialTheme.typography.bodySmall
                        .override(lineHeight = layout.plotLineHeight),
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
    scrim: MediaScrim = MediaScrim.Catalogue,
    scaleTrailerToFit: Boolean = false,
    alignMediaToTop: Boolean = false,
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
            alignment = if (alignMediaToTop) Alignment.TopCenter else Alignment.Center,
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
                scaleToFit = scaleTrailerToFit,
                alignContentToTop = alignMediaToTop,
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
                    alignment = if (alignMediaToTop) Alignment.TopCenter else Alignment.Center,
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
                            scrim.edgeFraction to surface.copy(alpha = scrim.alphaAtEdge),
                            scrim.endFraction to surface.copy(alpha = 0.0F),
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

        val gradientHeight = scrim.bottomFade
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
    alignment: Alignment = Alignment.Center,
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
        alignment = alignment,
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
