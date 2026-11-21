# Triage round 2: startup-splash-preload

Incremental range reviewed: 8f8793d3..e6e4431c (rebased; full base 8428c946). Sources: review-edge-2.md, review-tests-2.md, review-codex-2.md (clean; one adversarial finding rejected by codex-reviewer as unproven). Round 2 rule: fix blockers and majors only; real minors are accepted and listed for the owner.

| ID | Sources | Severity | Finding | Verdict | Note |
|----|---------|----------|---------|---------|------|
| T8 | edge-2 F1 (tests-2 unproven 1) | blocker | ApiDomainInteractor.kt:143-145: owner's finally cancels the deferred before clearing inFlightWalk; a joiner resumed inline (both on Dispatchers.Main.immediate, e.g. two HomeVM loadHome jobs after a cancel) re-joins the cancelled deferred and spins forever: ANR | fix | Verified in code at e6e4431c: join() on a finished job does not suspend, the loop re-reads the same inFlightWalk. Clear the slot (if === walk) before cancel/complete. Red test: owner cancelled while a second caller on the same thread is joined; joiner walks itself and returns; a third call walks again (covers tests-2 F2). |
| T9 | tests-2 F1 | blocker | StartupReveal default frame hook builds Handler(Looper.getMainLooper()), so JVM VM tests pass or fail by class order | fix | Verified: frameHook = ::afterTwoFrames default, only resetForTests swaps it. Default to an immediate hook in JVM-safe code and install the Choreographer/Handler two-frame hook from production startup (PuberApp or MainActivity). Prove by running PuberVMTelemetryTest and SectionVMTest alone. |
| T10 | tests-2 F2 | minor | No test for the owner-fails-while-joined retry path | fix (with T8) | The T8 red test is this test. |
| A1 | edge-2 F2, codex-2 rejected item, tests-2 unproven 2 | minor | domainVersion check and the write in switchToFound are not atomic | accept | Window of milliseconds between check and write; not a regression (user writers never took the walk lock). Listed for the owner. |
| A2 | tests-2 F3 | minor | T3 version bump tested only through saveCustomDomain, not resetToDefault/applyEndpoint | accept | Listed for the owner. |
| A3 | tests-2 F4 | minor | T4 frame wait and T7 listener wiring untested in production code | accept | Covered by qa-device evidence required before gate 2 (Home cold start and content-link into Details, revealed after first poster decode). |
| R1 | edge-2 unproven | - | afterTwoFrames while the activity is stopped during the splash waits for the cap | reject | Unproven; the app is invisible then, the cap bounds it. |
| R2 | edge-2 unproven | - | Handler reached by a JVM test without resetForTests | reject as duplicate | Same as T9. |
