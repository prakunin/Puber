# Round 4 (AC-4 only; AC-1..3 and 5..8 carried unchanged from round 3 below)

CHECK: not re-run (no code change since round 3)
DEVICE: 192.168.1.110:5555, lease released
EVIDENCE: round-4 section in the device evidence file, audit passed; commit fbe81235, no Co-Authored-By
DEFECTS: none open on AC-4 as measured; the 292 ms cap-cause start is the one thing to watch (two of 45 starts are over 199; a third would move rank 43 over the gate).

# Round 3

Round 1 (summary): AC-4 FAIL (S1 p95 845 ms vs gate 199), AC-3/5/6/7/8 PASS, AC-1 cited, AC-2 implementer-only. Evidence 45d5c3d5.
Round 2 (summary): AC-4 PASS (S1 p95 131 ms), AC-3/5/6/7/8 PASS after every method rule was marked H (1733ce48, dbe5fe89).

Build under test: devBenchmarkRelease of 540c4ee4 content (worktree head 65d7e35a, later commits Makefile/docs only). APK sha256 8251ff30...36e6dd, baseline.prof 29426 B.

CHECK: AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass
DEVICE: 192.168.1.110:5555 (KM6, API 29), lease released
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS (carried) | baseline.md M4 |
| AC-2 | NOT CHECKED by qa (carried) | implementer's M1/M8 record only |
| AC-3 | PASS | baseline-prof.txt: 38673 method rules, 0 without H; favorites 121, history 597, contentlist 383, search 194 lines; details 920, player 4603, PlayerVM 131, DetailsScreen 298, TelemetryRuntime 32, RevealGate 20; only toptabs hit is adoptTopTabsMenuIfNeeded. startup-prof.txt: 24172 method rules without H (321 with), no favorites/contentlist/PlayerVM/DetailsScreen rule. BaselineProfileContentTest green |
| AC-4 | PASS (as measured, 45 starts) | M5 S1 over 45 starts (15 round 3 + 30 round 4, same APK, same method): median 50, p95 90 ms at nearest rank 43 of 45, gate 199. Sorted top: 90 (43), 215 (44), 292 (45). Two of 45 starts are over 199 (215 and 292, both cause cap); 43 had cause ready. 42 of 45 had no key_lat (counted 50). 30-start subset alone: median 50, p95 50 (rank 29), max 292 (start 7, cause cap, revealed 2650); 29 of 30 no key_lat. Round-3 15 starts alone: p95 215 (FAIL). Round-4 box load 2.79-5.47. S1 prep: compile wall 15801 ms, Compiler stats 15665, [reason=unknown]. Per-start table in evidence |
| AC-5 | PASS | unit tests green; box: one startup line with prof in 30 of 30 starts (S1 prof 1, S0 prof 2) |
| AC-6 | PASS (fresh devBenchmarkRelease) | baseline.prof 29426 B and baseline.profm 1646 B in the APK; prod release APK in the worktree (11:50) predates regeneration, not rebuilt |
| AC-7 | PASS | docs/release.md re-read at 65d7e35a: one command, preflight, account-write note, S/P-only copy paragraph; Makefile recipe matches. Read, not run |
| AC-8 | PASS | update over base: prof 2 first start, Details/Play/player ok, BACK Player->Details needed 3 presses (round 2: one), no crash; bg-dexopt-job Success 16.5 s, [reason=bg-dexopt], Compiler stats 16289 (install 4320; round 2 15433); next start prof 1 |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/release-startup-compilation-2/docs/superpowers/evidence/2026-11-23-release-startup-compilation-device.md (round 3 section; audit passed; commit 2d1c7395, no Co-Authored-By)
DEFECTS:
1. (round 3, superseded by the round-4 45-start result) AC-4: S1 first-key p95 215 ms > 199. Repro: install-preserve the APK, INSTALL_PROFILE broadcast, compile -m speed-profile -f, run.sh 15 starts; start 14 (cause cap) lat 215. Per-start list in the evidence.
UNAVAILABLE: none
MANUAL: none
