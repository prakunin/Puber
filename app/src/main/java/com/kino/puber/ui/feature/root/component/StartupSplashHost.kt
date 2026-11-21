package com.kino.puber.ui.feature.root.component

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag

internal const val STARTUP_SPLASH_TAG = "startup_splash"
internal const val REVEAL_FADE_MS = 250

/**
 * Draws [content] and, above it, the splash while [covered]. The content is composed and laid out
 * underneath from the first frame, so it loads and claims its launch focus behind the wordmark; the
 * splash fades out when [covered] turns false and leaves the composition once it is gone. It is not
 * focusable and holds no focus requester, so it cannot take focus from what is underneath.
 */
@Composable
internal fun StartupSplashHost(covered: Boolean, content: @Composable () -> Unit) {
    var splashGone by remember { mutableStateOf(!covered) }
    Box(Modifier.fillMaxSize()) {
        content()
        if (!splashGone) {
            val alpha = animateFloatAsState(
                targetValue = if (covered) 1f else 0f,
                animationSpec = tween(REVEAL_FADE_MS, easing = FastOutLinearInEasing),
                label = "startupSplashAlpha",
                finishedListener = { if (it == 0f) splashGone = true },
            )
            SplashContent(Modifier.testTag(STARTUP_SPLASH_TAG).graphicsLayer { this.alpha = alpha.value })
        }
    }
}
