LENS: tests  ROUND: 1  BASE: 63771351

### F1 [minor] AC-7: the new test is not wired into `check.full`
- Where: `/home/dev/Puber/.flow/config.yaml` (`check.full`); state.md line 43.
- Problem: `grep itest .flow/config.yaml` finds nothing, so `check.full` does not run `tools/tests/test-itest-tooling.sh`. The run notes say the edit is deferred to ship because the script exists only on the branch. Until that edit lands, a regression in the `itest` recipe will not turn the suite red.
- Failure scenario: someone edits the `itest` recipe later and breaks the result checks. `check.full` still passes.
- Fix: append ` && bash tools/tests/test-itest-tooling.sh` to `check.full` at ship, and confirm it before the gate.

### Unproven
- `Makefile` `testRunFailed` grep: it needs the attribute order `<testcase name="testRunFailed"`. This matches the recorded AGP shape in `itest-r5-2.xml`. Another AGP version that reorders the attributes would slip past the check.
- `Makefile` zero-test branch: the device evidence says a `TESTS=` filter that matches nothing makes AGP fail the Gradle run itself. So the zero-test branch is covered only by the fake-Gradle test. That is fine for AC-3, but the "TESTS typo" scenario in the test design is really caught by Gradle's exit code.

Suite state and traceability:
- I ran `bash tools/tests/test-itest-tooling.sh` in the worktree. All 6 tests pass.
- I ran the same script against the base Makefile in a scratch copy.
  - `test_incomplete_run_fails`, `test_no_xml_fails`, `test_stale_xml_not_counted` and `test_zero_tests_fails` fail on base.
  - `test_complete_passing_run_succeeds` and `test_gradle_failure_kept` pass on base.
  - This matches the test design: AC-1, AC-2, AC-3 and AC-5 are red, and AC-4 and AC-6 are marked guard.
- Every AC has a test or a manual check.
  - AC-1 to AC-6 each have a named test.
  - AC-8 is covered by the doc edit.
  - AC-7 is the F1 gap.
- I read the assertions. They check exit code, output text, the fake adb `kill-server` on port 5038, and that the sentinel APK survives. None of them mirrors the implementation.
