package com.kino.puber.domain.interactor.api

import com.kino.puber.BuildConfig
import com.kino.puber.core.logger.log
import com.kino.puber.data.api.config.ApiEndpointPreset
import com.kino.puber.data.api.config.KinoPubConfig
import com.kino.puber.data.api.network.EndpointProbe
import com.kino.puber.data.api.network.EndpointReachability
import com.kino.puber.data.repository.ICryptoPreferenceRepository
import com.kino.puber.data.repository.ItemDetailsRepository
import com.kino.puber.data.repository.PersistentPayloadStore
import com.kino.puber.domain.interactor.genre.GenreInteractor
import com.kino.puber.domain.interactor.prefetch.DetailsPrefetcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

internal data class ApiDomainState(
    val domain: String,
    val customDomain: String?,
) {
    val isCustom: Boolean get() = customDomain != null
}

internal sealed interface ApiDomainUpdateResult {
    data class Success(val state: ApiDomainState) : ApiDomainUpdateResult
    data object Empty : ApiDomainUpdateResult
    data object Invalid : ApiDomainUpdateResult
}

internal sealed interface ApiDomainDetectionResult {
    data class Success(val state: ApiDomainState) : ApiDomainDetectionResult
    data object NotFound : ApiDomainDetectionResult
}

internal sealed interface ApiDomainAutoResolveResult {
    data class Success(
        val state: ApiDomainState,
        val changed: Boolean,
    ) : ApiDomainAutoResolveResult

    data object NotFound : ApiDomainAutoResolveResult
}

internal class ApiDomainInteractor(
    private val preferences: ICryptoPreferenceRepository,
    private val itemDetailsRepository: ItemDetailsRepository,
    private val genreInteractor: GenreInteractor,
    private val store: PersistentPayloadStore,
    private val probe: EndpointProbe,
    private val reachability: EndpointReachability,
    private val detailsPrefetcher: DetailsPrefetcher,
) {

    fun initialize() {
        KinoPubConfig.setDomainOverride(
            resolveStartupDomain(
                savedDomain = preferences.getApiDomain(),
                buildDomain = BuildConfig.API_DOMAIN_OVERRIDE,
            )
        )
    }

    fun getState(): ApiDomainState {
        val customDomain = KinoPubConfig.CUSTOM_API_DOMAIN
        return ApiDomainState(
            domain = customDomain ?: KinoPubConfig.DEFAULT_API_DOMAIN,
            customDomain = customDomain,
        )
    }

    suspend fun saveCustomDomain(input: String): ApiDomainUpdateResult {
        val normalized = normalizeDomain(input)
        if (normalized.isEmpty()) return ApiDomainUpdateResult.Empty
        if (!normalized.isValidHostname()) return ApiDomainUpdateResult.Invalid

        preferences.saveApiDomain(normalized)
        KinoPubConfig.setDomainOverride(normalized)
        clearDomainSensitiveCaches()
        return ApiDomainUpdateResult.Success(getState())
    }

    suspend fun detectAndSaveWorkingDomain(): ApiDomainDetectionResult = withContext(Dispatchers.IO) {
        val preset = KinoPubConfig.BUILT_IN_ENDPOINTS.firstOrNull(::isEndpointReachable)
            ?: return@withContext ApiDomainDetectionResult.NotFound

        applyEndpoint(preset)
        ApiDomainDetectionResult.Success(getState())
    }

    suspend fun detectAndSaveAlternativeBuiltInDomain(): ApiDomainDetectionResult = withContext(Dispatchers.IO) {
        val currentEndpoint = KinoPubConfig.CURRENT_ENDPOINT
        val preset = KinoPubConfig.BUILT_IN_ENDPOINTS
            .filterNot { it.domain == currentEndpoint.domain }
            .filterNot { it.oauthBaseUrl == currentEndpoint.oauthBaseUrl }
            .firstOrNull(::isEndpointReachable)
            ?: return@withContext ApiDomainDetectionResult.NotFound

        applyEndpoint(preset)
        ApiDomainDetectionResult.Success(getState())
    }

    suspend fun autoResolveWorkingDomain(): ApiDomainAutoResolveResult = withContext(Dispatchers.IO) {
        val currentDomain = KinoPubConfig.CURRENT_API_DOMAIN
        // The home screen resolves before every load, and the load behind an ON_RESUME is by far the
        // most common one. Re-asking a domain that answered minutes ago costs a full catalogue GET
        // whose body has to be read and parsed, and it sits in front of everything the screen came
        // back to show. The candidate walk below would have picked this same domain first and
        // reported the same `changed = false`, so answering from here is the identical outcome
        // without the request.
        if (isKnownReachable(currentDomain)) {
            return@withContext ApiDomainAutoResolveResult.Success(state = getState(), changed = false)
        }

        val endpoint = buildAutoResolveCandidates().firstOrNull(::isEndpointReachable)
            ?: return@withContext ApiDomainAutoResolveResult.NotFound

        val persistedDomain = endpoint.domain.takeIf { it != KinoPubConfig.DEFAULT_API_DOMAIN }
        val changed = endpoint.domain != currentDomain

        if (changed) {
            preferences.saveApiDomain(persistedDomain)
            KinoPubConfig.setDomainOverride(persistedDomain)
            clearDomainSensitiveCaches()
        }

        ApiDomainAutoResolveResult.Success(
            state = getState(),
            changed = changed,
        )
    }

    suspend fun resetToDefault(): ApiDomainState {
        preferences.saveApiDomain(null)
        KinoPubConfig.setDomainOverride(null)
        clearDomainSensitiveCaches()
        return getState()
    }

    /**
     * By the time this runs, the caller has already persisted the new domain and pointed
     * [KinoPubConfig] at it — the switch has happened. A cache that fails to clear is stale data,
     * not a reason to undo that switch, so each clear is independently best-effort: one failing
     * must not stop the other from running, and neither may abort the caller's continuation (close
     * the dialog, show the result, kick off the reload) that follows this call.
     */
    private suspend fun clearDomainSensitiveCaches() {
        clearWithoutFailing { itemDetailsRepository.clear() }
        clearWithoutFailing { genreInteractor.clearCache() }
        // Every payload in the store describes one domain's catalogue; a switch makes all of them
        // wrong at once. store.clear() (unlike the prefix removal above) also bumps the store's
        // session generation, which does two jobs: a revalidation that was already in flight when
        // the switch happened is withdrawn instead of landing after this wipe, and every CachedFeed
        // — including the ones inside the screen-scoped HomeInteractor, which this global cannot
        // reach — drops its own memory tier the next time it reads the store.
        clearWithoutFailing { store.clear() }
        // The prefetcher's own record of what is warm describes the caches just emptied above.
        // Kept, it would refuse to re-fetch those ids for up to a minute of browsing the new
        // domain — against a cache that no longer holds a single one of them.
        clearWithoutFailing { detailsPrefetcher.invalidate() }
    }

    private suspend fun clearWithoutFailing(clear: suspend () -> Unit) {
        try {
            clear()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            log(error, "Failed to clear a domain-sensitive cache after switching domains")
        }
    }

    private suspend fun applyEndpoint(endpoint: ApiEndpointPreset) {
        val persistedDomain = endpoint.domain.takeIf { it != KinoPubConfig.DEFAULT_API_DOMAIN }
        preferences.saveApiDomain(persistedDomain)
        KinoPubConfig.setDomainOverride(persistedDomain)
        clearDomainSensitiveCaches()
    }

    private fun buildAutoResolveCandidates(): List<ApiEndpointPreset> {
        val currentEndpoint = KinoPubConfig.CURRENT_ENDPOINT
        return (listOf(currentEndpoint) + KinoPubConfig.BUILT_IN_ENDPOINTS)
            .distinctBy { it.domain }
    }

    private fun isEndpointReachable(endpoint: ApiEndpointPreset): Boolean {
        val reachable = probe.isReachable(endpoint)
        if (reachable) reachability.markReachable(endpoint.domain)
        return reachable
    }

    /**
     * Whether [domain] answered recently enough to be taken on trust.
     *
     * The verdict is shared with the network layer, which is what makes it worth trusting: a probe
     * only ever saw one moment, while the client reports every request that failed to reach the
     * host. Without those reports a mirror that went quiet just after a probe would be chosen for
     * the rest of the window, and no screen can supply them — one with cached content to draw never
     * learns whether the refresh behind it arrived, and screens past the home never had a say.
     */
    private fun isKnownReachable(domain: String): Boolean {
        return reachability.answeredWithin(domain, ProbeCacheTtl)
    }

    internal companion object {
        private const val MAX_HOSTNAME_LENGTH = 253
        private const val MIN_DOMAIN_PARTS = 2
        private const val MAX_LABEL_LENGTH = 63

        /** How long a successful probe stands in for the next one. */
        private val ProbeCacheTtl = 15.minutes

        fun resolveStartupDomain(savedDomain: String?, buildDomain: String?): String? {
            return savedDomain.toValidDomainOrNull() ?: buildDomain.toValidDomainOrNull()
        }

        fun String?.toValidDomainOrNull(): String? {
            val normalized = normalizeDomain(this.orEmpty())
            return normalized.takeIf { it.isNotEmpty() && it.isValidHostname() }
        }

        fun normalizeDomain(input: String): String {
            return input
                .trim()
                .lowercase(Locale.US)
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .trim()
                .trim('.')
        }

        fun String.isValidHostname(): Boolean {
            if (length > MAX_HOSTNAME_LENGTH) return false

            val labels = split(".")
            if (labels.size < MIN_DOMAIN_PARTS || labels.any(String::isEmpty)) return false

            return labels.all { label ->
                label.length <= MAX_LABEL_LENGTH &&
                    label.first().isLetterOrDigit() &&
                    label.last().isLetterOrDigit() &&
                    label.all { it.isLetterOrDigit() || it == '-' }
            }
        }
    }
}
