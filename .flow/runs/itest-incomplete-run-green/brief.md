# Brief: itest-incomplete-run-green

Kind: bug. Size: S. Tracks: none. Device verification: no (Makefile recipe and a shell test; the fake-Gradle test proves every AC, and a live incomplete run cannot be produced on demand).
Research: research.md (sections 3, 6, 7); option A taken. Checked on master 63771351.

## Root cause (confirmed)

`Makefile:138-141` runs `:app:connected$(VARIANT)AndroidTest` and exits with Gradle's status; nothing reads the results. When the instrumentation process dies before reporting a test, AGP 9.5.1 still ends the task with BUILD SUCCESSFUL and writes one synthetic case `testRunFailed` ("Test run failed to complete. No test results"):

| Evidence | Where |
|---|---|
| `BUILD SUCCESSFUL in 3s` after `Running tests on devices` | `.flow/runs/player-hermetic-fixtures/itest5-2.log` (tail) |
| `<testsuites tests="1" failures="1">`, testcase `testRunFailed`, classname `android-test` | `.flow/runs/player-hermetic-fixtures/itest-r5-2.xml` |
| Same command with real failing tests exits 2 (Gradle fails on real failures) | `.flow/runs/player-hermetic-fixtures/device-run-5.md` runs 1, 3 |
| Results dir for the instrumentation flavor: `app/build/outputs/androidTest-results/connected/debug/flavors/instrumentation/`; file name has spaces (`TEST-sdk_google_atv64_arm64 - 16.xml`, as the sibling baselineprofile results) | `.flow/runs/player-audio-und-label/qa-report.md:14` |

## Acceptance criteria

All checked by `tools/tests/test-itest-tooling.sh` (fake adb and fake Gradle, sandbox copy of the Makefile, as `tools/tests/test-baseline-profile-tooling.sh:16-50`); "the XML" is written by the fake Gradle under `app/build/outputs/androidTest-results/connected/debug/flavors/instrumentation/` in a file whose name contains spaces.

- AC-1. Given Gradle exits 0 and writes only the `testRunFailed` XML (shape of `itest-r5-2.xml`), when `make DEVICE=<s> FLAVOR=instrumentation itest` runs, then make exits non-zero, the output names the incomplete run (contains `testRunFailed`), and adb `kill-server` on port 5038 was called.
- AC-2. Given Gradle exits 0 and writes no XML, when `make itest` runs, then make exits non-zero with a message that no test results were written.
- AC-3. Given Gradle exits 0 and writes an XML with zero `<testcase` elements (`tests="0"`, e.g. a `TESTS=` filter that matches nothing), when `make itest` runs, then make exits non-zero with a message that no tests ran.
- AC-4. Given Gradle exits 0 and writes a complete passing XML (shape of `itest-r5-1.xml`, all cases passing), when `make itest` runs, then make exits 0.
- AC-5. Given a complete passing XML from an earlier run is already in the results dir and Gradle exits 0 writing nothing, when `make itest` runs, then make exits non-zero (the old XML does not count).
- AC-6. Given Gradle exits 1, when `make itest` runs, then make exits non-zero and `kill-server` on 5038 was called (today's behaviour kept).
- AC-7. Given the worktree, when `check.full` from `.flow/config.yaml` runs with `bash tools/tests/test-itest-tooling.sh` appended, then it passes.
- AC-8. Given `docs/testing/player-playback.md:41`, when read, then it no longer says `make itest` can exit 0 on an incomplete run and instead names the checks of AC-1..AC-3.

## Risks

- Clearing the results dir must stay limited to `app/build/outputs/androidTest-results/connected`; the APKs under `app/build/outputs/apk` are untouched.
- File names with spaces: iterate with `find ... -name '*.xml' -print0` or a quoted glob, never word-split `$(ls)`.
- The `itest` target depends on `build` (`Makefile:73-74`, `$(GRADLE) assemble$(VARIANT)`); the fake Gradle must write XML only for a `connected*AndroidTest` argument.
- `make baseline-profile` has the same blind spot for `:app:generateBaselineProfile`; not changed here (Q2).

## Open questions

| ID | Question | Recommended | Blocks |
|---|---|---|---|
| Q1 | Fail a completed run that ran 0 tests (a `TESTS=` filter matching nothing)? | Yes, with its own message (AC-3) | no |
| Q2 | Apply the same check to `make baseline-profile`? | No; open an idea run if wanted | no |

## Plan

1. `Makefile:133-141` (`itest`): before the Gradle call, `rm -rf app/build/outputs/androidTest-results/connected`. After it, keep a non-zero `status` as is; when `status` is 0, collect `*.xml` under that dir and set a non-zero status with a one-line reason when (a) none exist, (b) any holds `<testcase name="testRunFailed"`, (c) the count of `<testcase ` elements is 0. `kill-server` on `ITEST_ADB_PORT` runs on every path before `exit`. Inline in the recipe if it stays under about 15 lines; otherwise move the check into `tools/check-itest-results.sh` called from the recipe (research.md: 6, option B). Add a short comment above the recipe naming the AGP false green.
2. New `tools/tests/test-itest-tooling.sh`: copy the harness of `tools/tests/test-baseline-profile-tooling.sh` (sandbox Makefile, fake adb logging `port=`, fake Gradle on `GRADLE=`, per-test `mktemp -d`); fake Gradle writes the XML shape chosen by an env var only when called with a `connected` task. One test function per AC-1..AC-6.
3. `docs/testing/player-playback.md:41`: replace the warning with the checks `make itest` now makes.
4. Conductor, main checkout: append ` && bash tools/tests/test-itest-tooling.sh` to `check.full` in `.flow/config.yaml` (AC-7).
