LENS: edge  ROUND: 2  BASE: b622364e

### F1 [minor] Evidence table for the AC-4 round-1 fix records no outcome for the strings.xml edit
- Where: /home/dev/orca/workspaces/Puber/gradle-build-cache/docs/superpowers/evidence/2026-11-28-gradle-build-cache-device.md (lines of the "Review round 1 fixes" table)
- Problem: The `strings.xml` row reads `edit /  ; revert testDevDebugUnitTest`. The edit outcome is blank, and the revert outcome is missing, while the other rows give `FROM-CACHE`. The `values/player.xml` row has the same gap on the revert. The rows are also not formatted as the 2-column table the header declares.
- Failure scenario: AC-4 names `strings.xml` and `proguard-rules.pro` as the files that must be checked. A reader of the evidence cannot confirm that the `strings.xml` edit made `testDevDebugUnitTest` execute, so the AC is not demonstrated for that file.
- Fix: Re-run the `strings.xml` and `values/player.xml` edit and revert checks. Record each outcome (EXECUTED, or FROM-CACHE on revert) in the table. Use the same `file | outcome` column layout as the other rows.

### Unproven
- /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts:473 Tests that read files outside `app/src/main` and `proguard-rules.pro` (for example `src/dev`, `src/prod`, `src/debug`, `AndroidManifest` overlays, `config/`, repo-root files). A grep of `app/src/test` for `File(`, `Paths.get`, `src/(dev|prod|debug|release)`, `docs/` and `config/` found only the previously listed reads. It also found fake or scratch paths (`update.apk`, `tempDir`, `cacheDir`). A new test that reads another path would be a stale-cache hazard, but none exists today.
- /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts:473 Declaring all of `src/main` means any asset or resource edit invalidates test results. This costs cache hits only, not correctness. The diff accepts that trade deliberately.
