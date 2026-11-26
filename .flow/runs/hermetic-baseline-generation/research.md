# Research: hermetic-baseline-generation

## 1. Request

item 2 of the split of hermetic-baseline-profiles. Generate baseline profiles against a loopback mock backend with synthetic auth instead of a real account: an instrumentation flavor (or equivalent) that uses the seams just merged in hermetic-baseline-seams (KinoPubConfig pinned endpoint, HttpStack without DoH when pinned, PuberApp hooks shouldInitializeRuntime/configureRuntime/runtimeModules); synthetic auth; an egress blocker that fails the run on any non-loopback request; a mock backend serving all four generator journeys incl. playback with a tiny synthetic stream in :baselineprofile assets (owner Q2); cache reset; delete the nonMinifiedRelease auth provider, AuthEventLog and the pairing steps; rewrite the profile section of docs/release.md; regenerate the committed profiles on the emulator. Absorbs baseline-auth-provider-presence and baseline-generation-hardening F1 plus the false "not paired" warning. Reference: local branch upstream-sync-triage commits 0b6253a9, c17847cc, 5709c601.

## 2. Understanding

The seams are on master (8a7adbe0), so this run is the rest of the umbrella AC-4..AC-9 (`../hermetic-baseline-profiles/brief.md`): a third app flavor `instrumentation` (own package, blank secrets) whose `Application` subclass pins the API to a loopback mock, swaps the token store for a synthetic one, disables the GitHub update check and installs a proxy-level egress blocker; a mock in the `:baselineprofile` test process that answers every request the four journeys make and fails on anything else; wiring so `make baseline-profile` generates from that flavor; removal of today's paired-account route; regenerated profiles. The branch code is a usable base, but it predates master's rail and playback journeys, three new token-store methods and the all-`H` route. Two gaps are new: the branch mock carried no media and no images, and `HttpsEnforcingInterceptor` makes loopback `http` posters unloadable.

## 3. Current state in code

Checked at 5cd3e37e. App paths are under `app/src/main/java/com/kino/puber/` unless they start with `app/`, `baselineprofile/`, `tools/` or `docs/`.

### Seams already merged (hermetic-baseline-seams)

| Where | Fact |
|---|---|
| `PuberApp.kt:69`, `:78-88`, `:92-99` | Class `open`; `shouldInitializeRuntime()` gates everything after `super.onCreate()`; `configureRuntime()` runs after `TelemetryRuntime.install` and before `startKoin`; `runtimeModules()` is appended last (`:112`), so its definitions override |
| `PuberApp.kt:125-162` | `newImageLoader` is an `override` in an open class, so a subclass can override it; it adds `HttpsEnforcingInterceptor` (`:151`) and the Ktor fetcher on `ImageHttpClientQualifier` (`:152-158`) |
| `data/api/config/KinoPubConfig.kt:25-26`, `:55-56`, `:63`, `:84-90` | `pinnedEndpoint` wins over the snapshot in every getter; `setPinnedEndpoint` has no caller yet |
| `domain/interactor/api/ApiDomainInteractor.kt:63`, `:81`, `:97`, `:106`, `:127`, `:136`, `:211` | All seven mutators return early when pinned; `autoResolveWorkingDomain` returns `Success`, so `AppStartup.warmUp` (`core/startup/AppStartup.kt:59-68`) proceeds to the Home warm-up |
| `data/di/modules.kt:71-76`, `data/api/network/HttpStack.kt:28-39` | `useDoh = !IS_PINNED_ENDPOINT`: pinned stack uses the system resolver, no `dns.google` client |

### Generation route today (to be replaced)

| Where | Fact |
|---|---|
| `app/build.gradle.kts:58-61` | `CLIENT_SECRET`, `TMDB_READ_ACCESS_TOKEN`, `API_DOMAIN_OVERRIDE` from `.env`/env in `defaultConfig` |
| `app/build.gradle.kts:119`, `:223-236` | One dimension `buildType`; flavors `dev` (`.stage`) and `prod` |
| `app/build.gradle.kts:202-219` | Build types `deploy`, `nonMinifiedRelease`, `benchmarkRelease` (debug-signed) |
| `app/build.gradle.kts:440-445` | `configurations.configureEach` adds `:baselineprofile` only to `devReleaseBaselineProfile` (a0121189; a plain `"devBaselineProfile"(...)` did not resolve, `docs/superpowers/plans/2026-11-23-release-startup-compilation.md:247`) |
| `app/build.gradle.kts:446-453` | `saveInSrc`, `automaticGenerationDuringBuild = false`, `mergeIntoMain = true`; no `filter {}` |
| `app/src/nonMinifiedRelease/java/com/kino/puber/profile/BaselineProfileAuthProvider.kt:39` | Exported provider; its `onCreate` installs `AuthEventLog` |
| `app/src/nonMinifiedRelease/java/com/kino/puber/profile/AuthEventLog.kt:22-40` | `PuberAuth` logger; `hasValue` is hardening F1 |
| `app/src/{dev,prod}NonMinifiedRelease/AndroidManifest.xml` | Register the provider at `${applicationId}.profile.auth` |
| `baselineprofile/build.gradle.kts:18-28`, `:37-39`, `:41-46` | Flavors `dev`/`prod` with `targetAppId`; `useConnectedDevices = true`; no mock dependency |
| `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt:28-34` | Refuses below API 34 (pairing-preservation reason) |
| `BaselineProfileGenerator.kt:36-48`, `:50-90`, `:92-142`, `:149-177` | Journeys `startupProfile` (includeInStartupProfile), `browseAndDetailsProfile`, `railNavigationProfile` (`maxIterations = 5`, title checks), `playbackProfile` (waits for resource id `player`, 5 s in the player) |
| `BaselineProfileGenerator.kt:183-192` | `requirePairedApp()` queries the token provider after every journey |
| `BaselineProfileGenerator.kt:194-200` | `railShows` searches the whole screen (hardening F5, stays with item 3) |
| `baselineprofile/.../StartupBenchmarks.kt:19-20`, `:29-31` | Uses `targetAppId` of whatever flavor runs it; documented entry is a `connected...` task the hook refuses |
| `tools/generate-baseline-profile.sh:13-17` | Exits 2 without `PUBER_CLIENT_SECRET` |
| `tools/generate-baseline-profile.sh:26-51` | Preflight: reachable, emulator, API >= 34 |
| `tools/generate-baseline-profile.sh:54-86`, `:117-121` | Pairing check and `devNonMinifiedRelease` install; post-run "not paired" warning (the false warning) |
| `tools/generate-baseline-profile.sh:109` | logcat filter includes `PuberAuth` |
| `Makefile:24-26` | `PACKAGE_dev`, `PACKAGE_prod`; no instrumentation package |
| `Makefile:141-179` | `baseline-profile`: private adb 5038, API/emulator refusals, `H` strip (`:169`), `:app:generateBaselineProfile` (`:170`), `H` re-mark on success (`:174`), trap INT TERM only (`:168`) |
| `docs/release.md:99-133` | Profile section: secrets from `.env` (`:105-107`), account write (`:120-121`), pairing (`:130-133`) |
| `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:21-58` | Required packages, removed classes, all method rules `H`, no details/player in the startup profile; no check against `com/kino/puber/profile/` |
| `app/src/main/generated/baselineProfiles/*.txt` | Each file holds 20 `com/kino/puber/profile/` rules (`AuthEventLog`), so today's profile already fails umbrella AC-6 |

### What each journey requests (from code)

All KinoPub paths come from `KinoPubConfig` (`data/api/KinoPubApiClient.kt:319-924`), so the pin moves them to the mock. The bearer is sent to the pinned host because `shouldSendKinoPubToken` compares against the pinned base URLs (`KinoPubApiClient.kt:1254-1257`). The OkHttp disk cache lives in `cacheDir/okhttpcache` (`:145`).

| Journey | Requests | Source |
|---|---|---|
| Every launch | `watching/serials?subscribed=1`, `items/hot?type=movie` (warm-up) | `AppStartup.kt:59-68`, `domain/interactor/home/HomeInteractor.kt:113`, `:178` |
| Every launch, Home | `items/{hot,fresh,popular}` (more pages only while `pagination.current < total` and anime is filtered), `bookmarks`, `bookmarks/{id}`, `collections` | `HomeInteractor.kt:130-146`, `:178-210` |
| Every launch, Main | watch-state catch-up `watching/serials`, `watching/movies`, `history?page&perpage`; `device/notify` POST after `StartupSlot.DeviceInfo` | `domain/interactor/watchstate/WatchStateSyncInteractor.kt:222-267`, `ui/feature/main/vm/MainVM.kt:113-120`, `data/repository/DeviceInfoRepository.kt:106-109` |
| Every launch | TV launcher row `history`; GitHub `api.github.com` release check (off when `IAppUpdateInteractor` is replaced) | `core/tvhome/ContinueWatchingSource.kt:15`, `ui/feature/update/vm/UpdatePromptVM.kt:60`, `KinoPubApiClient.kt:960` |
| browseAndDetails | `items/{id}`, `items/similar?id`, `bookmarks/get-item-folders?item` (watch-later check); `items/trailer` only when the item has a `trailer` without a playable URL | `data/repository/ItemDetailsRepository.kt:64`, `:97`, `domain/interactor/details/DetailsInteractor.kt:201`, `domain/interactor/trailer/TrailerLinkInteractor.kt:22-25` |
| railNavigation | Search: nothing (no text typed). Favourites: `watching/serials?subscribed=1` + `items/{id}`. History: `history`. Movies/Series/4K: `items` / `items/{shortcut}` with type, sort, quality, genre; `genres?type`; `items/{id}` of the focused card | `domain/interactor/favorites/FavoritesInteractor.kt:24`, `ui/feature/favorites/vm/FavoriteVM.kt:65`, `domain/interactor/history/HistoryInteractor.kt:48`, `domain/interactor/contentlist/ContentListInteractor.kt:200-202`, `domain/interactor/genre/GenreInteractor.kt:23`, `ui/feature/contentlist/vm/ContentListVM.kt:77` |
| playback | `items/{id}`; stream from `videos[0].files[0].url` in order `hls4`, `hls`, `http` (HLS when `hls*`, `MimeTypes.APPLICATION_M3U8`), optional `ac3default=1` query; on exit `watching/marktime?id&video&time` and a watch-state catch-up (`watching/serials`, `watching/movies`, `history`); `watching/toggle` only if less than 10 % remains or playback ended | `domain/interactor/player/PlayerInteractor.kt:218-240`, `:253-257`, `:276-278`, `ui/feature/player/vm/PlaybackMediaItemFactory.kt:43`, `ui/feature/player/vm/PlayerVM.kt:1898-1907`, `:1940-1948`, `:2016-2039` |
| playback, transport | Media goes through `OkHttpDataSource` on a copy of the shared client (system DNS) wrapped in `CacheDataSource` writing to `externalCacheDir/media_cache` | `ui/feature/player/vm/PlaybackController.kt:623-643`, `data/di/modules.kt:142-149` |

Off by construction: TMDB when the token is blank (`data/api/TmdbApiClient.kt:33-34`); IntroDB/TheIntroDB only when the item has `imdb` (`domain/interactor/player/SkipSegmentInteractor.kt:15-19`); trailers when `trailer` is absent. Remaining non-KinoPub hosts: `api.github.com`, `dns.google` (gone when pinned), image and media URLs from payloads.

Fixture shapes the code needs: `Item` (`data/api/models/Models.kt:46-71`), `Posters` small/medium/big/wide (`:215-220`), `Video` with `number`, `duration`, `files` (`:274-287`; `saveCurrentPosition` skips the save without `number`), `VideoFile.url` `http`/`hls`/`hls2`/`hls4` (`:579-595`).

### Token store and update interfaces since the branch

| Where | Fact |
|---|---|
| `data/repository/ICryptoPreferenceRepository.kt:17-19` | Three methods newer than the branch: `saveSessionEndReason`, `getSessionEndReason`, `clearSessionEndReason` (session-revoked-pairing); the branch `SyntheticAuthPreferences` does not compile without them |
| `data/di/modules.kt:102` | Only DI references the concrete `CryptoPreferenceRepository`, so a `runtimeModules()` override covers every reader |
| `domain/interactor/update/IAppUpdateInteractor.kt:8-28` | Unchanged; the branch `NoOpAppUpdateInteractor` still fits |

### Image http->https gap

| Where | Fact |
|---|---|
| `core/image/HttpsEnforcingInterceptor.kt:9-13` | Every `http://` image URL is rewritten to `https://`; a poster at `http://127.0.0.1:<port>` becomes a TLS call to a plain-HTTP mock and fails |
| `app/src/main/res/xml/network_security_config.xml:3-17`, `:28-32` | Main config already permits cleartext to `127.0.0.1` and `localhost`; release base config trusts system CAs only, so an HTTPS mock needs a flavor-level config with its own trust anchor |
| `core/ui/model/VideoItemUIMapper.kt:36-38`, `ui/feature/home/model/HomeUIMapper.kt:39-41` | Cards and Home hero read `posters.medium/big/wide` |
| Committed profile (5cd3e37e), rule lines baseline / startup | `coil3/` 1271 / 1187, `coil3/network/` 158 / 128, `coil3/decode/` 83 / 83, `io/ktor/` 2344 / 2242, `androidx/media3/` 4804 / 5, `androidx/media3/exoplayer/hls/` 376 / 1, `com/kino/puber/` 9458 / 5038 (comparison base for the regenerated profile) |

### Release packaging

| Where | Fact |
|---|---|
| `.github/workflows/release.yml:104` | Release CI builds only `assembleProdRelease` |
| `.github/workflows/pr-checks.yml:38`, `:63`, `:95`, `:127` | `detektAll`, `lintProdDebug`, `testProdDebugUnitTest`, `assembleProdDebug` |
| `.flow/config.yaml:13` | `check.full` = `testDevDebugUnitTest :app:detektAll :app:lintDevDebug`; `detektAll` = `detektDevDebug` + `detektDevDebugUnitTest` (`app/build.gradle.kts:342-346`), so `app/src/instrumentation` sources would get no detekt or lint |
| `tools/release.sh:72` | Compiles `DevDebug` and `ProdRelease` only |

## 4. Related work

| Item | Relation |
|---|---|
| `hermetic-baseline-profiles` (umbrella, idea) | Brief AC-4..AC-9 and Q1-Q3 are this run's; Split row 2 |
| `hermetic-baseline-seams` (done, 8a7adbe0) | Seams this run calls; its spec says item 2 should need no further main-source change (`docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md:64`, `:75`, `:113`) |
| Branch `upstream-sync-triage` 0b6253a9 | `instrumentation` flavor (`.instrumentation`, blank secrets, `BASELINE_MOCK_PORT` from `puber.baselineMockPort=18765`), `BaselineProfileApp`, `BaselineInstrumentationEnvironment`, `BaselineNetworkBlocker` (`ProxySelector` + file journal), `BaselineProfileControlReceiver` (own process, `DUMP` permission), flavor `network_security_config.xml`, `androidTestInstrumentation/.../BaselineNetworkIsolationTest.kt`, `mockwebserver3` (okhttp 5.4.0 there, 5.5.1 on master) |
| Branch c17847cc | `BaselineMockBackend` (route table per scenario, 404 + journal on unknown, required routes), `BaselineProfileTestControl` (clear/verify broadcasts, quiescence wait), JSON fixtures in `baselineprofile/src/main/assets/baseline/`, `variants { create("instrumentationRelease") { from(...) } }`, deletion of the auth provider, script and docs rewrite. Fixtures forbid `posters`, `videos`, `files`, `imdb`, `trailer`, any URL; scenarios cover startup, browse, three tabs only; no playback |
| Branch 5709c601 | Consumer `filter { exclude("com.kino.puber.profile.**") }` did not drop instrumentation classes (removed); `filterPredicate` on `com/kino/puber/profile/` instead; `deleteDatabase(PuberDatabase.NAME)` reset (`NAME` made internal) |
| Branch evidence (`docs/superpowers/evidence/2026-10-11-upstream-sync-triage-device.md`, run 4) | D-8 mock generation PASS on attempt 2, journals empty; StartupBenchmarks skipped |
| `baseline-auth-provider-presence` (idea) | Moot once the provider is deleted |
| `baseline-generation-hardening` (idea) | F1 and the false warning come here; F2-F5 (Makefile `.bak`/EXIT/HUP, offline wait, adb kill only when started, `railShows`) stay there, after this run (same files) |
| `player-hermetic-fixtures` (idea) | Plans its own loopback server, blocker and synthetic media (`tools/generate-player-test-fixtures.sh`); can reuse this run's stream asset |
| `startup-benchmark-entry` (idea) | Owns a compliant way to run `StartupBenchmarks`; once `:baselineprofile` has only the `instrumentation` flavor, that class also targets the mock |
| `release-startup-compilation` (done) | Built today's route; decision D3/D12 on `mergeIntoMain` (`docs/superpowers/specs/2026-11-23-release-startup-compilation.md:77`, `:86`) |
| `player-overlays-port` (impl), `catalogue-filters-country-year-status` (blocked) | Not on master; when they land, player and catalogue code changes, and a later refresh needs new mock routes (`countries`, year params) |

Duplicate: none.

## 5. External findings

| Finding | Source |
|---|---|
| With `mergeIntoMain = true` there is a single `generateBaselineProfile` task writing `src/main/generated/baselineProfiles`; `variants { <variant> { ... } }` sets options per variant | https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles (fetched 2026-11-26) |
| `filter { include/exclude("pkg.**") }` exists globally and per variant; the page does not say whether it applies to the merged output (the branch saw it not drop `com.kino.puber.profile.**`, `unverified` why) | same page |
| `BaselineProfileRule` is supported on API 33+ (28+ rooted); `collect(..., filterPredicate)` filters rule lines and "works only when the target application's code is not obfuscated" (true for `nonMinifiedRelease`) | https://developer.android.com/reference/kotlin/androidx/benchmark/macro/junit4/BaselineProfileRule (benchmark 1.5.0, `gradle/libs.versions.toml:28`) |
| A `domain-config` can trust a custom CA with `<trust-anchors><certificates src="@raw/my_ca"/></trust-anchors>` (PEM or DER); `debug-overrides` only applies when `android:debuggable=true`, which `nonMinifiedRelease` is not | https://developer.android.com/privacy-and-security/security-config (fetched 2026-11-26) |
| Since benchmark 1.4.0-alpha02 profiles come from every process of the target (relevant to the control receiver's process) | https://developer.android.com/jetpack/androidx/releases/benchmark (umbrella research) |

## 6. Options

All three share: `instrumentation` flavor in `app/build.gradle.kts` and `:baselineprofile`; `BaselineProfileApp` (three hooks, synthetic auth incl. the three session-end methods, no-op updates); `ProxySelector` blocker, journal and control receiver; `BaselineMockBackend` with route tables for startup, browse+details, rail (six entries) and playback incl. `watching/marktime`, `watching/toggle` optional, query-insensitive paths; a 20-30 s HLS asset in `baselineprofile/src/main/assets`; per-journey reset of `puber.db`, `okhttpcache`, `image_cache`, `media_cache`; `filterPredicate` on `com/kino/puber/profile/`; `variants { instrumentationRelease { from(...) } }` replacing `app/build.gradle.kts:440-445`; `requirePairedApp` replaced by mock and journal verification; deletion of `app/src/nonMinifiedRelease/`, both `*NonMinifiedRelease` manifests and script steps `:13-17`, `:54-86`, `:117-121`; `docs/release.md:99-133` rewritten; `BaselineProfileContentTest` asserts no `com/kino/puber/profile/` rule. They differ in how posters (and TLS) are covered.

| Option | Images and TLS | Trade-offs | Size |
|---|---|---|---|
| A | No posters in fixtures (branch approach); mock on plain `http://127.0.0.1` | Smallest; no main change. Coil network/decode rules (today 158 + 83 in the baseline profile) and OkHttp TLS rules go cold, a regression against today's real-backend profile | M |
| B (recommended) | Mock serves HTTPS on `127.0.0.1` with a loopback test CA; the pinned endpoint and poster/stream URLs are `https://127.0.0.1:<port>`; `app/src/instrumentation/res/xml/network_security_config.xml` trusts that CA for `127.0.0.1` only; tiny poster images in assets; `okhttp-tls` (`HeldCertificate`) in `:baselineprofile` | Keeps `HttpsEnforcingInterceptor` and main sources untouched; API, image and media calls run the same TLS path as production. Adds a test CA (cert in app flavor res, key in `:baselineprofile`), an expiry date to manage, one dependency | M |
| C | Plain-HTTP mock plus a main-source change: `HttpsEnforcingInterceptor` leaves loopback hosts alone, or a fourth `PuberApp` seam for image interceptors | Posters load; TLS rules still cold; reopens main code the seams run meant to close | M |

## 7. Risks

| Risk | Why it matters | Mitigation |
|---|---|---|
| Unseen egress | Any real request repeats the 2026-11-25 pattern | Blocker fails closed and is installed in `configureRuntime()` before Koin; journal checked after every journey; blank TMDB token; no `imdb`/`trailer` in fixtures; no-op updates; generator test with a planted request (umbrella AC-5) |
| Requests that bypass `ProxySelector` | The blocker sees only proxy-aware clients (OkHttp, Ktor-OkHttp, Media3 OkHttp source, `HttpURLConnection`); a raw socket or native client would pass unseen (`unverified` that none exists beyond those found in section 3) | Mock 404s on unknown routes are a second net only for loopback; grep for other HTTP clients in review |
| Fixed fixture details | Branch serves one `details` body (`id` 101) for every `items/\d+`; per-id caches and Favourites/ContentList lookups may mismatch | Echo the requested id or serve per-id fixtures |
| Short stream ends inside the journey | Under 10 % remaining or `STATE_ENDED` triggers `watching/toggle` and the ended overlay (`PlayerVM.kt:1389`, `:1940-1948`), changing what the journey profiles | Stream long enough for the 5 s dwell plus startup (20-30 s) |
| Stream asset authoring | `ffmpeg` is not installed in the `puber-mac` box (`which ffmpeg`: none) | Build the asset once elsewhere and commit it with its recipe (Q2) |
| Media cache satisfies later iterations | `CacheDataSource` reads `media_cache` instead of the mock, so required stream routes look missed | Reset `media_cache` per journey (branch `clearNetworkCaches`) |
| Packaging | Instrumentation classes reaching a shipped profile or APK | Source set `app/src/instrumentation/` only; `filterPredicate`; content test; umbrella AC-7 lists `assembleDevRelease`/`assembleProdRelease` APK classes |
| Static gate blind spot | `check.full` and PR checks never compile or analyse `instrumentation` sources (section 3, Release packaging) | Q3 |
| Variant matrix | Third flavor adds `instrumentation{Debug,Deploy,Release,NonMinifiedRelease,BenchmarkRelease}`; `:baselineprofile` losing `dev`/`prod` also changes where `StartupBenchmarks` points | `:app:generateBaselineProfile --dry-run` must list only `InstrumentationNonMinifiedRelease` tasks |
| Test CA (option B) | A committed loopback key trips secret scanners; a fixed cert expires and breaks generation | Q1; long validity, SAN `IP:127.0.0.1`, trusted only by the `.instrumentation` package |
| Profile drift vs real data | Few items, one movie, no series: series and season paths stay cold | Compare per-package counts with section 3; record gaps in evidence |
| Device | Generation needs API 33+ (box is API 29) | Emulator `host.docker.internal:5555` per owner decision (state.md); other device checks on the box |

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | How are posters covered against the mock (option A, B or C), and if B, is a committed loopback-only test CA (cert in `app/src/instrumentation/res/raw`, key in `:baselineprofile` assets) acceptable? | B, with a committed CA valid for 20+ years, SAN `IP:127.0.0.1`, named as test-only |
| Q2 | The stream asset: format and how it is produced, given no `ffmpeg` in the box | HLS (playlist + TS or fMP4 segments, H.264 + AAC, about 320x180, 20-30 s, under 300 KB), built once with `ffmpeg` on the Mac host and committed with the exact command in a short script or doc |
| Q3 | Add the `instrumentation` variant to the static gate (`check.full` in `.flow/config.yaml`, PR checks)? | Yes: `:app:detektInstrumentationRelease` and `:app:lintInstrumentationRelease` in `check.full`; PR checks unchanged |

## 9. Sources

- Files: `app/build.gradle.kts`, `baselineprofile/build.gradle.kts`, `baselineprofile/src/main/java/com/kino/puber/baselineprofile/{BaselineProfileGenerator,StartupBenchmarks}.kt`, `app/src/nonMinifiedRelease/**`, `app/src/{dev,prod}NonMinifiedRelease/AndroidManifest.xml`, `tools/generate-baseline-profile.sh`, `Makefile`, `docs/release.md`, `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt`, `app/src/main/generated/baselineProfiles/*.txt`, `app/src/main/res/xml/network_security_config.xml`, `app/src/main/AndroidManifest.xml`, `.github/workflows/{release,pr-checks}.yml`, `tools/release.sh`, `.flow/config.yaml`, `gradle/libs.versions.toml`, and under `app/src/main/java/com/kino/puber/`: `PuberApp.kt`, `core/startup/AppStartup.kt`, `core/image/HttpsEnforcingInterceptor.kt`, `core/tvhome/ContinueWatchingSource.kt`, `data/api/{KinoPubApiClient,TmdbApiClient}.kt`, `data/api/config/KinoPubConfig.kt`, `data/api/network/{HttpStack,ImageHttpClient}.kt`, `data/api/models/Models.kt`, `data/di/modules.kt`, `data/repository/{ICryptoPreferenceRepository,DeviceInfoRepository,ItemDetailsRepository}.kt`, `domain/interactor/{api/ApiDomainInteractor,home/HomeInteractor,details/DetailsInteractor,player/PlayerInteractor,player/SkipSegmentInteractor,trailer/TrailerLinkInteractor,watchstate/WatchStateSyncInteractor,favorites/FavoritesInteractor,history/HistoryInteractor,contentlist/ContentListInteractor,genre/GenreInteractor,update/IAppUpdateInteractor}.kt`, `ui/feature/{main/vm/MainVM,details/vm/DetailsVM,contentlist/vm/ContentListVM,favorites/vm/FavoriteVM,update/vm/UpdatePromptVM}.kt`, `ui/feature/player/vm/{PlayerVM,PlaybackController,PlaybackMediaItemFactory}.kt`
- Commits: 8a7adbe0, a0121189, 1ec21652, 0b6253a9, c17847cc, 5709c601 (branch files `app/src/instrumentation/**`, `baselineprofile/src/main/java/com/kino/puber/baselineprofile/{BaselineMockBackend,BaselineProfileTestControl,BaselineProfileGenerator}.kt`, `baselineprofile/src/main/assets/baseline/*.json`, branch spec D11-D19, D28, branch device evidence run 4)
- Runs: hermetic-baseline-profiles, hermetic-baseline-seams (spec `docs/superpowers/specs/2026-11-26-hermetic-baseline-seams.md`), release-startup-compilation, baseline-generation-hardening, baseline-auth-provider-presence, player-hermetic-fixtures, startup-benchmark-entry, player-overlays-port, catalogue-filters-country-year-status
- URLs (fetched 2026-11-26): https://developer.android.com/topic/performance/baselineprofiles/configure-baselineprofiles, https://developer.android.com/reference/kotlin/androidx/benchmark/macro/junit4/BaselineProfileRule, https://developer.android.com/privacy-and-security/security-config; from the umbrella research: https://developer.android.com/jetpack/androidx/releases/benchmark
