# Research: itest-incomplete-run-green

## 1. Request

From `.flow/runs/itest-incomplete-run-green/state.md` (title, Log):

> make itest exits 0 when the instrumentation run fails to complete

> 2026-11-27 created (stage idea) from player-hermetic-fixtures: device-run-5.md run 2: connectedInstrumentationDebugAndroidTest finished in 3 s with exit 0 while the JUnit XML holds only "testRunFailed: Test run failed to complete. No test results". make itest (Makefile:133-141) trusts the Gradle exit code; it should fail on an incomplete run (e.g. check the XML for testRunFailed or a zero test count).

## 2. Understanding

`make itest` is the only sanctioned way to run instrumented tests, and agents read its exit code as the verdict. When the instrumentation process dies before reporting any test, AGP still ends the connected task with BUILD SUCCESSFUL and writes a JUnit XML with a single synthetic `testRunFailed` case, so the run looks green. The target should treat a run that did not complete (or ran no tests) as a failure, with a message that says why.

## 3. Current state in code

- `Makefile:133-141`: the `itest` recipe. Starts a private adb server on `ITEST_ADB_PORT` (default 5038, `Makefile:124-125`), connects `DEVICE`, runs `$(GRADLE) :app:connected$(VARIANT)AndroidTest` with `leaveApksInstalledAfterRun=true` and the optional `TESTS` class filter, then `status=$$?; kill-server; exit $$status`. Nothing reads the test results.
- `Makefile:23,31`: `GRADLE` is overridable (worktrees pass `GRADLE=./tools/agentw`); `VARIANT` is `<Flavor><BuildType>`, e.g. `InstrumentationDebug` with `FLAVOR=instrumentation` (`app/build.gradle.kts:239-249`).
- `Makefile:145-219`: `baseline-profile`, the sibling target. It shows the house style for guarded recipes (refusals with a reason, EXIT trap, exit status kept).
- `tools/tests/test-baseline-profile-tooling.sh:16-50,137-139`: a reusable harness: sandbox copy of the Makefile, fake `adb` and fake Gradle first on PATH, `make -C <sandbox> GRADLE=<fake>`. It is part of `check.full` (`.flow/config.yaml` `check.full`). No test covers `itest` today.
- AGP `9.5.1`, Gradle `9.9.1` (`gradle/libs.versions.toml:2`, `gradle/wrapper/gradle-wrapper.properties`).
- Result location: AGP writes JUnit XML under `app/build/outputs/androidTest-results/connected/...`. Exact subpath for a flavored variant in AGP 9.5.1: `unverified` (no worktree build dir is left to look at; the copies in player-hermetic-fixtures are renamed).
- Evidence of the defect, `.flow/runs/player-hermetic-fixtures/`:
  - `itest5-2.log` (tail): `> Task :app:connectedInstrumentationDebugAndroidTest`, `Running tests on devices: sdk_google_atv64_arm64 - 16`, then `BUILD SUCCESSFUL in 3s`.
  - `itest-r5-2.xml`: `<testsuites tests="1" failures="1">`, one suite `android-test` with testcase `testRunFailed`, failure `Test run failed to complete. No test results`.
  - Complete runs for contrast: `itest-r5-1.xml` (`tests="20" failures="2"`, real class suites), `device-run-6.md:5` (three runs, 20 cases each, "no testRunFailed" checked by hand).

## 4. Related work

| Item | Relation |
|------|----------|
| run `player-hermetic-fixtures` (`state.md:53`) | Source; device run 5 found the false green and opened this run |
| run `player-device-tests-hardening` (idea) | Tests that pass without exercising their scenario; test bodies, not the runner. No overlap |
| run `catalogue-poster-grid` `qa-report.md:45` | A combined itest run died with "Process crashed" on the box; exit code of that run not recorded. Same family of incomplete runs |
| run `diagnostics-server-card-itest-red` (idea) | A real red test via `make itest`; unrelated to completion |
| commit `135309c7` | Added `baseline-profile` hardening and `tools/tests/test-baseline-profile-tooling.sh`, the pattern to copy |
| commit `a0121189` | Introduced the private-server `itest` recipe in its current form (`git log -S leaveApksInstalledAfterRun -- Makefile`) |
| `.flow/roles/test-designer.md:12`, `.claude/agents/qa-device.md:91` | Both route every instrumented test through `make itest`; both rely on its exit code |

Possible duplicate: none.

## 5. External findings

External findings: off (local research only).

## 6. Options

| Option | What | Trade-offs | Size |
|--------|------|-----------|------|
| A (recommended) | Result check in the `itest` recipe: before Gradle, clear (or timestamp) the connected results dir; after Gradle exits 0, fail with a named reason when no XML was written, any XML has a `testRunFailed` testcase, or the total of real test cases is 0. Keep Gradle's non-zero exit as is. Add `tools/tests/test-itest-tooling.sh` on the existing fake-Gradle harness and append it to `check.full` | Closes the false green where agents look; reads only files AGP already writes; stale XML from an earlier run must not count, so the clear/timestamp step is load-bearing | S |
| B | Same check, but the recipe delegates to a new `tools/run-itest.sh` (as `baseline-profile` delegates to `tools/generate-baseline-profile.sh`) | Easier to read and test than a Make one-liner; one more file and the Makefile/script split to keep in step | S |
| C | Gradle-side: a `doLast` on `connected*AndroidTest` in `app/build.gradle.kts` that parses the results and throws | Also catches direct Gradle calls, but those are forbidden anyway; touches the build script and every variant; depends on AGP internals (task type, results dir property) that are `unverified` for 9.5.1 | M |

## 7. Risks

- Stale results: if the dir is not cleared or the XML not filtered by mtime, an old green XML can mask a new incomplete run (the run-5 failure took 3 s and may write nothing new).
- Clearing `app/build/outputs/androidTest-results` must not touch the APKs that `leaveApksInstalledAfterRun` depends on; it only removes reports.
- A zero-test rule fails a `TESTS=` filter that matches no class. That is a typo case the owner probably wants red (see Q1).
- XML parsing in shell: use `grep` on attributes AGP writes (`testcase name="testRunFailed"`, `<testsuites tests="N"`); the exact attribute layout for AGP 9.5.1 is read from `itest-r5-*.xml`, not from a spec.
- No device is needed to prove the fix; the tooling test with fake Gradle writing each XML shape covers it. A live incomplete run cannot be produced on demand.

## 8. Open questions

| ID | Question | Recommended answer |
|----|----------|--------------------|
| Q1 | Fail when the run completed but ran 0 tests (e.g. a `TESTS` filter that matches nothing)? | Yes, with its own message |
| Q2 | Apply the same check to `make baseline-profile` (`:app:generateBaselineProfile`)? | No; open a separate idea run if wanted, its results path and task differ |
| Q3 | Inline in the Makefile (A) or a script under `tools/` (B)? | A if the check stays under about 15 recipe lines, otherwise B |

## 9. Sources

- `/home/dev/Puber/Makefile` (lines 23, 31, 124-141, 145-219)
- `/home/dev/Puber/app/build.gradle.kts:223-249`
- `/home/dev/Puber/gradle/libs.versions.toml:2`, `/home/dev/Puber/gradle/wrapper/gradle-wrapper.properties`
- `/home/dev/Puber/tools/tests/test-baseline-profile-tooling.sh`
- `/home/dev/Puber/.flow/config.yaml` (`check.full`)
- `/home/dev/Puber/.flow/roles/test-designer.md`, `/home/dev/Puber/.claude/agents/qa-device.md`
- Runs: `player-hermetic-fixtures` (`state.md`, `device-run-5.md`, `device-run-6.md`, `itest5-2.log`, `itest-r5-1.xml`, `itest-r5-2.xml`), `catalogue-poster-grid` (`qa-report.md`), `player-device-tests-hardening`, `diagnostics-server-card-itest-red`
- Commits: `135309c7`, `a0121189`, `1674417d`
