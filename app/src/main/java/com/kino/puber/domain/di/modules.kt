package com.kino.puber.domain.di

import com.kino.puber.core.lifecycle.AppForegroundState
import com.kino.puber.data.api.network.HttpEndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import com.kino.puber.domain.interactor.auth.AuthInteractor
import com.kino.puber.domain.interactor.auth.IAuthInteractor
import com.kino.puber.domain.interactor.bookmarks.BookmarkFoldersInteractor
import com.kino.puber.domain.interactor.bookmarks.SavedItemInteractor
import com.kino.puber.domain.interactor.bookmarks.WatchLaterBookmarkInteractor
import com.kino.puber.domain.interactor.device.DeviceInfoInteractor
import com.kino.puber.domain.interactor.device.DeviceSettingInteractor
import com.kino.puber.domain.interactor.device.IDeviceInfoInteractor
import com.kino.puber.domain.interactor.device.IDeviceSettingInteractor
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher
import com.kino.puber.domain.interactor.update.AppUpdateCheckCoordinator
import com.kino.puber.domain.interactor.update.AppUpdateInteractor
import com.kino.puber.domain.interactor.update.IAppUpdateInteractor
import com.kino.puber.domain.interactor.watchstate.CardDisplayChanges
import com.kino.puber.domain.interactor.watchstate.RecentlyPlayedOrder
import com.kino.puber.domain.interactor.watchstate.WatchStateSyncInteractor
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val interactorModule = module {
    singleOf(::AppUpdateCheckCoordinator)
    singleOf(::AppUpdateInteractor) { bind<IAppUpdateInteractor>() }
    singleOf(::AuthInteractor) { bind<IAuthInteractor>() }
    singleOf(::DeviceInfoInteractor) { bind<IDeviceInfoInteractor>() }
    singleOf(::DeviceSettingInteractor) { bind<IDeviceSettingInteractor>() }
    singleOf(::GenreInteractor)
    singleOf(::BookmarkFoldersInteractor)
    singleOf(::WatchLaterBookmarkInteractor)
    singleOf(::SavedItemInteractor)
    single {
        ApiDomainInteractor(
            preferences = get(),
            itemDetailsRepository = get(),
            genreInteractor = get(),
            store = get(),
            probe = HttpEndpointProbe(get()),
            reachability = get(),
            detailsPrefetcher = get(),
        )
    }
    singleOf(::AppForegroundState)
    single {
        WatchStateSyncInteractor(
            api = get(),
            repository = get(),
            awaitForeground = get<AppForegroundState>()::awaitForeground,
        )
    }
    singleOf(::CardDisplayChanges)
    // Shared by the home row and the "I'm watching" tab, which show the same list on two screens
    // with a Koin scope each, so it cannot be scoped to either of them.
    singleOf(::RecentlyPlayedOrder)
    // A single, and deliberately not screen-scoped: focus survives moves between tabs, and a warm
    // already on the network outlives the screen that asked for it.
    single {
        DetailsPrefetcher(
            details = get(),
            foreground = get(),
        )
    }
}
