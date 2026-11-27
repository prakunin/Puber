# Research: player-hermetic-fixtures

## 1. Request

State title (`.flow/runs/player-hermetic-fixtures/state.md`): "Add hermetic player test fixtures (synthetic MP4/HLS/VTT, loopback server, network blocker) and check the stale-callback gate".

Log, verbatim:

> Port P6 (triage T10), upstream fe6224fb, aa9ffb47, 1d3ec39e, 7c30221e. Its blockers are done (subtitle-upstream-port, subtitle-sideload-merge, subtitle-restore-model). Part A: `player-test-fixtures` module, loopback server, network blocker, `tools/generate-player-test-fixtures.sh`, `docs/testing/player-playback.md`, controller-level instrumented tests run through `make itest` (no Kaspresso, no MockWebServer in our catalog). Gated item: run the logic of upstream's PlaybackControllerCallbackGenerationTest / PlayerVMCallbackGenerationTest against our PlaybackController (930 lines); port `PlaybackCallbackGate` only if the stale callback after a media rebuild reproduces. Part B (Compose E2E and focus tests, PuberVM router rebind) only after player-overlays-port lands. Likely size L: split in triage. Related: playback-controller-split (upstream's policy split as reference).

## 2. Understanding

Bring upstream's hermetic player test kit into the fork: a `:player-test-fixtures` library with synthetic MP4, HLS and WebVTT assets, a loopback HTTP server and a network blocker, plus instrumented tests that drive the real `PlaybackController` and ExoPlayer against it with no real network. Inside that work, decide whether the fork has the defect upstream's `PlaybackCallbackGate` fixes (a player callback for the previous media reaching the VM after a rebuild) and port the gate only if it does. Compose E2E and focus tests (Part B) wait for `player-overlays-port`. The triage spec already fixes most design choices (D20 to D28); this research checks them against master f53f2144.

## 3. Current state in code

Player:

- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:160`: constructor `(context, okHttpClient, mediaCache, playerPreferencesRepository)`, 930 lines; the spec cites `:149`, now `:160`.
- `PlaybackController.kt:214-271`: one `playerListener` object for the controller's life; `onPlayerError` builds `onStreamFailure` with `streamUrl = currentStreamUrl` (`:260-264`), so the URL is the one current at dispatch time, not the one that failed.
- `PlaybackController.kt:378-449` `prepare()`: calls `release()` first, builds a new `ExoPlayer` and adds the listener (`:428`).
- `PlaybackController.kt:581-598` `release()`: `removeListener(playerListener)` and `removeAnalyticsListener` before `player.release()`.
- `PlaybackController.kt:499-519` `switchStream()`: same player and listener; `setMediaItem(..., savedPosition)` then `prepare()`; `currentStreamUrl` is updated before `prepare()`.
- `PlaybackController.kt:184-196`: `SeekStallRecovery.onStalled` reports `onStreamFailure` through a main-thread `Handler`; it is cancelled in `release()` and re-armed by `onStreamSwitched` (`:518`).
- `PlaybackController.kt:670-722`: the warm-up player has its own anonymous listener that never touches `callback`.
- `PlaybackController.kt:600-620` `disableAc3AndRetry()`: the AC3 retry-once fallback (upstream's `Ac3FallbackPolicy`) is inline.
- `PlaybackMediaItemFactory.kt` (94 lines) already exists in the fork, so upstream's `PlaybackMediaItemFactory` has already been ported.
- `app/src/main/java/com/kino/puber/ui/feature/player/vm/PlayerVM.kt:213-285` `playbackCallback`: `onStreamFailure` drops a URL that is not the current candidate (`:273`). `onError` (`:282`), `onPlaybackEnded`, `onTracksUpdated` and `onPlaybackStateChanged` do not check the media generation.
- `PlayerVM.kt:139, 307, 401-403, 2022`: `mediaGeneration` guards only the async prepare (`isCurrentPrepare`) and exit, not controller callbacks.

Test infrastructure on master:

| Piece | Where | Fit for P6 |
|---|---|---|
| `instrumentation` flavor, `.instrumentation` suffix, mock ports from `gradle.properties` | `app/build.gradle.kts:239-248` | Host flavor for hermetic tests (spec D13, D23) |
| `BaselineProfileApp` replaces the application in that flavor | `app/src/instrumentation/AndroidManifest.xml` | Tests in `androidTestInstrumentation` run under it |
| `BaselineNetworkBlocker` (a `ProxySelector` with an allow-list of origins and a file journal) | `app/src/instrumentation/java/com/kino/puber/profile/BaselineNetworkBlocker.kt:19-48` | Already the blocker that spec D22 asks for; it needs the loopback server origin added |
| JVM tests for the instrumentation flavor | `app/src/testInstrumentation/kotlin/.../profile/` (5 tests) | Already in `check.full` (`.flow/config.yaml`) |
| `mockwebserver3`, `okhttp-tls` in the catalog | `gradle/libs.versions.toml:89-90`; used only by `baselineprofile/build.gradle.kts` | The spec's "no MockWebServer in our catalog" is out of date; upstream's fixtures module uses `libs.mockwebserver3` |
| `BaselineMockBackend` (MockWebServer 3, HTTP/1.1 and HTTP/2) | `baselineprofile/src/main/java/com/kino/puber/baselineprofile/BaselineMockBackend.kt` (444 lines) | API mock for profile generation, separate from the media server P6 needs |
| `make itest` | `Makefile:131-141` | `FLAVOR=instrumentation` gives `connectedInstrumentationDebugAndroidTest`; `PACKAGE_instrumentation` is missing (`Makefile:25-27`), but `itest` does not use `PACKAGE` |
| Instrumented tests | `app/src/androidTest/kotlin/...`, plain `AndroidJUnit4` + `createComposeRule`; player: `PlayerAboutPanelTest`, `PlayerLaunchFocusGateTest`, `PlayerPromptFocusAndKeysTest` | No controller-level or media test yet; no Kaspresso and no Robolectric |
| `androidTest` dependencies | `app/build.gradle.kts:439-444` | No `instrumentationImplementation` / `androidTestInstrumentationImplementation` yet |
| `settings.gradle.kts` | includes `:app` and `:baselineprofile` | `:player-test-fixtures` and the android-library plugin alias are new |

Testing doc: `docs/architecture/testing.md` describes only JVM tests on JUnit 5. It says nothing about `make itest` or the instrumentation flavor.

## 4. Related work

| Item | Status | Relevance |
|---|---|---|
| `upstream-sync-triage` spec, T10 / P6, D20-D28, AC-17..19 | branch `upstream-sync-triage`, stage impl | Settled design: plain AndroidJUnit4 (D20), module layout (D21), one blocker (D22), `androidTestInstrumentation` + `make FLAVOR=instrumentation itest` (D23), controller test scope (D24), instrumented stale-callback probe instead of upstream's Robolectric tests (D25), gate port only if the probe reproduces (D26), Part B gated (D27), mock-evidence labelling (D28) |
| `hermetic-baseline-seams`, `hermetic-baseline-generation`, `baseline-generation-hardening`, `baseline-mock-protocol-parity` | done, merged 2026-11-26 | Delivered P5 (instrumentation flavor, blocker, mock); this is the base P6 assumed |
| `hermetic-baseline-profiles` | stage idea, split into the runs above | Its parent row; done by the split |
| `subtitle-upstream-port`, `subtitle-sideload-merge`, `subtitle-restore-model` | done | The P6 blockers in the spec's conflict rule are cleared |
| `player-overlays-port` | stage impl since 2026-10-12; branch is 1 commit (92a9441d, 2026-10-10) ahead and 504 behind master; not merged; diff touches `player/component/*` and `docs/design/player-overlays/` | Part B dependency. It is stalled, and master has since moved the player panels (bfb93a46 lifted side panel and glass surface into uikit), so Part B stays blocked for the foreseeable future |
| `playback-controller-split` | idea, M | Upstream's policy split as reference; P6 controller tests become its safety net |
| `player-vm-split`, `player-ended-play-hardening`, `baseline-player-track-coverage` | idea | Same files; could reuse the fixtures later. Not duplicates |
| upstream 7c30221e | ‐ | Touches only `MainVM.kt` and `config/detekt/detekt-baseline.xml` (an upstream merge fix, T12 territory); nothing to port for P6 |

Duplicate: none.

Upstream commits (`git show --stat`):

| Commit | Content |
|---|---|
| fe6224fb | 68 files, +6647: `player-test-fixtures` module (assets about 1.7 MB in 1d3ec39e, `HermeticTestServer` 522 lines, request journal, `LoopbackNetworkBlocker`/`Journal`), `PlaybackController*` device tests (Device 444, NetworkTracks 860, Resources 752), `PlayerScreenE2ETest`, `PlaybackCallbackGate` + controller rewiring (182 lines changed), `Ac3FallbackPolicy`, `PuberVM` router rebind, Robolectric `PlaybackControllerCallbackGenerationTest` and `PlayerVMCallbackGenerationTest`, `docs/testing/player-playback.md`, `tools/generate-player-test-fixtures.sh`, `tools/resolve-android-test-apk-pair` |
| aa9ffb47 | Kaspresso base `PlayerInstrumentationTestCase`, Compose screen objects, test tags in player components, docs rewrite |
| 1d3ec39e | Kaspresso refinement: `PlayerTvRobot`, rewritten device and E2E tests, more test tags in 8 player components |
| 7c30221e | Not player: see the row above |

Upstream test names in 1d3ec39e: Device (`progressiveMp4_reachesReady...`, `hlsVod_reachesReady...`, `progressivePlayback_controlsAndStartPosition...`, two fail-closed checks); NetworkTracks (track selection and cues, early codec tracks, switch preserving position/intent/tracks, 404/malformed playlist, variant 400/502 fallback, delayed segment, redirect isolation); Resources (cache replay, corrupt cache entry, lifecycle, decoder counters, release during blocked load, bounded prepare/seek/release loop).

## 5. External findings

| Finding | Source |
|---|---|
| `ListenerSet.remove` releases the listener's holder, and a queued event is invoked only `if (!released)`, so an event already queued for a removed listener is not delivered | androidx/media 1.11.0 (the project's `media3` version, `libs.versions.toml:27`), `ListenerSet.java` lines 284-291 and 439-467: https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/common/src/main/java/androidx/media3/common/util/ListenerSet.java |
| `ExoPlayerImpl.handlePlaybackInfo` applies an internal-thread update only when `pendingOperationAcks == 0`; a `setMediaItem`/`prepare` issued on the app thread raises the count, so a state or error update produced before them is masked and never reaches listeners. Exact behaviour for an error raised between those two calls: `unverified` | androidx/media 1.11.0, `ExoPlayerImpl.java` line 2228: https://raw.githubusercontent.com/androidx/media/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayerImpl.java |

Assessment of the stale callback in the fork. After `prepare()` the old player's listener is removed before release (`PlaybackController.kt:586`), and ListenerSet drops its queued events. After `switchStream()` the listener is the same, and ExoPlayer's operation masking drops old-media updates. Upstream's controller test calls removed listeners by hand (`listenerA.onPlayerError(...)` after removal), which a real ExoPlayer does not do. It guards upstream's own design of one listener per switch. The fork's plausible residual windows are narrower. The first is the attribution of an error to `currentStreamUrl` (`:260-264`) if an old-media error escaped masking. The second is `SeekStallRecovery`'s handler post. The defect is therefore implausible but not proven absent; the D25 probe is the check.

## 6. Options

| Option | Scope | Size | Trade-offs |
|---|---|---|---|
| A (recommended) | Part A plus the D25 probe in one run: `:player-test-fixtures` (upstream assets byte-for-byte with `SHA256SUMS`, server, journal), blocker allow-list extended or delegated (D22), `androidTestInstrumentation` controller tests per D24, `PlaybackControllerStaleCallbackTest` (D25), `docs/testing/player-playback.md`, generator script kept as maintainer tool, `:player-test-fixtures` unit tests added to `check.full`. No production player edit. Gate fix (D26) is a follow-up run only if the probe fails. Part B goes to a new idea row blocked by `player-overlays-port` | M | One shippable goal; reuses the P5 flavor and blocker. The controller tests are a rewrite, not a cherry-pick (Kaspresso removed, fork API), which is most of the effort. Touches the Gradle build (new module, plugin alias) |
| B | As A, but include the gate fix (D26: `PlaybackCallbackGate`, one listener per media session) regardless of the probe | L | Matches upstream more closely, but changes production playback with no reproduced defect. That is against AC-18 and makes `playback-controller-split` conflict |
| C | Close the gate by code analysis only (section 5), port just the fixtures module and the Device test (MP4 and HLS reach READY and end), skip NetworkTracks/Resources | S-M | Smallest and fastest. Leaves the stale-callback question unproven, and the fault-injection value (variant fallback, delayed segment) is lost |

## 7. Risks

- **Device choice.** The tests install `com.kino.puber.instrumentation` and its test APK. The owner's standing box authorization covers only `com.kino.puber.stage` (AGENTS.md), and the emulator is allowed for baseline generation only (memory, 2026-11-26). Without a decision, AC-17 cannot be run.
- **Box hardware (API 29).** Decoder and timing tests (decoder counters, delayed segment, rendered first frame) may behave differently on the box than on the API 36 emulator. Fences and timeouts must be generous, and flakiness is likely on the first runs.
- **Fixture regeneration.** `ffmpeg` is not in the dev box (hermetic-baseline-generation research). Upstream's docs pin `ffmpeg 9.0.1`, which is `unverified` as a real release. Byte reproducibility of the script cannot be checked here, so committed bytes plus `SHA256SUMS` are the source of truth.
- **Blocker scope.** ExoPlayer's OkHttp data source uses the app's `okHttpClient`. Whether its proxy honours the default `ProxySelector` that `BaselineNetworkBlocker` installs depends on how `HttpStack` builds the client (`unverified`). Tests that build their own client must route through the blocker, or the "no real network" claim fails.
- **Memory and parallel runs.** Each test creates an ExoPlayer and a `SimpleCache`. A per-test cache directory and release in teardown are needed (upstream does this).
- **Production edits leaking in.** Upstream's tests rely on test tags and `PuberVM` changes (Part B). Part A must not touch `player/component/*` (AC-17).

## 8. Open questions

| # | Question | Recommended answer |
|---|---|---|
| Q1 | Which device runs the hermetic player tests, given that they install `com.kino.puber.instrumentation`? | Allow `com.kino.puber.instrumentation` plus its test APK on the box `192.168.1.110:5555` through `make FLAVOR=instrumentation DEVICE=192.168.1.110:5555 itest`. The tests need no login and leave `.stage` alone. Use the emulator only as a fallback when the box is offline |
| Q2 | Part B depends on `player-overlays-port`, which has been stalled since 2026-10-10 and is 504 commits behind master. Keep it in this run? | No. Drop it from this run and record a new idea row (`player-compose-e2e-tests`), `blocked_by: player-overlays-port` |
| Q3 | Does the gated item close with the instrumented probe alone? | Yes, per D25. If `PlaybackControllerStaleCallbackTest` passes on current code, record "not reproduced" with the probe kept as a regression test. If it fails, open a separate run for D26 |
| Q4 | Take upstream's fixture bytes without regenerating them? | Yes. Copy the assets and verify them against `SHA256SUMS`; ship the script as a maintainer tool, not run in the gate |

## 9. Sources

- Run files: `.flow/runs/player-hermetic-fixtures/state.md`, `.flow/runs/{player-overlays-port,playback-controller-split,hermetic-baseline-profiles,hermetic-baseline-seams,hermetic-baseline-generation,baseline-generation-hardening,subtitle-upstream-port,subtitle-sideload-merge,subtitle-restore-model,upstream-sync-triage}/state.md`
- Spec: `git show upstream-sync-triage:docs/superpowers/specs/2026-10-11-upstream-sync-triage-design.md` (T10, P6, D13, D20-D28, AC-17..19)
- Code: `PlaybackController.kt`, `PlayerVM.kt`, `app/build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `Makefile`, `app/src/instrumentation/**`, `app/src/testInstrumentation/**`, `app/src/androidTest/**`, `docs/architecture/testing.md`, `.flow/config.yaml`, `AGENTS.md`
- Commits: upstream fe6224fb, aa9ffb47, 1d3ec39e, 7c30221e; fork 92a9441d (player-overlays-port), bfb93a46, 6cd733f4, 7d5cf63f, 624a8247
- URLs: the two androidx/media 1.11.0 raw sources in section 5 (fetched 2026-11-27)
