# hermetic-baseline-generation device and static evidence, round 2

Date: 2026-11-26. Worktree head: 3e79f660 (fix round 7d639a9b..3e79f660). Base 5cd3e37e.

## Build and device

- Dev debug APK: app-dev-debug.apk, sha256 93d5a372e817f061f506f3162e7077b9d1c09413a7fea5b856e30f83a78950cb, version 1.12.8-dev.
- Box 192.168.1.110:5555 (com.kino.puber.stage only), leased and released. Generation evidence (AC-1..AC-5) is read from the implementer's emulator run (host.docker.internal:5555, API 36, logcat tag PuberGen, results XML in baselineprofile/build/outputs/androidTest-results); generation was not repeated.

## Static gate

- check.full extended: :app:testDevDebugUnitTest 2726 tests, 0 failures, 0 skipped (re-run with --rerun); :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*' 19 tests, 0 failures; detektAll, detektInstrumentationDebug, lintDevDebug, lintInstrumentationDebug pass. A first attempt with --rerun-tasks failed with a Kotlin compiler OutOfMemoryError in :app:compileDevDebugKotlin (full recompile in the 2 GiB daemon); the normal run passed.

## AC-1, AC-2 (generation run, implementer's Task 16)

- Results XML: 17 tests, 0 failures, 0 errors, 2 skipped (StartupBenchmarks, not part of the ACs). BaselineProfileGenerator startupProfile, browseAndDetailsProfile, railNavigationProfile, playbackProfile pass; BaselineMockSelfTest 11/11.
- Logcat 20261126-162655: 35 iteration lines, each missing=[] unknown=[]; the VERIFY journal broadcast returned result=-1 data="" every time (empty egress journal); every journey ends with a late check (unknown=[], empty journal). Round-3 routes present in every iteration: POST /dns-query, GET /v1/history, GET /github/.../releases/latest. 9 Playback iterations, each with stream.m3u8, seg*.ts (6) and watching/marktime. No FATAL lines.
- baseline-prof.txt: 38646 method rules, 0 without H. startup-prof.txt: 24656 method rules keep S/P as at base.

## AC-4, AC-5

- 0 rules containing com/kino/puber/profile/ or baselineprofile/ in both files; BaselineProfileContentTest (in the 2726) passes.
- Rule lines per package, 5cd3e37e -> now: coil3/network/ 158 -> 221, coil3/decode/ 83 -> 148, androidx/media3/exoplayer/hls/ 376 -> 387, okhttp3/internal/tls/ 30 -> 32, okhttp3/ 2071 -> 1931 (-6.8 %).
- Three-segment packages with 40 or more base rules that dropped over 20 %: com/adamglin/phosphoricons 69 -> 51, kotlinx/serialization/descriptors 73 -> 51, kotlinx/serialization/internal 197 -> 140, okhttp3/internal/http1 51 -> 0 (the mock answers over HTTP/2).

## AC-6 (fresh release builds, one assemble per invocation)

| Check | prod release | dev release | control: instrumentation APK |
|---|---|---|---|
| assemble exit | 0 | 0 | n/a (build of 16:25) |
| assets/dexopt/baseline.prof (+ .profm) | present (30750 B) | present (30750 B) | n/a |
| mapping.txt hits for com.kino.puber.profile, baselineprofile, blocker, app, mock CA | 0 | 0 | n/a |
| dex strings: BaselineProfileApp, BaselineNetworkBlocker, baseline.test, BaselineProfileControl, BEGIN CERTIFICATE | 0 | 0 | 40 (names) |
| manifest (apkanalyzer): BaselineProfile or ControlReceiver | 0 | 0 | 2 |

The loopback CA is a raw resource only under app/src/instrumentation/res/raw.

## AC-8

- No CLIENT_SECRET, pair, PuberAuth or profile.auth in tools/generate-baseline-profile.sh, baselineprofile/src/main/java, the Makefile baseline-profile target, or the release.md profile section (the remaining hits are the CI secret list at release.md:61 and the auth-save/auth-restore targets). app/src/nonMinifiedRelease, devNonMinifiedRelease and prodNonMinifiedRelease do not exist.

## AC-9 (box)

1. make DEVICE=192.168.1.110:5555 auth-save: saved 3173376 bytes (databases, files, shared_prefs).
2. tools/android-apk-install-preserve: outcome installed, classification compatible_replace, same signer 6b29181257cb..., destructive_action false.
3. On-device sha256 of base.apk equals the APK sha256 above.
4. Crash buffer cleared, HOME, force-stop, one cold launch (am start -W: LaunchState COLD, 9.7 s): resumed MainActivity, Home with hero, "continue watching" and "new" rows, logged in, no pairing screen.
5. HOME and relaunch (LaunchState HOT, task brought to front): MainActivity resumed on Home, same state.
6. logcat -b crash: 0 lines after both launches.
- No Retry, no further launches. The screenshots were viewed and not saved because they show account content. The debug APK carries no embedded profile; the profile launch is a release.md step.
