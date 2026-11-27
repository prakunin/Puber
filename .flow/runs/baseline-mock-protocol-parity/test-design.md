# Test design: baseline-mock-protocol-parity

Inputs: [brief.md](brief.md), [design-architect.md](design-architect.md), [qa-baseline.md](qa-baseline.md). Base 08f50b81.

Levels (`.flow/roles/test-designer.md`):

| Level | Where | Runs with |
|---|---|---|
| `unit` | `app/src/test` (JUnit 5) | `./tools/agentw testDevDebugUnitTest` |
| `unit-instr` | `app/src/testInstrumentation` (JUnit 5, instrumentation `BuildConfig`) | `./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'` |
| `self-test` | `baselineprofile/src/main/java/.../BaselineMockSelfTest.kt` (JUnit 4 on device) | runs inside `make DEVICE=host.docker.internal:5555 baseline-profile`; results under `baselineprofile/build/outputs/androidTest-results/` |
| `device` | generation run on the emulator, recorded in the evidence file | Manual checks below |

## Risk profile

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

## Traceability

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

## Failing-first test

`BaselineProfileContentTest.baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons` is written first. On the base profile (`git show 08f50b81:app/src/main/generated/baselineProfiles/baseline-prof.txt`) six of the seven prefixes have 0 rule lines (only `Http2Connection` has 84), so it fails today; it turns green only after the AC-5 regeneration. 5cd3e37e has 3 to 47 lines for each prefix, so the guard is reachable.

## Manual checks

Device rules: emulator `host.docker.internal:5555` only, under a lease from `tools/emulator-resource-lock.sh`; generation reaches only the loopback mock; no command touches the live KinoPub API.

1. **Generation (AC-3, AC-5).**
   1. `ssh mac-emu start` (prints `ready`), take the lease, `adb connect host.docker.internal:5555`.
   2. From the worktree: `make DEVICE=host.docker.internal:5555 baseline-profile`.
   3. Expected: exit 0; results XML shows `startupProfile`, `browseAndDetailsProfile`, `railNavigationProfile`, `playbackProfile` passed and every `BaselineMockSelfTest` test passed, including the four new ones; each journey's route journal prints `unknown=[]` and lists `GET /github/...` and `POST /dns-query` as matched; the egress journal is empty.
   4. Must not happen: a request to any non-loopback host, a `playbackProfile` failure at the player check (R2: record it as a question, do not drop the fixtures), a retry loop, a Gradle generation task other than `InstrumentationNonMinifiedRelease`.
   5. Release the lease; `ssh mac-emu stop` if `tools/emulator-resource-lock.sh status` shows no other lease.
2. **Counts (AC-7).** Run the Perf command on the regenerated `baseline-prof.txt` and on `git show 5cd3e37e:...`; write both columns into the evidence file. Expected: every gating row meets its threshold. Must not happen: counting with another method than the one in Perf.
3. **No production change (AC-8).** `git -C <wt> diff --name-only 08f50b81 -- app/src/main/java app/src/main/res`. Expected: empty output. Must not happen: any path listed.

## Perf

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

## UNTESTABLE

N/A: every AC has a test or a manual check.
