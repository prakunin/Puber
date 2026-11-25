package com.kino.puber.core.ui.uikit.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text

private val CompactButtonHeight = 24.dp
private val CompactButtonHorizontalPadding = 12.dp

/**
 * A 24 dp text button for the strip above a grid or a shelf: «browse all», «filters», «reset».
 * [outlined] is the secondary look for a button that sits beside a filled one.
 */
@Composable
internal fun TvCompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
) {
    val sized = modifier.height(CompactButtonHeight)
    val padding = PaddingValues(horizontal = CompactButtonHorizontalPadding)
    if (outlined) {
        OutlinedButton(onClick = onClick, modifier = sized, contentPadding = padding) { CompactButtonLabel(text) }
    } else {
        Button(onClick = onClick, modifier = sized, contentPadding = padding) { CompactButtonLabel(text) }
    }
}

@Composable
private fun CompactButtonLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
