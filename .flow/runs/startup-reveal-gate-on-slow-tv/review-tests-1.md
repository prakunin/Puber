LENS: tests  ROUND: 1  BASE: 9158cf82

check.full (`cd /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv && AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug`) passed with BUILD SUCCESSFUL and exit 0.
- Unit tests: 253 suites, 2466 tests, 0 failures, 0 errors, 0 skipped.
- detekt and lint are green.
- Gradle reported every task UP-TO-DATE, so the results come from the 18:17 build of the same sources. During the review, HEAD moved from 854a4981 to 3289825e. That commit changes only the spec, by restating AC-7, so no code changed.
- `RevealGateTest` has 33 cases, all nine new grace cases among them.
- Every P0 guard named in the Traceability table exists under its name and was not weakened. The diff changes assertions in only one test, the planned rewrite of `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd`, and that rewrite still checks that the late request `c` adds no reveal.
- The Implementation record shows a red run for all four P0 red tests. I read through the gate code against the AC-4 red test: `graceFromBeforeAnotherBegin_isDropped` would fail if `onGrace` lost its generation check, because `ready` is true again after the second `markReady`.
- AC-1 to AC-5 have unit tests. AC-6 to AC-10 have documented device checks.

### F1 [minor] The restated AC-7 has no verdict in the evidence, and the Traceability row still states the old AC-7
- Where: docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md:130; docs/superpowers/specs/2026-11-23-startup-reveal-gate-on-slow-tv.md:143
- Problem: The spec's AC-7 row (line 24) now counts anchors A, C and the baseline-method control, and records anchor B only as an observation. The evidence verdict table still reads "AC-7 FAIL on set 2: 5 of 15". The Traceability row at line 143 still reads "at most one `key_lat` over 199 ms across 15 starts". The data for the new wording is in the file (A and C 0 of 10 over 199 ms, control 0 of 15 over 50 ms), but no row judges AC-7 as restated.
- Failure scenario: The final gate reads the evidence verdict table and sees AC-7 FAIL with no PASS row for the restated AC-7. It either blocks a run that the owner's decision allows, or passes AC-7 with no recorded check.
- Fix: Add an AC-7 (restated) verdict row to the evidence: PASS, A+C 0/10, control 0/15, focus ring moves one row each start, anchor B recorded as an observation against `ready_drained`. Bring Traceability line 143 in line with the restated AC-7.

### F2 [minor] The AC-6 PASS rests on set 2 only; set 1 shows the bound fails whenever ready lands within G of the cap
- Where: docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md:108,129
- Problem: In set 1, 6 of 15 starts revealed by `cap` with `ready` < 2500 (ready 2323 to 2470). With G = 300 and the cap at 2500, any start whose ready falls between 2200 and 2500 ms reveals by cap. AC-6 forbids that. The verdict calls set 1 a method effect (early screencap load) and judges only set 2 and the control, where ready topped out near 2000.
- Failure scenario: A loaded or slower S1 start with ready at about 2300 ms ends as a `cap` start, the case AC-6 is meant to remove. The PASS does not say that this window exists.
- Fix: In the AC-6 verdict, state that the bound holds only for ready up to cap - G (2200 ms), and cite set 1 as the case where it does not. Alternatively, bring it to the owner as a known limit; no code change is required by this lens.

### Unproven
- /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/StartupReveal.kt:22,62 There is no JVM test that `installFrameWait` wires `graceHook` and `resetForTests` restores the no-op. The spec marks this UNTESTABLE, and the device shows `grace` as the cause in 14 of 15 starts. It would become a finding if a later change to `StartupReveal` could drop the wiring without the device set being run again.
- /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/app/src/main/java/com/kino/puber/core/startup/RevealGate.kt:75 No test covers the `forcedCause == null` check that skips arming G after a cap before the ready frame. Removing it would only queue a G that does nothing, because `onGrace` and `revealedOnce` block it. It would matter only if a caller relied on G not being queued.
