package com.kino.puber.ui.feature.main.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.kino.puber.domain.model.TabType

@Immutable
internal data class MainViewState(
    val tabs: List<MainTab> = emptyList(),
    val selectedTab: TabType = TabType.Home,
)


@Immutable
internal data class MainTab(
    val type: TabType,
    val icon: ImageVector,
    val isSelected: Boolean = false,
    val isVisible: Boolean = false,
)
