package com.kino.puber.ui.feature.showall.content

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kino.puber.R
import com.kino.puber.core.ui.uikit.component.TvCompactButton
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.core.ui.uikit.theme.SectionTitleStyle
import com.kino.puber.ui.feature.showall.model.ShowAllAction
import com.kino.puber.ui.feature.showall.model.ShowAllFilterState
import com.kino.puber.ui.feature.showall.model.ShowAllTitle

private val HeaderPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp)
private val HeaderRowHeight = 24.dp
private val TitleToFiltersGap = 16.dp
private val FiltersToResetGap = 8.dp
private val ResetToSummaryGap = 12.dp

/**
 * Names what the grid shows, and on a browse grid carries the filter buttons: «Фильтры», and while
 * a filter is applied «Сброс» and a summary of it. One 24 dp row either way, so the first row of
 * cards sits at the same height on every grid. The title is not focusable: first focus stays on the
 * first card.
 *
 * [downTarget] is where Down leaves the buttons for, and [canFocus] keeps «Фильтры» from taking the
 * first focus of a grid that is still loading.
 */
@Composable
internal fun ShowAllHeader(
    title: String,
    modifier: Modifier = Modifier,
    filter: ShowAllFilterState = ShowAllFilterState.Unavailable,
    filtersFocus: FocusRequester = remember { FocusRequester() },
    canFocus: Boolean = true,
    downTarget: FocusRequester = FocusRequester.Default,
    onAction: (UIAction) -> Unit = {},
) {
    Row(
        modifier = modifier
            .padding(HeaderPadding)
            .height(HeaderRowHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = SectionTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (filter.available) {
            val resetFocus = remember { FocusRequester() }
            Spacer(Modifier.width(TitleToFiltersGap))
            TvCompactButton(
                text = stringResource(R.string.show_all_filters),
                onClick = { onAction(ShowAllAction.FilterPanelOpened) },
                modifier = Modifier
                    .focusRequester(filtersFocus)
                    .focusProperties {
                        this.canFocus = canFocus
                        up = FocusRequester.Cancel
                        down = downTarget
                        left = FocusRequester.Default
                        right = if (filter.isActive) resetFocus else FocusRequester.Cancel
                    },
            )
            if (filter.isActive) {
                Spacer(Modifier.width(FiltersToResetGap))
                TvCompactButton(
                    text = stringResource(R.string.show_all_filters_reset),
                    onClick = {
                        runCatching { filtersFocus.requestFocus() }
                        onAction(ShowAllAction.FilterReset)
                    },
                    modifier = Modifier
                        .focusRequester(resetFocus)
                        .focusProperties {
                            up = FocusRequester.Cancel
                            down = downTarget
                            left = filtersFocus
                            right = FocusRequester.Cancel
                        },
                    outlined = true,
                )
                Spacer(Modifier.width(ResetToSummaryGap))
                Text(
                    text = filterSummary(filter),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        }
    }
}

@Composable
internal fun ShowAllTitle.resolve(): String {
    val base = titleRes?.let { stringResource(it) } ?: title
    return prefixRes?.let { stringResource(R.string.show_all_title_shelf, stringResource(it), base) } ?: base
}
