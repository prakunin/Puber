# Brief: player-corrupt-cache-recovery

Kind: bug. Size: S (on the recommended Q1 answer; M if Q1 picks option B). Tracks: none. Device verification: yes (the changed file is an instrumented test; AC-1 and AC-2 only show on a device run). Production sources are not touched.
Research: research.md (Current state in code, Media3 1.11.0 behaviour, How a bad entry gets into the cache, Options); option A taken. Checked on master 8ffdbca1.

## Root cause (confirmed)

A cache span holding non-media bytes is read without a data-source error, so `seenCacheError` is never set and `FLAG_IGNORE_CACHE_ON_ERROR` (`app/src/main/java/com/kino/puber/ui/feature/player/vm/PlaybackController.kt:675`) does not bypass the cache; the extractor above it throws a `ParserException` (research.md: Media3 1.11.0 behaviour, read from bytecode; device-run-5 saw the error in both failing runs). `streamRecovery()` maps it to `NEXT_URL` (`PlaybackController.kt:367-370`) and `PlayerVM.onStreamFailure` switches to the next candidate, which has a different cache key (`PlayerVM.kt:268-281`). So the app fails over rather than recovering on the same URL; only a 200 non-media body written by warm-up puts such a span in the cache in real use (research.md: How a bad entry gets into the cache).

The gap this run closes: `PlaybackControllerResourcesTest.kt:59` accepts `sawReady || error`, so it would pass if the corrupt entry played on, or failed without the failover signal. The KDoc (`:36-40`), the line comment (`:58`) and `docs/testing/player-playback.md:87` describe the outcome as unasserted.

VM side is already covered: `app/src/test/kotlin/com/kino/puber/ui/feature/player/vm/PlayerVMPlaybackFlowTest.kt:257` (`streamFailure_switchesToNextCandidate_andIgnoresStaleFailure`) and `:279` (`exhaustedCandidates_refreshesLinksOnce_thenShowsError`).

## Acceptance criteria

"Run on the emulator" means `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerResourcesTest` under a lease.

- AC-1. Given a corrupt span written for `url`, when the controller prepares that path, then the test waits (bounded) for `corrupt.error` to be non-null and asserts that `harness.events` since a mark taken before `prepare` contains `PlaybackEvent.StreamFailure(recovery = NEXT_URL, url = url)` and no `PlaybackEvent.Error`.
- AC-2. Given AC-1's test, when `PlaybackControllerResourcesTest` runs on the emulator 3 times, then every case passes each time.
- AC-3. Given a local mutant that skips `writeCorruptCacheEntry(url)` (not committed), when the test runs on the emulator, then it fails on the AC-1 wait or assertion; the result is recorded in evidence.
- AC-4. Given the test's KDoc and `docs/testing/player-playback.md:87`, when read, then both say the corrupt entry fails over to the next candidate (`StreamFailure(NEXT_URL)`) and no longer say "not asserted" or point to this run as open. The test name is unchanged.
- AC-5. Given the worktree, when `check.full` from `.flow/config.yaml` runs, then it passes.

## Risks

- If the corrupt span plays to READY with no error on some device (research.md: Risks), AC-1 times out. Device-run-5 saw the error in both runs; a timeout is a finding to report, not a reason to relax the wait.
- The `StreamFailure` is delivered through `PlaybackCallbackGate.routeError` (`PlaybackCallbackGate.kt:45-57`), which re-prepares once for a stale error right after a switch; the test has no switch, so one report is expected. Assert "contains", not an exact event list.

## Open questions

| ID | Question | Recommended | Blocks |
|---|---|---|---|
| Q1 | Accept the failover to the next candidate as the intended outcome (A), or also stop warm-up committing a non-media body (B, size M)? | A: the VM already recovers on another key; open B as a separate idea run only if failovers show up in real use | yes |
| Q2 | Rename the test, whose name says "Recovers"? | No: the name maps to upstream in `docs/testing/player-playback.md:87`; the KDoc states the actual outcome | no |

## Plan

1. `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt:56-60`: take `val mark = harness.events.mark()` before `harness.prepare(path)`; replace the `sawReady || error` wait with `harness.awaitCondition("corrupt-cache failure") { corrupt.error.get() != null }`; then assert `harness.events.since(mark)` contains a `PlaybackEvent.StreamFailure` with `recovery == PlaybackControl.StreamRecovery.NEXT_URL` and `url == url` (wait for it with `awaitCondition` if it is posted after the probe's error) and none of type `PlaybackEvent.Error`. Replace the `:58` comment and the KDoc `:36-40` with the failover outcome (AC-4).
2. `docs/testing/player-playback.md:87`: change the status cell to "ported: corrupt container fails over with StreamFailure(NEXT_URL); then a clean load after the resource is removed".
3. Device: run the class 3x on the emulator and the AC-3 mutant once; store the result XMLs in `docs/superpowers/evidence/`.
