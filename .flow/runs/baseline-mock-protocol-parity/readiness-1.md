# Readiness 1: baseline-mock-protocol-parity

Verdict: **NOT READY**

Spec: `docs/superpowers/specs/2026-11-26-baseline-mock-protocol-parity.md`; plan: `docs/superpowers/plans/2026-11-26-baseline-mock-protocol-parity.md` (worktree, uncommitted). Base 08f50b81.

## AC coverage

| AC | Plan task | Test design row |
|---|---|---|
| AC-1 | 1 | `apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2`, `routeOfOtherServer_returns404AndIsListedAsUnknown` |
| AC-2 | 1 | `jsonRouteOnH1_isChunked_mediaRouteIsFixedLength`, `everyResponse_carriesDateHeader` |
| AC-3 | 2 | `configure_pinsApiToH1PortAndDohToH2Port`, `allowedOrigins_listsEachServedOriginOnItsServerPort`, `select_returnsDeadProxyAndJournals_whenApiOnH2OrDnsOnH1`, Manual 1 |
| AC-4 | 3 | `itemFixtures_decodeAsAppItems`, `itemFixtures_carryWatchedAndBookmarkedMovie`, `itemFixtures_carryBooleanFloatAndDoubleValues`, existing fixture guards |
| AC-5 | 4 | Manual 1 |
| AC-6 | 4 | `baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons` |
| AC-7 | 4 | Manual 2, Perf |
| AC-8 | 4 | Manual 3 |

## Citations sampled

| Citation | Claim | Result |
|---|---|---|
| `BaselineMockBackend.kt:21-25`, `:27`, `:36-37` | constructor, `server`, `boundPort` | resolves |
| `BaselineMockBackend.kt:96-106`, `:153-158`, `:213-225` | dispatcher, item/github/dns routes, response builders | resolves |
| `BaselineMockBackend.kt:264-277`, `:328` | `BaselineMockRoute`, banned `seasons` key | resolves |
| `BaselineProfileTestControl.kt:18-31`, `:88-104`, `:118` | `createBackend`, `awaitQuiescence`, `ARG_MOCK_PORT` | resolves |
| `gradle.properties:5`, `app/build.gradle.kts:246`, `baselineprofile/build.gradle.kts:23-24` | port property, BuildConfig field, runner argument | resolves |
| `BaselineInstrumentationEnvironment.kt:17`, `:22-26`, `:45`; `BaselineProfileApp.kt:26` | origins, DoH URL, GitHub base | resolves |
| `BaselineNetworkBlockerTest.kt:17-21`, `:49`, `:91` | allowed set, 18766 as "differs" port | resolves |
| `Models.kt:62-64`, `:72`, `:81`, `:85`, `:87`, `:95`, `:164-166`, `:209-212`, `:283`, `:346-351` | Item booleans, bookmarks, watched, `fps: Float?`, `isFullyWatched`, `Duration.average: Double?`, `Video.watched`, `Bookmark` | resolves |
| `DetailsInteractor.kt:51`, `KinoPubApiClient.kt:133-137`, `:146` | movie bookmark rule, lenient Json, OkHttp `Cache` | resolves |
| `DetailsScreenContent.kt:530`, `:550`; `PlayerButtonRow.kt:141-145`; `PlayerInteractor.kt:133-137` | `Fill.BookmarkSimple`, `Fill.Eye`, player watched icon and source | resolves |
| `BaselineProfileGenerator.kt:177-180`, `:216-218` | player check, `createBackend` call | resolves |
| `BaselineProfileContentTest.kt:19`, `:60-68`; `docs/release.md:121-126` | `hasRuleFor`, image/TLS/HLS test, mock port text | resolves |

## Order, build, scope

- Order 1 -> 2 -> 3 -> 4 follows dependencies: Task 2 consumes `BuildConfig.BASELINE_MOCK_H2_PORT` from Task 1; Task 4 needs 1-3.
- Each task compiles on its own: Task 1 changes the backend constructor and its only callers (`BaselineProfileTestControl`, `BaselineMockSelfTest`) together; the generator call at `BaselineProfileGenerator.kt:217` is unchanged. Between Tasks 1 and 2 the app still points DoH at h1, but no device run happens until Task 4.
- `tools/generate-baseline-profile.sh` drives `make ... baseline-profile` (line 92) through `tools/agentw` (line 21), so Task 4 and Manual 1 run the same route; device is the emulator only, loopback mock only.
- No `app/src/main/java` or `app/src/main/res` file in any task; no `imdb`/`trailer` fixture. Q1-Q3 answered; none blocks.
- Every P0 row names its mutation, is marked `red` or `guard` (device rows are manual checks), and names its test in backticks.

## Fix list

1. Perf baseline for `com/adamglin/phosphoricons` contradicts its own method. With the stated method `grep -c 'Lcom/adamglin/phosphoricons/'` the committed profiles give 08f50b81 = 51 and 5cd3e37e = 69 (rerun here); the Perf table says 54 / 72, which is qa-baseline.md's broader `Lcom/adamglin/` count. Correct the spec Perf row to 51 / 69 and the threshold to `ceil(0.8 x 69)` = 56, and the same threshold in plan Task 4 (`phosphoricons >= 58` -> `>= 56`). As written, a result of 56 or 57 meets AC-7 but would be recorded as Blocked.

## Notes (no fix needed)

- The four new `BaselineMockSelfTest` P0 `red` rows cannot get a recorded red run: they run only on the device in Task 4 and reference `boundH2Port`, which does not exist at base. The plan records red runs for the unit and unit-instr rows only; that is the available evidence.
