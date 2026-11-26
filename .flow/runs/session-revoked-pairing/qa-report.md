Round 4

(Round 1: static gate green; AC-5, 6, 8 FAIL on the box. Round 2: fixed; AC-1..9 PASS. Round 3: AC-1..9 PASS incl. persisted blocked line after force-stop; AC-10 owner's call.)

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at 672d3618; SessionEndCleanupTest 3, KinoPubApiClientSessionEndTest 19, SessionEndReasonForTest 8, 0 failures)
DEVICE: 192.168.1.110:5555 (leased and released; auth-restore before and after; install-preserve outcome installed, destructive_action false; APK sha256 776218d8...)
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1..4, AC-9 | PASS | check.full, unchanged suites green |
| AC-5 | PASS | unit (SessionEndCleanupTest, bus tests); box: one stable Auth over 54 s, no Home spinner |
| AC-6 | PASS | unchanged since round 3; blocked line on cold start again (r4) |
| AC-7 | PASS | HomeVMTest, DeviceSettingsVMErrorTest; itest from round 2 not re-run (untouched) |
| AC-8 | PASS | M-1: cold start of the banned session shows Auth with the blocked line at 12 s, mirror hint by 30 s |
| AC-10 | NOT CHECKED | owner's judgement; screenshots in evidence |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/session-revoked-pairing/docs/superpowers/evidence/2026-11-26-session-revoked-pairing-device-r4.md (+ r4 pngs), audit passed, 8be284f2
DEFECTS: none
UNAVAILABLE: none (worktree build has no client secret, so no device code is shown)
MANUAL: owner judges the blocked line (AC-10) and whether the double Back in the mirror dialog also happens on master. The box holds the restored banned session; do not launch a worktree APK now.
