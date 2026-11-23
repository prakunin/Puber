# Research: startup-reveal-gate-on-slow-tv

Base master 9158cf82. Paths under `app/src/main/java/com/kino/puber/` unless they start with `app/`, `docs/` or `.flow/`.

## 1. Request

"Make the reveal gate fit a slow TV: cap value, bounded image hold, deferred-work window. The run's state.md Next and Log describe the origin (deferred AC-4, AC-5, AC-7 of startup-first-input-on-tv; box baseline 36/36 starts reveal by the 2.5 s cap)."

## 2. Understanding

The startup splash lifts when Home has marked ready and no image request of the whole loader is in flight, or at a 2.5 s cap. The request was filed on an uncompiled box baseline (36/36 cap reveals, first key ~3.2 s). Since then release-startup-compilation shipped an all-hot baseline profile: in the compiled state (S1) the box now reveals by `ready` in 13/15 and 43/45 starts, Home marks ready at ~1.5-1.9 s, and the splash then waits a further ~0.4-1.0 s for images. The cap fires only when that image wait runs past it, and those cap starts carry the only first keys over 199 ms. In the uncompiled state (S0: fresh install or in-app update before background dexopt) every start still reveals by the cap, before Home is ready. The run has to decide what the gate should do with each of those two states.

## 3. Current state in code

| Item | Where | What it does now |
|---|---|---|
| Cap value | `core/startup/RevealGate.kt:9` | `REVEAL_CAP_MS = 2_500L`, counted from `begin()` (onCreate) |
| Cap timer | `MainActivity.kt:83-86` | `LaunchedEffect` delays `remainingCapMs()` then `onCap()`; runs on the main thread, so a busy main thread can postpone it |
| Cover rule | `core/startup/RevealGate.kt:109` | covered while `begun && forcedCause == null && !revealedOnce && !(ready && inFlight.isEmpty())`: waits for every in-flight request of the loader, not only those present at `ready` |
| Held set | `core/startup/RevealGate.kt:34,64,83-85` | Requests in flight when `ready` takes effect are copied to `held`; used only for the `ready_drained` telemetry edge, not for the cover rule |
| Ready mark | `core/startup/RevealGate.kt:54-70`, `core/startup/StartupReveal.kt:228-234` | `markReady` waits a main-looper post plus two Choreographer frames before `ready` |
| Image in flight | `core/startup/StartupImageActivity.kt` (Coil `EventListener`), installed by `core/startup/StartupImageLoader.kt` | From `resolveSizeEnd` to success, error or cancel |
| Reveal causes | `core/startup/RevealObserver.kt:6` | `ready` / `cap` / `forced`; no grace cause exists |
| Fade | `ui/feature/root/component/StartupSplashHost.kt:19,35-37` | `REVEAL_FADE_MS = 250`; `onGone` after the fade |
| Deferred work latch | `core/startup/StartupSettled.kt:14-20,58-60` | Completes at `onSplashGone` (`MainActivity.kt:91`), on restore, or 5 s fallback; slots at +0.5 s steps: TvHome, UpdateCheck, DeviceInfo, WatchState |
| Slot users | `ui/feature/main/vm/MainVM.kt:67,99,115`, `ui/feature/update/vm/UpdatePromptVM.kt:59` | TvHome refresh +0.5 s, update check +1.0 s, device info +1.5 s, watch-state catch-up and sync +2.0 s |
| Latch-only users | `domain/di/modules.kt:83` (DetailsPrefetcher), `data/repository/WatchStateRepository.kt:158` (clearAllPending) | Start at the latch, no slot offset |
| New since the last box measurement | `183c10b1` (watch-index-catch-up) | Adds `catchUp` before `syncIfStale` in the WatchState slot (`MainVM.kt:96-103`); not measured on the box |
| Tests | `app/src/test/kotlin/com/kino/puber/core/startup/` | `RevealGateTest` (24 tests), `StartupSettledTest`, `StartupRevealTest`, `StartupImageActivityTest`, `StartupKeyFilterTest`, `RecordingRevealObserver`; telemetry side in `StartupMarksTest`, `TelemetryEncoderTest` |

Box numbers, compiled state S1 (`docs/superpowers/evidence/2026-11-23-startup-first-input-on-tv-verify-5-device.md`; `docs/superpowers/evidence/2026-11-23-release-startup-compilation-device.md` rounds 2-4):

| Measure | S1 (speed-profile, all-H profile) | S0 (as installed, `reason=install`) |
|---|---|---|
| Reveal cause | `ready` 13/15 (verify-5), 43/45 (release-startup-compilation) | `cap` 15/15 |
| `first_content` | ~0.8-1.0 s | ~1.2-1.6 s |
| `ready` | ~1.4-2.2 s | ~3.4-4.7 s, after the cap |
| `ready` to `revealed` (image wait) | 389-956 ms in ready starts (verify-5); `ready_drained` precedes `revealed` by 9-19 ms | n/a (cap first) |
| Cap starts | `ready` before the cap, held images drained after it (verify-5 rows 2, 14: drained 2866 / 2717 vs revealed 2629 / 2628) | every start |
| First key | <= 50 ms in most starts; the over-199 ms keys (131, 215, 292 ms) are all cap starts | median 2.0-2.7 s, p95 ~3.0-3.5 s |

## 4. Related work

| Run / spec | Relation |
|---|---|
| `startup-first-input-on-tv` (done) | Origin. Round-2 design removed AC-4 (bounded image hold: only requests sized by `ready`, plus grace G), AC-7 (release deferred work later than fade end, quiet window Q) and the AC-5 threshold (research Q2: no cap reveal when `ready` lands before the cap; revealed median <= ready median + 300 ms). Reason: no start reached `ready` before the cap on the uncompiled box (`design-architect.md` Findings, "Phase B premise"; Alternatives: "Lower the cap now: a product decision without compiled-state numbers"). The round-1 G/Q spec text is lost in the squash (commit 740d780f not in the repository); `research.md` option B and `state.md` Decisions keep the intent |
| `release-startup-compilation` (done, d0720e7c) | Gave the compiled-state numbers above; judge-1 Concern 1: the two over-gate keys are cap starts |
| `startup-splash-preload` (done) | Built the gate and the image hold; owner decision Q2 and AC-4 amendment: hero and on-screen card images drawn, not skeletons, in the first frame after the fade unless the cap fired (`docs/superpowers/specs/2026-11-21-startup-splash-preload.md:236,282-283`). A bounded hold reverses this |
| `startup-settled-signal` (done, 08271680) | Built `StartupSettled` and the slots (AC-7 territory) |
| `home-hero-late-insert` (idea) | Home scrolled and hero missing when a cap reveal comes before Hot settles; this is the S0 symptom |
| `update-install-dex-metadata` (idea) | Installs in-app updates with `base.dm` so they start compiled; removes most S0 starts at the source |
| `card-image-size-tier` (idea) | Decoding 4K wide posters into cards; would shorten the image wait this run bounds |
| `watch-index-catch-up` (done, 183c10b1) | New work in the WatchState slot since the last box measurement |

Possible duplicate: none.

## 5. External findings

| Finding | Source |
|---|---|
| The platform `SplashScreen` keeps the splash with `setKeepOnScreenCondition`, "evaluated before each frame is drawn"; dismissal is meant for when the app is "stable from a visual standpoint". The app uses its own Compose overlay instead (no `core-splashscreen` dependency), which lets Home compose and load under it; the platform API is not a drop-in alternative | https://developer.android.com/develop/ui/views/launch/splash-screen (fetched 2026-11-23) |
| Startup guidance: show UI with placeholder data first and update bitmap-dependent properties later; `reportFullyDrawn` / `FullyDrawnReporter` separate initial display from fully drawn | https://developer.android.com/topic/performance/vitals/launch-time (fetched 2026-11-23) |

## 6. Options

| Option | What | Trade-offs | Size |
|---|---|---|---|
| A. Re-baseline, then close or narrow | Box set at 9158cf82 in S1 and S0 with keys at reveal +1 s and +2.5 s (covers the new WatchState catch-up); record cause, `ready`, `ready_drained`, `revealed`; ship no gate change | Cheap; tells whether AC-7 has any evidence; leaves the ~0.5 s image wait and the cap-start key spikes in place | S |
| B. Bounded image hold (recommended) | Cover rule counts only `held` (requests in flight at `ready`) and ends at `ready + G`, whichever first; new reveal cause `grace`; cap stays 2.5 s; AC-7 only as an observation at a late key anchor; G and targets fixed from an S1 box baseline at 9158cf82 | Reveal moves to about `ready + G` (S1 image wait today 389-956 ms) and cap starts in S1 mostly disappear; some hero or card images show their skeleton at fade end, reversing the startup-splash-preload Q2 decision; touches the startup gate and telemetry (risk area); needs device evidence | M |
| C. Gate per compile state | B plus a cap that adapts in S0 (e.g. counted from `first_content`, or raised when `ready_req` is pending) so an uncompiled start shows a complete Home | S0 starts would keep the splash to ~3.5-4.7 s; S0 is better removed by `update-install-dex-metadata`; overlaps `home-hero-late-insert` | M, near L |

## 7. Risks

| Risk | Where |
|---|---|
| Skeleton flash at fade end on the TV (visual regression against an owner decision) | `RevealGate.kt:109`, `StartupSplashHost.kt`; needs a ux-designer check |
| Decodes after an earlier reveal compete with the first key, as the cap starts already suggest (plausible, not measured) | Coil decodes, `PuberApp.kt` image loader; key anchors at reveal +300 ms and +1 s |
| A new `grace` cause changes the `startup` line contract and comparison with earlier evidence | `RevealObserver.kt:6`, `StartupMarks`, `TelemetryEncoder` |
| S1 needs a forced `cmd package compile -m speed-profile -f` on the box; not on the AGENTS.md standing list (earlier runs got it per run) | box 192.168.1.110:5555 |
| Box noise: load 2.7-5.5 and KinoPub API 502 episodes made earlier rounds unusable | evidence of startup-first-input-on-tv rounds 3-4 |
| The cap timer is a main-thread `LaunchedEffect`; a G timer placed the same way is postponed by the same busy frames | `MainActivity.kt:83-86` |

## 8. Open questions

| # | Question | Recommended answer |
|---|---|---|
| 1 | Accept skeletons at fade end for images not decoded by `ready + G` (reverses startup-splash-preload Q2)? | Yes, with G from the S1 baseline, starting at 300 ms (the old Q2 target: revealed median <= ready median + 300 ms) |
| 2 | Cap value: keep 2.5 s? | Keep 2.5 s; S0 goes to `update-install-dex-metadata` and `home-hero-late-insert` |
| 3 | AC-7 deferred-work window: in scope? | No change; record keys at a late anchor (reveal +2.5 s) as an observation, and file a run only if they exceed 199 ms |
| 4 | Which compile state gates the targets? | S1 gates (no cap reveal when `ready` lands before the cap; revealed median <= ready median + G; first-key p95 <= 199 ms); S0 recorded as observation. Authorise the S1 compile command for this run |

## 9. Sources

- Files: `core/startup/RevealGate.kt`, `StartupReveal.kt`, `StartupImageActivity.kt`, `StartupImageLoader.kt`, `StartupSettled.kt`, `RevealObserver.kt`; `MainActivity.kt`; `ui/feature/root/component/StartupSplashHost.kt`; `ui/feature/main/vm/MainVM.kt`; `ui/feature/update/vm/UpdatePromptVM.kt`; `domain/di/modules.kt`; `data/repository/WatchStateRepository.kt`; `gradle/libs.versions.toml` (coil 3.6.3, compose BOM 2026.09.00, tv-material 1.1.0).
- Evidence: `docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md`, `2026-11-23-startup-first-input-on-tv-verify-5-device.md`, `2026-11-23-release-startup-compilation-device.md`.
- Specs: `docs/superpowers/specs/2026-11-22-startup-first-input-on-tv.md`, `2026-11-21-startup-splash-preload.md`; plan `docs/superpowers/plans/2026-11-22-startup-first-input-on-tv.md`.
- Commits: 183c10b1, a6e04570, 6d793b81, 08271680, 0b5c0669, b3cc6c5e.
- Runs: `startup-first-input-on-tv` (state, brief, research, design-architect, qa-report), `release-startup-compilation` (state, brief, judge-1, qa-report), `startup-splash-preload`, `startup-settled-signal`, `home-hero-late-insert`, `update-install-dex-metadata`, `card-image-size-tier`, `startup-main-thread-io`, `cache-decoded-memory-tier`.
- URLs: https://developer.android.com/develop/ui/views/launch/splash-screen, https://developer.android.com/topic/performance/vitals/launch-time
