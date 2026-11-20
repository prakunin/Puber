package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import coil3.request.SuccessResult
import com.kino.puber.core.ui.uikit.component.modifier.placeholder

@Composable
fun SkeletonAsyncImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    onSuccess: (SuccessResult) -> Unit = {},
    onError: () -> Unit = {},
    skeletonOnlyUntilFirstImage: Boolean = false,
) {
    var isLoading by remember(model) { mutableStateOf(model != null) }
    var hasShownImage by remember(model != null) { mutableStateOf(false) }
    var isFailed by remember(model) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32F)),
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = contentScale,
                alignment = alignment,
                onLoading = {
                    isLoading = true
                    isFailed = false
                },
                onSuccess = {
                    isLoading = false
                    hasShownImage = true
                    isFailed = false
                    onSuccess(it.result)
                },
                onError = {
                    isLoading = false
                    isFailed = true
                    onError()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isFailed) 0F else 1F),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .placeholder(visible = skeletonVisible(isLoading, skeletonOnlyUntilFirstImage, hasShownImage)),
        )
    }
}

/**
 * With [skeletonOnlyUntilFirstImage] the skeleton marks the wait for the first picture only; later
 * loads keep the previous picture on screen instead of flashing the skeleton over it.
 */
internal fun skeletonVisible(
    isLoading: Boolean,
    skeletonOnlyUntilFirstImage: Boolean,
    hasShownImage: Boolean,
): Boolean = isLoading && !(skeletonOnlyUntilFirstImage && hasShownImage)
