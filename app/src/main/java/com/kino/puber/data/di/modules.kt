@file:OptIn(UnstableApi::class)

package com.kino.puber.data.di

import android.net.ConnectivityManager
import androidx.annotation.OptIn
import com.kino.puber.core.session.SessionEventBus
import com.kino.puber.core.system.ContentSharer
import com.kino.puber.data.api.KinoPubApiClient
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.repository.AppUpdateDownloader
import com.kino.puber.data.repository.AppUpdateInstaller
import com.kino.puber.data.repository.AppUpdatePreferencesRepository
import com.kino.puber.data.repository.AppUpdateRepository
import com.kino.puber.data.db.PuberDatabase
import com.kino.puber.data.db.transactions
import com.kino.puber.data.repository.CryptoPreferenceRepository
import com.kino.puber.data.repository.DeviceInfoRepository
import com.kino.puber.data.repository.DeviceSettingsRepository
import com.kino.puber.data.repository.EpisodeScheduleRepository
import com.kino.puber.data.repository.IAppUpdateRepository
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.IDeviceInfoRepository
import com.kino.puber.data.repository.IDeviceSettingsRepository
import com.kino.puber.data.repository.IKinoPubRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.KinoPubRepository
import com.kino.puber.data.cache.ContentCacheRepository
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.data.repository.PlayerPreferencesRepository
import com.kino.puber.data.repository.RoomPersistentPayloadStore
import com.kino.puber.data.repository.SkipSegmentRepository
import com.kino.puber.data.repository.SkipSegmentService
import com.kino.puber.data.repository.TmdbIdRepository
import com.kino.puber.data.repository.WatchStateRepository
import com.kino.puber.data.preferences.AppLanguageRepository
import com.kino.puber.data.preferences.NavigationPreferencesRepository
import com.kino.puber.data.api.IntroDbAppApiClient
import com.kino.puber.data.api.TheIntroDbApiClient
import com.kino.puber.data.api.TmdbApiClient
import okhttp3.OkHttpClient
import com.kino.puber.data.api.network.DnsOverHttpsFactory
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.database.StandaloneDatabaseProvider

val apiModule = module {
    single { SessionEventBus() }
    // Shared: the client is what reports a domain going quiet, the domain interactor is what acts
    // on it. Neither can see the other, so the verdict itself is the thing they hold in common.
    single { EndpointReachability() }
    single {
        OkHttpClient.Builder()
            .dns(DnsOverHttpsFactory.create())
            .build()
    }
    single {
        KinoPubApiClient(
            okHttpClient = get(),
            cacheDir = androidContext().cacheDir,
            connectivityManager = androidContext().getSystemService(ConnectivityManager::class.java),
            cryptoPreferenceRepository = get(),
            sessionEventBus = get(),
            reachability = get(),
        )
    }
    singleOf(::TmdbApiClient)
    singleOf(::TheIntroDbApiClient)
    singleOf(::IntroDbAppApiClient)
}

private const val MIB = 1024L * 1024L
private const val MEDIA_CACHE_SIZE_BYTES = 512L * MIB

val repositoryModule = module {
    single { ContentSharer(androidContext()) }
    singleOf(::AppUpdateRepository) { bind<IAppUpdateRepository>() }
    singleOf(::AppUpdatePreferencesRepository)
    singleOf(::AppUpdateInstaller)
    singleOf(::AppUpdateDownloader)
    singleOf(::KinoPubRepository) { bind<IKinoPubRepository>() }
    singleOf(::CryptoPreferenceRepository) { bind<ICryptoPreferenceRepository>() }
    singleOf(::DeviceInfoRepository) { bind<IDeviceInfoRepository>() }
    singleOf(::DeviceSettingsRepository) { bind<IDeviceSettingsRepository>() }
    single { ContentCacheRepository(store = get()) }
    single { ItemDetailsRepository(api = get(), watchStateRepository = get(), contentCache = get()) }
    single {
        EpisodeScheduleRepository(
            tmdbApiClient = get(),
        )
    }
    singleOf(::PlayerPreferencesRepository)
    singleOf(::TmdbIdRepository)
    singleOf(::SkipSegmentRepository)
    singleOf(::SkipSegmentService)
    singleOf(::NavigationPreferencesRepository)
    singleOf(::AppLanguageRepository)
    single { PuberDatabase.create(androidContext()) }
    single { get<PuberDatabase>().watchStateDao() }
    single { get<PuberDatabase>().watchStateSyncDao() }
    single { get<PuberDatabase>().cachedPayloadDao() }
    single<PersistentPayloadStore> { RoomPersistentPayloadStore(dao = get()) }
    single { get<PuberDatabase>().transactions() }
    single { WatchStateRepository(dao = get(), syncDao = get(), transaction = get()) }
    single<androidx.media3.datasource.cache.Cache> {
        val cacheDir = java.io.File(androidContext().externalCacheDir ?: androidContext().cacheDir, "media_cache")
        SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(MEDIA_CACHE_SIZE_BYTES),
            StandaloneDatabaseProvider(androidContext())
        )
    }
}
