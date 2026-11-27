# Implementation record: player-corrupt-cache-recovery

Base 8ffdbca1. Test and docs only; no `app/src/main` change.

## Commits (worktree)

| sha | subject |
|---|---|
| 000beab1 | Assert the corrupt cache entry fails over with StreamFailure(NEXT_URL) and no Error |
| 4ce45e30 | Document the corrupt cache entry failover in the player test mapping |

## Red run

- red (mutant, AC-3): `emptyCache_loadsNormally_andCorruptOwnedEntryRecoversWithoutPoisoningNextScenario` with `writeCorruptCacheEntry(url)` removed (local, reverted, not committed) - `AssertionError: corrupt-cache failure: condition stayed false for 15000 ms` at `PlaybackControllerResourcesTest.kt:59`. XML: itest-mutant.xml.
- A strict base-red is not possible (test-design: strengthening of a test that passed on base).

## Device (emulator host.docker.internal:5555, leased, released, emulator stopped)

| run | result | file |
|---|---|---|
| 1 | 3 tests, 0 failures | itest-1.xml |
| 2 | 3 tests, 0 failures | itest-2.xml |
| 3 | 3 tests, 0 failures | itest-3.xml |
| mutant | fails on the AC-1 wait | itest-mutant.xml |

## check.full

BUILD SUCCESSFUL in 3m 22s, then tooling shell tests all PASS (baseline-profile and itest tooling).

## Deviations

- XMLs are in this run folder (conductor instruction), not `docs/superpowers/evidence/` as plan task 3 says.
- Second wait `awaitCondition("corrupt-cache StreamFailure")` added: the plan allowed it in case the event posts after the probe error.
- Plan checkboxes: the brief has none to tick.

## For reviewers

The assertion uses "contains" for `StreamFailure(NEXT_URL, url)` and "none" for `PlaybackEvent.Error`, per the brief's routeError risk. Device-run timing is about 1.5 s per class run; no timeout seen in the corrupt step.

## Fix round 1

| Finding | Result |
|---------|--------|
| #1 late terminal Error | fixed (a8607ccc): `none { Error }` now checked on `harness.events.since(mark)` after `releaseAndDrain()` |
| #2 `sawReady` | fixed (a8607ccc): READY is never reached on the emulator, so `assertFalse(corrupt.sawReady)` was added; it held in all 3 runs |

Checks: `:app:compileDevDebugKotlin`, `:app:compileInstrumentationDebugAndroidTestKotlin`, `:app:detektInstrumentationDebug` green. `PlaybackControllerResourcesTest` on `host.docker.internal:5555`, 3 of 3 runs 3 tests 0 failures: `itest-fix1-1.xml`, `itest-fix1-2.xml`, `itest-fix1-3.xml`. Lease released, emulator stopped.
