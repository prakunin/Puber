# Device evidence: cache-decoded-memory-tier (2026-11-22, verify round 1)

Build: worktree HEAD 4d9a50e0 (base e9c383d0), `./tools/agentw :app:assembleDevDebug` (AGENT_GRADLE_SERIAL=1), dev debug 1.12.3-dev. APK sha256 9dc153a2067fd633d76076b9c10ccccfc63e7cfe60496387bd853f6e0ea58d67, equal on device after install-preserve and again after `make itest` re-installed it.
Device: emulator-5554 (AVD puber_tv_36, API 36 TV, software GPU), lease held and released. Existing pairing; no uninstall, data clear, downgrade or signer change. No account mutation (see Manual check 2).
Install: `tools/android-apk-install-preserve install-preserve` outcome `installed`, classification `compatible_replace`, destructive_action false, signer sha256 unchanged (6b291812...c49a83e). Over the base build left by the baseline run (1.12.3-dev, same versionCode, base.apk from e9c383d0).

## AC-4 / Manual check 1 (upgrade over the base build, data kept)

Steps: on the base build force-stop, cold start, dismiss the update dialog ("Not now"), DPAD_DOWN to Continue watching, three times DPAD_CENTER (Details), 5 s, BACK, DPAD_RIGHT. Install-preserve the worktree APK. Force-stop, cold start, 15 s, dismiss the dialog, same three Details.
Expected: Home and Details render, still logged in, no crash.
Observed: base build: Home rows and three Details rendered. After the upgrade: Home rows (hero, Continue watching, bookmarks) and the same three Details rendered with seasons, watched badges and the "up next" action, no login screen. `logcat -b crash` empty and no SerializationException or FATAL line for the app after the upgrade. Result: PASS.

## AC-7 (instrumented)

`make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`: BUILD SUCCESSFUL, 10 tests, 0 failures, 0 skipped, including aServerRowEqualToTheStoredOneIsNotRewritten, aServerRowDifferingInAnyWrittenColumnLands, aServerRowReadBeforeTheStoredOneDoesNotLand, aPendingRowIsLeftAloneByASync. Not run against the base UPDATE on the device (red on base is the implementer's reading of the base WHERE). Result: PASS.

## AC-10 (perf), same method and emulator as baseline.md

Sequence: force-stop, cold start, 15 s, dismiss the update dialog (about 4 s, inside the opens window), D-pad into Continue watching, five times DPAD_CENTER, 5 s, BACK, 3 s, DPAD_RIGHT. Decode = `kotlinx.serialization.json.Json.decodeFromString` and the ktor `KotlinxSerializationConverter.deserialize`, ART method traces (`am start --start-profiler --streaming`, sampling 100 us for the whole sequence), parsed with the baseline's parser (thread 1 = main).

### Cold start to Home, exact count (full trace)
| Run | decodeFromString total | on Main | on workers | baseline (total / Main) |
|---|---|---|---|---|
| c1 | 66 | 0 | 66 | 165 / 66, 101 / 31 |
| c2 | 64 | 0 | 64 | |
Main thread recorded 4.8 M method events in c1, so the zero is not an empty trace. c1 ended with a native SIGSEGV in `art::Trace::StopTracing` (the profiler's own stop, ART libart, uptime 17 s) and its file has no end marker; c2 stopped cleanly and agrees (64 vs 66). Baseline noted the same instability of full tracing.

### Whole sequence (cold start + five Details opens), sampling, three runs
| Run | Main decode episodes, cold window | Main decode ms, cold | Main episodes, opens window | Main ms, opens | worker decodeFromString episodes (ms) | worker ktor converter episodes |
|---|---|---|---|---|---|---|
| s1 | 0 | 0 | 0 | 0 | 55 (3330) | 13 |
| s2 | 0 | 0 | 0 | 0 | 50 (3784) | 12 |
| s3 | 0 | 0 | 0 | 0 | 58 (3617) | 12 |
Baseline Main: cold 72 / 87 / 95 episodes (1763-2698 ms), opens 197 / 213 / 240 episodes (9321-10181 ms), ktor converter on Main 10-15 episodes in the opens window. After: 0 on Main in both windows, including the ktor converter (so the token-refresh and ApiFailures decodes noted in readiness-2 did not run on Main in this sequence; they were not exercised either, as no 401 or error response occurred). Trace length 68 s (baseline 81-86 s).

### App heap after the sequence (no profiler, three runs, dumpsys meminfo, KB)
| Run | Dalvik PSS | Dalvik Private Dirty | Dalvik Heap Size | Dalvik Heap Alloc | Java Heap (App Summary PSS) | Native Heap PSS |
|---|---|---|---|---|---|---|
| h1 | 38052 | 37896 | 30263 | 15132 | 48104 | 51443 |
| h2 | 26820 | 26664 | 30863 | 15432 | 36872 | 56635 |
| h3 | 22632 | 22476 | 30892 | 15446 | 32684 | 57251 |
Sampling runs (profiler on): Dalvik Private Dirty 31628, 19132, 17240; Heap Alloc 16041, 15235, 15315; Java Heap summary 41836, 29336, 27444.
Baseline recorded "Dalvik Heap Alloc" 26347 / 26219 / 26203 and Java Heap PSS summary 40648 / 52344 / 58728. The Alloc figure of the baseline does not match this device's Heap Alloc column (about 15 MB now, with no way to re-read the base build); it sits between Private Dirty and Heap Size. By the columns that can be compared, Java Heap PSS summary after (32684-48104, median 36872) overlaps the baseline (40648-58728, median 52344), and Dalvik Private Dirty (22476-37896, median 26664) overlaps 26.2-26.3 MB. No heap growth over the 25 % line is visible; the comparison is coarse because PSS moves with GC timing. Result: recorded.

## Manual checks 2 and 3
- Check 2 (mark an episode watched, BACK to Home within 5 s of a cold start): NOT CHECKED. It mutates the account's watch state, which needs explicit owner authorization. The deferred-clear ordering is covered by WatchStateRepositoryTest rows and WatchStateRepositoryModuleTest (AC-8).
- Check 3 (five opens of one Details): five Details opens and returns ran three times (h1-h3) and five times (s1-s3) without a crash and with focus returning to the Continue watching row each time (the next card opened each time). Watched badges were not compared pixel for pixel across opens.

Screenshots were looked at only for this review and are not stored.

## Round 2 (head c93c20c6): AC-7 red on the base DAO

Device: emulator-5554 (puber_tv_36), lease held and released. Production code unchanged since 803329d9.

Steps and observed:

1. Temporarily replaced `WatchStateDao.kt` with the e9c383d0 version (`git show e9c383d0:...`), uncommitted.
2. `make DEVICE=emulator-5554 GRADLE=./tools/agentw itest TESTS=com.kino.puber.data.db.WatchStateDaoTest`: BUILD FAILED, 10 tests, 1 failure.
   - `aServerRowEqualToTheStoredOneIsNotRewritten`: FAIL on base, `AssertionError: expected:<1> but was:<2>`. This is the test that proves the equal-row skip.
   - `aServerRowDifferingInAnyWrittenColumnLands`: PASS on base. Expected: the base writes every row unconditionally, so a differing row lands there too. The test guards the head against over-skipping; it cannot be red on base, so it is not a red-on-base proof.
   - The other 8 tests pass on base.
3. `git checkout` of the DAO (head restored, `git status` clean), same itest command: BUILD SUCCESSFUL, 10 tests, 0 failures, both AC-7 tests green.
4. Reinstalled the head dev debug APK with `tools/android-apk-install-preserve`: outcome installed, compatible_replace, signer unchanged. On-device base.apk sha256 9dc153a2...58d67 equals the worktree APK.

Unit tests (head): ContentCacheRepositoryTest 37 tests, KinoPubApiClientDecodeDispatcherTest 4 tests, 0 failures, 0 errors, 0 skipped (`--rerun`).

AC-7: PASS (equal-row test red on base, green on head; differing-row test green on both, by design).
