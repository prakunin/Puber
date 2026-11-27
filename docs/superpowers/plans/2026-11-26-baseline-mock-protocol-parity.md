# Keep HTTP/1.1, serialization and filled-icon paths in the mock-generated baseline profile - plan

**Spec:** `docs/superpowers/specs/2026-11-26-baseline-mock-protocol-parity.md`
**Goal:** The loopback mock serves HTTP/1.1 (API, images, stream) and HTTP/2 (DoH, GitHub) on two ports with chunked bodies and `Date` headers, the fixtures carry a watched and bookmarked movie with boolean, float, double and null fields, a never-played series (id 2) carries season, episode and track models, and a regenerated `baseline-prof.txt` is guarded for the restored paths.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh`. Both run inside the worktree; never `./gradlew` there.
- Generator compile (`:baselineprofile` is not in the fast gate): `AGENT_GRADLE_SERIAL=1 ./tools/agentw :baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin`.
- Instrumentation unit tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`.
- One task = green fast gate + green tests of the touched code + one commit, message per commit_style "One imperative English sentence, no prefixes", never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- No file under `app/src/main/java` or `app/src/main/res` changes (AC-8). No `imdb` or `trailer` key in any fixture and no URL outside `{{MOCK}}` (Q2); `seasons` and `subtitles` only in `item_serial.json` (D17, from Task 5 on). No live KinoPub API request from any script or device run.
- Device: only Tasks 4 and 7 touch a device: emulator `host.docker.internal:5555` under a lease, loopback mock only. The TV box is not used. qa owns the evidence file and Manual checks 1-3.
- Ports: `h1` = `puber.baselineMockPort` (18765), `h2` = new `puber.baselineMockH2Port` (18766). Code reads them from Gradle/BuildConfig/runner arguments, never as literals.

---

## Tasks

### Task 1: Two mock servers (HTTP/1.1 and HTTP/2), chunked JSON, Date headers

**Covers:** AC-1, AC-2 (self-tests; they run on the device in Task 4), design D1-D5, R4, R6
**Files:**
- Modify: `gradle.properties` - after `:5` (`puber.baselineMockPort=18765`) add `puber.baselineMockH2Port=18766`
- Modify: `app/build.gradle.kts` - next to `:246` (`BASELINE_MOCK_PORT`) add `buildConfigField("int", "BASELINE_MOCK_H2_PORT", providers.gradleProperty("puber.baselineMockH2Port").get())`
- Modify: `baselineprofile/build.gradle.kts` - next to `:23-24` add runner argument `testInstrumentationRunnerArguments["baselineMockH2Port"] = providers.gradleProperty("puber.baselineMockH2Port").get()`
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt`:
  - Add public `enum class BaselineMockServer { H1, H2 }` next to `BaselineScenario` (`:257`); `BaselineMockRoute` (`:264-277`) gains `val server: BaselineMockServer` (constructor parameter after `required`)
  - Constructor (`:21-25`): `port: Int, h2Port: Int, fixtures, leaf`
  - `server` (`:27`) becomes `h1Server = MockWebServer().apply { protocols = listOf(Protocol.HTTP_1_1) }` and `h2Server = MockWebServer()` (default `[HTTP_2, HTTP_1_1]`); import `okhttp3.Protocol`. Add `val boundH2Port: Int get() = h2Server.port` next to `boundPort` (`:36-37`); KDoc names which server each port is
  - `start()` (`:43-52`): both `useHttps` with the same leaf factory; `h1Server.dispatcher = routeDispatcher(H1)`, `h2Server.dispatcher = routeDispatcher(H2)`; start `h1Server` on `(LOOPBACK, port)`, then `h2Server` on `(LOOPBACK, h2Port)` inside `try`, closing `h1Server` and rethrowing if the second start throws (R6)
  - `close()` (`:88-94`): `try { h1Server.close() } finally { h2Server.close() }`; the `started` guard stays, so a second `close()` is a no-op
  - `routeDispatcher()` (`:96-106`) takes the server; a route matches only when `it.server == server && it.matches(request)`; a route of the other server is unknown (recorded, 404) (D3). Update the class KDoc (`:14-20`)
  - `route(...)` (`:116-123`) gains `server: BaselineMockServer = BaselineMockServer.H1`; `json(...)` (`:125`) becomes `json(body: String, server: BaselineMockServer = H1)` returning `textResponse(HTTP_OK, JSON, body, chunked = server == H1)`; `/v1/items/\\d+` (`:153-156`) answers with `chunked = true`
  - `/github/...` (`:157`) gets `server = H2` and `json(..., server = H2)`; `/dns-query` (`:158`) gets `server = H2`. Every other route stays on `H1`
  - `textResponse` (`:213-218`) gains `chunked: Boolean = false`: chunked uses `MockResponse.Builder.chunkedBody(body, JSON_CHUNK_BYTES)` (new const 1024), else `.body(body)`. 404 and 400 stay fixed-length. `binaryResponse` (`:220-225`) stays fixed-length
  - Both builders add `Date` = now in IMF-fixdate, `DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC)` (an RFC 1123 form that `DateTimeFormatter.RFC_1123_DATE_TIME` parses; D5). No `Cache-Control`, `Expires` or `Last-Modified`
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileTestControl.kt` - `createBackend` (`:18-31`) reads `ARG_MOCK_H2_PORT = "baselineMockH2Port"` (const next to `:118`) with the same `?: error("... instrumentation argument is required")` and passes `h2Port`; fixture origin stays `https://127.0.0.1:$port` (h1)
- Test: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockSelfTest.kt`

**Consumes:** `BaselineProfileGenerator.kt:216-218` (`createBackend(InstrumentationRegistry.getArguments())`, unchanged)
**Produces:** `BaselineMockBackend(port, h2Port, fixtures, leaf)`, `boundH2Port`, `BaselineMockServer`; Gradle property `puber.baselineMockH2Port`; `BuildConfig.BASELINE_MOCK_H2_PORT` (instrumentation flavor); runner argument `baselineMockH2Port`

Self-test changes (`BaselineMockSelfTest.kt`):

| Where | Change |
|---|---|
| `setUp` `:31-44` | `BaselineMockBackend(0, 0, ...)`; keep `leaf` in a field; add `h2Origin = "https://127.0.0.1:${backend.boundH2Port}"` |
| `dohResolver` `:47-57` | URL `https://dns.baseline.test:${backend.boundH2Port}/dns-query`; the DoH client gets a network interceptor that appends `response.protocol` to a `dohProtocols` list |
| `get` `:64-65` | `get(path, base = origin)`; add `call(url): Response` for header/protocol checks (caller closes) |
| `hitStartupRoutes` `:72` | `/github/...` via `h2Origin` |
| new `apiClient` | trusts `leaf.certificate`, `.dns(doh)`, for `https://api.baseline.test:${backend.boundPort}` |

New self-tests (names from the spec's Test design):

| Test | Asserts |
|---|---|
| `apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2` | `apiClient` GET `https://api.baseline.test:<h1>/v1/items/hot` is 200 with `protocol == HTTP_1_1`; GET `$h2Origin/github/repos/o/r/releases/latest` is 200 with `HTTP_2` and its body decodes as JSON; `doh.lookup("api.baseline.test")` is `[127.0.0.1]` and `dohProtocols` is non-empty and all `HTTP_2` |
| `jsonRouteOnH1_isChunked_mediaRouteIsFixedLength` | `/v1/items/hot` on h1: `header("Transfer-Encoding") == "chunked"`, `body.contentLength() == -1L`; `/media/poster.jpg` on h1 has a `Content-Length` header |
| `everyResponse_carriesDateHeader` | h1 200 `/v1/items/hot`, h1 404 `/v1/not-a-route`, h2 200 `/github/repos/o/r/releases/latest`, h2 404 `/v1/not-a-route`: each `Date` parses with `DateTimeFormatter.RFC_1123_DATE_TIME` |
| `routeOfOtherServer_returns404AndIsListedAsUnknown` | GET `/v1/items/hot` on `h2Origin` and GET `/github/repos/o/r/releases/latest` on `origin` are 404; `verify().unknownRequests` contains both `BaselineRequest("GET", path)` |
| `close_stopsBothServers` (P2) | after `backend.close()`, a GET to `boundPort` and one to `boundH2Port` each throw `IOException` |

The existing tests (`:90-180`) stay and must keep passing with `hitStartupRoutes` on the split origins.

- [x] Gradle property, BuildConfig field, runner argument
- [x] Backend, control, self-test changes as above
- [x] Fast gate and generator compile exit 0; commit "Serve the baseline mock over HTTP/1.1 and HTTP/2 on two ports with chunked JSON and Date headers"

**Done when:** fast gate and generator compile exit 0, and `rg -n 'server = BaselineMockServer.H2|server = H2' baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt` lists only the `/github` and `/dns-query` routes (and the `json(..., server = H2)` call).

---

### Task 2: App-side origins per server

**Covers:** AC-3, design D6, D9, R3, R7
**Files:**
- Modify: `app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt`:
  - Add `fun mockH2Origin(): String = "https://$MOCK_HOST:${BuildConfig.BASELINE_MOCK_H2_PORT}"` next to `mockOrigin()` (`:17`, stays the h1 origin)
  - `allowedOrigins()` (`:22-26`) = exactly `mockOrigin()`, `https://$API_HOST:<BASELINE_MOCK_PORT>`, `mockH2Origin()`, `https://$DNS_HOST:<BASELINE_MOCK_H2_PORT>`; KDoc `:21` names the four origins and their servers
  - `configure` (`:34-48`): API preset stays on `BASELINE_MOCK_PORT`; `DohResolver` URL (`:45`) becomes `https://$DNS_HOST:${BuildConfig.BASELINE_MOCK_H2_PORT}/dns-query`; KDoc `:30-33` says DoH is on the h2 port
- Modify: `app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp.kt:26` - `BaselineInstrumentationEnvironment.mockH2Origin() + "/github"`
- Modify: `app/src/instrumentation/java/com/kino/puber/profile/BaselineNetworkBlocker.kt:13-17` - KDoc only: the allowed origins are `BaselineInstrumentationEnvironment.allowedOrigins()`
- Modify: `docs/release.md:121-123` - the mock listens on `127.0.0.1:18765` (`puber.baselineMockPort`, HTTP/1.1: API, images, stream) and `127.0.0.1:18766` (`puber.baselineMockH2Port`, HTTP/2: DoH `dns.baseline.test`, `/github`); the blocker sentence (`:125-126`) says each test host is allowed on its own server's port
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineInstrumentationEnvironmentTest.kt`, `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineNetworkBlockerTest.kt`

**Consumes:** `BuildConfig.BASELINE_MOCK_PORT`, `BuildConfig.BASELINE_MOCK_H2_PORT` (Task 1); `DohResolver` equality as used at `BaselineInstrumentationEnvironmentTest.kt:55-58`
**Produces:** `BaselineInstrumentationEnvironment.mockH2Origin()`

Tests (`com.kino.puber.BuildConfig` for every port; no 18765/18766 literal):

| Test | Change |
|---|---|
| `BaselineInstrumentationEnvironmentTest.configure_pinsApiToH1PortAndDohToH2Port` (replaces `configure_pinsApiToTheMockHostAndResolver`, `:48-59`) | `MAIN_API_BASE_URL` = `https://api.baseline.test:${BASELINE_MOCK_PORT}/v1/`, `EXTRA_API_BASE_URL` = `https://api.baseline.test:${BASELINE_MOCK_PORT}/`, `DNS_RESOLVER` = `DohResolver("https://dns.baseline.test:${BASELINE_MOCK_H2_PORT}/dns-query", listOf("127.0.0.1"))` |
| `BaselineInstrumentationEnvironmentTest.allowedOrigins_listsEachServedOriginOnItsServerPort` (new) | `assertNotEquals(BASELINE_MOCK_PORT, BASELINE_MOCK_H2_PORT)`; `allowedOrigins()` equals exactly {`https://127.0.0.1:h1`, `https://api.baseline.test:h1`, `https://127.0.0.1:h2`, `https://dns.baseline.test:h2`}; `mockH2Origin()` = `https://127.0.0.1:h2` |
| `BaselineNetworkBlockerTest` fixture `:17-21` | `allowedOrigins = BaselineInstrumentationEnvironment.allowedOrigins()`; `h1`, `h2` from BuildConfig; `other = maxOf(h1, h2) + 1` |
| `select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers` `:46-53`, `select_returnsDeadProxyAndJournals_whenAllowedTestHostPortDiffers` `:87-95` | port `other` (D9) |
| `select_returnsNoProxy_whenOriginIsMock` `:55-63` | `127.0.0.1:h1` and `127.0.0.1:h2` both get `NO_PROXY` |
| `select_returnsNoProxy_whenOriginIsApiOrDnsTestHost` `:65-75` | `api.baseline.test:h1`, `dns.baseline.test:h2` |
| `select_returnsDeadProxyAndJournals_whenTestHostIsNotAllowed` `:77-85` | port `h1` |
| `select_returnsDeadProxyAndJournals_whenApiOnH2OrDnsOnH1` (new) | `https://api.baseline.test:h2/x` and `https://dns.baseline.test:h1/dns-query` get the dead proxy (`assertDeadProxy`, `:26-33`); journal lists both |

- [x] Write the two new red tests and the replaced `configure_pinsApiToH1PortAndDohToH2Port` first; run the instrumentation unit tests; they fail on the unchanged environment (DoH on h1, three origins); record the red run
- [x] Change the environment, app, blocker KDoc and the remaining blocker tests; update `docs/release.md`
- [x] Instrumentation unit tests pass; fast gate exits 0; commit "Point DoH and the update check at the HTTP/2 mock port and allow each test host only on its server"

**Done when:** `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` and the fast gate exit 0, and `rg -n '1876[56]' app/src/testInstrumentation app/src/instrumentation` prints nothing.

---

### Task 3: Enriched fixtures

**Covers:** AC-4, design D7, D8
**Files:**
- Modify: `baselineprofile/src/main/assets/baseline/item.json` - keep every current field; add on `item`: `"duration":{"average":24.5,"total":24}`, `"advert":false`, `"subscribed":false`, `"in_watchlist":true`, `"poor_quality":false`, `"finished":true`, `"fps":23.976`, `"watched":1`, `"bookmarks":[{"id":1,"title":"Буду смотреть"}]` (the folder of `bookmarks.json`), `"age_rating":null`; on `videos[0]`: `"watched":1`. Fields per `app/src/main/java/com/kino/puber/data/api/models/Models.kt:57` (`duration`), `:62-64`, `:72`, `:81`, `:85`, `:87`, `:95` (`fps: Float?`), `:96` (`age_rating`), `:209-212` (`Duration.average: Double?`), `:283` (`Video.watched`), `:346-351` (`Bookmark`)
- Modify: `baselineprofile/src/main/assets/baseline/items.json` - entry `id 2` gains `"watched":1,"advert":false,"subscribed":false,"poor_quality":false`; other entries unchanged
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineFixtureContentTest.kt`

**Consumes:** `ApiResponseList` (`Models.kt:11`), `ApiResponse` (`Models.kt:18`), `Item` (`Models.kt:46`), `Item.isFullyWatched()` (`Models.kt:164-176`; a movie is watched when `watched > 0`), the app's decoder settings `KinoPubApiClient.kt:133-137` (`ignoreUnknownKeys`, `isLenient`); movie bookmark state `DetailsInteractor.kt:49-52` (non-empty `bookmarks`)
**Produces:** fixtures the Task 4 journeys decode and draw

Tests:

| Test | Asserts |
|---|---|
| origin `:25` | `"https://127.0.0.1:${BuildConfig.BASELINE_MOCK_PORT}"`; `lenientJson` (`:26`) adds `isLenient = true` |
| `itemFixtures_decodeAsAppItems` (new, guard) | `items.json` decodes as `ApiResponseList<Item>` with non-empty `items`; `item.json` (with `{{ID}}` -> `1`, `{{MOCK}}` -> origin) decodes as `ApiResponse<Item>` with non-null `item` |
| `itemFixtures_carryWatchedAndBookmarkedMovie` (new, red) | decoded `item.json` item: `isFullyWatched()`, `inWatchlist == true`, `bookmarks` non-empty; `items.json` `items.drop(1).any { it.isFullyWatched() }` |
| `itemFixtures_carryBooleanFloatAndDoubleValues` (new, red) | decoded `item.json` item: `advert`, `subscribed`, `poorQuality`, `finished`, `fps`, `duration?.average` all non-null |
| `itemFixture_carriesExplicitNull` (new, P2, red) | `parsed("item.json")` `item` object has a value that `is JsonNull` |
| `fixtures_haveNoUrlOutsideMockOrigin` `:54-64`, `fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys` `:66-75` (guards) | unchanged bodies; origin from BuildConfig |

- [x] Write the four new tests and the origin change first; run the instrumentation unit tests; the three red tests fail on the current fixtures; record the red run
- [x] Edit both fixtures (single-line JSON, as now)
- [x] Instrumentation unit tests pass; fast gate exits 0; commit "Add watched, bookmarked and primitive fields to the baseline mock fixtures"

**Done when:** the instrumentation unit tests and the fast gate exit 0, and `rg -n '"(imdb|trailer|subtitles|seasons)"' baselineprofile/src/main/assets/baseline` prints nothing.

---

### Task 4: Content guard and regeneration on the emulator

**Covers:** AC-5, AC-6, AC-7 (counts for qa), AC-8
This device run is the implementer's: its output is a committed source file the red guard depends on. qa reads its log and owns the evidence file and Manual checks 1-3.
**Files:**
- Modify: `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt` - new test `baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons` after `baselineProfileCoversImageTlsAndHlsPaths` (`:60-68`), same shape, on `baseline` via `hasRuleFor` (`:19`), prefixes: `okhttp3/internal/http1/Http1ExchangeCodec`, `okhttp3/internal/http2/Http2Connection`, `kotlinx/serialization/internal/BooleanSerializer`, `kotlinx/serialization/internal/FloatSerializer`, `kotlinx/serialization/internal/DoubleSerializer`, `com/adamglin/phosphoricons/fill/EyeKt`, `com/adamglin/phosphoricons/fill/BookmarksimpleKt`; existing tests unchanged
- Modify (generated): `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt`

**Consumes:** Tasks 1-3; `tools/generate-baseline-profile.sh`; `tools/emulator-resource-lock.sh` (usage `:17-28`)
**Produces:** regenerated profiles; logcat path, results location and counts in the Implementation record

- [x] Write the guard; `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.BaselineProfileContentTest'` fails on it (six of seven prefixes absent at 08f50b81); record the red run
- [x] `tools/emulator-resource-lock.sh acquire host.docker.internal:5555` (keep the token), `ssh mac-emu start` (prints `ready`), `adb connect host.docker.internal:5555`
- [x] With no `.env`: `AGENT_GRADLE_SERIAL=1 DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh` exits 0. Expected: `startupProfile`, `browseAndDetailsProfile`, `railNavigationProfile`, `playbackProfile` and every `BaselineMockSelfTest` test pass; each `PuberGen` `iteration` and `late after=` line shows `unknown=[]` and an empty journal (`result=-1`); `GET /github/...` and `POST /dns-query` matched
- [x] On a failure from a missing route: add the route on its server in `BaselineMockBackend.kt` (never loosen `JourneyCheck` or the blocker) and rerun once; list it under Deviations. On a `playbackProfile` failure at the player check (`BaselineProfileGenerator.kt:177-180`, design R2): stop, keep the fixtures, record under **Blocked** as a question. No loop of reruns
- [x] `tools/emulator-resource-lock.sh release host.docker.internal:5555 <token>`; `ssh mac-emu stop` if `tools/emulator-resource-lock.sh status` shows no other lease
- [x] Counts (Test design Perf method) into the Implementation record, both columns: for each prefix `okhttp3/internal/http1`, `kotlinx/serialization/internal`, `kotlinx/serialization/descriptors`, `com/adamglin/phosphoricons`, `okhttp3/internal/http2`, `okhttp3/internal/tls`: `grep -c "L<prefix>/" app/src/main/generated/baselineProfiles/baseline-prof.txt` and the same on `git show 5cd3e37e:app/src/main/generated/baselineProfiles/baseline-prof.txt`. Thresholds: `http1` >= 40, `serialization/internal` >= 188, `serialization/descriptors` >= 163, `phosphoricons` >= 56, counted with `grep -c 'Lcom/adamglin/phosphoricons/'`. A miss is recorded under **Blocked**, not fixed with `imdb`/`trailer` fixtures
- [x] `grep -c '^[^H].*->' app/src/main/generated/baselineProfiles/baseline-prof.txt` is 0; `git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res` is empty (AC-8)
- [x] Full gate exits 0; commit guard and both profiles together: "Regenerate baseline profiles against the two-protocol mock and guard the HTTP/1.1, serializer and filled-icon rules"
- [x] Record: logcat file path printed by the script, the `BaselineMockSelfTest` results under `baselineprofile/build/outputs/androidTest-results/`, the count table

**Done when:** generation exits 0 with `unknown=[]` in every `PuberGen` line, `BaselineProfileContentTest` passes on the regenerated files, the four gating counts meet their thresholds, and the full gate exits 0.

---

### Task 5: Series fixture, list and details levers, series-only ban policy

**Covers:** AC-4, design D11, D14-D17, R13. Tests first.
**Files:**
- Create: `baselineprofile/src/main/assets/baseline/item_serial.json` - single-line JSON, literal `"id":2` (no `{{ID}}`), fields below
- Modify: `baselineprofile/src/main/assets/baseline/items.json` - entry `id 2`: `"type":"serial"`, `"watched":1`, `"new":1`, `"total":2`, drop `advert`/`subscribed`/`poor_quality`; entry `id 3` gains `"watched":1,"advert":false,"subscribed":false,"poor_quality":false` (D15); every one of the 12 entries gains `"unmodelled":0` (D16)
- Modify: `baselineprofile/src/main/assets/baseline/item.json` - keep every field; add on `item` `"unmodelled":0`, `"genres":[{"id":1,"title":"Драма"}]`, `"countries":[{"id":1,"title":"Россия"}]`, `"tracklist":[{"artists":"Baseline","title":"Baseline theme"}]` (no `url`); no `audios`, `subtitles` or `translation` key (D14)
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt`:
  - `NAMES` (`:372-375`) gains `"item_serial"`
  - `BANNED_KEY` (`:377`) becomes `Regex("\"(imdb|trailer)\"")`; new `private val SERIES_ONLY_KEY = Regex("\"(seasons|subtitles)\"")` and `private const val SERIES_FIXTURE = "item_serial"` next to it
  - `validate()` (`:362-369`): after the banned-key check, `if (name != SERIES_FIXTURE)` check `SERIES_ONLY_KEY.find(body) == null` with message `"Baseline fixture $name carries $key, allowed only in $SERIES_FIXTURE"`
  - KDoc `:349-352`: no real URL, no `imdb`/`trailer`; season and subtitle data only in `item_serial`, the never-played series
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineFixtureContentTest.kt`, `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockSelfTest.kt`

`item_serial.json` (`Models.kt` fields: `Item` `:46`, `:52-53`, `:64`, `:76`, `:83`, `:86-88`; `Season` `:542-547`; `Episode` `:550-562`; `Audio` `:302-310`; `AudioType` `:313`; `AudioAuthor` `:320`; `SubtitleLink` `:437-444`; `VideoFile` `:579-587`; `Translation` `:610-613`; `WatchingInfo` `:565-571`):

| Where | Content |
|---|---|
| `item` | `"id":2`, `"title":"Baseline series"`, `"type":"serial"`, `"plot":"Deterministic series"`, `"in_watchlist":true`, `"watched":1`, `"new":1`, `"total":2`, `"unmodelled":0`, `genres`, `countries`, `tracklist` as in `item.json`, `posters` as in `item.json` (`{{MOCK}}/media/poster.jpg`, `wide.jpg`), one season |
| `seasons[0]` | `"id":1,"number":1,"title":"Season 1"`, two episodes |
| each episode | `id` 201 / 202, `number` 1 / 2, `title`, `"thumbnail":"{{MOCK}}/media/wide.jpg"`, `"duration":24`; `audios[0]` = `{"id":1,"index":1,"codec":"aac","channels":2,"lang":"rus","type":{"id":1,"title":"Дубляж","short_title":"Дб"},"author":{"id":1,"title":"Baseline studio","short_title":"BS"}}`; `subtitles[0]` = `{"lang":"rus","url":"{{MOCK}}/media/subtitle.vtt","shift":0,"embed":false,"forced":false}`; `files[0]` = `{"url":{"hls4":"{{MOCK}}/media/stream.m3u8"},"quality":"360p","w":320,"h":180,"translation":{"id":1,"title":"Дубляж","type":"voice","lang":"rus"}}` |
| episode 1 / 2 | `"watched":1` / `"watched":0,"watching":{"time":5,"status":0}` |

No `imdb`, `trailer`, `bookmarks` or `videos` key in `item_serial.json`.

Tests in `BaselineFixtureContentTest.kt` (helper `decodedSerial()` next to `decodedItem()` `:62-65`, same shape on `item_serial.json`; import `com.kino.puber.data.api.models.isSeriesLike` (`Models.kt:145`) and `kotlinx.serialization.descriptors.elementNames`):

| Test | Priority | Asserts |
|---|---|---|
| `fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture` (replaces `fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys` `:119-128`) | P0 guard | offenders = every fixture's `keys()` in `{imdb, trailer}`, plus keys in `{seasons, subtitles}` for every fixture but `item_serial.json`; equals empty |
| `serialFixture_decodesAsSeriesWithTwoEpisodesOnMockOrigin` | P0 red | `decodedSerial()`: `type.isSeriesLike()`, `inWatchlist == true`, `seasons` has one season with two episodes, each `files!![0].url?.hls4 == "$origin/media/stream.m3u8"`; some episode has `audios[0].type` and `.author` non-null, `files[0].translation` non-null, `subtitles[0].url` starting with `origin` |
| `movieFixture_carriesNoTrackData` | P0 guard | `parsed("item.json").keys()` contains none of `audios`, `subtitles`, `translation` |
| `itemsFixture_secondEntryIsSeries` | P1 red | `decodedItems().single { it.id == 2 }`: `type.isSeriesLike()`, `(new ?: 0) > 0` |
| `itemFixtures_carryUnmodelledKey` | P1 red | `val modelled = Item.serializer().descriptor.elementNames.toSet()`; every object of `parsed("items.json")["items"]`, `parsed("item.json")["item"]`, `parsed("item_serial.json")["item"]` has a key not in `modelled` |
| `detailsFixtures_carryGenresCountriesAndTracklist` | P1 red | `decodedItem()` and `decodedSerial()`: `genres`, `countries`, `tracklist` each non-empty |
| `movieFixtures_carryPostersAndHlsStream` (extend `:130-146`) | P1 | posters check also on `parsed("item_serial.json")["item"]`; every episode `thumbnail == "$origin/media/wide.jpg"` |

Existing tests stay; `itemFixtures_carryWatchedAndBookmarkedMovie` (`:79-86`) must pass with the watched movie now at id 3.

Self-test in `BaselineMockSelfTest.kt` (device only):

| Test | Priority | Asserts |
|---|---|---|
| `validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture` | P0 red | `body = """{"item":{"seasons":[],"subtitles":[]}}"""`; `BaselineFixtures(mapOf("item_serial" to body), { ByteArray(0) }, origin).validate()` passes; the same body under `"item"` throws `IllegalStateException`; `item_serial` with `{"item":{"imdb":1}}` and with `{"item":{"trailer":{}}}` each throw |

- [x] Write the unit-instr tests first; run `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` on the unchanged fixtures; record the red run (`serialFixture_*`, `itemsFixture_*`, `itemFixtures_carryUnmodelledKey`, `detailsFixtures_*`, extended `movieFixtures_*` fail on the missing fixture or field; the two guards pass)
- [x] Write the self-test; add `item_serial.json`, edit `items.json` and `item.json`; change `validate()`, `NAMES`, the regexes and the KDoc
- [x] Instrumentation unit tests, fast gate and generator compile (`:baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin`) exit 0; commit "Add a never-played series fixture and allow season and subtitle keys only in it"

**Done when:** the three commands exit 0; `rg -n '"(imdb|trailer)"' baselineprofile/src/main/assets/baseline` prints nothing; `rg -ln '"(seasons|subtitles)"' baselineprofile/src/main/assets/baseline` prints only `item_serial.json`. This replaces Task 3's banned-key observation.

---

### Task 6: Series route for id 2 outside Playback

**Covers:** AC-1 (route record), AC-4, design D12-D14, R10, R11
**Files:**
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt` - in `routesFor` (`:142-203`), directly before the generic `/v1/items/\\d+` route (`:183-186`): `if (!playback) route("/v1/items/2", required = browse, respond = json(fixtures.json("item_serial")))` (h1, chunked through `json()` `:154-155`). Route order is the match order (`firstOrNull`, `:126`). KDoc of `routesFor` (`:136-140`) names the series route and why Playback lacks it
- Test: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockSelfTest.kt`

**Consumes:** `item_serial` fixture (Task 5); `BaselineMockBackend.startIteration` (`:88-95`), `verify().missingRequiredRoutes` (`:106-108`, description `"$method $pathText"` `:320-321`); self-test helpers `get`/`call` (`:81-84`), `assertCheckFails` (`:95-107`)
**Produces:** required route `GET /v1/items/2` in BrowseAndDetails, matched in Startup and Rail when the prefetcher warms id 2, absent in Playback

Self-tests (device only):

| Test | Priority | Asserts |
|---|---|---|
| `seriesRoute_servesSeriesInBrowse_movieInPlayback` | P0 red | `startIteration(BrowseAndDetails)`; `call("$origin/v1/items/2")` body contains `"type":"serial"` and `"seasons"`; `startIteration(Playback)`; the same GET's body contains `"id":2` and `"type":"movie"` and no `"seasons"` |
| `browseScenario_requiresSeriesRoute` | P1 red | `startIteration(BrowseAndDetails)`; `hitStartupRoutes()` (`:86-93`), `/v1/items/similar`, `/v1/items/1`, `/media/poster.jpg` each 200; `verify().missingRequiredRoutes == listOf("GET /v1/items/2")`; `assertCheckFails("Broadcast completed: result=-1", "GET /v1/items/2")` |

Existing self-tests stay (`startupScenario_requiresFreshHistoryGithubAndDnsQuery` `:203-218` is unaffected: it only checks `containsAll`).

- [x] Write both self-tests, then the route and KDoc
- [x] Fast gate and generator compile exit 0; commit "Serve the series fixture for item 2 outside Playback and require it in Browse"

**Done when:** both commands exit 0, and in `BaselineMockBackend.kt` the `"/v1/items/2"` route line precedes the `"/v1/items/\\d+"` line (`rg -n '"/v1/items/' baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt` lists the `/v1/items/2` line before the generic one).

---

### Task 7: Regeneration on the emulator and AC-7 counts

**Covers:** AC-5, AC-6 (existing guard, unchanged), AC-7, AC-8, design R9-R12
The device run is the implementer's, as in Task 4; qa owns the evidence file and Manual checks 1-3.
**Files:**
- Modify (generated): `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt`

**Consumes:** Tasks 5-6; the Task 4 device procedure (lease, `ssh mac-emu start`, `adb connect`, `tools/generate-baseline-profile.sh`, release, `ssh mac-emu stop`)

- [x] Lease, start and connect the emulator exactly as in Task 4; with no `.env`: `AGENT_GRADLE_SERIAL=1 DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh` exits 0. Expected in addition to Task 4: every `BaselineMockSelfTest` test passes including the three new ones; every `PuberGen` `iteration scenario=BrowseAndDetails` line (`BaselineProfileTestControl.kt:60`) lists `GET /v1/items/2` in `matched=`; no `/media/subtitle.vtt` request anywhere (it would show as `unknown`, R11)
- [x] Stop rules (one run, no rerun loop): a Browse failure with `missing=[GET /v1/items/2]` (R10) or a `playbackProfile` failure at the player check is recorded under **Blocked** as a question, fixtures and route kept, `required` not relaxed; an unknown loopback route other than `/media/subtitle.vtt` follows the Task 4 rule (add it on its server, rerun once, list under Deviations)
- [x] Release the lease; `ssh mac-emu stop` if `tools/emulator-resource-lock.sh status` shows no other lease
- [x] Counts with the Task 4 method and prefixes, three columns (08f50b81, 5cd3e37e, regenerated). Thresholds unchanged: `http1` >= 40, `serialization/internal` >= 188, `serialization/descriptors` >= 163, `phosphoricons` >= 56. A miss on any of them is recorded under **Blocked** with the counts; never loosened, never fixed with `imdb`/`trailer` fixtures
- [x] `grep -c '^[^H].*->' app/src/main/generated/baselineProfiles/baseline-prof.txt` is 0; `git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res` is empty
- [x] Full gate exits 0 (includes `BaselineProfileContentTest` and the Task 5 unit-instr tests); commit both profiles: "Regenerate baseline profiles with the series fixture"
- [x] Append a **Round 2** block to the Implementation record (Task 1-4 entries stay): commits, red run of Task 5, logcat path, results XML, the count table, Blocked if any

**Done when:** generation exits 0 with `unknown=[]` in every `PuberGen` line and `GET /v1/items/2` matched in every BrowseAndDetails iteration, the four gating counts meet their thresholds, and the full gate exits 0; otherwise the record carries **Blocked**.

---

## Implementation record

<!-- owner: implementer -->

**Commits:** (shas in the final commit list below)

1. Task 1: two mock servers, chunked JSON, Date headers
2. Task 2: app-side origins per server
3. Task 3: enriched fixtures
4. Task 4: guard test, regenerated profiles

**Red run** (P0 `red` tests on the unchanged code):

- red: `BaselineInstrumentationEnvironmentTest.configure_pinsApiToH1PortAndDohToH2Port` - expected `DohResolver(url=https://dns.baseline.test:18766/dns-query...)` but was the h1 port
- red: `BaselineInstrumentationEnvironmentTest.allowedOrigins_listsEachServedOriginOnItsServerPort` - expected the four-origin set, was three (`mockH2Origin()` stubbed to `mockOrigin()` so the test compiles)
- red: `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenApiOnH2OrDnsOnH1` - expected HTTP dead proxy but was DIRECT; also red: `select_returnsNoProxy_whenOriginIsMock`, `select_returnsNoProxy_whenOriginIsApiOrDnsTestHost` (expected DIRECT, was dead proxy)
- red: `BaselineFixtureContentTest.itemFixtures_carryWatchedAndBookmarkedMovie` - `expected: <true> but was: <false>`
- red: `BaselineFixtureContentTest.itemFixtures_carryBooleanFloatAndDoubleValues` - `expected: not <null>`
- red: `BaselineFixtureContentTest.itemFixture_carriesExplicitNull` - `expected: <true> but was: <false>`
- green on base (guard): `itemFixtures_decodeAsAppItems` - guard row by design
- red: `BaselineProfileContentTest.baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons` - `no baseline rule for okhttp3/internal/http1/Http1ExchangeCodec` (first missing prefix)
- Task 1 self-tests run only on the device; they passed in the Task 4 run (see below). No host red run exists for them.

**Deviations from the plan:**

- The plan lists the Task 1 self-test red run as not applicable on the host; none done.
- `BaselineInstrumentationEnvironmentTest` stubs `mockH2Origin()` during the red run only (smallest stub), real body in the same task.
- Task 4 commit contains the regenerated profiles although a gating threshold is missed (see Blocked), so the conductor can inspect them.

**Files touched outside the plan:** none.

**For the reviewers:**

- Task 4 run: emulator `host.docker.internal:5555`, lease taken and released, `ssh mac-emu stop` done (no other lease). `tools/generate-baseline-profile.sh` exit 0, BUILD SUCCESSFUL in 20m 6s, no `.env`.
- Logcat: `/tmp/puber-baseline-profile/20261126-200235-logcat.txt` (88 `PuberGen` lines; 40 `iteration` lines, every one `result=-1`; 4 `late after=` lines; 0 lines without `unknown=[]`). `GET /github/...` and `POST /dns-query` matched in each iteration. (Emulator clock reads 11-23 in the log.)
- Results: `baselineprofile/build/outputs/androidTest-results/connected/nonminifiedrelease/flavors/instrumentation/TEST-sdk_google_atv64_arm64 - 16.xml`: 22 tests, 0 failures, 2 skipped (`startupWithProfile`, `startupWithoutProfile`); `BaselineMockSelfTest` 16/16 incl. the five new tests; `startupProfile`, `browseAndDetailsProfile`, `railNavigationProfile`, `playbackProfile` pass.
- Profile comparison printed by the plugin: baseline 47737 -> 47643 rules, startup 31786 -> 32023.
- AC-8: `git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res` is empty; `grep -c '^[^H].*->'` is 0.
- `app/src/main/generated/baselineProfiles/*` shifted by about 0.7-0.9 percent of rules besides the target prefixes.

Counts (`grep -c "L<prefix>/"`):

| Prefix | 08f50b81 | 5cd3e37e | Regenerated | Threshold | Result |
|---|---|---|---|---|---|
| okhttp3/internal/http1 | 0 | 51 | 54 | >= 40 | ok |
| kotlinx/serialization/internal | 165 | 234 | 188 | >= 188 | ok (equal) |
| kotlinx/serialization/descriptors | 137 | 203 | 149 | >= 163 | **miss** |
| com/adamglin/phosphoricons | 51 | 69 | 57 | >= 56 | ok |
| okhttp3/internal/http2 | 292 | 316 | 282 | - | info |
| okhttp3/internal/tls | 32 | 30 | 32 | - | info |

**Blocked:**

Perf threshold missed: `kotlinx/serialization/descriptors` is 149, threshold 163 (base 137, reference 203). Per the plan it is recorded, not fixed: no `imdb`/`trailer` fixture added, no check loosened, no rerun. `kotlinx/serialization/internal` meets its threshold exactly (188). Question for the conductor: accept 149 (the fixtures carry no seasons, trailer or imdb models, which hold the remaining descriptors of the reference profile), lower the threshold, or add fixture content.

### Round 2 (Tasks 5-7)

**Commits:** Task 5 `78695874` (series fixture, ban policy), Task 6 `923439cf` (series route), Task 7 "Regenerate baseline profiles with the series fixture" (the commit holding this block).

**Red run, Task 5** (unchanged fixtures, `:app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`, 30 tests, 5 failed):

- red: `serialFixture_decodesAsSeriesWithTwoEpisodesOnMockOrigin` - `NoSuchElementException` (`item_serial.json` absent)
- red: `detailsFixtures_carryGenresCountriesAndTracklist` - `NoSuchElementException` (`item_serial.json` absent)
- red: `movieFixtures_carryPostersAndHlsStream` (extended) - `NoSuchElementException` (`item_serial.json` absent)
- red: `itemFixtures_carryUnmodelledKey` - `NoSuchElementException` (`item_serial.json` absent)
- red: `itemsFixture_secondEntryIsSeries` - `AssertionFailedError`, id 2 was a movie
- green on base (guards, by design): `fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture`, `movieFixture_carriesNoTrackData`
- Self-tests of Tasks 5 and 6 (`validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture`, `seriesRoute_*`, `browseScenario_requiresSeriesRoute`) run only on the device: no host red run; they passed in the Task 7 run.

**Task 7 run:** emulator `host.docker.internal:5555`, lease taken and released, `ssh mac-emu stop` done (no other lease). `tools/generate-baseline-profile.sh` exit 0, BUILD SUCCESSFUL in 14m 4s, no `.env`.

- Logcat: `/tmp/puber-baseline-profile/20261127-052845-logcat.txt`. 32 `iteration` lines and 4 `late after=` lines, every one `unknown=[]` and `missing=[]` (the other 32 `PuberGen` lines are `Broadcast completed: result=-1`). `GET /v1/items/2` matched in all 5 BrowseAndDetails iterations (also in Rail). No `subtitle.vtt` request.
- Results: `baselineprofile/build/outputs/androidTest-results/connected/nonminifiedrelease/flavors/instrumentation/TEST-sdk_google_atv64_arm64 - 16.xml`: `BaselineMockSelfTest` 19/19 (incl. the three new), `BaselineProfileGenerator` 4/4, `StartupBenchmarks` 2 skipped.
- Profile comparison printed by the plugin: baseline 47643 -> 47605 rules (288 added, 38 removed), startup 32023 -> 32305.
- AC-8: `git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res` empty; `grep -c '^[^H].*->'` is 0.
- Full gate exit 0 (`testDevDebugUnitTest`, detekt, lint, instrumentation unit tests, tooling script).

| Prefix | 08f50b81 | 5cd3e37e | Regenerated | Threshold | Result |
|---|---|---|---|---|---|
| okhttp3/internal/http1 | 0 | 51 | 54 | >= 40 | ok |
| kotlinx/serialization/internal | 165 | 234 | 235 | >= 188 | ok |
| kotlinx/serialization/descriptors | 137 | 203 | 203 | >= 163 | ok |
| com/adamglin/phosphoricons | 51 | 69 | 57 | >= 56 | ok |
| okhttp3/internal/http2 | 292 | 316 | 284 | - | info |
| okhttp3/internal/tls | 32 | 30 | 32 | - | info |

The Round 1 **Blocked** (descriptors 149) is resolved by the series fixture.

**Deviations:** none. Files outside the plan: none.

**Blocked:** none.

### Fix round 1 (triage-1 findings 1, 2, 4, 5, 6, 7)

**Commits:** see `git log` after `f04f6e36`: fixture and mock fixes, Browse dwell, populated item-folders response, regenerated profiles with the folder guard.

| # | Fix |
|---|-----|
| 1 | `item.json` is the plain movie (empty `bookmarks`, not in watchlist, not watched), so the details screen takes the `get-item-folders` lookup; the route is `required` in Browse and answers with `bookmark_folders.json` (one watch-later folder, replaces `empty_bookmark_folders.json`) so the lookup also fills the bookmark icon. Guard `BaselineProfileContentTest.baselineProfileCoversBookmarkFolderLookup` |
| 2 | The movie Playback plays is never watched or bookmarked (`itemFixture_isUnwatchedAndUnbookmarked_soDetailsAndPlaybackTakeTheFolderLookup`). Result: player counts did not recover, see Blocked |
| 4 | `everyResponse_carriesDateHeader` covers `/media/poster.jpg` and the DoH responses |
| 5 | Chunking is decided by the dispatching server (`chunkedBodies()`), not by an argument next to the route's `server` |
| 6 | `Http2Connection` is marked as a guard in the content test (88 -> 84 rules at base, present before the change) |
| 7 | Red-by-construction reasoning per Task 1 self-test below |

**Red run (fix round 1, `:app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`):**

- red: `itemFixture_isUnwatchedAndUnbookmarked_soDetailsAndPlaybackTakeTheFolderLookup` - `AssertionFailedError` (`item.json` was fully watched)
- red: `bookmarkFoldersFixture_listsTheWatchLaterFolder_soTheLookupFillsTheBookmarkIcon` - `NoSuchElementException` (`bookmark_folders.json` absent)
- red: `BaselineProfileContentTest.baselineProfileCoversBookmarkFolderLookup` - `AssertionFailedError` on the committed profile (0 rules for `getItemBookmarkFolders`)
- Self-test rows (device only, passed in the final run, no host red run). Red by construction against base 08f50b81: `apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2`, `jsonRouteOnH1_isChunked_mediaRouteIsFixedLength`, `everyResponse_carriesDateHeader`, `routeOfOtherServer_returns404AndIsListedAsUnknown`, `close_stopsBothServers` cannot compile or run there (no `h2Port`, `boundH2Port`, `BaselineMockServer`; one server, no Date, no chunking). `seriesRoute_servesSeriesInBrowse_movieInPlayback` and `browseScenario_requiresSeriesRoute` fail on base because `/v1/items/2` answers the movie in every scenario and no route is required for it. `browseScenario_requiresItemFoldersRoute` fails against f04f6e36 because `get-item-folders` was not required.
- guard: `Http2Connection` prefix in `baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons` (green on base).

**Device runs** (emulator `host.docker.internal:5555`, lease taken and released each time, `ssh mac-emu stop` after, loopback mock only): four runs of `tools/generate-baseline-profile.sh`.

| Run | Exit | Why it ended |
|---|---|---|
| 1 | 2 | Browse: id 1 served by a separate watched-movie route left the generic item route unhit and `GET /v1/items/2` missing; self-test `browseScenario_requiresSeriesRoute` failed on the same route split. The watched variant could not coexist with the folder lookup: only ids 1 (opened) and 2 (series, prefetched) are ever requested, so the opened movie must be the plain one. Variant removed |
| 2 | 2 | Browse: `missing=[GET /v1/items/2]` with the folder lookup hit. The journey pressed OK right after the last D-pad move, so the 750 ms neighbour dwell of `DetailsPrefetcher` was a race (round 2 won it 5 of 5, this one lost) |
| 3 | 0 | After a 1.5 s rest before OK in the Browse journey. `BookmarksimpleKt` (fill) rules were gone: the lookup returned no folder, so no bookmark icon was filled. Profiles not committed |
| 4 | 0 | Final. Logcat `/tmp/puber-baseline-profile/20261127-063806-logcat.txt`: 41 `iteration` lines, every one `missing=[]` and `unknown=[]`, 4 `late after=` lines `unknown=[]`; `GET /v1/items/2` and `GET /v1/bookmarks/get-item-folders` matched in all 11 BrowseAndDetails iterations; no `subtitle.vtt`. Results XML (same path as round 2): 26 tests, 0 failures, 2 skipped (`StartupBenchmarks`) |

AC-8: `git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res` empty; `grep -c '^[^H].*->'` is 0. Full gate exit 0.

| Prefix | 08f50b81 | f04f6e36 | Fix round 1 | Threshold | Result |
|---|---|---|---|---|---|
| okhttp3/internal/http1 | 0 | 54 | 54 | >= 40 | ok |
| kotlinx/serialization/internal | 165 | 235 | 237 | >= 188 | ok |
| kotlinx/serialization/descriptors | 137 | 203 | 205 | >= 163 | ok |
| com/adamglin/phosphoricons | 51 | 57 | 63 | >= 56 | ok |
| `getItemBookmarkFolders` rules | 8 | 0 | 8 | guard | ok |
| `BookmarkFoldersResponse` rules | 21 | 0 | 21 | guard | ok |
| androidx/media3/exoplayer | 3490 | 3312 | 3313 | - | -5.1% vs base |
| androidx/media3/exoplayer/hls | 387 | 349 | 348 | - | -10.1% vs base |
| AudioTrackPreferenceResolver | 57 | 48 | 48 | - | -15.8% vs base |
| SubtitleTrackMerger | 16 | 12 | 12 | - | -25% vs base |
| PlayerTextTracks | 12 | 9 | 9 | - | -25% vs base |

**Deviations:** (1) one generator change, outside the plan: `BaselineProfileGenerator.browseAndDetailsProfile` rests 1.5 s before OK (run 2 above). (2) AC-4's watched and bookmarked movie in details is not kept: it cannot coexist with the folder lookup; the watched state lives in `items.json` (id 3), the bookmarked state in the series (`in_watchlist`) and in the populated folder lookup. (3) Four device runs instead of one.

**Blocked:** player counts stay below base although the Playback movie is now plain: media3/exoplayer -177, hls -39, `AudioTrackPreferenceResolver` -9, `SubtitleTrackMerger` -4 (lost: `merge`, `enrichPlayerTracks`, `findExactIdentityMatch`, `<init>`; lost resolver rules are the `exactLabelMatch`, `exactSubtitleUrlMatch`, `findAudioTrackIndex`, `findSubtitleTrackIndex` family). The watched flag is therefore not the cause (counts are the same with it removed). Base `item.json` carried no `audios`, `subtitles` or `translation` either, so the track-merge path in base came from something else in the old journey (older mock transport or timing); not traced. Question for the conductor: accept the player-count drop, or investigate what drove the track merge at base.

## Readiness

Verdict: **READY**

Spec and plan at worktree commit 28c49373 (worktree clean). Base 08f50b81. Round 1: [readiness-1.md](readiness-1.md).

### Round 1 fix

| # | Fix | Result |
|---|---|---|
| 1 | Phosphoricons Perf row 51 / 69, threshold 56, same in plan Task 4 | Done: spec Perf row `51 | 69 | >= 56`, plan Task 4 `phosphoricons >= 56` with `grep -c 'Lcom/adamglin/phosphoricons/'`. Rerun on the committed profiles: 51 at 08f50b81, 69 at 5cd3e37e |

The other gating baselines also match a rerun of the stated method: `http1` 0 / 51, `serialization/internal` 165 / 234 (threshold 188), `serialization/descriptors` 137 / 203 (threshold 163).

### AC coverage

Unchanged from round 1 (readiness-1.md, AC coverage); every AC-1..AC-8 has a plan task and a Test design row.

### Citations sampled (round 2, new ones)

| Citation | Claim | Result |
|---|---|---|
| `BaselineMockBackend.kt:116-123`, `:125`, `:153-158`, `:213-225`, `:257` | `route(...)`, `json(...)`, item/github/dns routes, text/binary builders, `BaselineScenario` | resolves |
| `Models.kt:57`, `:95-96`, `:164-176` | `duration`, `fps: Float?`, `age_rating`, `Item.isFullyWatched()` (movie: `watched > 0`) | resolves |
| `DetailsInteractor.kt:49-52` | movie bookmark state from non-empty `bookmarks` | resolves |
| `BaselineInstrumentationEnvironmentTest.kt:48-59` | `configure_pinsApiToTheMockHostAndResolver`, DoH on 18765 | resolves |
| `BaselineNetworkBlockerTest.kt:17-21`, `:26-33`, `:46-53`, `:55-63`, `:65-75`, `:77-85`, `:87-95` | allowed set, `assertDeadProxy`, the five named tests | resolves |
| `BaselineFixtureContentTest.kt:25`, `:26`, `:54-64`, `:66-75` | origin literal, `lenientJson` without `isLenient`, URL and banned-key guards | resolves |
| `BaselineMockSelfTest.kt:31-44`, `:47-57`, `:64-65`, `:72` | `setUp`, `dohResolver` on `boundPort`, `get`, `/github` in `hitStartupRoutes` | resolves |
| `BaselineProfileContentTest.kt:19`, `:60-68`; `emulator-resource-lock.sh:17-28` | `hasRuleFor`, image/TLS/HLS test, lock usage | resolves |

### Order, build, scope

Unchanged from round 1: order 1 -> 2 -> 3 -> 4, each task ends with a green fast gate and one commit, no `app/src/main/java` or `app/src/main/res` file, no `imdb`/`trailer` fixture, Q1-Q3 answered and non-blocking, every P0 row names its mutation, is `red` or `guard` (device rows are manual checks) and names its test in backticks.

### Fix list

None.

## Readiness (round 3, Tasks 5-7)

Verdict: **READY**

Spec and plan at worktree commit 69eeca1d (worktree clean). Base 08f50b81. Tasks 1-4: [readiness-2.md](readiness-2.md).

#### AC coverage (delta)

| AC | Plan task | Test design rows |
|---|---|---|
| AC-1 (route record) | Task 6 | `seriesRoute_servesSeriesInBrowse_movieInPlayback` (P0 red), `browseScenario_requiresSeriesRoute` (P1 red) |
| AC-4 | Task 5 | `fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture` (P0 guard), `serialFixture_decodesAsSeriesWithTwoEpisodesOnMockOrigin` (P0 red), `movieFixture_carriesNoTrackData` (P0 guard), `validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture` (P0 red), four P1 rows |
| AC-5, AC-6, AC-7, AC-8 | Task 7 | Manual 1-3, Perf (thresholds unchanged, descriptors >= 163) |

#### Citations sampled

| Citation | Claim | Result |
|---|---|---|
| `BaselineMockBackend.kt:126`, `:142-203`, `:154-155`, `:183-186` | first-match dispatch per server, `routesFor`, `json()` chunked on h1, generic `/v1/items/\\d+` route | resolves |
| `BaselineMockBackend.kt:88-95`, `:106-108`, `:320-321`, `:323-325` | `startIteration`, `missingRequiredRoutes`, description `"$method $pathText"`, full-path `matches` (so `/v1/items/2` cannot match `/v1/items/12`) | resolves |
| `BaselineMockBackend.kt:349-352`, `:362-369`, `:372-375`, `:377` | fixtures KDoc, `validate()`, `NAMES`, `BANNED_KEY` with four keys | resolves |
| `BaselineFixtureContentTest.kt:41-44`, `:62-65`, `:79-86`, `:119-128`, `:130-146` | fixtures map of every `*.json` (picks up `item_serial.json`), `decodedItem()`, watched-movie test, ban test, posters/HLS test | resolves |
| `BaselineMockSelfTest.kt:81-84`, `:86-93`, `:95-107`, `:203-218` | `get`/`call`, `hitStartupRoutes`, `assertCheckFails`, `containsAll` startup test | resolves |
| `Models.kt:46-88`, `:145`, `:302-324`, `:437-444`, `:542-571`, `:579-587`, `:610-613` | `Item` fields, `isSeriesLike`, `Audio`/`AudioType`/`AudioAuthor`, `SubtitleLink`, `Season`/`Episode`/`WatchingInfo`, `VideoFile`, `Translation`; the planned `item_serial.json` sets every non-default field | resolves |
| `DetailsPrefetcher.kt:38`, `:76`, `:92`, `:123` | 750 ms neighbour dwell, `onFocused`, neighbour warm, `awaitSettled` | resolves |
| `BaselineProfileGenerator.kt:52`, `:67`, `:75`, `:114`, `:167`, `:176-180`; `BaselineProfileTestControl.kt:60`; `ContentCacheRepository.kt:38` | `clearTarget`, journey idles, player check, `iteration scenario=` log line, item feed | resolves |

Checked by reading: the `browseScenario_requiresSeriesRoute` hits cover every other Browse-required route (`:165`, `:174-175`, `:179`, `:182-183`, `:187-195`), so `missingRequiredRoutes == listOf("GET /v1/items/2")` holds. `item.json` carries `"type":"movie"` and `"id":{{ID}}`, as the Playback half of `seriesRoute_*` expects.

#### Order, build, scope

| Check | Result |
|---|---|
| Order | 5 -> 6 -> 7: Task 5 adds `item_serial` to `NAMES` and narrows the ban; Task 6 consumes it; Task 7 regenerates |
| Build | Each of Tasks 5 and 6 ends with the fast gate, the generator compile and one commit; Task 7 with the full gate |
| Scope | No `app/src/main/java` or `res` file; no `imdb`/`trailer`; `seasons`/`subtitles` only in `item_serial.json` (Q5); Q4-Q7 answered, none blocking |
| P0 rows | Each names its mutation, is `red` or `guard` and names its test in backticks; device rows are manual checks |
| Stop rules | R10 miss and player-check failure are recorded as Blocked, `required` not relaxed, no rerun loop; thresholds not loosened |

#### Notes (non-blocking)

1. Spec Test design Manual 1 step 3 still says "the four new ones" (round 1) and does not list `GET /v1/items/2` matched per BrowseAndDetails iteration; plan Task 7 states both. qa should check against Task 7.
2. D12 (id 2 warmed by the neighbour prefetch in Browse) is inferred, not traced; R10 makes a miss a named failure.

#### Fix list

None.
