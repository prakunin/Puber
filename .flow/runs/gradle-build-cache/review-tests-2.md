LENS: tests  ROUND: 2  BASE: b622364e

### F1 [minor] Round-2 evidence table is garbled and does not show the AC-4 outcomes it claims
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/docs/superpowers/evidence/2026-11-28-gradle-build-cache-device.md, "Review round 1 fixes" table (about lines 22-26)
- Problem: The rows have no separator between the edit and the revert outcomes. Examples: `strings.xml edit /  ; revert testDevDebugUnitTest` and `values/player.xml edit / testDevDebugUnitTest ; revert testDevDebugUnitTest`. The strings.xml revert row has no outcome. For the other rows, the edit outcome ("executed") is missing. The table also does not show that `strings.xml` and `values/player.xml` were re-run after the broadened input set.
- Failure scenario: The only proof that the broadened `src/main` input makes the Test task execute after an edit is unreadable. The `values/player.xml` and `strings.xml` rows are the new coverage added in this round. A reader cannot tell whether the edit executed or came from cache, so AC-4 is not demonstrated for them. The summary rows in the round-1 table (for example "AC-4 update_file_paths.xml edit | executed; revert FROM-CACHE") are fine, but the round-2 rows replace them.
- Fix: Rewrite the rows as `<file> | edit: executed | revert: FROM-CACHE`. Add the outcomes for `strings.xml` and `values/player.xml`.

### Unproven
- /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts:473 Inputs are now only `proguard-rules.pro` and `src/main`. I found no other project-file reads in `app/src/test`. `ProguardRulesTest` also tries `File("app/proguard-rules.pro")`, which resolves to the same file. A new test that reads a file outside `src/main`, for example `build.gradle.kts`, would be stale-cached. No such test exists today.
- No automated guard enforces that the Test task declares every file the tests read. This is accepted in the test design, where the checks are build-level evidence.
