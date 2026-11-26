Round 2

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug -> pass (BUILD SUCCESSFUL; tests re-run with --rerun: dev 2726 tests, 0 failures; instrumentation unit 19 tests, 0 failures; detektAll and lint tasks ran or were up to date on unchanged inputs). A first attempt with --rerun-tasks hit a Kotlin compiler OutOfMemoryError (full recompile in the 2 GiB daemon); the normal run passed.
DEVICE: generation evidence read from the implementer's emulator run (host.docker.internal:5555, API 36), not repeated; AC-9 on the TV box 192.168.1.110:5555 (com.kino.puber.stage), leased and released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (note) | Results XML: 17 tests, 0 failures, 4 generator journeys pass, BaselineMockSelfTest 11/11, 2 StartupBenchmarks skipped. baseline-prof.txt 38646 method rules, 0 without H; no Dev/Prod generation tasks (Task 16 run, no .env). Note: startup-prof.txt keeps S/P flags as at base, as the amended AC says |
| AC-2 | PASS | 35 iterations, each missing=[] unknown=[], VERIFY journal empty every time, late check per journey clean; /dns-query, /v1/history, github releases/latest present in every iteration; 9 playback iterations each with stream.m3u8, 6 seg*.ts, watching/marktime; no FATAL |
| AC-3 | PASS | BaselineNetworkBlockerTest and BaselineInstrumentationEnvironmentTest in the 19 instrumentation unit tests (green); BaselineMockSelfTest 11/11 in the device results (journeyCheck and lateCheck fail on unknown route and non-empty journal) |
| AC-4 | PASS | BaselineProfileContentTest 7/7; 0 profile/baselineprofile rules in both files; prod cold-start guard holds |
| AC-5 | PASS | coil3/network 158->221, coil3/decode 83->148, exoplayer/hls 376->387, okhttp3/internal/tls 30->32; drops over 20 percent listed in evidence (phosphoricons, kotlinx/serialization, okhttp3/internal/http1 51->0) |
| AC-6 | PASS | Fresh assembleProdRelease and assembleDevRelease (one per invocation, both exit 0): baseline.prof present; 0 hits in mapping, dex strings and manifest; positive control on the instrumentation APK 40 dex and 2 manifest hits; CA is a raw resource only under app/src/instrumentation |
| AC-7 | PASS | extended check.full, see CHECK |
| AC-8 | PASS | Greps empty in script, generator, release.md profile section, Makefile baseline-profile target (other hits are the CI secret list and auth-save targets); all NonMinifiedRelease source sets absent |
| AC-9 | PASS (caveat) | Box: auth-save 3173376 B, install-preserve installed (compatible_replace, same signer), on-device sha256 equals APK, one cold launch (COLD) and HOME + relaunch (HOT) both on Home logged in, crash buffer 0 lines, no Retry or extra launches. Caveat: debug APK has no embedded profile; screenshots viewed, not saved (account content) |
| AC-10 | PASS | HttpStackTest 3, DnsOverHttpsFactoryTest 6, ApiModulePinnedEndpointTest 3, AppUpdateRepositoryTest 8, KinoPubApiClientGitHubReleaseTest 2, all green |
| AC-11 | PASS | ApiDomainInteractorPinnedEndpointTest 9/9 green |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-r2-device.md (committed 811fde6b; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Optional as before: a prod/dev release build on the box would exercise the embedded profile but needs a matching signer.

---

Round 1

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' :app:detektInstrumentationDebug :app:lintInstrumentationDebug -> pass (BUILD SUCCESSFUL; tests re-run with --rerun: dev 2714 tests, 0 failures; instrumentation unit 9 tests, 0 failures; BaselineProfileContentTest 6/6; :baselineprofile:compileInstrumentationNonMinifiedReleaseKotlin ok)
DEVICE: emulator host.docker.internal:5555 (implementer's run read, not repeated); TV box 192.168.1.110:5555 for M6, leased and released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (note) | Run results: 4 journeys passed, 11 tests 0 failures; dry-run without .env/secret has 0 Dev/Prod tasks; baseline-prof.txt 0 non-H method rules. Note: startup-prof.txt keeps S/P flags as at base (Makefile re-marks only baseline-prof); AC wording says both files |
| AC-2 | PASS | 37/37 iterations missing=[] unknown=[] empty journal; 15 playback iterations each with m3u8, 6 ts, watching/marktime |
| AC-3 | PASS | BaselineNetworkBlockerTest (unit, green) and BaselineMockSelfTest 5/5 in the device run results |
| AC-4 | PASS | BaselineProfileContentTest 6/6 incl. ban; 0 profile/baselineprofile rules in both files (20 at base) |
| AC-5 | PASS | Table in evidence; coil3/network 223, coil3/decode 148, exoplayer/hls 387, okhttp3/internal/tls 29; no drop over 20 percent (okhttp3/ -18.7 %) |
| AC-6 | PASS | Prod and dev release APKs have baseline.prof; no instrumentation classes, CA or receiver (dex, manifest, mapping greps; positive control 80 hits on the instrumentation APK) |
| AC-7 | PASS | check.full extended, see CHECK |
| AC-8 | PASS | Greps for CLIENT_SECRET, pair, PuberAuth, profile.auth empty in script, generator, release.md profile section, Makefile baseline-profile target; app/src/nonMinifiedRelease and both manifests absent |
| AC-9 | PASS (caveat) | Box: install-preserve installed (compatible_replace, same signer), on-device sha256 equals APK, cold launch and HOME + HOT relaunch both land on Home, logged in, crash buffer empty. Caveat: debug APK has no embedded profile; no master screenshot comparison was taken |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/hermetic-baseline-generation/docs/superpowers/evidence/2026-11-26-hermetic-baseline-generation-device.md (committed 7d639a9b; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Optional: a prod/dev release build on the box would exercise the embedded profile, but needs a signer matching the installed app, so I did not attempt it. Note a combined assembleProdRelease assembleDevRelease run exhausted the 2 GiB Gradle heap in R8; each alone succeeds.
