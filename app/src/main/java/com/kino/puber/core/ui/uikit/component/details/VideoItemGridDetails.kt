package com.kino.puber.core.ui.uikit.component.details

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.asPainter
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.transitionFactory
import coil3.transition.CrossfadeTransition
import coil3.transition.Transition
import coil3.transition.TransitionTarget
import com.kino.puber.core.ui.uikit.component.Rating
import com.kino.puber.core.ui.uikit.component.RatingUIState
import com.kino.puber.core.ui.uikit.component.SkeletonAsyncImage
import com.kino.puber.core.ui.uikit.component.modifier.placeholder
import com.kino.puber.core.ui.uikit.model.Lorem
import com.kino.puber.core.ui.uikit.theme.PuberTheme
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull


private const val POSTER_FADE_OUT_MS = 300
private const val POSTER_CROSSFADE_MS = 200

/** How long a card's `Loading` may keep the previous poster before the panel falls back to the skeleton. */
internal const val POSTER_HOLD_MS = 3_000L

/**
 * The panel's card-change motion, in one place so tuning it is a one-line change. The poster and
 * the text move on the same translation and differ in distance and in how they fade.
 */
internal object PanelMotion {
    const val DURATION_MS = 250
    val POSTER_SHIFT = 48.dp
    val TEXT_SHIFT = 40.dp
    const val POSTER_FADE_IN_MS = 150
    const val TEXT_FADE_OUT_MS = 90
    const val TEXT_FADE_IN_DELAY_MS = 90
    const val TEXT_FADE_IN_MS = 160

    /** Larger than any screen: the text clip bounds one edge and leaves the others open. */
    const val OPEN_CLIP_EDGE_PX = 16_384F
}

/** The poster keeps its last picture instead of flashing a skeleton on every card change. */
internal const val POSTER_SKELETON_ONLY_UNTIL_FIRST_IMAGE = true

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
    val plotSize: TextUnit,
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
            plotSize = TextUnit.Unspecified,
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
            plotSize = 14.sp,
            plotLineHeight = 17.sp,
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
    direction: PanelDirection = PanelDirection.None,
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
                direction = direction,
            )
            // Drawn after the poster on purpose. The trailer is a `SurfaceView`, which clears
            // everything the window painted before it; only what comes later survives on top of a
            // playing video.
            VideoDetailsDescription(
                modifier = Modifier.fillMaxWidth(FullBleedDescriptionWidthFraction),
                state = state,
                descriptionMaxLines = descriptionMaxLines,
                layout = DescriptionLayout.Catalogue,
                direction = direction,
            )
        }
    } else {
        Row(modifier = modifier) {
            VideoDetailsDescription(
                modifier = Modifier.weight(DescriptionWeight),
                state = state,
                descriptionMaxLines = descriptionMaxLines,
                direction = direction,
            )
            VideoDetailsPoster(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(PosterWeight),
                imageUrl = state.imageUrl,
                imageFallbackUrls = state.imageFallbackUrls,
                isLoading = state.isLoading,
                trailerUrl = trailerUrl,
                onTrailerFinished = onTrailerFinished,
                direction = direction,
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
    direction: PanelDirection = PanelDirection.None,
) {
    if (expandIntoContent) {
        ExpandedMediaLayout(modifier = modifier) {
            VideoDetailsPoster(
                modifier = Modifier,
                imageUrl = state.imageUrl,
                imageFallbackUrls = state.imageFallbackUrls,
                isLoading = state.isLoading,
                trailerUrl = trailerUrl,
                onTrailerFinished = onTrailerFinished,
                fullBleed = true,
                scrim = scrim,
                scaleTrailerToFit = true,
                alignMediaToTop = true,
                direction = direction,
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
            isLoading = state.isLoading,
            trailerUrl = trailerUrl,
            onTrailerFinished = onTrailerFinished,
            fullBleed = true,
            scrim = scrim,
            direction = direction,
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
    direction: PanelDirection = PanelDirection.None,
) {
    val factsStyle = MaterialTheme.typography.labelSmall
        .override(size = layout.factsSize, lineHeight = layout.factsLineHeight)
    val held = rememberHeldDescription(state)
    // One direction per change: the screen's direction falls back to None once the card is marked
    // as shown, and that recomposition must not turn a running shift into a plain crossfade.
    val latched = remember(held.id) { direction }
    val layoutDirection = LocalLayoutDirection.current
    val textShiftPx = with(LocalDensity.current) { PanelMotion.TEXT_SHIFT.roundToPx() }
    Box(
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = held,
            modifier = Modifier.startEdgeClip(),
            contentKey = { it.id },
            transitionSpec = { textTransition(initialState, targetState, latched, layoutDirection, textShiftPx) },
            label = "descriptionChange",
        ) { shown ->
            VideoDetailsDescriptionBody(
                state = shown,
                descriptionMaxLines = descriptionMaxLines,
                layout = layout,
                factsStyle = factsStyle,
            )
        }
    }
}

private fun textTransition(
    initial: VideoDetailsUIState,
    target: VideoDetailsUIState,
    direction: PanelDirection,
    layoutDirection: LayoutDirection,
    shiftPx: Int,
): ContentTransform {
    val sign = if (initial.isLoading || target.isLoading) 0 else enterSign(direction, layoutDirection)
    val slide = tween<IntOffset>(PanelMotion.DURATION_MS, easing = FastOutSlowInEasing)
    val enter = slideInHorizontally(slide) { sign * shiftPx } +
        fadeIn(
            tween(
                PanelMotion.TEXT_FADE_IN_MS,
                delayMillis = PanelMotion.TEXT_FADE_IN_DELAY_MS,
                easing = LinearOutSlowInEasing,
            ),
        )
    val exit = slideOutHorizontally(slide) { -sign * shiftPx } +
        fadeOut(tween(PanelMotion.TEXT_FADE_OUT_MS, easing = FastOutLinearInEasing))
    return ContentTransform(
        targetContentEnter = enter,
        initialContentExit = exit,
        sizeTransform = SizeTransform(clip = false),
    )
}

/**
 * Clips at the start edge only. The text slides in from beyond it and must not draw into the
 * closed rail; the bottom stays open because a catalogue plot deliberately runs below the panel.
 */
private fun Modifier.startEdgeClip(): Modifier = drawWithContent {
    val open = PanelMotion.OPEN_CLIP_EDGE_PX
    val rtl = layoutDirection == LayoutDirection.Rtl
    clipRect(
        left = if (rtl) -open else 0F,
        top = -open,
        right = if (rtl) size.width else open,
        bottom = open,
    ) {
        this@drawWithContent.drawContent()
    }
}

@Composable
private fun VideoDetailsDescriptionBody(
    state: VideoDetailsUIState,
    descriptionMaxLines: Int,
    layout: DescriptionLayout,
    factsStyle: TextStyle,
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
                    .override(size = layout.plotSize, lineHeight = layout.plotLineHeight),
                overflow = TextOverflow.Ellipsis,
                maxLines = descriptionMaxLines,
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoDetailsPoster(
    modifier: Modifier,
    imageUrl: String,
    imageFallbackUrls: List<String>,
    isLoading: Boolean = false,
    trailerUrl: String? = null,
    onTrailerFinished: () -> Unit = {},
    fullBleed: Boolean = false,
    scrim: MediaScrim = MediaScrim.Catalogue,
    scaleTrailerToFit: Boolean = false,
    alignMediaToTop: Boolean = false,
    direction: PanelDirection = PanelDirection.None,
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
        val heldUrls = rememberHeldUrls(imageUrls, isLoading)
        var urlIndex by remember(heldUrls) { mutableIntStateOf(0) }
        val currentUrl = heldUrls.getOrNull(urlIndex)
        val transition = rememberPosterTransition(heldUrls, direction)
        // A card change is moved by the transition below; Coil's own fade would blend twice. The
        // first image and a fallback URL have nothing to move from and keep it.
        val posterRequest = rememberPosterRequest(
            imageUrl = currentUrl,
            crossfade = transition.crossfades(urlIndex),
        )

        var trailerRendered by remember(trailerUrl) { mutableStateOf(false) }

        val hasTrailer = trailerUrl != null
        val fade = animateFloatAsState(
            targetValue = posterFadeTarget(hasTrailer, trailerRendered),
            animationSpec = if (hasTrailer && trailerRendered) {
                tween(POSTER_FADE_OUT_MS, easing = FastOutSlowInEasing)
            } else {
                snap()
            },
            label = "posterFade",
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
        }

        // The player is a `SurfaceView`: it punches a transparent hole through everything the
        // window drew before it, and media3 fills that hole with a black shutter until the first
        // frame arrives. So the still is drawn after the player. It is the same composable before,
        // during and after a trailer, and rests at alpha 0 over a rendered one, so a fitted trailer
        // is not framed by poster pixels. The 48 dp gradients below stay visible over a playing
        // trailer for the same reason.
        val mediaAlignment = if (alignMediaToTop) Alignment.TopCenter else Alignment.Center
        val record = transition.record
        transition.layers.forEach {
            OutgoingPoster(it, mediaAlignment, visible = !(hasTrailer && trailerRendered))
        }
        PosterStill(
            request = posterRequest,
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = posterStillAlpha(hasTrailer, trailerRendered, fade.value) *
                        cardEnterAlpha(hasTrailer, trailerRendered, record?.stillAlpha?.value ?: 1F)
                    translationX = record?.stillOffset?.value ?: 0F
                },
            alignment = mediaAlignment,
            onSuccess = { result ->
                record?.settle()
                transition.shownImage = result.image
            },
            onError = {
                if (urlIndex < heldUrls.lastIndex) {
                    urlIndex++
                } else {
                    // The card has no picture to show: end the hold now and forget the old one, so
                    // a later change does not slide out a poster that is no longer on screen.
                    record?.settle()
                    transition.shownImage = null
                }
            },
        )

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

/**
 * Built once per URL rather than per recomposition. The still is redrawn on every focus move,
 * trailer tick and description swap on the details screen, and a fresh [ImageRequest] each time is
 * a fresh Coil request each time: the same picture re-fetched instead of read from memory.
 *
 * The request carries the previous image as its placeholder so a card change never shows a
 * skeleton. With [crossfade] it also fades from the old poster by itself, which is what the first
 * image and a fallback URL do; a card change turns it off because the panel's own transition
 * (the outgoing layer and the still's enter) does the fade and the shift.
 */
@OptIn(ExperimentalCoilApi::class)
@Composable
private fun rememberPosterRequest(imageUrl: String?, crossfade: Boolean): ImageRequest? {
    val context = LocalContext.current
    return remember(imageUrl, context, crossfade) { posterRequest(context, imageUrl, crossfade) }
}

@OptIn(ExperimentalCoilApi::class)
internal fun posterRequest(context: Context, imageUrl: String?, crossfade: Boolean = true): ImageRequest? =
    imageUrl
        ?.takeIf { it.isNotEmpty() }
        ?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .useExistingImageAsPlaceholder(true)
                .transitionFactory(if (crossfade) PosterCrossfade else Transition.Factory.NONE)
                .build()
        }

@Composable
private fun PosterStill(
    request: ImageRequest?,
    modifier: Modifier,
    alignment: Alignment = Alignment.Center,
    onSuccess: (SuccessResult) -> Unit = {},
    onError: () -> Unit = {},
) {
    SkeletonAsyncImage(
        // The outer skeleton covers having no URL at all; the component's own covers the wait for
        // a URL that is still arriving, which is what an incomplete poster set looks like here.
        modifier = modifier.placeholder(visible = request == null),
        model = request,
        onSuccess = onSuccess,
        onError = onError,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        alignment = alignment,
        skeletonOnlyUntilFirstImage = POSTER_SKELETON_ONLY_UNTIL_FIRST_IMAGE,
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

/**
 * While the next card's details load, `Loading` carries no poster URL. Keeping the previous list
 * (the same instance, so the URL index and the request survive) keeps the old poster on screen
 * instead of dropping to the skeleton. [holdExpired] bounds the wait: a details request that
 * never answers must not leave the old card's poster beside the new card's skeleton text.
 */
internal fun heldPosterUrls(
    previous: List<String>?,
    current: List<String>,
    isLoading: Boolean,
    holdExpired: Boolean = false,
): List<String> {
    val holding = isLoading && current.isEmpty() && !holdExpired
    return if (holding && previous != null) previous else current
}

/**
 * While the next card's details load, the previous card's text stays instead of dropping to the
 * skeleton; [holdExpired] bounds the wait, like [heldPosterUrls] does for the poster.
 */
internal fun heldDescription(
    previous: VideoDetailsUIState?,
    current: VideoDetailsUIState,
    holdExpired: Boolean = false,
): VideoDetailsUIState =
    previous?.takeIf { current.isLoading && !it.isLoading && !holdExpired } ?: current

private class DescriptionHolder {
    var last: VideoDetailsUIState? = null
}

@Composable
private fun rememberHeldDescription(state: VideoDetailsUIState): VideoDetailsUIState {
    val holder = remember { DescriptionHolder() }
    val holding = state.isLoading
    var holdExpired by remember(holding) { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (holding) {
            delay(POSTER_HOLD_MS)
            holdExpired = true
        }
    }
    val held = heldDescription(holder.last, state, holdExpired)
    SideEffect { holder.last = held }
    return held
}

/**
 * Coil's own `crossfade(true)` factory returns no transition for a memory-cache hit, so a warm
 * change between two prefetched posters would cut. This one fades every successful result.
 */
internal object PosterCrossfade : Transition.Factory {
    override fun create(target: TransitionTarget, result: ImageResult): Transition =
        if (result is SuccessResult) {
            CrossfadeTransition(target, result, POSTER_CROSSFADE_MS)
        } else {
            Transition.Factory.NONE.create(target, result)
        }
}

/**
 * One card change of the poster: the picture that was on screen leaves as [outgoingImage] while
 * the still enters from the side the carousel moves toward. The outgoing layer stays opaque under
 * the still until the motion ends: the still is shifted, so it leaves a strip bare at the edge
 * where it comes from, and only an opaque layer under it keeps that strip from showing the panel
 * behind. [settle] flips when the new picture has loaded, or has failed for good; until then the
 * old one stays, up to [POSTER_HOLD_MS]. [outgoingImage] is cleared when the motion ends.
 */
internal class PosterTransition(
    val sign: Int,
    image: coil3.Image,
    outgoingOffsetFrom: Float,
    outgoingAlphaFrom: Float,
    shiftPx: Float = 0F,
) {
    val outgoingOffset = Animatable(outgoingOffsetFrom)
    val outgoingAlpha = Animatable(outgoingAlphaFrom)
    val stillOffset = Animatable(sign * shiftPx)
    val stillAlpha = Animatable(0F)
    var outgoingImage by mutableStateOf<coil3.Image?>(image)
        private set
    var ready by mutableStateOf(false)
        private set
    var job: Job? = null

    fun settle() {
        ready = true
    }

    private suspend fun restOutgoingAlpha() {
        val fade = (PanelMotion.POSTER_FADE_IN_MS * (1F - outgoingAlpha.value)).toInt()
        outgoingAlpha.animateTo(1F, tween(fade, easing = LinearOutSlowInEasing))
    }

    private suspend fun restOutgoingOffset(shiftPx: Float) {
        val travel = if (shiftPx > 0F) (abs(outgoingOffset.value) / shiftPx).coerceAtMost(1F) else 0F
        outgoingOffset.animateTo(
            0F,
            tween((PanelMotion.DURATION_MS * travel).toInt(), easing = FastOutSlowInEasing),
        )
    }

    suspend fun run(shiftPx: Float) {
        try {
            coroutineScope {
                // A layer seeded from a motion in flight comes to opaque while the new picture
                // loads and while it moves, so the strip the shifted still leaves bare stays
                // covered. The offset rests until the picture settles, then the motion takes over.
                launch { restOutgoingAlpha() }
                val settled = withTimeoutOrNull(POSTER_HOLD_MS) {
                    coroutineScope {
                        val resting = launch { restOutgoingOffset(shiftPx) }
                        snapshotFlow { ready }.first { it }.also { resting.cancel() }
                    }
                }
                if (settled == null) {
                    stillOffset.snapTo(0F)
                    stillAlpha.snapTo(1F)
                } else {
                    val shift = tween<Float>(PanelMotion.DURATION_MS, easing = FastOutSlowInEasing)
                    launch { stillOffset.animateTo(0F, shift) }
                    launch {
                        stillAlpha.animateTo(
                            1F,
                            tween(PanelMotion.POSTER_FADE_IN_MS, easing = LinearOutSlowInEasing),
                        )
                    }
                    launch { outgoingOffset.animateTo(-sign * shiftPx, shift) }
                }
            }
        } finally {
            outgoingImage = null
        }
    }
}

/**
 * The outgoing layer's starting offset and alpha: those of the predecessor's still when it is
 * ready and moving, of its outgoing layer when it is still waiting, a settled layer otherwise.
 */
internal fun seedFrom(previous: PosterTransition?): Pair<Float, Float> = when {
    previous == null -> 0F to 1F
    previous.ready -> previous.stillOffset.value to previous.stillAlpha.value
    else -> previous.outgoingOffset.value to previous.outgoingAlpha.value
}

/** The picture being replaced, drawn under the still until its motion is done. */
@Composable
private fun OutgoingPoster(record: PosterTransition, alignment: Alignment, visible: Boolean) {
    val image = record.outgoingImage
    if (!visible || image == null) return
    val context = LocalContext.current
    Image(
        painter = remember(image) { image.asPainter(context) },
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        alignment = alignment,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = record.outgoingOffset.value
                alpha = record.outgoingAlpha.value
            },
    )
}

private class PosterTransitionHolder {
    var shownImage: coil3.Image? = null
    var record: PosterTransition? = null

    /** Transitions whose outgoing layer is still moving; an interrupted one finishes its own. */
    val active = mutableStateListOf<PosterTransition>()
}

private class PosterTransitionState(
    private val holder: PosterTransitionHolder,
    val record: PosterTransition?,
) {
    /** The outgoing layers to draw, oldest first: those still finishing, then the current one. */
    val layers: List<PosterTransition>
        get() = buildList {
            holder.active.forEach { if (it !== record && it.ready) add(it) }
            record?.let { add(it) }
        }

    /** Only the first URL of a card change is moved by the record; later ones keep Coil's fade. */
    fun crossfades(urlIndex: Int): Boolean = record == null || urlIndex != 0

    var shownImage: coil3.Image?
        get() = holder.shownImage
        set(value) {
            holder.shownImage = value
        }
}

/**
 * Starts a [PosterTransition] when the held URLs change while a picture is already on screen; the
 * first picture, and a card whose poster is still missing, have nothing to move away from. The
 * record is created while composing, in the same pass that swaps the request, so the old picture
 * is never a frame behind the new one.
 */
@Composable
private fun rememberPosterTransition(
    heldUrls: List<String>,
    direction: PanelDirection,
): PosterTransitionState {
    val holder = remember { PosterTransitionHolder() }
    val shiftPx = with(LocalDensity.current) { PanelMotion.POSTER_SHIFT.toPx() }
    val sign = enterSign(direction, LocalLayoutDirection.current)
    val record = remember(heldUrls) {
        val image = holder.shownImage
        if (image == null || heldUrls.isEmpty()) {
            null
        } else {
            Snapshot.withoutReadObservation {
                val previous = holder.record?.takeIf { it.outgoingImage != null }
                val (offset, alpha) = seedFrom(previous)
                PosterTransition(sign, image, offset, alpha, shiftPx)
            }
        }
    }
    SideEffect {
        holder.record = record
        if (heldUrls.isEmpty()) holder.shownImage = null
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(record) {
        // A previous record still waiting for its picture is replaced: the new one carries on from
        // its values. A ready one keeps running in its own job, so its outgoing layer is not cut.
        holder.active.filter { !it.ready || record == null }.forEach {
            it.job?.cancel()
            holder.active.remove(it)
        }
        if (record != null) {
            holder.active.add(record)
            record.job = scope.launch {
                try {
                    record.run(shiftPx)
                } finally {
                    holder.active.remove(record)
                }
            }
        }
    }
    return remember(holder, record) { PosterTransitionState(holder, record) }
}

private class PosterUrlHolder {
    var last: List<String>? = null
}

@Composable
private fun rememberHeldUrls(imageUrls: List<String>, isLoading: Boolean): List<String> {
    val holder = remember { PosterUrlHolder() }
    val holding = isLoading && imageUrls.isEmpty()
    var holdExpired by remember(holding) { mutableStateOf(false) }
    LaunchedEffect(holding) {
        if (holding) {
            delay(POSTER_HOLD_MS)
            holdExpired = true
        }
    }
    val held = heldPosterUrls(holder.last, imageUrls, isLoading, holdExpired)
    SideEffect { holder.last = held }
    return held
}

/** The still rests at 0 only over a trailer that has drawn its first frame; otherwise it is opaque. */
internal fun posterFadeTarget(hasTrailer: Boolean, trailerRendered: Boolean): Float =
    if (hasTrailer && trailerRendered) 0F else 1F

/**
 * The card-change enter fade never dims the still over a rendered trailer: there the still rests
 * at 0 by [posterStillAlpha] and the trailer is what the viewer sees.
 */
internal fun cardEnterAlpha(hasTrailer: Boolean, trailerRendered: Boolean, enter: Float): Float =
    if (hasTrailer && trailerRendered) 1F else enter

/**
 * The animated [fade] counts only while a rendered trailer is there. The moment the trailer goes,
 * the still is opaque in the same frame, even though the animation has not left 0 yet.
 */
internal fun posterStillAlpha(hasTrailer: Boolean, trailerRendered: Boolean, fade: Float): Float =
    if (!hasTrailer || !trailerRendered) 1F else fade
