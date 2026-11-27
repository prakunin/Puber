# Design (architect): baseline-mock-protocol-parity

Base 08f50b81 (hardening merged; no rebase pending). Citations checked in `/home/dev/orca/workspaces/Puber/baseline-mock-protocol-parity`.

## Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D1 | How to serve both codecs | One `BaselineMockBackend` owns two `MockWebServer`s on 127.0.0.1: `h1` with `protocols = listOf(Protocol.HTTP_1_1)` on `puber.baselineMockPort` (18765), `h2` with default protocols on new `puber.baselineMockH2Port` (18766) | Brief option A; one lock, one route record and one `verify()` keep `JourneyCheck` and `BaselineProfileTestControl` unchanged |
| D2 | Which server serves what | `h1`: `/v1`, `/oauth2`, `/media/*` (hosts `api.baseline.test`, `127.0.0.1`). `h2`: `POST /dns-query` (`dns.baseline.test`) and `/github/...` (`127.0.0.1`) | Brief Q1 split |
| D3 | Wrong-server requests | Each route names its server; each server gets its own dispatcher over the shared route table, and a route of the other server counts as unknown (recorded, 404) | A misrouted origin fails AC-5 (`unknown=[]`) instead of silently reverting to one protocol |
| D4 | Chunked vs fixed bodies | JSON 200 bodies on `h1` use `chunkedBody`; `h1` media and error bodies stay fixed-length; every `h2` body stays fixed-length | Restores both `ChunkedSource` and `FixedLengthSource`; chunk framing is HTTP/1.1-only and would corrupt an h2 body |
| D5 | Headers | Every response (both servers, 404 included) adds `Date` with the current time in RFC 1123 (`DateTimeFormatter.RFC_1123_DATE_TIME`, UTC); no `Cache-Control` | AC-2; `KinoPubApiClient` has an OkHttp `Cache` (`app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:146`) whose strategy parses `Date`; adding freshness headers would change which requests reach the mock |
| D6 | Origins on the app side | `mockOrigin()` stays the `h1` origin (images, stream, fixture `{{MOCK}}`); new `mockH2Origin()` feeds the GitHub base; API on `h1`, DoH URL on `h2`. `allowedOrigins()` lists exactly `127.0.0.1:h1`, `api.baseline.test:h1`, `127.0.0.1:h2`, `dns.baseline.test:h2` | AC-3 "each names the server that serves it"; `api:h2` and `dns:h1` stay blocked |
| D7 | Fixture enrichment | `item.json`: `watched: 1`, `videos[0].watched: 1`, `in_watchlist: true`, `bookmarks: [{id:1,title:...}]`, `advert/subscribed/poor_quality: false`, `finished: true`, `fps` (float), `duration: {average: <double>, total}`, one explicit `null` field. `items.json`: one non-first entry with `watched: 1` and the same booleans | Movie checked-bookmark comes from `bookmarks`, not `in_watchlist` (D8); `item.json` serves every id (`BaselineMockBackend.kt:153-156`) |
| D8 | Lighting `fill/BookmarksimpleKt` for a movie | Non-empty `bookmarks` in `item.json`; `in_watchlist: true` stays for AC-4 but only series read it | `DetailsInteractor.kt:49-51` returns `item.bookmarks.orEmpty().isNotEmpty()` for a movie; fixtures carry no series (`seasons` banned, `BaselineMockBackend.kt:328`) |
| D9 | Port literals (Q3) | `app/src/testInstrumentation` tests read `BuildConfig.BASELINE_MOCK_PORT` / `BASELINE_MOCK_H2_PORT`; "other port" cases use a port equal to neither | 18766, today the "differs" port in `BaselineNetworkBlockerTest.kt:49` and `:91`, becomes an allowed port |
| D10 | Guards | New test in `BaselineProfileContentTest` with the seven AC-6 prefixes, via existing `hasRuleFor` | Separate test name reports the codec/serializer/icon group on its own; existing tests untouched |

## Changes by layer

App layers (API/repository, ViewModel, screen and DI, Compose, resources): N/A, AC-8 forbids `app/src/main/java` and `app/src/main/res` changes.

| Area | File | Change |
|---|---|---|
| Mock | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt:21-25` | Constructor gains `h2Port: Int` |
| Mock | `BaselineMockBackend.kt:27`, `:36-37` | `server` becomes `h1Server` (`protocols = listOf(Protocol.HTTP_1_1)`) and `h2Server`; add `boundH2Port` next to `boundPort` |
| Mock | `BaselineMockBackend.kt:43-52`, `:88-94` | `start()` sets HTTPS, protocols and a dispatcher on both, starts both on `LOOPBACK`; `close()` closes both (second close runs even if the first throws) |
| Mock | `BaselineMockBackend.kt:96-106` | `routeDispatcher(server)`: a route matches only on its own server (D3) |
| Mock | `BaselineMockBackend.kt:114-168`, `BaselineMockRoute` `:264-277` | `route(...)` takes a server, default `h1`; `:157` (`/github`) and `:158` (`/dns-query`) on `h2`; `json()` chunks on `h1` only (D4) |
| Mock | `BaselineMockBackend.kt:213-225` | `textResponse`/`binaryResponse` add `Date`; `textResponse` takes a chunked flag |
| Control | `BaselineProfileTestControl.kt:18-31`, `:118` | Reads `baselineMockH2Port` runner argument (required, same error style) and passes it |
| Build | `gradle.properties:5` | Add `puber.baselineMockH2Port=18766` |
| Build | `app/build.gradle.kts:246` | Add `buildConfigField("int", "BASELINE_MOCK_H2_PORT", ...)` from the new property |
| Build | `baselineprofile/build.gradle.kts:23-24` | Add runner argument `baselineMockH2Port` |
| App (instrumentation) | `app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt:17`, `:22-26`, `:45` | Add `mockH2Origin()`; `allowedOrigins()` per D6; DoH URL `https://dns.baseline.test:<h2>/dns-query`; KDoc at `:21` names four origins |
| App (instrumentation) | `BaselineProfileApp.kt:26` | GitHub base `mockH2Origin() + "/github"` |
| App (instrumentation) | `BaselineNetworkBlocker.kt:13-17` | KDoc only: origins follow `allowedOrigins()` |
| Fixtures | `baselineprofile/src/main/assets/baseline/item.json`, `items.json` | D7; no `imdb`, `trailer`, `subtitles`, `seasons`, no URL outside `{{MOCK}}` |
| Tests | `BaselineMockSelfTest.kt:31-44`, `:52`, `:72` | DoH URL on `boundH2Port`; `/github` via the h2 origin; client for `api.baseline.test` trusts the leaf and resolves through the mock DoH. New: protocol per host (AC-1), chunked JSON + fixed-length media + `Date` on every response (AC-2), route on the wrong server is unknown (D3) |
| Tests | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineInstrumentationEnvironmentTest.kt:53-56` | Expected URLs from BuildConfig; asserts DoH on h2 port and API on h1 port (AC-3) |
| Tests | `BaselineNetworkBlockerTest.kt:17-21`, `:49-52`, `:59`, `:69-70`, `:81-84`, `:91-94` | Allowed set = `BaselineInstrumentationEnvironment.allowedOrigins()`; add `api:h2` and `dns:h1` rejected (AC-3) |
| Tests | `BaselineFixtureContentTest.kt:25` | Origin from BuildConfig; new tests: `item.json`/`items.json` decode as `Item` with the app's lenient Json, a watched item, `in_watchlist: true`, a boolean, a float (`fps`) and a double (`duration.average`) field (AC-4) |
| Tests | `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:60-68` | New sibling test with the AC-6 prefixes (D10) |
| Docs | `docs/release.md:121` | Name both ports and which host each serves |
| Output | `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | Regenerated on `host.docker.internal:5555` after the code lands (AC-5..AC-7) |

Code the fixtures exercise, for the test designer: watched badge `app/src/main/java/com/kino/puber/core/ui/uikit/component/moviesList/VideoItemUIState.kt:309-310`, `:373`; details `ui/feature/details/component/DetailsScreenContent.kt:530` (bookmark), `:550` (eye); player `ui/feature/player/component/PlayerButtonRow.kt:141-145` (movie: `PlayerInteractor.kt:133-137`); movie watched rule `data/api/models/Models.kt:164-166`; float/double fields `Models.kt:95` (`fps`), `:210` (`average`).

## Alternatives rejected

| Alternative | Reason |
|---|---|
| One server, `protocolNegotiationEnabled = false` (research B) | Cools the 291 `http2` rules on the DoH and GitHub paths |
| Fixtures and headers only (research C) | `http1` stays at 0 |
| Two `BaselineMockBackend` instances | Two route records, two `verify()` merges in `BaselineProfileTestControl` and `JourneyCheck` |
| Shared route table with no server ownership | A wrong origin passes unnoticed and quietly reverts the protocol split |
| `h2` port = `puber.baselineMockPort + 1` without its own property | Brief decision: own Gradle property |
| `Cache-Control` headers on API responses | Changes OkHttp cache hits and so the request pattern the journeys rely on |

## Risks

| # | Area | Risk | Lives in | Mitigation |
|---|---|---|---|---|
| R1 | Baseline profile | AC-7 thresholds missed for serialization or icons (fixtures restore only what journeys decode/draw; `json/internal` losses are untraced) | `item.json`, `items.json`, journeys in `BaselineProfileGenerator.kt:71-188` | qa counts per package; a shortfall is a finding for triage, not a reason to add `imdb`/`trailer` (Q2) |
| R2 | Correctness | Watched/bookmarked `item.json` changes the Playback journey: initially focused action or resume path differ; player check `BaselineProfileGenerator.kt:177-179` fails | `item.json`, `PlayerInteractor.kt:118-137` | Generation fails loudly on that check; fallback is watched only in `items.json` and bookmark only, recorded as a question |
| R3 | Correctness | Misrouted origin (e.g. GitHub left on `mockOrigin()`) | `BaselineProfileApp.kt:26`, `BaselineInstrumentationEnvironment.kt:22-26`, `:45` | D3 makes it an unknown request; `BaselineInstrumentationEnvironmentTest` and `BaselineNetworkBlockerTest` pin each origin |
| R4 | Data shape | Chunked framing on an h2 response corrupts the body | `BaselineMockBackend.kt:125`, `:213-218` | D4 by construction; self-test asserts DoH and `/github` decode on h2 |
| R5 | Concurrency | HTTP/1.1 opens up to 5 parallel connections per host (images, API) where h2 multiplexed one; dispatcher threads from both servers contend | `BaselineMockBackend.kt:96-106` | All state already under `lock`; `awaitQuiescence` (`BaselineProfileTestControl.kt:88-104`) counts both servers through `requestCount` |
| R6 | Lifecycle | Second server fails to bind (port taken) and the first leaks; or `close()` stops after one | `BaselineMockBackend.kt:43-52`, `:88-94` | Start `h2` after `h1` and close `h1` on `h2` start failure; `close()` closes both |
| R7 | Security / rollout | Egress widened by mistake to `api:h2` or `dns:h1` | `BaselineInstrumentationEnvironment.kt:22-26` | Blocker tests assert both are rejected; generation egress journal must be empty (AC-5) |
| R8 | Maintenance | Guards on library internals break on an okhttp/kotlinx/phosphor bump | `BaselineProfileContentTest.kt` | Top-level class names only (D10) |

## Open questions

N/A: Q1-Q3 answered in `state.md`; no AC change needed.


## Round 2 delta (Q4: series fixtures, AC-7 descriptors >= 163)

Checked in the worktree at 6fcea29c. Gap source: `comm` of `grep kotlinx/serialization/descriptors` lines, `git show 5cd3e37e:app/src/main/generated/baselineProfiles/baseline-prof.txt` vs the 6fcea29c profile (203 vs 149). Lines that differ only in flags (`HSP` vs `HP`) cancel out; what is missing in the 6fcea29c profile is below.

| Missing group | Classes (rule lines) | Lit by |
|---|---|---|
| Series models | `Season`, `Episode` (`getDescriptor`, `write$Self`) | `seasons` in a details payload, decoded and written to the item cache (`ContentCacheRepository.kt:38`) |
| Track models | `Audio`, `AudioType`, `AudioAuthor`, `Translation`, `SubtitleLink` | `audios`, `files[].translation`, `subtitles` of an episode or video (`Models.kt:282`, `:285`, `:586`) |
| Item lists | `Country`, `Tracklist`, `Genre.write$Self` | `countries`, `tracklist`, `genres` on a details item (`Models.kt:52-53`, `:76`) |
| Unknown-key path (library, about 30 lines) | `JsonNamesMapKt` (slow path, names map), `StreamingJsonDecoder.handleUnknown`, `DescriptorSchemaCache`, `PluginGeneratedSerialDescriptorKt.hashCodeImpl`, `SerialDescriptorKt.elementDescriptors`, `SerialKind.hashCode`, `NullableSerializer`/`SerialDescriptorForNullable` descriptors | A JSON key the model does not declare: `ignoreUnknownKeys` (`KinoPubApiClient.kt:133-137`) sends it through the alternative-names lookup, which hashes the descriptor into the schema cache. Inferred from the call chain, not traced on device |
| Out (Q2 / not reached) | `Trailer`, `IntroDbApp*` (need `trailer`, `imdb`), `BookmarkFolder*` (folder lookup skipped when `bookmarks` is non-empty, `DetailsInteractor.kt:56-59`), cache records `ItemListRecord`/`ItemPageRecord`/`HistoryPageRecord`, `Pagination`, `ItemTypeSerializer` descriptors (untraced) | - |

Why the four keys were banned: hermetic-baseline-generation D9 (`docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md:95`) kept TMDB, IntroDB and trailer lookups off by construction. What actually reaches outside: `imdb` (skip segments `SkipSegmentInteractor.kt:14-19`, TMDB id `SkipSegmentService.kt:56-63`, schedule button `DetailsScreenUIMapper.kt:185`) and `trailer` (`TrailerLinkInteractor.kt:22-24`). `seasons` and `subtitles` start no lookup: a series details screen requests only `items/{id}` and `items/similar` (`DetailsVM.kt:98-131`; series watchlist comes from `in_watchlist`, `DetailsInteractor.kt:50`, `:55`); subtitle URLs are fetched only by the player (`PlayerVM.kt:447`). The season, episode, subtitle and notification endpoints (`KinoPubApiClient.kt:908`, `:914-934`, `:937-961`) have no caller outside the client.

### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D11 | How many series, where | One series, id 2, in a new fixture `item_serial.json`: `type: "serial"`, `in_watchlist: true`, one season (`number: 1`) with two episodes; episode 1 `watched: 1`, episode 2 `watched: 0` with `watching`; each episode has `thumbnail` = `{{MOCK}}/media/wide.jpg` and `files[0].url.hls4` = `{{MOCK}}/media/stream.m3u8`, `audios[0]` with `type` and `author`, `files[0].translation`, and `subtitles[0].url` = `{{MOCK}}/media/subtitle.vtt`. No `imdb`, no `trailer` | Lights both series and track groups; every URL on the loopback mock |
| D12 | Which id, and does a journey have to open the series | Id 2 is the second card of every Home row (`items.json` index 1); no journey change. It is the right-hand neighbour of the card focus starts on, which `DetailsPrefetcher` warms after 750 ms (`DetailsPrefetcher.kt:38`, `:76`, `:92`); every journey idles at least 2 s there before its first key (`BaselineProfileGenerator.kt:67`, `:75`, `:114`, `:167`). The prefetch decodes the payload and writes it to the item cache, which is what the descriptor lines need | Smallest change; `BaselineProfileGenerator.kt` stays untouched. Browse may also open it (bonus, not relied on) |
| D13 | Route | New route `/v1/items/2` listed before `/v1/items/\\d+` (`BaselineMockBackend.kt:183`), present in every scenario except Playback, `required = browse`, chunked JSON on h1 | Its own description makes the hit visible in the route journal; `required` makes a miss fail the Browse iteration instead of silently missing AC-7 |
| D14 | Playback stability | In Playback the series route is absent, so `/v1/items/2` falls through to `item.json` (movie) as today; `item.json` keeps no `audios`, `subtitles` or `translation` | Playback serves byte-identical details for every id, so its Play focus and the player check (`BaselineProfileGenerator.kt:176-180`) cannot change, whichever card it opens |
| D15 | List entry | `items.json` id 2 becomes `type: "serial"` with `watched: 1`, `new: 1`, `total: 2` (unwatched counter, `VideoItemUIState.kt:305-315`); the watched-movie fields of round 1 (D7) move from id 2 to id 3 | List and details agree on the type; `itemFixtures_carryWatchedAndBookmarkedMovie` still finds a non-first watched movie |
| D16 | Cheap levers without new keys | `item.json` and `item_serial.json` add `genres`, `countries`, `tracklist` (no `url`), and all three item fixtures (`items.json`, `item.json`, `item_serial.json`) add one key no model declares (`"unmodelled": 0` on each item) | Item lists and the unknown-key path (gap table) need no banned key and no new route; they are UI-only, so Playback is unaffected |
| D17 | Ban policy | `BANNED_KEY` (`BaselineMockBackend.kt:377`) becomes `imdb|trailer`; a new `SERIES_ONLY_KEY` `seasons|subtitles` fails `validate()` (`:362-369`) in any fixture other than `item_serial`. `NAMES` (`:372-375`) gains `item_serial`; KDoc `:349-352` and `:136-140` updated | Keeps the by-construction guarantee: only the one unplayed series carries subtitle or season data |

### Changes by layer (delta)

App layers: N/A, AC-8 unchanged.

| Area | File | Change |
|---|---|---|
| Mock | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt:160-186` | Series route per D13/D14 before the generic items route |
| Mock | `BaselineMockBackend.kt:362-377` | D17 |
| Fixtures | `baselineprofile/src/main/assets/baseline/item_serial.json` (new), `items.json`, `item.json` | D11, D15, D16 |
| Tests | `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineFixtureContentTest.kt:119-128` | Becomes: no `imdb`/`trailer` anywhere, `seasons`/`subtitles` only in `item_serial.json` |
| Tests | `BaselineFixtureContentTest.kt` (new tests) | `item_serial.json` decodes as `ApiResponse<Item>`, `isSeriesLike()`, one season, two episodes each on `$origin/media/stream.m3u8`, an episode with `audios`, `subtitles` on the mock origin, `inWatchlist == true`; `items.json` id 2 is a series; each item fixture has a key outside `Item.serializer().descriptor` element names; `item.json` has `countries`, `genres`, `tracklist` |
| Tests | `BaselineFixtureContentTest.kt:130-146` | Posters check also covers `item_serial.json` and its episode thumbnails |
| Tests | `baselineprofile/.../BaselineMockSelfTest.kt` | `/v1/items/2` returns the series in BrowseAndDetails and the movie in Playback; `validate()` rejects `seasons` outside `item_serial` |
| Output | `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | Regenerated on `host.docker.internal:5555`; evidence records `GET /v1/items/2` matched per journey and the AC-7 counts |

### Alternatives rejected (delta)

| Alternative | Reason |
|---|---|
| Make `item.json` itself a series | Playback would open a series: Play resolves the first unwatched episode (`PlayerInteractor.kt:87-110`, `:259`) and loads its subtitles |
| Series for every id except 1 | Playback then depends on opening id 1; Browse loses its movie details |
| Add a journey step that opens the series | Card position is not known statically; touches `BaselineProfileGenerator.kt` |
| `audios`/`subtitles` on the movie in `item.json` | Reaches the player in Playback (`ContentStateFactory.kt:36-38`, `PlayerVM.kt:447`) |
| Mock route for `/media/subtitle.vtt` | Nothing plays the series; an accidental play then fails loudly as an unknown loopback request |
| `imdb` or `trailer` | Q2; IntroDB/TMDB/trailer lookups |

### Risks (delta, highest first)

| # | Area | Risk | Lives in | Mitigation |
|---|---|---|---|---|
| R9 | Baseline profile | Descriptors still under 163: the unknown-key share is inferred, series lines need the prefetch | fixtures, `DetailsPrefetcher.kt:76-97` | qa reruns the `comm` above on the new profile and names what is still missing; levers left are listed as Out |
| R10 | Correctness / flakiness | `required` series route missed in a Browse iteration (focus never rests next to id 2, prefetch deferred by `awaitSettled`) and generation fails | `BaselineMockBackend.kt` route, `DetailsPrefetcher.kt:123` | Failure names `GET /v1/items/2` in `missing`; fallback is `required = false` with the match count recorded in evidence, decided at triage |
| R11 | Correctness | Series reaches the player (Browse opens id 2 and something presses Play) and requests `/media/subtitle.vtt` | `item_serial.json`, journeys | No journey presses Play outside Playback; an accidental request is a loopback unknown route, so generation fails without egress |
| R12 | Data shape | List says series, a cached movie record for id 2 (Playback) merges with it on a later scenario | `ContentCacheRepository.kt` merge | `clearTarget` drops caches every iteration (`BaselineProfileGenerator.kt:52`) |
| R13 | Rollout / security | A later fixture edit adds `seasons` or `subtitles` to a played item | `BaselineMockBackend.kt:362-377` | `validate()` and the unit test reject them outside `item_serial` |

### Open questions (delta)

| ID | Question | Recommended | Blocks |
|---|---|---|---|
| Q5 | Q4 named `seasons`; D11 also lifts `subtitles` (episode only, never played) for `SubtitleLink`. Accept? | Yes: no lookup, loopback URL, guarded by D17 | no |
| Q6 | AC-4 says fixtures contain no `imdb`, `trailer` or non-loopback URL; adding a series needs no AC change. Should the spec's test-design rows for `fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys` be renamed in place? | Yes, test-designer updates the row and adds the series rows | no |
