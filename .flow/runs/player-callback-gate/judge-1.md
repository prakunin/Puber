# Gate - player-callback-gate - round 1

**Decision:** PASS
**Head:** 6ec6e015   **Base:** cec5f7fc

## Done as asked

`git diff cec5f7fc...6ec6e015` adds `PlaybackCallbackGate.kt`, gives each session its own listener in `PlaybackController.kt` (`bindSession` in `prepare`/`switchStream`, `gate.invalidate()` in `release`, `gate.onLoadStarted(dataSpec.uri)`), and moves the KDoc onto `switchStream`. It also adds `PlaybackCallbackGateTest` (10 cases), removes the `@Ignore` with the body unchanged, adds one device case (fix round 1) and updates `docs/testing/player-playback.md:11,57,63`. `switchStream` has no `stop()`; the only `player.stop()` is `PlaybackController.kt:641` (`disableAc3AndRetry`, unchanged). Nothing from Out of scope: no Robolectric, no Compose E2E.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `PlaybackControllerStaleCallbackTest` on emulator, 3 runs | evidence doc AC-1 (qa runs 3x3/0 fail); `itest-1..3.xml` 3/0 each; `itest-red.xml` 1 failure before the fix; `itest-fix1-1..3.xml` 4/0 at head tests | PASS |
| AC-2 | `dispatch_runsActionWithSessionUrl_whenSessionIsCurrent`, `dispatch_skipsAction_whenNewerSessionBegan`, `dispatch_skipsAction_afterInvalidate` | check.full exit 0 (re-run by judge at 6ec6e015) | PASS |
| AC-3 | 7 `routeError_*` cases in `PlaybackCallbackGateTest` | same run; read: re-prepare once, second error reported with session URL | PASS |
| AC-4 | code review of `SessionListener` | diff read: ENDED/READY/tracks/play state via `gate.dispatch`, READY uses the dispatched URL, error via `gate.routeError` with `report(url)`; no listener reads `currentStreamUrl` (remaining reads at `:189`, `:820` are seek-stall and debug) | PASS |
| AC-5 | read `switchStream` | no `stop()` (grep) | PASS |
| AC-6 | box 192.168.1.110:5555, two D-pad quality switches | `docs/superpowers/evidence/2026-11-27-player-callback-gate-device.md` (APK ed00b902; `app/src/main` unchanged since, `git diff ed00b902 HEAD -- app/src/main` empty) | PASS |
| AC-7 | read `docs/testing/player-playback.md` | lines 11 and 63 describe a passing regression | PASS |
| AC-8 | check.full | judge run at 6ec6e015: exit 0 | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| No test wires `onLoadStarted` into the gate (minor) | review-tests-1 F1, triage-1 #1 | fixed | 6ec6e015; `itest-fix1-mut.xml` red with the line removed (B requested twice) |
| Fixed `Thread.sleep(PARSE_WAIT_MS)` precondition (minor) | review-codex-1, triage-1 #2 | deferred | item 4 in `.flow/runs/player-device-tests-hardening/state.md:34`; AC-1 requires the body unchanged |
| Audio row shows "Track 1" after a quality switch | qa observation, triage-1 #3 | rejected for this diff + new run | label expression unchanged (base `:843`, head `:873`); manual quality tries `hls` before `hls4` (`PlayerInteractor.kt` manual vs auto candidates); run `player-audio-track-label-fallback` (idea) |
| Old load start of URL A marks a same-URL replacement session as loaded | codex-1 rejected | rejection accepted | spec Risks R12 accepts it; same behaviour as base |
| Single source-session identity | codex-1 rejected | rejection accepted | design preference, no failure shown |
| Stale-probe case table in `docs/testing/player-playback.md:59-63` does not list the new `switchStreamToFailingStream_reportsOneFailureWithoutRePrepare` (minor) | judge | accepted | the case is described in the test KDoc; one table row, too small for a run |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | One small object per session switch plus a string compare per load start; no new main-thread work; no `stop()`, so the allocator pool survives a switch |
| Reliability | `release` invalidates the gate and removes the listener; the re-prepare uses `exoPlayer?` and only fires for the current session; at most one re-prepare per switch session; a re-entrant switch inside a callback is covered by listener removal and the generation check |
| Maintainability | Matches the spec Design (gate class, inner `SessionListener`, KDoc moved); `playerListener` removed with no dead code left; JUnit 5 test |
| Security / privacy | No secrets or account data in the diff; the evidence file passed `tools/mobile-evidence-audit.sh` (judge re-run: passed) |
| TV | No Compose, focus or D-pad code changed; AC-6 drove quality switches with the D-pad on the box |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

none
