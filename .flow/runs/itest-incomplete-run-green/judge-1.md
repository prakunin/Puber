# Gate - itest-incomplete-run-green - round 1

**Decision:** PASS
**Head:** 345e30c4   **Base:** 63771351

## Done as asked

`git diff 63771351...345e30c4`: `Makefile` `itest` clears only `app/build/outputs/androidTest-results/connected`, then after a Gradle exit 0 fails on no XML, a `testRunFailed` case (any attribute order) or zero `<testcase`; `kill-server` on `ITEST_ADB_PORT` runs on every path (brief.md Plan 1). New `tools/tests/test-itest-tooling.sh` (Plan 2), `docs/testing/player-playback.md:41` rewritten (Plan 3), device evidence file. `make baseline-profile` untouched (Q2). Plan 4 (`check.full` wiring) is the conductor's at ship.

## Traceability

| AC | Test / manual check | Evidence | Status |
|----|---------------------|----------|--------|
| AC-1 | `test_incomplete_run_fails`, `test_incomplete_run_attribute_order_fails` | judge run at 345e30c4: PASS; both FAIL on base Makefile | PASS |
| AC-2 | `test_no_xml_fails` | PASS at head, FAIL on base | PASS |
| AC-3 | `test_zero_tests_fails` | PASS at head, FAIL on base; real unknown-class run fails in Gradle itself (evidence run 2) | PASS |
| AC-4 | `test_complete_passing_run_succeeds` | PASS; real emulator run exit 0, tests=4 (evidence run 1) | PASS |
| AC-5 | `test_stale_xml_not_counted` (stale XML removed, sentinel APK kept) | PASS at head, FAIL on base | PASS |
| AC-6 | `test_gradle_failure_kept` | PASS; real NoSuchTest run exit 2 | PASS |
| AC-7 | config `check.full` && `bash tools/tests/test-itest-tooling.sh` in the worktree | judge run at 345e30c4: exit 0, both tooling scripts all PASS | PASS |
| AC-8 | read `docs/testing/player-playback.md:41` | names the three checks, exit-0 warning gone | PASS |

Evidence file: `docs/superpowers/evidence/2026-11-27-itest-incomplete-run-green-device.md`; `tools/mobile-evidence-audit.sh` passed (judge run).

## Findings

| Finding | Source | Disposition | Proof |
|---------|--------|-------------|-------|
| tooling test not wired into `check.full` (minor) | blind F1, tests F1, triage #1 | fix at ship by the conductor | Plan task 4; accepted: on master before the merge the script is absent and `check.full` of other worktrees would fail |
| `testRunFailed` match depends on attribute order (minor) | blind F2, triage #2 | fixed | 345e30c4; `test_incomplete_run_attribute_order_fails` red on base |
| stale-results test does not assert removal (nit) | blind F3, triage #3 | fixed | 345e30c4 |
| clearing `connected` drops other variants' reports | codex | rejected, accepted | regenerated build output; scope fixed in brief Risks |
| truncated report after first `<testcase` passes | codex | rejected, accepted | no case shown; AGP writes the report whole |
| failed `rm -rf` skips `kill-server`; concurrent itest in one checkout | codex | rejected, accepted | unproven / forbidden by AGENTS.md lease rules |

## Non-functional

| Area | Assessment |
|------|------------|
| Performance | two `find` scans of a small results dir; negligible |
| Reliability | every exit path runs `kill-server`; file names with spaces handled by `-exec ... {} +`; Gradle non-zero keeps its status |
| Maintainability | harness follows `tools/tests/test-baseline-profile-tooling.sh`; check inline (about 12 lines) per plan; no dead code |
| Security / privacy | no secrets; evidence audit passed |

## Manual checks for the owner

none

## Must fix

none

## Concerns for the owner

none
