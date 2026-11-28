LENS: tests  ROUND: 1  BASE: 8c3117ea

### F1 [major] AC4-edit-strings is a P0 red test with no recorded red run
- Where: /home/dev/Puber/.flow/runs/gradle-build-cache/brief.md (Implementation record) and test-design.md traceability
- Problem: The test design lists `AC4-edit-proguard` and `AC4-edit-strings` as P0 red. The record shows a red run only for proguard. For strings.xml it records only the post-change result, "executed; revert FROM-CACHE". `AC1-clean-rebuild` is also P0 red and has no run on base or on base plus step 1. Under the project test rule, a P0 test with no red run is a major finding.
- Failure scenario: If the strings.xml declaration were wrong or missing, nothing shows the test would have failed beforehand. For strings.xml the record does not show that the pre-change task was stale. The test may only pass because `HistoryContextMenuTest` or other sources changed.
- Fix: Add a red run for strings.xml on base plus `org.gradle.caching=true` without the inputs: append a line, expect UP-TO-DATE, revert. Record it. For AC-1, record the base outcome: without the property, the second compile after `clean` is executed, not FROM-CACHE.

### F2 [minor] AC-3 warm-cache run never loaded from cache
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/docs/superpowers/evidence/2026-11-28-gradle-build-cache-device.md, line 9
- Problem: AC-3 asks for `check.full` to pass with the second run on a warm cache. Both recorded runs were UP-TO-DATE (114 of 117 tasks). QA says so itself: "not a cache load". No run showed detekt (alpha), KSP/Room, lint or the Test task passing when restored FROM-CACHE. That is the risk this AC targets (risk score 6).
- Failure scenario: A detekt or Room task that is cacheable but restores a broken output would pass the current evidence and fail for the next worktree.
- Fix: Run `check.full` after `./tools/agentw clean`, or in the git-archive copy used for AC-2. Record the FROM-CACHE count and exit 0.

### F3 [minor] AC4-edit-others was only partly checked, with no red run
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/docs/superpowers/evidence/2026-11-28-gradle-build-cache-device.md, lines 11-13
- Problem: QA ran update_file_paths.xml, a baselineProfiles file and PuberApp.kt after the change. These cases were not run on base. `UpdateFilePathsXmlTest` and `BaselineProfileContentTest` read their files from a working directory, so the base would have shown a stale result. That is likely, but it is not recorded.
- Failure scenario: Low. The declaration covers all the reads I found in `app/src/test`. The `variantFormat(...)` paths in `SubtitleLabelerTest` appear to be fake strings, not file reads.
- Fix: Accept as is, or record one red run per directory input.

### F4 [minor] AC-2 and AC-5 were verified on a copy, not a real worktree
- Where: /home/dev/Puber/.flow/runs/gradle-build-cache/qa-report.md (AC-2 caveat)
- Problem: The copy was made with `git archive` at a different path, so it tests path independence. A real worktree adds a different `.git` file, a `local.properties` sdk.dir and an Orca layout. QA states the caveat. This is not a test failure.
- Failure scenario: A worktree-specific input, such as `local.properties` or a git-derived version, causes a miss that the copy would not show.
- Fix: Accept. If a second same-commit worktree becomes available, run AC-2 once there.

### F5 [nit] No guard for a future test that reads a new project file
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts, the `tasks.withType<Test>` block
- Problem: The input list is hand-maintained. A future test that reads another file reintroduces the stale-result hazard. The only mitigation is the AGENTS.md sentence.
- Fix: Optional. Add a reviewer-role note to check this when a test reads a project file.

### Unproven
- /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts: the `src/main/java` input could cover Kotlin sources in `src/main/kotlin` if any exist. Evidence needed: a test that reads a `src/main/kotlin` path. I found none.
