package com.kino.puber.ui.feature.root.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices.TV_1080p
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kino.puber.BuildConfig
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.theme.PuberTheme

private const val KINOPUB_WORDMARK_ASPECT_RATIO = 640f / 169f

/**
 * The app wordmark shown while the app is still starting up.
 *
 * Deliberately built from foundation composables only — no Material theme, no DI, no view models —
 * so it can be drawn in the very first frame, before the heavy [App] tree is composed.
 */
@Composable
internal fun SplashContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.splash_background)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.kinopub_wordmark),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .width(320.dp)
                    .aspectRatio(KINOPUB_WORDMARK_ASPECT_RATIO),
            )
            BasicText(
                text = stringResource(R.string.splash_signature),
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 22.sp,
                    letterSpacing = 4.sp,
                    color = colorResource(R.color.splash_signature),
                    textAlign = TextAlign.Center,
                ),
            )
            BasicText(
                text = BuildConfig.VERSION_NAME,
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 9.sp,
                    letterSpacing = 1.sp,
                    color = colorResource(R.color.splash_signature),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}

@Preview(device = TV_1080p, showBackground = true)
@Composable
private fun SplashContentPreview() = PuberTheme {
    SplashContent()
}
