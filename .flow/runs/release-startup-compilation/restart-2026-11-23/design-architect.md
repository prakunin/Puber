# Design: release-startup-compilation (part 1)

Base b371653d (`e4c3b5aa` plus a hook chmod). ACs: `brief.md#acceptance-criteria-part-1`.
Owner answers: `state.md` Decisions. Supersedes the round-1 design of the old branch
`prakunin/release-startup-compilation`. Library citations are from
`androidx.benchmark:benchmark-macro:1.5.0` sources (`gradle/libs.versions.toml:28-29`).

## Decisions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| D1 | Does generation or benchmarking reinstall the app? | Only below API 34 without root. On API 34+ the reset is `cmd package compile -m verify` + `pm art clear-app-profiles` and keeps app data (`CompilationMode.kt:84-109`, `reinstallPackage` at `:137-150`). Both make targets refuse a device below API 34 | AC-1 and AC-2 hold only on that branch. The old run's run-4 log shows `Resetting profiles` with no `Reinstalling` line (`generation-run4-auth-logcat.txt:9-11`), so the reinstall claim in `handoff.md` was wrong |
| D2 | How the app is signed in during generation | The generator never seeds tokens. It relies on the pairing the device already holds and fails at once if the auth screen shows. Delete `BaselineProfileAuthReceiver`, `BaselineProfileAuthProvider` and the two manifests that export them without a permission; delete the token cache and export code in the script | Run 4 logged the emulator out by seeding rotated-out cached tokens over a live pairing (`generation-run4-auth-logcat.txt:13-32`). AC-1 already requires a paired app. The provider hands tokens to any app on the device |
| D3 | Token refresh mid-run with a worktree build | Both targets accept `APP_ENV=<path to .env>` and export only `PUBER_CLIENT_SECRET`, `PUBER_API_DOMAIN` and `TMDB_READ_ACCESS_TOKEN` from it to the Gradle process, silently. In a linked worktree, a target with neither `APP_ENV` nor `PUBER_CLIENT_SECRET` set stops before Gradle runs | `envOrNull` falls back to `System.getenv` (`app/build.gradle.kts:37-41`). An empty `CLIENT_SECRET` (`:58`) fails the refresh, and the app then logs out. The secrets reach no file and no log (Q4) |
| D4 | Restrict generation to the dev flavor | Replace `baselineProfile(project(":baselineprofile"))` (`app/build.gradle.kts:436`) with `variants { create("devRelease") { from(project(":baselineprofile")) } }` inside `baselineProfile {}` (`:439-446`). Fallback: the per-configuration dependency of old commit `3711812a` | `mergeIntoMain` leaves only `:app:generateBaselineProfile` (`agentw help --task :app:generateDevBaselineProfile`: "not found"), and that task runs the prod producer too. The prod producer targets `com.kino.puber` (`baselineprofile/build.gradle.kts:24-27`), which Q2 does not authorise. `from(Project)` per variant exists in plugin 1.5.0 (`BaselineProfileVariantConfigurationImpl`). `mergeIntoMain` still packages the files into every release variant |
| D5 | Make target shape | `baseline-profile` and `benchmark` share one prologue next to `itest` (`Makefile:123-136`). It starts the private server (`ITEST_ADB`, `:123-124`) and connects `DEVICE` if it has a port. It waits up to 60 s for the device. It then refuses unless the private server lists exactly `DEVICE`, `ro.build.version.sdk` ≥ 34, and `ro.boot.qemu` or `ro.kernel.qemu` is `1`. It records `firstInstallTime` and checks it again after the run. It always kills the server and keeps Gradle's exit status. `itest` itself is unchanged | A private server still auto-registers local emulators: the itest leak in `.flow/handoff/2026-11-23-watch-index-catch-up.md`. The main `.env` sets a default `DEVICE` (`Makefile:16-18`), so a bare `make baseline-profile` in main would reach that device. The SDK and emulator checks enforce D1 and Q2 mechanically |
| D6 | Generation command | `$(GRADLE) :app:generateBaselineProfile -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true --console=plain`. No `build` or `install*` prerequisite | Same as itest. The connected task installs with `-r` (data and `firstInstallTime` kept). The plain console shows the task list that AC-1 needs |
| D7 | Benchmark command | `$(GRADLE) :baselineprofile:connectedDevBenchmarkReleaseAndroidTest` with `class=com.kino.puber.baselineprofile.StartupBenchmarks`, `androidx.benchmark.suppressErrors=EMULATOR` and the leave-installed flag. Results: `com.kino.puber.baselineprofile-benchmarkData.json` (`ResultWriter.kt:50`), pulled under `baselineprofile/build/outputs/connected_android_test_additional_output/` | The task exists (`agentw :baselineprofile:tasks --all`). Macrobenchmark errors out on an emulator without the suppression (`DeviceInfo.kt:210`) |
| D8 | `GRADLE` default | `GRADLE ?= $(if $(wildcard .git/HEAD),./gradlew,./tools/agentw)` (`Makefile:23`) | In a linked worktree `.git` is a file, and a make recipe calls `./gradlew` out of the hook's sight (`guard-bash.sh:47`). This also makes `itest` safe there |
| D9 | Waiting out the splash (AC-3) | `ReportDrawnWhen { !covered }` in `MainActivity.setContent`, next to the reveal effect (`MainActivity.kt:81-88`). The generator waits for `By.res("startup_splash")` to be gone (tag at `StartupSplashHost.kt:18,46`), with a cap above `REVEAL_CAP_MS` (`RevealGate.kt:9`). It logs `PuberGen: first key` before its first key | System logcat then prints `Fully drawn <pkg>/…MainActivity` at the reveal; AC-3 orders those two lines. `startActivityAndWait` has no TTFD wait (`MacrobenchmarkScope.kt:178-201`). activity-compose is 1.13.0 (`libs.versions.toml:12`) |
| D10 | How a CUJ proves the screen it reached (AC-4) | `Modifier.semantics { testTagsAsResourceId = true }` once, on the `StartupSplashHost` root `Box` (`StartupSplashHost.kt:32`), covering the splash and the whole app. Tags from one new object `JourneyTags` on: Home content `HomeContent` (`HomeScreenContent.kt:181`), Details content `DetailsContentBody` (`DetailsScreenContent.kt:136`), player (`PlayerScreen.kt:72` passes `modifier` to `PlayerScreenContent.kt:41`), player error `ErrorOverlay` (`PlayerScreenContent.kt:176`), resume dialog (`ResumeDialog.kt:45`), Search root (`SearchScreenContent.kt:67`), Favourites root (`FavoriteScreenContent.kt:38`), auth root (`AuthComponent.kt:38`), rail items `rail_<TabType.name>` (`MainScreenComponent.kt:268`). The generator copies the strings, since `:baselineprofile` cannot import `:app` | Tags on content states, not the screen wrappers, so Loading and Error do not pass. Precedent: `HistoryScreenContent.kt:157`. Text matching would depend on the locale |
| D11 | CUJ set (AC-4, `research.md` Q3) | `startupProfile` (`includeInStartupProfile = true`): start, splash gone, Home present. `homeAndDetailsProfile`: rows down and right, OK, Details, Back, Home. `playbackProfile`: Home card, Details, Play; then the player tag, OK on the resume dialog if it shows, no error tag, about 5 s, Back until Details. `railProfile`: LEFT, DOWN until `rail_Search` has focus, OK, Search; the same for `rail_Favourites`. Each wait is `device.wait(Until.hasObject(By.res(tag)), timeout)` followed by `check`. The auth tag fails the run with "device not paired" | Replaces the top-tab journeys (`BaselineProfileGenerator.kt:35-122`) and `seedAuthState` (`:124-200`). D-pad, not `click()`, so the profile includes the focus code |
| D12 | Measuring the AC-7 base | Task 1 lands only the harness: Makefile and `StartupBenchmarks`. It leaves `app/`, `gradle/` and `buildSrc/` byte-identical to base (`git diff --stat b371653d HEAD -- app gradle buildSrc` is empty). qa-device runs `make DEVICE=<serial> APP_ENV=/home/dev/Puber/.env benchmark` on that commit as the baseline. At the head, the same command runs, then the base again from `git archive <task-1 sha>` extracted to the scratchpad and built with that tree's `tools/agentw`, the same day. The AC-7 numbers are that same-day pair | Base has no compliant way to run the benchmark: the hook refuses `connected*AndroidTest` (`guard-bash.sh:55`), and `StartupBenchmarks.kt:19-20` documents that refused command. The app under test stays base's. The `git archive` recipe was already used for base APKs (`baseline.md`) |
| D13 | Benchmark iterations | `iterations = 10` (`StartupBenchmarks.kt:47`), changed in Task 1 so base and head share it. The measured block is unchanged | Median of 5 on an emulator is noisy (brief risk) |
| D14 | Script | `tools/generate-baseline-profile.sh` keeps its name (`AGENTS.md` tool list) and becomes a wrapper. It requires `DEVICE`, passes `APP_ENV` and `GRADLE` through, and calls `make -C "$ROOT_DIR" … baseline-profile`. It has no adb call of its own | Removes `./gradlew` (`:101,:134`) and bare adb (`:52,:103-104`) (AC-8). One code path |
| D15 | Release procedure (AC-8, `research.md` Q4) | New step 2 in `docs/release.md:16-39`. If `git log -1 -- app/src/main/generated/baselineProfiles` is older than the previous tag and the range touches `app/src/main`, refresh through a run before the bump. Otherwise write `Profile refresh skipped: <reason>` in the version PR body. Rewrite `:94-142` to the make targets, the precondition (paired emulator, API 34+, lease, `APP_ENV` in a worktree) and the AC-5 grep. Replace `:154-163` with `make benchmark` | Generation needs the owner and a device, so it cannot be a CI gate |

## Changes by layer

| Layer | File | Change |
|-------|------|--------|
| API / repository | N/A: no API or repository change | |
| ViewModel state and actions | N/A: no ViewModel change | |
| Screen and DI module | `app/src/main/java/com/kino/puber/MainActivity.kt:81-88` | `ReportDrawnWhen { !covered }` (D9) |
| | `ui/feature/player/component/PlayerScreen.kt:72` | `modifier = Modifier.testTag(JourneyTags.PLAYER)` (D10) |
| Compose content | `ui/feature/root/component/StartupSplashHost.kt:32` | `testTagsAsResourceId` on the root `Box` (D10) |
| | `HomeScreenContent.kt:181`, `DetailsScreenContent.kt:136`, `PlayerScreenContent.kt:176`, `ResumeDialog.kt:45`, `search/content/SearchScreenContent.kt:67`, `favorites/content/FavoriteScreenContent.kt:38`, `auth/component/AuthComponent.kt:38`, `main/component/MainScreenComponent.kt:268` | `testTag` on each root modifier (D10). No layout or visual change |
| | new `app/src/main/java/com/kino/puber/core/ui/JourneyTags.kt` | tag constants |
| Resources | N/A | |
| Build | `app/build.gradle.kts:436,439-446` | D4 |
| | `app/src/nonMinifiedRelease/java/com/kino/puber/profile/*.kt`, `app/src/devNonMinifiedRelease/AndroidManifest.xml`, `app/src/prodNonMinifiedRelease/AndroidManifest.xml` | deleted (D2) |
| | `Makefile:23,55,123-136` | D5-D8. Targets listed in `help` and `.PHONY` |
| | `tools/generate-baseline-profile.sh` | D14 |
| Generator module | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineProfileGenerator.kt` | D9, D11. Seeding removed |
| | `baselineprofile/.../StartupBenchmarks.kt:15-21,47` | doc comment names `make benchmark`, D13 |
| Generated | `app/src/main/generated/baselineProfiles/{baseline,startup}-prof.txt` | regenerated on the leased emulator (AC-5) |
| Docs | `docs/release.md:16-39,94-163` | D15 |

## Alternatives rejected

- Resume the old branch: it carries ProfileVerifier telemetry (now part 2) and the token seeding that logged the emulator out.
- Keep seeding, but only into an empty app (old `0945400f`): the app needs a pairing anyway, and seeding brings the exported token provider back.
- Gate the prod variant off with `androidComponents.beforeVariants` in `:baselineprofile`: that also drops the prod benchmark. Kept only as a second fallback after D4.
- Wait a fixed `Thread.sleep` past the reveal: it gives no AC-3 evidence and is slow or flaky either way.
- Detect screens through telemetry screen events: recording is opt-in and off by default.
- Measure the base through a hand-run Gradle command: the hook refuses it, and it bypasses the device isolation.

## Risks

| # | Risk | Where it lives | Guard |
|---|------|----------------|-------|
| 1 | Lifecycle: the emulator loses its pairing. Either a refresh fails with an empty `CLIENT_SECRET`, or the run reaches the auth screen and someone pairs over it. A re-pair costs the owner a device-code entry | `Makefile` prologue, `app/build.gradle.kts:37-41,58` | D3 stops the target. D2 removes seeding. Each CUJ fails on the auth tag instead of continuing |
| 2 | Rollout: the wrong device is used. That may be the box (`.env` default `DEVICE`), an extra auto-registered emulator, or a device below API 34 where Macrobenchmark runs `pm uninstall` (`CompilationMode.kt:104-109,179-182`) | `Makefile` prologue | D5 refuses on the device list, SDK and qemu props before Gradle starts |
| 3 | Rollout: the prod producer still runs and installs `com.kino.puber` on the emulator | `app/build.gradle.kts:436-446` | Task `Done when`: `agentw :app:generateBaselineProfile --dry-run` lists no `Prod` task. Otherwise apply the D4 fallback |
| 4 | Data: each `playbackProfile` iteration writes a watch position to the owner's KinoPub account on exit (`PlayerVM.kt:1898-1925`). The rule runs up to 15 iterations by default (`junit4/BaselineProfileRule.kt:86`) | `BaselineProfileGenerator.kt` | Q1 |
| 5 | Correctness: a CUJ that misses its screen still produces a profile | generator | D10/D11 assertions fail the test. AC-5 grep (test-designer may add a JVM content test on the txt files) |
| 6 | Build: `check.fast` and `check.full` compile neither `:baselineprofile` nor the `nonMinifiedRelease` source set, so D2, D11 and D13 can break unseen | plan constraints | Every task touching them also runs `./tools/agentw :baselineprofile:compileDevNonMinifiedReleaseKotlin :app:compileDevNonMinifiedReleaseKotlin` |
| 7 | Performance: AC-7 noise on the emulator | qa protocol | D12 same-day pair, D13 ten iterations |
| 8 | Security: `APP_ENV` puts the client secret into a worktree's Gradle process and APK. A recipe that echoes it leaks it into logs | `Makefile` | `@`-silent recipe lines. AC-9 audit over the evidence |
| 9 | Recomposition: `ReportDrawnWhen` reads `covered` in a snapshot observer; the tags add semantics only | `MainActivity.kt`, D10 sites | No new composition-time reads in the screens. Device run of AC-3 |

## Open questions

| # | Question | Recommended | Blocks |
|---|----------|-------------|--------|
| Q1 | May `playbackProfile` write watch positions to the account? | Yes, capped. Play the first card of Continue watching, which resumes and moves its mark by seconds. Set `maxIterations = 5` for that CUJ | yes (AC-4 player CUJ) |
| Q2 | Q2 names `emulator-5554` / `puber_tv_36`. This host sees only `host.docker.internal:5555` (`sdk_google_atv64_arm64`). Authorise that serial instead, after the owner pairs it and qa confirms API ≥ 34? | Yes | yes (AC-1-3, 5, 7) |
| Q3 | Should the guard hook refuse a bare `generateBaselineProfile` (`guard-bash.sh:55` pattern)? This is beyond the ACs | Yes, one rule pointing to `make baseline-profile`. The make targets are unaffected | no |
| Q4 | Pass the main checkout's `PUBER_CLIENT_SECRET`, `PUBER_API_DOMAIN` and TMDB token into the worktree Gradle process for these two targets (D3)? | Yes. Without it a token expiry mid-run logs the emulator out | yes (D3) |
| Q5 | Give `itest` the D5 exact-device check, to fix the leak reported in the watch-index-catch-up handoff? | Yes, as its own idea run | no |
| Q6 | Add the D15 step to `.claude/skills/release/SKILL.md`? The main checkout has uncommitted edits there | Yes, one line, after those edits land | no |
| Q7 | `/home/dev/Puber/.todo/baseline-profile-auth.env` holds stale tokens that nothing will read after D2 | The owner deletes it | no |
