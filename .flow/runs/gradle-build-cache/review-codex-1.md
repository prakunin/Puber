CODEX: ran: review (adversarial not requested, size S)
CONFIRMED:
- [major] /home/dev/orca/workspaces/Puber/gradle-build-cache/app/build.gradle.kts:473-477 - `projectFilesReadByTests` omits `src/main/res/values/player.xml` and `src/main/res/values-en/strings.xml`. `SubtitleLabelerTest.kt:82-83` reads both directly. The hazard table in the brief left this test out. - Scenario: a worktree edits only the subtitle format text in `player.xml`. `testDevDebugUnitTest` still reports UP-TO-DATE or FROM-CACHE and the stale pass is restored. This is the hazard AC-4 and the new AGENTS.md bullet exist to prevent. Fix: add both files to the declared inputs. Better, declare `src/main/res` as a whole, which also covers `strings.xml` and `update_file_paths.xml`.
REJECTED:
- none
VERDICT: changes needed
