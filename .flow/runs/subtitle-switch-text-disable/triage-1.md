# Review triage - round 1

Run: subtitle-switch-text-disable   Base: 7a9fd580   Reviewed head: 4e4369e2

Sources: review-blind-1.md (F1 major, F2, F3 minor, F4 nit), review-tests-1.md (no findings, 2 unproven), review-codex-1.md (ran: review; 1 minor). Antigravity: skipped, quota exhausted (owner: do not wait).

| # | Source | Severity | Where | Finding | Verified how | Decision | Disposition |
|---|--------|----------|-------|---------|--------------|----------|-------------|
| 1 | blind F1 | major | whole diff | no production change, so nothing implements a fix | Q1 (owner, recommended): option B, no port of upstream 7d5addd2; tests are guards by design (test-design.md, implementation.md mutation table) | reject | intended scope; the blind lens had no brief |
| 2 | blind F2 | minor | `PlayerVMTrackSelectionTest` sendsOffRow | the unit test checks a mocked controller call, not the player outcome | the instrumented AC-1 test covers the controller side and runs in the suite (16/16, 3 runs + qa run) | reject | layered coverage exists |
| 3 | blind F3, tests (unproven) | minor | `PlaybackControllerNetworkTracksTest.kt:~350-353` | negative checks (no selected text track, no Spanish VTT request) run once with no settle wait | read the test | fix | keep the negatives true over a short bounded settle window before asserting |
| 4 | codex, tests (unproven) | minor | `PlaybackControllerNetworkTracksTest.kt:~373-400` | the AC-2 test passes even without the pending-row reselection it names: the carried override or preferred language also select English | read switchStream (`PlaybackController.kt:535,549`) | fix | rename the test and its comment and the docs line to what it proves (same English subtitle stays selected across the switch); no fixture rework |
| 5 | blind F4 | nit | helper placement, LargeClass suppress | style | - | reject | optional, same pattern as other test classes |

## Rejected codex claims

- none

## Next

- fix round 1: #3, #4 (tests and docs; run the class on the emulator, check.fast); size S, no remaining blocker or major, no second review round; unproven lines: 1 (blind 1)
