package com.kino.puber.ui.feature.contentlist.model

import com.kino.puber.core.ui.uikit.model.UIAction

internal sealed interface ContentListAction : UIAction {
    data class ShowAll(val config: SectionConfig) : ContentListAction
    data class GenreSelected(val genreId: Int?) : ContentListAction
    data class HeroSelected(val itemId: Int) : ContentListAction
    data object TrailerPreviewFinished : ContentListAction

    /**
     * Focus has left the card rows — into the hero carousel, the side rail, or nowhere at all
     * because the screen is being disposed. Distinct from [TrailerPreviewFinished], which is the
     * player reporting that it stopped on its own.
     */
    data object TrailerPreviewStopped : ContentListAction
}
