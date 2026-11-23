# Research: startup-first-input-on-tv

## Request

From `state.md` (title and Next): "Measure and fix the first D-pad response after the splash reveal on a real TV": startup-splash-preload left AC-13 (reveal later than master) and AC-15 (first key after reveal 330-930 ms on swiftshader) waived; startup-settled-signal baseline showed every cold start revealing only at the 2.5 s cap although content and LaunchFocusSettled land 1.0-1.6 s in. Find why the reveal gate holds and what governs first-input latency.

## Understanding

On a cold start the splash covers Home until Home reports its launch focus settled and, in addition, no Coil request of the app's single image loader is in flight; a 2.5 s cap from `onCreate` overrides both. On the emulator Home is ready 1.0-1.6 s in, but the splash lifts at the cap in nearly every start, and the first keys after the reveal take 0.3-2 s. All numbers so far come from a software-GPU emulator under host load; nothing has been measured on the owner's TV box. The run must measure on the box (base vs. variants), find out which gate input holds the reveal and what delays the first key, and then fix it.

## Current state in code

Reveal gate:

- `app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:7`: `REVEAL_CAP_MS = 2_500`, counted from `begin()` (`MainActivity.kt:77`, i.e. `onCreate`).
- `RevealGate.kt:78`: `covers = begun && !forced && !revealedOnce && !(ready && !imagesBusy)`. Two inputs: `ready` and `imagesBusy`.
- `RevealGate.kt:45-50`: `markReady()` only takes effect after `afterFrame`; `StartupReveal.kt:27-29,61-66` makes that two Choreographer frames, installed at `PuberApp.kt:82`.
- `StartupImageActivity.kt:23-29`: a request is "in flight" from `resolveSizeEnd` to `onSuccess/onError/onCancel`, for every request of the loader, visible or not, started before or after `ready`. Hooked in at `PuberApp.kt:144`. Coil 3.6.3 calls `onCancel` only when the execute coroutine is cancelled (RealImageLoader, see External findings).
- Home's ready: `HomeScreenContent.kt:161-176` (`isLaunchFocusSettled`: `heroSettled` and hero claim resolved / hero empty / rows held focus) -> `HomeScreenContent.kt:227-230` -> `HomeVM.kt:220` -> `StartupReveal.markReady` (`HomeVM.kt:63`); Home opts out of the generic content-state mark (`HomeVM.kt:67`, `PuberVM.kt:94`).
- Cap: `MainActivity.kt:85-88` (`LaunchedEffect` delays `remainingCapMs()` on Main, then `onCap()`).
- `revealed` mark: `MainActivity.kt:90`, a `LaunchedEffect(covered)` that runs after the recomposition following the gate change, so it lags the gate edge by at least one frame.
- No telemetry records why the splash lifted (cap, ready, forced) or when `ready` and the image-idle edge happened: `StartupMarks.kt:16-18` holds only `app_composed`, `first_content`, `revealed`. "Revealed by cap" in all past evidence is inferred, not measured.

Fade and the work released after the reveal:

- `StartupSplashHost.kt:19,35-46`: 250 ms alpha fade on a `graphicsLayer`; `onGone` fires when the fade ends.
- `MainActivity.kt:93` -> `StartupSettled.onSplashGone` (`StartupSettled.kt:46-48`) releases deferred work about 250 ms after the reveal: details prefetch with no slot (`DetailsPrefetcher.kt:123`, `domain/di/modules.kt:82`), watch-state clear (`WatchStateRepository.kt:132`), then slots 500 ms apart: TV home (`MainVM.kt:67`), update check (`UpdatePromptVM.kt:59`), device info (`MainVM.kt:106`), watch-state sync (`MainVM.kt:92`); offsets at `StartupSettled.kt:12-19`. These land inside the 3 s window where first keys are measured.

Image work on Home:

- `HeroCarousel.kt:225-240`, `VideoItemUIState.kt:186-200`, `VideoItemHorizontal.kt:96-115`: requests with `crossfade(true)` and no explicit size or precision; size comes from layout constraints.
- `VideoItemUIMapper.kt:83-89`: horizontal cards use the `wide` poster (3840x2160 / 2160x1215 sources per card-image-size-tier); qa Round 1c trace: 15 card decodes of 4K-class sources to 533x300, about 3.2 s CPU on the emulator, plus 4 hero decodes to 1800x1013.
- `PuberApp.kt:109-145`: loader has no `bitmapFactoryMaxParallelism`, `allowHardware` or dispatcher settings, so Coil defaults (4 parallel decodes).
- `HeroCarousel.kt:101-108`: auto-scroll every 5 s only while the hero is unfocused; outside the 2.5 s window.

Key handling and latency probe:

- `MainActivity.kt:59-71` and `StartupKeyFilter.kt:22-33`: D-pad/OK dropped while covered; the filter returns `true`, so `dispatchWithProbe` reports `handled = true` and dropped keys arm the latency matcher too (`KeyProbe.kt:81-86`).
- `KeyProbe.kt:88-101`: `key_lat` = event time to the end of the UI part of the first frame that starts after dispatch; RenderThread time after that is not included. Logged only above 50 ms (`KeyProbe.kt:8`). A key whose next frame starts more than 1 s after dispatch is discarded (`EXPIRY_NS`, `KeyProbe.kt:9,94-96`), and a key within 1 s of a pending one is not armed (`KeyProbe.kt:84`). Past evidence counted unlogged keys as 50 ms, so the slowest keys can be scored as fast.

Tests around it: `RevealGateTest`, `StartupImageActivityTest`, `StartupRevealTest`, `StartupKeyFilterTest`, `StartupMarksTest`, `StartupSettledTest`, `HomeLaunchFocusSettledTest`, `LaunchFocusSettledRuleTest`, `StartupSplashHostTest` (androidTest), `DispatchWithProbeTest` (commit stats of 0b5c0669, b3cc6c5e, 08271680).

## Related work

| Run / source | Relevance |
|---|---|
| startup-splash-preload (done) | Built the gate (0b5c0669) and the image hold (b3cc6c5e). qa Round 1c: before the image hold, head revealed at a median of about 1.8 s, before the cap; Round 2 (with the hold): revealed median 2789, 5 of 12 starts at the cap. Round 2b atrace: after the reveal the main thread waits 1415 ms in `postAndWait` while RenderThread draws the first frame for 1539 ms, 1.4 s of it 12 `shader_compile` slices (software GL); one of 9 starts revealed with skeletons 8 ms after `first_content`; one content-link reveal at 4693 ms. `codex-consult-ac13-ac15.md`: no code change without real-TV data; measure release vs master 8428c946 at fixed and reveal-relative anchors. |
| startup-settled-signal (done, 08271680) | baseline.md: 8 of 8 starts inferred cap-revealed, `revealed` median 2857 ms, `key_lat` p95 1060 ms. After: 7 of 9 still near the cap. Added the post-splash slots listed above. |
| card-image-size-tier (idea) | The 4K wide-poster decode into cards; a direct cause of long `imagesBusy`. Overlaps option C. |
| release-startup-compilation (idea) | Stale baseline profile (since 1.0.0), JIT after in-app update; affects first-key cost after the reveal. |
| startup-main-thread-io (idea) | Prefs read on main before the first frame; minor for this request. |
| cache-decoded-memory-tier, home-decode-off-main (done / folded) | JSON decode moved off Main; removes one earlier explanation of startup key lag. |
| Box evidence | `docs/superpowers/evidence/2026-11-22-tv-launcher-channel-logo-verify-device.md:4`: the box is model KM6, Android 10. No startup, reveal or `key_lat` numbers exist for the box. |

Possible duplicate: none. Specs: `docs/superpowers/specs/2026-11-21-startup-splash-preload.md` (Q2 and AC-4 amended, AC-13, AC-15 at lines 236-286), `docs/superpowers/specs/2026-11-21-startup-settled-signal.md`.

## External findings

| Finding | Source |
|---|---|
| Coil 3.6.3 is the newest 3.6 release (2026-09-18); 3.6.0 added `allowPartialImage` (on by default) for `StaticImageDecoder`; 3.6.2 fixed a hang in the experimental `DeDupeConcurrentRequestStrategy` (off by default). Nothing in 3.4-3.6 changes `EventListener` or hardware bitmaps. | https://coil-kt.github.io/coil/changelog/ |
| `DEFAULT_MAX_PARALLELISM = 4`; one semaphore gates `BitmapFactory`/`ImageDecoder` decodes; `ImageLoader.Builder.bitmapFactoryMaxParallelism(Int)` changes it. | https://raw.githubusercontent.com/coil-kt/coil/3.6.3/coil-core/src/androidMain/kotlin/coil3/decode/BitmapFactoryDecoder.kt ; https://coil-kt.github.io/coil/api/coil-core/coil3/-image-loader/-builder/index.html |
| Order in `execute()`: `onStart`, `resolveSizeStart`, `resolveSizeEnd`, interceptor chain (memory cache, fetch, decode), then `onSuccess`/`onError`; `onCancel` only on `CancellationException`. So a memory-cache hit still marks the request in flight briefly, and a decode keeps it in flight for the whole decode. | https://raw.githubusercontent.com/coil-kt/coil/3.6.3/coil-core/src/commonMain/kotlin/coil3/RealImageLoader.kt |
| `AsyncImage` resolves size from layout constraints and delays the request until constraints are positive; so a request enters "in flight" only after measure, which can be after the two-frame ready wait (fits Round 2b's skeleton reveal). | https://coil-kt.github.io/coil/compose/ |
| First-use shader compilation shows up as RenderThread compile slices, typically tens of ms each on hardware GPUs (secondary sources only; no official figure for HWUI's persistent shader cache was found). The 1.4 s seen on swiftshader is therefore not expected to carry over to the box. `unverified` for the KM6 GPU. | https://dev.to/software_mvp-factory/fixing-android-jank-you-cant-see-4p64 |

## Options

| Option | What | Trade-offs | Size |
|---|---|---|---|
| A. Measure only | Add reveal-cause telemetry (cap / ready / forced; times of the ready mark, of the last image-idle edge and the in-flight count at the cap) and fix the `key_lat` blind spot (`KeyProbe.kt:84,94-96`), then a box A/B of base 2fd851a4: cold starts, fixed and reveal-relative key anchors, atrace of a few starts. Fixes go to follow-up runs. | Cheap and answers "why the gate holds" on real hardware; leaves the user-visible delay as is for another run. | S |
| B. Measure, then a bounded gate and a quiet window (recommended) | A, plus: the image wait counts only requests resolved by the time `ready` lands (later requests cannot extend it) and ends a fixed grace after `ready` (value set from the box baseline); post-splash work (`StartupSettled`) is released later than the fade end so prefetch and slots do not hit the first keys. Then controlled box experiments on the remaining levers: no or shorter fade, `bitmapFactoryMaxParallelism(2)`. | Brings the reveal back to Home readiness on a slow device while keeping most images drawn; risk of skeletons for late requests (AC-4 amended of the parent run) and of shifting work into the first keys. Touches startup, telemetry and the image loader (risk area). | M |
| C. Cut the image work | Card-size requests or a card-size disk tier for wide posters (card-image-size-tier), size hints on hero/cards, lower decode parallelism. | Attacks the decode burst that both holds the gate and slows post-reveal frames; data/cache-shape change and overlaps an existing idea run; does not by itself say why the gate holds on the box. | L |

Recommended: B, with A's telemetry and box baseline as its first step; C stays in card-image-size-tier and is started only if the box shows decode as the dominant cost.

## Risks

- Box numbers may differ completely from the emulator: hardware GPU (shader compile cheaper), weaker CPU (decodes slower). The gate may not hit the cap on the box at all; the baseline must come first, and ACs set from it.
- Measurement bias: the 1 s expiry and 1 s arm guard in `KeyProbe` drop the slowest keys and earlier evidence scored them as 50 ms; comparisons with past numbers are invalid unless the probe is fixed in both builds or keys are timed externally (e.g. atrace `deliverInputEvent`).
- A bounded image wait reintroduces skeletons in the first frames after the fade (Round 1c's sluggish first interaction, codex consult point on option C).
- Moving `StartupSettled` later delays the TV home channel, update prompt and watch-state sync; `StartupSettled` already has a 5 s fallback (`StartupSettled.kt:28`).
- The box is a shared, logged-in device with no display re-pairing (AGENTS.md); every A/B install is install-preserve of `com.kino.puber.stage` only, with `auth-save` first.

## Open questions

| ID | Question | Recommended answer |
|---|---|---|
| Q1 | Base for the A/B: current master 2fd851a4, or also pre-splash master 8428c946 as the codex consult suggested? | 2fd851a4 as the base; one set of 8428c946 only as a reference for "launch to visible content", not gated. |
| Q2 | Targets on the box: what must hold after the fix? | Set from the box baseline: no start revealed by the cap when Home's ready mark lands before it; `revealed` median not above ready median + 300 ms; first key after the reveal p95 <= 199 ms at a reveal-relative anchor (same threshold as the parent's AC-15), with dropped-under-splash keys reported. |
| Q3 | Keep the image-aware hold, or reveal on Home readiness only? | Keep it, bounded (option B): only requests resolved by the ready mark count, with a grace from the box baseline. |
| Q4 | May this run add permanent telemetry fields (reveal cause, ready and image-idle times) and change `KeyProbe` expiry? | Yes: they are the only way to tell cap from ready on any device and to stop scoring slow keys as 50 ms. |

## Sources

- Code: `core/startup/RevealGate.kt`, `StartupReveal.kt`, `StartupImageActivity.kt`, `StartupImageLoader.kt`, `StartupKeyFilter.kt`, `StartupSettled.kt`, `AppStartup.kt`; `MainActivity.kt`; `PuberApp.kt`; `ui/feature/root/component/StartupSplashHost.kt`; `ui/feature/home/component/HomeScreenContent.kt`; `ui/feature/home/vm/HomeVM.kt`; `core/ui/PuberVM.kt`; `core/telemetry/StartupMarks.kt`, `KeyProbe.kt`; `core/ui/uikit/component/HeroCarousel.kt`, `SkeletonAsyncImage.kt`, `moviesList/VideoItemUIState.kt`, `moviesList/VideoItemHorizontal.kt`; `core/ui/model/VideoItemUIMapper.kt`; `domain/di/modules.kt`; `ui/feature/main/vm/MainVM.kt`; `ui/feature/update/vm/UpdatePromptVM.kt`; `data/repository/WatchStateRepository.kt`; `gradle/libs.versions.toml` (coil 3.6.3, compose BOM 2026.09.00, tv-material 1.1.0).
- Commits: 0b5c0669, b3cc6c5e, 08271680, 8428c946.
- Runs: startup-splash-preload (qa-report.md Rounds 1c, 2, 2b; codex-consult-ac13-ac15.md), startup-settled-signal (state.md, baseline.md), card-image-size-tier, release-startup-compilation, startup-main-thread-io, cache-decoded-memory-tier, home-decode-off-main, cache-layer-rework, cache-targeted-invalidation.
- Docs: `docs/superpowers/specs/2026-11-21-startup-splash-preload.md`, `docs/superpowers/evidence/2026-11-21-startup-settled-signal-device.md`, `docs/superpowers/evidence/2026-11-21-cache-shared-http-stack-device.md`, `docs/superpowers/evidence/2026-11-22-tv-launcher-channel-logo-verify-device.md`.
- URLs: as in External findings.
