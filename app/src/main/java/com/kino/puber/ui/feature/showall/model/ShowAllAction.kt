package com.kino.puber.ui.feature.showall.model

import com.kino.puber.core.ui.uikit.model.UIAction
import com.kino.puber.data.api.models.Genre
import com.kino.puber.domain.model.SortDirection
import com.kino.puber.domain.model.SortField

internal sealed interface ShowAllAction : UIAction {
    data object FilterPanelOpened : ShowAllAction

    data object FilterPanelClosed : ShowAllAction

    data class SortFieldChosen(val field: SortField) : ShowAllAction

    data class SortDirectionChosen(val direction: SortDirection) : ShowAllAction

    data class GenreChosen(val genre: Genre?) : ShowAllAction

    data object GenresRetry : ShowAllAction

    data object FilterReset : ShowAllAction
}
