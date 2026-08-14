package com.kino.puber.domain.interactor.watchstate

import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.repository.WatchStateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * Emits when a card mapped a moment ago would come out different now: the watch-state index moved,
 * or a setting that decides what a card shows was flipped.
 *
 * Screens that map their items once and hold the result need telling. Moving between screens does
 * not pause the activity, so a screen covered by the settings screen sees no resume when it comes
 * back, and the first sync of a cold start lands long after the screen under it finished loading.
 */
class CardDisplayChanges(
    watchStateRepository: WatchStateRepository,
    navigationPreferencesRepository: NavigationPreferencesRepository,
) {

    val changes: Flow<Unit> = merge(
        watchStateRepository.settledChanges.map { },
        navigationPreferencesRepository.displaySettingsChanges,
    )
}
