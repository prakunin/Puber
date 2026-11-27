# Review triage - round 1

Run: player-device-tests-hardening   Base: 13432572   Reviewed head: 59da16aa

Sources: review-blind-1.md (F1, F2 minor; F3, F4 nit), review-tests-1.md (F1, F2 minor), review-codex-1.md (ran: review; clean). Antigravity: skipped, quota exhausted (owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1, tests F2 | minor | `app/src/androidTestInstrumentation/kotlin/com/kino/puber/ui/feature/player/vm/PlayerTestControl.kt` ClosedResponseSignals | the close latch is keyed by path and never reset, so an earlier close of the same path makes the wait return at once; per-path scoping has no check (optional mutant M4 not run) | read ClosedResponseSignals: one CountDownLatch per encodedPath, created once | fix | reset (replace) the path's latch when a new request for it starts, and run M4 once and record it |
| 2 | blind F2, tests F1 | minor | `PlaybackControllerStaleCallbackTest.kt:105` | `!Looper.myQueue().isIdle` is true for any queued main-looper message, not only A's error | read the wait; brief Risks accept a bounded remainder; `assertNotNull(probe.error)` keeps a miss loud (6/6 final runs held) | reject | accepted bounded remainder in the brief; no silent pass is possible; codex rejects it as unproven |
| 3 | blind F3 | nit | `PlayerTestControl.kt` SignalingBody KDoc | KDoc says the close happens "after it parsed"; any close counts | read the KDoc | fix | soften the KDoc wording |
| 4 | blind F4 | nit | `PlaybackControllerResourcesTest.kt` | the corrupt-entry test still asserts only "ready or error" | KDoc defers to run `player-corrupt-cache-recovery`, which exists as an idea run | reject | covered by `player-corrupt-cache-recovery` |

## Rejected codex claims

- isIdle heuristic can pass early - see #2.
- Historical stateEvents branch passes after BUFFERING - not reachable while the first segment is gated.

## Next

- fix round 1: #1, #3 (tests only: re-run PlaybackControllerStaleCallbackTest on the emulator and check.fast); size S, no blocker or major, no second review round; unproven lines: 3 (blind 2, tests 1)

## Fix round 1 outcome (conductor, 2026-11-27)

- #1 and #3 fixed in 219d94f5. Mutant M4 survives (4/4): per-path scoping is not discriminated by the test; reset verified by reading.
- #2 reopened: with 219d94f5, PlaybackControllerStaleCallbackTest failed 1 of 6 emulator runs (itest-fix1-1.xml, "A's error never reached the player"). The rejection of #2 no longer holds: the bounded remainder flakes in practice and AC-6 (repeat runs green) is not met. Severity raised to major; fix round 2 on #2 only.
