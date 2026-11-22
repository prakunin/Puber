package com.kino.puber

import android.app.Application
import android.content.Context
import android.os.SystemClock
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.util.DebugLogger
import com.kino.puber.core.error.DefaultErrorHandler
import com.kino.puber.core.image.HttpsEnforcingInterceptor
import com.kino.puber.core.error.ErrorHandler
import com.kino.puber.core.contentlink.ContentLaunchCoordinator
import com.kino.puber.core.contentlink.ContentUriCodec
import com.kino.puber.core.logger.LinkingDebugTree
import com.kino.puber.core.startup.AppStartup
import com.kino.puber.core.startup.StartupReveal
import com.kino.puber.core.startup.reportingToStartupReveal
import com.kino.puber.core.system.AndroidResourceProvider
import com.kino.puber.core.system.AppLocale
import com.kino.puber.core.system.ResourceProvider
import com.kino.puber.core.telemetry.TelemetryRuntime
import com.kino.puber.core.telemetry.di.telemetryModule
import com.kino.puber.core.tvhome.ContinueWatchingSource
import com.kino.puber.core.tvhome.TvHomePublisher
import com.kino.puber.core.tvhome.TvHomePublisherFactory
import com.kino.puber.core.tvhome.TvHomeSyncCoordinator
import com.kino.puber.data.cache.CachedPayloadSweeper
import com.kino.puber.data.di.ImageHttpClientQualifier
import com.kino.puber.data.di.apiModule
import com.kino.puber.data.di.repositoryModule
import com.kino.puber.domain.di.interactorModule
import com.kino.puber.domain.interactor.api.ApiDomainInteractor
import io.ktor.client.HttpClient
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.dsl.module
import timber.log.Timber

private val resourceModule = module {
    single<ResourceProvider> { AndroidResourceProvider(get()) }
    singleOf(::ContentUriCodec)
    singleOf(::ContentLaunchCoordinator)
    single { ContinueWatchingSource(get()) }
    single<TvHomePublisher> { TvHomePublisherFactory.create(androidContext(), get()) }
    single { TvHomeSyncCoordinator(get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.IO)) }
}

private val handlersModule = module {
    singleOf(::DefaultErrorHandler) { bind<ErrorHandler>() }
}

private const val IMAGE_MEMORY_CACHE_BYTES = 48L * 1024 * 1024
private const val IMAGE_DISK_CACHE_BYTES = 100L * 1024 * 1024

class PuberApp : Application(), SingletonImageLoader.Factory {

    // Runs before onCreate, and therefore before Koin exists, which is why the chosen language is
    // read straight from preferences here. Wrapping the application itself is what keeps strings
    // resolved outside a composition in the same language as the screen.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        val appCreateMs = SystemClock.elapsedRealtime()
        super.onCreate()

        TelemetryRuntime.install(this, appCreateMs)
        StartupReveal.installFrameWait()
        initDi()
        initLogger()
    }

    private fun initDi() {
        val koinApplication = startKoin {
            androidContext(this@PuberApp)
            modules(
                resourceModule,
                handlersModule,
                apiModule,
                repositoryModule,
                interactorModule,
                telemetryModule,
            )
        }
        koinApplication.koin.get<AppStartup>().start()
        koinApplication.koin.get<CachedPayloadSweeper>().start()
    }

    private fun initLogger() {
        if (BuildConfig.DEBUG) {
            Timber.plant(LinkingDebugTree())
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(this)
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .crossfade(false)
            .networkCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(IMAGE_MEMORY_CACHE_BYTES)
                    .build()
                    .also { cache ->
                        if (BuildConfig.DEBUG) Timber.d("Coil memory cache maxSize=%d", cache.maxSize)
                    }
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath(normalize = true))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            .components {
                add(HttpsEnforcingInterceptor())
                add(
                    KtorNetworkFetcherFactory(
                        httpClient = {
                            get<HttpClient>(ImageHttpClientQualifier)
                        },
                    ),
                )
            }
            .reportingToStartupReveal()
            .build()
    }
}
