package com.kino.puber.ui.feature.player.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kino.puber.core.ui.uikit.component.moviesList.VideoItemUIState
import com.kino.puber.core.ui.uikit.component.EpisodeContextMenuDialog
import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.ui.feature.player.model.ActivePanel
import com.kino.puber.ui.feature.player.model.PlayerAction
import com.kino.puber.ui.feature.player.model.PlayerContentState

@Composable
internal fun PlayerSettingsPanels(
    content: PlayerContentState,
    onAction: (UIAction) -> Unit,
) {
    var episodeContextMenuItem by remember { mutableStateOf<VideoItemUIState?>(null) }

    // Audio, video and stream diagnostics are one panel behind one gear; the doors are what
    // survived of the three buttons that used to open it.
    PlayerSettingsPanel(
        content = content,
        onAction = onAction,
    )

    PlayerAboutPanel(
        visible = content.activePanel == ActivePanel.About,
        isMovie = content.isMovie,
        about = content.about,
    )

    PlayerEpisodesPanel(
        visible = content.activePanel == ActivePanel.Episodes,
        episodes = content.episodes,
        initialFocusedItemId = content.currentEpisodeId,
        onEpisodeSelected = { item -> onAction(PlayerAction.SelectEpisodeById(item.id)) },
        onEpisodeContextMenu = { episodeContextMenuItem = it },
        onDismiss = rememberAction(onAction, PlayerAction.ClosePanel),
        allowFocusExit = episodeContextMenuItem != null,
    )

    EpisodeContextMenuDialog(
        episode = episodeContextMenuItem,
        onDismiss = { episodeContextMenuItem = null },
        onPlay = { onAction(PlayerAction.SelectEpisodeById(it.id)) },
        onMarkEpisodeWatched = { item, watched -> onAction(PlayerAction.EpisodeWatchedChanged(item, watched)) },
        onMarkSeasonWatched = { item, watched -> onAction(PlayerAction.SeasonWatchedChanged(item, watched)) },
    )
}
