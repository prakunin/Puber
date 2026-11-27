Round 1

CHECK: check.full (./tools/agentw testDevDebugUnitTest ... && test-baseline-profile-tooling.sh && test-itest-tooling.sh) -> pass (exit 0)
DEVICE: host.docker.internal:5555 (emulator, leased, released, mac-emu stopped); box not used per device-box policy for this run
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | NetworkTracks 13/13 x3 (itest-1..3) + QA re-run 13/13; code requires pending delayed-segment with BUFFERING before gate.countDown(); mutant M1 fails |
| AC-2 | PASS | same runs; quality-low.m3u8, gates video_360_000.ts and audio_english_000.ts, both pending required with tracks |
| AC-3 | FAIL as written; PASS via substitute M2b | M2 as written (master, 720p gates) survives (itest-15, 13/13) because the emulator requests 720p first. M2b (master, gates on 360p) fails (itest-18). Accepted only under the recorded decision |
| AC-4 | PASS | Resources 3/3 (itest-4); assertTrue(getCachedSpans(url).isEmpty()) after second removeResource; KDoc names player-corrupt-cache-recovery; name unchanged |
| AC-5 | PASS with delta | no sleep between aOutcome() and switchStream(B); bounded wait on response-body close (app interceptor, per-path latch, EventListener never fires in debug builds) then bounded Looper isIdle wait; PARSE_WAIT_MS is the bound. Remaining Thread.sleep are poll/settle sleeps |
| AC-6 | PASS | 6/6 final-code runs (itest-9..14) + QA re-run 4/4 at HEAD; assertNotNull(probe.error) kept |
| AC-7 | PASS | check.full exit 0 |
TESTS ADDED: none by QA (the run itself changes only instrumented tests)
EVIDENCE: /home/dev/orca/workspaces/Puber/player-device-tests-hardening/docs/superpowers/evidence/2026-11-27-player-device-tests-hardening-device.md (audit passed; not committed, as instructed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Reviewer notes: the isIdle wait can be trivially true if other main-looper messages are queued (the assertNotNull(probe.error) keeps a miss loud); M4 not run.


---

Round 2 (head c1de902f, evidence only; no new device run, no test or production change by QA)

CHECK: no re-run by QA; round 2 is the evidence file only (the judge ran check.full at c1de902f: exit 0)
DEVICE: not needed this round: evidence update from the implementer's emulator runs (host.docker.internal:5555, leased and released by the implementer); XMLs itest-fix1-1..6, itest-fix1-m4, itest-fix2-1..10 re-read by QA
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | NetworkTracks 13/13 x3 (itest-1..3); M1 fails (itest-16); files unchanged since |
| AC-2 | PASS | same runs; single-variant gates on 360p video and audio, both pending required |
| AC-3 | FAIL as written; PASS via substitute M2b | M2 survives (itest-15); M2b fails (itest-18) |
| AC-4 | PASS | Resources 3/3 (itest-4); cache-empty assertion after second removeResource |
| AC-5 | PASS with delta | no sleep between A's outcome and switchStream(B); bounded close wait (interceptor, latch reset at request start) plus bounded queued-message wait inside a 5-attempt retry |
| AC-6 | PASS | itest-fix2-1..10: 4/4 each, 10 of 10; fix round 1 had 5 of 6 (itest-fix1-1 failed: A's error never reached the player); M4 mutant survives (itest-fix1-m4, 4/4); the old assertNotNull(probe.error) is replaced by the retry and itest-9..14 are superseded |
| AC-7 | PASS (judge) | check.full exit 0 at c1de902f |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/player-device-tests-hardening/docs/superpowers/evidence/2026-11-27-player-device-tests-hardening-device.md (rewritten for c1de902f; audit: exit 0; not committed, as instructed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Residual: per-path latch scoping is verified by reading only (M4 survives); AC-3 accepted only via substitute M2b.
