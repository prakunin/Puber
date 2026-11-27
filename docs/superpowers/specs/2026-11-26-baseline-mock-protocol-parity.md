# Keep HTTP/1.1, serialization and filled-icon paths in the mock-generated baseline profile

Status: approved
Date: 2026-11-26
Slug: baseline-mock-protocol-parity
Kind: improvement
Size: M
Tracks: perf

<!--
Each section has exactly one owning role; nobody else edits it. The conductor
copies each section verbatim from its owner's output and commits this file in
the worktree after each stage. Cite code as `path:line`; a claim with no
source is a guess.
-->

## Problem / Why

### Request

The hermetic regeneration of `baseline-prof.txt` lost rules against the real-account profile 5cd3e37e (`docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-r2-device.md`). Restore them by making the loopback mock serve HTTP/1.1 as well as HTTP/2 and carry richer fixtures, and guard the restored paths in the content test.

| Package | 5cd3e37e | b27d8e40 |
|---|---|---|
| `okhttp3/internal/http1` | 51 | 0 |
| `kotlinx/serialization/internal` | 197 | 140 |
| `kotlinx/serialization/descriptors` | 73 | 51 |
| `com/adamglin/phosphoricons` | 69 | 51 |

### Risks (analyst)

- Rebase: baseline-generation-hardening (worktree `/home/dev/orca/workspaces/Puber/baseline-generation-hardening`) changes `Makefile`, `tools/generate-baseline-profile.sh`, `BaselineProfileGenerator.kt` (rail check), `.gitignore`, `docs/release.md` and rewrites both profile files. This run branches from, or rebases onto, the master commit that merges hardening, and regenerates the profiles after that rebase; its own diff does not touch those three code files unless a journey must show the watched item.
- Splitting ports ends OkHttp coalescing of `api.` and `dns.baseline.test` onto one h2 connection; check `unknown=[]` per journey (AC-5).
- Guards on library internals can break on a dependency bump; they name stable top-level classes only.
- Device rules: generation and evidence only on the emulator (`ssh mac-emu start`, lease, stop after if no other lease); generation reaches only the loopback mock; no script touches the live KinoPub API.

## Acceptance criteria

- AC-1. Given the mock is started, when `BaselineMockSelfTest` runs on the emulator, then a request to `https://api.baseline.test:<port>` reports `Response.protocol == HTTP_1_1` and a request to `https://dns.baseline.test:<h2 port>/dns-query` reports `HTTP_2`.
- AC-2. Given the mock, when a JSON API route answers, then at least one route sends a chunked body and every response carries a `Date` header (self-test assertion).
- AC-3. Given the instrumentation build, when `allowedOrigins()`, the DoH URL and the GitHub origin are built, then each names the server that serves it and `BaselineInstrumentationEnvironmentTest` and `BaselineNetworkBlockerTest` pass with both ports.
- AC-4. Given the enriched fixtures, when `BaselineFixtureContentTest` runs, then they decode with the app's models; `item.json` is the plain movie (unwatched, empty `bookmarks`, not in watchlist), so details take the `get-item-folders` lookup, which answers with one watch-later folder (`bookmark_folders.json`); a watched item is `items.json` id 3, and `in_watchlist: true` comes from the series fixture and the populated folder lookup; at least one boolean, one float, one double and one explicit `null` field are present; and no `imdb`, `trailer` or non-loopback URL.
- AC-5. Given a full `make baseline-profile` on the emulator against the loopback mock, when it ends, then it exits 0, every route journal shows `unknown=[]`, and the egress journal names no real host.
- AC-6. Given the regenerated `baseline-prof.txt`, when `BaselineProfileContentTest` runs, then it requires and finds rules for `okhttp3/internal/http1/Http1ExchangeCodec`, `okhttp3/internal/http2/Http2Connection`, `kotlinx/serialization/internal/BooleanSerializer`, `FloatSerializer`, `DoubleSerializer`, `com/adamglin/phosphoricons/fill/EyeKt` and `fill/BookmarksimpleKt`; it also requires rules for the bookmark-folder lookup (`BaselineProfileContentTest.baselineProfileCoversBookmarkFolderLookup`: `getItemBookmarkFolders` and `BookmarkFoldersResponse`); all existing tests in that class still pass.
- AC-7. Given the regenerated profile against 5cd3e37e, when rule lines are counted per package of the table above, then `http1` is at least 40 and the other three are each within 20 % of 5cd3e37e or above it; the counts go into the evidence file.
- AC-8. Given the change, when `git diff --stat master` is read, then no file under `app/src/main/java` or `app/src/main/res` changed.

## Out of scope

Confirming which real host uses which protocol (no live API probe). imdb/trailer fixtures (Q2). Main-source changes under app/src/main/java or res.

## Design

Base 08f50b81 (hardening merged; no rebase pending). Citations checked in `/home/dev/orca/workspaces/Puber/baseline-mock-protocol-parity`.

### Decisions

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

### Changes by layer

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

### Alternatives rejected

| Alternative | Reason |
|---|---|
| One server, `protocolNegotiationEnabled = false` (research B) | Cools the 291 `http2` rules on the DoH and GitHub paths |
| Fixtures and headers only (research C) | `http1` stays at 0 |
| Two `BaselineMockBackend` instances | Two route records, two `verify()` merges in `BaselineProfileTestControl` and `JourneyCheck` |
| Shared route table with no server ownership | A wrong origin passes unnoticed and quietly reverts the protocol split |
| `h2` port = `puber.baselineMockPort + 1` without its own property | Brief decision: own Gradle property |
| `Cache-Control` headers on API responses | Changes OkHttp cache hits and so the request pattern the journeys rely on |

### Risks

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

### Open questions

N/A: Q1-Q3 answered in `state.md`; no AC change needed.


### Round 2 delta (Q4: series fixtures, AC-7 descriptors >= 163)

Checked in the worktree at 6fcea29c. Gap source: `comm` of `grep kotlinx/serialization/descriptors` lines, `git show 5cd3e37e:app/src/main/generated/baselineProfiles/baseline-prof.txt` vs the 6fcea29c profile (203 vs 149). Lines that differ only in flags (`HSP` vs `HP`) cancel out; what is missing in the 6fcea29c profile is below.

| Missing group | Classes (rule lines) | Lit by |
|---|---|---|
| Series models | `Season`, `Episode` (`getDescriptor`, `write$Self`) | `seasons` in a details payload, decoded and written to the item cache (`ContentCacheRepository.kt:38`) |
| Track models | `Audio`, `AudioType`, `AudioAuthor`, `Translation`, `SubtitleLink` | `audios`, `files[].translation`, `subtitles` of an episode or video (`Models.kt:282`, `:285`, `:586`) |
| Item lists | `Country`, `Tracklist`, `Genre.write$Self` | `countries`, `tracklist`, `genres` on a details item (`Models.kt:52-53`, `:76`) |
| Unknown-key path (library, about 30 lines) | `JsonNamesMapKt` (slow path, names map), `StreamingJsonDecoder.handleUnknown`, `DescriptorSchemaCache`, `PluginGeneratedSerialDescriptorKt.hashCodeImpl`, `SerialDescriptorKt.elementDescriptors`, `SerialKind.hashCode`, `NullableSerializer`/`SerialDescriptorForNullable` descriptors | A JSON key the model does not declare: `ignoreUnknownKeys` (`KinoPubApiClient.kt:133-137`) sends it through the alternative-names lookup, which hashes the descriptor into the schema cache. Inferred from the call chain, not traced on device |
| Out (Q2 / not reached) | `Trailer`, `IntroDbApp*` (need `trailer`, `imdb`), `BookmarkFolder*` (folder lookup skipped when `bookmarks` is non-empty, `DetailsInteractor.kt:56-59`), cache records `ItemListRecord`/`ItemPageRecord`/`HistoryPageRecord`, `Pagination`, `ItemTypeSerializer` descriptors (untraced) | - |

Why the four keys were banned: hermetic-baseline-generation D9 (`docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md:95`) kept TMDB, IntroDB and trailer lookups off by construction. What actually reaches outside: `imdb` (skip segments `SkipSegmentInteractor.kt:14-19`, TMDB id `SkipSegmentService.kt:56-63`, schedule button `DetailsScreenUIMapper.kt:185`) and `trailer` (`TrailerLinkInteractor.kt:22-24`). `seasons` and `subtitles` start no lookup: a series details screen requests only `items/{id}` and `items/similar` (`DetailsVM.kt:98-131`; series watchlist comes from `in_watchlist`, `DetailsInteractor.kt:50`, `:55`); subtitle URLs are fetched only by the player (`PlayerVM.kt:447`). The season, episode, subtitle and notification endpoints (`KinoPubApiClient.kt:908`, `:914-934`, `:937-961`) have no caller outside the client.

#### Decisions

| # | Question | Decision | Why |
|---|---|---|---|
| D11 | How many series, where | One series, id 2, in a new fixture `item_serial.json`: `type: "serial"`, `in_watchlist: true`, one season (`number: 1`) with two episodes; episode 1 `watched: 1`, episode 2 `watched: 0` with `watching`; each episode has `thumbnail` = `{{MOCK}}/media/wide.jpg` and `files[0].url.hls4` = `{{MOCK}}/media/stream.m3u8`, `audios[0]` with `type` and `author`, `files[0].translation`, and `subtitles[0].url` = `{{MOCK}}/media/subtitle.vtt`. No `imdb`, no `trailer` | Lights both series and track groups; every URL on the loopback mock |
| D12 | Which id, and does a journey have to open the series | Id 2 is the second card of every Home row (`items.json` index 1); no journey change. It is the right-hand neighbour of the card focus starts on, which `DetailsPrefetcher` warms after 750 ms (`DetailsPrefetcher.kt:38`, `:76`, `:92`); every journey idles at least 2 s there before its first key (`BaselineProfileGenerator.kt:67`, `:75`, `:114`, `:167`). The prefetch decodes the payload and writes it to the item cache, which is what the descriptor lines need | Smallest change; `BaselineProfileGenerator.kt` stays untouched. Browse may also open it (bonus, not relied on) |
| D13 | Route | New route `/v1/items/2` listed before `/v1/items/\\d+` (`BaselineMockBackend.kt:183`), present in every scenario except Playback, `required = browse`, chunked JSON on h1 | Its own description makes the hit visible in the route journal; `required` makes a miss fail the Browse iteration instead of silently missing AC-7 |
| D14 | Playback stability | In Playback the series route is absent, so `/v1/items/2` falls through to `item.json` (movie) as today; `item.json` keeps no `audios`, `subtitles` or `translation` | Playback serves byte-identical details for every id, so its Play focus and the player check (`BaselineProfileGenerator.kt:176-180`) cannot change, whichever card it opens |
| D15 | List entry | `items.json` id 2 becomes `type: "serial"` with `watched: 1`, `new: 1`, `total: 2` (unwatched counter, `VideoItemUIState.kt:305-315`); the watched-movie fields of round 1 (D7) move from id 2 to id 3 | List and details agree on the type; `itemFixtures_carryWatchedAndBookmarkedMovie` still finds a non-first watched movie |
| D16 | Cheap levers without new keys | `item.json` and `item_serial.json` add `genres`, `countries`, `tracklist` (no `url`), and all three item fixtures (`items.json`, `item.json`, `item_serial.json`) add one key no model declares (`"unmodelled": 0` on each item) | Item lists and the unknown-key path (gap table) need no banned key and no new route; they are UI-only, so Playback is unaffected |
| D17 | Ban policy | `BANNED_KEY` (`BaselineMockBackend.kt:377`) becomes `imdb|trailer`; a new `SERIES_ONLY_KEY` `seasons|subtitles` fails `validate()` (`:362-369`) in any fixture other than `item_serial`. `NAMES` (`:372-375`) gains `item_serial`; KDoc `:349-352` and `:136-140` updated | Keeps the by-construction guarantee: only the one unplayed series carries subtitle or season data |

#### Changes by layer (delta)

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

#### Alternatives rejected (delta)

| Alternative | Reason |
|---|---|
| Make `item.json` itself a series | Playback would open a series: Play resolves the first unwatched episode (`PlayerInteractor.kt:87-110`, `:259`) and loads its subtitles |
| Series for every id except 1 | Playback then depends on opening id 1; Browse loses its movie details |
| Add a journey step that opens the series | Card position is not known statically; touches `BaselineProfileGenerator.kt` |
| `audios`/`subtitles` on the movie in `item.json` | Reaches the player in Playback (`ContentStateFactory.kt:36-38`, `PlayerVM.kt:447`) |
| Mock route for `/media/subtitle.vtt` | Nothing plays the series; an accidental play then fails loudly as an unknown loopback request |
| `imdb` or `trailer` | Q2; IntroDB/TMDB/trailer lookups |

#### Risks (delta, highest first)

| # | Area | Risk | Lives in | Mitigation |
|---|---|---|---|---|
| R9 | Baseline profile | Descriptors still under 163: the unknown-key share is inferred, series lines need the prefetch | fixtures, `DetailsPrefetcher.kt:76-97` | qa reruns the `comm` above on the new profile and names what is still missing; levers left are listed as Out |
| R10 | Correctness / flakiness | `required` series route missed in a Browse iteration (focus never rests next to id 2, prefetch deferred by `awaitSettled`) and generation fails | `BaselineMockBackend.kt` route, `DetailsPrefetcher.kt:123` | Failure names `GET /v1/items/2` in `missing`; fallback is `required = false` with the match count recorded in evidence, decided at triage |
| R11 | Correctness | Series reaches the player (Browse opens id 2 and something presses Play) and requests `/media/subtitle.vtt` | `item_serial.json`, journeys | No journey presses Play outside Playback; an accidental request is a loopback unknown route, so generation fails without egress |
| R12 | Data shape | List says series, a cached movie record for id 2 (Playback) merges with it on a later scenario | `ContentCacheRepository.kt` merge | `clearTarget` drops caches every iteration (`BaselineProfileGenerator.kt:52`) |
| R13 | Rollout / security | A later fixture edit adds `seasons` or `subtitles` to a played item | `BaselineMockBackend.kt:362-377` | `validate()` and the unit test reject them outside `item_serial` |

#### Open questions (delta)

| ID | Question | Recommended | Blocks |
|---|---|---|---|
| Q5 | Q4 named `seasons`; D11 also lifts `subtitles` (episode only, never played) for `SubtitleLink`. Accept? | Yes: no lookup, loopback URL, guarded by D17 | no |
| Q6 | AC-4 says fixtures contain no `imdb`, `trailer` or non-loopback URL; adding a series needs no AC change. Should the spec's test-design rows for `fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys` be renamed in place? | Yes, test-designer updates the row and adds the series rows | no |

## Test design

Inputs: [brief.md](brief.md), [design-architect.md](design-architect.md), [qa-baseline.md](qa-baseline.md). Base 08f50b81.

Levels (`.flow/roles/test-designer.md`):

| Level | Where | Runs with |
|---|---|---|
| `unit` | `app/src/test` (JUnit 5) | `./tools/agentw testDevDebugUnitTest` |
| `unit-instr` | `app/src/testInstrumentation` (JUnit 5, instrumentation `BuildConfig`) | `./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` |
| `self-test` | `baselineprofile/src/main/java/.../BaselineMockSelfTest.kt` (JUnit 4 on device) | runs inside `make DEVICE=host.docker.internal:5555 baseline-profile`; results under `baselineprofile/build/outputs/androidTest-results/` |
| `device` | generation run on the emulator, recorded in the evidence file | Manual checks below |

### Risk profile

| Risk | Source | P | I | Score | Covered by |
|---|---|---|---|---|---|
| Misrouted origin (GitHub left on the h1 origin, DoH on h1, API on h2) silently reverts the split | design R3, D3 | 2 | 3 | 6 | `unit-instr` origin tests (AC-3), `routeOfOtherServer_returns404AndIsListedAsUnknown`, device `unknown=[]` (AC-5) |
| Watched/bookmarked `item.json` changes the Playback journey and the player check fails | design R2 | 2 | 3 | 6 | Manual 1: `playbackProfile` passes (AC-5) |
| Split ports end h2 coalescing of `api.`/`dns.baseline.test`; a journey misses a route | brief risk 2 | 2 | 3 | 6 | Manual 1: required routes matched, `unknown=[]` per journey (AC-5) |
| AC-7 thresholds missed (fixtures warm only what journeys decode and draw) | design R1 | 2 | 2 | 4 | Perf: counts in the evidence file (AC-7) |
| Egress widened to `api:h2` or `dns:h1`; a request leaves loopback | design R7, brief device rule | 1 | 3 | 3 | `select_returnsDeadProxyAndJournals_whenApiOnH2OrDnsOnH1`, egress journal empty (AC-5) |
| Chunk framing on an h2 response corrupts the body | design R4 | 1 | 3 | 3 | `apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2` decodes `/github` and DoH over h2 |
| Rebase onto baseline-generation-hardening | brief risk 1 | 1 | 2 | 2 | Accepted: base 08f50b81 already merges it |
| Guards on library internals break on a dependency bump | design R8 | 2 | 1 | 2 | Accepted: top-level class names only |
| Parallel HTTP/1.1 connections contend on the shared route state | design R5 | 1 | 2 | 2 | Accepted: state under one `lock`; Manual 1 exercises it |
| Second server fails to bind and the first leaks; `close()` stops after one | design R6 | 1 | 2 | 2 | Accepted; P2 `close_stopsBothServers` |
| A `Date` header changes OkHttp cache hits | design D5 | 1 | 2 | 2 | Accepted: no freshness header; Manual 1 required routes |
| A later fixture edit puts `seasons` or `subtitles` on a played item | design R13, D17 | 2 | 3 | 6 | `validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture`, `fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture` |
| Playback opens id 2 and gets the series, changing its Play focus or loading subtitles | design D14 | 1 | 3 | 3 | `seriesRoute_servesSeriesInBrowse_movieInPlayback`, `movieFixture_carriesNoTrackData` |
| Descriptors still under 163 after the series fixture | design R9 | 2 | 2 | 4 | Perf (AC-7); qa reruns the `comm` from the design delta on a miss |
| Required series route missed in a Browse iteration; generation fails | design R10 | 2 | 2 | 4 | `browseScenario_requiresSeriesRoute`; Manual 1 |
| Series reaches the player and requests `/media/subtitle.vtt` | design R11 | 1 | 3 | 3 | Manual 1: `unknown=[]`, egress journal empty |
| Cached movie record for id 2 merges with the series in a later scenario | design R12 | 1 | 2 | 2 | Accepted: `clearTarget` drops caches every iteration |

### Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | self-test | P0 red (by construction: base has no `h2Port`/`boundH2Port`, does not compile) | `BaselineMockSelfTest.apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2` | `Response.protocol` is `HTTP_1_1` for `https://api.baseline.test:<h1>/v1/items/hot` (resolved through the mock DoH, leaf trusted) and `HTTP_2` for `https://127.0.0.1:<h2>/github/...`; the DoH lookup over `dns.baseline.test:<h2>` returns 127.0.0.1. Fails if `protocols = [HTTP_1_1]` is dropped from `h1`, if `h2` is HTTP/1.1-only, or if chunking reaches an h2 body |
| AC-2 | self-test | P0 red (by construction: base `json()` never chunks, fails by reading) | `BaselineMockSelfTest.jsonRouteOnH1_isChunked_mediaRouteIsFixedLength` | `/v1/items/hot` on h1 has `Transfer-Encoding: chunked` and `body.contentLength() == -1`; `/media/poster.jpg` has a `Content-Length`. Fails if `json()` stops chunking or media gets chunked |
| AC-2 | self-test | P0 red (by construction: base sets no `Date` and has no `boundH2Port`, does not compile) | `BaselineMockSelfTest.everyResponse_carriesDateHeader` | h1 200, h1 404, h2 `/github` 200 and h2 404 each carry a `Date` that parses as RFC 1123. Fails if one response builder (text, binary, 404) skips the header |
| AC-1, D3 | self-test | P0 red (by construction: base has no `h2Port`/`boundH2Port`, does not compile) | `BaselineMockSelfTest.routeOfOtherServer_returns404AndIsListedAsUnknown` | `GET /v1/items/hot` on h2 and `GET /github/repos/o/r/releases/latest` on h1 return 404 and both appear in `verify().unknownRequests`. Fails if the route table is shared without server ownership |
| AC-1 | self-test | P0 guard | existing `BaselineMockSelfTest` tests (`journeyCheck_*`, `lateCheck_*`, `dnsQuery_*`, `unknownRoute_*`, `startupScenario_*`) with `hitStartupRoutes` sending `/github` to the h2 origin | Route record, `JourneyCheck` and DoH keep working across two servers |
| AC-3 | unit-instr | P0 red | `BaselineInstrumentationEnvironmentTest.configure_pinsApiToH1PortAndDohToH2Port` | After `configure(dir)`: `MAIN_API_BASE_URL` is `https://api.baseline.test:${BASELINE_MOCK_PORT}/v1/`, `DNS_RESOLVER` is `DohResolver("https://dns.baseline.test:${BASELINE_MOCK_H2_PORT}/dns-query", ["127.0.0.1"])`. Fails if DoH stays on the h1 port or API moves to h2 |
| AC-3 | unit-instr | P0 red | `BaselineInstrumentationEnvironmentTest.allowedOrigins_listsEachServedOriginOnItsServerPort` | `allowedOrigins()` equals exactly {`127.0.0.1:h1`, `api.baseline.test:h1`, `127.0.0.1:h2`, `dns.baseline.test:h2`} and `mockH2Origin()` is `https://127.0.0.1:${BASELINE_MOCK_H2_PORT}`. Fails on a missing or extra origin, and if both Gradle ports are set equal (the set collapses) |
| AC-3 | unit-instr | P0 red | `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenApiOnH2OrDnsOnH1` | With `allowedOrigins()` as the allowed set, `api.baseline.test:h2` and `dns.baseline.test:h1` get the dead proxy and are journaled. Fails if egress is widened (R7) |
| AC-3 | unit-instr | P0 guard | `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsApiOrDnsTestHost`, `select_returnsNoProxy_whenOriginIsMock`, `select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers`, `select_returnsDeadProxyAndJournals_whenAllowedTestHostPortDiffers` | Allowed set read from `allowedOrigins()`, ports from `BuildConfig`; the "differs" cases use a port equal to neither (D9) |
| AC-3 | device | P0 | Manual 1 | GitHub base in `BaselineProfileApp.kt:26` is not reachable from a unit test: a wrong origin shows as `GET /github/...` unknown or missing in the route journal |
| AC-4 | unit-instr | P0 guard | `BaselineFixtureContentTest.itemFixtures_decodeAsAppItems` | `items.json` and `item.json` decode with the app's response models (`Models.kt:11`, `:18`) and the lenient `Json`. Fails on a renamed key or a wrong type (e.g. `fps` as a string) |
| AC-4 | unit-instr | P0 red | `BaselineFixtureContentTest.itemFixtures_carryWatchedAndBookmarkedMovie` | Decoded `item.json` item: `isFullyWatched()` true, `inWatchlist == true`, `bookmarks` non-empty (D8); a non-first `items.json` entry has `isFullyWatched()` true. Fails if a watched or bookmark field is removed |
| AC-4 | unit-instr | P0 red | `BaselineFixtureContentTest.itemFixtures_carryBooleanFloatAndDoubleValues` | Decoded `item.json` item has non-null `advert`, `subscribed`, `poorQuality`, `finished` (Boolean), `fps` (Float) and `duration.average` (Double). Fails if a primitive field is dropped |
| AC-4 | unit-instr | P0 guard | `BaselineFixtureContentTest.fixtures_haveNoUrlOutsideMockOrigin`, `fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture` (origin from `BuildConfig.BASELINE_MOCK_PORT`; replaces `fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys`) | No fixture holds an external URL or an `imdb`/`trailer` key (Q2); `seasons`/`subtitles` appear in no fixture but `item_serial.json` (D17). Fails if `item.json` or `items.json` gains either key |
| AC-4, D11 | unit-instr | P0 red | `BaselineFixtureContentTest.serialFixture_decodesAsSeriesWithTwoEpisodesOnMockOrigin` | `item_serial.json` decodes as `ApiResponse<Item>`: `isSeriesLike()`, `inWatchlist == true`, one season with two episodes, each `files[0].url.hls4 == "$origin/media/stream.m3u8"`; an episode has `audios[0]` with `type` and `author`, `files[0].translation`, and `subtitles[0].url` on the mock origin. Fails if the fixture is missing, not a series, or a track group is dropped |
| AC-4, D14 | unit-instr | P0 guard | `BaselineFixtureContentTest.movieFixture_carriesNoTrackData` | Raw `item.json` has no `audios`, `subtitles` or `translation` key. Fails if track data reaches the item Playback plays |
| AC-4, D15 | unit-instr | P1 red | `BaselineFixtureContentTest.itemsFixture_secondEntryIsSeries` | Decoded `items.json` entry with id 2 is `isSeriesLike()` with `new` > 0. Fails if list and details disagree on the type |
| AC-4, D16 | unit-instr | P1 red | `BaselineFixtureContentTest.itemFixtures_carryUnmodelledKey` | Each item of `items.json`, `item.json` and `item_serial.json` has a key outside `Item.serializer().descriptor` element names. Fails if the unknown-key lever is removed from any of the three |
| AC-4, D16 | unit-instr | P1 red | `BaselineFixtureContentTest.detailsFixtures_carryGenresCountriesAndTracklist` | Decoded `item.json` and `item_serial.json` items have non-empty `genres`, `countries` and `tracklist`. Fails if one list is dropped |
| AC-4, D11 | unit-instr | P1 | `BaselineFixtureContentTest.movieFixtures_carryPostersAndHlsStream` (extended) | Also covers `item_serial.json` posters and episode `thumbnail` = `$origin/media/wide.jpg`. Fails if a series image points off the served media routes |
| AC-1, D13, D14 | self-test | P0 red (by construction: base `/v1/items/2` returns the movie, fails by reading) | `BaselineMockSelfTest.seriesRoute_servesSeriesInBrowse_movieInPlayback` | `GET /v1/items/2` in a BrowseAndDetails backend returns a body with `"type":"serial"` and `seasons`; in a Playback backend it returns `item.json` with id 2 and no `seasons`. Fails if the series route is missing, ordered after `/v1/items/\\d+`, or present in Playback |
| AC-1, D13 | self-test | P1 red | `BaselineMockSelfTest.browseScenario_requiresSeriesRoute` | A BrowseAndDetails journey check that hits every required route except `GET /v1/items/2` fails with it in `missingRequiredRoutes`. Fails if the route is not `required = browse` (R10 shows as a named miss, not a silent AC-7 drop) |
| AC-4, D17 | self-test | P0 red (by construction: base bans `seasons` in every fixture, fails by reading) | `BaselineMockSelfTest.validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture` | `BaselineFixtures(mapOf("item_serial" to <body with seasons, subtitles>), …).validate()` passes; the same body under `item` throws; `item_serial` with `imdb` or `trailer` throws. Fails if the ban stays global (today) or is lifted for every fixture |
| AC-4 | unit-instr | P2 | `BaselineFixtureContentTest.itemFixture_carriesExplicitNull` | Raw `item.json` holds at least one `JsonNull` value (nullable-serializer path, D7) |
| AC-5 | device | P0 | Manual 1 | Exit 0, all generator journeys and `BaselineMockSelfTest` pass, `unknown=[]` per journey, egress journal empty. Catches R2, R3 and the coalescing risk |
| AC-6 | unit | P0 red | `BaselineProfileContentTest.baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons` | `baseline-prof.txt` has a rule for each of the seven AC-6 prefixes via `hasRuleFor`. Fails on a regeneration that loses the HTTP/1.1 codec, a primitive serializer or a filled icon |
| AC-6, triage #1 | unit | P0 guard | `BaselineProfileContentTest.baselineProfileCoversBookmarkFolderLookup` | `baseline-prof.txt` has rules for `KinoPubApiClient.getItemBookmarkFolders` and `BookmarkFoldersResponse` via `hasRuleFor` (8 and 21 lines at 08f50b81, so it passes on base; it fails on the round 1 head f04f6e36 and turns green only after a regeneration that keeps one details payload on the bookmark-folder fallback path). Fails if every details payload takes the early return again |
| AC-6 | unit | P0 guard | existing `BaselineProfileContentTest` tests | Regenerated profiles keep details/player/rail, cold-start, image/TLS/HLS coverage and stay free of generator classes |
| AC-7 | device | P0 | Manual 2, Perf | Per-package counts meet the thresholds |
| AC-8 | manual | P1 | Manual 3 | No production source or resource changed |
| R6 | self-test | P2 | `BaselineMockSelfTest.close_stopsBothServers` | After `close()`, a request to `boundPort` and to `boundH2Port` fails to connect. Fails if `close()` stops after the first server |

Rows tagged D11-D17 belong to the round 2 delta: `red` there means failing at 6fcea29c (round 1 head) and passing after the series fixtures.

### Failing-first test

`BaselineProfileContentTest.baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons` is written first. On the base profile (`git show 08f50b81:app/src/main/generated/baselineProfiles/baseline-prof.txt`) six of the seven prefixes have 0 rule lines (only `Http2Connection` has 84), so it fails today; it turns green only after the AC-5 regeneration. 5cd3e37e has 3 to 47 lines for each prefix, so the guard is reachable.

### Manual checks

Device rules: emulator `host.docker.internal:5555` only, under a lease from `tools/emulator-resource-lock.sh`; generation reaches only the loopback mock; no command touches the live KinoPub API.

1. **Generation (AC-3, AC-5).**
   1. `ssh mac-emu start` (prints `ready`), take the lease, `adb connect host.docker.internal:5555`.
   2. From the worktree: `make DEVICE=host.docker.internal:5555 baseline-profile`.
   3. Expected: exit 0; results XML shows `startupProfile`, `browseAndDetailsProfile`, `railNavigationProfile`, `playbackProfile` passed and every `BaselineMockSelfTest` test passed, including the four new ones; each journey's route journal prints `unknown=[]` and lists `GET /github/...` and `POST /dns-query` as matched; the egress journal is empty.
   4. Must not happen: a request to any non-loopback host, a `playbackProfile` failure at the player check (R2: record it as a question, do not drop the fixtures), a retry loop, a Gradle generation task other than `InstrumentationNonMinifiedRelease`.
   5. Release the lease; `ssh mac-emu stop` if `tools/emulator-resource-lock.sh status` shows no other lease.
2. **Counts (AC-7).** Run the Perf command on the regenerated `baseline-prof.txt` and on `git show 5cd3e37e:...`; write both columns into the evidence file. Expected: every gating row meets its threshold. Must not happen: counting with another method than the one in Perf.
3. **No production change (AC-8).** `git -C <wt> diff --name-only 08f50b81 -- app/src/main/java app/src/main/res`. Expected: empty output. Must not happen: any path listed.

### Perf

Metric: rule lines per class prefix in `app/src/main/generated/baselineProfiles/baseline-prof.txt`. Method (same as qa-baseline.md): `grep -c 'L<prefix>/' baseline-prof.txt`; the phosphor pattern is `grep -c 'Lcom/adamglin/phosphoricons/'` (51 at 08f50b81, 69 at 5cd3e37e). The brief's table used a three-segment package count and is not comparable; AC-7 is measured with this method only. Baseline: the committed files (no device run), from [qa-baseline.md](qa-baseline.md):

| Prefix | 08f50b81 | 5cd3e37e | Pass threshold (after) | Gating |
|---|---|---|---|---|
| `okhttp3/internal/http1` | 0 | 51 | >= 40 | yes |
| `kotlinx/serialization/internal` | 165 | 234 | >= 188 | yes |
| `kotlinx/serialization/descriptors` | 137 | 203 | >= 163 | yes |
| `Lcom/adamglin/phosphoricons/` (`grep -c`) | 51 | 69 | >= 56 (ceil(0.8 x 69)) | yes |
| `okhttp3/internal/http2` | 292 | 316 | >= 253 | no, reported (the h2 paths must not cool) |
| `okhttp3/internal/tls` | 32 | 30 | >= 24 | no, reported |

Player-count check (triage #2): after the AC-5 generation, report `androidx/media3/exoplayer`, `androidx/media3/exoplayer/hls` and `AudioTrackPreferenceResolver` line counts against 08f50b81 (3490, 387, 57) with the same `grep -c`. Reported in the evidence file, not gating.

Thresholds for the 20 % rows are `ceil(0.8 x 5cd3e37e)`. The after value is the AC-5 generation on `host.docker.internal:5555`.

### UNTESTABLE

N/A: every AC has a test or a manual check.

## Open questions

| ID | Question | Blocks | Status | Answer |
|----|----------|--------|--------|--------|
| Q1 | Accept the inferred split (HTTP/1.1 for API, images, stream; HTTP/2 for DoH and GitHub) without per-host confirmation? | no | answered | Accept |
| Q2 | Add imdb or trailer to fixtures to warm two icons? | no | answered | No |
| Q3 | Fix the hardcoded test port 18765 here? | no | answered | Yes, where the second port touches it |
| Q4 | descriptors 149 vs 163: accept or add series fixtures? | yes | answered | Add series fixtures here; threshold stays 163 |
| Q5 | Allow subtitles on the never-played series episodes? | no | answered | Yes |
| Q6 | Rename the fixtures ban row and add series test rows? | no | answered | Yes |

## Decisions

| Date | Decision | By |
|------|----------|----|
| 2026-11-26 | Q1-Q3 as recommended | owner (standing instruction: recommended) |
| 2026-11-26 | Gate 1: spec approved | owner (standing instruction: recommended) |
| 2026-11-27 | Q4: add series fixtures in this run | owner |
| 2026-11-27 | Q5, Q6 as recommended; gate 1 delta approved | owner (standing instruction: recommended) |
| 2026-11-27 | Review round 1: AC-4 and AC-6 amended to the fixture reality (plain movie takes the folder lookup; watched = items.json id 3); player-count drop accepted (non-gating), investigation deferred to baseline-player-track-coverage | owner (standing instruction: recommended) |
