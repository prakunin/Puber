# API Access

Every KinoPub endpoint lives in one class: `data/api/KinoPubApiClient.kt`. There
are no per-feature API interfaces, no Retrofit, and no generated SDK. Ktor 3.5
with OkHttp does the transport, `kotlinx.serialization` the parsing.

Other hosts have their own small clients next to it: `TmdbApiClient.kt`,
`TheIntroDbApiClient.kt`, `IntroDbAppApiClient.kt`.

## Endpoint shape

```kotlin
suspend fun getItems(
    type: String? = null,
    sort: String? = null,
    page: Int? = null,
    quality: String? = null,
    genre: String? = null,
    conditions: List<String>? = null,
): Result<PaginatedResponse<Item>> = apiCall {
    httpClient.get("${KinoPubConfig.MAIN_API_BASE_URL}items") {
        type?.let { parameter("type", it) }
        page?.let { parameter("page", it) }
        conditions?.forEach { parameter("conditions[]", it) }
    }
}
```

`suspend`, returns `Result<T>`, body wrapped in `apiCall { }`. Optional query
parameters are added only when non-null. The base URL constant already ends
with a slash - `"${KinoPubConfig.MAIN_API_BASE_URL}items"`, not `.../items`.

`apiCall` is an inline helper on the client:

```kotlin
suspend inline fun <reified T> apiCall(block: suspend () -> HttpResponse): Result<T>
```

It records the domain before the request - the active domain can change while a
slow request is still in flight - rethrows `CancellationException` untouched,
and returns `Result.failure` for anything else, first reporting the domain as
unreachable when the exception means the host could not be reached.

## Response envelopes

Models are `@Serializable` classes in `data/api/models/`, used directly as
domain and mapper input - there is no parallel entity layer.

- `PaginatedResponse<T>(items: List<T>, pagination: Pagination)` for lists.
- `ApiResponse<T>` for a single item, `ApiResponseList<T>` for a bare list.
- Endpoint-specific envelopes exist where the API is irregular:
  `CollectionViewResponse`, `HistoryPageResponse`, `TokenResponse`,
  `DeviceCodeResponse`, `WatchingToggleResponse`, `TrailerLinksResponse`.

Item ids are `Int`.

## Interactors

An interactor takes the client and returns domain-shaped results. Global ones
are singletons in `PuberApp.kt`, with an interface when something must be faked
at the boundary (`IAuthInteractor`); screen-scoped ones are registered with
`scopedOf(...)` in the screen's `buildModule` and need no interface.

Inside a `coroutineScope { async { } }`, use `.getOrThrow()` so one failure
cancels the siblings and surfaces through `dispatchError`. For a single call,
`onSuccess` / `onFailure` keeps the failure local. Never wrap a `Result` in
another `Result`.

## Caching

Two layers, both real:

- `core/collections/TypedTtlCache.kt` - `TypedTtlCache<K, V>` with
  `TypedTtlCacheImpl(defaultTtl: Duration, ...)`, for per-key in-memory caching
  such as skip segments.
- `data/cache/` - `ContentCacheRepository`, `CachedFeed`, `CacheKeys`, plus the
  `Cached` wrapper. Feed reads come back as `Cached.Value(value, isStale)` or
  `Cached.RefreshFailed(error)`, which is what lets a screen keep showing stored
  content when a background refresh fails. `ContentListPagingVM` treats an
  unrequested `RefreshFailed` as a log line, not as a user-visible error.

Never cache without either a TTL or an explicit invalidation path.
