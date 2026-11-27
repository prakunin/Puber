Round 1

CHECK: check.full (agentw testDevDebugUnitTest ... lintInstrumentationDebug) -> pass (BUILD SUCCESSFUL, mostly up-to-date); then test-baseline-profile-tooling.sh -> all PASS; test-itest-tooling.sh -> pass (6/6)
DEVICE: host.docker.internal:5555 (emulator, per brief)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | test_incomplete_run_fails |
| AC-2 | PASS | test_no_xml_fails |
| AC-3 | PASS | test_zero_tests_fails (fake Gradle). Real run with unknown class: Gradle exits 2 itself, the check was not reached |
| AC-4 | PASS | test_complete_passing_run_succeeds; real run exit 0, XML found (tests=4, failures=0) |
| AC-5 | PASS | test_stale_xml_not_counted |
| AC-6 | PASS | test_gradle_failure_kept; real NoSuchTest run exit 2 |
| AC-7 | PASS | command above run in the worktree (conductor appends to config.yaml) |
| AC-8 | PASS | docs/testing/player-playback.md:39-41 names the three checks, no exit-0 warning |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/itest-incomplete-run-green/docs/superpowers/evidence/2026-11-27-itest-incomplete-run-green-device.md (audit: evidence_audit_status=passed | evidence_dir=/home/dev/orca/workspaces/Puber/itest-incomplete-run-green/docs/superpowers/evidence | files_scanned=426 | audit=0)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
Note: AGP result path confirmed on a real run. Nothing committed, per the brief.
