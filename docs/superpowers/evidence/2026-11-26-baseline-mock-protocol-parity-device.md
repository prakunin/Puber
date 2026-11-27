# baseline-mock-protocol-parity: device and count evidence (round 2)

Date: 2026-11-27. Branch head 8bda22ae. Base 08f50b81. Real-account profile 5cd3e37e.

## Device and runs

- Device: emulator host.docker.internal:5555 (sdk_google_atv64_arm64, API 36 AVD puber_tv_36), under lease. Generation reached only the loopback mock.
- Generation: implementer run 4, logcat run id 20261127-063806, results written 2026-11-27 06:58:20, test-result-exit-code 0. Profiles committed in f4086c71 and byte-identical to the working tree at 8bda22ae (cmp).
- QA did not re-run generation; every AC below was judged from that run and from check.full re-run at 8bda22ae (exit 0).
- Results: 26 tests, 0 failures, 0 errors, 2 skipped (StartupBenchmarks startupWithProfile, startupWithoutProfile; assumption not met, not part of generation).
- BaselineMockSelfTest: 20 of 20 passed, including apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2, jsonRouteOnH1_isChunked_mediaRouteIsFixedLength, everyResponse_carriesDateHeader, routeOfOtherServer_returns404AndIsListedAsUnknown, close_stopsBothServers, seriesRoute_servesSeriesInBrowse_movieInPlayback, browseScenario_requiresItemFoldersRoute, browseScenario_requiresSeriesRoute, lateCheck_fails_whenEgressJournalNotEmpty, journeyCheck_fails_whenEgressJournalNotEmpty.
- BaselineProfileGenerator: startupProfile, browseAndDetailsProfile, railNavigationProfile, playbackProfile all passed.

## Journeys, unknown and missing

| Journey | Iterations | unknown=[] | missing | Late check after |
|---|---|---|---|---|
| Rail | 5 | 5 of 5 | none (run exit 0) | unknown=[] |
| BrowseAndDetails | 11 | 11 of 11 | none (run exit 0) | unknown=[] |
| Startup | 10 | 10 of 10 | none (run exit 0) | unknown=[] |
| Playback | 15 | 15 of 15 | none (run exit 0) | unknown=[] |

Logcat: 41 iteration lines and 4 late lines carry unknown=[] (45 matches); no other unknown value. 26 GET /v1/bookmarks/get-item-folders requests matched, so the folder lookup path is exercised. missing=[] is enforced by JourneyCheck (a missing required route fails the journey; self-test journeyCheck_fails_whenRequiredRouteMissing passed) and the run exited 0. The egress journal is not printed on success; JourneyCheck and the late check assert it empty (self-tests journeyCheck_fails_whenEgressJournalNotEmpty and lateCheck_fails_whenEgressJournalNotEmpty passed, run exit 0).

## Acceptance criteria

| AC | Status | Evidence |
|---|---|---|
| AC-1 | PASS | BaselineMockSelfTest.apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2 passed on the emulator |
| AC-2 | PASS | jsonRouteOnH1_isChunked_mediaRouteIsFixedLength and everyResponse_carriesDateHeader passed |
| AC-3 | PASS | check.full: BaselineInstrumentationEnvironmentTest 3 and BaselineNetworkBlockerTest 10 tests, 0 failures |
| AC-4 | PASS | check.full: BaselineFixtureContentTest 16 tests, 0 failures ("itemFixture_carriesExplicitNull()" "serialFixture_decodesAsSeriesWithTwoEpisodesOnMockOrigin()" "bookmarkFoldersFixture_listsTheWatchLaterFolder_soTheLookupFillsTheBookmarkIcon()" "historyFixture_carriesOneUnfinishedMovieWithHttpsWidePoster()" "movieFixtures_carryPostersAndHlsStream()" "itemFixtures_carryBooleanFloatAndDoubleValues()" "fixtures_haveNoUrlOutsideMockOrigin()" "fixtures_haveNoImdbOrTrailerKeys_seasonsAndSubtitlesOnlyInSerialFixture()" "itemFixtures_decodeAsAppItems()" "itemFixtures_carryUnmodelledKey()" "movieFixture_carriesNoTrackData()" "itemFixture_isUnwatchedAndUnbookmarked_soDetailsAndPlaybackTakeTheFolderLookup()" "detailsFixtures_carryGenresCountriesAndTracklist()" "itemFixtures_carryWatchedAndBookmarkedMovie()" "itemsFixture_secondEntryIsSeries()" "githubRelease_isOlderThanAnyBuild()") |
| AC-5 | PASS | exit code 0, four journeys passed, unknown=[] on every iteration and late check, egress asserted empty by the harness |
| AC-6 | PASS | BaselineProfileContentTest 9 tests, 0 failures, including baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons and baselineProfileCoversBookmarkFolderLookup |
| AC-7 | PASS | Perf table below |
| AC-8 | PASS | git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res is empty |

AC-6 rule lines now (grep -c on baseline-prof.txt): Http1ExchangeCodec 50, Http2Connection 80, BooleanSerializer 8, FloatSerializer 8, DoubleSerializer 8, fill/EyeKt 3, fill/BookmarksimpleKt 3, getItemBookmarkFolders 8, BookmarkFoldersResponse 21.

## Perf: rule lines per prefix

Method: grep -c 'L<prefix>/' on app/src/main/generated/baselineProfiles/baseline-prof.txt (phosphor: grep -c 'Lcom/adamglin/phosphoricons/'). Columns: base 08f50b81, real-account 5cd3e37e, now (8bda22ae). One generation, no repeats.

| Prefix | base | real-account | now | Threshold | Gating | Result |
|---|---|---|---|---|---|---|
| okhttp3/internal/http1 | 0 | 51 | 54 | >= 40 | yes | pass |
| kotlinx/serialization/internal | 165 | 234 | 237 | >= 188 | yes | pass |
| kotlinx/serialization/descriptors | 137 | 203 | 205 | >= 163 | yes | pass |
| Lcom/adamglin/phosphoricons/ | 51 | 69 | 63 | >= 56 | yes | pass |
| okhttp3/internal/http2 | 292 | 316 | 284 | >= 253 | no, reported | pass |
| okhttp3/internal/tls | 32 | 30 | 32 | >= 24 | no, reported | pass |

## Player counts (non-gating)

| Prefix | base | real-account | now |
|---|---|---|---|
| androidx/media3/exoplayer | 3490 | 3203 | 3313 |
| androidx/media3/exoplayer/hls | 387 | 376 | 348 |
| AudioTrackPreferenceResolver | 57 | 46 | 48 |

These are reported, not gating. exoplayer and AudioTrackPreferenceResolver are below base (3313 vs 3490, 48 vs 57) and hls is below base and below the real-account profile (348 vs 387 and 376). The drop was accepted by the owner at review round 1; the investigation is deferred to the run baseline-player-track-coverage.
