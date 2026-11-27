Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh -> pass (exit 0; tooling script all PASS)
DEVICE: not used by me. No app code changed (git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res is empty), so no TV box check. Generation evidence taken from the implementer's emulator run (host.docker.internal:5555, sdk_google_atv64_arm64, API 16 in the result file name, results written 2026-11-27 05:42, logcat 20261127-052845). I did not re-run generation.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | results XML: BaselineMockSelfTest 19 tests, 0 failures, incl. apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2 |
| AC-2 | PASS | jsonRouteOnH1_isChunked_mediaRouteIsFixedLength, everyResponse_carriesDateHeader passed |
| AC-3 | PASS | check.full: com.kino.puber.profile.* unit tests (EnvironmentTest, NetworkBlockerTest) green |
| AC-4 | PASS | BaselineFixtureContentTest in check.full green; serial fixture item_serial.json guarded by validate_allowsSeasonsAndSubtitlesOnlyInSerialFixture (self-test passed) |
| AC-5 | PASS (egress indirect) | results: 25 tests, 0 failures, 2 skipped (StartupBenchmarks startupWith/WithoutProfile, assumption not met, not part of generation); all four journeys passed; logcat has 32 'unknown=[]' and no other unknown value; late checks after Rail, BrowseAndDetails, Startup, Playback all unknown=[]; exit-code file 0. Egress journal is not printed on success: it is asserted empty by JourneyCheck (self-tests lateCheck/journeyCheck_fails_whenEgressJournalNotEmpty pass) and the run exited 0, so non-empty would have failed it |
| AC-6 | PASS | BaselineProfileContentTest (check.full) green; rule lines now: Http1ExchangeCodec 50, Http2Connection 80, BooleanSerializer 8, FloatSerializer 8, DoubleSerializer 8, fill/EyeKt 3, fill/BookmarksimpleKt 3 |
| AC-7 | PASS | counts below, recounted by me on committed baseline-prof.txt |
| AC-8 | PASS | git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res: 0 files |
Series delta (Q4, descriptors >= 163): PASS, 203 vs 5cd3e37e 203; series route and fixture tests passed (seriesRoute_servesSeriesInBrowse_movieInPlayback, browseScenario_requiresSeriesRoute, close_stopsBothServers).

Perf (grep -c 'L<prefix>/' on baseline-prof.txt, same method as baseline):
| Prefix | 08f50b81 | 5cd3e37e | now | Threshold | Result |
|---|---|---|---|---|---|
| okhttp3/internal/http1 | 0 | 51 | 54 | >= 40 | pass |
| kotlinx/serialization/internal | 165 | 234 | 235 | >= 188 | pass |
| kotlinx/serialization/descriptors | 137 | 203 | 203 | >= 163 | pass |
| Lcom/adamglin/phosphoricons/ | 51 | 69 | 57 | >= 56 | pass (margin 1) |
| okhttp3/internal/http2 (info) | 292 | 316 | 284 | >= 253 | pass |
| okhttp3/internal/tls (info) | 32 | 30 | 32 | >= 24 | pass |
(Counts use a plain substring of the prefix; same figures as the qa-baseline method for these rows. Device and build: the implementer's emulator run, one generation, no repeats.)

TESTS ADDED: none
EVIDENCE: n/a for me (no device used by qa). docs/superpowers/evidence has no file for this slug yet; the implementer's emulator run (Task 7) needs one, run through tools/mobile-evidence-audit.sh, before the final gate.
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Notes: phosphoricons margin is thin (57 vs 56); egress journal proven only through the exit code and the self-tests, not printed.

---

Round 2

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug && bash tools/tests/test-baseline-profile-tooling.sh -> pass (exit 0, tooling script all PASS)
DEVICE: emulator host.docker.internal:5555 for the implementer's run 4 (logcat 20261127-063806, results 2026-11-27 06:58:20, exit code 0); I did not re-run generation or touch a device. TV box not needed: git diff --name-only 08f50b81 -- app/src/main/java app/src/main/res is empty.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | BaselineMockSelfTest 20/20 passed (apiHost_negotiatesHttp11_dnsAndGithub_negotiateHttp2) |
| AC-2 | PASS | jsonRouteOnH1_isChunked_mediaRouteIsFixedLength, everyResponse_carriesDateHeader passed |
| AC-3 | PASS | check.full: EnvironmentTest 3, NetworkBlockerTest 10, 0 failures |
| AC-4 | PASS | check.full: BaselineFixtureContentTest 16 tests, 0 failures (amended AC: plain movie, folder lookup, items.json id 3) |
| AC-5 | PASS | exit 0; 4 journeys passed; 41 iteration lines and 4 late checks (Rail, BrowseAndDetails, Startup, Playback) all unknown=[]; 26 get-item-folders hits; egress asserted empty by JourneyCheck (not printed on success) |
| AC-6 | PASS | BaselineProfileContentTest 9/9 incl. baselineProfileCoversHttp1CodecSerializerPrimitivesAndFilledIcons and baselineProfileCoversBookmarkFolderLookup; lines: Http1ExchangeCodec 50, Http2Connection 80, Boolean/Float/DoubleSerializer 8 each, fill/EyeKt 3, fill/BookmarksimpleKt 3, getItemBookmarkFolders 8, BookmarkFoldersResponse 21 |
| AC-7 | PASS | Perf table below, recounted by me; committed profile equals the generated one |
| AC-8 | PASS | diff empty |

Perf (grep -c 'L<prefix>/', baseline-prof.txt):
| Prefix | 08f50b81 | 5cd3e37e | now | Threshold | Result |
|---|---|---|---|---|---|
| okhttp3/internal/http1 | 0 | 51 | 54 | >= 40 | pass |
| kotlinx/serialization/internal | 165 | 234 | 237 | >= 188 | pass |
| kotlinx/serialization/descriptors | 137 | 203 | 205 | >= 163 | pass |
| Lcom/adamglin/phosphoricons/ | 51 | 69 | 63 | >= 56 | pass |
| okhttp3/internal/http2 (info) | 292 | 316 | 284 | >= 253 | pass |
| okhttp3/internal/tls (info) | 32 | 30 | 32 | >= 24 | pass |
Player counts (non-gating, deferred to baseline-player-track-coverage): media3/exoplayer 3490 / 3203 / 3313, exoplayer/hls 387 / 376 / 348, AudioTrackPreferenceResolver 57 / 46 / 48 (base / real-account / now).

TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/baseline-mock-protocol-parity/docs/superpowers/evidence/2026-11-26-baseline-mock-protocol-parity-device.md (audit passed, committed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
