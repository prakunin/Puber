# Test design: player-device-tests-hardening

Level: `instrumented` only (the changed files are the tests; production is untouched). Run command per brief.md "Acceptance criteria". No unit level applies: each defect needs a real player, loader and local server.

## Risk profile

| Risk | P | I | Score | Test / mitigation |
|---|---|---|---|---|
| Item 1 gate still opens before the segment request (vacuous pass) | 3 | 2 | 6 | AC-1 pending-entry condition; mutant M1 |
| Item 2 ABR starts on an ungated variant, gates never reached | 3 | 2 | 6 | single-variant master plus pending-gate condition; mutant M2 (AC-3) |
| Item 2 chunkless preparation off, so gating segment 0 blocks tracks | 1 | 3 | 3 | resolved in brief (production `PlaybackController.kt:781` enables it); a timeout would show on the 3 device runs |
| Item 4 OkHttp event never fires (loader does not close the body) | 2 | 3 | 6 | fallback event per brief Risks; recorded in evidence; failure is loud via `harness.diagnostics()` |
| Item 4 EventListener raised by stream B or other traffic | 2 | 2 | 4 | listener keyed by URL path of A; mutant M4 |
| Item 4 loader-thread to main-post remainder misses on a slow device | 2 | 2 | 4 | existing `assertNotNull(probe.error)`; 5 repeated runs (AC-6) |
| Item 3 assertion restored but fails because cache is not empty | 1 | 2 | 2 | accepted; AC-4 run shows it |

## Traceability

All rows are tests that already exist by name; the change strengthens their bodies. "red" is not available for them (production is unchanged, so each passes on the base commit, vacuously for items 1-2); strength is proven by the mutants below, so P0 rows are `guard` plus mutant.

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 guard + M1 | `hlsDelayedAndInterruptedSegment_enterBufferingThenRecover` | fails if `gate.countDown()` is reached without a pending `delayed-segment` entry observed with BUFFERING (M1 times out) |
| AC-2 | instrumented | P0 guard + M2 | `hlsPreparation_exposesCodecTracksBeforeTheFirstMediaChunkCompletes` | fails if video/audio tracks appear while either gated route (`chunkless-video-gate`, `chunkless-audio-gate`) has no pending entry |
| AC-3 | instrumented | P0 mutant | same test as AC-2, mutant M2 | fails (timeout on the pending-gate condition) when the master is `/media/hls/master.m3u8` with gates on 720p, i.e. the old vacuity |
| AC-4 | instrumented | P1 guard | `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` | fails if `removeResource(url)` leaves spans in `getCachedSpans(url)`; KDoc is documentation, not asserted by test |
| AC-5 | instrumented | P1 | `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` | review-only check: no fixed `Thread.sleep` between `aOutcome()` and `switchStream(B)`; bounded wait on A's signal (not a source-text test; `rg "Thread.sleep" ` on the file as a reviewer aid) |
| AC-6 | instrumented | P0 guard | `switchStreamAfterQueuedFailure_reportsNoFailureForPreviousStream` and the other cases of `PlaybackControllerStaleCallbackTest` x5 | fails if the wait misses A's queued error (`probe.error` null) or the signal is wrong; stays green 5/5 |
| AC-7 | n/a | P0 | `check.full` | fails on compile, detekt or unit regressions |

Mutants are local, run once on the emulator, discarded, results recorded in evidence:

| Mutant | Change | Expected |
|---|---|---|
| M1 | item 1 test: replace pending condition with BUFFERING-only (old code) and make the segment route never requested (gate on a route id the player does not fetch) | the new test version with a never-pending route times out; shows the condition is load-bearing |
| M2 | item 2 test: master path `/media/hls/master.m3u8`, gates on `video_720_000.ts` | fails (AC-3) |
| M4 | item 4: listener signals on any URL path instead of A's | optional; if it passes anyway, the `probe.error` precondition still holds, note it in evidence |

## Bugs

N/A: improvement; no production defect. No test fails on base: items 1-2 pass vacuously on base, which is the defect, shown by M1 and M2 rather than a red test.

## Failing-first order

Run M2 against the old item-2 gates (base shape, new single-variant not applied) is the only way to show vacuity; implementer runs M2 before finalising step 2 to confirm it fails, then applies the final test.

## Manual checks

N/A: no UI or production behaviour change; device runs are the instrumented runs above.

## Device runs (evidence)

| Run | Count | Result file |
|---|---|---|
| `PlaybackControllerNetworkTracksTest` | 3 | result XMLs in the run folder |
| `PlaybackControllerResourcesTest` | 1 | same |
| `PlaybackControllerStaleCallbackTest` | 5 | same; `probe.error` non-null in item 4 |
| Mutant M2 (and M1) | 1 each | failing XML, mutant discarded |

Also record which OkHttp event item 4 used (brief Risks).

## UNTESTABLE

- AC-4 KDoc content and AC-5 absence of `Thread.sleep`: documentation and structure, verified by reviewer reading; no behavioural test exists for them.

## perf

N/A: perf track off.
