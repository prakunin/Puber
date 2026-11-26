# Gate - baseline-generation-hardening - round 2

**Decision:** PASS
**Head:** 6be9fcf8   **Base:** b27d8e40

## Done as asked

Since round 1 (d6c128ef) the only commit is 6be9fcf8, a two-line change to `docs/release.md:143-144`. It now states the rule the recipe applies (`Makefile:195-199`) and spec AC-1 states: leftover backups are dropped only when `baseline-prof.txt` exists, is non-empty and has no method rule without `H`; otherwise both are restored. The rest of the round 1 reading holds: F2-F5 and the API 34 text are covered, the work stays in scope, and the only production change is the `main_rail` tag (fallback D8, checked on the box in M4).

## Traceability

Re-run here at 6be9fcf8, because `qa-report.md` (19:12) is older than the last commit: `check.full` from `.flow/config.yaml` exited 0, and the shell suite inside it ran 30 PASS, 0 FAIL.

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `test_leftover_bak_is_restored_before_backup`, `_when_profile_is_missing`, `_when_profile_is_empty`, `test_stale_bak_does_not_overwrite_newer_profile`, `test_sigkill_after_merge_before_remark_restores_original`, `test_leftover_restore_failure_keeps_original_backup` | suite run here | PASS |
| AC-2 | `test_failed_generation_restores_profile`, `test_hup/int/term_to_group_restores_profile`, `test_failed_final_remark_restores_profile`, startup-profile tests | suite run here (real recipe, fake Gradle); the on-device Ctrl-C (M3) was not run | PASS |
| AC-3 | `test_bak_in_generated_dir_is_git_ignored` | suite run here | PASS |
| AC-4 | `test_preflight_waits_for_offline_device`, `_times_out_naming_last_state`, `_reconnects_missing_device` | suite run here | PASS |
| AC-5 | `test_preflight_refusal_kills_no_server`, `test_generation_kills_only_baseline_server`, `test_script_refuses_itest_port_before_any_server_is_touched` | suite run here | PASS |
| AC-6 | `test_make_target_uses_only_baseline_port`, `test_baseline_port_equal_to_itest_or_default_refused`, `test_make_refuses_*` | suite run here | PASS |
| AC-7 | M1 (guard), M2 (scratch) | plan record and qa-report: M2 fails with "The rail did not reopen after entry 5 of 6"; M1 logcat `20261126-183120` passes all entries | PASS |
| AC-8 | M1, `BaselineProfileContentTest` | M1 logcat: `missing=[] unknown=[]`, journal empty; content test green in `check.full` here | PASS |
| AC-9 | `test_api34_refusal_reason_text`, M5 | suite run here; the text in the diff was read | PASS |

## Findings

All round 1 dispositions in `judge-1.md` stand. This table covers only the two items that were open.

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| Shell suite runs in no gate (triage-1 #7) | blind, tests r1; judge-1 must-fix 2 | fixed | `check.full` in `/home/dev/Puber/.flow/config.yaml` ends with `&& bash tools/tests/test-baseline-profile-tooling.sh` (diff of the main checkout; uncommitted, next to the owner's own config edits); recorded in `state.md` Log; this gate ran it |
| `docs/release.md` still described the round-2 rule for leftover backups | judge-1 must-fix 1 | fixed | 6be9fcf8 |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | No change since round 1. The profiles are all-`H` and leak no test-only symbols. |
| Reliability | No change since round 1. The Gradle daemon window is documented (D12). |
| Maintainability | The guide, the recipe comment and spec AC-1 now state the same rule, and the suite is gated. No dead code. |
| Security / privacy | 6be9fcf8 is documentation only, with no secrets. The round 1 evidence audit still applies. |

## Manual checks for the owner

None.

## Must fix

None.

## Concerns for the owner

None. The selected-entry rail check sits outside AC-7 and is tracked by the run `rail-left-focus-nearest-entry`.
