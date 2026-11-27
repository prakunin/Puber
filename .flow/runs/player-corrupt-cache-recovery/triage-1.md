# Review triage - round 1

Run: player-corrupt-cache-recovery   Base: 8ffdbca1   Reviewed head: 7d1ee636

Sources: review-blind-1.md (F1 minor, F2 nit), review-tests-1.md (F1 minor, F2 nit), review-codex-1.md (ran: review; clean). Antigravity: skipped, quota exhausted (owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, tests F1 | minor | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlaybackControllerResourcesTest.kt:58-77` | "no terminal Error" is checked on one snapshot right after the first StreamFailure; a late Error would go unseen | read the test: snapshot before `releaseAndDrain()`, drained events never asserted | fix | re-check `none { Error }` on `harness.events.since(mark)` after `releaseAndDrain()` |
| 2 | tests F2 | nit | same test, :59 | `corrupt.sawReady` is not asserted false | read the test | fix | cheap: add `assertFalse(corrupt.sawReady)` if the evidence shows READY is never reached (research notes the test once reached READY before failing; keep it only if true on the emulator) |
| 3 | blind F2 | nit | test name | name still says "Recovers" | Q2 (owner, recommended): keep the name mapped in docs/testing/player-playback.md:87 | reject | owner decision Q2; the KDoc states the failover outcome |

## Rejected codex claims

- none

## Next

- fix round 1: #1, #2 (tests only: run the class 3x on the emulator, check.fast); size S, no blocker or major, no second review round; unproven lines: 1 (tests 1)
