# Readiness 3: startup-first-input-on-tv

Verdict: **NOT READY**

Base c506d0bd; phase A f48beb86; spec at bb1af218; plan as in the worktree (uncommitted edit over bb1af218); test design round 2 (`.flow/runs/startup-first-input-on-tv/test-design.md`).

## Fix list

| # | Item | Fix |
|---|---|---|
| 1 | The plan's Global constraints name "spec section Test design, Traceability table" as the source of test names and red/guard status, and Task 10 cites "Manual checks 2, 3; check 4". The spec at bb1af218 has no `## Test design` (headings: Problem / Why, Acceptance criteria, Out of scope, Design); 740d780f had one, the round-2 recopy dropped it. The round-2 rows and manual checks exist only in the run folder, which does not merge with the code | Copy test-design.md round 2 (Risk profile, Traceability, Bug, Manual checks, Perf) into the spec as `## Test design` |
| 2 | AC-9 (brief and spec) lists `StartupImageActivityTest` as unchanged with no reference point, but phase A (Task 5, f48beb86) rewrote it: `git diff --stat c506d0bd f48beb86` shows `StartupImageActivityTest.kt` 42 lines changed. Read against base, as the gate reads the diff, AC-9 already fails. The plan (Global constraints, Task 9 Done when) and the test-design AC-9 row use f48beb86 | State the reference in AC-9 (brief, spec): `StartupImageActivityTest` unchanged since f48beb86, the others since c506d0bd |

## Checks

| Check | Result |
|---|---|
| Every live AC has a plan task and a Test design row | Pass: AC-1 Tasks 5, 7, 8; AC-2 Tasks 1, 3, 4, 9; AC-3 Task 6 (done, 5fb5de17); AC-5, AC-6 Task 10; AC-9 Task 9 full gate. Every round-2 row of test-design.md is named in Task 7, 8 or 9 |
| Citations resolve at f48beb86 | Pass, sampled: `RevealGate.kt:54-55` (`markedIn`), `:78-85` (`onImageFinished`, `!revealedOnce` at `:81`), `:105-113` (`update`, covering rule); `StartupMarks.kt:9-18`, `:36-62`, `:68` (`onTimeout`), `:70-72` (`emitWhenComplete`), `:74-93` (`emit`); `RevealObserver.kt:13,15-27,28-33`; `StartupReveal.kt:18-34`; `TelemetryRuntime.kt:163`; `TelemetrySink.kt:128-130` (`schedule`), `:260` (`DRAIN_PERIOD_MS`); `TelemetryExecutor.kt:32-34`; `TelemetryEvent.kt:131-147`, `:218-227`; `KeyProbe.kt:114-122` (`answer`, `:117`, `:121`); `TelemetryEncoder.kt:67-80`, `:121-126`; tests `StartupMarksTest.kt:10-12,44,142`, `RevealGateTest.kt:57-67,204,220`, `TelemetryRuntimeTest.kt:243`, `SectionVMTest.kt:201`, `PuberVMTelemetryTest.kt:136,153,169,197`, `KeyProbeGateTest.kt:65`, `TelemetryEncoderTest.kt:33,61,75`. `git diff --stat f48beb86 HEAD` touches docs only |
| No task leaves the build red; dependency order | Pass: Task 7 `schedule` has an inline default (other callers compile); Task 8 `onReadyRequested` has a default body, consumes Task 7; Task 9 `waitMs` defaults to null; Task 10 runs on the Task 9 commit |
| P0 rows: defect named, red/guard, backticked test | Pass for round 2. Expected values re-derived: `heldDrained_reportedAfterACapReveal` log `["revealed:CAP:1", "ready:1", "drained"]` matches `RecordingRevealObserver` and the default immediate `afterFrame`; `keyLatCarriesDispatchWait` `waitMs` 30, `latMs` 100; expired at `frame(6_001, 10)` is 5001 ms past dispatch; `ready_req` 1100 |
| Out of scope respected; blocking questions answered | Pass. Brief open question 1 (speed-profile compile) is non-blocking; decided not authorised, Manual check 4 NOT CHECKED |

## Notes for implementation

- With the inline default `schedule`, `markRevealed` runs the 5 s bound task at once, so default-built marks still emit at the reveal; the extra `markReady(0)` in guard `oneLineWhenRevealedComesBeforeFirstContent` is not needed for it to pass. Keep the change only if the test design row requires it (AC-9: guards change only where the contract does).
- `ManualTelemetryExecutor.runScheduled` runs a snapshot; in `revealedStartWritesOneLineWhenBoundAndFallbackBothFire` the 60 s fallback runs first and the single-shot `emitted` blocks the rest, so the test proves single emission, not which path emitted.
- The plan edit is uncommitted in the worktree; commit it with the spec fix.
