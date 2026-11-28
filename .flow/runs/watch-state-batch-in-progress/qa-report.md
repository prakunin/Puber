Round 1

CHECK: check.full -> pass (BUILD SUCCESSFUL, 117 tasks, 3 executed 114 up-to-date; both tooling scripts PASS). Gradle reused the 07:16 unit results of unchanged sources (WatchStateRepositoryTest 53 tests, 0 failures); HEAD adds only docs/tests records.
DEVICE: 192.168.1.110:5555
ACS: AC-1 PASS (unit), AC-2 PASS (unit), AC-3 PASS (unit), AC-4 PASS (itest 12/12), AC-5 PASS (device), AC-6 PASS.
EVIDENCE: /home/dev/orca/workspaces/Puber/watch-state-batch-in-progress/docs/superpowers/evidence/2026-11-28-watch-state-batch-in-progress-device.md
DEFECTS: none

Round 2 (AC-4 only)

CHECK: not re-run this round (scope: AC-4 itest only); the change since round 1 is test-only.
DEVICE: 192.168.1.110:5555 (leased, released)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-4 | PASS | itest WatchStateDaoTest 13/13 on the box; markInProgressInOneTransactionEmitsNoPartialList and the control markInProgressInSeparateCommitsEmitsAPartialList both green |

TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/watch-state-batch-in-progress/docs/superpowers/evidence/2026-11-28-watch-state-batch-in-progress-device.md
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
