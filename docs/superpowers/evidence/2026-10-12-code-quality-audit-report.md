# Code quality audit, 2026-10-12

Task: `code-quality-audit`. Spec:
`docs/superpowers/specs/2026-10-12-code-quality-audit-design.md`.

- **Audited commit:** `36cda915` (branch `code-quality-audit`). Its `app/`,
  `gradle/` and `build.gradle.kts` are byte-identical to master `4e18338e`
  (`git diff --stat master -- app gradle build.gradle.kts` is empty), so every
  `[Source: path:line]` below holds on master too.
- **Line numbers are on the audited commit.** Every `[Source: path:line]` refers
  to `36cda915`, including findings that touch files the in-branch fixes
  F1-F4 changed. Those lines have moved on the branch head. The main case is
  `TmdbApiClient.kt`, which F2 shortened: `apiResult` is at `:127` on
  `36cda915` and at `:70` on the branch (BP-2, SOLID-13). Read such sources with
  `git show 36cda915:<path>`.
- **Method:** static reading of `app/src/main` (386 files, ~51k lines), checked
  against `gradle/libs.versions.toml`, the platform and kotlinx, plus one detekt
  run (see [Detekt](#detekt)). Every "duplicate" verdict was made after reading
  the class, not from its name.
- **Not repeated here:** the 2026-09-08 audit
  (`docs/superpowers/evidence/2026-09-08-memory-and-performance-audit.md`, items
  M1-M5, P1-P12) and the `perf-deferred` board row (P3, M4, P5, P8-P12).
  Overlaps are cross-referenced by those IDs.
- **Perf caveat:** this task does no profiling (spec, Out of scope). Every
  performance finding is **static reading, unmeasured** unless it says otherwise.
- **Fix column:** `fix in this task` means the finding is resolved on this branch
  (one commit per finding, see the spec's Design). `follow-up <slug>` names the
  proposed board row in [Follow-up rows](#proposed-follow-up-board-rows).
- **Branches in flight:** `upstream-sync-triage` already deletes `PagingColumn`
  (`b5bd1cca`) and `player-overlays-port` already deletes
  `PanelFocusRequester.kt`. Those removals are not repeated here.

Severity: high = wrong behaviour or a risk to users now; medium = makes change
costly or holds a latent defect; low = hygiene. Effort: S < half a day,
M = 1-3 days, L = more.

## Structure

The package layout matches `docs/architecture/`: `core/` (cross-cutting),
`data/` (api, cache, db, preferences, repository), `domain/` (interactors and
models), `ui/feature/<screen>/{component|content, model, vm}`. Dependencies
mostly point inward (ui → domain → data). The exceptions:

| ID | Finding | Source | Sev | Effort | Approach | Fix |
|---|---|---|---|---|---|---|
| STR-1 | `domain` imports UI types: the content-list interactor and pager take `SectionConfig` / `AnimeFilterMode` from `ui.feature.contentlist.model`. The domain layer cannot be reasoned about (or reused by another screen) without the UI package. 19 files reference the two types. | [Source: app/src/main/java/com/kino/puber/domain/interactor/contentlist/FreshSectionPager.kt:9] [Source: app/src/main/java/com/kino/puber/domain/interactor/contentlist/ContentListInteractor.kt:13] [Source: app/src/main/java/com/kino/puber/ui/feature/contentlist/model/SectionConfig.kt:9] | medium | M | Move `SectionConfig` and `AnimeFilterMode` to `domain/model` (they are configuration, not UI state); keep the parcelable annotation. Pure move, imports only. | follow-up `layer-deps-cleanup` |
| STR-2 | Shared UI kit imports a feature: `VideoGridUIState` (core) uses `rememberFocusedListItemScroller` defined in the content-list feature. | [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoGridUIState.kt:47] [Source: app/src/main/java/com/kino/puber/ui/feature/contentlist/content/ContentListScreenContent.kt:255] | low | S | Move the helper to `core/ui/uikit/component/moviesList/`. | follow-up `layer-deps-cleanup` |
| STR-3 | `ui/feature/player/vm/` holds 17 files of which one is a ViewModel; the ExoPlayer adapter `PlaybackController`, buffer tuning and diagnostics live there. The package name misleads about what may touch Android media APIs. | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:149] | low | S | Move playback engine classes to `ui/feature/player/playback/` when SOLID-4 is done. | follow-up `playback-controller-split` |
| STR-4 | Architecture docs describe code that is not used: `paging-and-filters.md` "Filters" presents `ContentFilter` / `SortField` as the catalogue filter model, but no production or test code references them (filtering is `SectionConfig`). `navigation.md` mentions `IdGenerator`, which has no callers. | [Source: docs/architecture/paging-and-filters.md:99] [Source: app/src/main/java/com/kino/puber/domain/model/ContentFilter.kt:6] [Source: docs/architecture/navigation.md:61] | low | S | Fix the `IdGenerator` sentence with RW-5. Drop `ContentFilter` and rewrite the "Filters" paragraph around `SectionConfig` in the dead-code row (needs the user to confirm no filter feature is planned on it). | RW-5 part: fixed in this task (`ee4da3ce`); rest: follow-up `dead-code-sweep` |

## Reinvented wheels

Hand-written code checked against what the catalogue
(`gradle/libs.versions.toml`), the Android platform and kotlinx already offer.
"Dependency recommended" verdicts (AC-11) would have to explain TV focus and the
paging model; **this audit recommends no new dependency**, so no finding carries
that flag. Where a library was considered, the reason it loses is given.

### Named candidates (AC-3)

| ID | Candidate | Verdict | Reason | Source | Sev | Effort | Fix |
|---|---|---|---|---|---|---|---|
| RW-1 | `Paginator` (604 lines) + `PagingVM` | **keep** | Closest library is `androidx.paging` (Paging 3 + `paging-compose`), not in the catalogue. It does not fit three things the app relies on. (1) In-place item mutation: `ItemUpdated` / `ItemDeleted` / `ItemAdded` edit the loaded list directly (bookmark, watched and history changes). Paging 3 can only invalidate the `PagingSource` and reload, which re-keys the lazy items and on a TV remote moves or drops the focused card. (2) The paging model in `paging-and-filters.md`: `refreshPagingKeepingContent` keeps drawing the list during a reload so focus has a card to stay on, and `Error<T>` / `PageErrorNext<T>` keep the data visible. `PagingData` is opaque and exposes only `LoadState`, so the ten `Paginator.State` variants that the VMs map from cannot be reproduced. (3) A page emptied by local filtering asks for the next page (`hasMorePages`). Paging 3 would need a custom `PagingSource` that loops, which is the same logic in a less visible place. Migrating would cost more than the class it replaces. The class does need work: see PERF-2 and BP-6. | [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:432] [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:214] [Source: docs/architecture/paging-and-filters.md:39] | low | - | n/a (keep) |
| RW-2 | `TypedTtlCache` (325 lines) | **keep** | Combines TTL, LRU by count and by weight, and coroutine single-flight: one loader per key, with waiters released on cancellation and invalidation. `android.util.LruCache` / `androidx.collection.LruCache` have no TTL, no single-flight and no `suspend` loader. Guava / Caffeine are JVM caches whose async loading is `CompletableFuture`-based (no coroutine cancellation), and they would add ~1-3 MB of classes before R8. Store5 is discussed under RW-3. The single-flight part is the value, and no candidate provides it with coroutine semantics. | [Source: app/src/main/java/com/kino/puber/core/collections/TypedTtlCache.kt:32] [Source: app/src/main/java/com/kino/puber/core/collections/TypedTtlCache.kt:111] | low | - | n/a (keep) |
| RW-3 | `CachedFeed` (607) + `ContentCacheRepository` (634) | **defer** | Closest library is Store5 (`org.mobilenativefoundation.store:store5`): fetcher + source of truth (Room) + memory cache + a stale-while-revalidate stream. On paper it covers `CachedFeed.load`. It does not cover the invalidation model: per-key epochs, destroy-vs-overwrite tickets and the store-generation wipe on logout or domain switch. That model exists because of concrete races documented in the class (a bookmark toggled during a background revalidation). It also does not cover `ContentCacheRepository`'s sparse item merge. A port would re-open those races for no user-visible gain. TV focus would not change, because emission order (cached value first, fresh value second) is what keeps focus in place, and Store5 preserves it. The paging model is unaffected, since paging stays in `Paginator`. Revisit only if the cache keeps producing race bugs. The OkHttp HTTP cache is already installed for the API client and is not a substitute: it has no offline-first read and no item merge. | [Source: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:168] [Source: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:95] [Source: app/src/main/java/com/kino/puber/data/cache/ContentCacheRepository.kt:511] [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:134] | low | - | n/a (defer) |
| RW-4 | `ContentUriCodec` (178) | **keep** | The platform alternative is `android.net.Uri` (`getPathSegments`, `getQueryParameter`). It decodes percent-escapes silently and tolerates duplicate or unknown keys, ports and user-info. This codec exists to *reject* those, because it validates deep links that can start playback (`SENSITIVE_WEB_QUERY_KEYS`, `strictQueryOrNull`). `java.net.URI` also keeps the codec a plain JVM class that `ContentUriCodecTest` runs without Robolectric. | [Source: app/src/main/java/com/kino/puber/core/contentlink/ContentUriCodec.kt:110] [Source: app/src/main/java/com/kino/puber/core/contentlink/ContentUriCodec.kt:119] | low | - | n/a (keep) |
| RW-5 | `IdGenerator` | **replace → delete** | A verbatim copy of the platform's `View.generateViewId()` (API 17; minSdk is 24), with zero callers in `app/`, `baselineprofile/` and tests. It is not replaced by the platform call: it is removed, and the doc sentence that mentions it is dropped. | [Source: app/src/main/java/com/kino/puber/core/system/IdGenerator.kt:8] [Source: docs/architecture/navigation.md:61] | low | S | **fixed in this task** (`ee4da3ce`) |
| RW-6 | `SessionEventBus` | **keep the library, fix the delivery** | As a wheel it is fine: ten lines over kotlinx `MutableSharedFlow`, which is the library. The configuration has a defect, though. `MutableSharedFlow(extraBufferCapacity = 1)` has no replay, and `tryEmit` on a shared flow with **no subscriber** returns at once and the event is gone. The only subscriber is the composable `SessionExpiredHandler`, inside the root `FlowComponent` content, which collects from a `LaunchedEffect`. There is therefore no subscriber before the root content's first composition, between Activity teardown and the next composition, and at any time the process is alive without an Activity. The 401 path (`onSessionExpired`) clears both tokens *before* it emits. A dropped `Unauthorized` therefore still sends the user to auth on the next start, because `LauncherVM.openStartScreen` finds no access token and opens the auth screen. What does not happen are the session clears, which run only in that handler: TV-home programs, the watch-state index, the content cache and the prefetcher are not cleared. One account's history and cached catalogue then outlive its session, possibly into another account's login. No other code path clears them on expiry. The API-domain switch (`clearDomainSensitiveCaches`) clears the content cache and the prefetcher, but not the TV-home programs or the watch-state index, and it runs only on a domain change. Static reading, unmeasured. When `Unauthorized` is merely repeated, the drop is harmless because the event is idempotent. The defect is only the no-subscriber case. Approach (preferred): split the clears from the navigation. A `SessionTerminator` (SOLID-11) collects the bus on an application-lifetime scope (BP-5) from `Application.onCreate`, so the clears always run at emit time, whether or not an Activity exists. The composable then only navigates. For navigation, a dropped event is correct as it is, because a relaunch without a token already lands on auth through `LauncherVM`. Add a unit test that emits before any UI subscribes and asserts that the clears ran. Rejected as the main fix: a `Channel(Channel.CONFLATED)` read through `receiveAsFlow()` in the composable. It keeps the stale event until the next composition. After a relaunch with no token, `LauncherVM` has already opened auth, and the handler would then call `newRootScreen(auth)` a second time. That gives a fresh `AuthVM` and possibly a second device-code request. The handler's `clearSession()` would also drop a deep-link target that `LauncherVM` just parked with `waitForAuthentication()`. Suppressing both would need the handler to skip navigation when the root is already auth, and to skip `clearSession()` for an event older than the current launch. That is more state than the application-scope collector needs. | [Source: app/src/main/java/com/kino/puber/core/session/SessionEventBus.kt:12] [Source: app/src/main/java/com/kino/puber/core/session/SessionEventBus.kt:16] [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:232] [Source: app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:63] [Source: app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:167] [Source: app/src/main/java/com/kino/puber/ui/feature/root/vm/LauncherVM.kt:35] [Source: app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt:161] | medium | S | follow-up `session-expiry-delivery` |

### Other candidates found

| ID | Candidate | Verdict | Reason | Source | Sev | Effort | Fix |
|---|---|---|---|---|---|---|---|
| RW-7 | Playback clock `H:MM:SS` formatter written twice, semantically identical (Int vs Long constants, equivalent by widening), in `PlayerProgressBar.formatTimeMs` and `PlayerUIMapper.formatTime` (a third variant in `PlaybackDebugFormat` always prints hours and is left alone) | **keep in-house, consolidate** | Platform `DateUtils.formatElapsedTime` prints `MM:SS` with a leading zero and locale digits, which would change visible text, so it is not used. The two copies become one internal function. | [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerProgressBar.kt:325] [Source: app/src/main/java/com/kino/puber/ui/feature/player/model/PlayerUIMapper.kt:215] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackDebugFormat.kt:126] | low | S | **fixed in this task** (`c07d807d`) |
| RW-8 | `CryptoPreferenceRepository`: hand-rolled AES-GCM with an AndroidKeyStore key | **keep** | The library alternative, `androidx.security:security-crypto`, is deprecated upstream, and the app's own catalogue entry for it is unused (BP-4). Tink would be a new dependency for the same two calls. The hand-rolled version is small and auditable. | [Source: app/src/main/java/com/kino/puber/data/repository/CryptoPreferenceRepository.kt:135] | low | - | n/a (keep) |
| RW-9 | `CurlLogger` Ktor plugin vs `ktor-client-logging` | **keep** | It prints curl commands through `SensitiveRequestLogPolicy` redaction, which the stock `Logging` plugin does not do. The stock plugin's dependency is therefore dead (BP-4). | [Source: app/src/main/java/com/kino/puber/data/api/KtorPlugins.kt:58] | low | - | n/a (keep) |
| RW-10 | Retry, JSON, debounce, date and time | **no wheel** | Retry uses Ktor `HttpRequestRetry` (`retryOnlyRepeatableRequests`). JSON is kotlinx.serialization throughout, with no hand parsing. Search debounce is `delay()` inside the cancellable `searchJob`, by documented design (`paging-and-filters.md`, "Search is not paged"). Dates use `java.time` (desugared) for localized formatting and kotlinx-datetime for the model. | [Source: app/src/main/java/com/kino/puber/data/api/ApiRetryPolicy.kt:33] [Source: app/src/main/java/com/kino/puber/ui/feature/episodeschedule/model/EpisodeScheduleUIMapper.kt:90] | - | - | n/a |
| RW-11 | `AppUpdateChecksum`: hand-written hex encoder (`ByteArray.toHexString()` over a `HEX_CHARS` table) and hex validator (`isSha256`, a char-range loop), plus a manual `MessageDigest` read loop | **replace with stdlib** | The catalogue pins Kotlin `2.4.10`, where the stdlib's `ByteArray.toHexString()` and `kotlin.text.HexFormat` are stable (they have not been `@ExperimentalStdlibApi` since 2.2). They cover the encoder exactly (lowercase by default). The validator becomes `value.length == 64 && runCatching { value.hexToByteArray() }.isSuccess`, or a `HexFormat`-based check. `parseSha256` lowercases first, so the accepted alphabet does not change. `java.util.HexFormat` is **not** an option: it is API 34 and minSdk is 24. Okio is the alternative: `HashingSource.sha256(file.source())` drained into `blackholeSink()`, then `.hash.hex()`, with `decodeHex()` for parsing. Okio is already on the classpath through OkHttp and imported directly (`BoundedDownload`), but it has no catalogue entry of its own, so the stdlib wins on "no new direct dependency". The private `toHexString` also shadows the stdlib member of the same name, which a reader can mistake for it. `AppUpdateDownloadTest` covers `parseSha256` and the digest, so the swap has a test net. | [Source: app/src/main/java/com/kino/puber/data/repository/AppUpdateDownload.kt:125] [Source: app/src/main/java/com/kino/puber/data/repository/AppUpdateDownload.kt:142] [Source: app/src/main/java/com/kino/puber/data/repository/AppUpdateDownload.kt:148] [Source: gradle/libs.versions.toml:3] [Source: app/src/main/java/com/kino/puber/data/api/network/diagnostics/BoundedDownload.kt:9] [Source: app/src/test/kotlin/com/kino/puber/data/repository/AppUpdateDownloadTest.kt:17] | low | S | follow-up `api-client-split` (same update-download code) |

## SOLID

All eight files over 700 lines named in the spec (AC-4), then the other SOLID
findings.

| ID | File (lines) | Finding / verdict | Source | Sev | Effort | Approach | Fix |
|---|---|---|---|---|---|---|---|
| SOLID-1 | `PlayerVM.kt` (1859) | **Split.** Holds at least five responsibilities next to view-state reduction: skip-segment detection and countdown (~200 lines), watched-mark mutations with their mutex and local sync (~290), stream-candidate fallback and link refresh (~100), the queued progress-save drain (~100), and next-episode prefetch and countdown. 117 functions; `LargeClass` is suppressed. | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:59] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1516] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:954] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:410] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1709] | medium | L | Extract plain classes owned by the VM, as `SeekHandler`, `ControlsStateMachine` and `ProgressTracker` already are: `SkipSegmentCoordinator`, `WatchedMutationCoordinator`, `StreamCandidateSelector`, `ProgressSaveQueue`. Each takes the VM scope and a callback, not the VM. | follow-up `player-vm-split` |
| SOLID-2 | `PlayerVM.kt` | `getExoPlayer()` downcasts the `PlaybackControl` abstraction to the concrete `PlaybackController` (dependency inversion leak). A test double silently yields no surface. | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1832] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:52] | low | S | Add the surface accessor to `PlaybackControl` (or a small `PlayerSurfaceSource`). Related to the 2026-09-08 audit's P7 (lambda not remembered), which is fixed. | follow-up `player-vm-split` |
| SOLID-3 | `KinoPubApiClient.kt` (1195) | **Split.** Besides the KinoPub REST surface it owns the GitHub release check and APK download with checksum (~165 lines, a third-party host), and the OAuth device flow (~90 lines). The file's own comment already names the split. `LargeClass` is suppressed. | [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:99] [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:890] [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:1059] | medium | M | Move update traffic to `AppUpdateApiClient` and the device flow to `DeviceAuthApiClient`, both on the shared `OkHttpClient`. `api.md`'s "single client" rule then reads "single client per host". Do it after BP-1 removes the dead endpoints. | follow-up `api-client-split` |
| SOLID-4 | `PlaybackController.kt` (990) | **Split, lightly.** It is the only ExoPlayer adapter, which is a coherent responsibility. It also formats debug info (~60 lines) and implements text-track matching (~100 lines) that are pure functions of `Tracks`. | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:768] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:884] | low | M | Extract `TextTrackMatcher` (pure, unit-testable) and a debug-info builder. The warm-up player (P3) and player re-creation (P8) stay in `perf-deferred`. | follow-up `playback-controller-split` |
| SOLID-5 | `DetailsScreenContent.kt` (945) | **Acceptable.** Pure composables for one screen (state + `onAction`), about 110 lines of named layout constants, and no business logic. Moving the skeleton (~110 lines) to its own file would help navigation but not design. | [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt:91] [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt:722] | low | S | Optional: `DetailsSkeleton.kt`. | none |
| SOLID-6 | `DeviceSettingsContent.kt` (816) | **Acceptable.** Already split into one `LazyListScope` builder per settings section. `stringResource` here renders static labels, which is not the label-value anti-pattern from `screens-and-di.md`. Its private `ErrorView` / `LoadingView` are covered by BP-8. | [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt:471] | low | - | - | none |
| SOLID-7 | `HistoryVM.kt` (805) | **Split, one piece.** Runtime state is already extracted (`HistoryRuntimeState`, 570 lines). What remains mixes paging with a deletion queue and post-delete reconciliation (~250 lines). | [Source: app/src/main/java/com/kino/puber/ui/feature/history/vm/HistoryVM.kt:516] [Source: app/src/main/java/com/kino/puber/ui/feature/history/vm/HistoryVM.kt:645] | low | M | Extract `HistoryDeletionCoordinator`. P10 (re-publish per focus step) stays in `perf-deferred`. | follow-up `history-vm-split` |
| SOLID-8 | `FlowComponent.kt` (785) | **Split the file.** Four concerns in one file: the root flow, the tab flow (from line 511), the root-return lazy-anchor restore registry, and an `ActivityNavigator`. It also has two near-copies of the command interpreter (`FlowCommandRunner`, `TabFlowCommandRunner`) that differ in four branches. | [Source: app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:212] [Source: app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:697] [Source: app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:350] [Source: app/src/main/java/com/kino/puber/core/ui/navigation/component/FlowComponent.kt:512] | medium | M | Files `TabFlowComponent.kt` and `RootAnchorRestore.kt`, plus one interpreter with a strategy for the branches that differ. Navigation and focus change, so this needs the device. | follow-up `flow-component-split` |
| SOLID-9 | `HomeVM.kt` (705) | **Split one concern.** About 70 lines run the API-domain dialog (open, save, detect, reset). The same block is repeated in `AuthVM` and `DeviceSettingsVM`, three copies of one feature. | [Source: app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:527] [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:219] [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:444] | medium | M | One `ApiDomainDialogController` (state flow + actions + a screen-specific `onDomainChanged` hook) composed into the three VMs. | follow-up `api-domain-dialog-delegate` |
| SOLID-10 | `HomeVM`, `AuthVM`, `DeviceSettingsVM` | The private mapper `ApiDomainState.toDialogState()` is byte-identical in three VMs. This is the part of SOLID-9 that can move safely now. | [Source: app/src/main/java/com/kino/puber/ui/feature/home/vm/HomeVM.kt:673] [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:291] [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/vm/DeviceSettingsVM.kt:505] | low | S | One `internal fun ApiDomainState.toDialogState()` next to `ApiDomainDialogState`. | **fixed in this task** (`93a06ca2`) |
| SOLID-11 | `App.kt` `SessionExpiredHandler` | A composable orchestrates logout: it pulls six singletons through `getKoin().get()` (service locator, re-resolved on every recomposition) and sequences their clears. That is domain policy in the UI layer, and it cannot be unit-tested. The same `getKoin().get()` pattern appears in `WarmContentLaunchHandler` and `MainActivity`. | [Source: app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:63] [Source: app/src/main/java/com/kino/puber/ui/feature/root/component/App.kt:114] [Source: app/src/main/java/com/kino/puber/MainActivity.kt:85] | medium | S | A `SessionTerminator` interactor (global `single`) that owns the ordered best-effort clears, unit-tested. The composable keeps only `collect` and navigation, and resolves through `koinInject()`. | follow-up `session-logout-interactor` |
| SOLID-12 | `DetailsVM.kt` (698, `@Suppress("LargeClass")`) | **Acceptable, with one shared extraction.** It is two lines under the spec's 700-line cut, which is why AC-4's list does not name it. On reading, its 40 functions serve one screen: load and render, the watchlist, bookmark and watched mutations with their refresh, the episode and season selection, the trailer request and preview jobs (~60 lines), and close-after-mutations navigation. None of these is a separate engine the way PlayerVM's skip-segment or stream-fallback logic is (SOLID-1), and none is shared with another screen except one piece. That piece is the pending-mutation queue (`launchMutation` + `awaitPendingMutations`, ~25 lines: a tracked job set that `closeDetails` drains before `router.back`). The job-set and drain logic is repeated in `PlayerVM` and `CollectionDetailVM`, but the copies are not identical. Only `DetailsVM` serialises the blocks with a `Mutex`; the other two run them concurrently. `PlayerVM`'s version also takes an `onCreated` hook, which the progress-save drain uses to record its job. Splitting the trailer jobs out would move lines, not responsibilities, so it is not proposed. The `LargeClass` suppression stays justified once the queue moves out. | [Source: app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt:44] [Source: app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt:577] [Source: app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt:593] [Source: app/src/main/java/com/kino/puber/ui/feature/details/vm/DetailsVM.kt:601] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1785] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1720] [Source: app/src/main/java/com/kino/puber/ui/feature/collections/detail/vm/CollectionDetailVM.kt:154] | low | S | One `MutationQueue` class (VM scope in, `launch(onCreated)` / `awaitIdle` out) in `core/`, unit-tested, used by the three VMs. Serialisation must be **optional per caller** (a `serialized: Boolean`, or an optional `Mutex`), on only for `DetailsVM`. A queue-wide lock would make player watched toggles wait behind the long-running progress-save drain. Close-after-mutation timing changes are visible on the device. | follow-up `vm-mutation-queue` |
| SOLID-13 | `TmdbApiClient.kt` (on `36cda915`) | **Duplicated error wrapper, two failure policies for one endpoint.** (1) The private `apiResult` re-implements `runCatchingCancellable` (rethrow `CancellationException`, wrap the rest in `Result`). The only difference is that it catches `Exception` where the shared helper catches `Throwable`, so an `Error` (for example a `NoClassDefFoundError` from an API missing on an old TV box) escapes TMDB calls, while every other caller of the helper gets it wrapped in a failed `Result`. The helper has 12 call sites in `app/src/main`. (2) `findTvByImdbId` and `findByImdbId` call the same `find/{imdb_id}` endpoint with the same parameters. The first turns a non-2xx response into a failed `Result` (`TmdbApiException`), which `EpisodeScheduleRepository` surfaces with `getOrThrow()`. The second turns it into `Result.success(null)`, which reads as "no TMDB match", and it also falls back from TV to movie results, for `SkipSegmentService`. An expired TMDB token or a 429 is therefore an error on the schedule screen and a silent "no skip segments" in the player. No miss is cached (only a hit is saved), so the impact is diagnostics, not stale data. | [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:127] [Source: app/src/main/java/com/kino/puber/core/coroutine/CoroutinesErrorHandler.kt:37] [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:50] [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:73] [Source: app/src/main/java/com/kino/puber/data/repository/EpisodeScheduleRepository.kt:55] [Source: app/src/main/java/com/kino/puber/data/repository/SkipSegmentService.kt:62] | low | S | Replace `apiResult` with `runCatchingCancellable`. Keep one private `find(imdbId)` that returns the raw `TmdbFindResponse` with one failure policy (fail on non-2xx), and two thin projections (TV only; TV then movie). `SkipSegmentService` only calls `.getOrNull()` today and logs nothing, so the follow-up must add a log of the failure there (for example `onFailure { log(it, ...) }` before `getOrNull()`). A failure then still means "no segments", but it is recorded as a failure, not lost as a miss. Pin both behaviours in `TmdbApiClientTest` first. | follow-up `tmdb-result-handling` |

## Performance

All findings are **static reading, unmeasured**. None repeats an item from the
2026-09-08 audit or `perf-deferred`; related items are named.

| ID | Finding | Source | Sev | Effort | Approach | Related | Fix |
|---|---|---|---|---|---|---|---|
| PERF-1 | Every position tick (500 ms while controls, the settings panel or buffering are shown) copies the whole `PlayerContentState`, and `PlayerContent` passes that one object to all six layers. The VM's own comment says a copy means "a recomposition pass across the entire player". The guard limits *when* this happens, not how much it costs. | [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1422] [Source: app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:1399] [Source: app/src/main/java/com/kino/puber/ui/feature/player/component/PlayerScreenContent.kt:108] | medium | M | Publish position, duration and buffered position on their own `StateFlow` (or a `State<Long>` read inside the progress bar's draw/layout lambda) so the tick does not touch `PlayerContentState`. Measure frame time with controls up before and after (perf route). | P2 (shimmer, fixed) | follow-up `player-position-state` |
| PERF-2 | `Paginator` page merge is O(n·m): `NextPage` runs `indexOfFirst` over the whole list for every incoming item, and `PrevPage` copies the whole list once per item through `addItemOrUpdate`. Cost grows with depth scrolled. It runs on the paginator's single background thread, so the user sees later pages, not jank. | [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:370] [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:332] [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:472] | low | S | Build a key → index map once per merge (the comparator would need a key function), or merge into one `MutableList` per action. | - | follow-up `paginator-hardening` |
| PERF-3 | `TypedTtlCache.putUnsafe` scans every entry for expiry on each put, under the lock. `CachedFeed.putAll` calls `put` once per key, so a page write of k items costs O(k·n) with n up to 1024. | [Source: app/src/main/java/com/kino/puber/core/collections/TypedTtlCache.kt:304] [Source: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:370] | low | S | Evict expired entries lazily on read (already done in `getValidValueUnsafe`) and only from the LRU head on overflow. | M2 (count-bounded tier, fixed with a weight bound) | follow-up `paginator-hardening` (same "collections" review) |
| PERF-4 | `CachedFeed` keeps four per-key `ConcurrentHashMap`s. `keyWrites` and `keySupersedeWrites` are never cleared by design, so they grow by one entry per distinct cache key for the life of the process (items, queries and payloads visited). Each entry is small, but nothing bounds them. | [Source: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:120] [Source: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:131] [Source: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:432] | low | M | Fold the four counters into one per-key record and drop records whose key has no row and no flight (needs a careful proof against the races the class documents). | M2 | follow-up `cached-feed-key-maps` |
| PERF-5 | Coil is configured without a network fetcher, so `coil-network-ktor3` builds its own default Ktor client: a second OkHttp connection pool and dispatcher, and **without** the DNS-over-HTTPS resolver that the API, TMDB, IntroDB and Media3 clients share. On networks where DoH is the reason the API works, posters may fail separately (behaviour, not only cost). | [Source: app/src/main/java/com/kino/puber/PuberApp.kt:94] [Source: app/src/main/java/com/kino/puber/data/di/modules.kt:57] [Source: app/src/main/java/com/kino/puber/data/api/TheIntroDbApiClient.kt:21] | medium | S | `components { add(KtorNetworkFetcherFactory(httpClient = { HttpClient(OkHttp) { engine { preconfigured = get<OkHttpClient>() } } })) }` uses only the existing `coil-ktor` dependency. It changes the image DNS path, so verify on the device. | - | follow-up `coil-shared-http` |

## Best practices

| ID | Finding | Source | Sev | Effort | Approach | Fix |
|---|---|---|---|---|---|---|
| BP-1 | Dead API surface: 36 public `KinoPubApiClient` endpoints have no caller in `app/src/main` or tests (for example `getTVChannels`, `getTVChannelDetails`, `voteForItem`, `removeVoteForItem`, `addNotification`, `getServerLocations`, `getCountries`, `getItemComments`, `searchByTitle`, `searchByDirector`, `clearMediaHistory` (an alias of `clearExactMediaHistory`), `getAccountInfo`, `unlinkDevice`). Their response models in `Models.kt` (`EPGProgram`, `NotificationInfo`, `Subscription`, ...) are dead with them. `getDeviceCode` / `getDeviceToken` are only used inside the client and can be `private`. `ContentFilter` (STR-4) belongs to the same sweep. | [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:410] [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:677] [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:730] [Source: app/src/main/java/com/kino/puber/data/api/models/Models.kt:442] [Source: app/src/main/java/com/kino/puber/domain/model/ContentFilter.kt:6] | medium | M | Delete them, or mark the few the user wants kept for planned features. This needs a product decision, so it is not done here. | follow-up `dead-code-sweep` |
| BP-2 | Leftovers of the TMDB cast enrichment that `0192929e` dropped: `findMediaByImdbId` (with the `normalizedImdbTitleIdOrNull` helper and its regex, which only it uses), `getMovieCredits`, `getTvAggregateCredits`, `getConfiguration`, all of `TmdbImageConfig`, and all types in `TmdbCastModels.kt` have no production caller. Only `TmdbApiClientTest.castAndConfigurationEndpoints_*` keeps them alive. | [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:27] [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:84] [Source: app/src/main/java/com/kino/puber/data/api/TmdbApiClient.kt:105] [Source: app/src/main/java/com/kino/puber/data/api/config/TmdbImageConfig.kt:3] [Source: app/src/main/java/com/kino/puber/data/api/models/TmdbCastModels.kt:1] [Source: app/src/test/kotlin/com/kino/puber/data/api/TmdbApiClientTest.kt:135] | low | S | Delete the four methods, the helper and regex, the two files and that one test. The schedule endpoints used by `EpisodeScheduleRepository` stay. | **fixed in this task** (`2342ea57`) |
| BP-3 | `runCatching { focusRequester.requestFocus() }` is copy-pasted at about 28 sites with three different readings of the result. Two sites read `.isSuccess`, which counts a `false` return (focus not taken) as success. `TrailerOverlay`'s retry loop therefore stops after its first attempt even when focus did not land, and `onDirectionKey` consumes the key even when the target did not take focus. | [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/TrailerOverlay.kt:179] [Source: app/src/main/java/com/kino/puber/ui/feature/details/component/DetailsScreenContent.kt:715] [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/FocusTargetState.kt:159] | medium | M | One `FocusRequester.tryRequestFocus(): Boolean` in `core/ui/uikit/component/modifier/` that returns `false` on throw *or* refusal. Migrate the sites, fixing the two `.isSuccess` readings. This changes focus behaviour, so it needs the device. | follow-up `focus-request-helper` |
| BP-4 | Declared dependencies with no import in `app/src/main`: `ktor-client-logging`, `coil-video` (no `VideoFrameDecoder` registered), `security-crypto` (replaced by RW-8). `appcompat` is used only as the parent of `Theme.Puber`. | [Source: app/build.gradle.kts:363] [Source: app/build.gradle.kts:370] [Source: app/build.gradle.kts:382] [Source: app/build.gradle.kts:349] [Source: app/src/main/res/values/themes.xml:3] | low | S | Drop the three dead artifacts; consider a platform theme parent for `appcompat`. This is a build change and must be checked on a release build with R8 (relates to P5), so it is not done here (AC-7). | follow-up `dependency-prune` |
| BP-5 | Four application-lifetime scopes are created ad hoc as `CoroutineScope(SupervisorJob() + Dispatchers.X)` with no `CoroutineExceptionHandler`. An exception escaping a `launch` in any of them crashes the process, and none of them can be swapped in tests through DI. | [Source: app/src/main/java/com/kino/puber/PuberApp.kt:48] [Source: app/src/main/java/com/kino/puber/domain/interactor/watchstate/WatchStateSyncInteractor.kt:51] [Source: app/src/main/java/com/kino/puber/domain/interactor/prefetch/DetailsPrefetcher.kt:40] [Source: app/src/main/java/com/kino/puber/data/repository/WatchStateRepository.kt:46] | medium | S | One Koin `single<ApplicationScope>` built with `DefaultExceptionHandler` (already in `core/coroutine`), injected where the four scopes are made today. | follow-up `app-coroutine-scope` |
| BP-6 | `Paginator.Store` crosses threads without synchronisation. `state` and `render` are plain `var`s, written on the store's `Dispatchers.Default.limitedParallelism(1)` coroutine and assigned and read (`render` setter calls `value(state)`) from the VM on Main. The window is the `init` call, so this is latent rather than observed. | [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:542] [Source: app/src/main/java/com/kino/puber/core/paginator/Paginator.kt:545] [Source: app/src/main/java/com/kino/puber/core/paginator/PagingVM.kt:36] | low | S | Mark both `@Volatile`, or send `render` assignment through the action channel. In the same change, collapse the identical branch pairs in `executeNextPageAction`, `executePrevPageAction`, `executeErrorAction` and `executePageErrorAction` (PERF-2 edits the same code), with characterization tests first, because `PaginatorReducerTest` does not cover those merges today. | follow-up `paginator-hardening` |
| BP-7 | The API client's `Json` has `prettyPrint = true`, and it is also the `ContentNegotiation` encoder, so every JSON request body is sent indented. Seven separate `Json { }` configurations exist across the data layer. | [Source: app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:122] [Source: app/src/main/java/com/kino/puber/data/cache/CachedFeed.kt:593] [Source: app/src/main/java/com/kino/puber/data/api/ApiFailures.kt:18] | low | S | One shared `Json` for API decoding without `prettyPrint`. This changes request bytes, so it is a follow-up. | follow-up `dead-code-sweep` (same review of `data/api`) |
| BP-8 | Home and Device Settings render private `ErrorView` / `LoadingView` composables instead of the shared `FullScreenError` / `FullScreenProgressIndicator`, as `screens-and-di.md` already notes. | [Source: app/src/main/java/com/kino/puber/ui/feature/home/component/HomeScreenContent.kt:128] [Source: app/src/main/java/com/kino/puber/ui/feature/device/settings/DeviceSettingsContent.kt:127] [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/Errors.kt:25] [Source: app/src/main/java/com/kino/puber/core/ui/uikit/component/Loading.kt:11] | low | S | Use the shared components, extending them with a secondary action (the API-domain button) if needed. This is a visual change and needs the ux-designer. | follow-up `screen-error-states` |
| BP-9 | The pairing-code countdown ticks with `delay(1000)` per second, so the displayed time drifts behind the real expiry by the loop's own work (state copy, recomposition) on every tick. | [Source: app/src/main/java/com/kino/puber/ui/feature/auth/vm/AuthVM.kt:199] | low | S | Compute the remaining time from a deadline (`SystemClock.elapsedRealtime()`) on each tick. | follow-up `screen-error-states` (same small-UI review), or drop |

## Detekt

**Status: "Before" obtained on 2026-10-12 in the worktree (no main-checkout fallback needed): 0 findings in `devDebug` and in `devDebugUnitTest`. The "After" row: 0 findings in both, so AC-9 holds (no more findings than "Before").**

The sixth attempt (implementation phase, swap still ~90 % used) completed in
54 s. Detekt printed "83 compiler errors found during analysis" for `devDebug`
and "7" for `devDebugUnitTest`, which it says affects accuracy; the finding
count itself is 0 in both reports.

History of the five failed attempts that preceded it:

Command, per the spec's answer to Q4 (`tools/agentw` takes plain Gradle
arguments; there is no task-slug argument):

```
cd <wt> && AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:detektAll --continue
```

Every attempt on 2026-10-12 lost the Gradle daemon to a JVM fault. No
`app/build/reports/detekt/*` file was written:

| # | Variation | Task reached | JVM failure (hs_err) |
|---|---|---|---|
| 1 | defaults (system OpenJDK 21.0.12) | `detektDevDebug` | SIGSEGV in C2 compiler thread, `PhaseCCP::transform`, compiling `FirMissingDependencyClassChecker::check` |
| 2 | defaults, retry | `detektDevDebug` | SIGSEGV in `SymbolTable::grow` |
| 3 | `JAVA_HOME` = JBR 21.0.6 | `detektDevDebug` | SIGSEGV in `SymbolTable::grow` |
| 4 | `--max-workers=1 -Dorg.gradle.jvmargs="-Xmx4g -XX:+UseSerialGC -XX:TieredStopAtLevel=1"` | `detektDevDebug` | hang: "VM Thread" at 100 % CPU for 20 min, attach refused; daemon killed |
| 5 | defaults, retry | `compileDevDebugKotlin` | SIGSEGV in a G1 concurrent-mark worker |

Five unrelated JVM internals on two JDK builds, with swap ~90 % used at the
time, point at the host rather than at detekt or the project. The same task
completed with **0 findings** on this host in neighbouring worktrees on
2026-10-10 (`subtitle-player-fixes` `7ad23247`) and 2026-10-11
(`upstream-sync-triage` `43b9538e`). Those runs used the same
`config/detekt/detekt.yml` and the empty `config/detekt/detekt-baseline.xml`,
but they are **not** the audited commit. They are a reference only.

The crash logs were moved out of the worktree (nothing was committed). The
attempts used `--stop` once and killed one hung daemon of this task's own run.
Both waited for the `AGENT_GRADLE_SERIAL` lock first.

Static facts that do not need the run:

- The baseline file is empty, so every finding a run reports is a real one.
  Neighbouring branches report 0, so a non-zero count on this commit would be a
  regression on master.
- 71 `@Suppress` annotations in `app/src/main` hide rules locally: `unused` 20
  (the `buildModule` convention), `ReturnCount` 10, `UNCHECKED_CAST` 8
  (`Paginator`), `SuspendFunSwallowedCancellation` 7 (each with a reason
  comment), `MagicNumber` 6, `DEPRECATION` 5, `TooGenericExceptionCaught` 3,
  `LargeClass` 3 (`PlayerVM`, `KinoPubApiClient`, `DetailsVM`: SOLID-1,
  SOLID-3, SOLID-12), `LongParameterList` 2, `CyclomaticComplexMethod` 2, and one each
  of `UNUSED_PARAMETER`, `TooManyFunctions`, `FunctionName`.

To complete AC-8 and AC-9: re-run the command above when the host is stable
(qa, before the first fix commit, on `36cda915` or any later commit whose `app/`
is unchanged). Record the per-rule-set counts from
`app/build/reports/detekt/devDebug.md` and `devDebugUnitTest.md` here, under
"Before". Then repeat after F1-F4 under "After".

| Run | Commit | devDebug findings | devDebugUnitTest findings | Per rule set |
|---|---|---|---|---|
| Before | worktree `HEAD` `72a2da6d` (`app/src/main` equal to `36cda915`; `devDebugUnitTest` includes the Task 1 tests) | 0 | 0 | none: 0 findings in every rule set of both reports (run in the worktree via `./tools/agentw`) |
| After F1-F4 | worktree `HEAD` `c07d807d` | 0 | 0 | none: 0 findings in every rule set of both reports (worktree, `./tools/agentw :app:detektAll --continue`; same 83 and 7 "compiler errors" notes as before; `devDebugUnitTest` now also includes `ApiDomainDialogStateTest` and `PlaybackClockTest`, and loses the deleted TMDB cast test) |

## Summary

Ordered by group (libraries → SOLID and structure → performance → best
practices, AC-6), then severity, then effort.

| # | ID | Group | Sev | Effort | Fix |
|---|---|---|---|---|---|
| 1 | RW-6 | Libraries | medium | S | follow-up `session-expiry-delivery` |
| 2 | RW-5 | Libraries | low | S | fixed in this task (`ee4da3ce`) |
| 3 | RW-7 | Libraries | low | S | fixed in this task (`c07d807d`) |
| 4 | RW-11 | Libraries | low | S | follow-up `api-client-split` |
| 5 | RW-1 | Libraries | low | - | keep |
| 6 | RW-2 | Libraries | low | - | keep |
| 7 | RW-3 | Libraries | low | - | defer |
| 8 | RW-4 | Libraries | low | - | keep |
| 9 | RW-8 | Libraries | low | - | keep |
| 10 | RW-9 | Libraries | low | - | keep |
| 11 | RW-10 | Libraries | - | - | no wheel |
| 12 | SOLID-11 | SOLID | medium | S | follow-up `session-logout-interactor` |
| 13 | SOLID-3 | SOLID | medium | M | follow-up `api-client-split` |
| 14 | SOLID-8 | SOLID | medium | M | follow-up `flow-component-split` |
| 15 | SOLID-9 | SOLID | medium | M | follow-up `api-domain-dialog-delegate` |
| 16 | STR-1 | Structure | medium | M | follow-up `layer-deps-cleanup` |
| 17 | SOLID-1 | SOLID | medium | L | follow-up `player-vm-split` |
| 18 | SOLID-10 | SOLID | low | S | fixed in this task (`93a06ca2`) |
| 19 | SOLID-2 | SOLID | low | S | follow-up `player-vm-split` |
| 20 | SOLID-13 | SOLID | low | S | follow-up `tmdb-result-handling` |
| 21 | SOLID-12 | SOLID | low | S | acceptable; queue extraction: follow-up `vm-mutation-queue` |
| 22 | STR-2 | Structure | low | S | follow-up `layer-deps-cleanup` |
| 23 | STR-3 | Structure | low | S | follow-up `playback-controller-split` |
| 24 | STR-4 | Structure | low | S | RW-5 part: fixed in this task (`ee4da3ce`); rest: follow-up `dead-code-sweep` |
| 25 | SOLID-5 | SOLID | low | S | acceptable |
| 26 | SOLID-4 | SOLID | low | M | follow-up `playback-controller-split` |
| 27 | SOLID-7 | SOLID | low | M | follow-up `history-vm-split` |
| 28 | SOLID-6 | SOLID | low | - | acceptable |
| 29 | PERF-5 | Performance | medium | S | follow-up `coil-shared-http` |
| 30 | PERF-1 | Performance | medium | M | follow-up `player-position-state` |
| 31 | PERF-2 | Performance | low | S | follow-up `paginator-hardening` |
| 32 | PERF-3 | Performance | low | S | follow-up `paginator-hardening` |
| 33 | PERF-4 | Performance | low | M | follow-up `cached-feed-key-maps` |
| 34 | BP-5 | Best practices | medium | S | follow-up `app-coroutine-scope` |
| 35 | BP-1 | Best practices | medium | M | follow-up `dead-code-sweep` |
| 36 | BP-3 | Best practices | medium | M | follow-up `focus-request-helper` |
| 37 | BP-2 | Best practices | low | S | fixed in this task (`2342ea57`) |
| 38 | BP-4 | Best practices | low | S | follow-up `dependency-prune` |
| 39 | BP-6 | Best practices | low | S | follow-up `paginator-hardening` |
| 40 | BP-7 | Best practices | low | S | follow-up `dead-code-sweep` |
| 41 | BP-8 | Best practices | low | S | follow-up `screen-error-states` |
| 42 | BP-9 | Best practices | low | S | follow-up `screen-error-states` |

### Fixed in this task

| Fix | Finding | Commit | Change | Runtime reach |
|---|---|---|---|---|
| F1 | RW-5, STR-4 (part) | `ee4da3ce` | Delete `IdGenerator.kt`; drop its sentence from `navigation.md`. | none (no callers) |
| F2 | BP-2 | `2342ea57` | Delete the TMDB cast leftovers and their one test. | none (no production callers); `TmdbApiClient` still serves the episode schedule |
| F3 | SOLID-10 | `93a06ca2` | One shared `ApiDomainState.toDialogState()`. | API-domain dialog on Home (error state), Auth and Settings |
| F4 | RW-7 | `c07d807d` | One shared playback clock formatter. | player progress bar, seek indicator, resume dialog |

### Proposed follow-up board rows

Ordered by the user's priority (libraries → SOLID → performance → best
practices), then by value. Each row is one reviewable change. Kind uses the
spec vocabulary; dimension is the audit section it comes from.

| Slug | Scope (one line) | Findings | Kind | Size | Dimension | Device |
|---|---|---|---|---|---|---|
| `session-expiry-delivery` | Run the session clears on an application-scope collector (with `session-logout-interactor`'s `SessionTerminator`), so they no longer depend on a composed UI; the UI keeps only navigation. Not a conflated `Channel` in the composable, which would replay into a second auth navigation after relaunch. Test emit-before-subscribe | RW-6 | bug | S | libraries | yes (401 with the Activity gone, then relaunch); land with or before `session-logout-interactor`, same handler |
| `session-logout-interactor` | Move logout orchestration from `SessionExpiredHandler` into a tested `SessionTerminator`; `koinInject` instead of `getKoin().get` | SOLID-11 | refactor | S | SOLID | yes (logout path) |
| `api-domain-dialog-delegate` | One API-domain dialog controller shared by Home, Auth and Settings VMs | SOLID-9 | refactor | M | SOLID | yes |
| `api-client-split` | Move update download and the device flow out of `KinoPubApiClient`; stdlib hex in `AppUpdateChecksum` | SOLID-3, RW-11 | refactor | M | SOLID | yes (update check, pairing) |
| `player-vm-split` | Extract skip-segment, watched-mutation, stream-fallback and progress-save coordinators; surface accessor on `PlaybackControl` | SOLID-1, SOLID-2 | refactor | L | SOLID | yes; after `player-overlays-port` |
| `flow-component-split` | Split `FlowComponent.kt` into flow / tab flow / anchor restore files; one command interpreter | SOLID-8 | refactor | M | SOLID | yes (navigation, focus) |
| `layer-deps-cleanup` | Move `SectionConfig`/`AnimeFilterMode` to `domain/model`; move the list scroller helper to core | STR-1, STR-2 | refactor | M | structure | compile + smoke |
| `tmdb-result-handling` | `runCatchingCancellable` instead of `apiResult`; one `find` call with one failure policy behind the TV-only and TV-then-movie lookups; log the lookup failure in `SkipSegmentService` | SOLID-13 | refactor | S | SOLID | smoke (episode schedule, skip segments) |
| `vm-mutation-queue` | One tested `MutationQueue` (serialisation opt-in, `onCreated` hook) replacing the three `launchMutation`/`awaitPendingMutations` variants in `DetailsVM`, `PlayerVM`, `CollectionDetailVM` | SOLID-12 | refactor | S | SOLID | yes (close after a bookmark or watched toggle); `PlayerVM` part after `player-overlays-port` |
| `history-vm-split` | Extract the deletion queue and reconciliation from `HistoryVM` | SOLID-7 | refactor | M | SOLID | yes |
| `playback-controller-split` | Extract text-track matching and debug info from `PlaybackController`; move engine classes out of `vm/` | SOLID-4, STR-3 | refactor | M | SOLID | yes (playback) |
| `coil-shared-http` | Give Coil the shared OkHttp/DoH client | PERF-5 | perf | S | performance | yes (posters on DoH-only network) |
| `player-position-state` | Publish playback position outside `PlayerContentState`; measure before and after | PERF-1 | perf | M | performance | yes (perf route) |
| `paginator-hardening` | Characterization tests, then collapse duplicate branches, keyed O(n) merge, thread-safe `render`/`state`; lazy expiry in `TypedTtlCache` | PERF-2, PERF-3, BP-6 | refactor | M | performance | yes (every paged list) |
| `cached-feed-key-maps` | Bound `CachedFeed`'s per-key epoch maps | PERF-4 | perf | M | performance | no (unit-level) |
| `app-coroutine-scope` | One injected application scope with an exception handler | BP-5 | refactor | S | best practices | smoke |
| `focus-request-helper` | One `tryRequestFocus()`; fix the two `.isSuccess` misreadings | BP-3 | bug | M | best practices | yes (focus) |
| `dead-code-sweep` | Remove unused API endpoints, models and `ContentFilter` (user picks what stays); fix `paging-and-filters.md`; one API `Json` | BP-1, BP-7, STR-4 | refactor | M | best practices | compile + smoke |
| `dependency-prune` | Drop `ktor-client-logging`, `coil-video`, `security-crypto`; review `appcompat` | BP-4 | chore | S | best practices | yes (release build) |
| `screen-error-states` | Shared error/loading states on Home and Settings; deadline-based pairing countdown | BP-8, BP-9 | feature (visual) | S | best practices | yes |
