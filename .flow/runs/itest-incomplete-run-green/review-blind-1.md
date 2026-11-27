LENS: blind  ROUND: 1  BASE: 63771351

### F1 [minor] The new tooling test is not wired into any check
- Where: /home/dev/orca/workspaces/Puber/itest-incomplete-run-green/tools/tests/test-itest-tooling.sh
- Problem: The script is only run by hand (`bash tools/tests/test-itest-tooling.sh`). The diff does not add it to `make check`, CI or any other target.
- Failure scenario: Someone edits the `itest` recipe and breaks the XML check. No automated run catches it, so the guard silently rots.
- Fix: Add a Makefile target, or a line in `check`, that runs the script.

### F2 [minor] The `testRunFailed` and test-count checks match XML text with grep
- Where: /home/dev/orca/workspaces/Puber/itest-incomplete-run-green/Makefile, the `itest` recipe
- Problem: The checks grep for the literal strings `<testcase name="testRunFailed"` and `<testcase `, and count lines rather than elements.
  - If AGP changes attribute order or puts several `testcase` elements on one line, the incomplete-run check stops matching and a dead run reads as green again.
  - Counting lines does not prove the number of tests that ran.
  - A genuinely run test named `testRunFailed` would be reported as an incomplete run.
- Failure scenario: Following an AGP upgrade, the synthetic case is written as `<testcase classname=".." name="testRunFailed">`. The grep misses it. If it is the only case, the "no tests ran" branch still fails the run. If real tests exist beside it, the run passes.
- Fix: Match the attribute order-independently (for example `grep -E '<testcase[^>]*name="testRunFailed"'`). Better, also parse `<failure>` or the `failures=` and `tests=` totals.

### F3 [nit] The stale-results test only half checks what it names
- Where: /home/dev/orca/workspaces/Puber/itest-incomplete-run-green/tools/tests/test-itest-tooling.sh, `test_stale_xml_not_counted`
- Problem: The APK sentinel check shows the cleanup left the APK directory alone. It does not show the stale XML was actually removed.
- Failure scenario: If `rm -rf` were dropped, the stale XML would still be counted. The test would then fail only because `expect_fail_with` does not see "no test results", so it does catch that case, but only indirectly.
- Fix: Assert directly that the stale XML file no longer exists.
