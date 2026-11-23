# AC-7 diagnosis (architect): startup-reveal-gate-on-slow-tv

Head `854a4981` (code `7ed2f1ec`). Data: `{wt}/docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md` (round 1b), old gate: `docs/superpowers/evidence/2026-11-23-startup-first-input-on-tv-verify-5-device.md`. Paths under `app/src/main/java/com/kino/puber/`. Offsets are ms after the `revealed` mark; "drain" is `ready_drained`.

## Where the 291-543 ms goes

`lat = (frameStart + frame_ms) - eventTime`, `wait_ms = dispatch - eventTime` (`core/telemetry/KeyProbe.kt:115-126`); the frame is the first one FrameMetrics reports with start >= dispatch (`KeyProbe.kt:99`), start = `INTENDED_VSYNC_TIMESTAMP` (`core/telemetry/FrameTracker.kt:22`; metrics-performance 1.0.0 `JankStatsApi26Impl.getFrameStartTime` reads metric 10), `frame_ms` includes `UNKNOWN_DELAY` (metrics 0-5). With wait 11-26 and frame 19-54, 230-480 ms lie between dispatch and the intended vsync of the next reported frame: the main thread took the key at once and the frame that answered it was short, but no frame was reported in between.

| Observation (sets 1 and 2 of round 1b, verify-5) | Count |
|---|---|
| New gate, first key injected before drain: over 199 ms | 14 of 14 (286-657) |
| New gate, first key injected after drain: over 199 ms | 0 of 16 (max 102) |
| New gate, slow keys whose answering frame (inj + lat) falls after drain | 14 of 14 (32-492 ms after it) |
| Old gate (verify-5) anchor B, 183-345 after the mark: over 199 ms | 0 of 5; 4 of 5 after drain, 1 (row 2) 48 ms before it |
| Old-gate slow keys in the baseline (release-startup-compilation r3-4): cause | `cap` 2 of 2, the only starts revealing before drain |

Conclusion: the slow window is `[revealed, ready_drained]`, the held images landing after a `grace` reveal, not the fade (the old gate faded with the same `REVEAL_FADE_MS`, `ui/feature/root/component/StartupSplashHost.kt:19`, and B passed) and not `StartupSettled` work (it starts at fade end, `MainActivity.kt:91`, `core/startup/StartupSettled.kt:46-48`; slots `+500 ms` later, `StartupSettled.kt:12,18`; B keys at 97-232 precede it, and K2 with all slots running is 0 of 15 over 50). Since `onImageFinished` keeps reaching the gate during the window, the main thread is not blocked; the inferred mechanism is frames drawn but not reported (skipped by the render thread or held in the GPU queue while the landing bitmaps upload and the card crossfades run, `core/ui/uikit/component/moviesList/VideoItemUIState.kt:190`, `moviesList/VideoItemHorizontal.kt:100`). Not confirmed: no trace of a grace start exists.

## Reachability of AC-7 with AC-6

AC-7 anchor B was met by the baseline build (verify-5: 5 of 5 at most 67 ms), because the old cover rule waited for every request, so the key landed after drain. With the current arrival cost, B passes only where the reveal follows drain. Set 2 `ready_drained - ready`: 10 of 15 starts exceed 550 ms (= G + 250, the AC-6 max), up to 864; `ready` reaches 2001, so any G above about 500 turns starts into `cap` (AC-6 cap clause). No G satisfies both: AC-7 anchor B and AC-6 are exclusive until the held arrivals stop withholding frames.

## Decisions

| Question | Decision | Why |
|---|---|---|
| Code change in this run for AC-7 | None | No candidate below is shown to meet anchor B without losing AC-6; the cheap ones are unmeasured |
| What to do with AC-7 | Return to the owner as Q1 (AC change), not a design change | AC-7 is approved; anchor B as written contradicts AC-6 on the measured drain |
| How to settle the mechanism | Q2: one atrace set (3 grace starts, key at B, categories `gfx view hwui input sched`) in a follow-up run before any image-cost change | Candidate fixes differ by mechanism (skipped frames vs upload vs crossfade) |

## Changes by layer

N/A: no code change proposed in this run.

## Alternatives rejected

| Alternative | Why not |
|---|---|
| Larger G | Above about 500 ms produces `cap` starts (AC-6); at 500 most B keys still precede drain (10 of 15 drains exceed ready + 550) |
| Defer `StartupSettled` until drain or a quiet frame | Settled starts at fade end (`MainActivity.kt:91`); B keys precede it, and keys after it are fast |
| Move decodes off main | Coil already decodes off main; the gate's `onImageFinished` callbacks arrive during the window, so main is free |
| Cancel held requests at G | Restarts the same decodes later, in the A/C window; skeletons stay longer |
| Hold image delivery until fade end or drain (interceptor) | New Coil plumbing in startup; keys after fade end and before drain are also slow (set 1 #13: 270 after the mark, 530 ms) |
| Drop crossfade for startup-held requests, `CompositingStrategy.ModulateAlpha` on the splash layer (`StartupSplashHost.kt:46`) | Plausible but unmeasured; belongs to the follow-up after the trace (Q2) |

## Risks

| Risk | Where | Note |
|---|---|---|
| A key pressed in the first ~0.5 s after a `grace` reveal answers in 0.3-0.65 s, a regression against the old gate for that window | `core/startup/RevealGate.kt:75,123` | The trade-off Q1 asks the owner to accept |
| Mechanism inferred, not traced; a wrong guess sends the follow-up to the wrong fix | render thread, `PuberApp.kt:110-145` image loader | Q2 trace first |
| Anchor B timing depends on the screencap detector, which itself loads the box (set 1) | evidence method | Any re-measure uses set 2's detector start (+1.9 s) |

## Open questions

| # | Question | Recommended answer | Blocks |
|---|---|---|---|
| 1 | Restate AC-7: "at most one first key over 199 ms across anchors A and C and the baseline-method control (key at launch +2.44 s); anchor B keys and their position relative to `ready_drained` recorded as an observation"? (Round 1b already measures A+C 0 of 10, control 0 of 15.) | Yes: keep the earlier reveal (AC-6) and accept slow keys during the held drain; the alternative, keeping AC-7, means reverting to a drain-bound reveal and dropping the run's goal | yes |
| 2 | File a follow-up run (perf) on the cost of held images landing after the reveal: trace first, then the cheapest fix (crossfade off for startup-held requests, splash layer compositing, decode size), bound: anchor B at most 1 of 15 over 199 ms? | Yes | no |
