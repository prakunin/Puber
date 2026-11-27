# Gate - baseline-mock-protocol-parity - round 1

**Decision:** CONCERNS
**Head:** b2a8c42f   **Base:** 08f50b81

## Done as asked

The diff splits the loopback mock into an HTTP/1.1 server (:18765, API, images, stream, chunked JSON) and an HTTP/2 server (:18766, DoH, `/github`), adds `Date` to every response, enriches the fixtures (primitives, explicit null, watched list item id 3, series `item_serial.json` for id 2 outside Playback, watch-later folder lookup), points DoH and the update check at the H2 port, and regenerates both profiles. No `app/src/main/java` or `app/src/main/res` file changed (`git diff --name-only 08f50b81...HEAD` empty). Fixtures keep no `imdb`/`trailer` key (`BaselineMockBackend.kt` `BANNED_KEY`). Outside the brief's file list: `BaselineProfileGenerator.kt` gains a 1.5 s rest before OK in Browse (ac31a23c, fix round 1, logged in state.md).

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `BaselineMockSelfTest.apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2` | results XML `TEST-sdk_google_atv64_arm64 - 16.xml` 06:58:20: suite 20/20, 0 failures (re-read) | PASS |
| AC-2 | `jsonRouteOnH1_isChunked_mediaRouteIsFixedLength`, `everyResponse_carriesDateHeader` (media and DoH included) | same XML | PASS |
| AC-3 | `BaselineInstrumentationEnvironmentTest`, `BaselineNetworkBlockerTest` | qa-report round 2 check.full exit 0 at 8bda22ae (code equal to head) | PASS |
| AC-4 | `BaselineFixtureContentTest` (16) | qa-report round 2; fixtures read in the diff: `advert` bool, `fps` 23.976, `duration.average` 24.5, `age_rating: null`, `item.json` `bookmarks: []`, `in_watchlist: false` | PASS |
| AC-5 | full generation on emulator host.docker.internal:5555 | XML: 26 tests, 0 failures, 2 skipped (StartupBenchmarks); evidence file journey table, unknown=[] on 41 iterations and 4 late checks; egress asserted by JourneyCheck | PASS |
| AC-6 | `BaselineProfileContentTest` incl. `baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons`, `baselineProfileCoversBookmarkFolderLookup` | recounted on committed `baseline-prof.txt`: Http1ExchangeCodec 50, Http2Connection 80, Boolean/Float/DoubleSerializer 8, fill/EyeKt 3, fill/BookmarksimpleKt 3, `getItemBookmarkFolders` 1, BookmarkFoldersResponse 21 | PASS |
| AC-7 | rule-line count vs 5cd3e37e | recounted: http1 54 (>= 40), serialization/internal 237 (>= 188), descriptors 205 (>= 163), phosphoricons 63 (>= 56); evidence file Perf table | PASS |
| AC-8 | `git diff --name-only` | empty (re-run) | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Folder lookup left the profile | blind-1 F1 | fixed | c8d70d90, 9d76588b; guard `baselineProfileCoversBookmarkFolderLookup`; route required in Browse |
| Player coverage below base | blind-1 unproven, triage-1 #2 | fixed in part, rest deferred | watched/bookmarked moved off `item.json` (9d76588b); new run `baseline-player-track-coverage` (stage idea) |
| No evidence file for AC-5/AC-7 | tests-1 F1, edge-1 F1, tests-2 F1 | fixed | b2a8c42f, audit passed (re-run: `evidence_audit_status=passed`) |
| Date not asserted on binary/DoH | codex-1, tests-1 F2 | fixed | 9d76588b (`everyResponse_carriesDateHeader` covers poster.jpg and DoH) |
| Chunking decided outside the route's server | blind-1 F2 | fixed | `chunkedBodies()` reads `dispatching`, set inside the dispatcher lock |
| Http2Connection passes on base | blind-1 F3 | fixed | guard comment in `BaselineProfileContentTest` |
| P0 red self-tests without red run | tests-1 F3 | fixed | d4f81e41 Implementation record (device-only self-tests recorded as red by construction) |
| Wrong host on right port, close() lock, h2 bind branch, runner arg, equal ports, `subtitle.vtt` route, phosphor margin | codex-1, edge-1, tests-1 | rejected, accepted | spot-checked: `subtitle.vtt` would surface as unknown and fail the run, not a silent gap; Playback serves the movie (`if (!playback)`); margin now 63 vs 56 |
| `dispatching` race | codex-2 | rejected, accepted | spot-checked: written and read inside `synchronized(lock)` in `routeDispatcher` |
| Browse required `items/2` relies on a fixed 1.5 s sleep | edge-2 F1, codex-2, tests-2 F3 | accepted minor | fails loudly (`missing=[...]`), never commits a bad profile |
| Playback movie shows bookmarked via folder lookup | codex-2 | accepted minor | icon state only; KDoc in `routesFor` still says the Playback movie is "never ... bookmarked" |
| Folder fixture test checks non-empty only; untraced `browseScenario_requiresItemFoldersRoute`; stale test name `itemFixtures_carryWatchedAndBookmarkedMovie` | edge-2 F2/F3, tests-2 F2/F4 | accepted minor/nit | listed in triage-2 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Gating counts met (AC-7). Player rules below base 08f50b81: media3/exoplayer 3313 vs 3490, exoplayer/hls 348 vs 387 (also below real-account 376), AudioTrackPreferenceResolver 48 vs 57; non-gating, deferred to `baseline-player-track-coverage`. |
| Reliability | Generation depends on a 1.5 s timer for one required route (accepted minor); failure is loud. h2 bind failure closes h1; `close()` closes both in `finally`. |
| Maintainability | Production code untouched; ports from Gradle properties, no new 18765 literals in tests; `empty_bookmark_folders.json` removed with no remaining reference; `docs/release.md` updated. KDoc claim in `routesFor` contradicts codex-2 finding (minor). Branch carries 20 commits; owner rule asks for fewer than 5 before ship. |
| Security / privacy | Fixtures loopback-only (`fixtures_haveNoUrlOutsideMockOrigin`), no `baseline.test` or generator classes in the profile (0 lines), evidence audit passed, no tokens or account data in the diff. |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

1. Player-path coverage regresses against base: exoplayer/hls 387 -> 348 (below the real-account 376 too), AudioTrackPreferenceResolver 57 -> 48. Accepted under the standing instruction and deferred to `baseline-player-track-coverage`; shipping this profile lands that drop until that run lands.
2. Browse generation relies on a fixed 1.5 s rest (`BaselineProfileGenerator.kt`, `NEIGHBOUR_DWELL_WAIT_MS`) for the required `GET /v1/items/2`; a slow emulator fails the run with exit 2 and needs a manual rerun.
