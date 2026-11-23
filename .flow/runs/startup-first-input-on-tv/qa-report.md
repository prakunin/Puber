Round 1

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL; unit tests re-run with --rerun: 2368 tests, 0 failed, 0 skipped; detekt and lint up to date and green; no HomeVMIncrementalPublishTest flake seen). Frozen tests StartupSettledTest, StartupRevealTest, StartupKeyFilterTest, HomeLaunchFocusSettledTest, StartupImageActivityTest: unchanged since f48beb86 (git diff lists 6 other test files).
DEVICE: 192.168.1.110:5555 (owner TV box), devBenchmarkRelease of ed92c932, apk sha256 2fa6e9e9..., on-device base.apk sha256 equal, install-preserve outcome installed. Compiler filter as installed: speed-profile (reason=install).
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Unit rows in the test design all green in the rerun; device: 18 of 18 starts wrote exactly one startup line with cause, ready_req, ready, ready_in_flight, ready_drained (where in flight > 0), revealed |
| AC-2 | PASS | Unit rows green; device: wait_ms on all 15 key_lat lines, 0 <= wait_ms <= lat_ms on all, none expired; 3 keys dropped under the splash counted in dropped_keys with no key_lat line |
| AC-3 | PASS (met at 5fb5de17) | docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-baseline-device.md, not re-run |
| AC-5 | PASS (observation) | 15 counted + 3 atrace starts, ready present 18 of 18 (all cap reveals), ready - revealed 93/2217/2910 ms (min/median/max), ready_req - first_content 1364/3238/4187 ms; per-start table in the evidence |
| AC-6 | PASS (observation) for the as-installed half; compiled half NOT CHECKED | Filter speed-profile recorded; lat_ms median/p95 A 2943.5/3039 (n=2, 3 dropped), B 3582/3891, C 3070/3362; wait_ms median/p95 A 162/200, B 130/358, C 4/23; 3 atrace starts with slices over 100 ms by name. Check 4 NOT CHECKED: the speed-profile compile is not authorised |
| AC-9 | PASS | check.full green, frozen tests unchanged |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-verify-device.md (committed 6eb30d7c in the worktree; mobile-evidence-audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: (1) make auth-save could not run (benchmark build is not debuggable, "nothing to save yet"), so no .auth snapshot exists for this install; login was intact before and after. (2) The pixel poll lagged: B and C keys went 2.4-3.4 s after the telemetry reveal, not +300/+1000 ms (baseline: 1.3 and 1.9 s median); anchor A keys at 3.5-4.7 s were dropped under the splash in 3 of 5 starts. Latencies stay 2.5-3.9 s at every anchor, near the baseline 3.2 s. If the owner wants true reveal-relative anchors, they need an in-app trigger, not an adb poll. (3) Compiled-state half of AC-6 needs the owner's authorisation for cmd package compile -m speed-profile -f com.kino.puber.stage, run by the conductor.

---

Round 2

CHECK: ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug -> pass (BUILD SUCCESSFUL at 9d454407; unit tests re-run with --rerun: 2370 tests, 0 failed, 0 skipped; detekt and lint green).
DEVICE: 192.168.1.110:5555 (owner TV box), devBenchmarkRelease of 9d454407, apk sha256 efb00a3a..., on-device base.apk sha256 equal, install-preserve outcome installed. Compiler filter as installed: speed-profile (reason=install).
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | Restored-start fix 9eec4d24: `RevealGate.update` reports FORCED once when `forcedCause` is set without `begin` (RevealGate.kt:105-112); the new RevealGateTest case and StartupMarksTest `forcedRevealEmitsWithoutReady` are green in the rerun. Device: 34 of 34 starts wrote one startup line with cause, ready_req, ready, revealed, dropped_keys; ready_drained exactly where ready_in_flight > 0. A restored start has no device row (needs a saved-state restore); covered by unit evidence only |
| AC-6 | PASS (observation) for the as-installed half at anchor A; anchors B and C NOT MET as specified; compiled half NOT CHECKED | Filter speed-profile recorded. A (launch +3 s realised 3062-3156 ms): 22 starts, 9 lat lines, 4 dropped under splash, lat_ms median/p95 3654/3957, wait_ms 592/1036. B (target reveal +300 ms, realised 1220-2063 ms after the mark): 6 lines, lat 3666.5/4330, wait 329.5/515. C (target +1000 ms, realised 1800-2712 ms): 6 lines, lat 4068.5/4745, wait 259/500. 0 <= wait_ms <= lat_ms on all 21 lines, none expired. Atrace slices not re-run (round 1 stands for ed92c932). Compiled half: speed-profile compile not authorised |
| AC-9 | PASS | check.full green, frozen tests unchanged since f48beb86 (git diff 6eb30d7c..9d454407 lists no frozen test file) |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-verify-2-device.md (audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: (1) B and C cannot be placed at reveal +300 / +1000 ms from outside the app: the first frame that shows the reveal is drawn 0.7-0.9 s after the `revealed` mark and nothing external precedes the mark; detector fired 0.9-1.8 s after it. Hitting those anchors needs an in-app hook (a debug-only key injection at the mark). (2) Compiled-state half of AC-6 needs the owner's authorisation for cmd package compile -m speed-profile -f com.kino.puber.stage, run by the conductor. (3) No auth-save snapshot (benchmark build is not debuggable); login intact.

Round 3

CHECK: not re-run: no code changed since round 2 (tree 79afebd3, HEAD ce3d859e).
DEVICE: 192.168.1.110:5555 (owner TV box), lease released. Fresh `devBenchmarkRelease` build of HEAD sha256 efb00a3a... equals the on-device base.apk, no reinstall. Compiler filter before and after both sets: `speed-profile`, reason=unknown (conductor's compile; qa did not compile).
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-6 compiled state (Manual check 4) | NOT CHECKED | The KinoPub API returned HTTP 502 for most requests: 42 of 48 starts (two sets of 24, 4 minutes apart) landed on the Home error state or ended with no ready line, with no `startup` line; a 9-start probe after them read Error or no ready line, never Content. 6 starts reached Home content (A 3, B 3, C 0; spec asks for 12 or more). Those 6: cause cap, revealed 2627-2642 ms, key injected 185-394 ms after the mark, no `key_lat` line, 0 dropped keys, none expired; `ready - revealed` 1860-2652 ms, `ready_req - first_content` 307-912 ms (row 1.8 has neither field). No lat_ms or wait_ms per anchor can be computed; round 2 as installed: A 3654/3957, B 3666.5/4330, C 4068.5/4745 (lat median/p95). Anchors: A met (launch +3.03-3.05 s), B met (+185-394 ms after the mark), C NOT MET as specified (no content start). Atrace of 3 starts not run (it would profile the error path) |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-verify-3-device.md (audit passed; 276ddbee Record the round 3 compiled-state attempt of the startup first-input run blocked by API 502 errors)
DEFECTS: none
UNAVAILABLE: none (the backend 502s are external; Home error state in those starts not examined further)
MANUAL: re-run Manual check 4 when the KinoPub API answers (same installed build, filter speed-profile): 12 or more cold starts with Home content, per-anchor lat_ms and wait_ms, 3 atrace starts.


Round 4

CHECK: not re-run: no code changed since round 2 (tree 79afebd3).
DEVICE: 192.168.1.110:5555 (owner TV box), lease released, emulator not used. Verified before the probe: on-device base.apk sha256 efb00a3a... equals the built devBenchmarkRelease apk; compiler filter `speed-profile`, reason=unknown, before and after. No reinstall, no compile by qa.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-6 compiled state (Manual check 4) | NOT CHECKED | The API still answered HTTP 502: in a 6-start probe (A, B, A, C, A, B) 5 starts landed on the Home error state (err HTTP 502 or ready st=Error) and 1 (anchor B) reached Home content, after 10.3 s to first content (round 2 logged no first_content; its ready_req was 3.5-5.2 s). The spec asks for 12 or more content starts, so the full protocol was stopped early. No lat_ms or wait_ms per anchor; the one content start wrote no key_lat line and no screenshot was taken. Round 2 as installed: A 3654/3957, B 3666.5/4330, C 4068.5/4745 (lat median/p95). Anchors: A injected at launch +3.02-3.04 s but no content start; B n = 1; C NOT MET as specified (no content start). Atrace of 3 starts not run (error path) |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/docs/superpowers/evidence/2026-11-22-startup-first-input-on-tv-verify-4-device.md (audit passed)
DEFECTS: none
UNAVAILABLE: none (the backend 502s are external)
MANUAL: re-run Manual check 4 when the KinoPub API answers (installed build unchanged, filter speed-profile): 12 or more cold starts with Home content, per-anchor lat_ms and wait_ms, 3 atrace starts. Rate seen so far: round 3 6 of 48 content starts, round 4 1 of 6.

---

Round 5

CHECK: not re-run: no code changed since round 2 (tree 79afebd3).
DEVICE: 192.168.1.110:5555 (owner TV box), lease released, emulator not used. base.apk sha256 efb00a3a... unchanged, filter `speed-profile` reason=unknown before and after. No reinstall, no compile by qa. Injector files removed from /data/local/tmp.
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-6 compiled state (Manual check 4) | PASS (observation) | API answered: probe of 3 and 12 more starts, 15 of 15 Home content (A 5, B 5, C 5), one startup line each, dropped_keys 0. key_lat line on 1 of 15 (B: lat 67 ms, wait 8 ms); the other 14 wrote none, so lat_ms <= 50 ms, and a screenshot of each shows the focus ring on the first rail card (no-key reference start has none), no two-row jump. Per-anchor median/p95 not computable (0 lines at A and C). Round 2 as installed: A 3654/3957, B 3666.5/4330, C 4068.5/4745 (lat median/p95). Compiled: first_content median 830 ms, revealed median 2151 ms, cause ready 13 and cap 2. 3 atrace starts: main-thread slices over 100 ms all before 1.6 s after bindApplication (doFrame 117-264 ms, measureAndLayout 142-152 ms), none after the key. Anchors: A met (3026-3038 ms after launch), B met (+183-345 ms after the mark), C met (+919-1046 ms) |
TESTS ADDED: none
EVIDENCE: /home/user1/orca/workspaces/Puber/startup-first-input-on-tv/docs/superpowers/evidence/2026-11-23-startup-first-input-on-tv-verify-5-device.md (audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none
