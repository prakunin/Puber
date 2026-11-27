# Brief: player-hermetic-fixtures

Kind: improvement. Size: M. Tracks: none. Device verification: yes (instrumented tests on a device; which device needs the owner, Q1).
Research: research.md (sections 3-8); option A taken. Checked on master f53f2144.

## Request

Port upstream P6 Part A (triage T10; upstream fe6224fb, 1d3ec39e): a `:player-test-fixtures` module with synthetic MP4/HLS/WebVTT, a loopback media server and request journal, one network blocker, controller-level instrumented tests against the real `PlaybackController` and ExoPlayer, `docs/testing/player-playback.md` and `tools/generate-player-test-fixtures.sh`. Inside it, run the stale-callback probe (spec D25) and record whether the defect reproduces. Design choices are the triage spec's D20-D25 and D28 (`git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md`); research.md: 4 checked them against master.

## Why M, not L or S

One goal (a hermetic player test kit plus the probe it hosts). Production player code stays unchanged (AC-7). It still crosses the Gradle build (new module, plugin alias, `settings.gradle.kts`, `app/build.gradle.kts:439-444` dependencies), `app/src/instrumentation` (blocker), `Makefile` and CI, and it exercises playback and build variants, which are risk areas. Moved out of this run:

| Item | Where it goes | Condition |
|---|---|---|
| Part B: Compose E2E/focus tests, `PuberVM` router rebind, test tags (D27) | new idea row `player-compose-e2e-tests`, `blocked_by: player-overlays-port` | that port is stalled, 504 commits behind (research.md: 4) |
| Gate fix: `PlaybackCallbackGate` + one listener per media session (D26) | new run `player-callback-gate` | only if AC-5 reproduces the stale callback |
| upstream 7c30221e | nothing | `MainVM.kt` merge fix, not player (research.md: 4) |

## Code facts this run relies on

| Fact | Where |
|---|---|
| Constructor `(context, okHttpClient, mediaCache, playerPreferencesRepository)`; DI builds it in the screen scope | `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:160`, `ui/feature/player/component/PlayerScreen.kt:58` |
| One `playerListener` for the controller's life; `onPlayerError` attributes failures to `currentStreamUrl` at dispatch time | `PlaybackController.kt:214-271`, `:260-264` |
| `switchStream` reuses player and listener, sets `currentStreamUrl` before `prepare()` | `PlaybackController.kt:499-519` |
| `release` removes both listeners before `player.release()` | `PlaybackController.kt:581-598` |
| `SeekStallRecovery` posts `onStreamFailure` through a main-thread `Handler` | `PlaybackController.kt:184-196` |
| VM drops an `onStreamFailure` for a non-current URL; `onError` is not generation-checked | `ui/feature/player/vm/PlayerVM.kt:273`, `:282` |
| Existing blocker: a default `ProxySelector` with an origin allow-list and file journal | `app/src/instrumentation/java/com/kino/puber/profile/BaselineNetworkBlocker.kt:19-48` |
| `mockwebserver3` is already in the catalog | `gradle/libs.versions.toml:89` |
| `instrumentation` flavor, `.instrumentation` suffix | `app/build.gradle.kts:239-248` |
| `make itest` runs `connected$(VARIANT)AndroidTest`; `PACKAGE_instrumentation` missing; `build` and `itest` call `$(GRADLE)` (default `./gradlew`) | `Makefile:25-27`, `:73-74`, `:131-141` |
| CI unit job runs only `testProdDebugUnitTest` | `.github/workflows/pr-checks.yml:95` |
| `docs/testing/` does not exist; `docs/architecture/testing.md` covers JVM tests only | research.md: 3 |

Stale callback: implausible on code reading (ListenerSet drops events queued for a removed listener; ExoPlayer masks old-media updates across `setMediaItem`/`prepare`), residual windows at `:260-264` and the stall handler post. Not proven absent; AC-5 is the check (research.md: 5).

## Acceptance criteria

- AC-1. Given the repo after the change, when `./tools/agentw :player-test-fixtures:testDebugUnitTest` runs, then JUnit 5 tests pass that show `HermeticTestServer` serves byte-range requests, the request journal records each request, and every asset matches `SHA256SUMS`.
- AC-2. Given `app/build.gradle.kts`, when the dependency graph of `devDebug` and `prodRelease` is printed (`:app:dependencies --configuration devDebugRuntimeClasspath` and `prodReleaseRuntimeClasspath`), then neither contains `:player-test-fixtures` or `mockwebserver3`; the module is consumed only by the instrumentation flavor's test configurations.
- AC-3. Given the instrumentation flavor, when `BaselineNetworkBlockerTest` and a new blocker test run (`./tools/agentw :app:testInstrumentationDebugUnitTest --tests 'com.kino.puber.profile.*'`), then a loopback origin of the media server is allowed and a non-loopback host gets the deny proxy and is journaled; there is one `ProxySelector` implementation (D22).
- AC-4. Given a device (Q1), when `make FLAVOR=instrumentation DEVICE=<serial> GRADLE=./tools/agentw itest TESTS=<PlaybackControllerDeviceTest>` runs, then the synthetic MP4 and HLS fixtures reach `STATE_READY`, position advances, and playback ends, served only by the loopback server; the network journal is empty after the run.
- AC-5. Given the same device, when `PlaybackControllerStaleCallbackTest` runs (stream A answers with a delayed failure, `switchStream` to B before it lands), then the result is recorded in evidence whether it passes or fails: pass = "not reproduced" and the test stays as a regression test; fail = run `player-callback-gate` is opened and the failing test is committed `@Ignore`d with that slug.
- AC-6. Given upstream's NetworkTracks and Resources device tests, when the port ends, then each upstream test name appears in `docs/testing/player-playback.md` as ported or "not ported: needs a production change" (D24).
- AC-7. Given the branch, when `git diff master --stat -- app/src/main` runs, then it is empty.
- AC-8. Given `docs/testing/player-playback.md`, when read, then it names the exact `make ... itest TESTS=` command, the device rule from Q1, how fixtures are checked (`sha256sum -c SHA256SUMS`), and that `tools/generate-player-test-fixtures.sh` is a maintainer tool not run in the gate.
- AC-9. Given `.flow/config.yaml` `check.full` and `.github/workflows/pr-checks.yml`, when read, then both run `:player-test-fixtures:testDebugUnitTest`, and `check.full` passes in the worktree.

Evidence for AC-4 and AC-5 is headed "loopback fixtures" (D28). Until the owner answers Q1, AC-4 and AC-5 are reported `NOT CHECKED: device not authorized`; the run does not reach gate 2 with them unchecked unless the owner accepts that.

## Risks

- Device authorization. The tests install `com.kino.puber.instrumentation` and its test APK. The box's standing permission covers only `com.kino.puber.stage`; the emulator is allowed for baseline-profile generation only. No device action outside those without the owner's answer to Q1.
- Blocker reach (plausible, from OkHttp's builder): an `OkHttpClient` takes `ProxySelector.getDefault()` when it is built, so a client built before the blocker is installed bypasses it. Tests build their own client after install and assert the journal (AC-4).
- `make itest` and `build` call `./gradlew` by default; in the worktree pass `GRADLE=./tools/agentw` and `AGENT_GRADLE_SERIAL=1`.
- Timing on the device: delayed-segment and decoder tests may flake; use generous timeouts, a per-test `SimpleCache` directory and release in teardown.
- Fixtures: about 1.7 MB of binary assets copied byte-for-byte from upstream 1d3ec39e; `ffmpeg` is not in the dev box, so bytes plus `SHA256SUMS` are the source of truth (research.md: 7).
- No test or script touches the live KinoPub API; all media come from the loopback server.

## Open questions

| ID | Question | Recommended | Blocks |
|---|---|---|---|
| Q1 | Which device may install and run `com.kino.puber.instrumentation` + its test APK through `make FLAVOR=instrumentation itest`? This is outside every standing permission. | Emulator `host.docker.internal:5555` (it already hosts that package for baseline generation; no pairing needed; leaves the box untouched). Until the owner authorizes it, AC-4 and AC-5 stay `NOT CHECKED`; the conductor asks the owner and does not take this answer by the standing rule. | yes |
| Q2 | Drop Part B from this run and open `player-compose-e2e-tests` blocked by `player-overlays-port`? | Yes | no |
| Q3 | Take upstream's fixture bytes without regenerating them? | Yes; verify against `SHA256SUMS`; the generator ships as a maintainer tool | no |
