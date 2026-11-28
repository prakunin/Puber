LENS: tests  ROUND: 3  BASE: dbbab1fd

### F1 [minor] Failing-first (red) run on the unchanged inputs is not recorded
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/docs/superpowers/evidence/2026-11-28-gradle-build-cache-device.md
- Problem: The test design marks `AC4-edit-proguard` and `AC4-edit-strings` as P0 red. It says to run them on base plus step 1 (caching on, no test inputs) and expect UP-TO-DATE or FROM-CACHE after the edit. The evidence shows only the green runs, with edits executed after the fix. Nothing shows the check can fail.
- Failure scenario: Evidence that only shows "executed" can't tell a real input declaration from a test task that always runs. For example, `outputs.upToDateWhen { false }` would also give "executed" on the edit, but the revert would then never be FROM-CACHE. The FROM-CACHE on revert covers most of that. A cheap red run would remove the doubt.
- Fix: Add one line to the evidence for the red run (the edit under caching only, expecting FROM-CACHE or UP-TO-DATE). Alternatively mark the check as a guard.

### F2 [nit] The round-1 table drops the `src/main/java` edit row
- Where: the same evidence file, the "Review round 1 fixes" table
- Problem: The first table keeps a `PuberApp.kt` edit row. The `strings.xml` row now reads "executed / FROM-CACHE", which is correct. The rewrite fixed the garbled rows from round 2, and all five hazard files are now covered.

Round 3 changes only the evidence doc. No production code or tests changed. Nothing was run, since this is a docs-only diff.

### Unproven
- `app/build.gradle.kts` `EpisodeSchedulePrivacyTest` and `SkipSegmentPrivacyTest` read `src/main/java/**`. The evidence row for a `src/main/java` edit is in the round-1 table and was not repeated after the move to the whole `src/main` directory. Evidence that the inputs still cover `src/main/java` after the fix would make this real.
