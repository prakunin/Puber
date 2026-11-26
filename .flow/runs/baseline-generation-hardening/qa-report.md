Round 1

CHECK: check.full -> pass (BUILD SUCCESSFUL; BaselineProfileContentTest 7/7, 0 failures); bash tools/tests/test-baseline-profile-tooling.sh -> 15/15 PASS
DEVICE: 192.168.1.110:5555 (box, stage only, M4). Generation evidence taken from the implementer's emulator run (not re-run).
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | test_leftover_bak_is_restored_before_backup |
| AC-2 | PASS | failed/HUP/TERM/INT/success shell tests; on-device Ctrl-C (M3) not observed by me |
| AC-3 | PASS | test_bak_in_generated_dir_is_git_ignored |
| AC-4 | PASS | test_preflight_waits_for_offline_device, _times_out_naming_last_state, _reconnects_missing_device |
| AC-5 | PASS | test_preflight_refusal_kills_no_server, test_generation_kills_only_baseline_server (concurrent 5038 server on emulator not observed by me) |
| AC-6 | PASS | test_make_target_uses_only_baseline_port, test_baseline_port_equal_to_itest_or_default_refused |
| AC-7 | PASS (negative path inferred) | Pass path: logcat 20261126-183120 shows 5 Rail iterations plus late check, no "did not reopen". Negative: androidTest result XML (18:57, 3 journeys skipped) fails "The rail did not reopen after entry 5 of 6" with the tag selector; the scratch edit and base-selector counter-run were not seen by me |
| AC-8 | PASS (script exit code not seen) | Logcat: 44 PuberGen lines, all unknown=[] and missing=[] through the final Playback late check; BaselineProfileContentTest green on the committed profile in check.full; working tree clean, profiles committed in ddbe66c7 |
| AC-9 | PASS | test_api34_refusal_reason_text; docs/release.md diff reviewed |
| M4 | PASS | box evidence below |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/baseline-generation-hardening/docs/superpowers/evidence/2026-11-26-baseline-generation-hardening-device.md (audit passed; committed 6063ddec)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none required
Notes: M4 on the box: tagged build (on-device sha matched) vs a temporary base-MainScreenComponent build, identical rail behaviour: opening, focus placement, D-pad traversal through all 12 entries, Series open and LEFT return; no crash, session intact. Temporary base edit reverted, tree clean, tagged APK reinstalled. Side finding: after LEFT from content the rail focuses the entry nearest by position (Settings), not the selected tab, on both builds, so D7's stated premise is false on the box; the tag-scoped check does not rely on it. Lease released.
