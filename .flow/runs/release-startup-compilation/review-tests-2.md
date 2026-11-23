LENS: tests  ROUND: 2  BASE: e4c3b5aa (incremental a4c19ba4..2d1c7395)

No blockers or majors.

check.full (`AGENT_GRADLE_SERIAL=1 ./tools/agentw testDevDebugUnitTest :app:detektAll :app:lintDevDebug` at 2d1c7395): BUILD SUCCESSFUL. 2395 unit tests ran: 0 failed, 0 skipped, 0 errors. Result XMLs were written during this run. Detekt and lint are green.

Would each changed test fail if its behaviour broke?
- #8, `TelemetryRuntimeTest.profileStatusReachesMarksOnlyThroughTheTelemetryExecutor`: yes. Suppose the listener calls `marks.markProfileStatus(code)` directly. The callback then sets `profileStatus` right away, and `marks.onTimeout()` -> `emit()` writes `prof=327680`. `assertTrue(... != "327680")` fails. Its scheduled check can't hide that: the line is already written, and the `emitted` flag blocks a second one. On the real path (`TelemetryRuntime.kt:57`, `sink.schedule(0)`), the task waits in `scheduled`. `runRepeating()` only flushes the writer, so the forced line carries no `prof` and the test passes. `single { }` also fails if no line is written.
- #7, `StartupMarksTest.boundEmitsWithoutProfileStatusWhenAwaited`: yes. `queued.map { it.first } == [7000]` fails if the awaited path keeps 5 s. The plain path stays pinned at 5 s by `lineEmitsFiveSecondsAfterRevealWithoutReady` (asserts `[5_000L]`), so a swapped condition is also caught.
- Rail-tab assertion in `BaselineProfileContentTest`: yes. The checked-in profile has 0 rule lines for `ui/feature/favorites/`, `history/` and `contentlist/` at both e4c3b5aa and a4c19ba4, and 109 / 317 / 366 at head. The plan records the test red before the regeneration.

### F1 [minor] The test-design row for #8 still describes the old procedure
- Where: `/home/dev/Puber/.flow/runs/release-startup-compilation/test-design.md`, the AC-5 P2 row for `profileStatusReachesMarksOnlyThroughTheTelemetryExecutor`
- Problem: The row still says "executor not run: the line is not yet written; after `runScheduled` it carries `prof`". The test now forces `onTimeout()` and asserts `prof` is absent.
- Failure scenario: A later reviewer checks the test against the row, finds a mismatch and opens a false finding.
- Fix: Change Proves to: "callback fired, `onTimeout()` before the executor runs: the forced line has no `prof`; fails if the listener writes marks directly (R6)".

### F2 [minor] The AC-3 traceability row names the old test and leaves out the rail tabs
- Where: `/home/dev/Puber/.flow/runs/release-startup-compilation/test-design.md`, first AC-3 P0 row
- Problem: The row still names `baselineProfileCoversDetailsPlayerAndStartupClasses`, which this diff renamed to `baselineProfileCoversDetailsPlayerRailTabsAndStartupClasses`. It also doesn't list the favorites, history and contentlist prefixes the test now asserts.
- Failure scenario: A search for the test by the name in the row finds nothing, and the rail-tab coverage is not traceable to an AC.
- Fix: Rename the row to the new test and add the three prefixes and the case "a regeneration whose rail journey skipped the tabs".

### F3 [minor] The `prof` check fails with no message
- Where: `/home/dev/orca/workspaces/Puber/release-startup-compilation-2/app/src/test/kotlin/com/kino/puber/core/telemetry/TelemetryRuntimeTest.kt:342`
- Problem: `assertTrue(x != "327680")` reports only "expected true" when it fails, so the failure doesn't show which value was written.
- Failure scenario: The test fails, and the output doesn't say whether `prof` was 327680 or something else.
- Fix: Use `assertNull(lines().single { it.str("type") == "startup" }.str("prof"))`.

### Unproven
- `AuthEventLog.kt:40`: the `hasValue` empty-string branch has no test. It is generation-only diagnostics, so this matters only if a run's pass or fail starts to depend on this log line.
- `Makefile`: the strip / restore / re-`H` steps in `baseline-profile` are checked only by a recorded dry run. It becomes a finding if a failed generation is ever seen to leave the stripped `baseline-prof.txt` behind.
- `BaselineProfileGenerator.kt:194`: `railShows` might pass even when the rail is collapsed, if the title stays in the accessibility tree. The regenerated rail-tab rules make that moot for this profile; it becomes a finding only if a later regeneration loses those rules while the check passes.
