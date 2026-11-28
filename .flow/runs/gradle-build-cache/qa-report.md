Round 1

CHECK: check.full (+ both tooling scripts) -> pass (exit 0; run was warm, 117 tasks: 3 executed, 114 up-to-date)
DEVICE: not needed: build config and AGENTS.md only
ACS:
| AC | Status | Evidence |
|----|--------|----------|
| AC-1 | PASS | implementer run: after clean, ksp and compile FROM-CACHE, 9 from cache (not re-run by me) |
| AC-2 | PASS (with caveat) | copy of HEAD at a different path (git archive, run with the shared GRADLE_USER_HOME, not a real worktree): kspDevDebugKotlin FROM-CACHE, compileDevDebugKotlin FROM-CACHE, 10 from cache |
| AC-3 | PASS | check.full exit 0 after the implementer's two runs; my run was UP-TO-DATE, not a cache load |
| AC-4 | PASS | proguard, strings (implementer) and update_file_paths.xml, a baselineProfiles file, PuberApp.kt (me): each edit executes the Test task, each revert FROM-CACHE; git status clean |
| AC-5 | PASS | lint loaded FROM-CACHE in the copy; the original worktree path is absent from its lint-results-devDebug.{xml,txt,sarif}, only its own path |
| AC-6 | PASS | AGENTS.md line 72 names the cache and --no-build-cache |
TESTS ADDED: none
EVIDENCE: /home/dev/orca/workspaces/Puber/gradle-build-cache/docs/superpowers/evidence/2026-11-28-gradle-build-cache-device.md (commit b622364e; audit passed)
DEFECTS: none
UNAVAILABLE: none
MANUAL: none. Caveat: the only other worktree at the same app sources (player-dpad-seek-coalesce) already had build outputs, so I used a copy outside Orca; the copy differs by path only, which is what AC-2 tests.
