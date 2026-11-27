# Implementation record: itest-incomplete-run-green

## Commits

```
7d329c87 Document the result checks make itest now makes
ea626208 Test make itest against incomplete, empty and stale instrumented results
0d9212c6 Fail make itest when the instrumented run left no usable test results
```

## Red run

Run on the unchanged Makefile (test script new), failure line for each: `make exited 0` (output shows Gradle ran, no result check).

- red: test_incomplete_run_fails - make exited 0
- red: test_no_xml_fails - make exited 0
- red: test_zero_tests_fails - make exited 0
- red: test_stale_xml_not_counted - make exited 0
- green on base (guards, as designed): test_complete_passing_run_succeeds, test_gradle_failure_kept

After the change all six pass.

## Checks

- check.fast: BUILD SUCCESSFUL.
- check.full plus `bash tools/tests/test-itest-tooling.sh`: green, both tooling scripts PASS.

## Deviations

None. The check stayed inline in the Makefile recipe (about 12 lines); no `tools/check-itest-results.sh`. Plan task 4 (config.yaml) left to the conductor.

## For reviewers

- Makefile `itest`: scan uses `find ... -exec grep/cat {} +`, safe for file names with spaces; results dir cleared is only `app/build/outputs/androidTest-results/connected` (sentinel APK test).
- `make itest` echoes the added `rm -rf` line.
- The `testRunFailed` check matches the exact `<testcase name="testRunFailed"` text with that attribute order, as in the real XML.

## Fix round 1

Triage items #2 and #3 (#1 is the conductor's, at ship).

- #2: the Makefile match is now `<testcase[^>]*name="testRunFailed"`; new tooling case `test_incomplete_run_attribute_order_fails` (fake XML with `classname` before `name`).
  - red: test_incomplete_run_attribute_order_fails - make exited 0 (current recipe)
- #3: `test_stale_xml_not_counted` now asserts the stale XML file is gone.
- Checks: tooling script 7 of 7 PASS; check.fast BUILD SUCCESSFUL.
