# Test design: player-corrupt-cache-recovery

Kind: bug (test gap, no production change). Level: instrumented (emulator, leased). Base 8ffdbca1.

## Risk profile

| Risk | P | I | Score | Test / handling |
|---|---|---|---|---|
| Corrupt entry plays on or fails without the failover signal; the old `sawReady || error` wait accepts both | 2 | 3 | 6 | AC-1 asserts `StreamFailure(NEXT_URL, url)` and no `Error`; AC-3 mutant proves the wait bites |
| Corrupt span reaches READY with no error on some device, so the wait times out | 1 | 2 | 2 | Accepted: report as a finding, do not relax the wait (brief Risks) |
| `routeError` re-prepare on a stale error adds a second report | 1 | 2 | 2 | Accepted: assert "contains", not an exact list |
| Flaky timing across runs | 2 | 2 | 4 | AC-2: 3 full-class runs |
| Docs/KDoc keep saying "not asserted" | 2 | 1 | 2 | AC-4 read check |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | instrumented | P0 red | `PlaybackControllerResourcesTest.emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` (corrupt step: `awaitCondition("corrupt-cache failure") { corrupt.error.get() != null }`, then `harness.events.since(mark)` has `StreamFailure(NEXT_URL, url)`, no `PlaybackEvent.Error`) | Fails if the corrupt entry plays on, errors without failover, or reports a terminal Error |
| AC-2 | instrumented | P0 guard | same class, 3 runs, all cases | Fails if the new assertion or any other case is flaky |
| AC-3 | instrumented | P0 red | same test with a local uncommitted mutant skipping `writeCorruptCacheEntry(url)` | Fails on the wait/assertion: the test depends on the corrupt entry, not on any outcome |
| AC-4 | manual | P1 | read KDoc and `docs/testing/player-playback.md:87` | Fails if "not asserted" or the open-run pointer remains |
| AC-5 | unit/build | P1 | `check.full` | Fails if the edit breaks lint, detekt or unit suites |

Red status of AC-1: on base the test only waits for `sawReady || error` and asserts nothing about events, so it passes on base even with a wrong outcome. The implementer proves red by AC-3 mutant (the new assertions fail without the corrupt entry); a strict base-red is not possible for a test-only strengthening. Name verified: the test above exists in the file at base.

## Bugs

No production bug fix. The "failing first" check is the AC-3 mutant: apply the new assertions, then skip `writeCorruptCacheEntry(url)`, run once, expect failure, revert the mutant.

## Manual checks

1. Run `make FLAVOR=instrumentation DEVICE=host.docker.internal:5555 GRADLE=./tools/agentw itest TESTS=com.kino.puber.ui.feature.player.vm.PlaybackControllerResourcesTest` under a lease, 3 times. Expected: every case passes each time. Must not: a timeout in the corrupt step (report as finding).
2. Run once with the `writeCorruptCacheEntry(url)` call removed. Expected: the test fails on the corrupt wait or assertion. Must not: pass. Revert and confirm `git diff` shows no mutant.
3. Open the KDoc and `docs/testing/player-playback.md:87`. Expected: both state failover with `StreamFailure(NEXT_URL)`. Must not: "not asserted", or a pointer to this run as open.
4. Store the result XMLs of steps 1 and 2 in `docs/superpowers/evidence/`.

## UNTESTABLE

none
