Round 1

STATUS: needs-conductor (S1 not reached; no forced compile authorised)

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL, 44 of 46 tasks up to date from impl at 7ed2f1ec). RevealGateTest 33/0 failures, TelemetryEncoderTest 26/0, StartupMarksTest 26/0, StartupRevealTest 5/0; all grace tests of the traceability table present and green.
DEVICE: 192.168.1.110:5555 (leased, released). Stage logged in. Master devDebug installed, token refreshed, auth-save done. Worktree devBenchmarkRelease (c31dab2d...) installed with install-preserve (installed, compatible_replace, destructive_action false); on-device sha equal; app not launched after the install.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1..AC-5 | PASS | unit tests listed in test-design traceability, all present and green in the test results XML |
| AC-6, AC-7, AC-10 | NOT CHECKED | S1 not reached: dumpsys "[status=speed-profile] [reason=install]", dump-profiles: profile and reference both empty. This is S0 (same dumpsys line as S0 in release-startup-compilation); not S1. |
| AC-8 | NOT CHECKED | needs an S1 start with cause grace |
| AC-9 | NOT CHECKED | box is in S0 now, but a launch would add runtime profile data and spoil the round-4 recipe (app not launched after install) the conductor needs for S1; the test design runs S0 after S1 |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md (commit 79074a0a, audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: conductor asks the owner for the forced S1 compile (round-4 recipe: dump-profiles both empty, INSTALL_PROFILE result=1, force-stop, compile -m speed-profile -f), without launching the app first. APK c31dab2d is installed. Then re-spawn qa for the S1 set (15 starts) and the S0 set (5 starts after it).

---

Round 1b

CHECK: not re-run (round 1: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass, head 7ed2f1ec; only evidence committed since)
DEVICE: 192.168.1.110:5555 (leased, released). APK c31dab2d (on-device sha equal). S1 confirmed read-only before the sets (speed-profile, reason=unknown), no compile or reinstall for S1. S0 reached by a same-APK install-preserve (installed, compatible_replace, destructive_action false; dump-profiles both empty; reason=install). Box is left in S0.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1..AC-5 | PASS | unit tests, round 1 |
| AC-6 | PASS | set 2 (anchored): revealed - ready median 326, max 379; control (baseline method): median 321, max 332; limits 350 / 550 (baseline 473 / 642). Cap starts with ready < 2500: 0 of 15 in both (baseline 3 of 45). Cause grace 14 of 15 in both, so graceHook fires in production (R3). Set 1 (detector from launch +0.8 s) had 6 of 15 cap: the screencap load slowed ready (median 2286 vs 1832); a method effect, reported, not used |
| AC-7 | FAIL | anchored set 2: 5 of 15 first keys over 199 ms (limit 1), all at anchor B: 399, 304, 291, 356, 543 ms (key 97-232 ms after the reveal mark; wait_ms 11-26, frame_ms 19-54). A: 0 of 5, C: 0 of 5 (max 59). Control with the baseline method (key about 0.6-1.0 s after the reveal): 0 of 15 above 50 ms. Set 1 shows the same B pattern (5 of 5) plus A under load |
| AC-8 | PASS (observation) | screenshot 1.3 s after a grace reveal: Home fully painted, no skeleton or splash, focus ring on the first rail card; the shot shows account data, not committed. Skeleton visibility during the 300 ms fade itself is the owner's call (R9) |
| AC-9 | PASS (observation) | S0, 5 starts: cause cap 5 of 5, no grace, revealed 3201-3958 (median 3588; earlier S0 3269 / 3381), prof 2, one startup line each |
| AC-10 | PASS (observation, latency) | K2 at detection +2.5 s: 0 of 15 above 50 ms; focus per key NOT CHECKED (see AC-7 focus clause) |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md (round 1b section; audit passed; commit 13601474 or its amend, see git log in the worktree)
DEFECTS:
1. AC-7 at anchor B. Reproduce: S1 (reason=unknown), devBenchmarkRelease c31dab2d, cold start, DPAD_DOWN injected 100-230 ms after the splash fade starts (first non-black pixel at (20,72)); first key_lat 291-543 ms in 5 of 5 starts, wait_ms small. The grace reveal at ready + 300 ms puts the first key into the post-reveal burst (ready_drained 0.5-0.7 s after ready), which the baseline avoided by revealing later. Failing check: AC-7 bound of at most 1 of 15 over 199 ms. Keys 0.5 s or more after the reveal are fine. Per the owner decision, regenerating the baseline profile is the stated follow-up only for an AC-6 miss; AC-6 holds, so the conductor decides how to treat AC-7 (spec bound or G).
UNAVAILABLE: none
MANUAL: owner looks at the fade on the box for skeletons (R9). The box is in S0 now; S1 needs the conductor's compile recipe again if more S1 work is wanted.

---

Round 1c (evidence only, no device work, no lease; rows below replace the AC-6 and AC-7 rows of round 1b)

CHECK: not re-run (docs only; head code unchanged)
DEVICE: not needed in this round: the verdicts are restated from the round-1b data in the evidence file
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-6 | PASS, with a stated limit | Set 2 and control as in round 1b (median 326 / 321, max 379 / 332, 0 cap starts with ready < 2500). The bound holds only while ready <= cap - G = 2200 ms; set 1 shows cap reveals for ready 2323-2470 ms (6 of 15). Sets 2 and control had ready 1485-1911 ms, so they do not test beyond the limit. Known limit for the owner at handover |
| AC-7 (restated) | PASS | At most one first key over 199 ms across anchors A, C and the baseline-method control: A 0 of 5, C 0 of 5 (max 59 ms), control 0 of 15, total 0 of 25. Focus ring one row per key (one screenshot, anchor A start). Anchor B observation: 5 of 5 first keys over 199 ms (291-543), arriving 126-330 ms before ready_drained, 97-232 ms after the reveal mark; goes to startup-post-reveal-key-latency |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md
DEFECTS: none under the restated AC-7 (anchor B is an observation, follow-up run startup-post-reveal-key-latency)
UNAVAILABLE: none
MANUAL: owner looks at the fade on the box for skeletons (R9); AC-6 limit (ready > 2200 ms is revealed by cap) noted at handover


---

Round 1d (evidence only, no device work, no lease; rows below replace the AC-6 and AC-7 rows of round 1c)

CHECK: not re-run (docs only; head code unchanged)
DEVICE: not needed in this round: the verdicts are restated from the round-1b data
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-6 | PASS, with a stated limit | Set 2 and control as before (median 326 / 321, max 379 / 332, 0 cap starts with ready < 2500). The bound holds only while ready - begin <= cap - G, about ready <= 2330 ms on the line's clock (begin is 125-155 ms after process start). Set 1 rows 1, 5, 8 (ready 2286, 2307, 2283) are grace, row 6 (2323) is the first cap. Ready was 1662-2001 ms in set 2 and 1718-1911 ms in the control, inside the limit. Known limit for the owner |
| AC-7 (restated) | PASS (latency clause); focus clause checked on one start, rest NOT CHECKED | Latency: 0 of 25 first keys over 199 ms (A 0 of 5, C 0 of 5, control 0 of 15), limit 1. Focus: one screenshot at the end of an anchor A start with two keys; which of the 25 counted starts it came from is unidentified. The other starts are NOT CHECKED for the focus clause; owner decides at gate 2. Anchor B stays an observation (startup-post-reveal-key-latency) |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/startup-reveal-gate-on-slow-tv/docs/superpowers/evidence/2026-11-23-startup-reveal-gate-on-slow-tv-device.md
DEFECTS: none under the restated AC-7
UNAVAILABLE: none
MANUAL: owner looks at the fade on the box for skeletons (R9); owner decides at gate 2 on the AC-7 focus clause (checked on one start only) and on the AC-6 limit (ready - begin > 2200 ms is revealed by cap)
