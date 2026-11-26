# Gate - baseline-generation-hardening - round 1

**Decision:** FAIL
**Head:** d6c128ef   **Base:** b27d8e40

## Done as asked

The diff covers F2-F5 and the stale API 34 text from `brief.md`, and stays in scope. `itest` keeps its 5038 recipe. The only production change is the `main_rail` tag and `testTagsAsResourceId` on the rail `Column` (`MainScreenComponent.kt:292-297`, fallback D8, checked on the box in M4). One gap: `docs/release.md:143-145` still describes the round-2 rule for leftover backups ("dropped as out of date only when `baseline-prof.txt` already holds an `H`-marked rule"). The recipe (`Makefile`, the `[ -s $$f ] && ! grep -qE '^[SP]*L.*->'` branch) and spec AC-1 drop them only when the file exists, is non-empty and has no method rule without H. A fresh merge result carries H rules and is restored, so the guide states the opposite of the code for that case.

## Traceability

Re-run here at d6c128ef: `check.full` exit 0 (Kotlin and profiles unchanged since ddbe66c7, so tasks were up to date) and `bash tools/tests/test-baseline-profile-tooling.sh` exit 0, 30 PASS. Committed `baseline-prof.txt` has no method rule without H, and neither profile contains an `instrumentation`, `mock` or `baselineprofile` symbol.

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `test_leftover_bak_is_restored_before_backup`, `_when_profile_is_missing`, `_when_profile_is_empty`, `test_stale_bak_does_not_overwrite_newer_profile`, `test_sigkill_after_merge_before_remark_restores_original`, `test_leftover_restore_failure_keeps_original_backup` | suite run here | PASS |
| AC-2 | `test_failed_generation_restores_profile`, `test_hup/int/term_to_group_restores_profile`, `test_failed_final_remark_restores_profile`, startup-profile tests | suite run here; signals sent to the real recipe with a fake Gradle; the on-device Ctrl-C (M3) never ran | PASS |
| AC-3 | `test_bak_in_generated_dir_is_git_ignored` | suite; `.gitignore` `/app/src/main/generated/baselineProfiles/*.bak` | PASS |
| AC-4 | `test_preflight_waits_for_offline_device`, `_times_out_naming_last_state`, `_reconnects_missing_device` | suite run here | PASS |
| AC-5 | `test_preflight_refusal_kills_no_server`, `test_generation_kills_only_baseline_server`, `test_script_refuses_itest_port_before_any_server_is_touched` | suite run here | PASS |
| AC-6 | `test_make_target_uses_only_baseline_port`, `test_baseline_port_equal_to_itest_or_default_refused`, `test_make_refuses_*` | suite run here | PASS |
| AC-7 | M1 (guard), M2 (scratch: LEFT skipped after Series) | plan record: M2 fails with "The rail did not reopen after entry 5 of 6"; M1 logcat `20261126-183120` passes all 6 entries. The check proves only that the rail reopened (KDoc `railShows`) | PASS |
| AC-8 | M1, `BaselineProfileContentTest` | plan record M1: exit 0, journal empty, `missing=[] unknown=[]`; content test green in `check.full` | PASS |
| AC-9 | `test_api34_refusal_reason_text`, M5 | suite; Makefile, script and `docs/release.md` text read in the diff | PASS |

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Rail check overclaims (triage-1 #1) | blind, edge, tests r1 | fixed (comment and KDoc reworded) | 0a7757b8; selected-entry check is tracked by run rail-left-focus-nearest-entry |
| Script kills a refused port (triage-1 #2) | codex, edge r1 | fixed | 05501d77, `test_script_refuses_itest_port_before_any_server_is_touched` |
| Unchecked cp/sed; stale .bak; reconnect test; refusal paths (triage-1 #3-#6) | r1 | fixed | 05501d77 |
| Shell suite not executable and run by no gate (triage-1 #7) | blind, tests r1 | **open** | file is 100755, but `check.full` in `/home/dev/Puber/.flow/config.yaml` does not run the suite, no other gate does, and no run tracks it. "Conductor adds at ship" is a promise, not a commit |
| Concurrent runs, ddmlib reuse etc. (triage-1 #8) | codex adversarial | rejected, accepted | single leased emulator, no caller runs two generations |
| startup-prof.txt unprotected (triage-2 #1, major) | codex, antigravity, edge, tests r2 | fixed | 9a3c3a12; startup tests in the suite |
| triage-2 #2-#6 | r2 | fixed | 9a3c3a12, 91a9b7ba, effcb7c2 |
| triage-3 #1-#5 | r3 | fixed | 017e975e, d3da0106 |
| SIGKILL inside restore(), daemon window, adjacent rm (triage-3 #6) | antigravity, codex | rejected, accepted | needs two faults at once; the daemon window is documented as D12 in `docs/release.md` |
| Missing or empty profile read as stale (triage-4 #1) | codex, antigravity | fixed | 123eb814, two tests |
| Signal between re-mark and trap re-arm (triage-4 #2) | edge, codex | rejected, accepted | the window is one statement wide |
| Spec AC-1 wording (triage-5) | codex r5 | fixed | d6c128ef |
| `docs/release.md` still states the round-2 rule for leftover backups | judge | **open** | `docs/release.md:143-145` contradicts the recipe and spec AC-1 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | Profiles regenerated in ddbe66c7: all method rules are H and no test-only symbols leaked. No runtime code path changed beyond one semantics modifier on the rail. |
| Reliability | Covers failure, HUP, INT, TERM, SIGKILL leftovers, a missing or empty profile, and failed copies. The daemon write after a restore is documented, not prevented (D12). |
| Maintainability | The recipe is long but its comment matches the code. `testTagsAsResourceId` follows the precedent in `HistoryScreenContent.kt`. No dead code. The new suite runs in no gate (open finding). |
| Security / privacy | No secrets in the diff. `tools/mobile-evidence-audit.sh docs/superpowers/evidence` passed. Box screenshots with watch history were kept out of Git. |

## Manual checks for the owner

None. The on-device Ctrl-C (M3) would only add coverage of the daemon window, which is already accepted as D12.

## Must fix

1. Change `docs/release.md:143-145` to match the recipe: leftover backups are dropped as out of date only when `baseline-prof.txt` exists, is non-empty and has no method rule without `H`; otherwise they are restored.
2. Close triage-1 #7: make `bash tools/tests/test-baseline-profile-tooling.sh` part of `check.full` in `/home/dev/Puber/.flow/config.yaml` (or a make target that it calls) and record the change in `state.md`, or create a run for it.

## Concerns for the owner

- AC-7 proves only that the rail reopened, not which entry is selected. Run rail-left-focus-nearest-entry tracks that.
