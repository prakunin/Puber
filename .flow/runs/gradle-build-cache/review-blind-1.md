LENS: blind  ROUND: 1  BASE: 8c3117ea

### F1 [major] Test input declaration misses two files that SubtitleLabelerTest reads, so the build cache can replay a stale pass
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts:468-480, and `app/src/test/kotlin/com/kino/puber/ui/feature/player/model/SubtitleLabelerTest.kt:82-83`
- Problem: The `inputs.files(...)` list declares `strings.xml`, `update_file_paths.xml`, `proguard-rules.pro`, the baseline profile directory and `src/main/java`. `composedLabel_usesTheSameSeparatorAsTheVariantString` also reads `src/main/res/values/player.xml` and `src/main/res/values-en/strings.xml` through `variantFormat(...)`. Neither file is declared. AGENTS.md now says tests that read project files "must declare them as inputs", and this change does not do that for these two.
- Failure scenario: Someone edits the variant-label separator in `values-en/strings.xml` or `values/player.xml`. Nothing the test task fingerprints has changed, so it is UP-TO-DATE or a FROM-CACHE hit, including a hit from another worktree through the shared `GRADLE_USER_HOME`. The test that should fail does not run, and the broken change passes. This is the false green the new inputs block was added to prevent.
- Fix: Declare the whole `src/main/res` directory, for example `layout.projectDirectory.dir("src/main/res")`, in place of the individual res files. Alternatively add the two missing paths. A grep for `src/main` and `File(` under `app/src/test` should confirm that nothing else is missed.

### F2 [minor] The input list is hand-maintained and has no guard against drift
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts:471-480
- Problem: F1 shows the list is already incomplete at introduction. Any new test that reads a project file will fail the same way. Only the AGENTS.md sentence guards against that.
- Fix: Declare the whole `src/main` directory as an input with relative path sensitivity. Source changes already invalidate the unit tests, so this costs almost nothing and removes the drift.

### Unproven
- /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts:471 Declaring `src/main/java` as a test input with `PathSensitivity.RELATIVE` is probably harmless. Compiled outputs already feed the test task, so this just fingerprints source twice.
- /home/dev/orca/workspaces/Puber/gradle-build-cache/gradle.properties The build cache is now on globally. A `Test` task cached from another worktree's run cannot rerun a flaky test. AGENTS.md documents `--no-build-cache` for this, so I don't count it as a finding.
