# Research: baseline-mock-protocol-parity

## 1. Request

"Keep HTTP/1.1 and serialization paths in the mock-generated baseline profile (okhttp3/internal/http1 51 -> 0)". Deferred from hermetic-baseline-generation triage-2 #5 (`.flow/runs/baseline-mock-protocol-parity/state.md`): the regenerated `baseline-prof.txt` lost `okhttp3/internal/http1` 51 -> 0, `kotlinx/serialization/internal` 197 -> 140, `kotlinx/serialization/descriptors` 73 -> 51, `com/adamglin/phosphoricons` 69 -> 51 against base 5cd3e37e. Find which protocol the real hosts use (from code and existing evidence only), how the loopback mock negotiates, how to restore parity, and content-test guards.

## 2. Understanding

The app's one OkHttp stack offers HTTP/2 and HTTP/1.1 over ALPN, and the last real-account generation (5cd3e37e) recorded both codecs, so in ordinary use some origins answer HTTP/1.1 and some HTTP/2. The hermetic mock is a single MockWebServer that serves every origin (API, images, stream, DNS-over-HTTPS, GitHub) on one HTTPS port with its default ALPN list, so every connection becomes HTTP/2 and the HTTP/1.1 codec never runs. The serialization and icon losses have a different cause: the fixtures leave out the boolean, float/double and watched/bookmarked fields that real payloads carry, so their serializers and the filled icons are never touched. Parity needs an HTTP/1.1 origin on the mock (without losing the HTTP/2 one), richer fixtures, and content-test guards so the next regeneration cannot drop them silently.

## 3. Current state in code

Checked at b27d8e40.

### Client protocol

| Where | Fact |
|---|---|
| `app/src/main/java/com/kino/puber/data/api/network/HttpStack.kt:33` | Base client is `OkHttpClient.Builder().build()`; no `.protocols(...)` anywhere in `app/src/main` (grep), so the default `[HTTP_2, HTTP_1_1]` applies to API, DoH, images and media |
| `data/di/modules.kt:77-79`, `data/api/network/ImageHttpClient.kt:9-17` | Ktor image client is preconfigured with the same `OkHttpClient` |
| `ui/feature/player/vm/PlaybackController.kt:623-638` | Media3 `OkHttpDataSource` derives from the same client (timeouts, optional `Dns.SYSTEM`); protocol untouched |
| `data/api/network/NetworkStats.kt:53-66` | Event listener counts calls, DNS, connects, cache; it logs no negotiated protocol, so no ordinary-use log shows protocol per origin |

### What ordinary use recorded (profiles from real-account generation)

Counts of rule lines by `okhttp3/internal/<pkg>/`, `baseline-prof.txt` (`git show <rev>:app/src/main/generated/baselineProfiles/baseline-prof.txt`):

| Package | 5cd3e37e (real account) | b27d8e40 (mock) |
|---|---|---|
| `http1` | 51 | 0 |
| `http2` | 315 | 291 |
| `connection` | 252 | 237 |
| `http` | 93 | 81 |
| `tls` | 21 | 22 |

- The lost `http1` classes are `Http1ExchangeCodec` with `ChunkedSource`, `FixedLengthSource`, `KnownLengthSink`, and `HeadersReader`: real hosts answered HTTP/1.1 with both chunked and `Content-Length` bodies, and at least one request with a body (POST) went over HTTP/1.1.
- `http2` rules were present at base too, so at least one real origin negotiated h2.
- Also lost from `okhttp3/internal`: `http/DateFormattingKt` (no `Date`/`Last-Modified`/`Expires` headers from the mock), `dns/ResourceRecord$Https`, `dns/InetAddressDns`, `connection/RetryTlsHandshakeKt`, `ech/EchUntrustedException`.
- Which origin speaks which protocol is `unverified`: no evidence file under `docs/superpowers/evidence/` records a protocol (grep for `HTTP/1.1`, `h2`, `ALPN`: only unrelated hits). Inference: HTTP/2 from `dns.google` (DoH) and `api.github.com`, HTTP/1.1 from KinoPub API, CDN or stream hosts (`unverified`).

### Mock

| Where | Fact |
|---|---|
| `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt:27`, `:43-49` | One `MockWebServer`, `useHttps(...)`, bound to `127.0.0.1:<port>`; `protocols` and `protocolNegotiationEnabled` left at defaults, so ALPN picks h2 |
| `BaselineMockBackend.kt:213-225` | `textResponse`/`binaryResponse` set only `Content-Type` and a fixed body: no chunked bodies, no `Date`/cache headers |
| `BaselineMockBackend.kt:135-170` | Route table: API under `/v1`, `/oauth2`, `/github/...`, `/media/*`, `/dns-query` on the same server |
| `app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt:12-46` | Three origins on one port: `https://127.0.0.1` (images, stream, GitHub via `BaselineProfileApp.kt:26`), `https://api.baseline.test` (pinned API), `https://dns.baseline.test/dns-query` (DoH, bootstrap 127.0.0.1); `allowedOrigins()` lists exactly these |
| `baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem` | SAN `IP:127.0.0.1, DNS:api.baseline.test, DNS:dns.baseline.test`; a second port needs no new certificate |
| `baselineprofile/build.gradle.kts:20-26` | One runner argument `baselineMockPort` from `puber.baselineMockPort`; the app side reads `BuildConfig.BASELINE_MOCK_PORT` |

### Fixtures vs the lost serialization and icon rules

| Lost rules (5cd3e37e -> b27d8e40) | Code that needs them | Fixture gap (`baselineprofile/src/main/assets/baseline/*.json`, 6693 B total) |
|---|---|---|
| `internal/BooleanSerializer`, `FloatSerializer`, `DoubleSerializer`, `NullableSerializer`, `descriptors/PrimitiveKind$BOOLEAN/FLOAT/DOUBLE` | `data/api/models/Models.kt:62-95` (`advert`, `subscribed`, `in_watchlist`, `poor_quality`, `finished`, `fps`), `:210` (`average: Double?`), `:537` (`rating: Float?`), `:380-384` (device `support_*`) | `items.json`/`item.json` carry only `id`, `title`, `type`, `posters`, `videos` (`item.json` one movie, one video, one file) |
| `json/JsonNames`, `json/internal/TreeJsonDecoderKt`, `PolymorphicKt`, `JsonElementMarker`, `UnknownFieldException` | not traced to a model field (`unverified`) | `unverified`; likely payload shapes (unknown keys, objects decoded via tree) the fixtures lack |
| `phosphoricons/fill/EyeKt`, `FillGroup`, `__FillKt` | watched badge `core/ui/uikit/component/moviesList/VideoItemUIState.kt:373`; checked watched button `ui/feature/details/component/DetailsScreenContent.kt:539`, `ui/feature/player/component/PlayerButtonRow.kt:145` | no watched item in any fixture |
| `phosphoricons/fill/BookmarksimpleKt` | checked watch-later button `DetailsScreenContent.kt:519` | `empty_bookmark_folders.json`, no `in_watchlist: true` |
| `phosphoricons/duotone/CalendarblankKt`, `VideocameraKt` | schedule button when `imdb` is set (`ui/feature/details/model/DetailsScreenUIMapper.kt:185-193`), trailer button when `trailer` is set (`:174-182`) | excluded on purpose: `imdb` drives external lookups (`DetailsVM.kt:303`, `domain/interactor/player/SkipSegmentInteractor.kt:15`), `trailer` starts a preview (`DetailsVM.kt:129`) |

### Guards

`app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt`: `baselineProfileCoversImageTlsAndHlsPaths` requires `coil3/network/`, `coil3/decode/`, `androidx/media3/exoplayer/hls/`, `okhttp3/internal/tls/`; nothing requires `okhttp3/internal/http1/`, `http2/`, serialization primitives or filled icons. `hasRuleFor` is a substring test, so a guard is one list entry.

Unit tests already pin both protocols in fakes (`app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClient*Test.kt` build responses with `Protocol.HTTP_1_1`; `data/api/network/DnsOverHttpsFactoryTest.kt:82` with `Protocol.HTTP_2`). They are test doubles, not evidence of what the servers send.

## 4. Related work

| Item | Relevance |
|---|---|
| hermetic-baseline-generation (shipped, b27d8e40) | Built the mock (design D3, option B of its `research.md`); qa round 2 AC-5 recorded the drops; triage-2 #5 deferred them here. Accepted minors #1-#4 (late-check cascade, DoH resolver assertion, hardcoded port 18765 in `BaselineInstrumentationEnvironmentTest`, truncated DoH question) touch the same files |
| hermetic-baseline-profiles (umbrella) | AC-4..AC-9 parent of the above |
| baseline-generation-hardening | F5 (`railShows` searches the whole screen) stays open in the generator |
| cache-shared-http-stack, http-stack-consolidation, coil-shared-http | Produced the single `HttpStack` client whose defaults decide the protocol |
| Evidence | `docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-r2-device.md` (AC-4/AC-5 counts, "the mock answers over HTTP/2") |

Duplicate: none.

## 5. External findings

| Finding | Source |
|---|---|
| `MockWebServer.protocols` defaults to `[HTTP_2, HTTP_1_1]` ("protocols supported by ALPN on incoming HTTPS connections in order of preference"); must contain `HTTP_1_1` (or be `H2_PRIOR_KNOWLEDGE` alone) | https://raw.githubusercontent.com/square/okhttp/parent-5.3.0/mockwebserver/src/main/kotlin/mockwebserver3/MockWebServer.kt (5.3.0 source; project uses 5.5.1, `gradle/libs.versions.toml:14`) |
| `protocolNegotiationEnabled` (default true); false disables ALPN and restricts connections to HTTP/1.1; with negotiation on and no ALPN result the server falls back to HTTP/1.1 | same |
| Setters `setProtocols`, `setProtocolNegotiationEnabled`, `useHttps(SSLSocketFactory)`, `start(InetAddress, int)` and `MockResponse.Builder.chunkedBody(Buffer|String, int)`, `headers`, `throttleBody` exist in 5.5.1 | `javap` on `~/.gradle-agents/.../mockwebserver3-5.5.1.jar` |
| `OkHttpClient` `DEFAULT_PROTOCOLS = [HTTP_2, HTTP_1_1]`; ALPN negotiates when several protocols are listed; negotiation only for HTTPS | https://raw.githubusercontent.com/square/okhttp/parent-5.3.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt |

## 6. Options

All options add guards to `BaselineProfileContentTest` for what they restore and regenerate on the emulator per the owner's device rule.

| Option | What | Trade-offs | Size |
|---|---|---|---|
| A (recommended) | Two MockWebServer instances in `BaselineMockBackend`: an HTTP/1.1 server (`protocols = listOf(Protocol.HTTP_1_1)`) for `api.baseline.test` and `127.0.0.1` (API, images, stream) and the existing h2 server on a second port for `dns.baseline.test` and the `/github` route. Second port through `puber.baselineMockPort` + 1 (or a new property) into `BuildConfig` and the runner args; `allowedOrigins()`, DoH URL and `BaselineProfileApp` GitHub origin follow. JSON routes switch to `chunkedBody` with a fixed-length minority, and responses gain `Date`/`Cache-Control` headers. Fixtures gain boolean/float/double/nullable fields, a watched item and `in_watchlist: true`. Guards: `okhttp3/internal/http1/Http1ExchangeCodec$ChunkedSource`, `okhttp3/internal/http2/Http2Connection`, `kotlinx/serialization/internal/BooleanSerializer`, `FloatSerializer`, `DoubleSerializer`, `phosphoricons/fill/EyeKt`, `fill/BookmarksimpleKt` | Restores both codecs with the split the base profile implies; the per-origin split itself is `unverified` (Q1). Touches only `:baselineprofile`, `app/src/instrumentation`, build scripts and the content test; no `app/src/main` change. Two ports to reserve on the emulator; mock self-test and `BaselineInstrumentationEnvironmentTest` (hardcoded 18765) need updating | M |
| B | One server forced to HTTP/1.1 (`protocolNegotiationEnabled = false`), plus the same headers, chunked bodies, fixtures and guards except `http2` | Smallest change (one line for protocol); `http2` rules (291 now) go cold, trading one loss for another on prod paths that DoH and GitHub take | S |
| C | Fixtures, headers and guards only; keep h2 everywhere | Restores serialization and icon rules; `http1` stays at 0, which is what the run was opened for | S |

## 7. Risks

| Risk | Mitigation |
|---|---|
| Real origin protocols differ from the assumed split | Q1; the guard asserts both codecs, which holds under either split as long as the mock serves both |
| Second loopback port collides or is unreachable from the app process on the emulator | Derive it from the existing property; extend `BaselineMockSelfTest` to hit both servers |
| OkHttp coalesces `api.baseline.test` and `dns.baseline.test` onto one h2 connection today (same IP, port and certificate); splitting ports changes connection reuse | Expected and closer to prod (different real hosts); check `unknown=[]` and journal per journey as now |
| Richer fixtures add fields that trigger egress (`imdb`, `trailer`, external URLs) | Keep `imdb`/`trailer` out (Q2); blocker and journal already fail the run on any non-loopback request |
| Guards on library class names break on a dependency bump that renames internals | Guard on stable top-level classes (`Http1ExchangeCodec`, `Http2Connection`, `BooleanSerializer`), not lambdas |
| Profile drift remains in packages not guarded | Keep the per-package comparison against the last profile as a qa evidence step (as hermetic-baseline-generation AC-5) |

## 8. Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Accept the inferred split (HTTP/1.1 for API, images and stream; HTTP/2 for DoH and GitHub) without confirming per origin? Confirming needs a debug log of `Response.protocol` during one ordinary session on the box | Accept; the base profile shows both codecs in use and option A serves both. No API probe |
| Q2 | Should fixtures include `imdb` or `trailer` to warm the schedule and trailer icons (CalendarBlank, VideoCamera)? | No: both start external lookups or a preview; accept those few icon rules as cold |
| Q3 | Fold in the accepted hermetic-baseline-generation minors that touch the same files (#3 hardcoded port 18765, #1 late-check cascade)? | Yes for #3 (the second port changes that test anyway); #1 only if the implementer touches the late check |

## 9. Sources

- Files: `app/src/main/java/com/kino/puber/data/api/network/{HttpStack,ImageHttpClient,NetworkStats}.kt`, `data/di/modules.kt`, `ui/feature/player/vm/PlaybackController.kt`, `data/api/models/Models.kt`, `ui/feature/details/model/DetailsScreenUIMapper.kt`, `ui/feature/details/component/DetailsScreenContent.kt`, `ui/feature/details/vm/DetailsVM.kt`, `ui/feature/player/component/PlayerButtonRow.kt`, `core/ui/uikit/component/moviesList/VideoItemUIState.kt`, `domain/interactor/player/SkipSegmentInteractor.kt`; `app/src/instrumentation/java/com/kino/puber/profile/{BaselineInstrumentationEnvironment,BaselineProfileApp}.kt`, `app/src/instrumentation/res/xml/network_security_config.xml`; `baselineprofile/build.gradle.kts`, `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt`, `baselineprofile/src/main/assets/baseline/**`; `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt`; `gradle/libs.versions.toml`; `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` at 5cd3e37e and HEAD
- Commits: 5cd3e37e, b27d8e40, b17f7ba8, 6cd733f4, 201cb1b0, a0121189
- Runs: hermetic-baseline-generation (`research.md`, `design-architect.md`, `qa-report.md`, `triage-2.md`, `state.md`), hermetic-baseline-profiles, baseline-generation-hardening, cache-shared-http-stack, http-stack-consolidation, coil-shared-http
- Evidence: `docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-r2-device.md`
- URLs (fetched 2026-11-26): https://raw.githubusercontent.com/square/okhttp/parent-5.3.0/mockwebserver/src/main/kotlin/mockwebserver3/MockWebServer.kt, https://raw.githubusercontent.com/square/okhttp/parent-5.3.0/okhttp/src/commonJvmAndroid/kotlin/okhttp3/OkHttpClient.kt
