package com.kino.puber.ui.feature.root.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kino.puber.BuildConfig
import com.kino.puber.R

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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BasicText(
                text = stringResource(R.string.app_name),
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 48.sp,
                    letterSpacing = 8.sp,
                    color = colorResource(R.color.splash_title),
                    textAlign = TextAlign.Center,
                ),
            )
            BasicText(
                text = stringResource(R.string.splash_version, BuildConfig.VERSION_NAME),
                style = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontSize = 16.sp,
                    letterSpacing = 2.sp,
                    color = colorResource(R.color.splash_version),
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
