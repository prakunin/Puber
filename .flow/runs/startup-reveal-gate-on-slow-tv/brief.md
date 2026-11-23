# Brief: startup-reveal-gate-on-slow-tv

Triage of 2026-11-23 against master `9158cf82`. Builds on `research.md`; paths under `app/src/main/java/com/kino/puber/`. Checked in code: cover rule `core/startup/RevealGate.kt:109`, held set used only for telemetry `RevealGate.kt:34,64,83-85`, cap `RevealGate.kt:9` and its main-thread timer `MainActivity.kt:83-86`, causes `core/startup/RevealObserver.kt:4-8` (wire value encoded at `core/telemetry/TelemetryEncoder.kt:73`), fade `ui/feature/root/component/StartupSplashHost.kt:19,35-44`, deferred-work latch `core/startup/StartupSettled.kt:12-19,46-58`. All hold as research.md describes them.

## Classification

- **kind:** improvement
- **size:** M. One goal after the recommended answers below: bound the image hold after `ready`. Cap value stays (Q2), the deferred-work window is an observation only (Q3). Touches startup (risk area) and the `startup` telemetry line contract.
- **tracks:** perf. The goal is a measured reveal time in the compiled state; the visual side (skeletons at fade end) is AC-8, owner judges.
- **Device verification:** yes (startup, reveal timing, first key).

## Root need

In the compiled state (S1) Home is ready at ~1.4-2.2 s, then the splash waits 389-956 ms more for images (research.md: 3, verify-5 table), and when that wait crosses 2.5 s the start becomes a `cap` start; those carry the only first keys over 199 ms (research.md: 4, release-startup-compilation judge-1). The wait is unbounded because the cover rule waits for every in-flight request of the loader, including ones started after `ready` (`RevealGate.kt:109`), while `held` (the requests present at `ready`) is unused by it. Wanted: reveal by `ready` + G at the latest, cap unchanged, uncompiled starts (S0) unchanged.

## Scope

| Item | Decision |
|---|---|
| Bounded image hold | In: only `held` requests keep the splash, at most G after `ready`; new cause `grace` when G ends it |
| G | Start at 300 ms (Q1); final value fixed from the S1 box set and written into the spec |
| Cap value | Unchanged 2.5 s (Q2) |
| Deferred-work window (old AC-7) | No code change; keys at a late anchor recorded as an observation (Q3) |
| S0 behaviour | Unchanged; recorded as an observation |

## Acceptance criteria

| AC | Given | When | Then | Checked by |
|---|---|---|---|---|
| AC-1 | gate begun, `ready` taken effect with requests in `held` | all held requests end before G | splash uncovers at the last end with cause `ready` | unit (`RevealGateTest`) |
| AC-2 | gate begun, `ready` with held requests still in flight | G elapses after `ready` | splash uncovers with cause `grace`; `onRevealed` reports the requests still in flight | unit |
| AC-3 | gate begun, `ready` with `held` empty or drained | a request starts after `ready` | splash uncovers without waiting for it (cause `ready`) | unit |
| AC-4 | gate begun, no `ready` | 2.5 s pass from `begin()`; or `revealNow()` is called | cause `cap` / `forced` exactly as today; a later `ready` or G timeout changes neither the cause nor the reveal; a stale `ready` from before `begin()`/`reset()` arms no G | unit; existing `RevealGateTest`, `StartupRevealTest` pass, tests that assert waiting on post-ready requests are rewritten, not deleted |
| AC-5 | a `grace` reveal | the `startup` line is encoded | it carries `"cause":"grace"` and the other fields as today | unit (`TelemetryEncoderTest`, `StartupMarksTest`) |
| AC-6 | box 192.168.1.110:5555, stage build of the run head in S1 (`dumpsys package com.kino.puber.stage` shows `speed-profile`), 15 cold starts | each start's `startup` line is read | no start with `ready` before 2500 ms has cause `cap`; median of `revealed - ready` <= G + 50 ms; max <= G + 250 ms | device evidence |
| AC-7 | the same 15 starts, one DPAD_DOWN at anchor A (launch +3 s) and at anchor C (detection +0.7 s), and the baseline-method control (key at launch +2.44 s) | the key is handled | at most one first key over 199 ms across anchors A and C and the control, and the focus ring moves one row in every start; anchor B (first-pixel detection) keys and their position relative to `ready_drained` are recorded as an observation (design-ac7-diagnosis.md Q1) | device evidence |
| AC-8 | an S1 start where G ended the hold | a screenshot is taken right after the fade | skeletons in place of undecoded hero or card images are acceptable (owner judges) | device screenshot (not committed if account data shows) |
| AC-9 | box in S0 (as installed, `reason=install`), 5 cold starts | `startup` lines are read | cause `cap` and `revealed` within 2.5 s + 300 ms as before (observation, no threshold change) | device evidence |
| AC-10 | the AC-6 set | a key at reveal +2.5 s (deferred-work slots running, incl. the new WatchState catch-up of `183c10b1`) | latency recorded per start (observation; over 199 ms on more than one start files a follow-up run) | device evidence |

## Risks

| Risk | Where | Mitigation |
|---|---|---|
| G timer on the main thread is postponed by the same busy frames as the cap (`MainActivity.kt:83-86`), so `revealed - ready` overshoots G | `StartupReveal`, `MainActivity` | spec chooses where the G deadline is scheduled; AC-6 max bound catches it |
| Decodes finishing after an earlier reveal compete with the first key (plausible, not measured) | Coil decode, `StartupImageLoader.kt` | AC-7 |
| Reverses startup-splash-preload Q2 (images drawn, not skeletons, after the fade) | `docs/superpowers/specs/2026-11-21-startup-splash-preload.md:236,282-283` | AC-8, Q1 |
| New `grace` value breaks comparisons with earlier evidence and any reader of the `startup` line | `RevealObserver.kt:4-8`, `StartupMarks.kt:84`, `TelemetryEncoder.kt:73` | AC-5; evidence tables add the cause column value |
| S1 for a freshly installed build needs either the system's background dexopt or a forced `cmd package compile`, which the box's standing authorization does not cover (the verify-5 S1 state came from a conductor compile of 2026-11-22) | box | Q3 |
| The verify-5 key injector is scaffolding pushed to `/data/local/tmp`; whether "remote input" covers it is not written down | box | reuse exactly the verify-5 method and remove it after, as verify-5 did; otherwise `adb shell input keyevent` with its detector lag |
| Box noise (load 2.7-5.5, KinoPub API 502 episodes) invalidated earlier rounds | box | record load average per set; repeat a set rather than drop starts |

## Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| Q1 | Accept skeletons at fade end for images not decoded by `ready` + G (reverses startup-splash-preload Q2)? | Yes, G starts at 300 ms and is fixed from the S1 set | no |
| Q2 | Keep the 2.5 s cap? | Keep; S0 is left to `update-install-dex-metadata` and `home-hero-late-insert` | no |
| Q3 | How does the run reach S1 on the box without a forced compile? Deferred-work window in scope? | Wait for the system's background dexopt and check it read-only with `dumpsys package`; if it has not reached `speed-profile` before verification, escalate the forced compile as a single authorised action. Deferred-work window: observation only (AC-10) | no |

## Plan

N/A: size M, planned after the spec.
