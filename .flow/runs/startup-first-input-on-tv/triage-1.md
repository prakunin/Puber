# Review triage - round 1

Run: startup-first-input-on-tv   Base: c506d0bd   Reviewed head: 6eb30d7c

Sources: review-blind-1.md, review-edge-1.md, review-tests-1.md,
review-codex-1.md (ran: review, adversarial).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | tests F1 | major | evidence verify-device.md, qa-report.md AC-6 | AC-6 marked PASS though B/C keys landed 2.4-3.6 s after reveal (not +300/+1000 ms) and anchor A has 2 latency lines | Read the evidence "inj - revealed" column and qa MANUAL 2; holds | fix | qa re-measure with a trigger that can hit the reveal-relative anchors and n >= 5 at A; otherwise B/C reported "NOT MET as specified" with actual offsets, never PASS |
| 2 | edge F1, codex | minor | `RevealGate.kt:108-109`, `StartupReveal.kt:57` | Restored start (revealNow without begin) never reports FORCED, so the startup line has no cause and waits the 5 s bound | `update` reports only when `begun`; restored path calls `revealNow()` only; holds | fix | implementer: report FORCED once when not begun, without covering or restarting the cap; add a RevealGateTest case |
| 3 | blind F2 | nit | `StartupMarks.kt:118`, TelemetryEncoderTest `startupOmitsImageFieldsNotReached` | `dropped_keys` always written in production, but the encoder test models it as optional | Read emit; holds | fix (cheap) | implementer: make `droppedKeys` non-null on `Startup` or align the test |
| 4 | tests F2 | minor | StartupImageActivityTest (frozen since f48beb86) | P0 guard "onStart is not in flight" lost its assertion | Read the rewritten test; holds | fix (cheap) | implementer: new test class with begin/onStart/markReady/assertFalse(covered); frozen file untouched |
| 5 | tests F3 | minor | `KeyLatencyMatcherConcurrencyTest.kt:34,41`, `KeyProbe.kt:89` | Concurrency guard counts refused arms, so a double emit can hide | `arm` returns at MAX_PENDING; `armed` incremented regardless; holds | fix (cheap) | implementer: unique identity per key (waitMs = i), fail on duplicate |
| 6 | blind F1 | minor | `KeyProbe.kt:83-110`, `KeyLatencyMatcherTest.kt:128-134` | A handled key that draws nothing gets the latency of a later frame | Holds in code; it is design risk 6 ("a handled key that draws nothing is matched to the next frame up to 5 s later"), accepted at gate 1 because AC-2 requires slow keys to be reported and frame timing alone cannot tell a no-redraw key from a stalled one | reject | Accepted design risk under AC-2; the reviewer's fix would drop real stalled keys, which is what the baseline is about |

## Rejected codex claims

- AC-6 anchors missed - codex rejected it as a run fact, not code; the tests lens raised it as a major and it is fixed as row 1.

## Next

- Fix round: implementer rows 2-5 -> check.fast -> qa re-measure (row 1, AC-6, plus AC-1 unit evidence) -> lens edge and codex on the incremental diff, lens tests (tests changed). Unproven lines: 8 (blind 3, edge 3, tests 2).
