# Review tests, round 2 (7a836616..cfa72862)

LENS: tests  ROUND: 2  BASE: 90e69aff

Suite state at head cfa72862: test_ci_workflows.py 6 PASS; test-verify-apk-signer.sh 15 PASS; app/ diff empty. check.full not run (workflows/tools only).
test_rotated_signer_line_counts checked against the 7a836616 verifier in a scratch copy: fails as wanted (exit 0, wanted 1). test_missing_path_fails is a guard.

### F1 [minor] Fix #1 (verifier taken from `github.sha`) has no test that fails if it is reverted or removed
- Where: tools/tests/test_ci_workflows.py:52-55, :94; .github/workflows/release.yml:106-118
- Failure scenario: in a scratch copy, `run:` back to `bash tools/verify-apk-signer.sh ...` keeps all 6 static tests green; deleting the verifier checkout too.
- Fix: assert `f"{VERIFIER_PATH}/tools/verify-apk-signer.sh"` in the step's run, and a checkout with `path == VERIFIER_PATH`, `ref == "${{ github.sha }}"` before the signer step.

### F2 [minor] The durable AC-8 check no longer checks the 5 required secrets in "Validate release secrets"
- Where: tools/tests/test_ci_workflows.py:70-80
- Failure scenario: removing STOREPASS from the step's loop in a scratch copy keeps all tests green.
- Fix: assert the env key set equals the 5 names and each appears in the step's run.

No blocker or major findings.

### Unproven
- test_ci_workflows.py:56 `total > 0` no longer catches a dropped PR checkout.
