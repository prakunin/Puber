package com.kino.puber.ui.feature.showall.content

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.theme.SectionTitleStyle
import com.kino.puber.ui.feature.showall.model.ShowAllTitle

private val HeaderPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp)

/** Names what the grid shows. Not focusable: first focus stays on the first card. */
@Composable
internal fun ShowAllHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = SectionTitleStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(HeaderPadding),
    )
}

@Composable
internal fun ShowAllTitle.resolve(): String {
    val base = titleRes?.let { stringResource(it) } ?: title
    return prefixRes?.let { stringResource(R.string.show_all_title_shelf, stringResource(it), base) } ?: base
}
