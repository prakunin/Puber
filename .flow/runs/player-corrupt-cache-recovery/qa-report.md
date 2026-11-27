Round 1

CHECK: check.full (config.yaml) -> pass (BUILD SUCCESSFUL, most tasks up-to-date from the implementer run; tooling shell tests all PASS)
DEVICE: host.docker.internal:5555 (emulator, leased, released, stopped). Test-only change, so no box run.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Diff read: mark before prepare, wait on corrupt.error, then StreamFailure(NEXT_URL, url) contained and no Error. QA class run at HEAD 4ce45e30: 3 tests, 0 failures |
| AC-2 | PASS | itest-1..3.xml (3 tests, 0 failures each) plus the QA run: 4 green runs |
| AC-3 | PASS (not re-run by QA) | itest-mutant.xml read: 1 failure, "corrupt-cache failure: condition stayed false for 15000 ms", with the media route served 200 and StreamReady seen, so the mutant plays clean and the assertion bites |
| AC-4 | PASS | KDoc and docs/testing/player-playback.md:87 state failover with StreamFailure(NEXT_URL); no "not asserted" or open-run pointer; test name unchanged |
| AC-5 | PASS | check.full green |
Manual rows: 1 (3 runs) and 2 (mutant) covered by the instrumented runs; 3 covered by the AC-4 read; 4 done (XMLs in the evidence dir). No row is not applicable beyond the box run.
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/player-corrupt-cache-recovery/docs/superpowers/evidence/2026-11-27-player-corrupt-cache-recovery-device.md (plus 5 result XMLs; audit passed, uncommitted)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none


Round 2

CHECK: not re-run (evidence-only round); round-1 check.full was green
DEVICE: no new device run by QA; implementer runs at head a8607ccc on emulator host.docker.internal:5555 read from XMLs
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1..5 | PASS | unchanged from round 1 |
| Judge-1 fix (assertFalse(sawReady), late Error re-check after releaseAndDrain) | PASS (not re-run by QA) | itest-fix1-1..3.xml: 3 tests, 0 failures, 0 errors, 0 skipped each; commit a8607ccc diff read |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/player-corrupt-cache-recovery/docs/superpowers/evidence/2026-11-27-player-corrupt-cache-recovery-device.md (new row AC-6; 3 XMLs copied as 2026-11-27-player-corrupt-cache-recovery-itest-fix1-1..3.xml; uncommitted). Audit: evidence_audit_status=passed
evidence_dir=/home/dev/orca/workspaces/Puber/player-corrupt-cache-recovery/docs/superpowers/evidence
files_scanned=436
exit=0
DEFECTS: none
UNAVAILABLE: none
MANUAL: none

