# Hermetic baseline profile generation - plan

**Spec:** `docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md`
**Goal:** Generate the committed baseline profiles from an `instrumentation` flavor that talks only to an HTTPS loopback mock with synthetic auth, and remove the paired-account route.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug` (config `check.full` plus the spec D18 additions; the `.flow/config.yaml` edit is the conductor's, at ship).
- The fast gate compiles neither the flavor nor `:baselineprofile`. Every task also runs its **Flavor compile** line; after Task 4 that line is `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileInstrumentationNonMinifiedReleaseKotlin :baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin`.
- One task = green fast gate + green tests of the touched code + one commit, message per `commit_style` (one imperative English sentence, no prefixes), never a `Co-Authored-By` line.
- Work in `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation`; never `./gradlew` there. `make` targets that run Gradle get `GRADLE=./tools/agentw` (the script does this).
- No main-source change: everything new lives in `app/src/instrumentation/`, `app/src/testInstrumentation/`, `baselineprofile/`, `tools/`, build files and docs.
- Never send a request to the live KinoPub API, GitHub, TMDB or any non-loopback host from a test, script or device run. Fixtures are written by hand (shapes from `app/src/main/java/com/kino/puber/data/api/models/Models.kt`), never captured.
- Re-port, not cherry-pick: branch files are read with `git show <sha>:<path>` (shas 0b6253a9, c17847cc, 5709c601) and changed as each task says.
- Out of scope: Makefile/script hardening F2-F5, `railShows`, `StartupBenchmarks`, PR workflows (spec "Out of scope").
- Device: only Task 8 touches a device (emulator `host.docker.internal:5555`, lease, loopback mock only). The box and every other manual check (M1-M7 evidence) belong to qa.

## Shared definitions

| Name | Value |
|---|---|
| Mock port | Gradle property `puber.baselineMockPort=18765` in `gradle.properties`; app gets `BuildConfig.BASELINE_MOCK_PORT`, generator gets runner arg `baselineMockPort` |
| Mock origin | `https://127.0.0.1:18765` (fixtures write `{{MOCK}}`, replaced at load) |
| Target package | `com.kino.puber.instrumentation` |
| Control receiver | `com.kino.puber.profile.BaselineProfileControlReceiver`, process `:baseline_profile_control`; actions `com.kino.puber.profile.CLEAR` (caches and DBs, journal untouched), `com.kino.puber.profile.RESET_JOURNAL` (journal only, once per test, spec D12), `com.kino.puber.profile.VERIFY` (result `-1` empty, `2` with the lines as data) |
| Required routes (D14) | Startup: `GET /v1/items/hot`, `GET /v1/watching/serials`. Browse: Startup + `/v1/items/\d+`, `/v1/items/similar`, one `/media/(poster|wide)\.jpg`. Rail: Startup + `/v1/history`, `/v1/genres`, `/v1/items`. Playback: `/v1/items/\d+`, `/media/stream\.m3u8`, one `/media/seg\d+\.ts`, `/v1/watching/marktime` |

---

## Tasks

### Task 1: Instrumentation flavor and loopback TLS material

**Covers:** AC-6 (placement), AC-7 (variant exists)
**Files:**
- Modify: `app/build.gradle.kts` - add flavor `instrumentation` after `prod` (`productFlavors` at `:223-236`, dimension `buildType` at `:119`): `applicationIdSuffix = ".instrumentation"`, `resValue("string", "app_name", "Puber(Instrumentation)")`, `buildConfigField` `CLIENT_SECRET`/`TMDB_READ_ACCESS_TOKEN`/`API_DOMAIN_OVERRIDE` = `"\"\""` (overrides `defaultConfig` `:58-61`), `buildConfigField("int", "BASELINE_MOCK_PORT", providers.gradleProperty("puber.baselineMockPort").get())`; reference `git show 0b6253a9 -- app/build.gradle.kts`
- Modify: `gradle.properties` - add `puber.baselineMockPort=18765` (file ends without newline at `:4`)
- Create: `tools/make-baseline-mock-cert.sh` - openssl 3 recipe (openssl is in the box): in a `mktemp -d` dir, EC P-256 root (`req -x509 -days 10958 -subj /CN=Puber baseline mock test-only root -addext basicConstraints=critical,CA:TRUE -addext keyUsage=critical,keyCertSign,cRLSign`), EC P-256 leaf key (`genpkey`, PKCS#8), leaf CSR, leaf signed by the root for 9131 days (25 years) with `subjectAltName=IP:127.0.0.1`, `extendedKeyUsage=serverAuth`, `basicConstraints=CA:FALSE`; writes the root cert and leaf cert + leaf key; deletes the root key and the temp dir (trap EXIT); prints both `notAfter` dates. Header comment: test-only, loopback-only, root key never kept
- Create (script output): `app/src/instrumentation/res/raw/baseline_mock_ca.pem` (root cert only), `baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem` (leaf cert PEM then `-----BEGIN PRIVATE KEY-----` block, the format `HeldCertificate.decode` reads)

**Consumes:** none
**Produces:** variant set `instrumentation{Debug,Release,Deploy,NonMinifiedRelease,BenchmarkRelease}`; `BuildConfig.BASELINE_MOCK_PORT` in that flavor only; the two PEM files

- [x] Add the flavor and property; run `tools/make-baseline-mock-cert.sh` once and keep its output
- [x] `openssl verify -CAfile app/src/instrumentation/res/raw/baseline_mock_ca.pem baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem` prints OK; `grep -c 'PRIVATE KEY' app/src/instrumentation/res/raw/baseline_mock_ca.pem` is 0
- [x] Fast gate; flavor compile `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileInstrumentationNonMinifiedReleaseKotlin`; commit ("Add the instrumentation flavor and a loopback-only test certificate")

**Done when:** both compiles exit 0 and the `openssl verify` line prints OK.

---

### Task 2: Instrumentation runtime: synthetic auth, pinned endpoint, egress blocker, control receiver

**Covers:** AC-3 (blocker half), AC-6, AC-7
**Files:**
- Create: `app/src/instrumentation/AndroidManifest.xml` - `<application android:name=".profile.BaselineProfileApp" tools:replace="android:name">` over `app/src/main/AndroidManifest.xml:22`; receiver per Shared definitions, `android:exported="true"`, `android:permission="android.permission.DUMP"` (branch: `git show 5709c601:app/src/instrumentation/AndroidManifest.xml`)
- Create: `app/src/instrumentation/res/xml/network_security_config.xml` - replaces the main one (`app/src/main/res/xml/network_security_config.xml`): only `<base-config cleartextTrafficPermitted="false"><trust-anchors><certificates src="@raw/baseline_mock_ca"/></trust-anchors></base-config>`; no `domain-config`, no `system` or `user` anchors
- Create: `app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp.kt` - `internal class BaselineProfileApp : PuberApp()` (`PuberApp.kt:69`). `shouldInitializeRuntime()` (`:93`) false when the process name ends with `:baseline_profile_control`; `configureRuntime()` (`:96`) calls `BaselineInstrumentationEnvironment.configure(this)`; `runtimeModules()` (`:99`, appended at `:112`) binds `ICryptoPreferenceRepository` to `SyntheticAuthPreferences` and `IAppUpdateInteractor` to `NoOpAppUpdateInteractor`. Start from `git show 5709c601:app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp.kt` and add the three session-end methods of `ICryptoPreferenceRepository.kt:17-19` (save/clear no-op, `getSessionEndReason() = null`). No-op update interactor matches `domain/interactor/update/IAppUpdateInteractor.kt:8-28` (bound in main at `domain/di/modules.kt:38`; `AppUpdateDownload.Error.StorageUnavailable` `data/repository/AppUpdateDownload.kt:96`, `InstallLaunchResult.PermissionRequired` `data/repository/AppUpdateInstaller.kt:62`). Koin 4.2.2 (`gradle/libs.versions.toml:22`) lets the later module override `data/di/modules.kt:102`
- Create: `.../profile/BaselineInstrumentationEnvironment.kt` - `const MOCK_HOST = "127.0.0.1"`, `fun mockOrigin() = "https://127.0.0.1:${BuildConfig.BASELINE_MOCK_PORT}"`; `configure(context)` pins `KinoPubConfig.setPinnedEndpoint` (`data/api/config/KinoPubConfig.kt:84`) to `ApiEndpointPreset` (`:5-11`) with `domain = "127.0.0.1:<port>"`, `apiHost = "127.0.0.1"`, base URLs `<origin>/v1/`, `<origin>/oauth2/`, `<origin>/`, then `BaselineNetworkBlocker.install(File(context.noBackupFilesDir, "baseline_network_journal"), mockOrigin())`; `clearCaches(context)` deletes every name in `context.databaseList()` with `deleteDatabase` and the contents of `cacheDir` and `externalCacheDir` (no reference to `PuberDatabase.NAME`, private at `data/db/PuberDatabase.kt:34`)
- Create: `.../profile/BaselineNetworkBlocker.kt` - re-port of `git show 5709c601:app/src/instrumentation/java/com/kino/puber/profile/BaselineNetworkBlocker.kt` with these changes: `install(journalDir: File, allowedOrigin: String)` (no `Context`); `internal fun selector(allowedOrigin: String, journal: BaselineNetworkJournal): ProxySelector` so tests never call `ProxySelector.setDefault`; `internal class BaselineNetworkJournal(directory: File)` with `record(uri)`, `snapshot(): List<String>`, `clear()`. Allow rule compares scheme, host and port (`origin(uri)`), deny returns `127.0.0.1:9` HTTP proxy (unresolved) and journals; `connectFailed` journals any URI whose origin is not allowed
- Create: `.../profile/BaselineProfileControlReceiver.kt` - re-port of the 5709c601 receiver: `CLEAR` calls `clearCaches` only (journal untouched, D12), `RESET_JOURNAL` clears the journal, `VERIFY` as in Shared definitions, unknown action result `3`; journal opened on the same directory as `configure`
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineNetworkBlockerTest.kt` (JUnit 5, `@TempDir` journal dir, real `URI`s, origin `https://127.0.0.1:18765`)

**Consumes:** Task 1 flavor and `@raw/baseline_mock_ca`
**Produces:** receiver actions and results (Shared definitions); `BaselineNetworkBlocker.selector`, `BaselineNetworkJournal(File)`

- [x] Write the six tests first (red: classes absent): `select_returnsDeadProxyAndJournals_whenHostIsNotLoopback`, `select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers`, `select_returnsNoProxy_whenOriginIsMock`, `connectFailed_journalsUri`, `journal_keepsLines_whenNewBlockerOpensSameDir`, `clear_emptiesJournal`; record the red run
- [x] Add the manifest, NSC and four classes
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug` (record the nearest real task name if one differs)
- [x] Fast gate; flavor compile `:app:compileInstrumentationNonMinifiedReleaseKotlin`; commit ("Run the instrumentation app on a pinned loopback endpoint with synthetic auth and an egress blocker")

**Done when:** the six tests pass and the four commands above exit 0.

---

### Task 3: Mock fixtures and their content test

**Covers:** AC-2 (fixture half)
**Files:**
- Create: `baselineprofile/src/main/assets/baseline/*.json` - start from `git ls-tree 5709c601 baselineprofile/src/main/assets/baseline/` (12 files) and change: `details.json` becomes `item.json` with `"id":{{ID}}` echoed by the mock; every movie in `items.json`, `similar.json`, `bookmark_items.json` and `item.json` gets `posters` (`small`/`medium`/`big` = `{{MOCK}}/media/poster.jpg`, `wide` = `{{MOCK}}/media/wide.jpg`; `Posters` at `Models.kt:215`); `item.json` gets `videos` (`Item.videos` `Models.kt:84`) with one `Video` (`:274`) carrying `id`, `number: 1`, `duration: 24`, `files[0].url.hls4 = {{MOCK}}/media/stream.m3u8` (`VideoFile` `:579`, `VideoUrl` `:590`; read order `domain/interactor/player/PlayerInteractor.kt:233-240`). No `imdb`, `trailer`, `subtitles`, `seasons` key anywhere; no URL that does not start with `{{MOCK}}`
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineFixtureContentTest.kt` - reads `../baselineprofile/src/main/assets/baseline` or `baselineprofile/src/main/assets/baseline` (first that exists, as `BaselineProfileContentTest.profileLines` does at `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt:12-17`); parses with `kotlinx.serialization.json.Json.parseToJsonElement` after replacing `{{ID}}` with `1` and `{{MOCK}}` with the origin

**Consumes:** none
**Produces:** fixture names and placeholders `{{MOCK}}`, `{{ID}}` used by Task 4; media file names `poster.jpg`, `wide.jpg`, `stream.m3u8`, `seg<N>.ts` produced by Task 6

- [x] Write `fixtures_haveNoUrlOutsideMockOrigin`, `fixtures_haveNoImdbTrailerSubtitlesOrSeasonsKeys`, `movieFixtures_carryPostersAndHlsStream` (red: no fixtures)
- [x] Add the fixtures
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`
- [x] Fast gate; commit ("Add loopback mock fixtures with posters and an HLS stream reference")

**Done when:** the three tests pass.

---

### Task 4: Wire generation to the instrumentation variant and add the mock backend

**Covers:** AC-1 (dry-run), AC-3 (mock half)
**Files:**
- Modify: `app/build.gradle.kts:438-444` - delete the `configurations.configureEach` block and its comment; inside `baselineProfile {}` (`:446-453`) add `variants { create("instrumentationRelease") { from(project(":baselineprofile")) } }`. Fallback if `--dry-run` shows no `:baselineprofile` dependency: keep the `configurations.configureEach` pattern with name `instrumentationReleaseBaselineProfile` (record under Deviations)
- Modify: `baselineprofile/build.gradle.kts:18-28` - only flavor `instrumentation` with runner args `targetAppId = "com.kino.puber.instrumentation"` and `baselineMockPort = providers.gradleProperty("puber.baselineMockPort").get()` (branch: `git show c17847cc -- baselineprofile/build.gradle.kts`); `:41-46` add `implementation(libs.mockwebserver3)`, `implementation(libs.okhttp.tls)`
- Modify: `gradle/libs.versions.toml` after `:88` (`okhttp-doh`) - `mockwebserver3` and `okhttp-tls`, both `group = "com.squareup.okhttp3"`, `version.ref = "okhttp"` (`:14`)
- Create: `baselineprofile/src/main/AndroidManifest.xml` - `<uses-permission android:name="android.permission.INTERNET"/>` (server socket in the test process)
- Create: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt` - re-port of `git show 5709c601:baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt` with: constructor `(port: Int, fixtures: BaselineFixtures, leaf: HeldCertificate)` and `server.useHttps(HandshakeCertificates.Builder().heldCertificate(leaf).build().sslSocketFactory())`; `port = 0` allowed (self-test); `BaselineScenario { Startup, BrowseAndDetails, Rail, Playback }`; `resetTest()` clears matched and unknown, `startIteration(scenario)` clears matched only (D12); paths matched on `request.url.encodedPath` only (query ignored, no `query` map); unknown route = 404 + recorded; routes: the branch table without `/__baseline/ready`, plus `/v1/items/\d+` serving `item.json` with `{{ID}}` replaced by the path id, `GET /v1/watching/marktime` and `GET /v1/watching/toggle` -> `ok.json`, `/media/poster.jpg`, `/media/wide.jpg` (`image/jpeg`), `/media/stream.m3u8` (`application/vnd.apple.mpegurl`), `/media/seg\d+\.ts` (`video/mp2t`), binary bodies from `assets/baseline/media/` via okio `Buffer`; required flags per Shared definitions; `BaselineFixtures.from(context, origin)` replaces `{{MOCK}}` and calls `validate()`, which fails on any `https?://` not starting with the origin and on keys `imdb`, `trailer`, `subtitles`, `seasons`; `HeldCertificate.decode` of `assets/baseline/tls/leaf-test-only.pem`. Also `object JourneyCheck { fun check(verifyBroadcastOutput: String, verification: BaselineVerification) }`: throws `IllegalStateException` unless the output contains `result=-1` and `verification.isSuccessful`; message lists matched, missing, unknown and the journal data
- Test: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockSelfTest.kt` - plain JUnit 4 with `@RunWith(AndroidJUnit4::class)`, no benchmark rule; real backend on port 0 with the committed leaf; HTTPS client `OkHttpClient` with `HandshakeCertificates.Builder().addInsecureHost("127.0.0.1")`; planted broadcast outputs as strings (for example `Broadcast completed: result=2, data="https://example.org:443/"`)

**Consumes:** Task 1 leaf PEM and port property, Task 3 fixtures
**Produces:** `BaselineMockBackend`, `BaselineScenario`, `BaselineVerification`, `BaselineFixtures`, `JourneyCheck` for Task 5

- [x] Write `unknownRoute_returns404AndIsListedInVerify`, `journeyCheck_fails_whenUnknownRouteServed`, `journeyCheck_fails_whenEgressJournalNotEmpty`, `journeyCheck_fails_whenRequiredRouteMissing`, `journeyCheck_passes_whenRequiredRoutesHitAndNothingElse` (red: classes absent; they run on a device inside Task 8)
- [x] Gradle and manifest changes, backend, `JourneyCheck`
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:generateBaselineProfile --dry-run` lists only `InstrumentationNonMinifiedRelease` generation and `:baselineprofile` tasks, none with `Dev` or `Prod`
- [x] Fast gate; flavor compile (Global constraints line); commit ("Generate baseline profiles from the instrumentation variant against an HTTPS loopback mock")

**Done when:** the dry-run output has no `Dev`/`Prod` generation task and both compiles exit 0. `BaselineProfileGenerator.kt` still compiles unchanged at this commit.

---

### Task 5: Per-iteration reset and journey check in the generator

**Covers:** AC-2, AC-3 (journey fails), AC-4 (filter)
**Files:**
- Create: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileTestControl.kt` - from `git show 5709c601:baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileTestControl.kt`, keeping `broadcast` (`am broadcast --receiver-foreground --include-stopped-packages -n <pkg>/<receiver> -a <action>`) and the quiescence loop; drop `forceStopTarget` from verification (D13). API: `createBackend(arguments)` (port arg, fixtures with origin `https://127.0.0.1:<port>`, leaf); `resetJournal(pkg)` (`RESET_JOURNAL`, expects `result=-1`); `clearTarget(pkg)` (`CLEAR`, expects `result=-1`); `checkIteration(pkg, backend)`: poll up to 15 s until `missingRequiredRoutes` is empty, wait 500 ms with no new mock request, `VERIFY` broadcast, `backend.verify()`, `Log.i("PuberGen", ...)` one line with scenario, matched, missing, unknown, journal; then `JourneyCheck.check`
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt` - delete `requireInPlaceReset` (`:28-34`), the playback KDoc (`:144-148`), `requirePairedApp` and its KDoc (`:179-192`) and its four calls (`:47`, `:89`, `:141`, `:176`), the `android.os.Build` import. The companion (`private companion object` at `:202`) becomes public (drop `private`; its existing private members stay `private`) so JUnit 4 sees public static lifecycle methods; its `@JvmStatic @BeforeClass` (public) creates and starts the backend, `@AfterClass` closes it; `@Before` calls `backend.resetTest()` and `resetJournal`. Each of the four tests keeps its name and journey steps (`:36-177`) and goes through one helper: `baselineProfileRule.collect(packageName, maxIterations, includeInStartupProfile, filterPredicate = { "com/kino/puber/profile/" !in it }) { killProcess(); clearTarget(packageName); backend.startIteration(scenario); <journey>; checkIteration(packageName, backend) }` with scenarios Startup, BrowseAndDetails, Rail (`maxIterations = RAIL_MAX_ITERATIONS`, `:216`), Playback. `railShows` (`:194-200`) unchanged

**Consumes:** Task 4 backend API and `JourneyCheck`; Task 2 receiver actions
**Produces:** a generator whose every iteration fails on a non-empty journal, an unknown route or a missing required route

- [x] Write `BaselineProfileTestControl.kt`, rewrite the generator
- [x] `grep -nE 'requirePairedApp|profile\.auth|SDK_INT|pair' baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt` prints nothing
- [x] Fast gate; flavor compile; commit ("Reset caches and check mock routes and egress on every baseline profile iteration")

**Done when:** the grep prints nothing and both compiles exit 0.

---

### Task 6: Synthetic HLS stream and poster images

**Covers:** AC-2 (playlist and segment routes), AC-5 (Coil, HLS paths)
**Files:**
- Create: `tools/make-baseline-mock-media.sh` - takes an output dir; needs ffmpeg with libx264 (not in the box). Commands: `ffmpeg -f lavfi -i testsrc2=size=320x180:rate=25 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 24 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -b:v 48k -g 100 -keyint_min 100 -sc_threshold 0 -c:a aac -b:a 16k -ac 1 -f hls -hls_time 4 -hls_playlist_type vod -hls_segment_filename <out>/seg%d.ts <out>/stream.m3u8`; `ffmpeg -f lavfi -i testsrc2=size=240x360 -frames:v 1 -q:v 10 <out>/poster.jpg`; same with `640x360` for `wide.jpg`; prints `du -cb`
- Create (script output): `baselineprofile/src/main/assets/baseline/media/{stream.m3u8,seg0.ts..seg5.ts,poster.jpg,wide.jpg}`

**Consumes:** Task 3 media names
**Produces:** the media the mock serves in Task 8

- [x] Run the recipe on the Mac host (owner Q2): `ssh mac-emu 'command -v ffmpeg'`; if found, `ssh mac-emu 'bash -s -- /tmp/puber-baseline-media' < tools/make-baseline-mock-media.sh`, then `scp 'mac-emu:/tmp/puber-baseline-media/*' baselineprofile/src/main/assets/baseline/media/`. If ffmpeg is missing or the host refuses the command, stop and record under **Blocked** (the conductor asks the owner to run the script); do not install software on the host
- [x] Playlist has `#EXT-X-ENDLIST` and six relative `seg<N>.ts` entries; stream files total under 300 KB
- [x] Fast gate; flavor compile; commit ("Add a 24-second synthetic HLS stream and poster images for the baseline mock")

**Done when:** `du -cb baselineprofile/src/main/assets/baseline/media/stream.m3u8 baselineprofile/src/main/assets/baseline/media/seg*.ts` totals under 307200 and `grep -c '^seg[0-9]*\.ts$' .../stream.m3u8` is 6.

---

### Task 7: Remove the paired-account route and rewrite the profile docs

**Covers:** AC-8
**Files:**
- Delete: `app/src/nonMinifiedRelease/` (`AuthEventLog.kt`, `BaselineProfileAuthProvider.kt`), `app/src/devNonMinifiedRelease/AndroidManifest.xml`, `app/src/prodNonMinifiedRelease/AndroidManifest.xml`
- Modify: `tools/generate-baseline-profile.sh` - delete `:12-17` (secret refusal), `:20-22` (`TARGET_PACKAGE`, `MAIN_ACTIVITY`, `AUTH_PROVIDER_URI`), `:53-86` (`is_paired`, `ensure_paired`), `:102-103` comment, `:117-121` (pairing warning), `:130` (`ensure_paired` call); logcat filter `:109` becomes `-s PuberGen:V Benchmark:V AndroidRuntime:E`; `:111` says "Generator events go to"; the API 34 refusal (`:47-50`) keeps its wording (D17)
- Modify: `docs/release.md:99-133` - rewrite "Refresh Baseline Profiles": when to refresh (keep `:101-103`); one command `DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh` with no secrets, no `.env`, no account, no pairing; emulator API 34+ and the private adb server (keep `:113-118`); the `instrumentation` flavor, loopback HTTPS mock, synthetic auth, egress blocker and the per-iteration check (`PuberGen` lines) that fails a run; `H` handling (keep `:123-128`); the two recipes `tools/make-baseline-mock-cert.sh`, `tools/make-baseline-mock-media.sh` and the leaf `notAfter` date from Task 1; one line that `StartupBenchmarks` now targets `com.kino.puber.instrumentation` without a mock and is owned by `startup-benchmark-entry`. `Makefile` unchanged (`:139-179` names no pairing)

**Consumes:** Task 5 generator without `requirePairedApp`
**Produces:** nothing later tasks call

- [x] Delete and edit; `bash -n tools/generate-baseline-profile.sh`
- [x] `grep -nE 'CLIENT_SECRET|pair|PuberAuth|profile\.auth' tools/generate-baseline-profile.sh baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt`, `sed -n '139,179p' Makefile | grep -nE 'CLIENT_SECRET|pair|PuberAuth|profile\.auth'` and the same grep over the rewritten "Refresh Baseline Profiles" section print nothing
- [x] Fast gate; `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleDevNonMinifiedRelease` builds without the removed manifests; flavor compile; commit ("Remove the paired-account baseline profile route and its auth provider")

**Done when:** the greps print nothing, `ls app/src/nonMinifiedRelease app/src/devNonMinifiedRelease/AndroidManifest.xml app/src/prodNonMinifiedRelease/AndroidManifest.xml` fails for all three, and the builds exit 0.

---

### Task 8: Ban instrumentation rules and regenerate the profiles on the emulator

**Covers:** AC-1, AC-2, AC-3 (device run of the self-test), AC-4, AC-5 (guard), AC-9 (qa M6, after Task 8)
This task's device run is the implementer's because its output is a committed source file the red test depends on. qa reads this run's log for M1/M2 and owns M3-M7 and the box (M6).
**Files:**
- Modify: `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt` - add `profilesHaveNoInstrumentationOrGeneratorClasses` (both `baseline` and `startup`, prefixes `com/kino/puber/profile/` and `com/kino/puber/baselineprofile/`, via `hasRuleFor` at `:19`) and `baselineProfileCoversImageTlsAndHlsPaths` (`coil3/network/`, `coil3/decode/`, `androidx/media3/exoplayer/hls/`, `okhttp3/internal/tls/` in `baseline`); existing four tests (`:21-58`) unchanged
- Modify (generated): `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt`

**Consumes:** Tasks 1-7
**Produces:** regenerated profiles; the logcat file path for qa

- [x] Write the two tests; `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.BaselineProfileContentTest'` fails on `profilesHaveNoInstrumentationOrGeneratorClasses` (20 `com/kino/puber/profile/` rules per file at base); record the red run
- [x] Lease the emulator (`tools/emulator-resource-lock.sh acquire host.docker.internal:5555`, usage at `:17-28`), `ssh mac-emu start` (prints `ready`), `adb connect host.docker.internal:5555`
- [x] With no `.env` and `PUBER_CLIENT_SECRET` unset: `AGENT_GRADLE_SERIAL=1 DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh` exits 0. On a failure caused by a missing route, add the route to the table in `BaselineMockBackend.kt` (never loosen `JourneyCheck` or the blocker) and rerun; list each added route under Deviations
- [x] Release the lease; `ssh mac-emu stop` if `tools/emulator-resource-lock.sh status` shows no other lease
- [x] `grep -c '^[^H].*->' app/src/main/generated/baselineProfiles/baseline-prof.txt` is 0; `grep -c 'com/kino/puber/profile/' app/src/main/generated/baselineProfiles/*.txt` is 0 for both
- [x] Full gate (Global constraints); flavor compile; commit test and profiles together ("Regenerate baseline profiles against the loopback mock and ban instrumentation classes from them")
- [x] Record in the Implementation record: the logcat file path printed by the script, the `BaselineMockSelfTest` result location under `baselineprofile/build/outputs/androidTest-results/`

**Done when:** the generation run exits 0, the content test class passes with the regenerated files, and the full gate exits 0.

---

## Round 3: fix tasks (triage-1 #1-#6)

Inputs: `triage-1.md` #1-#6 (run folder), spec "Round 2 delta" D20-D27, spec Test design rows marked `R3`. Tasks 1-8 stand as recorded; Tasks 9-17 run on top of 43d68e7e.

### Round 3 constraints

- Q5 (owner): Tasks 9-11 change main source (D21, D22, D24); every default stays equal to today and is pinned by the JVM tests named there. The "No main-source change" constraint above holds for Tasks 12-17.
- Gates, commit rule, worktree, device and API rules: Global constraints above, unchanged. Fast gate `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug`; flavor compile `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileInstrumentationNonMinifiedReleaseKotlin :baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin`. Every task runs all three before its commit except where it says otherwise.
- Only Task 16 touches a device (emulator `host.docker.internal:5555`, loopback mock only).

### Round 3 shared definitions (supersede "Required routes (D14)" above)

| Name | Value |
|---|---|
| API host, DNS host | `api.baseline.test`, `dns.baseline.test` (RFC 6761 `.test`) |
| Allowed origins (D23) | `https://127.0.0.1:18765`, `https://api.baseline.test:18765`, `https://dns.baseline.test:18765` |
| Pinned endpoint (D22) | `ApiEndpointPreset(domain = "api.baseline.test:18765", apiHost = "api.baseline.test", mainBaseUrl = "https://api.baseline.test:18765/v1/", oauthBaseUrl = ".../oauth2/", extraBaseUrl = "https://api.baseline.test:18765/")` |
| Pinned resolver (D22) | `DohResolver("https://dns.baseline.test:18765/dns-query", listOf("127.0.0.1"))` |
| GitHub base (D24) | main default `GITHUB_API_BASE_URL = "https://api.github.com"`; instrumentation `https://127.0.0.1:18765/github` |
| Fixture URLs | unchanged: `{{MOCK}}` = `https://127.0.0.1:18765` |
| Required routes (spec D14 as replaced, plus D26) | Startup: `GET /v1/items/hot`, `GET /v1/watching/serials`, `GET /v1/items/fresh`, `GET /v1/history`, `GET /github/repos/[^/]+/[^/]+/releases/latest`, `POST /dns-query`. BrowseAndDetails: Startup + `/v1/items/\d+`, `/v1/items/similar`, one `/media/(poster|wide)\.jpg`. Rail: Startup + `/v1/items`. Playback: `/v1/items/\d+`, `/media/stream\.m3u8`, one `/media/seg\d+\.ts`, `/v1/watching/marktime`. Every route is registered in every scenario; only the `required` flag differs |
| D27 guard substrings | `com/kino/puber/data/repository/CryptoPreferenceRepository;->decrypt`, `com/kino/puber/data/repository/CryptoPreferenceRepository;->secretKey`, `com/kino/puber/data/api/network/DnsOverHttpsFactory;->create`, `okhttp3/dnsoverhttps/DnsOverHttps;->`, `okhttp3/dnsoverhttps/BootstrapDns;->lookup`, `com/kino/puber/domain/interactor/api/ApiDomainInteractor;->walkForWorkingDomain`, `com/kino/puber/data/api/network/HttpEndpointProbe;->isReachable`, `com/kino/puber/domain/interactor/update/AppUpdateInteractor;->checkForUpdate`, `com/kino/puber/data/repository/AppUpdateRepository;->getAvailableUpdate`, `androidx/tvprovider/media/tv/PreviewProgram`, `com/kino/puber/core/tvhome/AndroidTvHomePublisher;->publish` (package-qualified so `NoOpAppUpdateInteractor` cannot satisfy the update rule; each present at 5cd3e37e in both files, 0 at 7d639a9b) |

### Corrections to the design delta found while planning

| Item | Design text | Plan uses | Source |
|---|---|---|---|
| D25 `history.json` shape | entry with `item`, `video`, `time` | wire shape `HistoryEntryResponse`: `counter`, `first_seen`, `item` {`id`, `title`, `type`, `posters`}, `last_seen`, `media` {`id`, `number`, `snumber`, `duration`}, `time`; `video` is the model field `toModel()` builds | `app/src/main/java/com/kino/puber/data/api/history/HistoryPageResponse.kt:32-43`, `:68-74`, `:85-92` |
| `KinoPubApiClientGitHubReleaseTest` recorder | Ktor `MockEngine` | OkHttp application interceptor on the `OkHttpClient` passed to `KinoPubApiClient`, as `Env.interceptor`/`offHost` in `KinoPubApiClientSessionEndTest.kt:383`, `:391-417`: `KinoPubApiClient` takes an `OkHttpClient` and builds its own Ktor client, so no engine can be injected | `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:107-131` |

---

### Task 9: Pinnable DoH resolver in main source (D22)

**Covers:** AC-10
**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/api/network/DnsOverHttpsFactory.kt:9-27` - add `data class DohResolver(val url: String, val bootstrapHosts: List<String>)` with `companion object { val GOOGLE = DohResolver("https://dns.google/dns-query", listOf("8.8.4.4", "8.8.8.8")) }` (today's values, `:19-23`); `create(client, cache, resolver: DohResolver = DohResolver.GOOGLE)` uses `resolver.url.toHttpUrl()` and `bootstrapDnsHosts(resolver.bootstrapHosts.map(InetAddress::getByName))`; `post(true)`, `includeIPv6(false)` unchanged
- Modify: `app/src/main/java/com/kino/puber/data/api/network/HttpStack.kt:28-51` - parameter `useDoh: Boolean = true` (`:30`) becomes `resolver: DohResolver? = DohResolver.GOOGLE`; `null` takes today's `!useDoh` branch (`:34-39`); otherwise `DnsOverHttpsFactory.create(dohClient, dnsCache, resolver)` (`:45`). KDoc `:9-13` and `:24-27`: "null resolver = system resolver"
- Modify: `app/src/main/java/com/kino/puber/data/api/config/KinoPubConfig.kt` - `@Volatile private var pinnedResolver: DohResolver? = null` next to `pinnedEndpoint` (`:26`); `val DNS_RESOLVER: DohResolver? get() = if (pinnedEndpoint != null) pinnedResolver else DohResolver.GOOGLE` after `IS_PINNED_ENDPOINT` (`:63`); `setPinnedEndpoint(endpoint, resolver: DohResolver? = null)` sets the resolver then the endpoint (`:84-86`); `clearPinnedEndpoint()` (`:88-90`) nulls both; KDoc `:80-83` names the resolver
- Modify: `app/src/main/java/com/kino/puber/data/di/modules.kt:72-75` - `HttpStack.create(if (BuildConfig.DEBUG) NetworkStats::factory else null, resolver = KinoPubConfig.DNS_RESOLVER)`
- Test: `app/src/test/kotlin/com/kino/puber/data/api/network/DnsOverHttpsFactoryTest.kt` - add `googleResolver_isDnsGoogleWithBootstrap8844And8888` (`assertEquals(DohResolver("https://dns.google/dns-query", listOf("8.8.4.4", "8.8.8.8")), DohResolver.GOOGLE)`) and `create_queriesTheGivenResolverUrl` (the fake DoH interceptor `:60-85` also records `request.url`; `create(..., resolver = DohResolver("https://dns.baseline.test:18765/dns-query", listOf("127.0.0.1")))`, one `lookup("media.example")`, recorded URL equals the resolver URL)
- Test: `app/src/test/kotlin/com/kino/puber/data/api/network/HttpStackTest.kt:19-39` - rename `create_usesSystemDns_withoutDoh` to `create_usesSystemDns_whenResolverIsNull` and `create_keepsTheAppEventListenerAndRequestsNoDohOne_withoutDoh` to `..._whenResolverIsNull`, both passing `resolver = null`; `create_resolvesThroughDoh_byDefault` (`:14-17`) unchanged
- Test: `app/src/test/kotlin/com/kino/puber/data/di/ApiModulePinnedEndpointTest.kt` - reuse `startApiModule` (`:58-69`); add `unpinnedEndpoint_buildsTheAppClientWithDoh` (no pin: `OkHttpClient.dns is DnsOverHttps`) and `pinnedEndpointWithResolver_buildsTheAppClientWithDoh` (pin of `:73-81` plus the round-3 resolver); `pinnedEndpoint_buildsTheAppClientWithoutDoh` (`:71-91`) unchanged
- Test: `app/src/test/kotlin/com/kino/puber/data/api/config/KinoPubConfigTest.kt` - add `dnsResolver_isGoogle_whenUnpinned` and `clearPinnedEndpoint_restoresTheGoogleResolver` (pin with a resolver, clear, `DNS_RESOLVER == DohResolver.GOOGLE`); `@AfterEach` (`:37-41`) already clears the pin

**Consumes:** none
**Produces:** `DohResolver`, `DohResolver.GOOGLE`, `HttpStack.create(listeners, resolver)`, `KinoPubConfig.setPinnedEndpoint(endpoint, resolver)`, `KinoPubConfig.DNS_RESOLVER` (Task 12)

- [x] Write the tests; record the red run (`create_queriesTheGivenResolverUrl`, `pinnedEndpointWithResolver_buildsTheAppClientWithDoh`: compile failure on `DohResolver`)
- [x] Main changes
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.api.network.*' --tests 'com.kino.puber.data.di.*' --tests 'com.kino.puber.data.api.config.*'`
- [x] Fast gate; flavor compile; commit ("Let a pinned endpoint name its own DNS-over-HTTPS resolver")

**Done when:** the test command and both compiles exit 0; `rg -n useDoh app/src` prints nothing.

---

### Task 10: Probe only the pin when the endpoint is pinned (D21)

**Covers:** AC-11
**Files:**
- Modify: `app/src/main/java/com/kino/puber/domain/interactor/api/ApiDomainInteractor.kt` - delete the pinned early return at `:136`; `buildAutoResolveCandidates()` (`:257-261`) returns `listOf(KinoPubConfig.CURRENT_ENDPOINT)` when `KinoPubConfig.IS_PINNED_ENDPOINT`; class KDoc `:47`: the six mutators other than `autoResolveWorkingDomain` return early when pinned, the walk probes only the pin. `switchToFound` (`:191-208`) is unchanged: one candidate equal to the current domain gives `changed = false` and no write
- Test: `app/src/test/kotlin/com/kino/puber/domain/interactor/api/ApiDomainInteractorPinnedEndpointTest.kt` - probe fake (`:68-71`) answers a mutable `probeAnswer` (default `true`); class KDoc `:30-34` updated. Replace `autoResolveWorkingDomain_probesNothing_whenPinned` (`:143-152`) with `autoResolveWorkingDomain_probesOnlyThePin_whenPinned`: result `Success(getState(), changed = false)`, `probeCalls == listOf(pin.domain)`, pin still current, no `saveApiDomain`, `evictCalls == 0`, no cache clear or prefetcher invalidate. Add `autoResolveWorkingDomain_returnsNotFound_whenThePinDoesNotAnswer`: `probeAnswer = false`, result `NotFound`, `probeCalls == listOf(pin.domain)`, same no-write checks. `autoResolveWorkingDomain_leavesNoWalkInFlight_whenPinned` (`:154-164`): after the pinned call assert `probeCalls == listOf(pin.domain)` instead of `assertNothingTouched()`. The other six tests (`:103-141`, `:166-172`) unchanged

**Consumes:** none
**Produces:** pinned cold start probes `<pin mainBaseUrl>items/fresh` once (`EndpointProbe.kt:59-61` accepts `items` + `pagination`)

- [x] Write the two tests; record the red run on the unchanged interactor (zero probes, `Success` where `NotFound` is expected)
- [x] Interactor change
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.domain.interactor.api.*'`
- [x] Fast gate; flavor compile; commit ("Probe only the pinned endpoint when resolving the working domain under a pin")

**Done when:** all `ApiDomainInteractor*` test classes pass and both compiles exit 0.

---

### Task 11: GitHub API base URL parameter (D24, main half)

**Covers:** AC-10
**Files:**
- Modify: `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt:959-965` - top-level `internal const val GITHUB_API_BASE_URL = "https://api.github.com"` in this file; `getLatestGitHubRelease(owner, repo, apiBaseUrl: String = GITHUB_API_BASE_URL)` requests `"${apiBaseUrl.trimEnd('/')}/repos/$owner/$repo/releases/latest"`; `Accept` header unchanged
- Modify: `app/src/main/java/com/kino/puber/data/repository/AppUpdateRepository.kt:8-10`, `:18` - constructor `(apiClient: KinoPubApiClient, githubApiBaseUrl: String = GITHUB_API_BASE_URL)`; passes it as the third argument
- Modify: `app/src/main/java/com/kino/puber/data/di/modules.kt:97` - `single<IAppUpdateRepository> { AppUpdateRepository(get()) }` (`singleOf` would ask Koin for the `String`)
- Test (new): `app/src/test/kotlin/com/kino/puber/data/api/KinoPubApiClientGitHubReleaseTest.kt` - JUnit 5; `Log`/`Base64` statics mocked as in `KinoPubApiClientSessionEndTest.kt:64-79`; client built as its `Env.client` (`:402-417`) with an interceptor that records `request.url.toString()` and `request.header("Accept")` and answers 200 `{"tag_name":"v0.0.1"}` (`reply` shape `:425-438`). `getLatestGitHubRelease_requestsGitHubApi_byDefault`: `getLatestGitHubRelease("o", "r")` records `https://api.github.com/repos/o/r/releases/latest`, `Accept` `application/vnd.github+json`. `getLatestGitHubRelease_replacesOnlyTheOrigin_whenBaseUrlGiven`: base `https://127.0.0.1:18765/github` records `https://127.0.0.1:18765/github/repos/o/r/releases/latest`, same `Accept`
- Test: `app/src/test/kotlin/com/kino/puber/data/repository/AppUpdateRepositoryTest.kt` - add `getAvailableUpdate_asksTheGitHubApiOrigin_byDefault` (`coVerify { apiClient.getLatestGitHubRelease("prakunin", "Puber", "https://api.github.com") }`) and `getAvailableUpdate_asksTheGivenOrigin_whenBaseUrlGiven` (`AppUpdateRepository(apiClient, "https://127.0.0.1:18765/github")`, `coVerify` on that third argument). Existing stubs (`:33`, `:69`, `:80`, `:88`, `:108`, `:127`) and `coVerify(exactly = 0)` (`:121`) take `any()` as the third argument so they match whatever the repository passes

**Consumes:** none
**Produces:** `AppUpdateRepository(apiClient, githubApiBaseUrl)`, `GITHUB_API_BASE_URL` (Task 13)

- [x] Write the four tests; record the red run (compile failure on the third parameter)
- [x] Main changes
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.data.repository.AppUpdateRepositoryTest' --tests 'com.kino.puber.data.api.KinoPubApiClient*'`
- [x] Fast gate; flavor compile; commit ("Let the update check take the GitHub API origin as a parameter")

**Done when:** the test command and both compiles exit 0.

---

### Task 12: Instrumentation network: hostname pin, pinned resolver, three origins, certificate SANs (D22, D23)

**Covers:** AC-3, AC-2 (hostname API reaches the mock)
**Files:**
- Modify: `tools/make-baseline-mock-cert.sh` - `[leaf_ext]` (`:41-44`) `subjectAltName = IP:127.0.0.1, DNS:api.baseline.test, DNS:dns.baseline.test`; header `:3` names the two DNS names. Run it once: both PEMs (`app/src/instrumentation/res/raw/baseline_mock_ca.pem`, `baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem`) are re-minted with a new root (key discarded); validity 2020-01-01 to 2051-01-01 unchanged
- Modify: `app/src/instrumentation/java/com/kino/puber/profile/BaselineNetworkBlocker.kt:19-40` - `install(journalDir: File, allowedOrigins: Set<String>)`, `selector(allowedOrigins: Set<String>, journal)`; allowed set normalized with `removeSuffix("/").lowercase()`; allow when `origin(uri) in allowed`, same for `connectFailed`; class KDoc `:13-16`
- Modify: `app/src/instrumentation/java/com/kino/puber/profile/BaselineInstrumentationEnvironment.kt:11-30` - constants `API_HOST`, `DNS_HOST`; `allowedOrigins()` (Round 3 shared definitions); `configure(context)` becomes `configure(journalDirectory(context))`; new `internal fun configure(journalDir: File)`: `KinoPubConfig.setPinnedEndpoint(<pinned endpoint>, <pinned resolver>)` then `BaselineNetworkBlocker.install(journalDir, allowedOrigins())`. `mockOrigin()` (`:14`) unchanged (fixtures, GitHub base)
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineNetworkBlockerTest.kt` - `blocker()` (`:19-20`) passes the three allowed origins; existing six tests unchanged otherwise; add `select_returnsNoProxy_whenOriginIsApiOrDnsTestHost` (both `.test` origins -> `[NO_PROXY]`, journal empty), `select_returnsDeadProxyAndJournals_whenTestHostIsNotAllowed` (`https://other.baseline.test:18765/x`), `select_returnsDeadProxyAndJournals_whenAllowedTestHostPortDiffers` (`https://api.baseline.test:18766/x`)
- Test (new): `.../profile/BaselineInstrumentationEnvironmentTest.kt` - JUnit 5, `@TempDir` journal dir; `@BeforeEach` saves `ProxySelector.getDefault()`, `@AfterEach` restores it and calls `KinoPubConfig.clearPinnedEndpoint()`. `configure_installsTheBlockerAsDefaultProxySelector`: after `configure(dir)`, `ProxySelector.getDefault().select(URI("https://example.org/"))` is the dead proxy (`127.0.0.1:9`) and `BaselineNetworkJournal(dir).snapshot() == ["https://example.org:443/"]`. `configure_pinsApiToTheMockHostAndResolver`: `IS_PINNED_ENDPOINT`, `MAIN_API_BASE_URL == "https://api.baseline.test:18765/v1/"`, `EXTRA_API_BASE_URL == "https://api.baseline.test:18765/"`, `DNS_RESOLVER` equals the pinned resolver
- Test (new): `.../profile/BaselineNetworkSecurityConfigTest.kt` - `baseConfig_trustsOnlyTheMockRootAndRefusesCleartext`: reads `src/instrumentation/res/xml/network_security_config.xml` or `app/src/...` (first that exists, as `BaselineFixtureContentTest.kt:20-23`) with `javax.xml.parsers.DocumentBuilderFactory`; exactly one `base-config` with `cleartextTrafficPermitted="false"`; exactly one `certificates` element in the document, `src="@raw/baseline_mock_ca"`; no `domain-config`

**Consumes:** Task 9 `DohResolver`, `setPinnedEndpoint(endpoint, resolver)`, `DNS_RESOLVER`
**Produces:** `BaselineInstrumentationEnvironment.allowedOrigins()`, `configure(journalDir)`; leaf with the two DNS SANs (Task 14 self-test)

- [x] Write the six tests; record the red run (compile failure on `Set` origins and `configure(File)`)
- [x] Script change and run; `openssl verify -CAfile app/src/instrumentation/res/raw/baseline_mock_ca.pem baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem` prints OK; `openssl x509 -in baselineprofile/src/main/assets/baseline/tls/leaf-test-only.pem -noout -ext subjectAltName` lists `127.0.0.1`, `api.baseline.test`, `dns.baseline.test`; `grep -c 'PRIVATE KEY' app/src/instrumentation/res/raw/baseline_mock_ca.pem` is 0
- [x] Flavor changes; `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug`
- [x] Fast gate; flavor compile; commit ("Pin the instrumentation app to loopback test hostnames resolved over DNS-over-HTTPS by the mock")

**Done when:** the openssl lines hold, the instrumentation test/detekt/lint command and both compiles exit 0.

---

### Task 13: Real token store with seeded tokens and the real update check (D20, D24 flavor half)

**Covers:** AC-4 (`BaselineAuthSeedTest`), AC-6 (seed lives only in the flavor)
**Files:**
- Create: `app/src/instrumentation/java/com/kino/puber/profile/BaselineAuthSeed.kt` - `internal object BaselineAuthSeed`: `fun ensure(context: Context) = ensure(CryptoPreferenceRepository(context))` (`data/repository/CryptoPreferenceRepository.kt:29-31`); `internal fun ensure(store: ICryptoPreferenceRepository)`: when `store.getAccessToken().isNullOrEmpty()`, `saveAccessToken("baseline-access-token")`, `saveRefreshToken("baseline-refresh-token")`, `saveUsername("baseline-profile")` (public save methods `:37-53`); otherwise nothing. Null-or-empty, not a key check: `clearAccessToken` seals an empty string (`:41`, `:69-73`)
- Modify: `app/src/instrumentation/java/com/kino/puber/profile/BaselineProfileApp.kt` - `configureRuntime()` (`:21-23`) calls `BaselineAuthSeed.ensure(this)` then `BaselineInstrumentationEnvironment.configure(this)`; `runtimeModules()` (`:25-30`) holds only `single<IAppUpdateRepository> { AppUpdateRepository(get(), BaselineInstrumentationEnvironment.mockOrigin() + "/github") }`; delete `SyntheticAuthPreferences` (`:50-80`), `NoOpAppUpdateInteractor` (`:82-104`) and their imports
- Test (new): `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineAuthSeedTest.kt` - in-memory fake implementing all 15 methods of `ICryptoPreferenceRepository.kt:3-20`. `ensure_sealsSyntheticTokens_whenAccessTokenIsNullOrEmpty`: for an access token `null` and `""`, `ensure(store)` leaves access, refresh and username at the three seeded values. `ensure_keepsTheStoredTokens_whenAccessTokenIsPresent`: stored `"kept-access"`/`"kept-refresh"` stay

**Consumes:** Task 11 `AppUpdateRepository(apiClient, githubApiBaseUrl)`; Task 12 `configure(context)`
**Produces:** the instrumentation app runs `CryptoPreferenceRepository` and `AppUpdateInteractor` -> `AppUpdateRepository` -> `getLatestGitHubRelease` against `/github/...` (Task 14 route)

- [x] Write the two tests (red: `BaselineAuthSeed` absent)
- [x] Seed and app changes; `rg -n 'SyntheticAuthPreferences|NoOpAppUpdateInteractor' app/src` prints nothing
- [x] Instrumentation test/detekt/lint command (Task 12)
- [x] Fast gate; flavor compile; commit ("Seed synthetic tokens into the real token store and run the real update check in the instrumentation app")

**Done when:** the rg prints nothing, the two tests pass and both compiles exit 0.

---

### Task 14: Mock routes and fixtures for DoH, the probe, the update check and TV home (D22, D24, D25, D26)

**Covers:** AC-2, AC-3
**Files:**
- Modify: `baselineprofile/build.gradle.kts:39-46` - `implementation(libs.okhttp.doh)` (`gradle/libs.versions.toml:88`) for the self-test
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt` - route table (`:135-163`): split `/v1/items/(fresh|popular)` (`:145`) into `/v1/items/fresh` (`required = home`) and `/v1/items/popular`; `/v1/history` (`:148`) `required = home`; add `GET /github/repos/[^/]+/[^/]+/releases/latest` (`required = home`, fixture `github_release`); add `POST /dns-query` (`required = home`): parse the RFC 1035 query in the body (12-byte header, QNAME labels, QTYPE); for QNAME `api.baseline.test` or `dns.baseline.test` with QTYPE A answer `application/dns-message` with the query's ID and question, flags `0x8180`, ANCOUNT 1, answer `0xC00C`, type A, class IN, TTL 60, RDLENGTH 4, `127.0.0.1`; any other QTYPE for those names: NOERROR, no answer; any other name: flags `0x8183` (NXDOMAIN), no answer, and `unknownRequests += BaselineRequest("DNS", <name>)` (the dispatcher already holds `lock`, `:97`). `NAMES` (`:259-262`) gains `github_release`. Numeric literals as named constants in the companion (`:181-190`)
- Create: `baselineprofile/src/main/assets/baseline/github_release.json` - `{"tag_name":"v0.0.1","name":"Baseline","draft":false,"prerelease":false,"assets":[]}` (shape `GitHubReleaseModels.kt:7-15`; no `html_url`, so no URL)
- Modify: `baselineprofile/src/main/assets/baseline/history.json` - one entry in the wire shape (Corrections table): `counter` 1, `first_seen`/`last_seen` a fixed epoch second, `item` {`id` 1, `title` "Baseline details", `type` "movie", `posters` as in `item.json` (`wide` = `{{MOCK}}/media/wide.jpg`)}, `media` {`id` 1, `number` 1, `snumber` 0, `duration` 24}, `time` 5; `pagination.total_items` 1. Passes `ContinueWatchingSource.kt:26-34` (5 s < 90 % of 24 s, `WatchCompletionPolicy.kt:20-24`; https artwork)
- Test: `app/src/testInstrumentation/kotlin/com/kino/puber/profile/BaselineFixtureContentTest.kt` - add `historyFixture_carriesOneUnfinishedMovieWithHttpsWidePoster` (decode with `Json { ignoreUnknownKeys = true }.decodeFromString<HistoryPageResponse>` after the `parsed` replacements `:30-32`, `.toModel().items` has one entry, type `MOVIE`, `posters.wide == "$origin/media/wide.jpg"`, `WatchCompletionPolicy.isFinished(time, video.duration)` false) and `githubRelease_isOlderThanAnyBuild` (`AppVersion.parse(tag_name)` is not null and `<= AppVersion(0, 0, 1)` and `< AppVersion.parse(BuildConfig.VERSION_NAME)`). The two existing scan tests cover the new files automatically (`:25-28`)
- Test: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockSelfTest.kt` - `hitStartupRoutes()` (`:48-51`) also hits `/v1/items/fresh`, `/v1/history`, `/github/repos/o/r/releases/latest` and one DoH lookup of `api.baseline.test`. DoH helper: `DnsOverHttps.Builder().client(<OkHttpClient trusting HandshakeCertificates.Builder().addTrustedCertificate(leaf.certificate)>).url("https://dns.baseline.test:<boundPort>/dns-query".toHttpUrl()).bootstrapDnsHosts(InetAddress.getByName("127.0.0.1")).post(true).includeIPv6(false).build()` (keep `leaf` from `setUp` `:28-30`; if the platform refuses a leaf as trust anchor, `addInsecureHost("dns.baseline.test")` and record it). New: `startupScenario_requiresFreshHistoryGithubAndDnsQuery` (for `Startup`, `BrowseAndDetails`, `Rail`: `startIteration` then `verify().missingRequiredRoutes` contains the four D26 descriptions), `dnsQuery_resolvesBothTestHostsToLoopback_throughOkHttpDoh` (both names -> `[127.0.0.1]`), `dnsQuery_answersNxdomainAndListsUnknown_forOtherName` (`other.baseline.test` throws `UnknownHostException`; `verify().unknownRequests` contains `BaselineRequest("DNS", "other.baseline.test")`)

**Consumes:** Task 12 leaf SANs
**Produces:** required routes per Round 3 shared definitions; `BaselineRequest("DNS", name)` for unknown names

- [x] Write the five tests (fixture tests red on the empty `history.json` and absent `github_release.json`; self-tests red by absence, run on the device in Task 16)
- [x] Routes, fixtures, dependency
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`
- [x] Fast gate; flavor compile; commit ("Serve DNS-over-HTTPS, the update check and a history entry from the baseline mock and require them on home journeys")

**Done when:** the fixture tests pass and both compiles exit 0.

---

### Task 15: Late-request check between tests and after the last one (triage #3)

**Covers:** AC-2
**Files:**
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt:281-300` - `JourneyCheck.checkLate(verifyBroadcastOutput, verification)`: throws `IllegalStateException` unless the output contains `result=-1` and `verification.unknownRequests` is empty; ignores `missingRequiredRoutes`; message starts "Late requests after <scenario>" and lists unknown and journal
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileTestControl.kt` - `checkLate(targetPackageName, backend)`: `awaitQuiescence(backend)` (`:72-88`), `VERIFY` broadcast, `backend.verify()`, one `Log.i("PuberGen", "late after=<scenario> unknown=<...> journal=<...>")`, then `JourneyCheck.checkLate`
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt` - companion (`:197-211`): `targetPackage()` reads `targetAppId` from `InstrumentationRegistry.getArguments()` (instance `packageName` `:24-26` delegates to it); `private var lateCheckDue = false`. `@Before resetMock()` (`:28-32`): when `lateCheckDue`, `checkLate(packageName, backend)` first; then `lateCheckDue = true`, `resetTest()`, `resetJournal`. `@AfterClass stopBackend()` (`:207-211`): `try { checkLate(targetPackage(), backend) } finally { backend.close() }`
- Test: `BaselineMockSelfTest.kt` - `lateCheck_fails_whenUnknownRouteServedAfterTheLastIteration` (`hitStartupRoutes()`, then `/v1/not-a-route`; `checkLate("Broadcast completed: result=-1", verify())` throws naming the path), `lateCheck_fails_whenEgressJournalNotEmpty` (`result=2, data="https://example.org:443/"` throws naming `example.org`), `lateCheck_passes_withNoRequiredRoutesHit` (no request; `result=-1` passes). Reuse `assertCheckFails` (`:53-61`) with the check as a parameter

**Consumes:** Task 14 self-test helpers
**Produces:** `PuberGen` `late after=` lines before every test after the first and in `@AfterClass` (qa M1 step 3)

- [x] Write the three tests (red by absence; run on the device in Task 16)
- [x] `checkLate` and generator lifecycle
- [x] Fast gate; flavor compile; commit ("Fail baseline generation on requests made after a journey's last iteration")

**Done when:** both compiles exit 0 and `rg -n 'checkLate' baselineprofile/src` shows the `@Before` and `@AfterClass` calls.

---

### Task 16: Prod cold-start guard and regeneration on the emulator (D27)

**Covers:** AC-1, AC-2, AC-3 (device run of the self-test), AC-4, AC-5 (guard), AC-6 and AC-7 (qa M3, M5 after this task), AC-9 (qa M6 after this task)
Device run is the implementer's, as in Task 8; qa reads its log for M1/M2.
**Files:**
- Modify: `app/src/test/kotlin/com/kino/puber/BaselineProfileContentTest.kt` - add `profilesCoverProdColdStartPaths`: every D27 guard substring (Round 3 shared definitions) has a rule (`hasRuleFor`, `:19`) in both `baseline` and `startup`; message names the file and the substring. Other tests (`:21-76`) unchanged
- Modify (generated): `app/src/main/generated/baselineProfiles/baseline-prof.txt`, `startup-prof.txt`
- Modify only on a failure, recorded under Deviations: `BaselineMockBackend.kt` route table (add a route, never loosen `JourneyCheck`, `checkLate` or the blocker); `BaselineProfileGenerator.kt` rail journey steps if the non-empty History screen moves focus (`railShows` checks stay)

**Consumes:** Tasks 9-15
**Produces:** regenerated profiles; logcat path and self-test result path for qa

- [x] Write the test; `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest --tests 'com.kino.puber.BaselineProfileContentTest'` fails on `profilesCoverProdColdStartPaths` with the 7d639a9b profiles; record the red run
- [x] Lease (`tools/emulator-resource-lock.sh acquire host.docker.internal:5555`), `ssh mac-emu start` (prints `ready`), `adb connect host.docker.internal:5555`
- [x] With no `.env` and `PUBER_CLIENT_SECRET` unset: `AGENT_GRADLE_SERIAL=1 DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh` exits 0
- [x] In the printed logcat file: every iteration line has `missing=[] unknown=[]` and an empty journal; a `late after=` line before each generator test after the first (three) and one from `@AfterClass`, each clean. In `baselineprofile/build/outputs/androidTest-results/`, all 11 `BaselineMockSelfTest` methods pass
- [x] Release the lease; `ssh mac-emu stop` if `tools/emulator-resource-lock.sh status` shows no other lease
- [x] `grep -c '^[^H].*->' app/src/main/generated/baselineProfiles/baseline-prof.txt` is 0; `grep -c 'com/kino/puber/profile/' app/src/main/generated/baselineProfiles/*.txt` is 0 for both
- [x] Full gate; flavor compile; commit test and profiles together ("Regenerate baseline profiles with the prod token, DNS, probe, update and TV home paths and guard them")
- [x] Implementation record: logcat path, self-test result path, the 11 guard counts per file

**Done when:** generation exits 0, `BaselineProfileContentTest` passes on the regenerated files and the full gate exits 0.

---

### Task 17: Profile docs for round 3

**Covers:** AC-8
**Files:**
- Modify: `docs/release.md:117-124` - generation section: the instrumentation app keeps the prod token store (seeded synthetic tokens), resolves `api.baseline.test` through DNS-over-HTTPS served by the mock (`dns.baseline.test`, bootstrap `127.0.0.1`), probes the pinned endpoint, runs the update check against the mock's `/github` route and publishes one TV home program; the blocker allows the three origins only; the per-iteration check plus the late check before each test and after the last (`PuberGen` `late after=` lines). "synthetic auth" becomes "seeded synthetic tokens"
- Modify: `docs/release.md:137` - the cert recipe row names the leaf SANs `IP:127.0.0.1`, `DNS:api.baseline.test`, `DNS:dns.baseline.test`

**Consumes:** Tasks 12-16
**Produces:** nothing later tasks call

- [x] Edit; `awk '/^## Refresh Baseline Profiles/,/^## Verification/' docs/release.md | grep -nE 'CLIENT_SECRET|pair|PuberAuth|profile\.auth'` prints nothing
- [x] Fast gate; commit ("Describe the round-three baseline generation network and checks in the release guide")

**Done when:** the grep prints nothing and the fast gate exits 0.

---

## Implementation record

<!-- owner: implementer -->

**Commits:**

| Task | Commit |
|---|---|
| 1 | 05c735cf Add the instrumentation flavor and a loopback-only test certificate |
| 2 | 57c10b1a Run the instrumentation app on a pinned loopback endpoint with synthetic auth and an egress blocker |
| 3 | 1e6ed805 Add loopback mock fixtures with posters and an HLS stream reference |
| 4 | 6e7fbdbf Generate baseline profiles from the instrumentation variant against an HTTPS loopback mock |
| 5 | 0d98c228 Reset caches and check mock routes and egress on every baseline profile iteration |
| 6 (recipe only) | d291fed3 Add the recipe for the synthetic HLS stream and poster images of the baseline mock |
| 7 | 62843871 Remove the paired-account baseline profile route and its auth provider |
| 6 (media) | bfeb2eb6 Add a 24-second synthetic HLS stream and poster images for the baseline mock |
| 6a | 8328331d Backdate the loopback test certificates so an emulator with a lagging clock accepts them |
| 8a | e3cc80d7 Serve the device settings route in the baseline mock and stop requiring genres on the rail journey |
| 8b | 24ea13b4 Start the rail journey from Home whatever entry LEFT lands on |
| 8 | Regenerate baseline profiles against the loopback mock and ban instrumentation classes from them (the commit after 24ea13b4) |

**Red run** (P0 `red` tests on the unchanged code):

- red: `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenHostIsNotLoopback` - expected: <1> but was: <0> (stub selector returns no proxy).
- red: `BaselineNetworkBlockerTest.select_returnsDeadProxyAndJournals_whenLoopbackPortDiffers` - expected: <1> but was: <0>.
- red: `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsMock` - expected: <[DIRECT]> but was: <[]>.
- red (P1): `connectFailed_journalsUri`, `journal_keepsLines_whenNewBlockerOpensSameDir`, `clear_emptiesJournal` - empty journal against the stub. The first run of the stub had `clear_emptiesJournal` green (vacuous: the stub records nothing); the test now asserts the line is present before `clear()`, and fails on the stub. All six fail against the stub (signature-only classes with default results).
- red (P2): `BaselineFixtureContentTest` three tests - "no fixtures found" / `Key items.json is missing` with the fixtures moved away.
- not run: `BaselineMockSelfTest` five tests (P0 red `unknownRoute_returns404AndIsListedInVerify`, `journeyCheck_fails_whenUnknownRouteServed`, `journeyCheck_fails_whenEgressJournalNotEmpty`, `journeyCheck_fails_whenRequiredRouteMissing`; P1 pass test). They are device tests in `:baselineprofile` (a `com.android.test` module with no JVM tests); the classes did not exist before Task 4 (red by absence). They first run in the Task 8 generation run.
- red: `BaselineProfileContentTest.profilesHaveNoInstrumentationOrGeneratorClasses` - "baseline profile holds com/kino/puber/profile/" (run on the base profiles, 6 tests, 1 failed).
- green on base: `BaselineProfileContentTest.baselineProfileCoversImageTlsAndHlsPaths` - the base profile already holds the Coil, HLS and TLS paths (guard, P1).
- red (Task 8 device run, before the fixes): `BaselineProfileGenerator` iterations failed `JourneyCheck` with every route missing (TLS clock), then `unknown=[GET /v1/device/info]`; `BaselineMockSelfTest` five tests pass on the emulator (5 of 11 tests, 2 skipped benchmark tests).

**Deviations from the plan:**

- Task 6: media made by the conductor with a static ffmpeg 4.2.2 (libx264, aac) and copied in; 296,745 bytes for stream.m3u8 plus six segments (limit 307200), six segment entries, ENDLIST present. Version noted in the recipe header.
- Task 8, first generation run failed on the emulator: the leaf and root were valid from the host clock (2026-11-26) but the emulator clock reads 2026-11-23, so the TLS handshake failed (screen "Checking KinoPub access", every route missing). Fix 8328331d: the recipe uses `openssl ca` with a fixed validity 2020-01-01 to 2051-01-01, both PEMs regenerated, docs/release.md line updated. Spec D7 says "25 years"; the leaf is now 31 years.
- Added routes (the plan allows adding routes): `GET /v1/device/info` (new fixture `device_info.json`, shape from `DeviceResponse` in Models.kt), requested by the player/startup settings repository and served 404 before. `GET /v1/genres` is no longer `required` on the Rail scenario: no screen of the rail journey requests it (only ShowAllVM does), so spec D14 "Rail: + genres" is dropped; the route stays served.
- Rail journey (e3cc80d7 follow-up, commit 8b): LEFT from content lands on the rail entry level with the focused content, which with the mock layout is History, so Search and Watching were never opened (profile lost `ui/feature/search` and `ui/feature/favorites`, `baselineProfileCoversDetailsPlayerRailTabsAndStartupClasses` failed). The journey now presses UP twelve times after opening the rail, so it starts from Home as intended. Confirmed on the emulator by screenshots before the fix.
- The generated `startup-prof.txt` has 23546 method lines without H (24172 at base); the plan's H check is for `baseline-prof.txt` only (0).
- `BaselineMockBackend` carries a `requestCount` and `boundPort` (needed by the quiescence wait and the port-0 self-test); `BaselineVerification` has no `generationId`; `BaselineFixtures` holds name-keyed JSON bodies plus a media reader instead of one field per fixture.
- Task 2: `BaselineInstrumentationEnvironment` also exposes `journalDirectory(context)` so the receiver and `configure` open the same directory.
- `:app:generateBaselineProfile --dry-run` showed only `:baselineprofile:*InstrumentationNonMinifiedRelease*` tasks, so the `variants { create("instrumentationRelease") }` route works; no fallback needed.
- A first combined compile run once failed with a Kotlin back-end internal error in `PlayerInteractor.setSeasonWatched` (main code, untouched); the same command passed on retry without change.

**Files touched outside the plan:** none.

**For the reviewers:**

- `checkIteration` runs inside the profiled block (plan wording); the control-process classes are filtered from the profile by `filterPredicate`.
- The egress blocker's NSC and `ProxySelector` cover the app's HTTP stack; the blocker unit tests use the real selector with real `URI`s.
- Fixtures were written by hand from `Models.kt`; no captured data.
- `StartupBenchmarks.kt` KDoc still names `connectedDevBenchmarkReleaseAndroidTest` (out of scope, owned by `startup-benchmark-entry`).
- Generation run (emulator host.docker.internal:5555, no .env, PUBER_CLIENT_SECRET unset): exit 0, `BUILD SUCCESSFUL in 18m 47s`. Logcat: `/tmp/puber-baseline-profile/20261126-141548-logcat.txt` (37 `PuberGen` iteration lines, all with `missing=[] unknown=[] journal` empty). Self-test results: `baselineprofile/build/outputs/androidTest-results/connected/nonminifiedrelease/flavors/instrumentation/TEST-sdk_google_atv64_arm64 - 16.xml` (11 tests, 0 failures, 2 skipped). The clock skew above means a re-run on a restored snapshot still works.
- Profiles: `baseline-prof.txt` 46224 lines, 0 non-H method rules, 0 `com/kino/puber/profile/`; `startup-prof.txt` 0 `com/kino/puber/profile/`. New in the baseline: `ui/feature/device` (590 rules, the Settings screen opened by the device settings route being served).
- check.full (extended): `BUILD SUCCESSFUL in 1m 29s` at the Task 8 commit.

**Blocked:** none. The Task 6 block was lifted by the owner running the recipe (static ffmpeg 4.2.2); media committed.

### Round 3 (Tasks 9-17)

| Task | Commit |
|---|---|
| 9 | 7f3917e8 Let a pinned endpoint name its own DNS-over-HTTPS resolver |
| 10 | a0f62a86 Probe only the pinned endpoint when resolving the working domain under a pin |
| 11 | 78333fd5 Let the update check take the GitHub API origin as a parameter |
| 12 | 6a6fe014 Pin the instrumentation app to loopback test hostnames resolved over DNS-over-HTTPS by the mock |
| 13 | fbf428bd Seed synthetic tokens into the real token store and run the real update check in the instrumentation app |
| 14 | de1fde21 Serve DNS-over-HTTPS, the update check and a history entry from the baseline mock and require them on home journeys |
| 15 | aeaa2a09 Fail baseline generation on requests made after a journey's last iteration |
| 16 | 9b7934a3 Regenerate baseline profiles with the prod token, DNS, probe, update and TV home paths and guard them |
| 17 | 2a21e280 Describe the round-three baseline generation network and checks in the release guide |

**Red run (round 3):**

Task 9, 11, 12, 13 ran against the smallest stub (new parameters and types present, default result), so each test failed on its assertion, not on a compile error.

- red: `DnsOverHttpsFactoryTest.create_queriesTheGivenResolverUrl` - expected the resolver URL, was `https://dns.google/dns-query` (stub factory ignores `resolver`).
- red: `KinoPubConfigTest.clearPinnedEndpoint_restoresTheGoogleResolver` - expected `DohResolver(baseline)`, was `GOOGLE`.
- red: `ApiModulePinnedEndpointTest.pinnedEndpoint_buildsTheAppClientWithoutDoh` (existing, unchanged) - stub `DNS_RESOLVER` was Google under a pin, so the client had DoH.
- green on base: `ApiModulePinnedEndpointTest.pinnedEndpointWithResolver_buildsTheAppClientWithDoh` - the stub always built DoH; the pinned-without-resolver test above is what discriminates. `googleResolver_isDnsGoogleWithBootstrap8844And8888`, `unpinnedEndpoint_buildsTheAppClientWithDoh`, `dnsResolver_isGoogle_whenUnpinned`, `create_resolvesThroughDoh_byDefault`: guards of the unchanged defaults.
- red: `ApiDomainInteractorPinnedEndpointTest.autoResolveWorkingDomain_probesOnlyThePin_whenPinned`, `..._returnsNotFound_whenThePinDoesNotAnswer`, `..._leavesNoWalkInFlight_whenPinned` (changed) - zero probes (`expected [127.0.0.1] but was []`, `Success` where `NotFound` is expected).
- red: `KinoPubApiClientGitHubReleaseTest.getLatestGitHubRelease_replacesOnlyTheOrigin_whenBaseUrlGiven` - requested `https://api.github.com/...`; `AppUpdateRepositoryTest.getAvailableUpdate_asksTheGivenOrigin_whenBaseUrlGiven` - third argument was the default. green on base: `getLatestGitHubRelease_requestsGitHubApi_byDefault`, `getAvailableUpdate_asksTheGitHubApiOrigin_byDefault` (guards).
- red: `BaselineNetworkBlockerTest.select_returnsNoProxy_whenOriginIsApiOrDnsTestHost`, `BaselineInstrumentationEnvironmentTest.configure_installsTheBlockerAsDefaultProxySelector`, `configure_pinsApiToTheMockHostAndResolver` - stub allowed only the first origin, `configure(File)` did nothing. green on base: `select_returnsDeadProxyAndJournals_whenTestHostIsNotAllowed`, `..._whenAllowedTestHostPortDiffers` (a one-origin stub already denied them), `BaselineNetworkSecurityConfigTest` (the config already held; guard).
- red: `BaselineAuthSeedTest.ensure_sealsSyntheticTokens_whenAccessTokenIsNullOrEmpty` - access token was null (empty `ensure`). green on base: `ensure_keepsTheStoredTokens_whenAccessTokenIsPresent` (guard).
- red: `BaselineFixtureContentTest.historyFixture_carriesOneUnfinishedMovieWithHttpsWidePoster` (`NoSuchElementException`, empty `history.json`), `githubRelease_isOlderThanAnyBuild` (fixture absent).
- not run on the JVM: `BaselineMockSelfTest` additions (Tasks 14, 15): device tests, red by absence of the routes and `checkLate`; they passed in the Task 16 run (11 of 11).
- red: `BaselineProfileContentTest.profilesCoverProdColdStartPaths` on the 7d639a9b profiles - `baseline profile has no rule for com/kino/puber/data/repository/CryptoPreferenceRepository;->decrypt`; green on the regenerated profiles.

**Task 16 evidence (emulator host.docker.internal:5555, API 36, loopback mock only, no .env, `PUBER_CLIENT_SECRET` unset):**

- Generation exit 0 on the second run (`BUILD SUCCESSFUL`, about 17 min). Logcat: `/tmp/puber-baseline-profile/20261126-162655-logcat.txt`; 35 `PuberGen` iteration lines, every one `missing=[] unknown=[]` with an empty journal; 4 `late after=` lines (after Rail, BrowseAndDetails, Startup from `@Before`, after Playback from `@AfterClass`), all `unknown=[]` with an empty journal.
- Self-test and generator results: `baselineprofile/build/outputs/androidTest-results/connected/nonminifiedrelease/flavors/instrumentation/TEST-sdk_google_atv64_arm64 - 16.xml` (17 tests, 0 failures, 2 skipped benchmarks; all 11 `BaselineMockSelfTest` methods pass).
- Profile checks: `baseline-prof.txt` 47739 lines, 0 non-H method rules, 0 `com/kino/puber/profile/`; `startup-prof.txt` 31780 lines, 0 `com/kino/puber/profile/`. Coil/HLS/TLS rule lines in the baseline: `coil3/network/` 221, `coil3/decode/` 148, `androidx/media3/exoplayer/hls/` 387, `okhttp3/internal/tls/` 32.
- D27 guard counts (baseline / startup), equal to the 5cd3e37e counts: CryptoPreferenceRepository decrypt 1/1, secretKey 1/1; DnsOverHttpsFactory create 1/1; `DnsOverHttps;->` 4/4; BootstrapDns lookup 1/1; ApiDomainInteractor walkForWorkingDomain 1/1; HttpEndpointProbe isReachable 1/1; AppUpdateInteractor checkForUpdate 2/2; AppUpdateRepository getAvailableUpdate 1/1; PreviewProgram 13/13; AndroidTvHomePublisher publish 1/1. No rule stayed absent.
- Lease released, `ssh mac-emu stop` run (no other lease).

**Deviations (round 3):**

- Task 16, first generation run failed on a mock gap: the app also asks DoH for the HTTPS-record name `_18765._https.api.baseline.test`, which the DNS route listed as an unknown request. The route now accepts that prefix for the two test hosts (NOERROR, no answer for a non-A type, as the plan says for other types); `JourneyCheck`, `checkLate` and the blocker are unchanged. The same run showed `dnsQuery_answersNxdomainAndListsUnknown_forOtherName` asserting an exception message that OkHttp leaves empty; the assertion was dropped.
- `KinoPubApiClientGitHubReleaseTest` checks that `application/vnd.github+json` is among the `Accept` values: Ktor's content negotiation appends `application/json`, so the header is not that single value.
- Self-test DoH trust uses `addTrustedCertificate(leaf)` as planned (no `addInsecureHost` fallback was needed): the leaf SANs are verified on the device.
- Spec D25 row synced to the wire shape (readiness note 1) in the Task 17 commit.
- `:baselineprofile` has no detekt task; `:app:detektAll` does not cover it.

**For the reviewers (round 3):**

- Main-source changes are Tasks 9-11 only; each default is pinned by the JVM tests named in the plan. `rg -n useDoh app/src` prints nothing.
- Pinned cold start now probes `<pin>items/fresh` once; with an answering mock the walk returns `changed = false`.
- check.full (extended): `BUILD SUCCESSFUL in 1m 51s` before the Task 16 commit.


## Readiness

Verdict: **READY**

Spec and plan at worktree HEAD c6e8d77c (base 5cd3e37e; no code change since base, `git diff 5cd3e37e..HEAD -- app baselineprofile tools Makefile gradle` is empty): `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md`, `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/plans/2026-11-26-hermetic-baseline-generation.md`.

### Round 1 fixes

| Fix | Status |
|---|---|
| 1. Manual P0 rows marked | Done: spec Traceability AC-1 (M1), AC-6 (M3), AC-7 (M5), AC-9 (M6) read `P0 guard`; `test-design.md` has no P0 row without `red`/`guard` |
| 2. AC-9 in a Covers line | Done: plan Task 8 Covers ends "AC-9 (qa M6, after Task 8)" |
| Note: private companion | Taken: Task 5 drops `private` from the companion (`BaselineProfileGenerator.kt:202`) so the `@JvmStatic @BeforeClass`/`@AfterClass` are public |

### Checks

| Check | Result |
|---|---|
| Every AC has a plan task and a Test design row | Pass: AC-1..AC-9 each in a Covers line (Tasks 1-8) and a Traceability row |
| Citations resolve (re-sampled at HEAD) | Pass: `app/build.gradle.kts:438-444` comment + `devReleaseBaselineProfile` block, `:446-453` `baselineProfile {}`; `ICryptoPreferenceRepository.kt:17-19` three session-end methods; `libs.versions.toml:14` okhttp, `:22` koin, `:88` `okhttp-doh`; `AndroidManifest.xml:22` `android:name=".PuberApp"`; `emulator-resource-lock.sh:17-28` usage; `Makefile:141` `baseline-profile` target; `gradle.properties` 4 lines, no trailing newline; `BaselineProfileContentTest.kt:12-17`, `:19`; round 1 table still holds (code unchanged) |
| No task leaves the build red; dependency order | Pass, unchanged from round 1 |
| P0 rows: defect named, `red`/`guard`, test in backticks | Pass: automated rows name their test in backticks; manual/device rows reference M1-M7 and are `guard` |
| Inside Out of scope; blocking questions answered | Pass: Makefile logic, `railShows`, `StartupBenchmarks`, PR workflows untouched; Q1, Q3 answered in `state.md` |

## Readiness (round 3, fix-round Tasks 9-17)

Verdict: **READY**

Scope: fix-round Tasks 9-17 of `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/plans/2026-11-26-hermetic-baseline-generation.md` (section "Round 3: fix tasks"), spec `/home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/specs/2026-11-26-hermetic-baseline-generation.md`, at worktree HEAD c0704a3f. `git diff --stat 7d639a9b..HEAD` touches only the spec and the plan, so every citation below was read in code that Tasks 9-17 have not changed yet.

### Checks

| Check | Result |
|---|---|
| Every AC has a task and a Test design row | Pass. Covers lines: AC-1 T16; AC-2 T12, T14, T15, T16; AC-3 T12, T14, T16; AC-4 T13, T16; AC-5 T16; AC-6 T13, T16 (qa M3); AC-7 T16 (qa M5); AC-8 T17; AC-9 T16 (qa M6); AC-10 T9, T11; AC-11 T10. Every R3 test name in `test-design.md` is in the spec Traceability table |
| Citations resolve (sampled more than 30) | Pass: `DnsOverHttpsFactory.kt:19-23` google URL and bootstrap hosts; `HttpStack.kt:30` `useDoh`, `:34-39` system branch, `:45` factory call; `KinoPubConfig.kt:26` `pinnedEndpoint`, `:63` `IS_PINNED_ENDPOINT`, `:84-90` set/clear; `modules.kt:74` `useDoh = !IS_PINNED_ENDPOINT`, `:97` `singleOf(::AppUpdateRepository)`; `ApiDomainInteractor.kt:136` pinned early return, `:191-208` `switchToFound`, `:257-261` `buildAutoResolveCandidates`; `KinoPubApiClient.kt:959-965` hard-coded GitHub URL; `AppUpdateRepository.kt:18`; `AppUpdateRepositoryTest.kt` stubs at :33, :69, :80, :88, :108, :127 and `coVerify` at :121; `KinoPubApiClientSessionEndTest.kt:391-417` interceptor and `client()`; `CryptoPreferenceRepository.kt:41` and `:69-73` (clear writes an encrypted ""); `ICryptoPreferenceRepository.kt:5-19` (15 methods); `BaselineProfileApp.kt:50-80` and `:82-104`; `BaselineNetworkBlocker.kt:19-40`; `BaselineMockBackend.kt:97` lock, `:145`, `:148`, `:259-262` NAMES, `:281-300` `JourneyCheck`; `BaselineProfileTestControl.kt:72-88`; `BaselineProfileGenerator.kt:24-32`, `:197-211`; `BaselineMockSelfTest.kt:28-30`, `:48-61`; `HistoryPageResponse.kt:32-43` wire shape; `ContinueWatchingSource.kt:26-34`; `libs.versions.toml:88` okhttp-doh; `make-baseline-mock-cert.sh:42` SAN; `docs/release.md:117-124`, `:137` |
| D27 guard counts | Pass: all 11 substrings re-counted with `git show`, the same in both files: 1,1,1,4,1,1,1,2,1,13,1 at 5cd3e37e and 0 at 7d639a9b, so `profilesCoverProdColdStartPaths` is red before Task 16 as planned |
| No task leaves the build red; dependency order | Pass. Each signature change updates its callers in the same task: `HttpStack.create` (only `modules.kt` and `HttpStackTest` pass `useDoh`), `BaselineNetworkBlocker.install` and `BaselineInstrumentationEnvironment.configure` (T12, callers in the flavor), `AppUpdateRepository` (T11 also edits `modules.kt:97`). Order 9 -> 12 -> 13 and 11 -> 13, 12 -> 14 -> 15 -> 16 -> 17 matches each task's Consumes line. The new profile guard enters only in T16, together with the regenerated profiles |
| P0 rows | Pass: every R3 P0 row names its defect, is `red` or `guard`, and names its test in backticks; manual rows (M1, M3, M5, M6) are `guard` |
| Out of scope; blocking questions | Pass: main-source edits only in T9-T11 (Q5 answered yes); T12-T17 stay in the flavor, `:baselineprofile`, `tools/` and docs; Makefile, `railShows`, `StartupBenchmarks`, PR workflows untouched. Q5, Q6, Q7 and the architect's wording question are answered in `state.md` |

### Notes for the implementer (not blocking)

| # | Note |
|---|---|
| 1 | Spec D25 still describes `history.json` as `item`/`video`/`time`; the plan's "Corrections" table (wire shape `HistoryEntryResponse`, `media`) is the one to follow. Sync the spec row before the gate |
| 2 | Task 9 abbreviates the second `HttpStackTest` rename; the full name is `create_keepsTheAppEventListenerAndRequestsNoDohOne_whenResolverIsNull` (test-design AC-10 row) |
| 3 | Task 16 only allows route-table and rail-step fixes. Two D27 rules depend on device behaviour the plan cannot pin: `HttpEndpointProbe;->isReachable` is skipped when `isKnownReachable` (`ApiDomainInteractor.kt:176`) already trusts the pin, and the tvprovider rules need the emulator launcher to accept the publish. If either stays at 0, report it as blocked; do not loosen the guard |
