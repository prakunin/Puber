package com.kino.puber.core.ui.uikit.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.delay

private const val HERO_AUTO_SCROLL_DELAY_MS = 5_000L
private const val INDICATOR_CORNER_PERCENT = 50
private const val KEN_BURNS_DURATION_MS = 10_000
private const val KEN_BURNS_DRIFT_PX = 20f

/**
 * Tells the caller which title the carousel is showing while it holds focus.
 *
 * Keyed on [items] as well as the page: a refresh can replace what this page shows without moving
 * either the page or the focus, and a caller that heard nothing would go on holding the title that
 * used to be here — and open it when the user pressed Select.
 */
@Composable
private fun ReportFocusedHeroEffect(
    items: List<HeroItemState>,
    pagerState: PagerState,
    isFocused: Boolean,
    onFocusedItemChanged: (Int) -> Unit,
) {
    LaunchedEffect(items, pagerState.currentPage, isFocused) {
        if (!isFocused) return@LaunchedEffect
        items.getOrNull(pagerState.currentPage)?.let { onFocusedItemChanged(it.id) }
    }
}

@Composable
fun HeroCarousel(
    items: List<HeroItemState>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onFocusedItemChanged: (Int) -> Unit = {},
) {
    if (items.isEmpty()) return

    if (items.size == 1) {
        val item = items.first()
        HeroItem(
            state = item,
            onClick = { onItemClick(item.id) },
            modifier = modifier
                .fillMaxWidth()
                .height(280.dp)
                .onSelectKeyClick { onItemClick(item.id) }
                .onFocusChanged { if (it.hasFocus) onFocusedItemChanged(item.id) },
        )
        return
    }

    val pagerState = rememberPagerState(pageCount = { items.size })
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState, isFocused) {
        if (!isFocused) {
            while (true) {
                delay(HERO_AUTO_SCROLL_DELAY_MS)
                val next = (pagerState.currentPage + 1) % items.size
                pagerState.animateScrollToPage(next, animationSpec = tween(500))
            }
        }
    }
    ReportFocusedHeroEffect(items, pagerState, isFocused, onFocusedItemChanged)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .onSelectKeyClick { onItemClick(items[pagerState.currentPage].id) }
            .onFocusChanged { focusState ->
                isFocused = focusState.hasFocus
                if (focusState.hasFocus) {
                    onFocusedItemChanged(items[pagerState.currentPage].id)
                }
            }
            .focusGroup(),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]
            HeroItem(
                state = item,
                onClick = { onItemClick(item.id) },
                modifier = Modifier.fillMaxSize(),
                animate = page == pagerState.currentPage,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .height(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { index ->
                val isSelected = index == pagerState.currentPage
                val width by animateDpAsState(
                    targetValue = when {
                        isFocused && isSelected -> 24.dp
                        else -> 8.dp
                    },
                    label = "indicatorWidth",
                )
                val height by animateDpAsState(
                    targetValue = when {
                        isFocused && isSelected -> 8.dp
                        isFocused -> 6.dp
                        else -> 6.dp
                    },
                    label = "indicatorHeight",
                )
                val color by animateColorAsState(
                    targetValue = when {
                        isFocused && isSelected -> MaterialTheme.colorScheme.primary
                        isFocused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    },
                    label = "indicatorColor",
                )
                Box(
                    modifier = Modifier
                        .width(width)
                        .height(height)
                        .background(color = color, shape = RoundedCornerShape(INDICATOR_CORNER_PERCENT)),
                )
            }
        }
    }
}

private fun Modifier.onSelectKeyClick(onClick: () -> Unit): Modifier {
    return onPreviewKeyEvent { event ->
        if (!event.key.isSelectKey()) {
            return@onPreviewKeyEvent false
        }
        when (event.type) {
            KeyEventType.KeyDown -> true
            KeyEventType.KeyUp -> {
                onClick()
                true
            }
            else -> false
        }
    }
}

private fun Key.isSelectKey(): Boolean {
    return this == Key.DirectionCenter || this == Key.Enter
}

/** The Ken Burns drift, or a still frame when this page is not the one being looked at. */
@Immutable
private data class KenBurnsTransform(val scale: Float, val translateX: Float)

/**
 * The pager keeps neighbouring pages composed, and an infinite transition runs for as long as it is
 * composed — so without this every hero page drifts at once, forever, and the ones off screen pay
 * the same per-frame invalidation as the visible one for nothing. On the weaker TV boxes that is
 * continuous GPU work behind a still picture.
 */
@Composable
private fun kenBurns(animate: Boolean, driftDirection: Float): KenBurnsTransform {
    if (!animate) return KenBurnsTransform(scale = 1f, translateX = 0f)

    val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = KEN_BURNS_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsScale",
    )
    val translateX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = KEN_BURNS_DRIFT_PX * driftDirection,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = KEN_BURNS_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurnsTranslateX",
    )
    return KenBurnsTransform(scale = scale, translateX = translateX)
}

@Composable
private fun HeroItem(
    state: HeroItemState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(pressedScale = 1f, focusedScale = 1f),
        border = CardDefaults.border(focusedBorder = Border.None, pressedBorder = Border.None),
        shape = CardDefaults.shape(RectangleShape),
    ) {
        Box(Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val urls = remember(state.id, state.wideImageUrl, state.fallbackImageUrl, state.fallbackImageUrls) {
                (listOf(state.wideImageUrl, state.fallbackImageUrl) + state.fallbackImageUrls)
                    .filter { it.isNotEmpty() }
                    .distinct()
            }
            var urlIndex by remember(state.id, urls) { mutableIntStateOf(0) }
            val currentUrl = urls.getOrNull(urlIndex)

            val imageRequest = remember(currentUrl) {
                currentUrl?.let {
                    ImageRequest.Builder(context)
                        .data(it)
                        .crossfade(true)
                        .build()
                }
            }
            val driftDirection = remember(state.id) { if ((state.id % 2) == 0) 1f else -1f }
            val (scale, translateX) = kenBurns(animate = animate, driftDirection = driftDirection)
            SkeletonAsyncImage(
                model = imageRequest,
                onError = { if (urlIndex < urls.lastIndex) urlIndex++ },
                contentDescription = null,
                alignment = Alignment.TopCenter,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = translateX
                    },
            )

            val scrimColor = MaterialTheme.colorScheme.scrim
            val gradientBrush = remember(scrimColor) {
                Brush.verticalGradient(
                    colors = listOf(
                        scrimColor.copy(alpha = 0.1f),
                        scrimColor.copy(alpha = 0.85f),
                    ),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(gradientBrush)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Bottom,
            ) {
                if (state.ratings.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.ratings.forEach { rating ->
                            Rating(rating)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                val infoLine = listOf(state.year, state.genres, state.country)
                    .filter { it.isNotEmpty() }
                    .joinToString(", ")
                if (infoLine.isNotEmpty()) {
                    Text(
                        text = infoLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (state.duration.isNotEmpty()) {
                    Text(
                        text = state.duration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
