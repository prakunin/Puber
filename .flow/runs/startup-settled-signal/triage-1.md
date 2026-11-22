# Triage round 1: startup-settled-signal

Reviewed: 7bcbfd83..5686efd0. Inputs: review-blind-1.md, review-edge-1.md, review-tests-1.md, review-codex-1.md (codex ran: review + adversarial). ux-designer not run: the diff changes no Compose layout (StartupSplashHost gains an onGone callback only).

| ID | Source | Sev | Finding | Verified | Mark | Note |
|----|--------|-----|---------|----------|------|------|
| T1 | tests F1 | major | P0 red row `restoredActivitySettlesAtOnce` has no recorded red run | yes: plan Implementation record says green on the stub | fix | Run it red on a stub where `restored = true` only arms the fallback; record the red line |
| T2 | codex, edge unproven | minor | Deferred auto update check sets `Available` over a manual flow (`Checking`, `Downloading`, `PermissionRequired`, `Error`) | yes: UpdatePromptVM.kt:51-58 has only the `dismissedTagName` guard | fix | Apply the auto result only while the state is `Hidden`; add a unit test for the interleaving |
| T3 | edge F1 | minor | Prompt opens about 1 s after reveal and takes focus while first keys go to Home; a DPAD_CENTER meant for a card can trigger Update | yes: UpdatePromptOverlay.kt:59-67 requests focus with no input guard; at base it opened under the splash with keys swallowed | fix | Ignore confirm keys on the prompt for a short guard (about 300 ms) after it first takes focus; test it |
| T4 | tests F3, blind F2 (dup) | minor | `aSectionArrivingAfterAClearPublishesEvenWhenAStalePublishWasSkipped` orders a race with real-time `delay(200)` | yes | fix | Wait on a condition (requested count grew) instead of a sleep |
| T5 | tests F2 | minor | `aWarmHeldForStartupTakesNoSlot` cannot fail on the mutation it names | yes per reviewer's trace | fix | Pin the order structurally (permit still free while gate closed) or drop the row and note it in the test design |
| T6 | blind F3 | nit | `settlingTwiceResumesEachWaiterOnce` cannot fail | yes: CompletableDeferred semantics | fix | Cheap: replace with an assertion that can regress (onSplashGone before onActivityCreated(false) leaves no fallback running) or drop |
| T7 | blind F1 | minor | Warm relaunch in the same process skips the settle wait; watch-state sync starts at 2 s instead of 5 s | yes | reject | Decided at gate 1 (Q1: "once per process"); StartupSettled KDoc states it. A warm relaunch has the disk/memory cache and an already-warm process, so contention is lower than on a cold start. Owner can reopen it as a run |
| T8 | blind unproven | - | onGone may not fire with animations off | not proven | reject | Unproven; the 5 s fallback covers it by design (AC-3) |
| T9 | edge unproven | - | Fallback may fire while the splash still covers on a jammed main thread | not proven | reject | Unproven on emulator; real-TV evidence belongs to startup-first-input-on-tv |
| T10 | edge unproven | - | `awaitPublishesDrained` can hang in a test that cancels last | not proven | reject | Test-only hook; no test does that |

Fix list for the implementer: T1-T6.
