# Player overlay auto-hide — review

**Spec:** `docs/superpowers/specs/2026-10-10-player-overlay-autohide-design.md`
**Plan:** `docs/superpowers/plans/2026-10-10-player-overlay-autohide.md`
**Evidence:** `docs/superpowers/evidence/2026-10-10-player-overlay-autohide-device.md`
**Branch:** `player-overlay-autohide` at `9503c2ef`

<!-- The conductor writes the rounds from the reviewers' returns; the
final-judge writes the Gate. Each finding gets a disposition: fixed (<sha>),
rejected (<why>), or deferred (<board row>). Nothing stays open silently. -->

## Round 1

### code-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | minor | ControlsStateMachine.kt:120-124 | Episodes timeout after the media ended lands on the ended controls with focus on `EpisodesButton`, the same target that loses its ring on Up-close; not checked on a device | deferred (player-focus-after-panel-close): repro added to the row |
| 2 | minor | PlayerVM.kt:1253, EpisodesPanel.kt:201 | Last episode ends with Episodes open: waiting keeps the ended controls (D13), but Up in the row closes the panel and exits at once; Up predates the branch and answer 9 does not mention it | rejected for this branch: pre-existing Up behaviour, reported to the user as a note on answer 9 for their test |

### test-reviewer

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | major | PlayerVMOverlayAutoHideTest.kt | AC-11: no VM test for Episodes open when a non-last episode ends; `markPlaybackEnded()` could sit only in the last-episode branch and every test would pass | to fix (round 1) |
| 2 | major | PlayerVMOverlayAutoHideTest.kt | D13 side effect untested: Settings/About open at the end, viewer closes it, controls must stay and Back must exit | to fix (round 1) |
| 3 | major | PlayerVMOverlayAutoHideTest.kt | AC-13 half covered: no test of the debug preference on through the Episodes timeout path | to fix (round 1) |
| 4 | minor | PlayerVMOverlayAutoHideTest.kt `controlsTimerDuringExit_changesNothing` | Never opens Episodes nor asserts no next-episode countdown on the panel-close branch (D9) | to fix (round 1) |
| 5 | minor | PlayerVMOverlayAutoHideTest.kt `pausing_setsIsPlayingFalse_andResumingSetsItTrue` | End-of-media step cannot fail (isPlaying already false) | to fix (round 1) |
| 6 | minor | PlayerVideoSurfaceTest.kt | Tests the helper, not the call sites; `minimalContent()` duplicates the fixture's `testContentState` | to fix (round 1): reuse the fixture; call-site coverage stays with device AC-15 |
| 7 | minor | PlayerVMOverlayAutoHideTest.kt | No VM case for `ResetControlsTimer` while held, nor for reopening Episodes after a timeout | to fix (round 1) |
| 8 | minor | PlayerSettingsPanels.kt | Key hook and hold lambdas have no JVM test | rejected: accepted residual risk in the design (no Compose UI test harness for this path); covered by device checks D3/D4, both PASS |

### codex-reviewer

Ran: review, adversarial

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|
| 1 | minor | ControlsStateMachine.kt:120-125 | Same as code #1: ended-media timeout landing can show controls without usable focus | deferred (player-focus-after-panel-close) |
| 2 | minor | PlayerVM.kt:196-198 | A stall longer than the 800 ms debounce keeps Episodes open for the whole stall plus 6 s | rejected: D12 resumes the timer after buffering by design, the bar behaves the same on master, no AC covers a long stall; raised to the user as a question for their test |

Rejected Codex claims:

- Severity "medium" for the long stall and the claim it violates AC-3/AC-6: the ACs assume no stall, and D12 resumes the timer after buffering on purpose.
- Severity "medium" for the ended-controls focus: Back and Up still work, and the root cause is the deferred focus bug.
- The `review` pass itself had no findings.

### ux-designer (visual changes)

Not run: no Compose layout change (the diff adds a non-consuming key modifier and dialog callbacks only).

| # | Severity | Where | Finding | Disposition |
|---|---|---|---|---|

## Gate

<!-- final-judge, gate mode. -->

**Decision:** PASS | CONCERNS | FAIL | WAIVED (user only)

### Traceability

| AC | Test / check | Evidence | Status |
|---|---|---|---|

### Non-functional

| Area | Assessment |
|---|---|
| Performance | |
| Reliability | |
| Maintainability | |
| Security / privacy | |
| TV (focus, D-pad, Back) | |

### Must fix

### Concerns for the user
