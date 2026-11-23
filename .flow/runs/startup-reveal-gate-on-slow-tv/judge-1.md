# Gate - startup-reveal-gate-on-slow-tv - round 1

<!-- owner: final-judge, mode gate. Trust files and command output, not summaries. -->

**Decision:** CONCERNS
**Head:** bf142737   **Base:** 9158cf82

## Done as asked

The code diff is `RevealGate.kt`, `RevealObserver.kt`, `StartupReveal.kt`, `StartupMarks.kt` (KDoc only) and three test files. It matches the spec Design table: the cover rule uses `held` (`RevealGate.kt:123`), `REVEAL_GRACE_MS = 300L`, `graceMs`/`scheduleGrace`, the grace timer is armed in the `markReady` frame block only when `forcedCause == null && held.isNotEmpty()`, `onGrace` has the generation, `ready` and `forcedCause` guards, `RevealCause.GRACE`, and `graceHook` runs on an async main-looper handler set in `installFrameWait()` and cleared in `resetForTests()`. The cap value, `MainActivity`, the encoder and S0 are unchanged, as the Out of scope table requires. One gap: the scope row "G: final value fixed from the S1 box set and written into the spec" (spec :34) is not done. The spec still says "Start at 300 ms", though AC-6 passed at 300.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `RevealGateTest.imagesSettlingAfterReadyUncovers`, `sameRequestFinishedTwice_doesNotUncoverOthers`, `graceAfterHeldDrained_changesNothing` | read in the diff/tree; check.full green at bf142737 (judge run) | PASS |
| AC-2 | `graceWithHeldInFlight_revealsWithCauseGrace`, `markReady_withHeldRequests_armsOneGraceOfGraceMs`; production wiring: device | unit as above; `grace` cause in 14 of 15 starts of set 2 and the control (evidence, Set 2 and Control) | PASS |
| AC-3 | rewritten `heldDrained_reportedWhenRequestsInFlightAtReadyAllEnd`, `markReady_withNothingHeld_armsNoGrace`, two guards | unit | PASS |
| AC-4 | `graceFromBeforeAnotherBegin_isDropped`, `graceAfterCapReveal_keepsCauseCap`, `capAfterGraceReveal_keepsCauseGrace`, `markReadyBeforeAnotherBegin_armsNoGrace`, `graceAfterReset_isDropped`, guards, `StartupRevealTest` | unit; all named guards present | PASS |
| AC-5 | `TelemetryEncoderTest.startupWritesGraceCause`, `StartupMarksTest.graceRevealLineWaitsForHeldDrain` | unit | PASS |
| AC-6 | device set 2 and control, 15 S1 starts each | median 326 / 321 (recomputed from set 2: 326), max 379 / 332, 0 cap starts with ready < 2500 | PASS on set 2 and the control; set 1 breaks the cap clause (see Concerns 1) |
| AC-7 (restated) latency | anchors A, C and the control | 0 of 25 first keys over 199 ms (evidence, Result per AC) | PASS |
| AC-7 (restated) focus | focus ring one row per key in every start | one screenshot, start not identified; 0 of 25 counted starts checked | NOT CHECKED |
| AC-8 | screenshot right after a grace fade, owner judges | one screenshot 1.3 s after the reveal, not during the fade; not committed (account data) | NOT CHECKED (owner, manual check 1) |
| AC-9 | S0, 5 cold starts | cap 5 of 5, revealed 3201-3958 | PASS (observation) |
| AC-10 | K2 at detection +2.5 s, set 2 | 0 of 15 over 50 ms | PASS (observation) |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| img_idle / in_flight meaning changed for cause ready | blind F1 (minor) | fixed | a00eea2b, 61b05719 (`StartupMarks.kt` KDoc) |
| No JVM test of production `graceHook` wiring | blind F2 (minor) | rejected, accepted: the spec lists it as device-only (R3), and `grace` causes in 14 of 15 S1 starts show the wiring works | triage-1 #2; evidence Set 2 |
| Restated AC-7 had no verdict; Traceability row stale | tests F1 (minor) | fixed | 7ef4b7dd, 0800302d |
| AC-6 limit ready <= cap - G not stated | tests F2 (minor) | fixed | 7ef4b7dd, 36fcb051 |
| AC-7 focus clause over-claimed | edge-2 F1 (major) | fixed: verdict split, focus NOT CHECKED, handed to owner | 36fcb051, bf142737 |
| AC-6 limit mixed clocks; wrong ready range | edge-2 F2, F3 (minor) | fixed | 36fcb051 |
| onRevealed KDoc guarantee | edge-2 F4 (minor) | fixed | 61b05719 (`RevealObserver.kt:29`) |
| Manual step 4 K2 on control starts | edge-2 F5 (nit) | fixed | cdb85fa1 |
| AC-10 row claimed focus; focus wording; HTML entities | edge-3 F1-F3 | fixed | bf142737 (evidence AC-10 and AC-6 rows read) |

`review-tests-1.md` is cited by triage-1 but absent from the run folder. Its two findings are traced through triage-1 only. Triage-1 #2 is the only rejection, and I checked it.

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | On the box, `revealed - ready` median went from 473 to 326 ms and max from 642 to 379; cap starts with ready < 2500 went from 3 of 45 to 0 of 30. A key 97-232 ms after a grace reveal regressed (anchor B, 5 of 5 at 291-543 ms); the owner accepted this, follow-up run `startup-post-reveal-key-latency`. The new gate methods are not in the baseline profile (accepted Q4). |
| Reliability | Stale-generation, cap, forced and reset paths are unit-tested. Unproven: a grace that fires while the app is backgrounded during the splash lifts it with cause `grace`, which has no visible effect. |
| Maintainability | Follows the existing `afterFrame` hook pattern. No dead code. Comment lines over 120 chars (`RevealGate.kt:61`, `RevealObserver.kt:23,29`) pass detekt because `excludeCommentStatements: true`. |
| Security / privacy | `tools/mobile-evidence-audit.sh` on the evidence dir: passed (84 files). The account screenshots were not committed. Injector removed from the box. |
| TV | Focus on the counted starts was not recorded (AC-7 focus). |

## Manual checks for the owner

1. AC-8 (skeletons at fade end): on box 192.168.1.110:5555 with `com.kino.puber.stage` in S1 (the conductor recipe: INSTALL_PROFILE, force-stop, `cmd package compile -m speed-profile -f`), cold-start the app 3 times and watch the 300 ms splash fade. Judge whether the grey skeletons in the hero and card slots during or right after the fade are acceptable.
2. AC-7 focus: in the same S1 state, cold-start and press DPAD_DOWN once about 3 s after launch, 5 times. Each time the focus ring should move from the hero to the first rail, one row, never two.

## Must fix

none

## Concerns for the owner

1. AC-6 holds only while ready - begin <= cap - G (about ready <= 2330 ms on the line's clock). In the disturbed set 1, 6 of 15 S1 starts with ready 2323-2470 ms revealed by cap, so the clause "no start with ready before 2500 ms has cause cap" cannot hold for ready in 2330-2500 ms by design.
2. AC-7 focus clause: NOT CHECKED on the 25 counted starts, though a per-start screenshot could have checked it in verify. It is in manual check 2.
3. The scope item "final G written into the spec" is open. G = 300 is the value AC-6 passed with; it needs a one-line spec edit before ship.
4. Anchor-B regression: first keys 97-232 ms after a grace reveal took 291-543 ms (5 of 5), against at most 67 ms on the old gate. Accepted by the owner's restated AC-7; it is carried by `startup-post-reveal-key-latency`.
5. Codex review was skipped in all three rounds (usage limit; owner's exception).
6. `review-tests-1.md` is missing from the run folder.
