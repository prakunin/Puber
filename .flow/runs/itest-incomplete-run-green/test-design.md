# Test design: itest-incomplete-run-green

Kind: bug. Level: all tests are `integration` in the project's sense of a shell test: `tools/tests/test-itest-tooling.sh` drives a sandbox copy of the Makefile with fake adb and fake Gradle (harness of `tools/tests/test-baseline-profile-tooling.sh`). No device, no instrumented level, no manual check.

## Risk profile

| Risk | P | I | Score | Covered by |
|---|---|---|---|---|
| Incomplete run (`testRunFailed` only) exits 0 (the defect) | 3 | 3 | 9 | `test_incomplete_run_fails` |
| Stale green XML from an earlier run masks a new run that wrote nothing | 2 | 3 | 6 | `test_stale_xml_not_counted` |
| No XML written passes | 2 | 3 | 6 | `test_no_xml_fails` |
| Check wrongly fails a complete passing run (false red blocks every itest) | 2 | 3 | 6 | `test_complete_passing_run_succeeds` |
| File names with spaces break the XML scan | 2 | 2 | 4 | all XML fixtures use a name with spaces |
| kill-server skipped on a failure path leaks the private adb server | 2 | 2 | 4 | asserted in AC-1 and AC-6 tests |
| Clearing results dir deletes APKs | 1 | 3 | 3 | assertion in `test_stale_xml_not_counted` (sentinel APK survives) |
| Zero-test run passes (`TESTS=` typo) | 2 | 2 | 4 | `test_zero_tests_fails` |
| Fake Gradle writes XML for `assemble` (itest depends on build) | 1 | 1 | 1 | accepted: fake writes only for a `connected` task |

## Traceability

| AC | Level | Priority | Test | Proves |
|---|---|---|---|---|
| AC-1 | integration | P0 red | `test_incomplete_run_fails` | fails if the `testRunFailed` check is absent: make exits non-zero, output contains `testRunFailed`, fake adb logged `kill-server` with `port=5038` |
| AC-2 | integration | P0 red | `test_no_xml_fails` | fails if a missing XML is accepted: non-zero exit, message says no test results written |
| AC-3 | integration | P0 red | `test_zero_tests_fails` | fails if `tests="0"` / no `<testcase` is accepted: non-zero exit, message says no tests ran |
| AC-4 | integration | P0 guard | `test_complete_passing_run_succeeds` | fails if the check rejects a good run (exit must be 0); XML has real suites and cases |
| AC-5 | integration | P0 red | `test_stale_xml_not_counted` | fails if the results dir is not cleared before Gradle: pre-seeded passing XML, Gradle writes nothing, exit must be non-zero; also sentinel file under `app/build/outputs/apk` still exists |
| AC-6 | integration | P0 guard | `test_gradle_failure_kept` | fails if a Gradle exit 1 is swallowed or kill-server skipped: non-zero exit, `kill-server` on 5038 |
| AC-7 | integration | P1 | `check.full` plus `bash tools/tests/test-itest-tooling.sh` | fails if the script is not wired into check.full or fails in the worktree; verified by running the command |
| AC-8 | manual | P2 | read `docs/testing/player-playback.md:41` | stale claim that `make itest` can exit 0 on an incomplete run remains; doc must name the three checks |

Notes for the implementer:
- `red` rows: run on base (Makefile unchanged) first. AC-1, AC-2, AC-3, AC-5 must fail there (exit 0 today). The test file does not exist on base, so run the new script against the base Makefile.
- AC-1 is the failing-first test. Fixture shape for the incomplete case copies `itest-r5-2.xml` (`<testsuites tests="1" failures="1">`, testcase `testRunFailed`, classname `android-test`); the passing case copies `itest-r5-1.xml` with all cases passing.
- Fake Gradle writes XML only when an argument matches `connected`, chosen by an env var, into `app/build/outputs/androidTest-results/connected/debug/flavors/instrumentation/TEST-fake device - 16.xml` (spaces).
- Assert on exit code plus combined output and the fake adb log, not on Makefile text.

## Bugs

Failing-first: `test_incomplete_run_fails` fails on base (Makefile:133-141 exits with Gradle's status 0, never reads results) and passes after.

## Manual checks

1. Read `docs/testing/player-playback.md:41`. Expected: it lists the no-results, `testRunFailed` and zero-tests checks. Must not: still state that `make itest` may exit 0 on an incomplete run.

## UNTESTABLE

None. A live incomplete run cannot be produced on demand; the fake Gradle covers it. `make baseline-profile` is out of scope (Q2).
