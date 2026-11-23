# Refresh the baseline profile and prove its effect on the TV box - plan

**Spec:** `docs/superpowers/specs/2026-11-23-release-startup-compilation.md`
**Goal:** Carry the ProfileVerifier status on the telemetry `startup` line, make generation dev-only and hook-compliant, regenerate both profiles for today's UI on emulator-5554, and document the refresh; the box A/B (AC-1, AC-4, AC-8) runs after Task 5.

<!-- owner: architect (tasks), implementer (Implementation record), final-judge via conductor (Readiness). -->

## Global constraints

- Fast gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:compileDevDebugKotlin`; full gate: `AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`. Both run in `/home/user1/orca/workspaces/Puber/release-startup-compilation`; never `./gradlew` there.
- Touched tests: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:testDevDebugUnitTest --tests '<fqcn>'` (one `--tests` per class).
- One task = green fast gate + green tests of the touched code + one commit; message is one imperative English sentence, no prefixes, never a `Co-Authored-By` line.
- The plan is self-contained: the implementer reads this file, the spec, and the files cited below, nothing else.
- Paths: main sources under `app/src/main/java/com/kino/puber/`, unit tests under `app/src/test/kotlin/com/kino/puber/`; both prefixes are dropped below. Line numbers are at base e4c3b5aa.
- Test names, red/guard status and checks M1-M8: spec section Test design (Traceability, Manual and device checks). A test named there is written with exactly that name. A P0/P1 `red` test is run before its production code and fails (not compiling on base counts as red); the Implementation record lists each red result.
- No app code calls `ProfileVerifier.writeProfileVerification` (spec D9): `rg -n writeProfileVerification app/src/main` returns nothing at every commit.
- Device work by the implementer is Task 5 only: emulator-5554, under a lease from `tools/emulator-resource-lock.sh`, every adb call with `-s emulator-5554`. Reinstall and data clear of `com.kino.puber.stage` on emulator-5554 are authorised for generation (owner, Q3); `com.kino.puber` is never installed or changed anywhere; the box `192.168.1.110:5555` is not touched by the implementer.
- Secrets: the main checkout's `.env` values `PUBER_CLIENT_SECRET`, `PUBER_API_DOMAIN`, `TMDB_READ_ACCESS_TOKEN` go into the environment of the single generation command only (Q-D2); nothing is written into the worktree, and no token, secret or raw content-query output goes into a commit, log excerpt or evidence file.
- Out of scope (spec, Out of scope): R8 rule narrowing, `coil-video`/`appcompat` removal, PackageInstaller `.dm` install.

---

## Tasks

### Task 1: Carry the ProfileVerifier status on the startup line

**Covers:** AC-5
**Files:**
- Modify: `core/telemetry/TelemetryEvent.kt:131-148` - append `val profileStatus: Int? = null` to `Startup` after `readyReqMs` (`:145`).
- Modify: `core/telemetry/TelemetryEncoder.kt:67-81` (`putStartup`) - `event.profileStatus?.let { put("prof", it) }`, raw code (no `procStartMs` subtraction).
- Modify: `core/telemetry/StartupMarks.kt`:
  - constructor (`:19-25`): add `private val awaitProfileStatus: Boolean = false` as the last parameter;
  - field `profileStatus = AtomicInteger(NOT_REACHED.toInt())` beside the atomics (`:26-38`);
  - `fun markProfileStatus(code: Int)`: first value wins (`compareAndSet`), then `scheduleCheck()` (`:92`); a status after `emit` writes nothing (`emitted`, `:38,103`);
  - `emitWhenComplete` (`:94-100`): `readyComplete` additionally requires `!awaitProfileStatus || profileStatus.get() != NOT_REACHED.toInt()`; the `FORCED` and `boundPassed` branches and `onTimeout` (`:90`) are unchanged;
  - `emit` (`:102-122`): `profileStatus = profileStatus.reached()` (`:126`).
- Create: `core/telemetry/ProfileStatusSource.kt` - `internal fun interface ProfileStatusSource { fun onStatus(callback: (Int) -> Unit) }` with `companion object { val None = ProfileStatusSource { } }`, and `internal object ProfileVerifierStatusSource : ProfileStatusSource`: `val future = ProfileVerifier.getCompilationStatusAsync()`; `future.addListener({ try { callback(future.get().profileInstallResultCode) } catch (e: ExecutionException) { Timber.w(e) } }, Runnable::run)`. No other `ProfileVerifier` call. `androidx.profileinstaller` is already an `implementation` dependency (`app/build.gradle.kts:435`); if `ListenableFuture` does not resolve on the compile classpath, stop and report under Blocked (no new dependency).
- Modify: `core/telemetry/TelemetryRuntime.kt`:
  - constructor (`:24-31`): add `private val profileStatus: ProfileStatusSource = ProfileStatusSource.None` as the last parameter;
  - `start` (`:42-56`): after `sink.schedule(STARTUP_FALLBACK_MS) { marks.onTimeout() }` (`:55`) add `profileStatus.onStatus { code -> sink.schedule(0) { marks.markProfileStatus(code) } }` (`TelemetrySink.schedule`, `core/telemetry/TelemetrySink.kt:128`). The off return at `:43` and the expiry return at `:50` stay above it;
  - `install` (`:127-172`): pass `profileStatus = ProfileVerifierStatusSource` to the `TelemetryRuntime(...)` call (`:152-159`) and `awaitProfileStatus = true` to `StartupMarks(...)` (`:163-169`).
- Test: `core/telemetry/StartupMarksTest.kt` - add `readyLineWaitsForProfileStatusWhenAwaited`, `boundEmitsWithoutProfileStatusWhenAwaited`, `forcedRevealEmitsWithoutProfileStatusWhenAwaited`, `profileStatusKeepsTheFirstValue`, `readyLineEmitsWithoutProfileStatusByDefault`. Fixture: the `queued`/`runQueued` pattern (`:13-26`) with a second instance built `awaitProfileStatus = true`; the default-constructor test uses `queuedMarks` (`:14-20`) as is.
- Test: `core/telemetry/TelemetryEncoderTest.kt` - add `startupWritesProfWhenKnown` (`Startup(2_000, 100, 150, 300, 900, profileStatus = 327680)` → `prof` = 327680). Guards unchanged and green: `everyEventTypeEmitsExactlyEnvelopePlusCatalogueFields` (`:75`, sample `:32-35` has no `prof`), `startupOmitsMarksNotReached` (`:211`).
- Test: `core/telemetry/TelemetryRuntimeTest.kt` - add `coldStartStartupLineCarriesProfileStatusFromSource`, `offStartNeverAsksProfileStatus`, `profileStatusReachesMarksOnlyThroughTheTelemetryExecutor`. A private fake `ProfileStatusSource` in the test file keeps the callback and counts `onStatus` calls; build the runtime as `build()` (`:45-48`) with it, and marks as `:246` with `awaitProfileStatus = true`. `sink.schedule(0)` lands in `ManualTelemetryExecutor`'s scheduled list (`util/ManualTelemetryExecutor.kt:21-24`), drained by `runScheduled()` (`:52-57`), not by `runAll()`; the P2 test asserts "no line" before `runScheduled()` of the listener task and `prof` after it. Existing cases `:243-284` stay unchanged and green.

**Consumes:** `TelemetrySink.schedule` (`TelemetrySink.kt:128`), `ManualTelemetryExecutor`, `FakeTelemetryClock`, `FakeSharedPreferences`, `FakeTelemetryStore`.
**Produces:** `Startup.profileStatus`, wire field `prof`, `StartupMarks(awaitProfileStatus)`, `StartupMarks.markProfileStatus(Int)`, `ProfileStatusSource`, `ProfileVerifierStatusSource`, `TelemetryRuntime(profileStatus)`.

- [x] Write the nine new tests; run the red ones (all except `readyLineEmitsWithoutProfileStatusByDefault` and the P2) red; record
- [x] Implement the event field, encoder branch, marks, source and runtime wiring
- [x] Fast gate; `StartupMarksTest`, `TelemetryEncoderTest`, `TelemetryRuntimeTest`, `TelemetryRuntimeProbesTest`; `:app:detektAll`; commit "Record the profile compilation status on the telemetry startup line"

**Done when:** the four classes and detekt pass, and `rg -n writeProfileVerification app/src/main` prints nothing.

---

### Task 2: Generator journeys for today's UI and a player test tag

**Covers:** AC-3 (journeys that produce the rules), D7, D8
**Files:**
- Modify: `ui/feature/player/component/PlayerScreen.kt:72-76` - pass `modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("player")` to `PlayerScreenContent`, which takes `modifier` (`PlayerScreenContent.kt:41`) and applies it to its root `Box` (`:51-55`). Precedent: `ui/feature/history/component/HistoryScreenContent.kt:157`. No other UI change.
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt`:
  - `startupProfile` (`:24-33`): after `device.waitForIdle()` sleep past the splash reveal cap (`REVEAL_CAP_MS = 2_500L`, `core/startup/RevealGate.kt:9`; a local `3_000L` constant citing it), then `waitForIdle()`. Keeps `includeInStartupProfile = true` (`:27`), the only journey that does;
  - `browseAndDetailsProfile` (`:35-75`): key path unchanged; comments `:44-45` rewritten for the side rail (no TopTabs);
  - replace `tabNavigationProfile` (`:77-122`) with `railNavigationProfile`: seed, start, settle as `:81-84`; LEFT opens the rail (`ModalNavigationDrawer`, `ui/feature/main/component/MainScreenComponent.kt:121`); for each rail entry DOWN then OK, `waitForIdle()` + 1 s, LEFT to reopen the rail; finally back to Home and RIGHT into content, DOWN ×5;
  - new `playbackProfile`: seed, start, settle; DOWN into Home content, OK → Details (`ui/feature/details/component/DetailsScreen.kt:27`), wait 2 s; OK on the initially focused Play action (`DetailsVM.openPlayer`, `ui/feature/details/vm/DetailsVM.kt:532-540`); `check(device.wait(Until.hasObject(By.res("player")), 15_000))` with a message naming the player; hold 5 s; BACK twice with `waitForIdle()` after each;
  - imports `androidx.test.uiautomator.By` and `Until` (`libs.androidx.uiautomator` is on the classpath, `baselineprofile/build.gradle.kts:44`). `seedAuthState` (`:124-144`) unchanged and called first in every journey.

**Consumes:** nothing from Task 1.
**Produces:** journeys `startupProfile`, `browseAndDetailsProfile`, `railNavigationProfile`, `playbackProfile`; resource id `player`.

- [x] Add the test tag; rewrite the journeys
- [x] Fast gate and `AGENT_GRADLE_SERIAL=1 ./tools/agentw :baselineprofile:compileDevNonMinifiedReleaseKotlin`; commit "Drive the baseline profile journeys through the side rail, details and the player"

**Done when:** both compile tasks pass and `rg -n tabNavigationProfile baselineprofile` prints nothing.

---

### Task 3: Dev-only, single-device, hook-compliant generation

**Covers:** AC-2 (static part), D3-D6
**Files:**
- Modify: `app/build.gradle.kts:436` - `baselineProfile(project(":baselineprofile"))` → `"devBaselineProfile"(project(":baselineprofile"))`. `baselineProfile { mergeIntoMain = true; saveInSrc = true }` (`:439-446`) unchanged. If the configuration does not resolve, use the fallback of spec Alternatives rejected (disable the prod variant in `baselineprofile/build.gradle.kts`) and record it under Deviations.
- Modify: `Makefile`:
  - `help` (`:39-57`): line `make baseline-profile  regenerate baseline profiles on \$$DEVICE only`;
  - `.PHONY` (`:126`): add `baseline-profile`;
  - target `baseline-profile: require-device` after `itest` (`:128-136`), same private server (`ITEST_ADB`, `:123-124`): start-server, disconnect, connect `$(DEVICE)`, print `baseline profile run sees:` and the device list, then `ANDROID_ADB_SERVER_PORT=$(ITEST_ADB_PORT) $(GRADLE) :app:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true $(PROFILE_ARGS)`, kill-server, exit with Gradle's status (as `:136`). No dependency on `build`. `GRADLE` stays overridable (`:23`).
- Modify: `tools/generate-baseline-profile.sh`:
  - at the top: `DEVICE="${DEVICE:-}"`; empty → message naming `DEVICE` on stderr, exit 2, before any adb or Gradle call (M8); `GRADLE="${GRADLE:-$ROOT_DIR/tools/agentw}"`; cache default (`:5`) unchanged (D6);
  - every adb call `adb -s "$DEVICE"`: `:52`, `:103`, `:104`;
  - `bootstrap_auth_if_needed` (`:89-113`): after the cache check (`:94-97`) try `export_auth_from_device` once and return on success; only then build `(cd "$ROOT_DIR" && "$GRADLE" :app:assembleDevNonMinifiedRelease)` (replaces `:101`), install with `"$ROOT_DIR/tools/android-apk-install-preserve" install-preserve --serial "$DEVICE" --package "$TARGET_PACKAGE" --apk "$ROOT_DIR/app/build/outputs/apk/dev/nonMinifiedRelease/app-dev-nonMinifiedRelease.apk"` (CLI: `tools/android-apk-install-preserve:24-33`), try the export again, and prompt (`:106-112`) only if that fails. A failed install-preserve stops the script (never uninstall or clear);
  - `run_generation` (`:115-135`): build `PROFILE_ARGS` from the same `-P` arguments (`:116-132`) and replace `:134` with `make -C "$ROOT_DIR" DEVICE="$DEVICE" GRADLE="$GRADLE" PROFILE_ARGS="${gradle_args[*]}" baseline-profile` (tokens contain no whitespace; `:app:generateBaselineProfile` leaves the array, the target names it).

**Consumes:** `itest` pattern (`Makefile:123-136`), install-preserve CLI.
**Produces:** `make DEVICE=<serial> [GRADLE=…] [PROFILE_ARGS=…] baseline-profile`; `DEVICE=<serial> ./tools/generate-baseline-profile.sh`.

- [x] Edit the three files
- [x] `AGENT_GRADLE_SERIAL=1 ./tools/agentw -q :app:generateBaselineProfile --dry-run | grep -c Prod` prints 0 (at base it lists `:app:*ProdNonMinifiedRelease*` tasks)
- [x] `make -n DEVICE=emulator-5554 GRADLE=./tools/agentw baseline-profile` shows the private-server sequence; `DEVICE= ./tools/generate-baseline-profile.sh; echo $?` prints the DEVICE message and 2 with no adb or Gradle output (M8); `bash -n tools/generate-baseline-profile.sh` clean
- [x] Fast gate; commit "Generate baseline profiles for the dev flavor on one named device through a make target"

**Done when:** the three checks above hold and `rg -n 'adb shell|gradlew' tools/generate-baseline-profile.sh` prints nothing.

---

### Task 4: Refresh rule and single command in the release doc

**Covers:** AC-7
**Files:**
- Modify: `docs/release.md:79-92` - state that every non-debuggable variant, `prodRelease` included, packages the two files as `assets/dexopt/baseline.prof` and `baseline.profm`, so an app update carries the profile (D12).
- Modify: `docs/release.md:94-142` (Refresh Baseline Profiles) - when: before every release whose diff touches startup, Home, Details or the player, and at least every minor release (D13); how: the one command `DEVICE=emulator-5554 ./tools/generate-baseline-profile.sh` (runs `make baseline-profile`; in a worktree pass `PUBER_BASELINE_AUTH_CACHE=<main>/.todo/baseline-profile-auth.env`); first-run steps `:106-113` rewritten to Task 3's order (cache, export from the paired app, install-preserve, prompt); the raw `./gradlew :app:generateBaselineProfile` block `:134-140` removed.
- Modify: `docs/release.md:154-163` - remove the `connected…AndroidTest` sanity check.

- [x] Edit; `rg -n 'connected.*AndroidTest' docs/release.md` prints nothing (M3)
- [x] Fast gate; commit "Say when to refresh baseline profiles and the one command that does it"

**Done when:** M3 holds.

---

### Task 5: Generate on the emulator's own pairing, then regenerate both profiles

**Covers:** AC-2, AC-3, AC-6
**Runs:** the implementer, in two commits. 5a has no device step. 5b holds the emulator lease. For Task 5, the values below replace the old-host paths and `emulator-5554` in Global constraints (`:10`, `:17`, `:18`). Line numbers in Task 5 are at a0d21716, not at base.

| Item | Value |
|---|---|
| Worktree | `/home/dev/orca/workspaces/Puber/release-startup-compilation-2` |
| Main checkout | `/home/dev/Puber` (secrets in `/home/dev/Puber/.env`) |
| Device | `host.docker.internal:5555`: AVD `puber_tv_36` on the Mac host (Android TV, API 36, arm64, user build, no root). Any emulator with API >= 34 works; root is not needed. Start it with `ssh mac-emu start` (prints `ready`) |
| Lease | `tools/emulator-resource-lock.sh acquire host.docker.internal:5555`; release with the token it returns; then `ssh mac-emu stop` only if `tools/emulator-resource-lock.sh status` shows no other lease |
| Secrets | `PUBER_CLIENT_SECRET`, `PUBER_API_DOMAIN`, `TMDB_READ_ACCESS_TOKEN` from the main `.env`, in the environment of the one script command only (Q-D2) |

**Why generation runs 1-4 lost the pairing** (the fix rests on these; the Implementation record's "Root cause of the logouts" is wrong):

| Fact | Evidence |
|---|---|
| On API >= 34, `CompilationMode.resetAndCompile` resets in place: `cmd package compile -m verify` + `pm art clear-app-profiles`, app data kept. It runs `pm uninstall` + reinstall only below API 34 without root | benchmark-macro 1.5.0 sources, `androidx/benchmark/macro/CompilationMode.kt:86-109`; run 4 log `/home/dev/Puber/.flow/runs/release-startup-compilation/generation-run4-auth-logcat.txt:9-11,287-289,416-418` shows "Resetting profiles", with no "Reinstalling" line |
| Every seeded token held literal single quotes. `UiDevice.executeShellCommand` runs no shell, so the quotes added by `shellQuote` (`BaselineProfileGenerator.kt:246-256`) became part of the extras. Seeded lengths follow L' = L + 3q + 2 (q = quotes in the value) from a 32-char token: 34, 42, 68, 148, 390, 1118, 3304 | run 4 log `:20,136,180,224,268,378,428` |
| A quoted refresh token gets HTTP 400, and the app logs out | log `:31-39` |
| `access=true refresh=true` at process start does not mean the app is paired. A logout writes the sealed empty string (`CryptoPreferenceRepository.kt:41,47,63-67`), `AuthEventLog.kt:28` tests only `contains`, and the provider skips blank values (`BaselineProfileAuthProvider.kt:29-33`). So the generator correctly saw no tokens at log `:19` | — |
| A token refresh needs `CLIENT_SECRET` in the APK (`KtorPlugins.kt:22,53`). A worktree build gets it only from the environment (`app/build.gradle.kts:37-41,58`) | — |

Route: the owner pairs `com.kino.puber.stage` on the emulator once. Generation never writes tokens, and on API >= 34 the reset keeps app data. Re-pairs: one on a fresh AVD, then none per run while the AVD keeps its userdata. Persistence across `ssh mac-emu stop`/`start` is unknown, so pairing and generation run in one emulator session (state.md Decisions, 2026-11-23, owner).

#### 5a: Generate only on a paired emulator, never write tokens

**Files:**
- Modify: `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt`:
  - delete `seedAuthState` (`:157-188`), `readAppAuth` (`:190-201`), `rememberAuthState` (`:203-215`), `BaselineProfileAuthArgs` (`:217-244`), `shellQuote` (`:246-256`), `latestAuth` (`:260`), `AUTH_RECEIVER_CLASS`/`ACTION_SEED_AUTH` (`:267-268`), `EXTRA_*` (`:270-273`), `ARG_*` (`:275-278`), imports `Bundle` and `Base64` (`:3-4`);
  - delete the `seedAuthState()` calls (`:34`, `:47`, `:90`, `:130`);
  - replace each `rememberAuthState()` (`:40`, `:83`, `:123`, `:154`) with `requirePairedApp()`. That is one `device.executeShellCommand("content query --uri content://$packageName.profile.auth/tokens")`. It is paired when the output matches both `name=access_token, value_base64=\S+` and `name=refresh_token, value_base64=\S+`. Log `Log.i(LOG_TAG, "journey end: paired=$paired")`, then `check(paired)` with a message: the app is not paired on this device; pair it once (docs/release.md, Refresh Baseline Profiles); the generator never writes tokens. It never decodes or logs a value;
  - add `@Before fun requireInPlaceReset()`: `check(Build.VERSION.SDK_INT >= 34)`, with a message that below API 34 without root the library uninstalls the target before each journey. Imports `android.os.Build`, `org.junit.Before`.
- Delete: `app/src/nonMinifiedRelease/java/com/kino/puber/profile/BaselineProfileAuthReceiver.kt`, and the `<receiver>` block with its trailing blank line in `app/src/devNonMinifiedRelease/AndroidManifest.xml:5-12` and `app/src/prodNonMinifiedRelease/AndroidManifest.xml:5-12`. The provider and `AuthEventLog` stay.
- Modify: `app/build.gradle.kts:438-439`: drop "and its auth receiver" from the comment.
- Modify: `Makefile` target `baseline-profile` (`:139-156`):
  - comment `:139-140`: drop the sentence that says `PROFILE_ARGS` carries tokens; delete the comment line `:152`;
  - between the device wait (`:145-150`) and the "sees:" line (`:151`), add three refusals. Each prints a message naming its check, kills the private server and exits 1 before Gradle. (1) The private server lists a device other than `$(DEVICE)`. (2) `$(ITEST_ADB) -s $(DEVICE) shell getprop ro.build.version.sdk` is below 34. (3) Not an emulator: none of `ro.boot.qemu` = `1`, `ro.kernel.qemu` = `1`, `ro.hardware` = `ranchu` holds. Strip `\r` from getprop output.
- Modify: `tools/generate-baseline-profile.sh`:
  - delete `CACHE_FILE` (`:11`), `AUTH_RECEIVER` (`:15`), `load_cached_auth` (`:18-23`), `decode_base64` (`:25-35`), `cache_auth` (`:37-50`), `export_auth_from_device` (`:52-92`) and `bootstrap_auth_if_needed` (`:94-130`);
  - after the `DEVICE` check (`:4-8`): if `PUBER_CLIENT_SECRET` is empty in the environment and no `^PUBER_CLIENT_SECRET=.` line is in `$ROOT_DIR/.env` or `$ROOT_DIR/local.properties` (the files `envOrNull` reads first, `app/build.gradle.kts:37-41`), print a message on stderr that names it and the main checkout's `.env`, then exit 2. This runs before any adb or Gradle call;
  - `is_paired`: one `adb -s "$DEVICE" shell content query --uri "$AUTH_PROVIDER_URI"` into a local variable, then status 0 only if both `name=access_token, value_base64=.` and `name=refresh_token, value_base64=.` match. Nothing is printed;
  - `ensure_paired`: if `is_paired`, return. Otherwise build `"$GRADLE" :app:assembleDevNonMinifiedRelease`, install with install-preserve as at `:110-114`, and `am start -n "$MAIN_ACTIVITY"` (as at `:121`). Then, when stdin is a terminal, print "pair the app on $DEVICE (code at kino.watch/device), then press Enter", `read -r`, and exit 1 unless `is_paired`. When stdin is not a terminal, print the same instruction without "press Enter" and exit 3. A failed install-preserve stops the script;
  - `run_generation` (`:132-174`): delete `gradle_args` (`:133-148`); the make call (`:163`) becomes `make -C "$ROOT_DIR" DEVICE="$DEVICE" GRADLE="$GRADLE" baseline-profile`; the comment `:150-152` keeps only "logcat goes outside git"; the post-run check (`:167-172`) uses `is_paired` and reports intact or not paired, with no cache;
  - entry (`:176-177`): `ensure_paired`, then `run_generation`.
- Modify: `docs/release.md:103-124`: the command is `DEVICE=host.docker.internal:5555 ./tools/generate-baseline-profile.sh`, with the three secrets from the main `.env` in that command's environment. The device must be an emulator with API >= 34: the reset keeps app data there, and the make target refuses anything else. `com.kino.puber.stage` must be paired once; if it is not, the script installs `devNonMinifiedRelease` with install-preserve and waits for the code. Generation never writes tokens. Delete the worktree cache sentence (`:110`) and the token-source list (`:112-121`). Keep `:123-124` minus the receiver.

**Consumes:** Tasks 2 and 3.
**Produces:** `requirePairedApp`, `requireInPlaceReset`; `make baseline-profile` refusals; script exit 2 (no `DEVICE`, no `PUBER_CLIENT_SECRET`) and exit 3 (not paired, non-interactive).

- [x] Edit the files above
- [x] `rg -n -g '!app/src/main/generated/**' 'seedAuthState|shellQuote|BaselineProfileAuthReceiver|SEED_AUTH|baseline-profile-auth|PUBER_BASELINE_(ACCESS|REFRESH|USERNAME|API_DOMAIN)|AUTH_CACHE' baselineprofile app/src app/build.gradle.kts tools/generate-baseline-profile.sh Makefile docs/release.md` prints nothing. The old profiles keep `BaselineProfileAuthReceiver` rules (`baseline-prof.txt:14154-14168`, `startup-prof.txt:13952-13966`) until 5b
- [x] `bash -n tools/generate-baseline-profile.sh` clean. M8: `DEVICE= ./tools/generate-baseline-profile.sh; echo $?` prints the DEVICE message and 2. `DEVICE=host.docker.internal:5555 PUBER_CLIENT_SECRET= ./tools/generate-baseline-profile.sh; echo $?` prints the secret message and 2. Neither prints adb or Gradle output
- [x] `make -n DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw baseline-profile` shows the three refusals before the Gradle line
- [x] Fast gate and `AGENT_GRADLE_SERIAL=1 ./tools/agentw :baselineprofile:compileDevNonMinifiedReleaseKotlin :app:compileDevNonMinifiedReleaseKotlin`. `BaselineProfileContentTest` stays red until 5b (committed red in a0d21716), so the full gate waits for 5b
- [x] Commit "Generate baseline profiles only on an emulator's own pairing and never write its tokens"

**Done when:** the checks above hold.

#### 5b: Regenerate both profiles on the emulator

`BaselineProfileContentTest.kt` with its three tests and its red run on the base files is done (a0d21716; Implementation record, Red run).

- [x] M1 step 1: lease the device, `ssh mac-emu start`, `adb connect host.docker.internal:5555`. `adb -s host.docker.internal:5555 shell getprop ro.build.version.sdk` prints 34 or more, and at least one of `getprop ro.boot.qemu` = `1`, `ro.kernel.qemu` = `1`, `ro.hardware` = `ranchu` holds (5a refusal 3). If none holds, report Blocked with the three values; nothing on the device has changed. Save the output of `adb -s host.docker.internal:5555 shell pm path com.kino.puber` (empty on this AVD)
- [x] M1 step 2, in the worktree: `AGENT_GRADLE_SERIAL=1 DEVICE=host.docker.internal:5555 PUBER_CLIENT_SECRET=… PUBER_API_DOMAIN=… TMDB_READ_ACCESS_TOKEN=… ./tools/generate-baseline-profile.sh`. Exit 3 means not paired: report Blocked with the emulator left running and the lease held. The conductor reads the code from the screen (`adb -s host.docker.internal:5555 exec-out screencap -p` into the scratchpad, never into evidence), and the owner enters it at kino.watch/device. Then run the same command again. Do not stop the emulator between pairing and generation
- [x] M1 steps 3-7: the "sees:" line lists only `host.docker.internal:5555`; no `Prod` task ran; exit 0; `playbackProfile` and `railNavigationProfile` passed; `pm path com.kino.puber` is unchanged; `git diff --stat` lists both profile files; `git status --short` shows no new untracked file. The run's log (path printed by the script, outside git) has a `PuberGen: journey end: paired=true` line for each journey and no `seeding` line. A failing journey or a `Prod` task stops the task (Blocked, with that log's `PuberAuth`/`PuberGen` lines, values never included), never a partial commit. Run the command again only if the script reported the pairing intact
- [x] `--tests com.kino.puber.BaselineProfileContentTest` green on the new files; `rg -n BaselineProfileAuthReceiver app/src/main/generated` prints nothing
- [x] M2: `AGENT_GRADLE_SERIAL=1 ./tools/agentw :app:assembleProdRelease :app:assembleDevBenchmarkRelease`; `unzip -l` of both APKs lists `assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm`
- [x] Full gate; release the lease (and `ssh mac-emu stop` per the table above); commit "Regenerate the baseline and startup profiles for the side rail, details and player"

**Done when:** M1 and M2 hold, `BaselineProfileContentTest` passes, and the full gate exits 0. The 5b commit's sha is the build measured below.

---

## Device verification after Task 5 (not implementer tasks)

Order and owners per `state.md` Decisions: the conductor runs every `cmd package compile`, ProfileInstaller broadcast and `bg-dexopt-job`; qa-device installs, launches, injects keys, reads telemetry and writes evidence. Box `192.168.1.110:5555` only, `com.kino.puber.stage` only, build `devBenchmarkRelease` installed with `tools/android-apk-install-preserve install-preserve`.

`cmd package compile --reset` is refused for shell on the box (API 29, SecurityException, observed 2026-11-23). Every reset below is: reinstall the same APK with install-preserve, then `cmd package dump-profiles com.kino.puber.stage` shows empty cur and reference profiles, and `dumpsys package` shows `[status=speed-profile] [reason=install]`.

| Step | Check | State recipe |
|---|---|---|
| Baseline | M4, AC-1 | done 2026-11-23 (`/home/dev/Puber/.flow/runs/release-startup-compilation/baseline.md`). If M5 runs on a later date, M4 runs again first, on M5's day (AC-4: same day) |
| New build | M5 (AC-4 gate on S1), M6 (AC-5 `prof` per line) | same day as M4; S1 then S0 |
| Update path | M7, AC-8 | base installed and launched once; new build over it with install-preserve, no reset |

| State | Recipe on the box |
|---|---|
| S0 | reinstall with install-preserve; dump-profiles empty; no compile |
| S1 | reinstall with install-preserve; ProfileInstaller broadcast (result logged); `am force-stop` at once; dump-profiles shows only the embedded profile; `cmd package compile -m speed-profile -f`; `dumpsys` `[status=speed-profile]`; only then the first launch |
| S2 | S0, 5 unmeasured cold starts with D-pad use, `am force-stop`, `compile -m speed-profile -f`, `dumpsys` |

## Spec deltas for the conductor

| # | Where | Delta |
|---|---|---|
| 1 | Test design, Manual and device checks, S0/S1 rows; spec Measurement states; brief S0/S1/S2; design Risk 4 | `cmd package compile --reset` is refused on the box (API 29); replace it with an install-preserve reinstall of the same APK plus `dump-profiles` showing empty profiles (recipe above) |
| 2 | Test design, Traceability, `profileStatusReachesMarksOnlyThroughTheTelemetryExecutor` | "after `runAll`" should read "after `runScheduled`": `sink.schedule(0)` queues into the scheduled list (`util/ManualTelemetryExecutor.kt:21-24,52-57`) |
| 3 | Test design, M1 heading "(emulator-5554, conductor)" | the implementer runs M1 in Task 5b; no compile-state command is involved |
| 4 | Files table row 4, D13, M1, M3, Decisions row 2 (Q3) | apply the owner's reading (state.md Decisions, 2026-11-23: AC-2 is read with `DEVICE=host.docker.internal:5555`): AC-2, M1 and M3 name `host.docker.internal:5555` (AVD `puber_tv_36`, API 36) where they name `emulator-5554`; the Files table row and D13 likewise. Q3's reinstall/clear authorisation is no longer used |
| 5 | Risks (analyst) row 3; Test design risk row "generation clears `com.kino.puber.stage`… M1 step 1 (token cache…)" | On API >= 34 the reset keeps app data (`CompilationMode.kt:86-109`), and the make target refuses API < 34 and non-emulators. No token is ever seeded. Guard: `requirePairedApp` at each journey end, and M1 step 2 exits 3 when the app is not paired |
| 6 | D5, D6, Q-D3; Files table row 3 | token export, cache and prompt are replaced by a pairing check and a one-time owner pairing. No token travels in Gradle arguments. `BaselineProfileAuthReceiver` and its two manifest entries are deleted; the provider stays read-only |
| 7 | Design Risk 3 | the script refuses an empty `PUBER_CLIENT_SECRET`; the domain comes from `PUBER_API_DOMAIN` in the same environment, not from a seeded `api_domain` |


---

## Implementation record

<!-- owner: implementer -->

**Commits:** 64fd004e Task 1; dbb2d28b Task 2; 3711812a Task 3; ea78e231 Task 4. Task 5a f166fbec; plan delta 17f663d1 (install-preserve regex); Task 5b: the commit that carries this record.

**Red run** (P0 `red` tests on the unchanged code, with the smallest stubs: `Startup.profileStatus`, `StartupMarks(awaitProfileStatus)` + no-op `markProfileStatus`, `ProfileStatusSource`, `TelemetryRuntime(profileStatus)`):
- red: StartupMarksTest.readyLineWaitsForProfileStatusWhenAwaited - AssertionFailedError, line written before the status
- red: StartupMarksTest.boundEmitsWithoutProfileStatusWhenAwaited - AssertionFailedError, ready path emitted without waiting (precondition of the test)
- red: StartupMarksTest.profileStatusKeepsTheFirstValue - AssertionFailedError, profileStatus null instead of 1
- red: TelemetryEncoderTest.startupWritesProfWhenKnown - NoSuchElementException, no `prof` key
- red: TelemetryRuntimeTest.coldStartStartupLineCarriesProfileStatusFromSource, profileStatusReachesMarksOnlyThroughTheTelemetryExecutor - the stub never registers the source (callback null / no line)
- green on base: StartupMarksTest.forcedRevealEmitsWithoutProfileStatusWhenAwaited - FORCED emits with null status both before and after; kept as guard
- green on base: TelemetryRuntimeTest.offStartNeverAsksProfileStatus, StartupMarksTest.readyLineEmitsWithoutProfileStatusByDefault - the stub never asks and the default does not wait; guards
- red: BaselineProfileContentTest.baselineProfileHasNoRemovedTopTabClasses - "stale baseline rule for com/kino/puber/ui/feature/main/toptabs/"
- red: BaselineProfileContentTest.baselineProfileCoversDetailsPlayerAndStartupClasses - "no baseline rule for com/kino/puber/ui/feature/details/"
- green on base: BaselineProfileContentTest.startupProfileHasNoDetailsOrPlayerRules - guard (P1)

**Deviations from the plan:**
- Task 3, D3: `"devBaselineProfile"(project(...))` does not resolve: the plugin creates `devReleaseBaselineProfile` and `devDeployBaselineProfile` after the dependencies block runs. Used `configurations.configureEach { if (name == "devReleaseBaselineProfile") dependencies.add(...) }`. `:app:generateBaselineProfile --dry-run` lists only `DevNonMinifiedRelease` tasks (grep -c Prod = 0).
- Task 3: the `baseline-profile` target runs `adb connect` only when `$(DEVICE)` has a `:` (an emulator serial is found by the private server's own scan).
- Task 1 test fixture: added `ManualTelemetryExecutor.runImmediateScheduled()` (runs only delay-0 tasks, repeatedly); `runScheduled()` also fires the 60 s fallback, which would emit the line before the status.
- Task 1 detekt: `rg writeProfileVerification app/src/main` still matches the generated profile files (library-internal method names), none in code.

- Fix after the first generation run: the server rotates the refresh token on first use, so re-seeding the argument tokens in every iteration logged the app out (login screen on emulator-5554, no tokens in the provider). `BaselineProfileGenerator` now remembers the tokens the app holds at the end of each journey and seeds those.

- Root cause of the logouts (generation runs 1-3): `androidx.benchmark` 1.5.0 `CompilationMode.resetAndCompile` calls `reinstallPackage` (`pm uninstall` + install from copied APK paths) before every `collect` when the shell session is not rooted. The uninstall wipes the app data and its AndroidKeyStore key, so the pairing is gone at the start of every journey and only what the generator seeds survives. The generator now seeds only when the app holds no tokens, from the last pair it read, and the nonMinifiedRelease build logs auth events (`AuthEventLog`, tag `PuberAuth`; token values never logged), which the script saves outside git.

**Files touched outside the plan:** `app/src/nonMinifiedRelease/.../AuthEventLog.kt`, `CryptoPreferenceRepository` (three consts `internal`), `app/src/test/kotlin/com/kino/puber/util/ManualTelemetryExecutor.kt`.

**For the reviewers:** `StartupMarks.emitWhenComplete` condition and first-value-wins status; `configurations.configureEach` hook in `app/build.gradle.kts`; install-preserve branch of `bootstrap_auth_if_needed`; `railNavigationProfile` assumes at most 6 rail entries and Home first (extra UP/DOWN presses clamp).

**Task 5a (no device):** generator, make target, script, docs and manifests as the plan lists; the old generator's seeding, cache and receiver are gone. Checks: M8 prints the DEVICE message and 2, then the secret message and 2, with no adb or Gradle output; `bash -n` clean; `make -n` shows the three refusals before the Gradle line; fast gate plus `:baselineprofile:compileDevNonMinifiedReleaseKotlin :app:compileDevNonMinifiedReleaseKotlin` BUILD SUCCESSFUL. The plan's `rg` prints only `CurlLogFormatter.shellQuote` (`data/api/network`), an unrelated private function. Full gate not run: `BaselineProfileContentTest` stays red until 5b. The deviation notes above on seeding and the logout root cause are superseded by the Task 5 table. For the reviewers: the make refusal order (server list, API level, emulator) and `ensure_paired` exit codes.

**Task 5b (device, host.docker.internal:5555, AVD puber_tv_36, API 36):** M1 step 1: sdk 36, `ro.boot.qemu`=1, `ro.kernel.qemu`=1, `ro.hardware`=ranchu; `pm path com.kino.puber` empty before and after. Plan delta 17f663d1: `tools/android-apk-install-preserve` rejected apksigner 36 output (`Signer #1 certificate SHA-256 digest:` has no colon after `#1`), so the first script run stopped at the install; `CERT_DIGEST` now accepts `Signer #N:?` and `V<n> Signer:`; checked on three sample lines and `inspect` on the 5b APK. The second run installed devNonMinifiedRelease (data kept) and exited 3 (not paired); the owner paired; the third run exited 0: "sees:" lists only `host.docker.internal:5555`, no `Prod` task, pairing intact, `pm path` unchanged, `git diff --stat` lists exactly the two profile files, no untracked file. The log (`/tmp/puber-baseline-profile/20261123-104343-logcat.txt`, outside git) has 44 `journey end: paired=true` lines and no `seeding` line. `BaselineProfileContentTest` green; `rg -n BaselineProfileAuthReceiver app/src/main/generated` prints nothing. M2: `assembleProdRelease` and `assembleDevBenchmarkRelease` BUILD SUCCESSFUL on the second run (the first run of the same command printed BUILD FAILED after 3 min without a captured cause; the rerun passed), both APKs list `assets/dexopt/baseline.prof` (21309 bytes) and `baseline.profm` (1590 bytes). Full gate (`testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) BUILD SUCCESSFUL. The lease and the emulator are left to the conductor.

**Blocked (before 5a):** Task 5 stops before generation (conductor runs it, owner's Q3). `BaselineProfileContentTest.kt` is written, red on base, uncommitted in the worktree.

### Review fix round 1 (phase A: code, tests, docs)

| # | Fixed | Round commit |
|---|-------|--------|
| 1 | Rail journey leaves Search with BACK (second BACK if the keyboard took the first), checks after each entry that the rail shows a title (resolved from the target's string resources) and fails the journey otherwise; coverage test gains favorites, history, contentlist | 1 |
| 2 | Account write documented in `docs/release.md` and the `playbackProfile` KDoc; behaviour unchanged | 1, 2 |
| 3, 4 | `preflight_device` before `ensure_paired`: `adb connect`, `get-state`, emulator props, API >= 34, exit 2 | 2 |
| 5 | `trap cleanup` on EXIT, INT, TERM: logcat pid and the 5038 adb server | 2 |
| 6 | `AuthEventLog` logs present only for a non-empty stored value | 3 |
| 7 | `LINE_WAIT_AFTER_REVEAL_WITH_PROFILE_MS` = 7 s when awaiting the status; `boundEmitsWithoutProfileStatusWhenAwaited` asserts the delay | 3 |
| 8 | `profileStatusReachesMarksOnlyThroughTheTelemetryExecutor` forces `marks.onTimeout()` before the executor runs; a direct write would show `prof` | 3 |
| 9, 10 | Test-design rows corrected in the run folder | n/a (not in git of the worktree) |

Red line: `green on base` not applicable; `BaselineProfileContentTest.baselineProfileCoversDetailsPlayerRailTabsAndStartupClasses` - red: `no baseline rule for com/kino/puber/ui/feature/favorites/` until phase B regenerates.

Check: `check.fast` green, touched telemetry tests green, `detektAll` green; `testDevDebugUnitTest` red only on the coverage assertion above (2395 run, 1 failed). `:app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL.

For reviewers: the rail check (`railShows`) is unproven on a device; the rail title may stay in the accessibility tree while the rail is collapsed, which would make it pass trivially. Phase B shows it through the profile (favorites, history, contentlist rules).

Phase B (regeneration, round 1): `railNavigationProfile` capped at `maxIterations = 5` (owner decision, commit "Cap the rail navigation journey..."). Runs 1-4 on `host.docker.internal:5555` died with `IllegalStateException: Failed to compile (out=)` at the compile reset between journeys; run 5 passed all journeys after the checked-in `baseline-prof.txt` was temporarily replaced with the one from `0d2fad28` (pre-H profile) so the embedded profile was not the all-H one. That the all-H embedded profile caused the flake is a hypothesis supported by this one run, not proven. The swap is now automatic: the `baseline-profile` make target backs up `baseline-prof.txt`, strips the leading `H` from method rules before generating, restores the backup on a failed or interrupted run, and on success marks every method rule `H` again and deletes the backup. A dry run of strip then rewrite on a copy of the checked-in file returned it byte-identical. The checked-in files were then overwritten by the generation and the Makefile marked every method rule `H`.

Result: 0 method rules without `H`; rule lines for `ui/feature/favorites/` 109, `history/` 317, `contentlist/` 366 (`search/` 138). `BaselineProfileContentTest` green; `testDevDebugUnitTest :app:detektAll :app:lintDevDebug` BUILD SUCCESSFUL.

Known script quirk: in run 3 `tools/generate-baseline-profile.sh` printed "not paired after the run" although the provider showed both tokens right after; a false alarm (the provider query raced the app process), not fixed in this round.

## Readiness

# Readiness 4: release-startup-compilation

Verdict: **READY**

Scope: `git show 43453de1` (plan only) against `readiness-3.md` Fix list and non-blocking notes.

## Readiness-3 items

| # | Item | Status | Evidence |
|---|---|---|---|
| 1 | 5a `rg` check cannot pass while the old profiles hold the receiver | done | 5a check now has `-g '!app/src/main/generated/**'`; run at 43453de1 it prints only non-generated hits (manifests, generator `:267`, receiver), which 5a deletes. 5b adds `rg -n BaselineProfileAuthReceiver app/src/main/generated` prints nothing |
| 2 | Delta 4 and the route paragraph cite empty Open questions | done | both now cite state.md Decisions 2026-11-23; delta 4 tells the conductor to put `host.docker.internal:5555` into AC-2, M1, M3, the Files table row and D13 |
| NB1 | qemu property unverified on the Mac arm64 AVD | done | refusal 3 also accepts `ro.hardware` = `ranchu`; 5b M1 step 1 checks the three values before any install and reports Blocked if none holds |
| NB2 | Script refuses the secret even when `.env` holds it | done | the check also greps `$ROOT_DIR/.env` and `$ROOT_DIR/local.properties`, the files `envOrNull` reads (`app/build.gradle.kts:21-41`, `rootProject.file`). The worktree has neither line (no `.env`; `local.properties` has no `PUBER_CLIENT_SECRET`), so the M8 secret case still exits 2 |

## Newly broken

None found.

## Non-blocking

- `^PUBER_CLIENT_SECRET=.` misses a properties line written as `PUBER_CLIENT_SECRET = …`; the script then refuses and names the variable, so nothing on the device is touched. Spec delta 7 still says "refuses an empty `PUBER_CLIENT_SECRET`" without the file fallback.

## Readiness-1 fix list

| # | Fix | Status | Evidence |
|---|---|---|---|
| 1 | One Test design and one Open questions | done | spec headings: `## Test design` :132, `## Open questions` :242, each once; the design has no `### Open questions`; S0/S1 rows :214-215 use the install-preserve reinstall; P2 row :181 reads `runScheduled`; M1 :220 is the implementer's |
| 2 | AC-3 checkable against a full stale list | done | spec :187-198 (and `test-design.md:56`) list 6 classes; an independent scan of the base `baseline-prof.txt` against `app/src` finds the same 6 missing, with the same entry counts (23, 8, 1, 2, 3, 1); the four prefixes of row :169 cover all 6 |
| 3 | Device and manual P0 rows marked | done | :165, :166, :171, :182, :183, :185 read `P0 guard` |

## Citations re-sampled

| Citation | Resolves |
|---|---|
| `DetailsVM.kt:536` (D7, Play) | yes: `router.navigateForResult` inside `openPlayer` (:532) |
| `app/build.gradle.kts:436` | yes: `baselineProfile(project(":baselineprofile"))` |
| `Makefile:123-124` | yes: `ITEST_ADB_PORT`, `ITEST_ADB` |
| `RevealGate.kt:9`, `PlayerScreen.kt:72` | yes |
| `StartupMarks.kt:10`, `:94-100` | yes: `LINE_WAIT_AFTER_REVEAL_MS`, `emitWhenComplete` |
| `ProguardRulesTest.kt:10-12` | yes: working-dir fallback |

## Non-blocking

- Spec :189 calls the 385 profile classes "outer classes"; 385 counts every `com/kino/puber` class including nested and lambda classes (115 outer). The 6 stale outer classes and their 38 entries are right.

### Fix round 1 (AC-4)

qa verify round 1 failed AC-4: the embedded profile carried 35556 method rules as `S` or `P`, which API 29 `speed-profile` does not compile (design-ac4-diagnosis.md, F3/F4).

- Commit: 1733ce48 (test, Makefile rewrite after generation, one-time rewrite of `baseline-prof.txt`, `docs/release.md` sentence). `startup-prof.txt` unchanged.
- red: BaselineProfileContentTest.baselineProfileMethodRulesAreHot - "35556 method rules without H, first: SPLandroidx/activity/ActivityFlags;-><clinit>()V"
- Plan delta: test-design row for `baselineProfileMethodRulesAreHot` (AC-4, unit, P0 guard).
- `assets/dexopt/baseline.prof` in `app-dev-benchmarkRelease.apk`: 29117 B (was 21309 B).
- Device verification (E3, M5 re-measure) is left to the conductor and qa.
